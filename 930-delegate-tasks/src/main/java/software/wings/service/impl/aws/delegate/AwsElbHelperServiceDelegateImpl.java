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
import static io.harness.delegate.task.aws.AwsElbListenerRuleData.AwsElbListenerRuleDataBuilder;
import static io.harness.eraro.ErrorCode.INIT_TIMEOUT;
import static io.harness.exception.WingsException.USER;
import static io.harness.logging.LogLevel.ERROR;
import static io.harness.threading.Morpheus.sleep;

import static software.wings.service.impl.aws.model.AwsConstants.FORWARD_LISTENER_ACTION;
import static software.wings.service.impl.aws.model.AwsConstants.MAX_TRAFFIC_SHIFT_WEIGHT;
import static software.wings.service.impl.aws.model.AwsConstants.MIN_TRAFFIC_SHIFT_WEIGHT;

import static com.amazonaws.services.elasticloadbalancingv2.model.ActionTypeEnum.Forward;
import static java.lang.String.format;
import static java.time.Duration.ofSeconds;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.concurrent.HTimeLimiter;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.task.aws.AwsElbListener;
import io.harness.delegate.task.aws.AwsElbListener.AwsElbListenerBuilder;
import io.harness.delegate.task.aws.AwsElbListenerRuleData;
import io.harness.delegate.task.aws.AwsLoadBalancerDetails;
import io.harness.delegate.task.aws.LbDetailsForAlbTrafficShift;
import io.harness.delegate.task.aws.LoadBalancerDetailsForBGDeployment;
import io.harness.eraro.ErrorCode;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.logging.LogLevel;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.AwsConfig;
import software.wings.beans.command.ExecutionLogCallback;
import software.wings.service.impl.aws.client.CloseableAmazonWebServiceClient;
import software.wings.service.intfc.aws.delegate.AwsAsgHelperServiceDelegate;
import software.wings.service.intfc.aws.delegate.AwsElbHelperServiceDelegate;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancingClientBuilder;
import com.amazonaws.services.elasticloadbalancing.model.DescribeInstanceHealthRequest;
import com.amazonaws.services.elasticloadbalancing.model.DescribeInstanceHealthResult;
import com.amazonaws.services.elasticloadbalancing.model.InstanceState;
import com.amazonaws.services.elasticloadbalancing.model.LoadBalancerDescription;
import com.amazonaws.services.elasticloadbalancingv2.AmazonElasticLoadBalancing;
import com.amazonaws.services.elasticloadbalancingv2.AmazonElasticLoadBalancingClient;
import com.amazonaws.services.elasticloadbalancingv2.model.Action;
import com.amazonaws.services.elasticloadbalancingv2.model.ActionTypeEnum;
import com.amazonaws.services.elasticloadbalancingv2.model.CreateListenerRequest;
import com.amazonaws.services.elasticloadbalancingv2.model.CreateListenerResult;
import com.amazonaws.services.elasticloadbalancingv2.model.CreateTargetGroupRequest;
import com.amazonaws.services.elasticloadbalancingv2.model.CreateTargetGroupResult;
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeListenersRequest;
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeListenersResult;
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeLoadBalancersRequest;
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeLoadBalancersResult;
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeRulesRequest;
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeRulesResult;
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeTargetGroupsRequest;
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeTargetGroupsResult;
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeTargetHealthRequest;
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeTargetHealthResult;
import com.amazonaws.services.elasticloadbalancingv2.model.ForwardActionConfig;
import com.amazonaws.services.elasticloadbalancingv2.model.Listener;
import com.amazonaws.services.elasticloadbalancingv2.model.LoadBalancer;
import com.amazonaws.services.elasticloadbalancingv2.model.ModifyListenerRequest;
import com.amazonaws.services.elasticloadbalancingv2.model.ModifyRuleRequest;
import com.amazonaws.services.elasticloadbalancingv2.model.Rule;
import com.amazonaws.services.elasticloadbalancingv2.model.TargetGroup;
import com.amazonaws.services.elasticloadbalancingv2.model.TargetGroupNotFoundException;
import com.amazonaws.services.elasticloadbalancingv2.model.TargetGroupTuple;
import com.amazonaws.services.elasticloadbalancingv2.model.TargetHealthDescription;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.TimeLimiter;
import com.google.common.util.concurrent.UncheckedTimeoutException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@Singleton
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
@Slf4j
@OwnedBy(CDP)
public class AwsElbHelperServiceDelegateImpl
    extends AwsHelperServiceDelegateBase implements AwsElbHelperServiceDelegate {
  public static final String ALB = "application";
  public static final String NLB = "network";

  @Inject private AwsAsgHelperServiceDelegate awsAsgHelperServiceDelegate;
  @Inject private TimeLimiter timeLimiter;

  @VisibleForTesting
  com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancingClient getClassicElbClient(
      Regions region, AwsConfig awsConfig) {
    AmazonElasticLoadBalancingClientBuilder builder =
        AmazonElasticLoadBalancingClientBuilder.standard().withRegion(region);
    attachCredentialsAndBackoffPolicy(builder, awsConfig);
    return (com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancingClient) builder.build();
  }

  @VisibleForTesting
  public AmazonElasticLoadBalancingClient getAmazonElasticLoadBalancingClientV2(Regions region, AwsConfig awsConfig) {
    com.amazonaws.services.elasticloadbalancingv2.AmazonElasticLoadBalancingClientBuilder builder =
        com.amazonaws.services.elasticloadbalancingv2.AmazonElasticLoadBalancingClientBuilder.standard().withRegion(
            region);
    attachCredentialsAndBackoffPolicy(builder, awsConfig);
    return (AmazonElasticLoadBalancingClient) builder.build();
  }

  @Override
  public List<String> listClassicLoadBalancers(
      AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails, String region) {
    encryptionService.decrypt(awsConfig, encryptionDetails, false);
    ExceptionMessageSanitizer.storeAllSecretsForSanitizing(awsConfig, encryptionDetails);
    try (CloseableAmazonWebServiceClient<com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancingClient>
             closeableAmazonElasticLoadBalancingClient =
                 new CloseableAmazonWebServiceClient(getClassicElbClient(Regions.fromName(region), awsConfig))) {
      List<String> result = new ArrayList<>();
      String nextMarker = null;
      do {
        com.amazonaws.services.elasticloadbalancing.model.DescribeLoadBalancersRequest describeLoadBalancersRequest =
            new com.amazonaws.services.elasticloadbalancing.model.DescribeLoadBalancersRequest()
                .withPageSize(400)
                .withMarker(nextMarker);
        com.amazonaws.services.elasticloadbalancing.model.DescribeLoadBalancersResult describeLoadBalancersResult =
            closeableAmazonElasticLoadBalancingClient.getClient().describeLoadBalancers(describeLoadBalancersRequest);
        tracker.trackClassicELBCall("Get LB descriptions");
        result.addAll(describeLoadBalancersResult.getLoadBalancerDescriptions()
                          .stream()
                          .map(LoadBalancerDescription::getLoadBalancerName)
                          .collect(toList()));
        nextMarker = describeLoadBalancersResult.getNextMarker();
      } while (nextMarker != null);
      return result;
    } catch (AmazonServiceException amazonServiceException) {
      handleAmazonServiceException(amazonServiceException);
    } catch (AmazonClientException amazonClientException) {
      handleAmazonClientException(amazonClientException);
    } catch (Exception e) {
      Exception sanitizeException = ExceptionMessageSanitizer.sanitizeException(e);
      log.error("Exception listClassicLoadBalancers", sanitizeException);
      throw new InvalidRequestException(ExceptionUtils.getMessage(sanitizeException), sanitizeException);
    }
    return emptyList();
  }

  @Override
  public List<AwsLoadBalancerDetails> listApplicationLoadBalancerDetails(
      AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails, String region) {
    return generateLoadBalancersList(awsConfig, encryptionDetails, region, new HashSet<>(Arrays.asList(ALB)));
  }

  @Override
  public List<AwsLoadBalancerDetails> listNetworkLoadBalancerDetails(
      AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails, String region) {
    return generateLoadBalancersList(awsConfig, encryptionDetails, region, new HashSet<>(Arrays.asList(NLB)));
  }

  @Override
  public List<AwsLoadBalancerDetails> listElasticLoadBalancerDetails(
      AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails, String region) {
    return generateLoadBalancersList(awsConfig, encryptionDetails, region, new HashSet<>(Arrays.asList(ALB, NLB)));
  }

  private List<AwsLoadBalancerDetails> generateLoadBalancersList(
      AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails, String region, Set<String> neededTypes) {
    encryptionService.decrypt(awsConfig, encryptionDetails, false);
    ExceptionMessageSanitizer.storeAllSecretsForSanitizing(awsConfig, encryptionDetails);
    try (CloseableAmazonWebServiceClient<AmazonElasticLoadBalancingClient> closeableAmazonElasticLoadBalancingClient =
             new CloseableAmazonWebServiceClient(
                 getAmazonElasticLoadBalancingClientV2(Regions.fromName(region), awsConfig))) {
      List<AwsLoadBalancerDetails> result = new ArrayList<>();
      String nextMarker = null;
      do {
        DescribeLoadBalancersRequest describeLoadBalancersRequest =
            new DescribeLoadBalancersRequest().withMarker(nextMarker).withPageSize(400);
        tracker.trackELBCall("Describe Load Balancers");
        DescribeLoadBalancersResult describeLoadBalancersResult =
            closeableAmazonElasticLoadBalancingClient.getClient().describeLoadBalancers(describeLoadBalancersRequest);
        result.addAll(describeLoadBalancersResult.getLoadBalancers()
                          .stream()
                          .filter(loadBalancer -> isNeededLoadBalancer(loadBalancer, neededTypes))
                          .map(loadBalancer
                              -> AwsLoadBalancerDetails.builder()
                                     .arn(loadBalancer.getLoadBalancerArn())
                                     .name(loadBalancer.getLoadBalancerName())
                                     .type(loadBalancer.getType())
                                     .scheme(loadBalancer.getScheme())
                                     .dNSName(loadBalancer.getDNSName())
                                     .ipAddressType(loadBalancer.getIpAddressType())
                                     .vpcId(loadBalancer.getVpcId())
                                     .build())
                          .collect(toList()));
        nextMarker = describeLoadBalancersResult.getNextMarker();
      } while (nextMarker != null);
      return result;
    } catch (AmazonServiceException amazonServiceException) {
      handleAmazonServiceException(amazonServiceException);
    } catch (AmazonClientException amazonClientException) {
      handleAmazonClientException(amazonClientException);
    } catch (Exception e) {
      Exception sanitizeException = ExceptionMessageSanitizer.sanitizeException(e);
      log.error("Exception generateLoadBalancersList", sanitizeException);
      throw new InvalidRequestException(ExceptionUtils.getMessage(sanitizeException), sanitizeException);
    }
    return emptyList();
  }

  private boolean isNeededLoadBalancer(LoadBalancer loadBalancer, Set<String> typesNeeded) {
    String type = loadBalancer.getType();
    return typesNeeded.contains(type.toLowerCase());
  }

  @Override
  public Map<String, String> listTargetGroupsForAlb(
      AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails, String region, String loadBalancerName) {
    encryptionService.decrypt(awsConfig, encryptionDetails, false);
    ExceptionMessageSanitizer.storeAllSecretsForSanitizing(awsConfig, encryptionDetails);
    try (CloseableAmazonWebServiceClient<AmazonElasticLoadBalancingClient> closeableAmazonElasticLoadBalancingClient =
             new CloseableAmazonWebServiceClient(
                 getAmazonElasticLoadBalancingClientV2(Regions.fromName(region), awsConfig))) {
      String loadBalancerArn = null;
      if (isNotBlank(loadBalancerName)) {
        DescribeLoadBalancersRequest request = new DescribeLoadBalancersRequest();
        request.withNames(loadBalancerName);
        tracker.trackELBCall("Describe Load Balancers");
        loadBalancerArn = closeableAmazonElasticLoadBalancingClient.getClient()
                              .describeLoadBalancers(request)
                              .getLoadBalancers()
                              .get(0)
                              .getLoadBalancerArn();
      }
      Map<String, String> result = new HashMap<>();
      String nextMarker = null;
      do {
        DescribeTargetGroupsRequest describeTargetGroupsRequest =
            new DescribeTargetGroupsRequest().withMarker(nextMarker).withPageSize(400);
        if (loadBalancerArn != null) {
          describeTargetGroupsRequest.withLoadBalancerArn(loadBalancerArn);
        }
        tracker.trackELBCall("Describe Target Groups");
        DescribeTargetGroupsResult describeTargetGroupsResult =
            closeableAmazonElasticLoadBalancingClient.getClient().describeTargetGroups(describeTargetGroupsRequest);
        describeTargetGroupsResult.getTargetGroups().forEach(
            group -> result.put(group.getTargetGroupArn(), group.getTargetGroupName()));
        nextMarker = describeTargetGroupsResult.getNextMarker();
      } while (nextMarker != null);
      return result;
    } catch (AmazonServiceException amazonServiceException) {
      handleAmazonServiceException(amazonServiceException);
    } catch (AmazonClientException amazonClientException) {
      handleAmazonClientException(amazonClientException);
    } catch (Exception e) {
      Exception sanitizeException = ExceptionMessageSanitizer.sanitizeException(e);
      log.error("Exception listTargetGroupsForAlb", sanitizeException);
      throw new InvalidRequestException(ExceptionUtils.getMessage(sanitizeException), sanitizeException);
    }
    return emptyMap();
  }

  @Override
  public Optional<TargetGroup> getTargetGroup(
      AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails, String region, String targetGroupArn) {
    encryptionService.decrypt(awsConfig, encryptionDetails, false);
    ExceptionMessageSanitizer.storeAllSecretsForSanitizing(awsConfig, encryptionDetails);
    try (CloseableAmazonWebServiceClient<AmazonElasticLoadBalancingClient> closeableAmazonElasticLoadBalancingClient =
             new CloseableAmazonWebServiceClient(
                 getAmazonElasticLoadBalancingClientV2(Regions.fromName(region), awsConfig))) {
      DescribeTargetGroupsRequest describeTargetGroupsRequest =
          new DescribeTargetGroupsRequest().withTargetGroupArns(targetGroupArn);
      tracker.trackClassicELBCall("Describe Target Group");
      DescribeTargetGroupsResult describeTargetGroupsResult =
          closeableAmazonElasticLoadBalancingClient.getClient().describeTargetGroups(describeTargetGroupsRequest);

      if (EmptyPredicate.isNotEmpty(describeTargetGroupsResult.getTargetGroups())) {
        if (describeTargetGroupsResult.getTargetGroups().get(0).getTargetGroupArn().equalsIgnoreCase(targetGroupArn)) {
          return Optional.of(describeTargetGroupsResult.getTargetGroups().get(0));
        }
      }
    } catch (AmazonServiceException amazonServiceException) {
      handleAmazonServiceException(amazonServiceException);
    } catch (AmazonClientException amazonClientException) {
      handleAmazonClientException(amazonClientException);
    } catch (Exception e) {
      Exception sanitizeException = ExceptionMessageSanitizer.sanitizeException(e);
      log.error("Exception getTargetGroup", sanitizeException);
      throw new InvalidRequestException(ExceptionUtils.getMessage(sanitizeException), sanitizeException);
    }

    return Optional.empty();
  }

  @Override
  public Optional<TargetGroup> getTargetGroupByName(
      AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails, String region, String targetGroupName) {
    encryptionService.decrypt(awsConfig, encryptionDetails, false);
    ExceptionMessageSanitizer.storeAllSecretsForSanitizing(awsConfig, encryptionDetails);
    try (CloseableAmazonWebServiceClient<AmazonElasticLoadBalancingClient> closeableAmazonElasticLoadBalancingClient =
             new CloseableAmazonWebServiceClient(
                 getAmazonElasticLoadBalancingClientV2(Regions.fromName(region), awsConfig))) {
      DescribeTargetGroupsRequest describeTargetGroupsRequest =
          new DescribeTargetGroupsRequest().withNames(targetGroupName);
      tracker.trackELBCall("Describe Target Group");
      DescribeTargetGroupsResult describeTargetGroupsResult =
          closeableAmazonElasticLoadBalancingClient.getClient().describeTargetGroups(describeTargetGroupsRequest);

      if (EmptyPredicate.isNotEmpty(describeTargetGroupsResult.getTargetGroups())) {
        return Optional.of(describeTargetGroupsResult.getTargetGroups().get(0));
      }
    } catch (AmazonServiceException amazonServiceException) {
      // Aws throws this exception if mentioned target group is not found
      if (amazonServiceException instanceof TargetGroupNotFoundException) {
        return Optional.empty();
      }
      handleAmazonServiceException(amazonServiceException);
    } catch (AmazonClientException amazonClientException) {
      handleAmazonClientException(amazonClientException);
    } catch (Exception e) {
      Exception sanitizeException = ExceptionMessageSanitizer.sanitizeException(e);
      log.error("Exception getTargetGroupByName", sanitizeException);
      throw new InvalidRequestException(ExceptionUtils.getMessage(sanitizeException), sanitizeException);
    }

    return Optional.empty();
  }

  @Override
  public Optional<LoadBalancer> getLoadBalancer(
      AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails, String region, String loadBalancerName) {
    encryptionService.decrypt(awsConfig, encryptionDetails, false);
    ExceptionMessageSanitizer.storeAllSecretsForSanitizing(awsConfig, encryptionDetails);
    try (CloseableAmazonWebServiceClient<AmazonElasticLoadBalancingClient> closeableAmazonElasticLoadBalancingClient =
             new CloseableAmazonWebServiceClient(
                 getAmazonElasticLoadBalancingClientV2(Regions.fromName(region), awsConfig))) {
      DescribeLoadBalancersRequest describeLoadBalancersRequest =
          new DescribeLoadBalancersRequest().withNames(loadBalancerName);
      tracker.trackELBCall("Desribe Load Balancer");
      DescribeLoadBalancersResult describeLoadBalancersResult =
          closeableAmazonElasticLoadBalancingClient.getClient().describeLoadBalancers(describeLoadBalancersRequest);

      if (EmptyPredicate.isNotEmpty(describeLoadBalancersResult.getLoadBalancers())) {
        return Optional.of(describeLoadBalancersResult.getLoadBalancers().get(0));
      }
    } catch (AmazonServiceException amazonServiceException) {
      handleAmazonServiceException(amazonServiceException);
    } catch (AmazonClientException amazonClientException) {
      handleAmazonClientException(amazonClientException);
    } catch (Exception e) {
      Exception sanitizeException = ExceptionMessageSanitizer.sanitizeException(e);
      log.error("Exception getLoadBalancer", sanitizeException);
      throw new InvalidRequestException(ExceptionUtils.getMessage(sanitizeException), sanitizeException);
    }

    return Optional.empty();
  }

  @Override
  public void waitForAsgInstancesToDeRegisterWithClassicLB(AwsConfig awsConfig,
      List<EncryptedDataDetail> encryptionDetails, String region, String classicLB, String asgName, int timeout,
      ExecutionLogCallback logCallback) {
    try (CloseableAmazonWebServiceClient<com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancingClient>
             closeableAmazonElasticLoadBalancingClient =
                 new CloseableAmazonWebServiceClient(getClassicElbClient(Regions.fromName(region), awsConfig))) {
      HTimeLimiter.callInterruptible21(timeLimiter, Duration.ofMinutes(timeout), () -> {
        List<String> instanceIds = awsAsgHelperServiceDelegate.listAutoScalingGroupInstanceIds(
            awsConfig, encryptionDetails, region, asgName, false);
        while (true) {
          if (allInstancesDeRegistered(
                  closeableAmazonElasticLoadBalancingClient.getClient(), instanceIds, classicLB, logCallback)) {
            logCallback.saveExecutionLog(format("All targets  de  registered for Asg: [%s]", asgName));
            return true;
          }
          sleep(ofSeconds(15));
        }
      });
    } catch (UncheckedTimeoutException e) {
      String errorMessage = format("Registration timed out for Asg: [%s]", asgName);
      logCallback.saveExecutionLog(errorMessage, ERROR);
      throw new WingsException(INIT_TIMEOUT).addParam("message", errorMessage);
    } catch (WingsException e) {
      throw(WingsException) ExceptionMessageSanitizer.sanitizeException(e);
    } catch (Exception e) {
      throw new InvalidRequestException(
          format("Registration timed out for Asg: [%s]", asgName), ExceptionMessageSanitizer.sanitizeException(e));
    }
  }

  @VisibleForTesting
  boolean allInstancesDeRegistered(
      com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancingClient amazonElasticLoadBalancingClient,
      List<String> instanceIds, String classicLB, ExecutionLogCallback logCallback) {
    if (isEmpty(instanceIds)) {
      return true;
    }
    DescribeInstanceHealthRequest request = new DescribeInstanceHealthRequest().withLoadBalancerName(classicLB);
    tracker.trackClassicELBCall("Describe Instance Health");
    DescribeInstanceHealthResult result = amazonElasticLoadBalancingClient.describeInstanceHealth(request);
    List<InstanceState> instances = result.getInstanceStates();
    if (isEmpty(instances)) {
      return true;
    }
    Set<String> instanceIdsInService =
        instances.stream().map(InstanceState::getInstanceId).filter(instanceIds::contains).collect(toSet());
    logCallback.saveExecutionLog(
        format("[%d] out of [%s] instances still registered", instanceIdsInService.size(), instanceIds.size()));
    return instanceIdsInService.size() == 0;
  }

  @Override
  public void waitForAsgInstancesToRegisterWithClassicLB(AwsConfig awsConfig,
      List<EncryptedDataDetail> encryptionDetails, String region, String classicLB, String asgName, int timeout,
      ExecutionLogCallback logCallback) {
    try (CloseableAmazonWebServiceClient<com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancingClient>
             closeableAmazonElasticLoadBalancingClient =
                 new CloseableAmazonWebServiceClient(getClassicElbClient(Regions.fromName(region), awsConfig))) {
      HTimeLimiter.callInterruptible21(timeLimiter, Duration.ofMinutes(timeout), () -> {
        List<String> instanceIds = awsAsgHelperServiceDelegate.listAutoScalingGroupInstanceIds(
            awsConfig, encryptionDetails, region, asgName, false);
        while (true) {
          if (allInstancesRegistered(
                  closeableAmazonElasticLoadBalancingClient.getClient(), instanceIds, classicLB, logCallback)) {
            logCallback.saveExecutionLog(format("All targets registered for Asg: [%s]", asgName));
            return true;
          }
          sleep(ofSeconds(15));
        }
      });
    } catch (UncheckedTimeoutException e) {
      String errorMessage = format("Registration timed out for Asg: [%s]", asgName);
      logCallback.saveExecutionLog(errorMessage, ERROR);
      throw new WingsException(INIT_TIMEOUT).addParam("message", errorMessage);
    } catch (WingsException e) {
      throw(WingsException) ExceptionMessageSanitizer.sanitizeException(e);
    } catch (Exception e) {
      throw new InvalidRequestException(
          format("Registration timed out for Asg: [%s]", asgName), ExceptionMessageSanitizer.sanitizeException(e));
    }
  }

  @VisibleForTesting
  boolean allInstancesRegistered(
      com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancingClient amazonElasticLoadBalancingClient,
      List<String> instanceIds, String classicLB, ExecutionLogCallback logCallback) {
    if (isEmpty(instanceIds)) {
      return true;
    }
    DescribeInstanceHealthRequest request = new DescribeInstanceHealthRequest().withLoadBalancerName(classicLB);
    tracker.trackClassicELBCall("Describe Instance Healths");
    DescribeInstanceHealthResult result = amazonElasticLoadBalancingClient.describeInstanceHealth(request);
    List<InstanceState> instances = result.getInstanceStates();
    if (isEmpty(instances)) {
      return false;
    }
    Set<String> instanceIdsInService = instances.stream()
                                           .filter(instance -> instance.getState().equalsIgnoreCase("InService"))
                                           .map(InstanceState::getInstanceId)
                                           .filter(instanceIds::contains)
                                           .collect(toSet());
    logCallback.saveExecutionLog(
        format("[%d] out of [%s] instances registered", instanceIdsInService.size(), instanceIds.size()));
    return instanceIdsInService.containsAll(instanceIds);
  }

  @Override
  public void waitForAsgInstancesToDeRegisterWithTargetGroup(AwsConfig awsConfig,
      List<EncryptedDataDetail> encryptionDetails, String region, String targetGroupArn, String asgName, int timeout,
      ExecutionLogCallback logCallback) {
    try (CloseableAmazonWebServiceClient<AmazonElasticLoadBalancingClient> closeableAmazonElasticLoadBalancingClient =
             new CloseableAmazonWebServiceClient(
                 getAmazonElasticLoadBalancingClientV2(Regions.fromName(region), awsConfig))) {
      HTimeLimiter.callInterruptible21(timeLimiter, Duration.ofMinutes(timeout), () -> {
        List<String> instanceIds = awsAsgHelperServiceDelegate.listAutoScalingGroupInstanceIds(
            awsConfig, encryptionDetails, region, asgName, false);
        while (true) {
          if (allTargetsDeRegistered(
                  closeableAmazonElasticLoadBalancingClient.getClient(), instanceIds, targetGroupArn, logCallback)) {
            logCallback.saveExecutionLog(format("All targets de-registered for Asg: [%s]", asgName));
            return true;
          }
          sleep(ofSeconds(15));
        }
      });
    } catch (UncheckedTimeoutException e) {
      String errorMessage = format("Deregistration timed out for Asg: [%s]", asgName);
      logCallback.saveExecutionLog(errorMessage, ERROR);
      throw new WingsException(INIT_TIMEOUT).addParam("message", errorMessage);
    } catch (WingsException e) {
      throw(WingsException) ExceptionMessageSanitizer.sanitizeException(e);
    } catch (Exception e) {
      throw new InvalidRequestException(
          format("Registration timed out for Asg: [%s]", asgName), ExceptionMessageSanitizer.sanitizeException(e));
    }
  }

  @VisibleForTesting
  boolean allTargetsDeRegistered(AmazonElasticLoadBalancingClient amazonElasticLoadBalancingClient,
      List<String> targetIds, String targetGroupARN, ExecutionLogCallback logCallback) {
    if (isEmpty(targetIds)) {
      return true;
    }
    DescribeTargetHealthRequest request = new DescribeTargetHealthRequest().withTargetGroupArn(targetGroupARN);
    tracker.trackELBCall("Describe Target Health");
    DescribeTargetHealthResult result = amazonElasticLoadBalancingClient.describeTargetHealth(request);
    List<TargetHealthDescription> healthDescriptions = result.getTargetHealthDescriptions();
    if (isEmpty(healthDescriptions)) {
      return true;
    }
    Set<String> instanceIdsStillRegistered = healthDescriptions.stream()
                                                 .map(description -> description.getTarget().getId())
                                                 .filter(targetIds::contains)
                                                 .collect(toSet());
    logCallback.saveExecutionLog(
        format("[%d] out of [%d] targets still registered", instanceIdsStillRegistered.size(), targetIds.size()));
    return instanceIdsStillRegistered.size() == 0;
  }

  @Override
  public void waitForAsgInstancesToRegisterWithTargetGroup(AwsConfig awsConfig,
      List<EncryptedDataDetail> encryptionDetails, String region, String targetGroupArn, String asgName, int timeout,
      ExecutionLogCallback logCallback) {
    try (CloseableAmazonWebServiceClient<AmazonElasticLoadBalancingClient> closeableAmazonElasticLoadBalancingClient =
             new CloseableAmazonWebServiceClient(
                 getAmazonElasticLoadBalancingClientV2(Regions.fromName(region), awsConfig))) {
      HTimeLimiter.callInterruptible21(timeLimiter, Duration.ofMinutes(timeout), () -> {
        while (true) {
          List<String> instanceIds = awsAsgHelperServiceDelegate.listAutoScalingGroupInstanceIds(
              awsConfig, encryptionDetails, region, asgName, false);
          if (allTargetsRegistered(
                  closeableAmazonElasticLoadBalancingClient.getClient(), instanceIds, targetGroupArn, logCallback)) {
            logCallback.saveExecutionLog(format("All targets registered for Asg: [%s]", asgName));
            return true;
          }
          sleep(ofSeconds(15));
        }
      });
    } catch (UncheckedTimeoutException e) {
      String errorMessage = format("Registration timed out for Asg: [%s]", asgName);
      logCallback.saveExecutionLog(errorMessage, ERROR);
      throw new WingsException(INIT_TIMEOUT).addParam("message", errorMessage);
    } catch (WingsException e) {
      throw(WingsException) ExceptionMessageSanitizer.sanitizeException(e);
    } catch (Exception e) {
      throw new InvalidRequestException(
          format("Registration timed out for Asg: [%s]", asgName), ExceptionMessageSanitizer.sanitizeException(e));
    }
  }

  @VisibleForTesting
  boolean allTargetsRegistered(AmazonElasticLoadBalancingClient amazonElasticLoadBalancingClient,
      List<String> targetIds, String targetGroupARN, ExecutionLogCallback logCallback) {
    if (isEmpty(targetIds)) {
      return true;
    }
    DescribeTargetHealthRequest request = new DescribeTargetHealthRequest().withTargetGroupArn(targetGroupARN);
    tracker.trackELBCall("Describe Target Health");
    DescribeTargetHealthResult result = amazonElasticLoadBalancingClient.describeTargetHealth(request);
    List<TargetHealthDescription> healthDescriptions = result.getTargetHealthDescriptions();
    if (isEmpty(healthDescriptions)) {
      return false;
    }
    Set<String> instanceIdsRegistered =
        healthDescriptions.stream()
            .filter(description -> description.getTargetHealth().getState().equalsIgnoreCase("Healthy"))
            .map(description -> description.getTarget().getId())
            .filter(targetIds::contains)
            .collect(toSet());
    logCallback.saveExecutionLog(format(
        "[%d] out of [%d] targets registered and in healthy state", instanceIdsRegistered.size(), targetIds.size()));
    return instanceIdsRegistered.containsAll(targetIds);
  }

  @Override
  public List<Action> getMatchingTargetGroupForSpecificListenerRuleArn(AwsConfig awsConfig,
      List<EncryptedDataDetail> encryptionDetails, String region, String listenerArn, String prodListenerRuleArn,
      String targetGroupArn, ExecutionLogCallback executionLogCallback) {
    List<Rule> listenerRules =
        getListenerRulesFromListenerArn(awsConfig, encryptionDetails, region, listenerArn, executionLogCallback);

    List<Action> prodRuleActionsMatchingNewServiceTargetGroup = new ArrayList<>();
    for (Rule rule : listenerRules) {
      List<Action> actions = rule.getActions();

      if (rule.getRuleArn().equalsIgnoreCase(prodListenerRuleArn)) {
        Optional<Action> action =
            actions.stream().filter(a -> a.getTargetGroupArn().equalsIgnoreCase(targetGroupArn)).findFirst();
        if (action.isPresent()) {
          prodRuleActionsMatchingNewServiceTargetGroup.add(action.get());
        }
      }
    }

    return prodRuleActionsMatchingNewServiceTargetGroup;
  }

  @Override
  public List<Rule> getListenerRuleFromListenerRuleArn(AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails,
      String region, String listenerRuleArn, ExecutionLogCallback logCallback) {
    encryptionService.decrypt(awsConfig, encryptionDetails, false);
    ExceptionMessageSanitizer.storeAllSecretsForSanitizing(awsConfig, encryptionDetails);
    try (CloseableAmazonWebServiceClient<AmazonElasticLoadBalancingClient> closeableAmazonElasticLoadBalancingClient =
             new CloseableAmazonWebServiceClient(
                 getAmazonElasticLoadBalancingClientV2(Regions.fromName(region), awsConfig))) {
      DescribeRulesRequest request = new DescribeRulesRequest().withRuleArns(listenerRuleArn);

      String nextMarker = null;
      List<Rule> listenerRules = new ArrayList<>();
      do {
        tracker.trackELBCall("Describe Listener Rules");
        DescribeRulesResult result = closeableAmazonElasticLoadBalancingClient.getClient().describeRules(request);
        if (EmptyPredicate.isNotEmpty(result.getRules())) {
          listenerRules.addAll(result.getRules());
        }
        nextMarker = result.getNextMarker();
      } while (nextMarker != null);

      logCallback.saveExecutionLog(format(
          "[%d] rules found when searching the given listener rule arn: [%s]", listenerRules.size(), listenerRuleArn));

      return listenerRules;
    } catch (Exception e) {
      Exception sanitizeException = ExceptionMessageSanitizer.sanitizeException(e);
      log.error("Exception getListenerRuleFromListenerRuleArn", sanitizeException);
      throw new InvalidRequestException(ExceptionUtils.getMessage(sanitizeException), sanitizeException);
    }
  }

  @Override
  public List<Rule> getListenerRulesFromListenerArn(AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails,
      String region, String listenerArn, ExecutionLogCallback logCallback) {
    encryptionService.decrypt(awsConfig, encryptionDetails, false);
    ExceptionMessageSanitizer.storeAllSecretsForSanitizing(awsConfig, encryptionDetails);
    try (CloseableAmazonWebServiceClient<AmazonElasticLoadBalancingClient> closeableAmazonElasticLoadBalancingClient =
             new CloseableAmazonWebServiceClient(
                 getAmazonElasticLoadBalancingClientV2(Regions.fromName(region), awsConfig))) {
      DescribeRulesRequest request = new DescribeRulesRequest().withListenerArn(listenerArn);

      String nextMarker = null;
      List<Rule> listenerRules = new ArrayList<>();
      do {
        tracker.trackELBCall("Describe Listener Rules");
        DescribeRulesResult result = closeableAmazonElasticLoadBalancingClient.getClient().describeRules(request);
        if (EmptyPredicate.isNotEmpty(result.getRules())) {
          listenerRules.addAll(result.getRules());
        }
        nextMarker = result.getNextMarker();
      } while (nextMarker != null);

      logCallback.saveExecutionLog(
          format("[%d] rules found when searching the given listener arn: [%s]", listenerRules.size(), listenerArn));

      return listenerRules;
    } catch (Exception e) {
      Exception sanitizeException = ExceptionMessageSanitizer.sanitizeException(e);
      log.error("Exception getListenerRulesFromListenerArn", sanitizeException);
      throw new InvalidRequestException(ExceptionUtils.getMessage(sanitizeException), sanitizeException);
    }
  }

  @Override
  public List<AwsElbListener> getElbListenersForLoadBalaner(
      AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails, String region, String loadBalancerName) {
    encryptionService.decrypt(awsConfig, encryptionDetails, false);
    ExceptionMessageSanitizer.storeAllSecretsForSanitizing(awsConfig, encryptionDetails);
    try (CloseableAmazonWebServiceClient<AmazonElasticLoadBalancingClient> closeableAmazonElasticLoadBalancingClient =
             new CloseableAmazonWebServiceClient(
                 getAmazonElasticLoadBalancingClientV2(Regions.fromName(region), awsConfig))) {
      DescribeLoadBalancersRequest request = new DescribeLoadBalancersRequest().withNames(loadBalancerName);

      tracker.trackELBCall("Describe Load Balancers");
      DescribeLoadBalancersResult result =
          closeableAmazonElasticLoadBalancingClient.getClient().describeLoadBalancers(request);
      if (EmptyPredicate.isEmpty(result.getLoadBalancers())) {
        throw new WingsException(
            ErrorCode.INVALID_ARGUMENT, "Invalid Load Balancer Name Provided. Could not be found", WingsException.USER)
            .addParam("message", "Invalid Load Balancer Name Provided. Could not be found");
      }

      String elbArn = result.getLoadBalancers().get(0).getLoadBalancerArn();

      List<Listener> listeners = new ArrayList<>();
      DescribeListenersRequest describeListenersRequest = new DescribeListenersRequest().withLoadBalancerArn(elbArn);
      String nextMarker = null;
      do {
        describeListenersRequest.setMarker(nextMarker);
        tracker.trackELBCall("Describe Listeners");
        DescribeListenersResult describeListenersResult =
            closeableAmazonElasticLoadBalancingClient.getClient().describeListeners(describeListenersRequest);
        if (EmptyPredicate.isNotEmpty(describeListenersResult.getListeners())) {
          listeners.addAll(describeListenersResult.getListeners());
        }
        nextMarker = describeListenersResult.getNextMarker();
      } while (nextMarker != null);
      if (EmptyPredicate.isEmpty(listeners)) {
        return Collections.emptyList();
      }

      DescribeRulesRequest describeRulesRequest = new DescribeRulesRequest();
      List<AwsElbListener> listenerDetails = new ArrayList<>();
      for (Listener listener : listeners) {
        AwsElbListenerBuilder builder = AwsElbListener.builder();
        builder.listenerArn(listener.getListenerArn());
        builder.loadBalancerArn(elbArn);
        builder.protocol(listener.getProtocol());
        builder.port(listener.getPort());
        List<AwsElbListenerRuleData> rules = new ArrayList<>();

        describeRulesRequest.setListenerArn(listener.getListenerArn());
        nextMarker = null;
        do {
          describeRulesRequest.setMarker(nextMarker);
          tracker.trackELBCall("Describe Rules");
          DescribeRulesResult describeRulesResult =
              closeableAmazonElasticLoadBalancingClient.getClient().describeRules(describeRulesRequest);
          List<Rule> currentRules = describeRulesResult.getRules();
          if (EmptyPredicate.isNotEmpty(currentRules)) {
            currentRules.forEach(currentRule -> {
              AwsElbListenerRuleDataBuilder ruleDataBuilder = AwsElbListenerRuleData.builder();
              ruleDataBuilder.ruleArn(currentRule.getRuleArn());
              ruleDataBuilder.rulePriority(currentRule.getPriority());
              ruleDataBuilder.isDefault(currentRule.isDefault());
              List<Action> currentRuleActions = currentRule.getActions();
              if (EmptyPredicate.isNotEmpty(currentRuleActions)) {
                ruleDataBuilder.ruleTargetGroupArn(currentRuleActions.get(0).getTargetGroupArn());
              }
              rules.add(ruleDataBuilder.build());
            });
          }
          nextMarker = describeRulesResult.getNextMarker();
        } while (nextMarker != null);
        builder.rules(rules);
        listenerDetails.add(builder.build());
      }
      return listenerDetails;
    } catch (Exception e) {
      Exception sanitizeException = ExceptionMessageSanitizer.sanitizeException(e);
      log.error("Exception getElbListenersForLoadBalaner", sanitizeException);
      throw new InvalidRequestException(ExceptionUtils.getMessage(sanitizeException), sanitizeException);
    }
  }

  @Override
  public Listener getElbListener(
      AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails, String region, String listenerArn) {
    encryptionService.decrypt(awsConfig, encryptionDetails, false);
    ExceptionMessageSanitizer.storeAllSecretsForSanitizing(awsConfig, encryptionDetails);
    try (CloseableAmazonWebServiceClient<AmazonElasticLoadBalancingClient> closeableAmazonElasticLoadBalancingClient =
             new CloseableAmazonWebServiceClient(
                 getAmazonElasticLoadBalancingClientV2(Regions.fromName(region), awsConfig))) {
      tracker.trackELBCall("Describe Listener");
      DescribeListenersResult listenerResult = closeableAmazonElasticLoadBalancingClient.getClient().describeListeners(
          new DescribeListenersRequest().withListenerArns(listenerArn));
      if (EmptyPredicate.isEmpty(listenerResult.getListeners())) {
        throw new WingsException(
            ErrorCode.INVALID_ARGUMENT, "Invalid ListenerArn. Listener could not be found", WingsException.USER)
            .addParam("message", "Invalid ListenerArn. Listener could not be found");
      }

      return listenerResult.getListeners().get(0);
    } catch (Exception e) {
      Exception sanitizeException = ExceptionMessageSanitizer.sanitizeException(e);
      log.error("Exception getElbListener", sanitizeException);
      throw new InvalidRequestException(ExceptionUtils.getMessage(sanitizeException), sanitizeException);
    }
  }

  @Override
  public TargetGroup cloneTargetGroup(AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails, String region,
      String targetGroupArn, String newTargetGroupName) {
    encryptionService.decrypt(awsConfig, encryptionDetails, false);
    ExceptionMessageSanitizer.storeAllSecretsForSanitizing(awsConfig, encryptionDetails);
    try (CloseableAmazonWebServiceClient<AmazonElasticLoadBalancingClient> closeableAmazonElasticLoadBalancingClient =
             new CloseableAmazonWebServiceClient(
                 getAmazonElasticLoadBalancingClientV2(Regions.fromName(region), awsConfig))) {
      if (isBlank(targetGroupArn)) {
        throw new WingsException(
            ErrorCode.INVALID_ARGUMENT, "TargetGroupArn to be cloned from can not be empty", WingsException.USER)
            .addParam("message", "TargetGroupArn to be cloned from can not be empty");
      }
      tracker.trackELBCall("Describe Target Groups");
      DescribeTargetGroupsRequest describeTargetGroupsRequest =
          new DescribeTargetGroupsRequest().withTargetGroupArns(targetGroupArn);
      DescribeTargetGroupsResult describeTargetGroupsResult =
          closeableAmazonElasticLoadBalancingClient.getClient().describeTargetGroups(describeTargetGroupsRequest);
      List<TargetGroup> targetGroups = describeTargetGroupsResult.getTargetGroups();
      if (isEmpty(targetGroups)) {
        throw new WingsException(
            ErrorCode.INVALID_ARGUMENT, "TargetGroupArn to be cloned from could not be Found", WingsException.USER)
            .addParam("message", "TargetGroupArn to be cloned from could not be Found");
      }
      TargetGroup sourceTargetGroup = targetGroups.get(0);
      CreateTargetGroupRequest createTargetGroupRequest =
          new CreateTargetGroupRequest()
              .withName(newTargetGroupName)
              .withTargetType(sourceTargetGroup.getTargetType())
              .withHealthCheckPath(sourceTargetGroup.getHealthCheckPath())
              .withHealthCheckPort(sourceTargetGroup.getHealthCheckPort())
              .withHealthCheckIntervalSeconds(sourceTargetGroup.getHealthCheckIntervalSeconds())
              .withHealthCheckProtocol(sourceTargetGroup.getHealthCheckProtocol())
              .withHealthCheckTimeoutSeconds(sourceTargetGroup.getHealthCheckTimeoutSeconds())
              .withHealthyThresholdCount(sourceTargetGroup.getHealthyThresholdCount())
              .withPort(sourceTargetGroup.getPort())
              .withProtocol(sourceTargetGroup.getProtocol())
              .withTargetType(sourceTargetGroup.getTargetType())
              .withVpcId(sourceTargetGroup.getVpcId())
              .withUnhealthyThresholdCount(sourceTargetGroup.getUnhealthyThresholdCount());

      CreateTargetGroupResult createTargetGroupResult =
          closeableAmazonElasticLoadBalancingClient.getClient().createTargetGroup(createTargetGroupRequest);
      tracker.trackELBCall("Create Target Group");
      return createTargetGroupResult.getTargetGroups().get(0);
    } catch (AmazonServiceException amazonServiceException) {
      handleAmazonServiceException(amazonServiceException);
    } catch (AmazonClientException amazonClientException) {
      handleAmazonClientException(amazonClientException);
    } catch (Exception e) {
      Exception sanitizeException = ExceptionMessageSanitizer.sanitizeException(e);
      log.error("Exception cloneTargetGroup", sanitizeException);
      throw new InvalidRequestException(ExceptionUtils.getMessage(sanitizeException), sanitizeException);
    }

    return null;
  }

  @Override
  public Listener createStageListener(AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails, String region,
      String listenerArn, Integer port, String targetGroupArn) {
    Listener prodListener = getElbListener(awsConfig, encryptionDetails, region, listenerArn);

    CreateListenerRequest createListenerRequest = new CreateListenerRequest();
    createListenerRequest.withProtocol(prodListener.getProtocol())
        .withPort(port)
        .withLoadBalancerArn(prodListener.getLoadBalancerArn())
        .withCertificates(prodListener.getCertificates())
        .withSslPolicy(prodListener.getSslPolicy());

    createListenerRequest.withDefaultActions(new Action().withType(Forward).withTargetGroupArn(targetGroupArn));

    encryptionService.decrypt(awsConfig, encryptionDetails, false);
    ExceptionMessageSanitizer.storeAllSecretsForSanitizing(awsConfig, encryptionDetails);
    try (CloseableAmazonWebServiceClient<AmazonElasticLoadBalancingClient> closeableAmazonElasticLoadBalancingClient =
             new CloseableAmazonWebServiceClient(
                 getAmazonElasticLoadBalancingClientV2(Regions.fromName(region), awsConfig))) {
      tracker.trackELBCall("Create Listener");

      CreateListenerResult result =
          closeableAmazonElasticLoadBalancingClient.getClient().createListener(createListenerRequest);
      return result.getListeners().get(0);
    } catch (Exception e) {
      Exception sanitizeException = ExceptionMessageSanitizer.sanitizeException(e);
      log.error("Exception createStageListener", sanitizeException);
      throw new InvalidRequestException(ExceptionUtils.getMessage(sanitizeException), sanitizeException);
    }
  }

  public void modifyListenerRule(AmazonElasticLoadBalancing client, String listenerArn, String listenerRuleArn,
      String targetGroupArn, ExecutionLogCallback executionLogCallback) {
    boolean isDefaultRule = checkIfIsDefaultRule(client, listenerArn, listenerRuleArn);

    if (!isDefaultRule) {
      modifySpecificRule(client, listenerRuleArn, targetGroupArn, executionLogCallback);
    } else {
      modifyDefaultListenerRuleWithTargetGroup(
          client, listenerArn, listenerRuleArn, targetGroupArn, executionLogCallback);
    }
  }

  @Override
  public void swapListenersForEcsBG(AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails,
      boolean isUseSpecificRules, String prodListenerArn, String stageListenerArn, String prodListenerRuleArn,
      String stageListenerRuleArn, String targetGroupArn1, String targetGroupArn2, String region,
      ExecutionLogCallback logCallback) {
    encryptionService.decrypt(awsConfig, encryptionDetails, false);
    ExceptionMessageSanitizer.storeAllSecretsForSanitizing(awsConfig, encryptionDetails);
    try (CloseableAmazonWebServiceClient<AmazonElasticLoadBalancingClient> closeableAmazonElasticLoadBalancingClient =
             new CloseableAmazonWebServiceClient(
                 getAmazonElasticLoadBalancingClientV2(Regions.fromName(region), awsConfig))) {
      if (isUseSpecificRules) {
        List<Rule> prodRules =
            getListenerRuleFromListenerRuleArn(awsConfig, encryptionDetails, region, prodListenerRuleArn, logCallback);
        List<Rule> stageRules =
            getListenerRuleFromListenerRuleArn(awsConfig, encryptionDetails, region, stageListenerRuleArn, logCallback);

        String prodTargetGroup = prodRules.get(0).getActions().get(0).getTargetGroupArn();
        String stageTargetGroup = stageRules.get(0).getActions().get(0).getTargetGroupArn();

        modifyListenerRule(closeableAmazonElasticLoadBalancingClient.getClient(), prodListenerArn, prodListenerRuleArn,
            stageTargetGroup, logCallback);
        modifyListenerRule(closeableAmazonElasticLoadBalancingClient.getClient(), stageListenerArn,
            stageListenerRuleArn, prodTargetGroup, logCallback);
      } else {
        tracker.trackELBCall("Describe Listeners");
        DescribeListenersResult prodListenerResult =
            closeableAmazonElasticLoadBalancingClient.getClient().describeListeners(
                new DescribeListenersRequest().withListenerArns(prodListenerArn));
        tracker.trackELBCall("Describe Listeners");
        DescribeListenersResult stageListenerResult =
            closeableAmazonElasticLoadBalancingClient.getClient().describeListeners(
                new DescribeListenersRequest().withListenerArns(stageListenerArn));
        Listener prodListener = prodListenerResult.getListeners().get(0);
        Listener stageListener = stageListenerResult.getListeners().get(0);
        tracker.trackELBCall("Modify Listeners");
        closeableAmazonElasticLoadBalancingClient.getClient().modifyListener(
            new ModifyListenerRequest()
                .withListenerArn(prodListener.getListenerArn())
                .withDefaultActions(stageListener.getDefaultActions()));
        tracker.trackELBCall("Modify Listeners");
        closeableAmazonElasticLoadBalancingClient.getClient().modifyListener(
            new ModifyListenerRequest()
                .withListenerArn(stageListener.getListenerArn())
                .withDefaultActions(prodListener.getDefaultActions()));
      }
    } catch (Exception e) {
      Exception sanitizeException = ExceptionMessageSanitizer.sanitizeException(e);
      log.error("Exception swapListenersForEcsBG", sanitizeException);
      throw new InvalidRequestException(ExceptionUtils.getMessage(sanitizeException), sanitizeException);
    }
  }

  private boolean checkIfIsDefaultRule(
      AmazonElasticLoadBalancing amazonElasticLoadBalancingClient, String listenerArn, String listenerRuleArn) {
    DescribeRulesRequest describeRulesRequest = new DescribeRulesRequest();
    describeRulesRequest.setListenerArn(listenerArn);
    String nextMarker = null;
    do {
      describeRulesRequest.setMarker(nextMarker);
      tracker.trackELBCall("Describe Rules");
      DescribeRulesResult describeRulesResult = amazonElasticLoadBalancingClient.describeRules(describeRulesRequest);
      List<Rule> currentRules = describeRulesResult.getRules();
      if (EmptyPredicate.isNotEmpty(currentRules)) {
        Optional<Rule> defaultRule = currentRules.stream().filter(rule -> rule.isDefault()).findFirst();
        if (defaultRule.isPresent()) {
          if (defaultRule.get().getRuleArn().equalsIgnoreCase(listenerRuleArn)) {
            return true;
          }
        }
      }

      nextMarker = describeRulesResult.getNextMarker();
    } while (nextMarker != null);

    return false;
  }

  @Override
  public void updateDefaultListenersForSpotInstBG(AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails,
      String prodListenerArn, String stageListenerArn, String region) {
    encryptionService.decrypt(awsConfig, encryptionDetails, false);
    ExceptionMessageSanitizer.storeAllSecretsForSanitizing(awsConfig, encryptionDetails);
    try (CloseableAmazonWebServiceClient<AmazonElasticLoadBalancingClient> closeableAmazonElasticLoadBalancingClient =
             new CloseableAmazonWebServiceClient(
                 getAmazonElasticLoadBalancingClientV2(Regions.fromName(region), awsConfig))) {
      tracker.trackELBCall("Describe Listeners");
      DescribeListenersResult prodListenerResult =
          closeableAmazonElasticLoadBalancingClient.getClient().describeListeners(
              new DescribeListenersRequest().withListenerArns(prodListenerArn));
      tracker.trackELBCall("Describe Listeners");
      DescribeListenersResult stageListenerResult =
          closeableAmazonElasticLoadBalancingClient.getClient().describeListeners(
              new DescribeListenersRequest().withListenerArns(stageListenerArn));
      Listener prodListener = prodListenerResult.getListeners().get(0);
      Listener stageListener = stageListenerResult.getListeners().get(0);
      tracker.trackELBCall("Modify Listeners");
      closeableAmazonElasticLoadBalancingClient.getClient().modifyListener(
          new ModifyListenerRequest()
              .withListenerArn(prodListener.getListenerArn())
              .withDefaultActions(stageListener.getDefaultActions()));
      tracker.trackELBCall("Modify Listeners");
      closeableAmazonElasticLoadBalancingClient.getClient().modifyListener(
          new ModifyListenerRequest()
              .withListenerArn(stageListener.getListenerArn())
              .withDefaultActions(prodListener.getDefaultActions()));
    } catch (Exception e) {
      Exception sanitizeException = ExceptionMessageSanitizer.sanitizeException(e);
      log.error("Exception updateDefaultListenersForSpotInstBG", sanitizeException);
      throw new InvalidRequestException(ExceptionUtils.getMessage(sanitizeException), sanitizeException);
    }
  }

  @Override
  public void updateListenersForEcsBG(AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails,
      String prodListenerArn, String stageListenerArn, String region) {
    encryptionService.decrypt(awsConfig, encryptionDetails, false);
    ExceptionMessageSanitizer.storeAllSecretsForSanitizing(awsConfig, encryptionDetails);
    try (CloseableAmazonWebServiceClient<AmazonElasticLoadBalancingClient> closeableAmazonElasticLoadBalancingClient =
             new CloseableAmazonWebServiceClient(
                 getAmazonElasticLoadBalancingClientV2(Regions.fromName(region), awsConfig))) {
      tracker.trackELBCall("Describe Listeners");
      DescribeListenersResult prodListenerResult =
          closeableAmazonElasticLoadBalancingClient.getClient().describeListeners(
              new DescribeListenersRequest().withListenerArns(prodListenerArn));
      tracker.trackELBCall("Describe Listeners");
      DescribeListenersResult stageListenerResult =
          closeableAmazonElasticLoadBalancingClient.getClient().describeListeners(
              new DescribeListenersRequest().withListenerArns(stageListenerArn));
      Listener prodListener = prodListenerResult.getListeners().get(0);
      Listener stageListener = stageListenerResult.getListeners().get(0);
      tracker.trackELBCall("Modify Listeners");
      closeableAmazonElasticLoadBalancingClient.getClient().modifyListener(
          new ModifyListenerRequest()
              .withListenerArn(prodListener.getListenerArn())
              .withDefaultActions(stageListener.getDefaultActions()));
      tracker.trackELBCall("Modify Listeners");
      closeableAmazonElasticLoadBalancingClient.getClient().modifyListener(
          new ModifyListenerRequest()
              .withListenerArn(stageListener.getListenerArn())
              .withDefaultActions(prodListener.getDefaultActions()));
    } catch (Exception e) {
      Exception sanitizeException = ExceptionMessageSanitizer.sanitizeException(e);
      log.error("Exception updateListenersForEcsBG", sanitizeException);
      throw new InvalidRequestException(ExceptionUtils.getMessage(sanitizeException), sanitizeException);
    }
  }

  @Override
  public void updateListenersForBGDeployment(AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails,
      List<LoadBalancerDetailsForBGDeployment> lbDetailsForBGDeployments, String region,
      ExecutionLogCallback logCallback) {
    encryptionService.decrypt(awsConfig, encryptionDetails, false);
    ExceptionMessageSanitizer.storeAllSecretsForSanitizing(awsConfig, encryptionDetails);
    try (CloseableAmazonWebServiceClient<AmazonElasticLoadBalancingClient> closeableAmazonElasticLoadBalancingClient =
             new CloseableAmazonWebServiceClient(
                 getAmazonElasticLoadBalancingClientV2(Regions.fromName(region), awsConfig))) {
      lbDetailsForBGDeployments.forEach(lbDetailsForBGDeployment -> {
        if (lbDetailsForBGDeployment.isUseSpecificRules()) {
          List<Rule> prodRules = getListenerRuleFromListenerRuleArn(
              awsConfig, encryptionDetails, region, lbDetailsForBGDeployment.getProdRuleArn(), logCallback);
          List<Rule> stageRules = getListenerRuleFromListenerRuleArn(
              awsConfig, encryptionDetails, region, lbDetailsForBGDeployment.getStageRuleArn(), logCallback);

          String prodTargetGroup = prodRules.get(0).getActions().get(0).getTargetGroupArn();
          String stageTargetGroup = stageRules.get(0).getActions().get(0).getTargetGroupArn();

          modifyListenerRule(closeableAmazonElasticLoadBalancingClient.getClient(),
              lbDetailsForBGDeployment.getProdListenerArn(), lbDetailsForBGDeployment.getProdRuleArn(),
              stageTargetGroup, logCallback);
          modifyListenerRule(closeableAmazonElasticLoadBalancingClient.getClient(),
              lbDetailsForBGDeployment.getStageListenerArn(), lbDetailsForBGDeployment.getStageRuleArn(),
              prodTargetGroup, logCallback);
        } else {
          tracker.trackELBCall("Describe Listeners");
          DescribeListenersResult prodListenerResult =
              closeableAmazonElasticLoadBalancingClient.getClient().describeListeners(
                  new DescribeListenersRequest().withListenerArns(lbDetailsForBGDeployment.getProdListenerArn()));
          tracker.trackELBCall("Describe Listeners");
          DescribeListenersResult stageListenerResult =
              closeableAmazonElasticLoadBalancingClient.getClient().describeListeners(
                  new DescribeListenersRequest().withListenerArns(lbDetailsForBGDeployment.getStageListenerArn()));
          Listener prodListener = prodListenerResult.getListeners().get(0);
          Listener stageListener = stageListenerResult.getListeners().get(0);
          tracker.trackELBCall("Modify Listeners");
          closeableAmazonElasticLoadBalancingClient.getClient().modifyListener(
              new ModifyListenerRequest()
                  .withListenerArn(prodListener.getListenerArn())
                  .withDefaultActions(stageListener.getDefaultActions()));
          tracker.trackELBCall("Modify Listeners");
          closeableAmazonElasticLoadBalancingClient.getClient().modifyListener(
              new ModifyListenerRequest()
                  .withListenerArn(stageListener.getListenerArn())
                  .withDefaultActions(prodListener.getDefaultActions()));
        }
      });
    } catch (Exception e) {
      Exception sanitizeException = ExceptionMessageSanitizer.sanitizeException(e);
      log.error("Exception updateListenersForBGDeployment", sanitizeException);
      throw new InvalidRequestException(ExceptionUtils.getMessage(sanitizeException), sanitizeException);
    }
  }

  private void swapListenerRulesWhenSpecificRulesCase(AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails,
      LoadBalancerDetailsForBGDeployment lbDetailsForBGDeployment, String region, AmazonElasticLoadBalancing client,
      ExecutionLogCallback logCallback) {
    List<Rule> prodRules = getListenerRuleFromListenerRuleArn(
        awsConfig, encryptionDetails, region, lbDetailsForBGDeployment.getProdRuleArn(), logCallback);
    List<Rule> stageRules = getListenerRuleFromListenerRuleArn(
        awsConfig, encryptionDetails, region, lbDetailsForBGDeployment.getStageRuleArn(), logCallback);

    String prodTargetGroup = prodRules.get(0).getActions().get(0).getTargetGroupArn();
    String stageTargetGroup = stageRules.get(0).getActions().get(0).getTargetGroupArn();

    modifyListenerRule(client, lbDetailsForBGDeployment.getProdListenerArn(), lbDetailsForBGDeployment.getProdRuleArn(),
        stageTargetGroup, logCallback);
    modifyListenerRule(client, lbDetailsForBGDeployment.getStageListenerArn(),
        lbDetailsForBGDeployment.getStageRuleArn(), prodTargetGroup, logCallback);
  }

  private void swapListenerRulesWhenBothDefaultRulesCase(
      AmazonElasticLoadBalancing client, LoadBalancerDetailsForBGDeployment lbDetailsForBGDeployment) {
    tracker.trackELBCall("Describe Listeners");
    DescribeListenersResult prodListenerResult = client.describeListeners(
        new DescribeListenersRequest().withListenerArns(lbDetailsForBGDeployment.getProdListenerArn()));
    tracker.trackELBCall("Describe Listeners");
    DescribeListenersResult stageListenerResult = client.describeListeners(
        new DescribeListenersRequest().withListenerArns(lbDetailsForBGDeployment.getStageListenerArn()));
    Listener prodListener = prodListenerResult.getListeners().get(0);
    Listener stageListener = stageListenerResult.getListeners().get(0);
    tracker.trackELBCall("Modify Listeners");
    client.modifyListener(new ModifyListenerRequest()
                              .withListenerArn(prodListener.getListenerArn())
                              .withDefaultActions(stageListener.getDefaultActions()));
    tracker.trackELBCall("Modify Listeners");
    client.modifyListener(new ModifyListenerRequest()
                              .withListenerArn(stageListener.getListenerArn())
                              .withDefaultActions(prodListener.getDefaultActions()));
  }

  @Override
  public void updateListenersForSpotInstBGDeployment(AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails,
      List<LoadBalancerDetailsForBGDeployment> lbDetailsForBGDeployments, String region,
      ExecutionLogCallback logCallback) {
    encryptionService.decrypt(awsConfig, encryptionDetails, false);
    ExceptionMessageSanitizer.storeAllSecretsForSanitizing(awsConfig, encryptionDetails);
    try (CloseableAmazonWebServiceClient<AmazonElasticLoadBalancingClient> closeableAmazonElasticLoadBalancingClient =
             new CloseableAmazonWebServiceClient(
                 getAmazonElasticLoadBalancingClientV2(Regions.fromName(region), awsConfig))) {
      lbDetailsForBGDeployments.forEach(lbDetailsForBGDeployment -> {
        if (lbDetailsForBGDeployment.isUseSpecificRules()) {
          swapListenerRulesWhenSpecificRulesCase(awsConfig, encryptionDetails, lbDetailsForBGDeployment, region,
              closeableAmazonElasticLoadBalancingClient.getClient(), logCallback);
        } else {
          swapListenerRulesWhenBothDefaultRulesCase(
              closeableAmazonElasticLoadBalancingClient.getClient(), lbDetailsForBGDeployment);
        }
      });
    } catch (Exception e) {
      Exception sanitizeException = ExceptionMessageSanitizer.sanitizeException(e);
      log.error("Exception updateListenersForSpotInstBGDeployment", sanitizeException);
      throw new InvalidRequestException(ExceptionUtils.getMessage(sanitizeException), sanitizeException);
    }
  }

  @Override
  public DescribeListenersResult describeListenerResult(
      AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails, String listenerArn, String region) {
    encryptionService.decrypt(awsConfig, encryptionDetails, false);
    ExceptionMessageSanitizer.storeAllSecretsForSanitizing(awsConfig, encryptionDetails);
    try (CloseableAmazonWebServiceClient<AmazonElasticLoadBalancingClient> closeableAmazonElasticLoadBalancingClient =
             new CloseableAmazonWebServiceClient(
                 getAmazonElasticLoadBalancingClientV2(Regions.fromName(region), awsConfig))) {
      tracker.trackELBCall("Describe Listeners");
      return closeableAmazonElasticLoadBalancingClient.getClient().describeListeners(
          new DescribeListenersRequest().withListenerArns(listenerArn));
    } catch (Exception e) {
      Exception sanitizeException = ExceptionMessageSanitizer.sanitizeException(e);
      log.error("Exception describeListenerResult", sanitizeException);
      throw new InvalidRequestException(ExceptionUtils.getMessage(sanitizeException), sanitizeException);
    }
  }

  @Override
  public String getTargetGroupForDefaultAction(Listener listener, ExecutionLogCallback executionLogCallback) {
    Optional<Action> action = listener.getDefaultActions()
                                  .stream()
                                  .filter(listenerAction
                                      -> isNotEmpty(listenerAction.getTargetGroupArn())
                                          && FORWARD_LISTENER_ACTION.equalsIgnoreCase(listenerAction.getType()))
                                  .findFirst();

    if (!action.isPresent()) {
      String message = format("Did not find any default forward actions for listener: [%s]", listener.getListenerArn());
      executionLogCallback.saveExecutionLog(message, LogLevel.ERROR);
      throw new WingsException(ErrorCode.INVALID_ARGUMENT, message, USER).addParam("message", message);
    }
    return action.get().getTargetGroupArn();
  }

  private void modifyDefaultListenerRuleWithTargetGroup(AmazonElasticLoadBalancing client, String listenerArn,
      String ruleArn, String targetGroupArn, ExecutionLogCallback executionLogCallback) {
    executionLogCallback.saveExecutionLog(
        format("Modifying the default listener: [%s] with listener rule: [%s] with target group [%s]", listenerArn,
            ruleArn, targetGroupArn));

    tracker.trackELBCall("Describe Listeners");
    DescribeListenersResult listenerResult =
        client.describeListeners(new DescribeListenersRequest().withListenerArns(listenerArn));
    Listener listener = listenerResult.getListeners().get(0);

    List<Action> defaultActions = new ArrayList<>();
    Action action = new Action().withType("forward").withTargetGroupArn(targetGroupArn);
    defaultActions.add(action);
    tracker.trackELBCall("Modify Listeners");
    client.modifyListener(
        new ModifyListenerRequest().withListenerArn(listener.getListenerArn()).withDefaultActions(defaultActions));
  }

  private void modifySpecificRule(
      AmazonElasticLoadBalancing client, String ruleArn, String targetGroupArn, ExecutionLogCallback logCallback) {
    logCallback.saveExecutionLog(format("Updating rule: [%s] to forward traffic to: [%s]", ruleArn, targetGroupArn));
    ModifyRuleRequest request = new ModifyRuleRequest().withRuleArn(ruleArn).withActions(
        new Action().withType(Forward).withTargetGroupArn(targetGroupArn));
    tracker.trackELBCall("Modify Rules");
    client.modifyRule(request);
  }

  @Override
  public void modifySpecificRule(AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails, String region,
      String ruleArn, String targetGroupArn, ExecutionLogCallback logCallback) {
    encryptionService.decrypt(awsConfig, encryptionDetails, false);
    ExceptionMessageSanitizer.storeAllSecretsForSanitizing(awsConfig, encryptionDetails);
    try (CloseableAmazonWebServiceClient<AmazonElasticLoadBalancingClient> closeableAmazonElasticLoadBalancingClient =
             new CloseableAmazonWebServiceClient(
                 getAmazonElasticLoadBalancingClientV2(Regions.fromName(region), awsConfig))) {
      modifySpecificRule(closeableAmazonElasticLoadBalancingClient.getClient(), ruleArn, targetGroupArn, logCallback);
    } catch (AmazonServiceException amazonServiceException) {
      handleAmazonServiceException(amazonServiceException);
    } catch (AmazonClientException amazonClientException) {
      handleAmazonClientException(amazonClientException);
    } catch (Exception e) {
      Exception sanitizeException = ExceptionMessageSanitizer.sanitizeException(e);
      log.error("Exception modifySpecificRule", sanitizeException);
      throw new InvalidRequestException(ExceptionUtils.getMessage(sanitizeException), sanitizeException);
    }
  }

  @Override
  public TargetGroup fetchTargetGroupForSpecificRules(AwsElbListener listener, String ruleArn,
      ExecutionLogCallback logCallback, AwsConfig awsConfig, String region,
      List<EncryptedDataDetail> encryptionDetails) {
    try {
      encryptionService.decrypt(awsConfig, encryptionDetails, false);
      ExceptionMessageSanitizer.storeAllSecretsForSanitizing(awsConfig, encryptionDetails);
      List<AwsElbListenerRuleData> rules = listener.getRules();
      if (isEmpty(rules)) {
        String errorMessage = format("Did not find any rules for Listener: [%s]", listener.getListenerArn());
        logCallback.saveExecutionLog(errorMessage, ERROR);
        throw new InvalidRequestException(errorMessage);
      }

      Optional<AwsElbListenerRuleData> specificRule =
          rules.stream().filter(rule -> ruleArn.equalsIgnoreCase(rule.getRuleArn())).findFirst();
      if (!specificRule.isPresent()) {
        String errorMessage =
            format("No rule with Arn: [%s] found for Listener: [%s]", ruleArn, listener.getListenerArn());
        logCallback.saveExecutionLog(errorMessage, ERROR);
        throw new InvalidRequestException(errorMessage);
      }

      Optional<TargetGroup> targetGroupOptional =
          getTargetGroup(awsConfig, encryptionDetails, region, specificRule.get().getRuleTargetGroupArn());
      if (!targetGroupOptional.isPresent()) {
        String errorMessage = format("Did not find any Target group with Arn: [%s] for listener rule: [%s]",
            specificRule.get().getRuleTargetGroupArn(), specificRule.get().getRuleArn());
        logCallback.saveExecutionLog(errorMessage, ERROR);
        throw new InvalidRequestException(errorMessage);
      }
      TargetGroup targetGroup = targetGroupOptional.get();
      logCallback.saveExecutionLog(format("Found that rule: [%s] forwarding traffic to Target group: [%s], Arn: [%s]",
          ruleArn, targetGroup.getTargetGroupName(), targetGroup.getTargetGroupArn()));
      return targetGroup;
    } catch (AmazonServiceException amazonServiceException) {
      handleAmazonServiceException(amazonServiceException);
    } catch (AmazonClientException amazonClientException) {
      handleAmazonClientException(amazonClientException);
    }
    return null;
  }

  @VisibleForTesting
  Action getFinalAction(AmazonElasticLoadBalancing client, LbDetailsForAlbTrafficShift originalLbDetails,
      ExecutionLogCallback logCallback, AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails,
      String region) {
    Action forwardingAction;
    if (originalLbDetails.isUseSpecificRule()) {
      DescribeRulesResult describeRulesResult =
          client.describeRules(new DescribeRulesRequest().withRuleArns(originalLbDetails.getRuleArn()));
      List<Rule> rules = describeRulesResult.getRules();
      if (isEmpty(rules)) {
        String errorMessage = format("Did not find any rule with Arn: [%s]", originalLbDetails.getRuleArn());
        logCallback.saveExecutionLog(errorMessage, ERROR);
        throw new InvalidRequestException(errorMessage);
      }
      List<Action> actions = rules.get(0).getActions();
      if (isEmpty(actions)) {
        String errorMessage = format("Did not find any action with Arn: [%s]", originalLbDetails.getRuleArn());
        logCallback.saveExecutionLog(errorMessage, ERROR);
        throw new InvalidRequestException(errorMessage);
      }
      forwardingAction = actions.get(0);
    } else {
      Listener elbListener = getElbListener(awsConfig, encryptionDetails, region, originalLbDetails.getListenerArn());
      List<Action> defaultActions = elbListener.getDefaultActions();
      forwardingAction = defaultActions.get(0);
    }
    return forwardingAction;
  }

  @VisibleForTesting
  List<TargetGroupTuple> validateActionAndGetTuples(Action forwardingAction, ExecutionLogCallback logCallback) {
    if (!ActionTypeEnum.Forward.toString().equals(forwardingAction.getType())) {
      String errorMessage = "Action type is not forward";
      logCallback.saveExecutionLog(errorMessage, ERROR);
      throw new InvalidRequestException(errorMessage);
    }
    ForwardActionConfig forwardConfig = forwardingAction.getForwardConfig();
    if (forwardConfig == null) {
      String errorMessage = "Forwarding Config is null. Please set up to send request to 2 Target groups.";
      logCallback.saveExecutionLog(errorMessage, ERROR);
      throw new InvalidRequestException(errorMessage);
    }
    List<TargetGroupTuple> targetGroups = forwardConfig.getTargetGroups();
    if (targetGroups.size() != 2) {
      String errorMessage =
          "Forwarding Config does not forward to 2 Target groups. Please set up to send request to 2 Target groups.";
      logCallback.saveExecutionLog(errorMessage, ERROR);
      throw new InvalidRequestException(errorMessage);
    }
    return targetGroups;
  }

  @VisibleForTesting
  TargetGroup fetchRequiredTargetGroup(AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails, String region,
      String targetGroupArn, ExecutionLogCallback logCallback) {
    Optional<TargetGroup> targetGroupOptional = getTargetGroup(awsConfig, encryptionDetails, region, targetGroupArn);
    if (!targetGroupOptional.isPresent()) {
      String errorMessage = format("Did not find any Target group with Arn: [%s]", targetGroupArn);
      logCallback.saveExecutionLog(errorMessage, ERROR);
      throw new InvalidRequestException(errorMessage);
    }
    return targetGroupOptional.get();
  }

  @Override
  public LbDetailsForAlbTrafficShift loadTrafficShiftTargetGroupData(AwsConfig awsConfig, String region,
      List<EncryptedDataDetail> encryptionDetails, LbDetailsForAlbTrafficShift originalLbDetails,
      ExecutionLogCallback logCallback) {
    encryptionService.decrypt(awsConfig, encryptionDetails, false);
    ExceptionMessageSanitizer.storeAllSecretsForSanitizing(awsConfig, encryptionDetails);
    try (CloseableAmazonWebServiceClient<AmazonElasticLoadBalancingClient> closeableAmazonElasticLoadBalancingClient =
             new CloseableAmazonWebServiceClient(
                 getAmazonElasticLoadBalancingClientV2(Regions.fromName(region), awsConfig))) {
      logCallback.saveExecutionLog(format("Loading Target group data for Listener: [%s] of Load Balancer: [%s]",
          originalLbDetails.getListenerArn(), originalLbDetails.getLoadBalancerArn()));
      if (originalLbDetails.isUseSpecificRule()) {
        logCallback.saveExecutionLog(format("Rule Arn: [%s]", originalLbDetails.getRuleArn()));
      } else {
        logCallback.saveExecutionLog("Using default rules.");
      }
      Action forwardingAction = getFinalAction(closeableAmazonElasticLoadBalancingClient.getClient(), originalLbDetails,
          logCallback, awsConfig, encryptionDetails, region);
      List<TargetGroupTuple> targetGroups = validateActionAndGetTuples(forwardingAction, logCallback);
      TargetGroupTuple targetGroupTuple0 = targetGroups.get(0);
      TargetGroupTuple targetGroupTuple1 = targetGroups.get(1);
      String prodTargetGroupArn;
      String stageTargetGroupArn;
      if (MAX_TRAFFIC_SHIFT_WEIGHT == targetGroupTuple0.getWeight()) {
        logCallback.saveExecutionLog(format("Target group: [%s] is Prod, and [%s] is Stage",
            targetGroupTuple0.getTargetGroupArn(), targetGroupTuple1.getTargetGroupArn()));
        prodTargetGroupArn = targetGroupTuple0.getTargetGroupArn();
        stageTargetGroupArn = targetGroupTuple1.getTargetGroupArn();
      } else if (MAX_TRAFFIC_SHIFT_WEIGHT == targetGroupTuple1.getWeight()) {
        logCallback.saveExecutionLog(format("Target group: [%s] is Prod, and [%s] is Stage",
            targetGroupTuple1.getTargetGroupArn(), targetGroupTuple0.getTargetGroupArn()));
        prodTargetGroupArn = targetGroupTuple1.getTargetGroupArn();
        stageTargetGroupArn = targetGroupTuple0.getTargetGroupArn();
      } else {
        String errorMessage =
            format("Did not find any Target group tuple getting: [%d] traffic", MAX_TRAFFIC_SHIFT_WEIGHT);
        logCallback.saveExecutionLog(errorMessage, ERROR);
        throw new InvalidRequestException(errorMessage);
      }

      return LbDetailsForAlbTrafficShift.builder()
          .loadBalancerName(originalLbDetails.getLoadBalancerName())
          .loadBalancerArn(originalLbDetails.getLoadBalancerArn())
          .listenerPort(originalLbDetails.getListenerPort())
          .listenerArn(originalLbDetails.getListenerArn())
          .useSpecificRule(originalLbDetails.isUseSpecificRule())
          .ruleArn(originalLbDetails.getRuleArn())
          .prodTargetGroupArn(prodTargetGroupArn)
          .prodTargetGroupName(fetchRequiredTargetGroup(awsConfig, emptyList(), region, prodTargetGroupArn, logCallback)
                                   .getTargetGroupName())
          .stageTargetGroupArn(stageTargetGroupArn)
          .stageTargetGroupName(
              fetchRequiredTargetGroup(awsConfig, emptyList(), region, stageTargetGroupArn, logCallback)
                  .getTargetGroupName())
          .build();
    } catch (AmazonServiceException amazonServiceException) {
      handleAmazonServiceException(amazonServiceException);
    } catch (AmazonClientException amazonClientException) {
      handleAmazonClientException(amazonClientException);
    } catch (Exception e) {
      Exception sanitizeException = ExceptionMessageSanitizer.sanitizeException(e);
      log.error("Exception loadTrafficShiftTargetGroupData", sanitizeException);
      throw new InvalidRequestException(ExceptionUtils.getMessage(sanitizeException), sanitizeException);
    }
    return null;
  }

  @Override
  public void updateRulesForAlbTrafficShift(AwsConfig awsConfig, String region,
      List<EncryptedDataDetail> encryptionDetails, List<LbDetailsForAlbTrafficShift> details,
      ExecutionLogCallback logCallback, int newServiceTrafficWeight, String groupType) {
    encryptionService.decrypt(awsConfig, encryptionDetails, false);
    ExceptionMessageSanitizer.storeAllSecretsForSanitizing(awsConfig, encryptionDetails);
    try (CloseableAmazonWebServiceClient<AmazonElasticLoadBalancingClient> closeableAmazonElasticLoadBalancingClient =
             new CloseableAmazonWebServiceClient(
                 getAmazonElasticLoadBalancingClientV2(Regions.fromName(region), awsConfig))) {
      if (newServiceTrafficWeight < MIN_TRAFFIC_SHIFT_WEIGHT) {
        newServiceTrafficWeight = MIN_TRAFFIC_SHIFT_WEIGHT;
      } else if (newServiceTrafficWeight > MAX_TRAFFIC_SHIFT_WEIGHT) {
        newServiceTrafficWeight = MAX_TRAFFIC_SHIFT_WEIGHT;
      }
      int oldServiceTrafficWeight = MAX_TRAFFIC_SHIFT_WEIGHT - newServiceTrafficWeight;

      TargetGroupTuple newTuple = new TargetGroupTuple().withWeight(newServiceTrafficWeight);
      TargetGroupTuple oldTuple = new TargetGroupTuple().withWeight(oldServiceTrafficWeight);
      Action forwardAction = new Action().withType(Forward).withForwardConfig(
          new ForwardActionConfig().withTargetGroups(newTuple, oldTuple));
      ModifyRuleRequest modifyRuleRequest = new ModifyRuleRequest().withActions(forwardAction);
      ModifyListenerRequest modifyListenerRequest = new ModifyListenerRequest().withDefaultActions(forwardAction);
      for (LbDetailsForAlbTrafficShift detail : details) {
        logCallback.saveExecutionLog(format("New %s service will get: [%d] weight. TargetGroup: [%s]", groupType,
            newServiceTrafficWeight, detail.getStageTargetGroupName()));
        logCallback.saveExecutionLog(format("Old %s service will get: [%d] weight. TargetGroup: [%s]", groupType,
            oldServiceTrafficWeight, detail.getProdTargetGroupName()));
        oldTuple.setTargetGroupArn(detail.getProdTargetGroupArn());
        newTuple.setTargetGroupArn(detail.getStageTargetGroupArn());
        logCallback.saveExecutionLog(format("Editing listener: [%s]", detail.getListenerArn()));
        if (detail.isUseSpecificRule()
            && !checkIfIsDefaultRule(
                closeableAmazonElasticLoadBalancingClient.getClient(), detail.getListenerArn(), detail.getRuleArn())) {
          logCallback.saveExecutionLog(format("Editing rule: [%s]", detail.getRuleArn()));
          modifyRuleRequest.setRuleArn(detail.getRuleArn());
          closeableAmazonElasticLoadBalancingClient.getClient().modifyRule(modifyRuleRequest);
        } else {
          logCallback.saveExecutionLog(format("Editing Default rule: [%s]", detail.getRuleArn()));
          modifyListenerRequest.setListenerArn(detail.getListenerArn());
          closeableAmazonElasticLoadBalancingClient.getClient().modifyListener(modifyListenerRequest);
        }
      }
    } catch (AmazonServiceException amazonServiceException) {
      handleAmazonServiceException(amazonServiceException);
    } catch (AmazonClientException amazonClientException) {
      handleAmazonClientException(amazonClientException);
    } catch (Exception e) {
      Exception sanitizeException = ExceptionMessageSanitizer.sanitizeException(e);
      log.error("Exception updateRulesForAlbTrafficShift", sanitizeException);
      throw new InvalidRequestException(ExceptionUtils.getMessage(sanitizeException), sanitizeException);
    }
  }
}
