/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.aws.asg;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.logging.LogLevel.ERROR;
import static io.harness.logging.LogLevel.INFO;
import static io.harness.threading.Morpheus.sleep;

import static software.wings.beans.LogColor.White;
import static software.wings.beans.LogHelper.color;
import static software.wings.beans.LogWeight.Bold;

import static com.google.common.collect.Lists.newArrayList;
import static java.lang.String.format;
import static java.time.Duration.ofSeconds;

import io.harness.annotations.dev.OwnedBy;
import io.harness.aws.CloseableAmazonWebServiceClient;
import io.harness.concurrent.HTimeLimiter;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.logging.LogCallback;

import com.amazonaws.AmazonWebServiceClient;
import com.amazonaws.services.autoscaling.AmazonAutoScalingClient;
import com.amazonaws.services.autoscaling.model.AutoScalingGroup;
import com.amazonaws.services.autoscaling.model.AutoScalingInstanceDetails;
import com.amazonaws.services.autoscaling.model.CreateAutoScalingGroupRequest;
import com.amazonaws.services.autoscaling.model.CreateAutoScalingGroupResult;
import com.amazonaws.services.autoscaling.model.DeleteAutoScalingGroupRequest;
import com.amazonaws.services.autoscaling.model.DeletePolicyRequest;
import com.amazonaws.services.autoscaling.model.DescribeAutoScalingGroupsRequest;
import com.amazonaws.services.autoscaling.model.DescribeAutoScalingGroupsResult;
import com.amazonaws.services.autoscaling.model.DescribeAutoScalingInstancesRequest;
import com.amazonaws.services.autoscaling.model.DescribeAutoScalingInstancesResult;
import com.amazonaws.services.autoscaling.model.DescribeInstanceRefreshesRequest;
import com.amazonaws.services.autoscaling.model.DescribeInstanceRefreshesResult;
import com.amazonaws.services.autoscaling.model.DescribeLaunchConfigurationsRequest;
import com.amazonaws.services.autoscaling.model.DescribeLaunchConfigurationsResult;
import com.amazonaws.services.autoscaling.model.DescribePoliciesRequest;
import com.amazonaws.services.autoscaling.model.DescribePoliciesResult;
import com.amazonaws.services.autoscaling.model.Instance;
import com.amazonaws.services.autoscaling.model.InstanceRefresh;
import com.amazonaws.services.autoscaling.model.LaunchConfiguration;
import com.amazonaws.services.autoscaling.model.LaunchTemplateSpecification;
import com.amazonaws.services.autoscaling.model.PutScalingPolicyRequest;
import com.amazonaws.services.autoscaling.model.PutScalingPolicyResult;
import com.amazonaws.services.autoscaling.model.RefreshPreferences;
import com.amazonaws.services.autoscaling.model.ScalingPolicy;
import com.amazonaws.services.autoscaling.model.StartInstanceRefreshRequest;
import com.amazonaws.services.autoscaling.model.StartInstanceRefreshResult;
import com.amazonaws.services.autoscaling.model.UpdateAutoScalingGroupRequest;
import com.amazonaws.services.autoscaling.model.UpdateAutoScalingGroupResult;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.AmazonEC2Exception;
import com.amazonaws.services.ec2.model.CreateLaunchTemplateRequest;
import com.amazonaws.services.ec2.model.CreateLaunchTemplateResult;
import com.amazonaws.services.ec2.model.CreateLaunchTemplateVersionRequest;
import com.amazonaws.services.ec2.model.CreateLaunchTemplateVersionResult;
import com.amazonaws.services.ec2.model.DescribeLaunchTemplatesRequest;
import com.amazonaws.services.ec2.model.DescribeLaunchTemplatesResult;
import com.amazonaws.services.ec2.model.LaunchTemplate;
import com.amazonaws.services.ec2.model.LaunchTemplateVersion;
import com.amazonaws.services.ec2.model.RequestLaunchTemplateData;
import com.google.common.util.concurrent.ExecutionError;
import com.google.common.util.concurrent.TimeLimiter;
import com.google.common.util.concurrent.UncheckedExecutionException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(CDP)
public class AsgSdkManager {
  public static final int STEADY_STATE_INTERVAL_IN_SECONDS = 20;
  private static final String INSTANCE_REFRESH_STATUS_SUCCESSFUL = "Successful";
  private static final String INSTANCE_STATUS_IN_SERVICE = "InService";

  private enum AwsClientType { EC2, ASG }

  private final Supplier<AmazonEC2Client> ec2ClientSupplier;
  private final Supplier<AmazonAutoScalingClient> asgClientSupplier;
  private final Integer steadyStateTimeOutInMinutes;
  private final TimeLimiter timeLimiter;
  @Setter private LogCallback logCallback;

  @Builder
  public AsgSdkManager(Supplier<AmazonEC2Client> ec2ClientSupplier, Supplier<AmazonAutoScalingClient> asgClientSupplier,
      LogCallback logCallback, Integer steadyStateTimeOutInMinutes, TimeLimiter timeLimiter) {
    this.ec2ClientSupplier = ec2ClientSupplier;
    this.asgClientSupplier = asgClientSupplier;
    this.logCallback = logCallback;
    this.steadyStateTimeOutInMinutes = steadyStateTimeOutInMinutes;
    this.timeLimiter = timeLimiter;
  }

  private CloseableAmazonWebServiceClient<AmazonEC2Client> getEc2Client() {
    return new CloseableAmazonWebServiceClient(ec2ClientSupplier.get());
  }

  private CloseableAmazonWebServiceClient<AmazonAutoScalingClient> getAsgClient() {
    return new CloseableAmazonWebServiceClient(asgClientSupplier.get());
  }

  private <C extends AmazonWebServiceClient, R> R awsCall(Function<C, R> call, AwsClientType type) {
    try (CloseableAmazonWebServiceClient<C> client = (type == AwsClientType.EC2)
            ? (CloseableAmazonWebServiceClient<C>) getEc2Client()
            : (CloseableAmazonWebServiceClient<C>) getAsgClient()) {
      return call.apply(client.getClient());
    } catch (Exception e) {
      log.error(e.getMessage());
      Exception sanitizeException = ExceptionMessageSanitizer.sanitizeException(e);
      throw new InvalidRequestException(ExceptionUtils.getMessage(sanitizeException), sanitizeException);
    }
  }

  private <T> T ec2Call(Function<AmazonEC2Client, T> call) {
    return awsCall(call, AwsClientType.EC2);
  }

  private <T> T asgCall(Function<AmazonAutoScalingClient, T> call) {
    return awsCall(call, AwsClientType.ASG);
  }

  public LaunchTemplate createLaunchTemplate(String asgName, CreateLaunchTemplateRequest createLaunchTemplateRequest) {
    createLaunchTemplateRequest.setLaunchTemplateName(asgName);
    String operationName = format("Create launchTemplate %s", asgName);
    info("Operation `%s` has started", operationName);

    CreateLaunchTemplateResult createLaunchTemplateResult =
        ec2Call(ec2Client -> ec2Client.createLaunchTemplate(createLaunchTemplateRequest));
    info("Operation `%s` ended successfully", operationName);
    return createLaunchTemplateResult.getLaunchTemplate();
  }

  public LaunchTemplate getLaunchTemplate(String asgName) {
    DescribeLaunchTemplatesRequest describeLaunchTemplatesRequest =
        new DescribeLaunchTemplatesRequest().withLaunchTemplateNames(asgName);
    try {
      DescribeLaunchTemplatesResult describeLaunchTemplatesResult =
          ec2Call(ec2Client -> ec2Client.describeLaunchTemplates(describeLaunchTemplatesRequest));
      List<LaunchTemplate> resultList = describeLaunchTemplatesResult.getLaunchTemplates();
      if (isEmpty(resultList)) {
        return null;
      }
      return resultList.get(0);
    } catch (AmazonEC2Exception e) {
      // AmazonEC2Exception is thrown if LaunchTemplate is not found
      return null;
    }
  }

  public LaunchTemplateVersion createLaunchTemplateVersion(
      LaunchTemplate launchTemplate, RequestLaunchTemplateData requestLaunchTemplateData) {
    String launchTemplateName = launchTemplate.getLaunchTemplateName();
    CreateLaunchTemplateVersionRequest createLaunchTemplateVersionRequest =
        new CreateLaunchTemplateVersionRequest()
            .withLaunchTemplateName(launchTemplateName)
            .withSourceVersion(launchTemplate.getLatestVersionNumber().toString())
            .withLaunchTemplateData(requestLaunchTemplateData);

    String operationName = format("Create new version for launchTemplate %s", launchTemplateName);
    info("Operation `%s` has started", operationName);
    CreateLaunchTemplateVersionResult createLaunchTemplateVersionResult =
        ec2Call(ec2Client -> ec2Client.createLaunchTemplateVersion(createLaunchTemplateVersionRequest));
    info("Operation `%s` ended successfully", operationName);

    return createLaunchTemplateVersionResult.getLaunchTemplateVersion();
  }

  public CreateAutoScalingGroupResult createASG(
      String asgName, String launchTemplateVersion, CreateAutoScalingGroupRequest createAutoScalingGroupRequest) {
    createAutoScalingGroupRequest.withAutoScalingGroupName(asgName).withLaunchTemplate(
        new LaunchTemplateSpecification().withLaunchTemplateName(asgName).withVersion(launchTemplateVersion));

    return asgCall(asgClient -> asgClient.createAutoScalingGroup(createAutoScalingGroupRequest));
  }

  public UpdateAutoScalingGroupResult updateASG(
      String asgName, String launchTemplateVersion, UpdateAutoScalingGroupRequest updateAutoScalingGroupRequest) {
    LaunchTemplateSpecification launchTemplateSpecification =
        new LaunchTemplateSpecification().withLaunchTemplateName(asgName).withVersion(launchTemplateVersion);

    UpdateAutoScalingGroupRequest request =
        new UpdateAutoScalingGroupRequest().withAutoScalingGroupName(asgName).withLaunchTemplate(
            launchTemplateSpecification);
    return asgCall(asgClient -> asgClient.updateAutoScalingGroup(request));
  }

  public AutoScalingGroup getASG(String asgName) {
    DescribeAutoScalingGroupsRequest describeAutoScalingGroupsRequest =
        new DescribeAutoScalingGroupsRequest().withAutoScalingGroupNames(asgName);

    DescribeAutoScalingGroupsResult describeAutoScalingGroupsResult =
        asgCall(asgClient -> asgClient.describeAutoScalingGroups(describeAutoScalingGroupsRequest));

    List<AutoScalingGroup> resultList = describeAutoScalingGroupsResult.getAutoScalingGroups();

    if (isEmpty(resultList)) {
      return null;
    }

    return resultList.get(0);
  }

  public void deleteAsgService(AutoScalingGroup autoScalingGroup) {
    String asgName = autoScalingGroup.getAutoScalingGroupName();
    String operationName = format("Delete Asg %s", asgName);
    info("Operation `%s` has started", operationName);
    DeleteAutoScalingGroupRequest deleteAutoScalingGroupRequest =
        new DeleteAutoScalingGroupRequest().withAutoScalingGroupName(asgName).withForceDelete(true);
    asgCall(asgClient -> asgClient.deleteAutoScalingGroup(deleteAutoScalingGroupRequest));
    waitReadyState(asgName, this::checkAsgDeleted, operationName);
    infoBold("Operation `%s` ended successfully", operationName);
  }

  public List<AutoScalingInstanceDetails> getAutoScalingInstanceDetails(AutoScalingGroup autoScalingGroup) {
    List<String> instanceIds =
        autoScalingGroup.getInstances().stream().map(Instance::getInstanceId).collect(Collectors.toList());
    if (isEmpty(instanceIds)) {
      return Collections.emptyList();
    }
    DescribeAutoScalingInstancesRequest describeAutoScalingInstancesRequest =
        new DescribeAutoScalingInstancesRequest().withInstanceIds(instanceIds);
    DescribeAutoScalingInstancesResult describeAutoScalingInstancesResult =
        asgCall(asgClient -> asgClient.describeAutoScalingInstances(describeAutoScalingInstancesRequest));
    return describeAutoScalingInstancesResult.getAutoScalingInstances();
  }

  public boolean checkAllInstancesInReadyState(String asgName) {
    AutoScalingGroup autoScalingGroup = getASG(asgName);
    List<Instance> instances = autoScalingGroup.getInstances();
    if (isEmpty(instances)) {
      return false;
    }

    long nrOfInstancesReady =
        instances.stream()
            .filter(instance -> INSTANCE_STATUS_IN_SERVICE.equalsIgnoreCase(instance.getLifecycleState()))
            .count();
    long totalNrOfInstances = instances.size();

    info("%d/%d instances are healthy", nrOfInstancesReady, totalNrOfInstances);

    return nrOfInstancesReady == totalNrOfInstances;
  }

  public boolean checkAsgDeleted(String asgName) {
    info("Checking if service `%s` is deleted", asgName);
    AutoScalingGroup autoScalingGroup = getASG(asgName);
    return autoScalingGroup == null;
  }

  public void waitReadyState(String asgName, Predicate<String> predicate, String operationName) {
    info("Waiting for operation `%s` to reach steady state", operationName);
    info("Polling every %d seconds", STEADY_STATE_INTERVAL_IN_SECONDS);
    try {
      HTimeLimiter.callInterruptible(timeLimiter, Duration.ofMinutes(steadyStateTimeOutInMinutes), () -> {
        while (!predicate.test(asgName)) {
          sleep(ofSeconds(STEADY_STATE_INTERVAL_IN_SECONDS));
        }
        return true;
      });
    } catch (ExecutionException | UncheckedExecutionException | ExecutionError e) {
      String errorMessage = format("Exception while waiting for steady state for `%s` operation. Error message: [%s]",
          operationName, e.getMessage());
      error(errorMessage);
      throw new InvalidRequestException(errorMessage, e.getCause());
    } catch (TimeoutException | InterruptedException e) {
      String errorMessage = format("Timed out while waiting for steady state for `%s` operation", operationName);
      error(errorMessage);
      throw new InvalidRequestException(errorMessage, e);
    } catch (Exception e) {
      String errorMessage = format("Exception while waiting for steady state for `%s` operation. Error message: [%s]",
          operationName, e.getMessage());
      error(errorMessage);
      throw new InvalidRequestException(errorMessage, e);
    }
  }

  public StartInstanceRefreshResult startInstanceRefresh(
      String asgName, Boolean skipMatching, Integer instanceWarmup, Integer minimumHealthyPercentage) {
    StartInstanceRefreshRequest startInstanceRefreshRequest =
        new StartInstanceRefreshRequest().withAutoScalingGroupName(asgName).withPreferences(
            new RefreshPreferences()
                .withSkipMatching(skipMatching)
                .withInstanceWarmup(instanceWarmup)
                .withMinHealthyPercentage(minimumHealthyPercentage));

    return asgCall(asgClient -> asgClient.startInstanceRefresh(startInstanceRefreshRequest));
  }

  public boolean checkInstanceRefreshReady(String asgName, String instanceRefreshId) {
    DescribeInstanceRefreshesRequest describeInstanceRefreshesRequest =
        new DescribeInstanceRefreshesRequest()
            .withInstanceRefreshIds(Arrays.asList(instanceRefreshId))
            .withAutoScalingGroupName(asgName);

    DescribeInstanceRefreshesResult describeInstanceRefreshesResult =
        asgCall(asgClient -> asgClient.describeInstanceRefreshes(describeInstanceRefreshesRequest));
    List<InstanceRefresh> instanceRefreshList = describeInstanceRefreshesResult.getInstanceRefreshes();

    Set<String> statuses = instanceRefreshList.stream().map(InstanceRefresh::getStatus).collect(Collectors.toSet());
    return statuses.size() == 1 && statuses.contains(INSTANCE_REFRESH_STATUS_SUCCESSFUL);
  }

  public List<ScalingPolicy> listAllScalingPoliciesOfAsg(String asgName) {
    List<ScalingPolicy> scalingPolicies = newArrayList();
    String nextToken = null;
    do {
      DescribePoliciesRequest request =
          new DescribePoliciesRequest().withAutoScalingGroupName(asgName).withNextToken(nextToken);
      DescribePoliciesResult result = asgCall(asgClient -> asgClient.describePolicies(request));

      if (isNotEmpty(result.getScalingPolicies())) {
        scalingPolicies.addAll(result.getScalingPolicies());
      }
      nextToken = result.getNextToken();
    } while (nextToken != null);
    return scalingPolicies;
  }

  public void clearAllScalingPoliciesForAsg(String asgName) {
    List<ScalingPolicy> scalingPolicies = listAllScalingPoliciesOfAsg(asgName);
    if (isEmpty(scalingPolicies)) {
      logCallback.saveExecutionLog(format("No policies found attached to Asg: [%s]", asgName));
      return;
    }
    scalingPolicies.forEach(scalingPolicy -> {
      DeletePolicyRequest deletePolicyRequest =
          new DeletePolicyRequest().withAutoScalingGroupName(asgName).withPolicyName(scalingPolicy.getPolicyARN());
      asgCall(asgClient -> asgClient.deletePolicy(deletePolicyRequest));
    });
  }

  public void attachScalingPoliciesToAsg(String asgName, List<PutScalingPolicyRequest> putScalingPolicyRequestList) {
    if (putScalingPolicyRequestList.isEmpty()) {
      logCallback.saveExecutionLog(format("No scaling policy found which should be attached to Asg: [%s]", asgName));
      return;
    }
    putScalingPolicyRequestList.forEach(putScalingPolicyRequest -> {
      logCallback.saveExecutionLog(
          format("Attaching policy `%s` to Asg: [%s]", putScalingPolicyRequest.getPolicyName(), asgName));
      putScalingPolicyRequest.setAutoScalingGroupName(asgName);
      PutScalingPolicyResult putScalingPolicyResult =
          asgCall(asgClient -> asgClient.putScalingPolicy(putScalingPolicyRequest));
      logCallback.saveExecutionLog(format("Created policy with Arn: [%s]", putScalingPolicyResult.getPolicyARN()));
    });
    return;
  }

  public LaunchConfiguration getLaunchConfiguration(String asgName) {
    DescribeLaunchConfigurationsRequest describeLaunchConfigurationsRequest = new DescribeLaunchConfigurationsRequest();
    describeLaunchConfigurationsRequest.setLaunchConfigurationNames(Collections.singleton(asgName));
    DescribeLaunchConfigurationsResult describeLaunchConfigurationsResult =
        asgCall(asgClient -> asgClient.describeLaunchConfigurations(describeLaunchConfigurationsRequest));
    return describeLaunchConfigurationsResult.getLaunchConfigurations().get(0);
  }

  public void info(String msg, Object... params) {
    info(msg, false, params);
  }

  public void infoBold(String msg, Object... params) {
    info(msg, true, params);
  }

  public void info(String msg, boolean isBold, Object... params) {
    String formatted = format(msg, params);
    log.info(formatted);
    if (isBold) {
      logCallback.saveExecutionLog(color(formatted, White, Bold), INFO);
    } else {
      logCallback.saveExecutionLog(formatted);
    }
  }

  public void error(String msg, String... params) {
    String formatted = format(msg, params);
    logCallback.saveExecutionLog(formatted, ERROR);
    log.error(formatted);
  }
}
