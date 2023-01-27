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
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.aws.v2.ecs.ElbV2Client;
import io.harness.concurrent.HTimeLimiter;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.logging.LogCallback;

import software.wings.service.impl.aws.client.CloseableAmazonWebServiceClient;

import com.amazonaws.AmazonWebServiceClient;
import com.amazonaws.services.autoscaling.AmazonAutoScalingClient;
import com.amazonaws.services.autoscaling.model.AttachLoadBalancerTargetGroupsRequest;
import com.amazonaws.services.autoscaling.model.AttachLoadBalancersRequest;
import com.amazonaws.services.autoscaling.model.AutoScalingGroup;
import com.amazonaws.services.autoscaling.model.AutoScalingInstanceDetails;
import com.amazonaws.services.autoscaling.model.CreateAutoScalingGroupRequest;
import com.amazonaws.services.autoscaling.model.CreateAutoScalingGroupResult;
import com.amazonaws.services.autoscaling.model.CreateOrUpdateTagsRequest;
import com.amazonaws.services.autoscaling.model.DeleteAutoScalingGroupRequest;
import com.amazonaws.services.autoscaling.model.DeleteLifecycleHookRequest;
import com.amazonaws.services.autoscaling.model.DeletePolicyRequest;
import com.amazonaws.services.autoscaling.model.DeleteTagsRequest;
import com.amazonaws.services.autoscaling.model.DescribeAutoScalingGroupsRequest;
import com.amazonaws.services.autoscaling.model.DescribeAutoScalingGroupsResult;
import com.amazonaws.services.autoscaling.model.DescribeAutoScalingInstancesRequest;
import com.amazonaws.services.autoscaling.model.DescribeAutoScalingInstancesResult;
import com.amazonaws.services.autoscaling.model.DescribeInstanceRefreshesRequest;
import com.amazonaws.services.autoscaling.model.DescribeInstanceRefreshesResult;
import com.amazonaws.services.autoscaling.model.DescribeLifecycleHooksRequest;
import com.amazonaws.services.autoscaling.model.DescribeLoadBalancerTargetGroupsRequest;
import com.amazonaws.services.autoscaling.model.DescribeLoadBalancerTargetGroupsResult;
import com.amazonaws.services.autoscaling.model.DescribeLoadBalancersRequest;
import com.amazonaws.services.autoscaling.model.DescribeLoadBalancersResult;
import com.amazonaws.services.autoscaling.model.DescribePoliciesRequest;
import com.amazonaws.services.autoscaling.model.DescribePoliciesResult;
import com.amazonaws.services.autoscaling.model.DescribeTagsRequest;
import com.amazonaws.services.autoscaling.model.DescribeTagsResult;
import com.amazonaws.services.autoscaling.model.DetachLoadBalancerTargetGroupsRequest;
import com.amazonaws.services.autoscaling.model.DetachLoadBalancersRequest;
import com.amazonaws.services.autoscaling.model.Filter;
import com.amazonaws.services.autoscaling.model.Instance;
import com.amazonaws.services.autoscaling.model.InstanceRefresh;
import com.amazonaws.services.autoscaling.model.LaunchTemplateSpecification;
import com.amazonaws.services.autoscaling.model.LifecycleHook;
import com.amazonaws.services.autoscaling.model.LifecycleHookSpecification;
import com.amazonaws.services.autoscaling.model.LoadBalancerState;
import com.amazonaws.services.autoscaling.model.LoadBalancerTargetGroupState;
import com.amazonaws.services.autoscaling.model.PutLifecycleHookRequest;
import com.amazonaws.services.autoscaling.model.PutScalingPolicyRequest;
import com.amazonaws.services.autoscaling.model.PutScalingPolicyResult;
import com.amazonaws.services.autoscaling.model.RefreshPreferences;
import com.amazonaws.services.autoscaling.model.ScalingPolicy;
import com.amazonaws.services.autoscaling.model.StartInstanceRefreshRequest;
import com.amazonaws.services.autoscaling.model.StartInstanceRefreshResult;
import com.amazonaws.services.autoscaling.model.Tag;
import com.amazonaws.services.autoscaling.model.TagDescription;
import com.amazonaws.services.autoscaling.model.UpdateAutoScalingGroupRequest;
import com.amazonaws.services.ec2.AmazonEC2Client;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
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
import software.amazon.awssdk.services.elasticloadbalancingv2.model.Action;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.ActionTypeEnum;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DescribeRulesRequest;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DescribeRulesResponse;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.ForwardActionConfig;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.ModifyListenerRequest;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.ModifyRuleRequest;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.Rule;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.TargetGroupTuple;

@Slf4j
@OwnedBy(CDP)
public class AsgSdkManager {
  public static final int STEADY_STATE_INTERVAL_IN_SECONDS = 20;
  private static final String INSTANCE_REFRESH_STATUS_SUCCESSFUL = "Successful";
  private static final String INSTANCE_STATUS_IN_SERVICE = "InService";

  private enum AwsClientType { EC2, ASG }
  public static final String BG_VERSION = "BG_VERSION";
  public static final String BG_GREEN = "GREEN";
  public static final String BG_BLUE = "BLUE";

  private final Supplier<AmazonEC2Client> ec2ClientSupplier;
  private final Supplier<AmazonAutoScalingClient> asgClientSupplier;
  private final ElbV2Client elbV2Client;
  private final Integer steadyStateTimeOutInMinutes;
  private final TimeLimiter timeLimiter;
  @Setter private LogCallback logCallback;

  @Builder
  public AsgSdkManager(Supplier<AmazonEC2Client> ec2ClientSupplier, Supplier<AmazonAutoScalingClient> asgClientSupplier,
      ElbV2Client elbV2Client, LogCallback logCallback, Integer steadyStateTimeOutInMinutes, TimeLimiter timeLimiter) {
    this.ec2ClientSupplier = ec2ClientSupplier;
    this.asgClientSupplier = asgClientSupplier;
    this.elbV2Client = elbV2Client;
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
    infoBold("Operation `%s` ended successfully", operationName);
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
    } catch (InvalidRequestException e) {
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
    infoBold("Operation `%s` ended successfully", operationName);

    return createLaunchTemplateVersionResult.getLaunchTemplateVersion();
  }

  public CreateAutoScalingGroupResult createASG(
      String asgName, String launchTemplateVersion, CreateAutoScalingGroupRequest createAutoScalingGroupRequest) {
    createAutoScalingGroupRequest.withAutoScalingGroupName(asgName).withLaunchTemplate(
        new LaunchTemplateSpecification().withLaunchTemplateName(asgName).withVersion(launchTemplateVersion));

    return asgCall(asgClient -> asgClient.createAutoScalingGroup(createAutoScalingGroupRequest));
  }

  public void updateASG(
      String asgName, String launchTemplateVersion, CreateAutoScalingGroupRequest createAutoScalingGroupRequest) {
    LaunchTemplateSpecification launchTemplateSpecification =
        new LaunchTemplateSpecification().withLaunchTemplateName(asgName).withVersion(launchTemplateVersion);

    UpdateAutoScalingGroupRequest updateAutoScalingGroupRequest =
        createAsgRequestToUpdateAsgRequestMapper(createAutoScalingGroupRequest);

    updateAutoScalingGroupRequest.setAutoScalingGroupName(asgName);
    updateAutoScalingGroupRequest.setLaunchTemplate(launchTemplateSpecification);
    asgCall(asgClient -> asgClient.updateAutoScalingGroup(updateAutoScalingGroupRequest));

    updateTags(asgName, createAutoScalingGroupRequest);

    updateLifecyleHooks(asgName, createAutoScalingGroupRequest);

    updateLoadBalancers(asgName, createAutoScalingGroupRequest);

    updateLoadBalancerTargetGroups(asgName, createAutoScalingGroupRequest);
  }

  private void updateTags(String asgName, CreateAutoScalingGroupRequest createAutoScalingGroupRequest) {
    List<TagDescription> currentTagDescriptionsList = new ArrayList<>();
    String nextToken = null;
    Filter filter = new Filter().withName("auto-scaling-group").withValues(asgName);

    do {
      DescribeTagsRequest describeTagsRequest = new DescribeTagsRequest().withFilters(filter).withNextToken(nextToken);

      DescribeTagsResult describeTagsResult = asgCall(asgClient -> asgClient.describeTags(describeTagsRequest));
      if (isNotEmpty(describeTagsResult.getTags())) {
        currentTagDescriptionsList.addAll(describeTagsResult.getTags());
      }
      nextToken = describeTagsResult.getNextToken();
    } while (nextToken != null);

    List<Tag> currentTagsList = new ArrayList<>();
    if (isNotEmpty(currentTagDescriptionsList)) {
      currentTagDescriptionsList.forEach(currentTagDescription -> {
        Tag tagTemp = new Tag();
        tagTemp.setKey(currentTagDescription.getKey());
        tagTemp.setResourceId(currentTagDescription.getResourceId());
        tagTemp.setResourceType(currentTagDescription.getResourceType());
        tagTemp.setValue(currentTagDescription.getValue());
        tagTemp.setPropagateAtLaunch(currentTagDescription.getPropagateAtLaunch());
        currentTagsList.add(tagTemp);
      });
    }

    if (isNotEmpty(currentTagsList)) {
      DeleteTagsRequest deleteTagsRequest = new DeleteTagsRequest().withTags(currentTagsList);
      asgCall(asgClient -> asgClient.deleteTags(deleteTagsRequest));
    }

    List<Tag> tags = createAutoScalingGroupRequest.getTags();
    List<Tag> tagsList = new ArrayList<>();
    if (isNotEmpty(tags)) {
      tags.forEach(tag -> {
        if (tag.getPropagateAtLaunch() == null) {
          tag.setPropagateAtLaunch(true);
        }
        if (tag.getResourceId() == null) {
          tag.setResourceId(asgName);
        }
        if (tag.getResourceType() == null) {
          tag.setResourceType("auto-scaling-group");
        }
        tagsList.add(tag);
      });

      CreateOrUpdateTagsRequest createOrUpdateTagsRequest = new CreateOrUpdateTagsRequest();
      createOrUpdateTagsRequest.setTags(tagsList);
      asgCall(asgClient -> asgClient.createOrUpdateTags(createOrUpdateTagsRequest));
    }
  }

  private void updateLifecyleHooks(String asgName, CreateAutoScalingGroupRequest createAutoScalingGroupRequest) {
    DescribeLifecycleHooksRequest describeLifecycleHooksRequest = new DescribeLifecycleHooksRequest();
    describeLifecycleHooksRequest.setAutoScalingGroupName(asgName);

    List<LifecycleHook> lifecycleHooks =
        (asgCall(asgClient -> asgClient.describeLifecycleHooks(describeLifecycleHooksRequest))).getLifecycleHooks();
    if (isNotEmpty(lifecycleHooks)) {
      lifecycleHooks.forEach(lifecycleHook -> {
        DeleteLifecycleHookRequest deleteLifecycleHookRequest = new DeleteLifecycleHookRequest();
        deleteLifecycleHookRequest.setLifecycleHookName(lifecycleHook.getLifecycleHookName());
        deleteLifecycleHookRequest.setAutoScalingGroupName(asgName);
        asgCall(asgClient -> asgClient.deleteLifecycleHook(deleteLifecycleHookRequest));
      });
    }

    List<LifecycleHookSpecification> lifecycleHooksSpecificationList =
        createAutoScalingGroupRequest.getLifecycleHookSpecificationList();
    if (isNotEmpty(lifecycleHooksSpecificationList)) {
      lifecycleHooksSpecificationList.forEach(lifecycleHookSpecification -> {
        PutLifecycleHookRequest putLifecycleHookRequest = new PutLifecycleHookRequest();
        putLifecycleHookRequest.setAutoScalingGroupName(asgName);
        putLifecycleHookRequest.setLifecycleHookName(lifecycleHookSpecification.getLifecycleHookName());
        putLifecycleHookRequest.setLifecycleTransition(lifecycleHookSpecification.getLifecycleTransition());
        putLifecycleHookRequest.setDefaultResult(lifecycleHookSpecification.getDefaultResult());
        putLifecycleHookRequest.setHeartbeatTimeout(lifecycleHookSpecification.getHeartbeatTimeout());
        putLifecycleHookRequest.setNotificationTargetARN(lifecycleHookSpecification.getNotificationTargetARN());
        putLifecycleHookRequest.setNotificationMetadata(lifecycleHookSpecification.getNotificationMetadata());
        putLifecycleHookRequest.setRoleARN(lifecycleHookSpecification.getRoleARN());
        asgCall(asgClient -> asgClient.putLifecycleHook(putLifecycleHookRequest));
      });
    }
  }

  private void updateLoadBalancers(String asgName, CreateAutoScalingGroupRequest createAutoScalingGroupRequest) {
    List<LoadBalancerState> loadBalancerStates = new ArrayList<>();
    String nextToken = null;
    do {
      DescribeLoadBalancersRequest describeloadBalancersRequest =
          new DescribeLoadBalancersRequest().withAutoScalingGroupName(asgName).withNextToken(nextToken);

      DescribeLoadBalancersResult describeLoadBalancersResult =
          asgCall(asgClient -> asgClient.describeLoadBalancers(describeloadBalancersRequest));
      if (isNotEmpty(describeLoadBalancersResult.getLoadBalancers())) {
        loadBalancerStates.addAll(describeLoadBalancersResult.getLoadBalancers());
      }
      nextToken = describeLoadBalancersResult.getNextToken();
    } while (nextToken != null);

    if (isNotEmpty(loadBalancerStates)) {
      loadBalancerStates.forEach(loadBalancerState -> {
        DetachLoadBalancersRequest detachLoadBalancersRequest =
            new DetachLoadBalancersRequest().withAutoScalingGroupName(asgName).withLoadBalancerNames(
                loadBalancerState.getLoadBalancerName());
        asgCall(asgClient -> asgClient.detachLoadBalancers(detachLoadBalancersRequest));
      });
    }

    List<String> loadBalancerNames = createAutoScalingGroupRequest.getLoadBalancerNames();
    if (isNotEmpty(loadBalancerNames)) {
      loadBalancerNames.forEach(loadBalancerName -> {
        AttachLoadBalancersRequest attachLoadBalancersRequest =
            new AttachLoadBalancersRequest().withAutoScalingGroupName(asgName).withLoadBalancerNames(loadBalancerName);
        asgCall(asgClient -> asgClient.attachLoadBalancers(attachLoadBalancersRequest));
      });
    }
  }
  private void updateLoadBalancerTargetGroups(
      String asgName, CreateAutoScalingGroupRequest createAutoScalingGroupRequest) {
    List<LoadBalancerTargetGroupState> loadBalancerTargetGroupStates = new ArrayList<>();
    String nextToken = null;
    do {
      DescribeLoadBalancerTargetGroupsRequest describeLoadBalancerTargetGroupsRequest =
          new DescribeLoadBalancerTargetGroupsRequest().withAutoScalingGroupName(asgName).withNextToken(nextToken);

      DescribeLoadBalancerTargetGroupsResult describeLoadBalancerTargetGroupsResult =
          asgCall(asgClient -> asgClient.describeLoadBalancerTargetGroups(describeLoadBalancerTargetGroupsRequest));
      if (isNotEmpty(describeLoadBalancerTargetGroupsResult.getLoadBalancerTargetGroups())) {
        loadBalancerTargetGroupStates.addAll(describeLoadBalancerTargetGroupsResult.getLoadBalancerTargetGroups());
      }
      nextToken = describeLoadBalancerTargetGroupsResult.getNextToken();
    } while (nextToken != null);

    if (isNotEmpty(loadBalancerTargetGroupStates)) {
      loadBalancerTargetGroupStates.forEach(loadBalancerTargetGroupState -> {
        DetachLoadBalancerTargetGroupsRequest detachLoadBalancerTargetGroupsRequest =
            new DetachLoadBalancerTargetGroupsRequest().withAutoScalingGroupName(asgName).withTargetGroupARNs(
                loadBalancerTargetGroupState.getLoadBalancerTargetGroupARN());
        asgCall(asgClient -> asgClient.detachLoadBalancerTargetGroups(detachLoadBalancerTargetGroupsRequest));
      });
    }

    List<String> targetGroupARNs = createAutoScalingGroupRequest.getTargetGroupARNs();
    if (isNotEmpty(targetGroupARNs)) {
      targetGroupARNs.forEach(targetGroupARN -> {
        AttachLoadBalancerTargetGroupsRequest attachLoadBalancerTargetGroupsRequest =
            new AttachLoadBalancerTargetGroupsRequest().withAutoScalingGroupName(asgName).withTargetGroupARNs(
                targetGroupARN);
        asgCall(asgClient -> asgClient.attachLoadBalancerTargetGroups(attachLoadBalancerTargetGroupsRequest));
      });
    }
  }

  public List<LifecycleHookSpecification> getLifeCycleHookSpecificationList(String asgName) {
    DescribeLifecycleHooksRequest describeLifecycleHooksRequest = new DescribeLifecycleHooksRequest();
    describeLifecycleHooksRequest.setAutoScalingGroupName(asgName);

    List<LifecycleHook> lifecycleHooks =
        (asgCall(asgClient -> asgClient.describeLifecycleHooks(describeLifecycleHooksRequest))).getLifecycleHooks();

    List<LifecycleHookSpecification> lifecycleHookSpecificationList = new ArrayList<>();

    if (isNotEmpty(lifecycleHooks)) {
      lifecycleHooks.forEach(lifecycleHook -> {
        LifecycleHookSpecification lifecycleHookSpecification = new LifecycleHookSpecification();
        lifecycleHookSpecification.setLifecycleHookName(lifecycleHook.getLifecycleHookName());
        lifecycleHookSpecification.setLifecycleTransition(lifecycleHook.getLifecycleTransition());
        lifecycleHookSpecification.setDefaultResult(lifecycleHook.getDefaultResult());
        lifecycleHookSpecification.setNotificationMetadata(lifecycleHook.getNotificationMetadata());
        lifecycleHookSpecification.setNotificationTargetARN(lifecycleHook.getNotificationTargetARN());
        lifecycleHookSpecification.setHeartbeatTimeout(lifecycleHook.getHeartbeatTimeout());
        lifecycleHookSpecification.setRoleARN(lifecycleHook.getRoleARN());
        lifecycleHookSpecificationList.add(lifecycleHookSpecification);
      });
    }
    return lifecycleHookSpecificationList;
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

  public void deleteAsg(String asgName) {
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

  public boolean checkAsgDownsizedToZero(String asgName) {
    AutoScalingGroup autoScalingGroup = getASG(asgName);
    List<Instance> instances = autoScalingGroup.getInstances();

    long totalNrOfInstances = instances.size();
    return totalNrOfInstances == 0;
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

  public void waitInstanceRefreshSteadyState(String asgName, String instanceRefreshId, String operationName) {
    info("Waiting for operation `%s` to reach steady state", operationName);
    info("Polling every %d seconds", STEADY_STATE_INTERVAL_IN_SECONDS);
    try {
      HTimeLimiter.callInterruptible(timeLimiter, Duration.ofMinutes(steadyStateTimeOutInMinutes), () -> {
        while (!checkInstanceRefreshReady(asgName, instanceRefreshId)) {
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
      logCallback.saveExecutionLog(
          format("No policies found which are currently attached with autoscaling group: [%s] to detach", asgName));
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
      logCallback.saveExecutionLog(
          format("No scaling policy provided which is needed be attached to autoscaling group: %s", asgName));
      return;
    }
    putScalingPolicyRequestList.forEach(putScalingPolicyRequest -> {
      putScalingPolicyRequest.setAutoScalingGroupName(asgName);
      PutScalingPolicyResult putScalingPolicyResult =
          asgCall(asgClient -> asgClient.putScalingPolicy(putScalingPolicyRequest));
      logCallback.saveExecutionLog(
          format("Attached scaling policy with Arn: %s", putScalingPolicyResult.getPolicyARN()));
    });
  }

  private UpdateAutoScalingGroupRequest createAsgRequestToUpdateAsgRequestMapper(
      CreateAutoScalingGroupRequest createAutoScalingGroupRequest) {
    String createAutoScalingGroupRequestContent = AsgContentParser.toString(createAutoScalingGroupRequest, false);
    return AsgContentParser.parseJson(createAutoScalingGroupRequestContent, UpdateAutoScalingGroupRequest.class, false);
  }

  public void modifySpecificListenerRule(
      String region, String listenerRuleArn, List<String> targetGroupArnsList, AwsInternalConfig awsInternalConfig) {
    Collection<software.amazon.awssdk.services.elasticloadbalancingv2.model.TargetGroupTuple> targetGroups =
        new ArrayList<>();
    if (isNotEmpty(targetGroupArnsList)) {
      targetGroupArnsList.forEach(targetGroupArn -> {
        TargetGroupTuple targetGroupTuple = TargetGroupTuple.builder().targetGroupArn(targetGroupArn).weight(1).build();
        targetGroups.add(targetGroupTuple);
      });
      ModifyRuleRequest modifyRuleRequest =
          ModifyRuleRequest.builder()
              .ruleArn(listenerRuleArn)
              .actions(Action.builder()
                           .type(ActionTypeEnum.FORWARD)
                           .forwardConfig(ForwardActionConfig.builder().targetGroups(targetGroups).build())
                           .build())
              .build();
      elbV2Client.modifyRule(awsInternalConfig, modifyRuleRequest, region);
    }
  }

  public boolean checkForDefaultRule(
      String region, String listenerArn, String listenerRuleArn, AwsInternalConfig awsInternalConfig) {
    String nextToken = null;
    do {
      DescribeRulesRequest describeRulesRequest =
          DescribeRulesRequest.builder().listenerArn(listenerArn).marker(nextToken).pageSize(10).build();
      DescribeRulesResponse describeRulesResponse =
          elbV2Client.describeRules(awsInternalConfig, describeRulesRequest, region);
      List<Rule> currentRules = describeRulesResponse.rules();
      if (EmptyPredicate.isNotEmpty(currentRules)) {
        Optional<Rule> defaultRule = currentRules.stream().filter(Rule::isDefault).findFirst();
        if (defaultRule.isPresent() && listenerRuleArn.equalsIgnoreCase(defaultRule.get().ruleArn())) {
          return true;
        }
      }
      nextToken = describeRulesResponse.nextMarker();
    } while (nextToken != null);
    return false;
  }

  public void modifyDefaultListenerRule(
      String region, String listenerArn, List<String> targetGroupArnsList, AwsInternalConfig awsInternalConfig) {
    Collection<software.amazon.awssdk.services.elasticloadbalancingv2.model.TargetGroupTuple> targetGroups =
        new ArrayList<>();
    if (isNotEmpty(targetGroupArnsList)) {
      targetGroupArnsList.forEach(targetGroupArn -> {
        TargetGroupTuple targetGroupTuple = TargetGroupTuple.builder().targetGroupArn(targetGroupArn).weight(1).build();
        targetGroups.add(targetGroupTuple);
      });

      ModifyListenerRequest modifyListenerRequest =
          ModifyListenerRequest.builder()
              .listenerArn(listenerArn)
              .defaultActions(Action.builder()
                                  .type(ActionTypeEnum.FORWARD)
                                  .forwardConfig(ForwardActionConfig.builder().targetGroups(targetGroups).build())
                                  .build())
              .build();
      elbV2Client.modifyListener(awsInternalConfig, modifyListenerRequest, region);
    }
  }

  public void updateBGTags(String asgName, String newTagValue) {
    Tag newTag = new Tag();
    newTag.withKey(BG_VERSION)
        .withValue(newTagValue)
        .withPropagateAtLaunch(true)
        .withResourceId(asgName)
        .withResourceType("auto-scaling-group");

    CreateOrUpdateTagsRequest createOrUpdateTagsRequest = new CreateOrUpdateTagsRequest();
    createOrUpdateTagsRequest.withTags(newTag);
    asgCall(asgClient -> asgClient.createOrUpdateTags(createOrUpdateTagsRequest));
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
