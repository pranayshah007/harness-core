/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.aws.delegate;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.eraro.ErrorCode.INIT_TIMEOUT;
import static io.harness.logging.LogLevel.ERROR;
import static io.harness.threading.Morpheus.sleep;

import static software.wings.service.impl.aws.model.AwsConstants.DEFAULT_AMI_ASG_DESIRED_INSTANCES;
import static software.wings.service.impl.aws.model.AwsConstants.DEFAULT_AMI_ASG_MAX_INSTANCES;
import static software.wings.service.impl.aws.model.AwsConstants.DEFAULT_AMI_ASG_MIN_INSTANCES;
import static software.wings.service.impl.aws.model.AwsConstants.DEFAULT_AMI_ASG_NAME;

import static com.google.common.collect.Lists.newArrayList;
import static java.lang.String.format;
import static java.time.Duration.ofSeconds;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.EMPTY;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.concurrent.HTimeLimiter;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InterruptedRuntimeException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.logging.LogCallback;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.AwsConfig;
import software.wings.beans.command.ExecutionLogCallback;
import software.wings.service.impl.aws.client.CloseableAmazonWebServiceClient;
import software.wings.service.impl.aws.model.AwsAsgGetRunningCountData;
import software.wings.service.intfc.aws.delegate.AwsAsgHelperServiceDelegate;
import software.wings.service.intfc.aws.delegate.AwsEc2HelperServiceDelegate;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.autoscaling.AmazonAutoScalingClient;
import com.amazonaws.services.autoscaling.AmazonAutoScalingClientBuilder;
import com.amazonaws.services.autoscaling.model.Activity;
import com.amazonaws.services.autoscaling.model.AttachLoadBalancerTargetGroupsRequest;
import com.amazonaws.services.autoscaling.model.AttachLoadBalancersRequest;
import com.amazonaws.services.autoscaling.model.AutoScalingGroup;
import com.amazonaws.services.autoscaling.model.CreateAutoScalingGroupRequest;
import com.amazonaws.services.autoscaling.model.CreateAutoScalingGroupResult;
import com.amazonaws.services.autoscaling.model.CreateLaunchConfigurationRequest;
import com.amazonaws.services.autoscaling.model.CreateLaunchConfigurationResult;
import com.amazonaws.services.autoscaling.model.CreateOrUpdateTagsRequest;
import com.amazonaws.services.autoscaling.model.DeleteAutoScalingGroupRequest;
import com.amazonaws.services.autoscaling.model.DeleteLaunchConfigurationRequest;
import com.amazonaws.services.autoscaling.model.DeletePolicyRequest;
import com.amazonaws.services.autoscaling.model.DeleteScheduledActionRequest;
import com.amazonaws.services.autoscaling.model.DescribeAutoScalingGroupsRequest;
import com.amazonaws.services.autoscaling.model.DescribeAutoScalingGroupsResult;
import com.amazonaws.services.autoscaling.model.DescribeLaunchConfigurationsRequest;
import com.amazonaws.services.autoscaling.model.DescribePoliciesRequest;
import com.amazonaws.services.autoscaling.model.DescribePoliciesResult;
import com.amazonaws.services.autoscaling.model.DescribeScalingActivitiesRequest;
import com.amazonaws.services.autoscaling.model.DescribeScalingActivitiesResult;
import com.amazonaws.services.autoscaling.model.DescribeScheduledActionsRequest;
import com.amazonaws.services.autoscaling.model.DescribeScheduledActionsResult;
import com.amazonaws.services.autoscaling.model.DetachLoadBalancerTargetGroupsRequest;
import com.amazonaws.services.autoscaling.model.DetachLoadBalancersRequest;
import com.amazonaws.services.autoscaling.model.LaunchConfiguration;
import com.amazonaws.services.autoscaling.model.PutScalingPolicyRequest;
import com.amazonaws.services.autoscaling.model.PutScalingPolicyResult;
import com.amazonaws.services.autoscaling.model.PutScheduledUpdateGroupActionRequest;
import com.amazonaws.services.autoscaling.model.ScalingPolicy;
import com.amazonaws.services.autoscaling.model.ScheduledUpdateGroupAction;
import com.amazonaws.services.autoscaling.model.SetDesiredCapacityRequest;
import com.amazonaws.services.autoscaling.model.Tag;
import com.amazonaws.services.autoscaling.model.UpdateAutoScalingGroupRequest;
import com.amazonaws.services.ec2.model.Instance;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.util.concurrent.TimeLimiter;
import com.google.common.util.concurrent.UncheckedTimeoutException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
@OwnedBy(CDP)
public class AwsAsgHelperServiceDelegateImpl
    extends AwsHelperServiceDelegateBase implements AwsAsgHelperServiceDelegate {
  private static final long AUTOSCALING_REQUEST_STATUS_CHECK_INTERVAL = TimeUnit.SECONDS.toSeconds(15);
  @Inject private AwsEc2HelperServiceDelegate awsEc2HelperServiceDelegate;
  @Inject private TimeLimiter timeLimiter;
  private final Integer MAX_SCHEDULED_ACTIONS_BATCH_SIZE = 100;

  @VisibleForTesting
  AmazonAutoScalingClient getAmazonAutoScalingClient(Regions region, AwsConfig awsConfig) {
    AmazonAutoScalingClientBuilder builder = AmazonAutoScalingClientBuilder.standard().withRegion(region);
    attachCredentialsAndBackoffPolicy(builder, awsConfig);
    return (AmazonAutoScalingClient) builder.build();
  }

  @Override
  public List<String> listAutoScalingGroupNames(
      AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails, String region) {
    try {
      List<AutoScalingGroup> result = listAllAsgs(awsConfig, encryptionDetails, region);
      return result.stream().map(AutoScalingGroup::getAutoScalingGroupName).collect(toList());
    } catch (AmazonServiceException amazonServiceException) {
      handleAmazonServiceException(amazonServiceException);
    } catch (AmazonClientException amazonClientException) {
      handleAmazonClientException(amazonClientException);
    }
    return emptyList();
  }

  @Override
  public List<String> listAutoScalingGroupInstanceIds(AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails,
      String region, String autoScalingGroupName, boolean isInstanceSync) {
    encryptionService.decrypt(awsConfig, encryptionDetails, isInstanceSync);
    ExceptionMessageSanitizer.storeAllSecretsForSanitizing(awsConfig, encryptionDetails);
    try (CloseableAmazonWebServiceClient<AmazonAutoScalingClient> closeableAmazonAutoScalingClient =
             new CloseableAmazonWebServiceClient(getAmazonAutoScalingClient(Regions.fromName(region), awsConfig))) {
      DescribeAutoScalingGroupsRequest describeAutoScalingGroupsRequest =
          new DescribeAutoScalingGroupsRequest().withAutoScalingGroupNames(autoScalingGroupName);
      tracker.trackASGCall("Describe ASGs");
      DescribeAutoScalingGroupsResult describeAutoScalingGroupsResult =
          closeableAmazonAutoScalingClient.getClient().describeAutoScalingGroups(describeAutoScalingGroupsRequest);
      if (describeAutoScalingGroupsResult.getAutoScalingGroups().isEmpty()) {
        return emptyList();
      }
      AutoScalingGroup autoScalingGroup = describeAutoScalingGroupsResult.getAutoScalingGroups().get(0);
      return autoScalingGroup.getInstances()
          .stream()
          .map(com.amazonaws.services.autoscaling.model.Instance::getInstanceId)
          .collect(toList());
    } catch (AmazonServiceException amazonServiceException) {
      handleAmazonServiceException(amazonServiceException);
    } catch (AmazonClientException amazonClientException) {
      handleAmazonClientException(amazonClientException);
    } catch (Exception e) {
      Exception sanitizeException = ExceptionMessageSanitizer.sanitizeException(e);
      log.error("Exception listAutoScalingGroupInstanceIds", sanitizeException);
      throw new InvalidRequestException(ExceptionUtils.getMessage(sanitizeException), sanitizeException);
    }
    return emptyList();
  }

  @Override
  public List<com.amazonaws.services.autoscaling.model.Instance> listAutoScalingGroupModelInstances(AwsConfig awsConfig,
      List<EncryptedDataDetail> encryptionDetails, String region, String autoScalingGroupName, boolean isInstanceSync) {
    try {
      encryptionService.decrypt(awsConfig, encryptionDetails, isInstanceSync);
      ExceptionMessageSanitizer.storeAllSecretsForSanitizing(awsConfig, encryptionDetails);
      DescribeAutoScalingGroupsRequest describeAutoScalingGroupsRequest =
          new DescribeAutoScalingGroupsRequest().withAutoScalingGroupNames(autoScalingGroupName);
      AmazonAutoScalingClient amazonAutoScalingClient = getAmazonAutoScalingClient(Regions.fromName(region), awsConfig);
      tracker.trackASGCall("Describe ASGs");
      DescribeAutoScalingGroupsResult describeAutoScalingGroupsResult =
          amazonAutoScalingClient.describeAutoScalingGroups(describeAutoScalingGroupsRequest);
      if (describeAutoScalingGroupsResult.getAutoScalingGroups().isEmpty()) {
        return emptyList();
      }
      AutoScalingGroup autoScalingGroup = describeAutoScalingGroupsResult.getAutoScalingGroups().get(0);
      return autoScalingGroup.getInstances();
    } catch (AmazonServiceException amazonServiceException) {
      handleAmazonServiceException(amazonServiceException);
    } catch (AmazonClientException amazonClientException) {
      handleAmazonClientException(amazonClientException);
    }
    return emptyList();
  }

  @Override
  public List<Instance> listAutoScalingGroupInstances(AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails,
      String region, String autoScalingGroupName, boolean isInstanceSync) {
    List<String> instanceIds =
        listAutoScalingGroupInstanceIds(awsConfig, encryptionDetails, region, autoScalingGroupName, isInstanceSync);
    return awsEc2HelperServiceDelegate.listEc2Instances(
        awsConfig, encryptionDetails, instanceIds, region, isInstanceSync);
  }

  @Override
  public AutoScalingGroup getAutoScalingGroup(
      AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails, String region, String autoScalingGroupName) {
    encryptionService.decrypt(awsConfig, encryptionDetails, false);
    ExceptionMessageSanitizer.storeAllSecretsForSanitizing(awsConfig, encryptionDetails);
    try (CloseableAmazonWebServiceClient<AmazonAutoScalingClient> closeableAmazonAutoScalingClient =
             new CloseableAmazonWebServiceClient(getAmazonAutoScalingClient(Regions.fromName(region), awsConfig))) {
      DescribeAutoScalingGroupsRequest describeAutoScalingGroupsRequest =
          new DescribeAutoScalingGroupsRequest().withAutoScalingGroupNames(autoScalingGroupName);
      tracker.trackASGCall("Describe Autoscaling Group");
      DescribeAutoScalingGroupsResult describeAutoScalingGroupsResult =
          closeableAmazonAutoScalingClient.getClient().describeAutoScalingGroups(describeAutoScalingGroupsRequest);
      if (!describeAutoScalingGroupsResult.getAutoScalingGroups().isEmpty()) {
        return describeAutoScalingGroupsResult.getAutoScalingGroups().get(0);
      }
    } catch (AmazonServiceException amazonServiceException) {
      handleAmazonServiceException(amazonServiceException);
    } catch (AmazonClientException amazonClientException) {
      handleAmazonClientException(amazonClientException);
    } catch (Exception e) {
      Exception sanitizeException = ExceptionMessageSanitizer.sanitizeException(e);
      log.error("Exception getAutoScalingGroup", sanitizeException);
      throw new InvalidRequestException(ExceptionUtils.getMessage(sanitizeException), sanitizeException);
    }
    return null;
  }

  @Override
  public LaunchConfiguration getLaunchConfiguration(
      AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails, String region, String launchConfigName) {
    encryptionService.decrypt(awsConfig, encryptionDetails, false);
    ExceptionMessageSanitizer.storeAllSecretsForSanitizing(awsConfig, encryptionDetails);
    try (CloseableAmazonWebServiceClient<AmazonAutoScalingClient> closeableAmazonAutoScalingClient =
             new CloseableAmazonWebServiceClient(getAmazonAutoScalingClient(Regions.fromName(region), awsConfig))) {
      tracker.trackASGCall("Describe Launch Configuration");
      return closeableAmazonAutoScalingClient.getClient()
          .describeLaunchConfigurations(
              new DescribeLaunchConfigurationsRequest().withLaunchConfigurationNames(launchConfigName))
          .getLaunchConfigurations()
          .stream()
          .findFirst()
          .orElse(null);
    } catch (AmazonServiceException amazonServiceException) {
      handleAmazonServiceException(amazonServiceException);
    } catch (AmazonClientException amazonClientException) {
      handleAmazonClientException(amazonClientException);
    } catch (Exception e) {
      Exception sanitizeException = ExceptionMessageSanitizer.sanitizeException(e);
      log.error("Exception getLaunchConfiguration", sanitizeException);
      throw new InvalidRequestException(ExceptionUtils.getMessage(sanitizeException), sanitizeException);
    }
    return null;
  }

  @Override
  public List<AutoScalingGroup> listAllAsgs(
      AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails, String region) {
    encryptionService.decrypt(awsConfig, encryptionDetails, false);
    ExceptionMessageSanitizer.storeAllSecretsForSanitizing(awsConfig, encryptionDetails);
    try (CloseableAmazonWebServiceClient<AmazonAutoScalingClient> closeableAmazonAutoScalingClient =
             new CloseableAmazonWebServiceClient(getAmazonAutoScalingClient(Regions.fromName(region), awsConfig))) {
      DescribeAutoScalingGroupsRequest describeAutoScalingGroupsRequest;
      DescribeAutoScalingGroupsResult describeAutoScalingGroupsResult;
      String nextToken = null;
      List<AutoScalingGroup> result = new ArrayList<>();
      do {
        describeAutoScalingGroupsRequest =
            new DescribeAutoScalingGroupsRequest().withMaxRecords(100).withNextToken(nextToken);
        tracker.trackASGCall("Describe Autoscaling Group");
        describeAutoScalingGroupsResult =
            closeableAmazonAutoScalingClient.getClient().describeAutoScalingGroups(describeAutoScalingGroupsRequest);
        result.addAll(describeAutoScalingGroupsResult.getAutoScalingGroups());
        nextToken = describeAutoScalingGroupsResult.getNextToken();
      } while (nextToken != null);
      return result;
    } catch (AmazonServiceException amazonServiceException) {
      handleAmazonServiceException(amazonServiceException);
    } catch (AmazonClientException amazonClientException) {
      handleAmazonClientException(amazonClientException);
    } catch (Exception e) {
      Exception sanitizeException = ExceptionMessageSanitizer.sanitizeException(e);
      log.error("Exception listAllAsgs", sanitizeException);
      throw new InvalidRequestException(ExceptionUtils.getMessage(sanitizeException), sanitizeException);
    }
    return emptyList();
  }

  @Override
  public void deleteLaunchConfig(
      AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails, String region, String autoScalingGroupName) {
    encryptionService.decrypt(awsConfig, encryptionDetails, false);
    ExceptionMessageSanitizer.storeAllSecretsForSanitizing(awsConfig, encryptionDetails);
    try (CloseableAmazonWebServiceClient<AmazonAutoScalingClient> closeableAmazonAutoScalingClient =
             new CloseableAmazonWebServiceClient(getAmazonAutoScalingClient(Regions.fromName(region), awsConfig))) {
      tracker.trackASGCall("Delete Launch Config");
      closeableAmazonAutoScalingClient.getClient().deleteLaunchConfiguration(
          new DeleteLaunchConfigurationRequest().withLaunchConfigurationName(autoScalingGroupName));
    } catch (AmazonServiceException amazonServiceException) {
      handleAmazonServiceException(amazonServiceException);
    } catch (AmazonClientException amazonClientException) {
      handleAmazonClientException(amazonClientException);
    } catch (Exception e) {
      Exception sanitizeException = ExceptionMessageSanitizer.sanitizeException(e);
      log.error("Exception deleteLaunchConfig", sanitizeException);
      throw new InvalidRequestException(ExceptionUtils.getMessage(sanitizeException), sanitizeException);
    }
  }

  @Override
  public CreateLaunchConfigurationResult createLaunchConfiguration(AwsConfig awsConfig,
      List<EncryptedDataDetail> encryptionDetails, String region,
      CreateLaunchConfigurationRequest createLaunchConfigurationRequest) {
    encryptionService.decrypt(awsConfig, encryptionDetails, false);
    ExceptionMessageSanitizer.storeAllSecretsForSanitizing(awsConfig, encryptionDetails);
    try (CloseableAmazonWebServiceClient<AmazonAutoScalingClient> closeableAmazonAutoScalingClient =
             new CloseableAmazonWebServiceClient(getAmazonAutoScalingClient(Regions.fromName(region), awsConfig))) {
      tracker.trackASGCall("Create Launch Config");
      return closeableAmazonAutoScalingClient.getClient().createLaunchConfiguration(createLaunchConfigurationRequest);
    } catch (AmazonServiceException amazonServiceException) {
      handleAmazonServiceException(amazonServiceException);
    } catch (AmazonClientException amazonClientException) {
      handleAmazonClientException(amazonClientException);
    } catch (Exception e) {
      Exception sanitizeException = ExceptionMessageSanitizer.sanitizeException(e);
      log.error("Exception createLaunchConfiguration", sanitizeException);
      throw new InvalidRequestException(ExceptionUtils.getMessage(sanitizeException), sanitizeException);
    }
    return new CreateLaunchConfigurationResult();
  }

  @Override
  public CreateAutoScalingGroupResult createAutoScalingGroup(AwsConfig awsConfig,
      List<EncryptedDataDetail> encryptionDetails, String region,
      CreateAutoScalingGroupRequest createAutoScalingGroupRequest, LogCallback logCallback) {
    AmazonAutoScalingClient amazonAutoScalingClient = null;
    encryptionService.decrypt(awsConfig, encryptionDetails, false);
    ExceptionMessageSanitizer.storeAllSecretsForSanitizing(awsConfig, encryptionDetails);
    try (CloseableAmazonWebServiceClient<AmazonAutoScalingClient> closeableAmazonAutoScalingClient =
             new CloseableAmazonWebServiceClient(getAmazonAutoScalingClient(Regions.fromName(region), awsConfig))) {
      amazonAutoScalingClient = closeableAmazonAutoScalingClient.getClient();
      tracker.trackASGCall("Create Autoscaling Group");
      return amazonAutoScalingClient.createAutoScalingGroup(createAutoScalingGroupRequest);
    } catch (AmazonServiceException amazonServiceException) {
      if (amazonAutoScalingClient != null && logCallback != null) {
        describeAutoScalingGroupActivities(amazonAutoScalingClient,
            createAutoScalingGroupRequest.getAutoScalingGroupName(), new HashSet<>(), logCallback, true);
      }
      handleAmazonServiceException(amazonServiceException);
    } catch (AmazonClientException amazonClientException) {
      handleAmazonClientException(amazonClientException);
    } catch (Exception e) {
      Exception sanitizeException = ExceptionMessageSanitizer.sanitizeException(e);
      log.error("Exception createAutoScalingGroup", sanitizeException);
      throw new InvalidRequestException(ExceptionUtils.getMessage(sanitizeException), sanitizeException);
    }
    return new CreateAutoScalingGroupResult();
  }

  @Override
  public void deleteAutoScalingGroups(AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails, String region,
      List<AutoScalingGroup> autoScalingGroups, LogCallback callback) {
    AmazonAutoScalingClient amazonAutoScalingClient = null;
    encryptionService.decrypt(awsConfig, encryptionDetails, false);
    ExceptionMessageSanitizer.storeAllSecretsForSanitizing(awsConfig, encryptionDetails);
    try (CloseableAmazonWebServiceClient<AmazonAutoScalingClient> closeableAmazonAutoScalingClient =
             new CloseableAmazonWebServiceClient(getAmazonAutoScalingClient(Regions.fromName(region), awsConfig))) {
      amazonAutoScalingClient = closeableAmazonAutoScalingClient.getClient();
      AmazonAutoScalingClient finalAmazonAutoScalingClient = amazonAutoScalingClient;
      autoScalingGroups.forEach(autoScalingGroup -> {
        try {
          tracker.trackASGCall("Delete Autoscaling Group");
          finalAmazonAutoScalingClient.deleteAutoScalingGroup(
              new DeleteAutoScalingGroupRequest().withAutoScalingGroupName(autoScalingGroup.getAutoScalingGroupName()));
          waitForAutoScalingGroupToBeDeleted(finalAmazonAutoScalingClient, autoScalingGroup, callback);
        } catch (Exception ignored) {
          describeAutoScalingGroupActivities(finalAmazonAutoScalingClient, autoScalingGroup.getAutoScalingGroupName(),
              new HashSet<>(), callback, true);
          log.warn("Failed to delete ASG: [{}] [{}]", autoScalingGroup.getAutoScalingGroupName(), ignored);
        }
        if (isNotEmpty(autoScalingGroup.getLaunchConfigurationName())) {
          try {
            tracker.trackASGCall("Delete Launch Config");
            finalAmazonAutoScalingClient.deleteLaunchConfiguration(
                new DeleteLaunchConfigurationRequest().withLaunchConfigurationName(
                    autoScalingGroup.getLaunchConfigurationName()));
          } catch (Exception ignored) {
            describeAutoScalingGroupActivities(finalAmazonAutoScalingClient, autoScalingGroup.getAutoScalingGroupName(),
                new HashSet<>(), callback, true);
            log.warn("Failed to delete ASG: [{}] [{}]", autoScalingGroup.getAutoScalingGroupName(), ignored);
          }
        }
      });
    } catch (AmazonServiceException amazonServiceException) {
      handleAmazonServiceException(amazonServiceException);
    } catch (AmazonClientException amazonClientException) {
      handleAmazonClientException(amazonClientException);
    } catch (Exception e) {
      Exception sanitizeException = ExceptionMessageSanitizer.sanitizeException(e);
      log.error("Exception deleteAutoScalingGroups", sanitizeException);
      throw new InvalidRequestException(ExceptionUtils.getMessage(sanitizeException), sanitizeException);
    }
  }

  @Override
  public Map<String, Integer> getDesiredCapacitiesOfAsgs(
      AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails, String region, List<String> asgs) {
    encryptionService.decrypt(awsConfig, encryptionDetails, false);
    ExceptionMessageSanitizer.storeAllSecretsForSanitizing(awsConfig, encryptionDetails);
    try (CloseableAmazonWebServiceClient<AmazonAutoScalingClient> closeableAmazonAutoScalingClient =
             new CloseableAmazonWebServiceClient(getAmazonAutoScalingClient(Regions.fromName(region), awsConfig))) {
      Map<String, Integer> capacities = new HashMap<>();
      String nextToken = null;
      DescribeAutoScalingGroupsRequest request = null;
      DescribeAutoScalingGroupsResult result = null;
      do {
        request = new DescribeAutoScalingGroupsRequest().withAutoScalingGroupNames(asgs).withNextToken(nextToken);
        tracker.trackASGCall("Describe ASGs");
        result = closeableAmazonAutoScalingClient.getClient().describeAutoScalingGroups(request);
        List<AutoScalingGroup> groups = result.getAutoScalingGroups();
        groups.forEach(group -> { capacities.put(group.getAutoScalingGroupName(), group.getDesiredCapacity()); });
        nextToken = result.getNextToken();
      } while (nextToken != null);
      return capacities;
    } catch (AmazonServiceException amazonServiceException) {
      handleAmazonServiceException(amazonServiceException);
    } catch (AmazonClientException amazonClientException) {
      handleAmazonClientException(amazonClientException);
    } catch (Exception e) {
      Exception sanitizeException = ExceptionMessageSanitizer.sanitizeException(e);
      log.error("Exception getDesiredCapacitiesOfAsgs", sanitizeException);
      throw new InvalidRequestException(ExceptionUtils.getMessage(sanitizeException), sanitizeException);
    }
    return emptyMap();
  }

  @Override
  public void setAutoScalingGroupCapacityAndWaitForInstancesReadyState(AwsConfig awsConfig,
      List<EncryptedDataDetail> encryptionDetails, String region, String autoScalingGroupName, Integer desiredCapacity,
      ExecutionLogCallback logCallback, Integer autoScalingSteadyStateTimeout,
      boolean amiInServiceHealthyStateFFEnabled) {
    encryptionService.decrypt(awsConfig, encryptionDetails, false);
    ExceptionMessageSanitizer.storeAllSecretsForSanitizing(awsConfig, encryptionDetails);
    AmazonAutoScalingClient amazonAutoScalingClient = null;
    try (CloseableAmazonWebServiceClient<AmazonAutoScalingClient> closeableAmazonAutoScalingClient =
             new CloseableAmazonWebServiceClient(getAmazonAutoScalingClient(Regions.fromName(region), awsConfig))) {
      amazonAutoScalingClient = closeableAmazonAutoScalingClient.getClient();
      logCallback.saveExecutionLog(
          format("Set AutoScaling Group: [%s] desired capacity to [%s]", autoScalingGroupName, desiredCapacity));
      tracker.trackASGCall("Set ASG Desired Capacity");
      amazonAutoScalingClient.setDesiredCapacity(new SetDesiredCapacityRequest()
                                                     .withAutoScalingGroupName(autoScalingGroupName)
                                                     .withDesiredCapacity(desiredCapacity));
      logCallback.saveExecutionLog("Successfully set desired capacity");
      if (amiInServiceHealthyStateFFEnabled) {
        waitForAllInstancesToBeInServiceHealthyState(awsConfig, encryptionDetails, region, autoScalingGroupName,
            desiredCapacity, logCallback, autoScalingSteadyStateTimeout);
      } else {
        waitForAllInstancesToBeReady(awsConfig, encryptionDetails, region, autoScalingGroupName, desiredCapacity,
            logCallback, autoScalingSteadyStateTimeout);
      }
    } catch (AmazonServiceException amazonServiceException) {
      describeAutoScalingGroupActivities(
          amazonAutoScalingClient, autoScalingGroupName, new HashSet<>(), logCallback, true);
      handleAmazonServiceException(amazonServiceException);
    } catch (AmazonClientException amazonClientException) {
      handleAmazonClientException(amazonClientException);
    } catch (Exception e) {
      Exception sanitizeException = ExceptionMessageSanitizer.sanitizeException(e);
      log.error("Exception setAutoScalingGroupCapacityAndWaitForInstancesReadyState", sanitizeException);
      throw new InvalidRequestException(ExceptionUtils.getMessage(sanitizeException), sanitizeException);
    }
  }

  @Override
  public void setMinInstancesForAsg(AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails, String region,
      String autoScalingGroupName, int minCapacity, ExecutionLogCallback logCallback) {
    encryptionService.decrypt(awsConfig, encryptionDetails, false);
    ExceptionMessageSanitizer.storeAllSecretsForSanitizing(awsConfig, encryptionDetails);
    try (CloseableAmazonWebServiceClient<AmazonAutoScalingClient> closeableAmazonAutoScalingClient =
             new CloseableAmazonWebServiceClient(getAmazonAutoScalingClient(Regions.fromName(region), awsConfig))) {
      if (isEmpty(autoScalingGroupName)) {
        return;
      }
      tracker.trackASGCall("Describe Autoscaling Group");
      DescribeAutoScalingGroupsRequest request =
          new DescribeAutoScalingGroupsRequest().withAutoScalingGroupNames(autoScalingGroupName);
      DescribeAutoScalingGroupsResult result =
          closeableAmazonAutoScalingClient.getClient().describeAutoScalingGroups(request);
      List<AutoScalingGroup> autoScalingGroups = result.getAutoScalingGroups();
      if (isEmpty(autoScalingGroups)) {
        return;
      }
      logCallback.saveExecutionLog(
          format("Setting min capacity of Asg[%s] to [%d]", autoScalingGroupName, minCapacity));
      UpdateAutoScalingGroupRequest updateAutoScalingGroupRequest =
          new UpdateAutoScalingGroupRequest().withAutoScalingGroupName(autoScalingGroupName).withMinSize(minCapacity);
      tracker.trackASGCall("Update Autoscaling Group");
      closeableAmazonAutoScalingClient.getClient().updateAutoScalingGroup(updateAutoScalingGroupRequest);
    } catch (AmazonServiceException amazonServiceException) {
      logCallback.saveExecutionLog(
          format("Exception: [%s] while setting Asg limits", ExceptionUtils.getMessage(amazonServiceException)));
      handleAmazonServiceException(amazonServiceException);
    } catch (AmazonClientException amazonClientException) {
      handleAmazonClientException(amazonClientException);
    } catch (Exception e) {
      Exception sanitizeException = ExceptionMessageSanitizer.sanitizeException(e);
      log.error("Exception setMinInstancesForAsg", sanitizeException);
      throw new InvalidRequestException(ExceptionUtils.getMessage(sanitizeException), sanitizeException);
    }
  }

  @Override
  public void setAutoScalingGroupLimits(AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails, String region,
      String autoScalingGroupName, Integer desiredCapacity, ExecutionLogCallback logCallback) {
    encryptionService.decrypt(awsConfig, encryptionDetails, false);
    ExceptionMessageSanitizer.storeAllSecretsForSanitizing(awsConfig, encryptionDetails);
    try (CloseableAmazonWebServiceClient<AmazonAutoScalingClient> closeableAmazonAutoScalingClient =
             new CloseableAmazonWebServiceClient(getAmazonAutoScalingClient(Regions.fromName(region), awsConfig))) {
      DescribeAutoScalingGroupsRequest request =
          new DescribeAutoScalingGroupsRequest().withAutoScalingGroupNames(autoScalingGroupName);
      tracker.trackASGCall("Describe Autoscaling Groups");
      DescribeAutoScalingGroupsResult result =
          closeableAmazonAutoScalingClient.getClient().describeAutoScalingGroups(request);
      List<AutoScalingGroup> autoScalingGroups = result.getAutoScalingGroups();
      if (isEmpty(autoScalingGroups)) {
        return;
      }
      AutoScalingGroup autoScalingGroup = autoScalingGroups.get(0);
      if (desiredCapacity < autoScalingGroup.getMinSize()) {
        logCallback.saveExecutionLog(
            format("Autoscaling group: [%s] has min Size: [%d] > desired capacity: [%d]. Updating",
                autoScalingGroupName, autoScalingGroup.getMinSize(), desiredCapacity));
        UpdateAutoScalingGroupRequest updateAutoScalingGroupRequest =
            new UpdateAutoScalingGroupRequest()
                .withAutoScalingGroupName(autoScalingGroupName)
                .withMinSize(desiredCapacity);
        tracker.trackASGCall("Update Autoscaling Group");
        closeableAmazonAutoScalingClient.getClient().updateAutoScalingGroup(updateAutoScalingGroupRequest);
      } else if (desiredCapacity > autoScalingGroup.getMaxSize()) {
        logCallback.saveExecutionLog(
            format("Autoscaling group: [%s] has max Size: [%d] < desired capacity: [%d]. Updating",
                autoScalingGroupName, autoScalingGroup.getMaxSize(), desiredCapacity));
        UpdateAutoScalingGroupRequest updateAutoScalingGroupRequest =
            new UpdateAutoScalingGroupRequest()
                .withAutoScalingGroupName(autoScalingGroupName)
                .withMaxSize(desiredCapacity);
        tracker.trackASGCall("Update Autoscaling Group");
        closeableAmazonAutoScalingClient.getClient().updateAutoScalingGroup(updateAutoScalingGroupRequest);
      }
    } catch (AmazonServiceException amazonServiceException) {
      logCallback.saveExecutionLog(
          format("Exception: [%s] while setting Asg limits", ExceptionUtils.getMessage(amazonServiceException)));
      handleAmazonServiceException(amazonServiceException);
    } catch (AmazonClientException amazonClientException) {
      handleAmazonClientException(amazonClientException);
    } catch (Exception e) {
      Exception sanitizeException = ExceptionMessageSanitizer.sanitizeException(e);
      log.error("Exception setAutoScalingGroupLimits", sanitizeException);
      throw new InvalidRequestException(ExceptionUtils.getMessage(sanitizeException), sanitizeException);
    }
  }

  private void waitForAutoScalingGroupToBeDeleted(
      AmazonAutoScalingClient amazonAutoScalingClient, AutoScalingGroup autoScalingGroup, LogCallback callback) {
    try {
      HTimeLimiter.callInterruptible21(timeLimiter, Duration.ofMinutes(1), () -> {
        Set<String> completedActivities = new HashSet<>();
        while (true) {
          tracker.trackASGCall("Describe Autoscaling Group");
          DescribeAutoScalingGroupsResult result = amazonAutoScalingClient.describeAutoScalingGroups(
              new DescribeAutoScalingGroupsRequest().withAutoScalingGroupNames(
                  autoScalingGroup.getAutoScalingGroupName()));
          if (result.getAutoScalingGroups().isEmpty()) {
            return true;
          }
          describeAutoScalingGroupActivities(amazonAutoScalingClient, autoScalingGroup.getAutoScalingGroupName(),
              completedActivities, callback, false);
          sleep(ofSeconds(AUTOSCALING_REQUEST_STATUS_CHECK_INTERVAL));
        }
      });
    } catch (UncheckedTimeoutException e) {
      throw new WingsException(INIT_TIMEOUT)
          .addParam("message", "Timed out waiting for autoscaling group to be deleted");
    } catch (WingsException e) {
      throw(WingsException) ExceptionMessageSanitizer.sanitizeException(e);
    } catch (Exception e) {
      throw new InvalidRequestException(
          "Error while waiting for autoscaling group to be deleted", ExceptionMessageSanitizer.sanitizeException(e));
    }
  }

  private void describeAutoScalingGroupActivities(AmazonAutoScalingClient amazonAutoScalingClient,
      String autoScalingGroupName, Set<String> completedActivities, LogCallback callback, boolean withCause) {
    if (callback == null) {
      log.info("Not describing autoScalingGroupActivities for {} since logCallback is null", completedActivities);
      return;
    }
    try {
      tracker.trackASGCall("Describe ASG activities");
      DescribeScalingActivitiesResult activitiesResult = amazonAutoScalingClient.describeScalingActivities(
          new DescribeScalingActivitiesRequest().withAutoScalingGroupName(autoScalingGroupName));
      List<Activity> activities = activitiesResult.getActivities();
      if (activities != null && activities.size() > 0) {
        activities.stream()
            .filter(activity -> !completedActivities.contains(activity.getActivityId()))
            .forEach(activity -> {
              String activityId = activity.getActivityId();
              String details = activity.getDetails();
              Integer progress = activity.getProgress();
              String activityDescription = activity.getDescription();
              String statuscode = activity.getStatusCode();
              String logStatement =
                  format("AutoScalingGroup [%s] activity [%s] progress [%d percent] , statuscode [%s]  details [%s]",
                      autoScalingGroupName, activityDescription, progress, statuscode, details);
              if (withCause) {
                String cause = activity.getCause();
                logStatement = format(logStatement + " cause [%s]", cause);
              }

              callback.saveExecutionLog(logStatement);
              if (progress == 100) {
                completedActivities.add(activityId);
              }
            });
      }
    } catch (Exception e) {
      log.warn("Failed to describe autoScalingGroup for [%s] %s", autoScalingGroupName,
          ExceptionMessageSanitizer.sanitizeException(e));
    }
  }

  private void waitForAllInstancesToBeReady(AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails,
      String region, String autoScalingGroupName, Integer desiredCount, ExecutionLogCallback logCallback,
      Integer autoScalingSteadyStateTimeout) {
    try (CloseableAmazonWebServiceClient<AmazonAutoScalingClient> closeableAmazonAutoScalingClient =
             new CloseableAmazonWebServiceClient(getAmazonAutoScalingClient(Regions.fromName(region), awsConfig))) {
      HTimeLimiter.callInterruptible21(timeLimiter, Duration.ofMinutes(autoScalingSteadyStateTimeout), () -> {
        Set<String> completedActivities = new HashSet<>();
        while (true) {
          List<String> instanceIds =
              listAutoScalingGroupInstanceIds(awsConfig, encryptionDetails, region, autoScalingGroupName, false);
          describeAutoScalingGroupActivities(closeableAmazonAutoScalingClient.getClient(), autoScalingGroupName,
              completedActivities, logCallback, false);
          if (instanceIds.size() == desiredCount
              && allInstanceInReadyStateWithRetry(awsConfig, encryptionDetails, region, instanceIds, logCallback)) {
            return true;
          }
          sleep(ofSeconds(AUTOSCALING_REQUEST_STATUS_CHECK_INTERVAL));
        }
      });
    } catch (UncheckedTimeoutException e) {
      logCallback.saveExecutionLog("Request timeout. AutoScaling group couldn't reach steady state", ERROR);
      throw new WingsException(INIT_TIMEOUT)
          .addParam("message", "Timed out waiting for all instances to be in running state");
    } catch (WingsException e) {
      throw(WingsException) ExceptionMessageSanitizer.sanitizeException(e);
    } catch (Exception e) {
      throw new InvalidRequestException("Error while waiting for all instances to be in running state",
          ExceptionMessageSanitizer.sanitizeException(e));
    }
    logCallback.saveExecutionLog("AutoScaling group reached steady state");
  }

  private void waitForAllInstancesToBeInServiceHealthyState(AwsConfig awsConfig,
      List<EncryptedDataDetail> encryptionDetails, String region, String autoScalingGroupName, Integer desiredCount,
      ExecutionLogCallback logCallback, Integer autoScalingSteadyStateTimeout) {
    try (CloseableAmazonWebServiceClient<AmazonAutoScalingClient> closeableAmazonAutoScalingClient =
             new CloseableAmazonWebServiceClient(getAmazonAutoScalingClient(Regions.fromName(region), awsConfig))) {
      HTimeLimiter.callInterruptible21(timeLimiter, Duration.ofMinutes(autoScalingSteadyStateTimeout), () -> {
        Set<String> completedActivities = new HashSet<>();
        while (true) {
          List<com.amazonaws.services.autoscaling.model.Instance> instances =
              listAutoScalingGroupModelInstances(awsConfig, encryptionDetails, region, autoScalingGroupName, false);
          describeAutoScalingGroupActivities(closeableAmazonAutoScalingClient.getClient(), autoScalingGroupName,
              completedActivities, logCallback, false);
          if (instances.size() == desiredCount
              && allInstanceInServiceHealthyStateWithRetry(
                  awsConfig, encryptionDetails, region, instances, logCallback)) {
            return true;
          }
          sleep(ofSeconds(AUTOSCALING_REQUEST_STATUS_CHECK_INTERVAL));
        }
      });
    } catch (UncheckedTimeoutException e) {
      logCallback.saveExecutionLog("Request timeout. Auto Scaling Group couldn't reach steady state", ERROR);
      throw new WingsException(INIT_TIMEOUT)
          .addParam("message", "Timed out waiting for Auto Scaling Group to be in InService and Healthy state");
    } catch (WingsException e) {
      throw(WingsException) ExceptionMessageSanitizer.sanitizeException(e);
    } catch (Exception e) {
      throw new InvalidRequestException("Error while waiting for all instances to be in InService and Healthy state",
          ExceptionMessageSanitizer.sanitizeException(e));
    }
    logCallback.saveExecutionLog("Auto Scaling Group reached steady state");
  }

  @VisibleForTesting
  boolean allInstanceInReadyStateWithRetry(AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails,
      String region, List<String> instanceIds, ExecutionLogCallback logCallback) throws InterruptedRuntimeException {
    try {
      return allInstanceInReadyState(awsConfig, encryptionDetails, region, instanceIds, logCallback);
    } catch (Exception ex) {
      log.warn("Aws List Ec2 instance call failed. Retrying once after 15 seconds");
    }
    sleep(ofSeconds(AUTOSCALING_REQUEST_STATUS_CHECK_INTERVAL));
    return allInstanceInReadyState(awsConfig, encryptionDetails, region, instanceIds, logCallback);
  }

  @VisibleForTesting
  boolean allInstanceInReadyState(AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails, String region,
      List<String> instanceIds, ExecutionLogCallback logCallback) {
    List<Instance> instances =
        awsEc2HelperServiceDelegate.listEc2Instances(awsConfig, encryptionDetails, instanceIds, region, false);
    boolean allRunning = instanceIds.isEmpty()
        || instances.stream().allMatch(instance -> instance.getState().getName().equals("running"));
    if (!allRunning) {
      Map<String, Long> instanceStateCountMap =
          instances.stream().collect(groupingBy(instance -> instance.getState().getName(), counting()));
      logCallback.saveExecutionLog("Waiting for instances to be in running state. "
          + Joiner.on(",").withKeyValueSeparator("=").join(instanceStateCountMap));
    }
    return allRunning;
  }

  @VisibleForTesting
  boolean allInstanceInServiceHealthyStateWithRetry(AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails,
      String region, List<com.amazonaws.services.autoscaling.model.Instance> instances,
      ExecutionLogCallback logCallback) throws InterruptedRuntimeException {
    try {
      return allInstanceInServiceHealthyState(awsConfig, encryptionDetails, region, instances, logCallback);
    } catch (Exception ex) {
      log.warn(
          "Exception while waiting for instances to be in InService and Healthy state. Retrying once after 15 seconds");
    }
    sleep(ofSeconds(AUTOSCALING_REQUEST_STATUS_CHECK_INTERVAL));
    return allInstanceInServiceHealthyState(awsConfig, encryptionDetails, region, instances, logCallback);
  }

  @VisibleForTesting
  boolean allInstanceInServiceHealthyState(AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails,
      String region, List<com.amazonaws.services.autoscaling.model.Instance> instances,
      ExecutionLogCallback logCallback) {
    boolean allInServiceHealthy = instances.isEmpty()
        || instances.stream().allMatch(instance
            -> instance.getLifecycleState() != null && instance.getLifecycleState().equals("InService")
                && instance.getHealthStatus() != null && instance.getHealthStatus().equals("Healthy"));
    if (!allInServiceHealthy) {
      Map<String, Long> instanceStateCountMap =
          instances.stream().collect(groupingBy(instance -> instance.getLifecycleState(), counting()));
      logCallback.saveExecutionLog("Waiting for instances to be in InService and Healthy state. "
          + Joiner.on(",").withKeyValueSeparator("=").join(instanceStateCountMap));
    }
    return allInServiceHealthy;
  }

  @Override
  public void registerAsgWithTargetGroups(AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails,
      String region, String asgName, List<String> targetGroupARNs, ExecutionLogCallback logCallback) {
    try (CloseableAmazonWebServiceClient<AmazonAutoScalingClient> closeableAmazonAutoScalingClient =
             new CloseableAmazonWebServiceClient(getAmazonAutoScalingClient(Regions.fromName(region), awsConfig))) {
      if (isEmpty(targetGroupARNs)) {
        logCallback.saveExecutionLog(format("No Target Groups to attach to: [%s]", asgName));
        return;
      }
      tracker.trackASGCall("Attach LB Target Groups");
      AttachLoadBalancerTargetGroupsRequest request =
          new AttachLoadBalancerTargetGroupsRequest().withAutoScalingGroupName(asgName).withTargetGroupARNs(
              targetGroupARNs);
      closeableAmazonAutoScalingClient.getClient().attachLoadBalancerTargetGroups(request);
    } catch (AmazonServiceException amazonServiceException) {
      handleAmazonServiceException(amazonServiceException);
    } catch (AmazonClientException amazonClientException) {
      handleAmazonClientException(amazonClientException);
    } catch (Exception e) {
      Exception sanitizeException = ExceptionMessageSanitizer.sanitizeException(e);
      log.error("Exception registerAsgWithTargetGroups", sanitizeException);
      throw new InvalidRequestException(ExceptionUtils.getMessage(sanitizeException), sanitizeException);
    }
  }

  @Override
  public void registerAsgWithClassicLBs(AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails, String region,
      String asgName, List<String> classicLBs, ExecutionLogCallback logCallback) {
    try (CloseableAmazonWebServiceClient<AmazonAutoScalingClient> closeableAmazonAutoScalingClient =
             new CloseableAmazonWebServiceClient(getAmazonAutoScalingClient(Regions.fromName(region), awsConfig))) {
      if (isEmpty(classicLBs)) {
        logCallback.saveExecutionLog(format("No classic load balancers to attach to: [%s]", asgName));
        return;
      }
      AttachLoadBalancersRequest request =
          new AttachLoadBalancersRequest().withAutoScalingGroupName(asgName).withLoadBalancerNames(classicLBs);
      tracker.trackASGCall("Attach Load Balancers");
      closeableAmazonAutoScalingClient.getClient().attachLoadBalancers(request);
    } catch (AmazonServiceException amazonServiceException) {
      handleAmazonServiceException(amazonServiceException);
    } catch (AmazonClientException amazonClientException) {
      handleAmazonClientException(amazonClientException);
    } catch (Exception e) {
      Exception sanitizeException = ExceptionMessageSanitizer.sanitizeException(e);
      log.error("Exception registerAsgWithClassicLBs", sanitizeException);
      throw new InvalidRequestException(ExceptionUtils.getMessage(sanitizeException), sanitizeException);
    }
  }

  @Override
  public void deRegisterAsgWithTargetGroups(AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails,
      String region, String asgName, List<String> targetGroupARNs, ExecutionLogCallback logCallback) {
    try (CloseableAmazonWebServiceClient<AmazonAutoScalingClient> closeableAmazonAutoScalingClient =
             new CloseableAmazonWebServiceClient(getAmazonAutoScalingClient(Regions.fromName(region), awsConfig))) {
      if (isEmpty(targetGroupARNs)) {
        logCallback.saveExecutionLog(format("No Target Groups to attach to: [%s]", asgName));
        return;
      }
      DetachLoadBalancerTargetGroupsRequest request =
          new DetachLoadBalancerTargetGroupsRequest().withAutoScalingGroupName(asgName).withTargetGroupARNs(
              targetGroupARNs);
      tracker.trackASGCall("Detach Load Balancer Target Groups");
      closeableAmazonAutoScalingClient.getClient().detachLoadBalancerTargetGroups(request);
    } catch (AmazonServiceException amazonServiceException) {
      handleAmazonServiceException(amazonServiceException);
    } catch (AmazonClientException amazonClientException) {
      handleAmazonClientException(amazonClientException);
    } catch (Exception e) {
      Exception sanitizeException = ExceptionMessageSanitizer.sanitizeException(e);
      log.error("Exception deRegisterAsgWithTargetGroups", sanitizeException);
      throw new InvalidRequestException(ExceptionUtils.getMessage(sanitizeException), sanitizeException);
    }
  }

  @Override
  public void deRegisterAsgWithClassicLBs(AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails,
      String region, String asgName, List<String> classicLBs, ExecutionLogCallback logCallback) {
    try (CloseableAmazonWebServiceClient<AmazonAutoScalingClient> closeableAmazonAutoScalingClient =
             new CloseableAmazonWebServiceClient(getAmazonAutoScalingClient(Regions.fromName(region), awsConfig))) {
      if (isEmpty(classicLBs)) {
        logCallback.saveExecutionLog(format("No classic load balancers to detach to: [%s]", asgName));
        return;
      }
      tracker.trackASGCall("Detach Load Balancers");
      DetachLoadBalancersRequest request =
          new DetachLoadBalancersRequest().withAutoScalingGroupName(asgName).withLoadBalancerNames(classicLBs);
      closeableAmazonAutoScalingClient.getClient().detachLoadBalancers(request);
    } catch (AmazonServiceException amazonServiceException) {
      handleAmazonServiceException(amazonServiceException);
    } catch (AmazonClientException amazonClientException) {
      handleAmazonClientException(amazonClientException);
    } catch (Exception e) {
      Exception sanitizeException = ExceptionMessageSanitizer.sanitizeException(e);
      log.error("Exception deRegisterAsgWithClassicLBs", sanitizeException);
      throw new InvalidRequestException(ExceptionUtils.getMessage(sanitizeException), sanitizeException);
    }
  }

  @Override
  public AwsAsgGetRunningCountData getCurrentlyRunningInstanceCount(
      AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails, String region, String infraMappingId) {
    try {
      String asgName = DEFAULT_AMI_ASG_NAME;
      int asgMin = DEFAULT_AMI_ASG_MIN_INSTANCES;
      int asgMax = DEFAULT_AMI_ASG_MAX_INSTANCES;
      int asgDesired = DEFAULT_AMI_ASG_DESIRED_INSTANCES;
      List<AutoScalingGroup> groups = listAllAsgs(awsConfig, encryptionDetails, region);
      if (isNotEmpty(groups)) {
        Optional<AutoScalingGroup> first =
            groups.stream()
                .filter(group
                    -> group.getTags().stream().anyMatch(
                        tagDescription -> isHarnessManagedTag(infraMappingId, tagDescription)))
                .filter(group -> group.getDesiredCapacity() > 0)
                .max(Comparator.comparing(AutoScalingGroup::getCreatedTime));
        if (first.isPresent()) {
          AutoScalingGroup autoScalingGroup = first.get();
          asgName = autoScalingGroup.getAutoScalingGroupName();
          asgMin = autoScalingGroup.getMinSize();
          asgMax = autoScalingGroup.getMaxSize();
          asgDesired = autoScalingGroup.getDesiredCapacity();
        }
      }
      return AwsAsgGetRunningCountData.builder()
          .asgMin(asgMin)
          .asgMax(asgMax)
          .asgDesired(asgDesired)
          .asgName(asgName)
          .build();
    } catch (AmazonServiceException amazonServiceException) {
      handleAmazonServiceException(amazonServiceException);
    } catch (AmazonClientException amazonClientException) {
      handleAmazonClientException(amazonClientException);
    }
    return null;
  }

  private String getJSONForScalingPolicy(ScalingPolicy scalingPolicy, ExecutionLogCallback logCallback) {
    if (scalingPolicy == null) {
      return EMPTY;
    }
    ObjectMapper mapper = new ObjectMapper();
    try {
      return mapper.writeValueAsString(scalingPolicy);
    } catch (Exception ex) {
      Exception sanitizeException = ExceptionMessageSanitizer.sanitizeException(ex);
      String errorMessage = format("Exception: [%s] while extracting policy JSON for scaling policy: [%s]. Ignored.",
          sanitizeException.getMessage(), scalingPolicy.getPolicyARN());
      log.error(errorMessage, sanitizeException);
      logCallback.saveExecutionLog(errorMessage);
      return EMPTY;
    }
  }

  private String getJSONForScheduledAction(
      ScheduledUpdateGroupAction scheduledAction, ExecutionLogCallback logCallback) {
    if (scheduledAction == null) {
      return EMPTY;
    }
    ObjectMapper mapper = new ObjectMapper();
    try {
      return mapper.writeValueAsString(scheduledAction);
    } catch (Exception ex) {
      Exception sanitizeException = ExceptionMessageSanitizer.sanitizeException(ex);
      String errorMessage = format("Exception: [%s] while extracting JSON for scheduled action: [%s]. Ignored.",
          sanitizeException.getMessage(), scheduledAction.getScheduledActionARN());
      log.error(errorMessage, sanitizeException);
      logCallback.saveExecutionLog(errorMessage);
      return EMPTY;
    }
  }

  private List<ScalingPolicy> listAllScalingPoliciesOfAsg(
      AmazonAutoScalingClient amazonAutoScalingClient, String asgName) {
    List<ScalingPolicy> scalingPolicies = newArrayList();
    String nextToken = null;
    DescribePoliciesRequest request = null;
    DescribePoliciesResult result = null;
    do {
      request = new DescribePoliciesRequest().withAutoScalingGroupName(asgName).withNextToken(nextToken);
      tracker.trackASGCall("Describe ASG Policies");
      result = amazonAutoScalingClient.describePolicies(request);
      if (isNotEmpty(result.getScalingPolicies())) {
        scalingPolicies.addAll(result.getScalingPolicies());
      }
      nextToken = result.getNextToken();
    } while (nextToken != null);
    return scalingPolicies;
  }

  @Override
  public List<String> getScalingPolicyJSONs(AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails,
      String region, String asgName, ExecutionLogCallback logCallback) {
    encryptionService.decrypt(awsConfig, encryptionDetails, false);
    ExceptionMessageSanitizer.storeAllSecretsForSanitizing(awsConfig, encryptionDetails);
    try (CloseableAmazonWebServiceClient<AmazonAutoScalingClient> closeableAmazonAutoScalingClient =
             new CloseableAmazonWebServiceClient(getAmazonAutoScalingClient(Regions.fromName(region), awsConfig))) {
      logCallback.saveExecutionLog(format("Extracting scaling policy JSONs from: [%s]", asgName));
      List<ScalingPolicy> scalingPolicies =
          listAllScalingPoliciesOfAsg(closeableAmazonAutoScalingClient.getClient(), asgName);
      if (isEmpty(scalingPolicies)) {
        logCallback.saveExecutionLog("No policies found");
        return emptyList();
      }
      List<String> scalingPolicyJSONs = newArrayList();
      scalingPolicies.forEach(scalingPolicy -> {
        logCallback.saveExecutionLog(format("Found scaling policy: [%s]", scalingPolicy.getPolicyARN()));
        scalingPolicyJSONs.add(getJSONForScalingPolicy(scalingPolicy, logCallback));
      });
      return scalingPolicyJSONs;
    } catch (AmazonServiceException amazonServiceException) {
      handleAmazonServiceException(amazonServiceException);
    } catch (AmazonClientException amazonClientException) {
      handleAmazonClientException(amazonClientException);
    } catch (Exception e) {
      Exception sanitizeException = ExceptionMessageSanitizer.sanitizeException(e);
      log.error("Exception getScalingPolicyJSONs", sanitizeException);
      throw new InvalidRequestException(ExceptionUtils.getMessage(sanitizeException), sanitizeException);
    }
    return emptyList();
  }

  @Override
  public void clearAllScalingPoliciesForAsg(AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails,
      String region, String asgName, ExecutionLogCallback logCallback) {
    encryptionService.decrypt(awsConfig, encryptionDetails, false);
    ExceptionMessageSanitizer.storeAllSecretsForSanitizing(awsConfig, encryptionDetails);
    try (CloseableAmazonWebServiceClient<AmazonAutoScalingClient> closeableAmazonAutoScalingClient =
             new CloseableAmazonWebServiceClient(getAmazonAutoScalingClient(Regions.fromName(region), awsConfig))) {
      logCallback.saveExecutionLog(format("Clearing away all scaling policies for Asg: [%s]", asgName));
      List<ScalingPolicy> scalingPolicies =
          listAllScalingPoliciesOfAsg(closeableAmazonAutoScalingClient.getClient(), asgName);
      if (isEmpty(scalingPolicies)) {
        logCallback.saveExecutionLog("No policies found");
        return;
      }
      scalingPolicies.forEach(scalingPolicy -> {
        logCallback.saveExecutionLog(format("Found scaling policy: [%s]. Deleting it.", scalingPolicy.getPolicyARN()));
        DeletePolicyRequest deletePolicyRequest =
            new DeletePolicyRequest().withAutoScalingGroupName(asgName).withPolicyName(scalingPolicy.getPolicyARN());
        tracker.trackASGCall("Delete ASG Policies");
        closeableAmazonAutoScalingClient.getClient().deletePolicy(deletePolicyRequest);
      });
    } catch (AmazonServiceException amazonServiceException) {
      handleAmazonServiceException(amazonServiceException);
    } catch (AmazonClientException amazonClientException) {
      handleAmazonClientException(amazonClientException);
    } catch (Exception e) {
      Exception sanitizeException = ExceptionMessageSanitizer.sanitizeException(e);
      log.error("Exception clearAllScalingPoliciesForAsg", sanitizeException);
      throw new InvalidRequestException(ExceptionUtils.getMessage(sanitizeException), sanitizeException);
    }
  }

  private ScalingPolicy getScalingPolicyFromJSON(String json, ExecutionLogCallback logCallback) {
    ObjectMapper mapper = new ObjectMapper();
    try {
      return mapper.readValue(json, ScalingPolicy.class);
    } catch (Exception ex) {
      Exception sanitizeException = ExceptionMessageSanitizer.sanitizeException(ex);
      String errorMessage = format("Exception: [%s] while deserializing cached JSON", sanitizeException.getMessage());
      logCallback.saveExecutionLog(errorMessage);
      log.error(errorMessage, sanitizeException);
      return null;
    }
  }

  private ScheduledUpdateGroupAction getScheduledActionFromJSON(String json, ExecutionLogCallback logCallback) {
    ObjectMapper mapper = new ObjectMapper();
    try {
      return mapper.readValue(json, ScheduledUpdateGroupAction.class);
    } catch (Exception ex) {
      Exception sanitizeException = ExceptionMessageSanitizer.sanitizeException(ex);
      String errorMessage = format("Exception: [%s] while deserializing cached JSON", sanitizeException.getMessage());
      logCallback.saveExecutionLog(errorMessage);
      log.error(errorMessage, sanitizeException);
      return null;
    }
  }

  @Override
  public void addUpdateTagAutoScalingGroup(AwsConfig awsConfig, List<EncryptedDataDetail> encryptedDataDetails,
      String asgName, String region, String tagKey, String tagValue, ExecutionLogCallback executionLogCallback) {
    encryptionService.decrypt(awsConfig, encryptedDataDetails, false);
    ExceptionMessageSanitizer.storeAllSecretsForSanitizing(awsConfig, encryptedDataDetails);
    try (CloseableAmazonWebServiceClient<AmazonAutoScalingClient> closeableAmazonAutoScalingClient =
             new CloseableAmazonWebServiceClient(getAmazonAutoScalingClient(Regions.fromName(region), awsConfig))) {
      executionLogCallback.saveExecutionLog(
          format("Tagging ASG with name: [%s] with tag: [%s] -> [%s]", asgName, tagKey, tagValue));
      tracker.trackASGCall("Add Update Tags to Autoscaling group");
      CreateOrUpdateTagsRequest createOrUpdateTagsRequest = new CreateOrUpdateTagsRequest();
      Tag blueVersionTag = new Tag();
      blueVersionTag.withKey(tagKey)
          .withValue(tagValue)
          .withPropagateAtLaunch(true)
          .withResourceId(asgName)
          .withResourceType("auto-scaling-group");
      createOrUpdateTagsRequest.withTags(blueVersionTag);
      closeableAmazonAutoScalingClient.getClient().createOrUpdateTags(createOrUpdateTagsRequest);
    } catch (AmazonServiceException amazonServiceException) {
      handleAmazonServiceException(amazonServiceException);
    } catch (AmazonClientException amazonClientException) {
      handleAmazonClientException(amazonClientException);
    } catch (Exception e) {
      Exception sanitizeException = ExceptionMessageSanitizer.sanitizeException(e);
      log.error("Exception addUpdateTagAutoScalingGroup", sanitizeException);
      throw new InvalidRequestException(ExceptionUtils.getMessage(sanitizeException), sanitizeException);
    }
  }

  @Override
  public void attachScalingPoliciesToAsg(AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails,
      String region, String asgName, List<String> scalingPolicyJSONs, ExecutionLogCallback logCallback) {
    encryptionService.decrypt(awsConfig, encryptionDetails, false);
    ExceptionMessageSanitizer.storeAllSecretsForSanitizing(awsConfig, encryptionDetails);
    try (CloseableAmazonWebServiceClient<AmazonAutoScalingClient> closeableAmazonAutoScalingClient =
             new CloseableAmazonWebServiceClient(getAmazonAutoScalingClient(Regions.fromName(region), awsConfig))) {
      logCallback.saveExecutionLog(format("Attaching scaling policies to Asg: [%s]", asgName));
      if (isEmpty(scalingPolicyJSONs)) {
        logCallback.saveExecutionLog("No policy to attach");
        return;
      }
      scalingPolicyJSONs.forEach(scalingPolicyJSON -> {
        if (isNotEmpty(scalingPolicyJSON)) {
          ScalingPolicy scalingPolicy = getScalingPolicyFromJSON(scalingPolicyJSON, logCallback);
          if (scalingPolicy != null) {
            logCallback.saveExecutionLog(
                format("Found policy: [%s]. Attaching to: [%s]", scalingPolicy.getPolicyName(), asgName));
            PutScalingPolicyRequest putScalingPolicyRequest =
                new PutScalingPolicyRequest()
                    .withAutoScalingGroupName(asgName)
                    .withPolicyName(scalingPolicy.getPolicyName())
                    .withPolicyType(scalingPolicy.getPolicyType())
                    .withAdjustmentType(scalingPolicy.getAdjustmentType())
                    .withCooldown(scalingPolicy.getCooldown())
                    .withEstimatedInstanceWarmup(scalingPolicy.getEstimatedInstanceWarmup())
                    .withMetricAggregationType(scalingPolicy.getMetricAggregationType())
                    .withMinAdjustmentMagnitude(scalingPolicy.getMinAdjustmentMagnitude())
                    .withMinAdjustmentStep(scalingPolicy.getMinAdjustmentStep())
                    .withScalingAdjustment(scalingPolicy.getScalingAdjustment())
                    .withStepAdjustments(scalingPolicy.getStepAdjustments())
                    .withTargetTrackingConfiguration(scalingPolicy.getTargetTrackingConfiguration());
            tracker.trackASGCall("Put ASG Scaling Policy");
            PutScalingPolicyResult putScalingPolicyResult =
                closeableAmazonAutoScalingClient.getClient().putScalingPolicy(putScalingPolicyRequest);
            logCallback.saveExecutionLog(
                format("Created policy with Arn: [%s]", putScalingPolicyResult.getPolicyARN()));
          }
        }
      });
    } catch (AmazonServiceException amazonServiceException) {
      handleAmazonServiceException(amazonServiceException);
    } catch (AmazonClientException amazonClientException) {
      handleAmazonClientException(amazonClientException);
    } catch (Exception e) {
      Exception sanitizeException = ExceptionMessageSanitizer.sanitizeException(e);
      log.error("Exception attachScalingPoliciesToAsg", sanitizeException);
      throw new InvalidRequestException(ExceptionUtils.getMessage(sanitizeException), sanitizeException);
    }
  }

  public void attachScheduledActionsToAsg(AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails,
      String region, String asgName, List<String> scheduledActionJSONs, ExecutionLogCallback logCallback) {
    encryptionService.decrypt(awsConfig, encryptionDetails, false);
    ExceptionMessageSanitizer.storeAllSecretsForSanitizing(awsConfig, encryptionDetails);
    try (CloseableAmazonWebServiceClient<AmazonAutoScalingClient> closeableAmazonAutoScalingClient =
             new CloseableAmazonWebServiceClient(getAmazonAutoScalingClient(Regions.fromName(region), awsConfig))) {
      logCallback.saveExecutionLog(format("Attaching scheduled actions to Asg: [%s]", asgName));
      if (isEmpty(scheduledActionJSONs)) {
        logCallback.saveExecutionLog("No scheduled action to attach");
        return;
      }
      scheduledActionJSONs.forEach(scheduledActionJSON -> {
        if (isNotEmpty(scheduledActionJSON)) {
          ScheduledUpdateGroupAction scheduledAction = getScheduledActionFromJSON(scheduledActionJSON, logCallback);
          logCallback.saveExecutionLog(format(
              "Found scheduled action : [%s]. Attaching to: [%s]", scheduledAction.getScheduledActionName(), asgName));
          PutScheduledUpdateGroupActionRequest putScheduledUpdateGroupActionRequest =
              new PutScheduledUpdateGroupActionRequest()
                  .withAutoScalingGroupName(asgName)
                  .withScheduledActionName(scheduledAction.getScheduledActionName())
                  .withTime(scheduledAction.getTime())
                  .withStartTime(scheduledAction.getStartTime())
                  .withEndTime(scheduledAction.getEndTime())
                  .withRecurrence(scheduledAction.getRecurrence())
                  .withMinSize(scheduledAction.getMinSize())
                  .withMaxSize(scheduledAction.getMaxSize())
                  .withDesiredCapacity(scheduledAction.getDesiredCapacity());
          tracker.trackASGCall("Put ASG Scheduled Action");
          closeableAmazonAutoScalingClient.getClient().putScheduledUpdateGroupAction(
              putScheduledUpdateGroupActionRequest);
          logCallback.saveExecutionLog(
              format("Created scheduled action with Arn: [%s]", scheduledAction.getScheduledActionARN()));
        }
      });
    } catch (AmazonServiceException amazonServiceException) {
      handleAmazonServiceException(amazonServiceException);
    } catch (AmazonClientException amazonClientException) {
      handleAmazonClientException(amazonClientException);
    } catch (Exception e) {
      Exception sanitizeException = ExceptionMessageSanitizer.sanitizeException(e);
      log.error("Exception attachScheduledActionsToAsg", sanitizeException);
      throw new InvalidRequestException(ExceptionUtils.getMessage(sanitizeException), sanitizeException);
    }
  }

  @Override
  public List<String> getScheduledActionJSONs(AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails,
      String region, String asgName, ExecutionLogCallback logCallback) {
    encryptionService.decrypt(awsConfig, encryptionDetails, false);
    ExceptionMessageSanitizer.storeAllSecretsForSanitizing(awsConfig, encryptionDetails);
    List<ScheduledUpdateGroupAction> scheduledUpdateGroupActionList = new ArrayList<>();
    List<String> scheduledUpdateGroupActionJSONList = new ArrayList<>();
    logCallback.saveExecutionLog(format("Extracting scheduled actions from: [%s]", asgName));
    try (CloseableAmazonWebServiceClient<AmazonAutoScalingClient> closeableAmazonAutoScalingClient =
             new CloseableAmazonWebServiceClient(getAmazonAutoScalingClient(Regions.fromName(region), awsConfig))) {
      scheduledUpdateGroupActionList = listScheduledActions(closeableAmazonAutoScalingClient.getClient(), asgName);

      if (isEmpty(scheduledUpdateGroupActionList)) {
        logCallback.saveExecutionLog("No scheduled actions found");
      } else {
        scheduledUpdateGroupActionList.forEach(scheduledUpdateGroupAction -> {
          logCallback.saveExecutionLog(
              format("Found scheduled action: [%s]", scheduledUpdateGroupAction.getScheduledActionName()));
          scheduledUpdateGroupActionJSONList.add(getJSONForScheduledAction(scheduledUpdateGroupAction, logCallback));
        });
      }

    } catch (AmazonServiceException amazonServiceException) {
      handleAmazonServiceException(amazonServiceException);
    } catch (AmazonClientException amazonClientException) {
      handleAmazonClientException(amazonClientException);
    } catch (Exception e) {
      Exception sanitizeException = ExceptionMessageSanitizer.sanitizeException(e);
      log.error("Exception getScheduledActionJSONs", sanitizeException);
      throw new InvalidRequestException(ExceptionUtils.getMessage(sanitizeException), sanitizeException);
    }

    return scheduledUpdateGroupActionJSONList;
  }

  private List<ScheduledUpdateGroupAction> listScheduledActions(
      AmazonAutoScalingClient amazonAutoScalingClient, String asgName) {
    List<ScheduledUpdateGroupAction> scheduledUpdateGroupActionList = new ArrayList<>();
    String nextToken;
    tracker.trackASGCall("Describe Scheduled Actions");
    do {
      DescribeScheduledActionsRequest describeScheduledActionsRequest =
          new DescribeScheduledActionsRequest().withAutoScalingGroupName(asgName).withMaxRecords(
              MAX_SCHEDULED_ACTIONS_BATCH_SIZE);
      DescribeScheduledActionsResult describeScheduledActionsResult =
          amazonAutoScalingClient.describeScheduledActions(describeScheduledActionsRequest);

      scheduledUpdateGroupActionList.addAll(describeScheduledActionsResult.getScheduledUpdateGroupActions());

      nextToken = describeScheduledActionsResult.getNextToken();
    } while (nextToken != null);

    return scheduledUpdateGroupActionList;
  }

  public void clearAllScheduledActionsForAsg(AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails,
      String region, String asgName, ExecutionLogCallback logCallback) {
    encryptionService.decrypt(awsConfig, encryptionDetails, false);
    ExceptionMessageSanitizer.storeAllSecretsForSanitizing(awsConfig, encryptionDetails);
    try (CloseableAmazonWebServiceClient<AmazonAutoScalingClient> closeableAmazonAutoScalingClient =
             new CloseableAmazonWebServiceClient(getAmazonAutoScalingClient(Regions.fromName(region), awsConfig))) {
      logCallback.saveExecutionLog(format("Clearing away all scheduled actions for Asg: [%s]", asgName));
      List<ScheduledUpdateGroupAction> scheduledUpdateGroupActions =
          listScheduledActions(closeableAmazonAutoScalingClient.getClient(), asgName);

      if (isEmpty(scheduledUpdateGroupActions)) {
        logCallback.saveExecutionLog("No scheduled actions found");
        return;
      }

      scheduledUpdateGroupActions.forEach(scheduledUpdateGroupAction -> {
        logCallback.saveExecutionLog(
            format("Found scheduled action: [%s]. Deleting it.", scheduledUpdateGroupAction.getScheduledActionName()));
        DeleteScheduledActionRequest deleteScheduledActionRequest =
            new DeleteScheduledActionRequest().withAutoScalingGroupName(asgName).withScheduledActionName(
                scheduledUpdateGroupAction.getScheduledActionName());
        tracker.trackASGCall("Delete ASG Scheduled Actions");
        closeableAmazonAutoScalingClient.getClient().deleteScheduledAction(deleteScheduledActionRequest);
      });
    } catch (AmazonServiceException amazonServiceException) {
      handleAmazonServiceException(amazonServiceException);
    } catch (AmazonClientException amazonClientException) {
      handleAmazonClientException(amazonClientException);
    } catch (Exception e) {
      Exception sanitizeException = ExceptionMessageSanitizer.sanitizeException(e);
      log.error("Exception clearAllScheduledActionsForAsg", sanitizeException);
      throw new InvalidRequestException(ExceptionUtils.getMessage(sanitizeException), sanitizeException);
    }
  }
}
