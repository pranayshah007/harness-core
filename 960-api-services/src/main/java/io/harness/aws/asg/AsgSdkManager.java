/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.aws.asg;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.logging.LogLevel.ERROR;
import static io.harness.logging.LogLevel.INFO;
import static io.harness.threading.Morpheus.sleep;

import static software.wings.beans.LogColor.White;
import static software.wings.beans.LogHelper.color;
import static software.wings.beans.LogWeight.Bold;

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
import com.amazonaws.services.autoscaling.model.DescribeAutoScalingGroupsRequest;
import com.amazonaws.services.autoscaling.model.DescribeAutoScalingGroupsResult;
import com.amazonaws.services.autoscaling.model.DescribeAutoScalingInstancesRequest;
import com.amazonaws.services.autoscaling.model.DescribeAutoScalingInstancesResult;
import com.amazonaws.services.autoscaling.model.Instance;
import com.amazonaws.services.autoscaling.model.LaunchTemplateSpecification;
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
import java.util.Collections;
import java.util.List;
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

  // TODO
  public UpdateAutoScalingGroupResult updateASG(String asgName, String launchTemplateVersion) {
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

  public String getLaunchTemplateVersion(String asgName, String launchTemplateContent) {
    CreateLaunchTemplateRequest createLaunchTemplateRequest =
        AsgContentParser.parseJson(launchTemplateContent, CreateLaunchTemplateRequest.class);
    LaunchTemplate launchTemplate = getLaunchTemplate(asgName);
    if (launchTemplate != null) {
      LaunchTemplateVersion launchTemplateVersion =
          createLaunchTemplateVersion(launchTemplate, createLaunchTemplateRequest.getLaunchTemplateData());
      return launchTemplateVersion.getVersionNumber().toString();
    } else {
      launchTemplate = createLaunchTemplate(asgName, createLaunchTemplateRequest);
      return launchTemplate.getLatestVersionNumber().toString();
    }
  }

  public AutoScalingGroup createAsgService(String launchTemplateContent,
      CreateAutoScalingGroupRequest createAutoScalingGroupRequest, Integer nrOfInstances) {
    String asgName = createAutoScalingGroupRequest.getAutoScalingGroupName();
    String operationName = format("Create Asg %s", asgName);
    info("Operation `%s` has started", operationName);
    String launchTemplateVersion = getLaunchTemplateVersion(asgName, launchTemplateContent);
    createAutoScalingGroupRequest.withMinSize(nrOfInstances)
        .withMaxSize(nrOfInstances)
        .withDesiredCapacity(nrOfInstances);
    createASG(asgName, launchTemplateVersion, createAutoScalingGroupRequest);
    waitReadyState(asgName, this::checkAllInstancesInReadyState, operationName);
    infoBold("Operation `%s` ended successfully", operationName);
    return getASG(asgName);
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
