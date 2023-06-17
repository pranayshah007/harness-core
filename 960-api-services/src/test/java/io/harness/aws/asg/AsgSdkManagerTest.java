/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.aws.asg;

import static io.harness.rule.OwnerRule.LOVISH_BANSAL;

import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static software.amazon.awssdk.services.elasticloadbalancingv2.model.TargetHealthStateEnum.HEALTHY;
import static software.amazon.awssdk.services.elasticloadbalancingv2.model.TargetHealthStateEnum.UNHEALTHY;

import io.harness.CategoryTest;
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.aws.v2.ecs.ElbV2Client;
import io.harness.category.element.UnitTests;
import io.harness.logging.LogCallback;
import io.harness.rule.Owner;

import com.amazonaws.services.autoscaling.AmazonAutoScalingClient;
import com.amazonaws.services.autoscaling.model.AutoScalingGroup;
import com.amazonaws.services.autoscaling.model.CreateAutoScalingGroupRequest;
import com.amazonaws.services.autoscaling.model.CreateAutoScalingGroupResult;
import com.amazonaws.services.autoscaling.model.DeleteAutoScalingGroupResult;
import com.amazonaws.services.autoscaling.model.DescribeAutoScalingGroupsResult;
import com.amazonaws.services.autoscaling.model.DescribeLifecycleHooksResult;
import com.amazonaws.services.autoscaling.model.DescribePoliciesRequest;
import com.amazonaws.services.autoscaling.model.DescribePoliciesResult;
import com.amazonaws.services.autoscaling.model.Instance;
import com.amazonaws.services.autoscaling.model.LifecycleHook;
import com.amazonaws.services.autoscaling.model.LifecycleHookSpecification;
import com.amazonaws.services.autoscaling.model.RefreshPreferences;
import com.amazonaws.services.autoscaling.model.ScalingPolicy;
import com.amazonaws.services.autoscaling.model.StartInstanceRefreshRequest;
import com.amazonaws.services.autoscaling.model.StartInstanceRefreshResult;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.CreateLaunchTemplateRequest;
import com.amazonaws.services.ec2.model.CreateLaunchTemplateResult;
import com.amazonaws.services.ec2.model.CreateLaunchTemplateVersionRequest;
import com.amazonaws.services.ec2.model.CreateLaunchTemplateVersionResult;
import com.amazonaws.services.ec2.model.DescribeLaunchTemplatesResult;
import com.amazonaws.services.ec2.model.LaunchTemplate;
import com.amazonaws.services.ec2.model.LaunchTemplateVersion;
import com.amazonaws.services.ec2.model.RequestLaunchTemplateData;
import com.google.common.util.concurrent.TimeLimiter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.Action;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.ActionTypeEnum;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DescribeListenersRequest;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DescribeListenersResponse;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DescribeRulesResponse;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DescribeTargetHealthRequest;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DescribeTargetHealthResponse;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.ForwardActionConfig;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.Listener;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.ModifyRuleRequest;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.Rule;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.TargetDescription;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.TargetGroupTuple;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.TargetHealth;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.TargetHealthDescription;

public class AsgSdkManagerTest extends CategoryTest {
  private static final String INSTANCE_STATUS_IN_SERVICE = "InService";
  private static final int STEADY_STATE_INTERVAL_IN_SECONDS = 20;
  private AsgSdkManager asgSdkManager;
  private final AmazonEC2Client amazonEC2Client = Mockito.mock(AmazonEC2Client.class);
  private final AmazonAutoScalingClient amazonAutoScalingClient = Mockito.mock(AmazonAutoScalingClient.class);
  private final Supplier<AmazonEC2Client> ec2ClientSupplier = () -> amazonEC2Client;
  private final Supplier<AmazonAutoScalingClient> asgClientSupplier = () -> amazonAutoScalingClient;
  private final LogCallback logCallback = Mockito.mock(LogCallback.class);
  private final TimeLimiter timeLimiter = Mockito.mock(TimeLimiter.class);
  private final ElbV2Client elbV2Client = Mockito.mock(ElbV2Client.class);

  @Before
  public void setUp() throws IllegalAccessException {
    AsgSdkManager asgSdkManager1 = AsgSdkManager.builder()
                                       .ec2ClientSupplier(ec2ClientSupplier)
                                       .asgClientSupplier(asgClientSupplier)
                                       .logCallback(logCallback)
                                       .steadyStateTimeOutInMinutes(10)
                                       .timeLimiter(timeLimiter)
                                       .elbV2Client(elbV2Client)
                                       .build();
    asgSdkManager = spy(asgSdkManager1);
  }

  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void testCreateLaunchTemplate() {
    CreateLaunchTemplateRequest createLaunchTemplateRequest = new CreateLaunchTemplateRequest();
    CreateLaunchTemplateResult createLaunchTemplateResult =
        new CreateLaunchTemplateResult().withLaunchTemplate(new LaunchTemplate().withLaunchTemplateName("lt"));
    doReturn(createLaunchTemplateResult).when(amazonEC2Client).createLaunchTemplate(any());
    LaunchTemplate launchTemplate = asgSdkManager.createLaunchTemplate("abcd", createLaunchTemplateRequest);
    assertThat(launchTemplate.getLaunchTemplateName()).isEqualTo("lt");
  }

  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void testGetLaunchTemplate() {
    DescribeLaunchTemplatesResult describeLaunchTemplatesResult =
        new DescribeLaunchTemplatesResult().withLaunchTemplates(
            Collections.singletonList(new LaunchTemplate().withLaunchTemplateName("lt")));
    doReturn(describeLaunchTemplatesResult).when(amazonEC2Client).describeLaunchTemplates(any());
    LaunchTemplate launchTemplate = asgSdkManager.getLaunchTemplate("abcd");
    assertThat(launchTemplate.getLaunchTemplateName()).isEqualTo("lt");
  }

  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void testCreateLaunchTemplateVersion() {
    LaunchTemplate launchTemplate = new LaunchTemplate().withLaunchTemplateName("lt").withLatestVersionNumber(1L);
    RequestLaunchTemplateData requestLaunchTemplateData = new RequestLaunchTemplateData();

    CreateLaunchTemplateVersionResult createLaunchTemplateVersionResult =
        new CreateLaunchTemplateVersionResult().withLaunchTemplateVersion(new LaunchTemplateVersion());

    doReturn(createLaunchTemplateVersionResult)
        .when(amazonEC2Client)
        .createLaunchTemplateVersion(any(CreateLaunchTemplateVersionRequest.class));

    LaunchTemplateVersion result = asgSdkManager.createLaunchTemplateVersion(launchTemplate, requestLaunchTemplateData);

    assertThat(result).isNotNull();
  }

  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void testCreateASG() {
    String asgName = "asg";
    String launchTemplateVersion = "1";

    CreateAutoScalingGroupRequest createAutoScalingGroupRequest = new CreateAutoScalingGroupRequest();

    CreateAutoScalingGroupResult createAutoScalingGroupResult = new CreateAutoScalingGroupResult();

    doReturn(createAutoScalingGroupResult)
        .when(amazonAutoScalingClient)
        .createAutoScalingGroup(any(CreateAutoScalingGroupRequest.class));

    CreateAutoScalingGroupResult result =
        asgSdkManager.createASG(asgName, launchTemplateVersion, createAutoScalingGroupRequest);

    assertThat(result).isNotNull();
  }

  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void testGetLifeCycleHookSpecificationList() {
    DescribeLifecycleHooksResult describeLifecycleHooksResult = new DescribeLifecycleHooksResult();
    List<LifecycleHook> lifecycleHooks = new ArrayList<>();

    // Creating a sample LifecycleHook object
    LifecycleHook lifecycleHook = new LifecycleHook();
    lifecycleHook.setLifecycleHookName("hook1");
    lifecycleHook.setLifecycleTransition("transition1");
    lifecycleHook.setDefaultResult("result1");
    lifecycleHook.setNotificationMetadata("metadata1");
    lifecycleHook.setNotificationTargetARN("targetARN1");
    lifecycleHook.setHeartbeatTimeout(60);
    lifecycleHook.setRoleARN("roleARN1");

    // Adding the LifecycleHook to the list
    lifecycleHooks.add(lifecycleHook);

    // Setting the LifecycleHook list in the DescribeLifecycleHooksResult
    describeLifecycleHooksResult.setLifecycleHooks(lifecycleHooks);

    // Mocking the behavior of the asgClient.describeLifecycleHooks method
    doReturn(describeLifecycleHooksResult).when(amazonAutoScalingClient).describeLifecycleHooks(any());

    // Calling the getLifeCycleHookSpecificationList method
    List<LifecycleHookSpecification> lifecycleHookSpecificationList =
        asgSdkManager.getLifeCycleHookSpecificationList("asgName");

    // Assertion
    assertThat(lifecycleHookSpecificationList).hasSize(1);
    LifecycleHookSpecification lifecycleHookSpecification = lifecycleHookSpecificationList.get(0);
    assertThat(lifecycleHookSpecification.getLifecycleHookName()).isEqualTo("hook1");
    assertThat(lifecycleHookSpecification.getLifecycleTransition()).isEqualTo("transition1");
    assertThat(lifecycleHookSpecification.getDefaultResult()).isEqualTo("result1");
    assertThat(lifecycleHookSpecification.getNotificationMetadata()).isEqualTo("metadata1");
    assertThat(lifecycleHookSpecification.getNotificationTargetARN()).isEqualTo("targetARN1");
    assertThat(lifecycleHookSpecification.getHeartbeatTimeout()).isEqualTo(60);
    assertThat(lifecycleHookSpecification.getRoleARN()).isEqualTo("roleARN1");
  }

  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void testGetASG() {
    DescribeAutoScalingGroupsResult describeAutoScalingGroupsResult = new DescribeAutoScalingGroupsResult();
    List<AutoScalingGroup> autoScalingGroups = new ArrayList<>();

    // Creating a sample AutoScalingGroup object
    AutoScalingGroup autoScalingGroup = new AutoScalingGroup();
    autoScalingGroup.setAutoScalingGroupName("asg1");

    // Adding the AutoScalingGroup to the list
    autoScalingGroups.add(autoScalingGroup);

    // Setting the AutoScalingGroup list in the DescribeAutoScalingGroupsResult
    describeAutoScalingGroupsResult.setAutoScalingGroups(autoScalingGroups);

    // Mocking the behavior of the asgClient.describeAutoScalingGroups method
    doReturn(describeAutoScalingGroupsResult).when(amazonAutoScalingClient).describeAutoScalingGroups(any());

    // Calling the getASG method
    AutoScalingGroup resultASG = asgSdkManager.getASG("asgName");

    // Assertion
    assertThat(resultASG).isNotNull();
    assertThat(resultASG.getAutoScalingGroupName()).isEqualTo("asg1");
  }

  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void testDeleteAsg() {
    String asgName = "asgName";

    // Mocking the behavior of the asgClient.deleteAutoScalingGroup method
    doReturn(new DeleteAutoScalingGroupResult()).when(amazonAutoScalingClient).deleteAutoScalingGroup(any());

    // Calling the deleteAsg method
    asgSdkManager.deleteAsg(asgName);

    // Verifying the method invocations
    verify(amazonAutoScalingClient, times(1)).deleteAutoScalingGroup(any());
    verify(asgSdkManager, times(1)).waitReadyState(eq(asgName), any(), any());

    // Verifying the log messages
    verify(asgSdkManager, times(1)).info("Deleting Asg %s", asgName);
    verify(asgSdkManager, times(1)).info("Waiting for deletion of Asg %s to complete", asgName);
    verify(asgSdkManager, times(1)).infoBold("Deleted Asg %s successfully", asgName);
  }

  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void testCheckAllInstancesInReadyState() {
    String asgName = "asgName";

    // Creating a sample AutoScalingGroup object
    AutoScalingGroup autoScalingGroup = new AutoScalingGroup();

    // Creating a list of sample instances
    List<Instance> instances = new ArrayList<>();

    // Creating a sample Instance object in-service
    Instance instance1 = new Instance();
    instance1.setLifecycleState(INSTANCE_STATUS_IN_SERVICE);

    // Creating a sample Instance object out-of-service
    Instance instance2 = new Instance();
    instance2.setLifecycleState("outOfService");

    // Adding the instances to the list
    instances.add(instance1);
    instances.add(instance2);

    // Setting the instances in the AutoScalingGroup
    autoScalingGroup.setInstances(instances);

    // Mocking the behavior of the getASG method
    doReturn(autoScalingGroup).when(asgSdkManager).getASG(eq(asgName));

    // Calling the checkAllInstancesInReadyState method
    boolean result = asgSdkManager.checkAllInstancesInReadyState(asgName);

    // Assertion
    assertThat(result).isFalse();

    // Verifying the log message
    verify(asgSdkManager, times(1)).info(anyString(), anyLong(), anyLong());
  }

  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void testStartInstanceRefresh() {
    String asgName = "asgName";
    Boolean skipMatching = true;
    Integer instanceWarmup = 60;
    Integer minimumHealthyPercentage = 80;

    // Creating a sample StartInstanceRefreshResult object
    StartInstanceRefreshResult startInstanceRefreshResult = new StartInstanceRefreshResult();

    // Mocking the behavior of the asgClient.startInstanceRefresh method
    doReturn(startInstanceRefreshResult).when(amazonAutoScalingClient).startInstanceRefresh(any());

    // Calling the startInstanceRefresh method
    StartInstanceRefreshResult result =
        asgSdkManager.startInstanceRefresh(asgName, skipMatching, instanceWarmup, minimumHealthyPercentage);

    // Assertion
    assertThat(result).isNotNull();

    // Verifying the method invocation
    verify(amazonAutoScalingClient, times(1)).startInstanceRefresh(any());

    // Verifying the arguments passed to startInstanceRefreshRequest
    ArgumentCaptor<StartInstanceRefreshRequest> requestCaptor =
        ArgumentCaptor.forClass(StartInstanceRefreshRequest.class);
    verify(amazonAutoScalingClient).startInstanceRefresh(requestCaptor.capture());
    StartInstanceRefreshRequest capturedRequest = requestCaptor.getValue();
    assertThat(capturedRequest.getAutoScalingGroupName()).isEqualTo(asgName);
    RefreshPreferences preferences = capturedRequest.getPreferences();
    assertThat(preferences.getSkipMatching()).isEqualTo(skipMatching);
    assertThat(preferences.getInstanceWarmup()).isEqualTo(instanceWarmup);
    assertThat(preferences.getMinHealthyPercentage()).isEqualTo(minimumHealthyPercentage);
  }

  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void testWaitInstanceRefreshSteadyState() throws Exception {
    String asgName = "asgName";
    String instanceRefreshId = "instanceRefreshId";
    String operationName = "operationName";

    // Mocking the behavior of the checkInstanceRefreshReady method
    doReturn(true).when(asgSdkManager).checkInstanceRefreshReady(eq(asgName), eq(instanceRefreshId));

    // Calling the waitInstanceRefreshSteadyState method
    asgSdkManager.waitInstanceRefreshSteadyState(asgName, instanceRefreshId, operationName);

    // Verifying the method invocations
    verify(asgSdkManager, times(1)).info("Polling every %d seconds", STEADY_STATE_INTERVAL_IN_SECONDS);
  }

  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void testListAllScalingPoliciesOfAsg() {
    String asgName = "asgName";

    // Creating a sample ScalingPolicy object
    ScalingPolicy scalingPolicy = new ScalingPolicy();
    scalingPolicy.setPolicyName("policyName");

    // Creating a sample DescribePoliciesResult object
    DescribePoliciesResult describePoliciesResult = new DescribePoliciesResult();
    describePoliciesResult.setScalingPolicies(Collections.singletonList(scalingPolicy));
    describePoliciesResult.setNextToken(null);

    // Mocking the behavior of the asgClient.describePolicies method
    doReturn(describePoliciesResult).when(amazonAutoScalingClient).describePolicies(any());

    // Calling the listAllScalingPoliciesOfAsg method
    List<ScalingPolicy> result = asgSdkManager.listAllScalingPoliciesOfAsg(asgName);

    // Assertion
    assertThat(result).isNotNull();
    assertThat(result).hasSize(1);
    assertThat(result.get(0).getPolicyName()).isEqualTo("policyName");

    // Verifying the method invocations
    verify(asgSdkManager, times(1)).info("Getting ScalingPolicies for Asg %s", asgName);
    verify(amazonAutoScalingClient, times(1)).describePolicies(any());

    // Verifying the arguments passed to describePoliciesRequest
    ArgumentCaptor<DescribePoliciesRequest> requestCaptor = ArgumentCaptor.forClass(DescribePoliciesRequest.class);
    verify(amazonAutoScalingClient).describePolicies(requestCaptor.capture());
    DescribePoliciesRequest capturedRequest = requestCaptor.getValue();
    assertThat(capturedRequest.getAutoScalingGroupName()).isEqualTo(asgName);
    assertThat(capturedRequest.getNextToken()).isNull();
  }

  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void testModifySpecificListenerRule() {
    String region = "us-west-2";
    String listenerRuleArn = "listenerRuleArn";
    List<String> targetGroupArnsList = Arrays.asList("targetGroupArn1", "targetGroupArn2");
    AwsInternalConfig awsInternalConfig = AwsInternalConfig.builder().build();

    // Creating a sample TargetGroupTuple object
    software.amazon.awssdk.services.elasticloadbalancingv2.model.TargetGroupTuple targetGroupTuple =
        software.amazon.awssdk.services.elasticloadbalancingv2.model.TargetGroupTuple.builder()
            .targetGroupArn("targetGroupArn")
            .weight(1)
            .build();

    // Creating a sample ModifyRuleRequest object
    ModifyRuleRequest modifyRuleRequest =
        ModifyRuleRequest.builder()
            .ruleArn(listenerRuleArn)
            .actions(
                Action.builder()
                    .type(ActionTypeEnum.FORWARD)
                    .forwardConfig(
                        ForwardActionConfig.builder().targetGroups(Collections.singletonList(targetGroupTuple)).build())
                    .build())
            .build();

    // Calling the modifySpecificListenerRule method
    asgSdkManager.modifySpecificListenerRule(region, listenerRuleArn, targetGroupArnsList, awsInternalConfig);

    verify(elbV2Client, times(1)).modifyRule(eq(awsInternalConfig), any(), eq(region));
  }

  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void testGetTargetGroupArnsFromLoadBalancer() {
    String region = "us-west-2";
    String listenerArn = "listenerArn";
    String listenerRuleArn = "listenerRuleArn";
    String loadBalancer = "loadBalancer";
    AwsInternalConfig awsInternalConfig = AwsInternalConfig.builder().build();

    // Creating a sample DescribeLoadBalancersRequest object
    software.amazon.awssdk.services.elasticloadbalancingv2.model
        .DescribeLoadBalancersRequest describeLoadBalancersRequest =
        software.amazon.awssdk.services.elasticloadbalancingv2.model.DescribeLoadBalancersRequest.builder()
            .names(loadBalancer)
            .build();

    // Creating a sample DescribeLoadBalancersResponse object
    software.amazon.awssdk.services.elasticloadbalancingv2.model
        .DescribeLoadBalancersResponse describeLoadBalancersResponse =
        software.amazon.awssdk.services.elasticloadbalancingv2.model.DescribeLoadBalancersResponse.builder()
            .loadBalancers(Collections.singletonList(
                software.amazon.awssdk.services.elasticloadbalancingv2.model.LoadBalancer.builder()
                    .loadBalancerArn("loadBalancerArn")
                    .build()))
            .build();

    // Mocking the behavior of the describeLoadBalancer method
    when(elbV2Client.describeLoadBalancer(eq(awsInternalConfig), eq(describeLoadBalancersRequest), eq(region)))
        .thenReturn(describeLoadBalancersResponse);

    // Creating a sample DescribeListenersRequest object
    DescribeListenersRequest describeListenersRequest =
        DescribeListenersRequest.builder().loadBalancerArn("loadBalancerArn").marker(null).pageSize(10).build();

    // Creating a sample DescribeListenersResponse object
    DescribeListenersResponse describeListenersResponse =
        DescribeListenersResponse.builder()
            .listeners(Collections.singletonList(Listener.builder().listenerArn(listenerArn).build()))
            .build();

    // Mocking the behavior of the describeListener method
    when(elbV2Client.describeListener(eq(awsInternalConfig), eq(describeListenersRequest), eq(region)))
        .thenReturn(describeListenersResponse);

    when(elbV2Client.describeRules(any(), any(), any())).thenReturn(DescribeRulesResponse.builder().build());

    // Mocking the behavior of the getListenerRulesForListener method
    List<String> targetGroupArns = Arrays.asList("targetGroupArn1", "targetGroupArn2");
    when(asgSdkManager.getListenerRulesForListener(eq(awsInternalConfig), eq(region), eq(listenerArn)))
        .thenReturn(Collections.singletonList(
            Rule.builder()
                .ruleArn(listenerRuleArn)
                .actions(Collections.singletonList(
                    Action.builder()
                        .forwardConfig(ForwardActionConfig.builder()
                                           .targetGroups(targetGroupArns.stream()
                                                             .map(targetGroupArn
                                                                 -> TargetGroupTuple.builder()
                                                                        .targetGroupArn(targetGroupArn)
                                                                        .weight(1)
                                                                        .build())
                                                             .collect(Collectors.toList()))
                                           .build())
                        .build()))
                .build()));

    // Calling the getTargetGroupArnsFromLoadBalancer method
    List<String> result = asgSdkManager.getTargetGroupArnsFromLoadBalancer(
        region, listenerArn, listenerRuleArn, loadBalancer, awsInternalConfig);

    // Assertion
    assertThat(result).containsExactly("targetGroupArn1", "targetGroupArn2");

    // Verifying the method invocations
    verify(elbV2Client, times(1))
        .describeLoadBalancer(eq(awsInternalConfig), eq(describeLoadBalancersRequest), eq(region));
    verify(elbV2Client, times(1)).describeListener(eq(awsInternalConfig), eq(describeListenersRequest), eq(region));
    verify(asgSdkManager, times(1)).getListenerRulesForListener(eq(awsInternalConfig), eq(region), eq(listenerArn));
  }

  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void testCheckAllTargetsRegistered_AllTargetsRegistered() {
    AwsInternalConfig awsInternalConfig = AwsInternalConfig.builder().build();
    // Arrange
    String region = "region";
    List<String> targetIds = Arrays.asList("target1", "target2", "target3");
    List<String> targetGroupARNList = Arrays.asList("targetGroup1", "targetGroup2", "targetGroup3");
    DescribeTargetHealthResponse response1 = mock(DescribeTargetHealthResponse.class);
    when(response1.targetHealthDescriptions())
        .thenReturn(Arrays.asList(TargetHealthDescription.builder()
                                      .target(TargetDescription.builder().id("target1").build())
                                      .targetHealth(TargetHealth.builder().state(HEALTHY).build())
                                      .build(),
            TargetHealthDescription.builder()
                .target(TargetDescription.builder().id("target2").build())
                .targetHealth(TargetHealth.builder().state(HEALTHY).build())
                .build(),
            TargetHealthDescription.builder()
                .target(TargetDescription.builder().id("target3").build())
                .targetHealth(TargetHealth.builder().state(HEALTHY).build())
                .build()));

    DescribeTargetHealthResponse response2 = mock(DescribeTargetHealthResponse.class);
    when(response2.targetHealthDescriptions())
        .thenReturn(Arrays.asList(TargetHealthDescription.builder()
                                      .target(TargetDescription.builder().id("target1").build())
                                      .targetHealth(TargetHealth.builder().state(HEALTHY).build())
                                      .build(),
            TargetHealthDescription.builder()
                .target(TargetDescription.builder().id("target2").build())
                .targetHealth(TargetHealth.builder().state(HEALTHY).build())
                .build(),
            TargetHealthDescription.builder()
                .target(TargetDescription.builder().id("target3").build())
                .targetHealth(TargetHealth.builder().state(HEALTHY).build())
                .build()));

    DescribeTargetHealthResponse response3 = mock(DescribeTargetHealthResponse.class);
    when(response3.targetHealthDescriptions())
        .thenReturn(Arrays.asList(TargetHealthDescription.builder()
                                      .target(TargetDescription.builder().id("target1").build())
                                      .targetHealth(TargetHealth.builder().state(HEALTHY).build())
                                      .build(),
            TargetHealthDescription.builder()
                .target(TargetDescription.builder().id("target2").build())
                .targetHealth(TargetHealth.builder().state(HEALTHY).build())
                .build(),
            TargetHealthDescription.builder()
                .target(TargetDescription.builder().id("target3").build())
                .targetHealth(TargetHealth.builder().state(HEALTHY).build())
                .build()));

    when(elbV2Client.describeTargetHealth(eq(awsInternalConfig), any(DescribeTargetHealthRequest.class), any()))
        .thenReturn(response1, response2, response3);

    boolean result = asgSdkManager.checkAllTargetsRegistered(targetIds, targetGroupARNList, awsInternalConfig, region);

    assertTrue(result);
  }

  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void testCheckAllTargetsRegistered_NotAllTargetsRegistered() {
    // Arrange
    AwsInternalConfig awsInternalConfig = AwsInternalConfig.builder().build();
    String region = "region";
    List<String> targetIds = Arrays.asList("target1", "target2", "target3");
    List<String> targetGroupARNList = Arrays.asList("targetGroup1", "targetGroup2", "targetGroup3");
    DescribeTargetHealthResponse response1 = mock(DescribeTargetHealthResponse.class);
    when(response1.targetHealthDescriptions())
        .thenReturn(Arrays.asList(TargetHealthDescription.builder()
                                      .target(TargetDescription.builder().id("target1").build())
                                      .targetHealth(TargetHealth.builder().state(HEALTHY).build())
                                      .build(),
            TargetHealthDescription.builder()
                .target(TargetDescription.builder().id("target2").build())
                .targetHealth(TargetHealth.builder().state(HEALTHY).build())
                .build(),
            TargetHealthDescription.builder()
                .target(TargetDescription.builder().id("target3").build())
                .targetHealth(TargetHealth.builder().state(HEALTHY).build())
                .build()));

    DescribeTargetHealthResponse response2 = mock(DescribeTargetHealthResponse.class);
    when(response2.targetHealthDescriptions())
        .thenReturn(Arrays.asList(TargetHealthDescription.builder()
                                      .target(TargetDescription.builder().id("target1").build())
                                      .targetHealth(TargetHealth.builder().state(HEALTHY).build())
                                      .build(),
            TargetHealthDescription.builder()
                .target(TargetDescription.builder().id("target2").build())
                .targetHealth(TargetHealth.builder().state(HEALTHY).build())
                .build()));

    DescribeTargetHealthResponse response3 = mock(DescribeTargetHealthResponse.class);
    when(response3.targetHealthDescriptions())
        .thenReturn(Arrays.asList(TargetHealthDescription.builder()
                                      .target(TargetDescription.builder().id("target1").build())
                                      .targetHealth(TargetHealth.builder().state(HEALTHY).build())
                                      .build(),
            TargetHealthDescription.builder()
                .target(TargetDescription.builder().id("target2").build())
                .targetHealth(TargetHealth.builder().state(HEALTHY).build())
                .build(),
            TargetHealthDescription.builder()
                .target(TargetDescription.builder().id("target3").build())
                .targetHealth(TargetHealth.builder().state(UNHEALTHY).build())
                .build()));

    when(elbV2Client.describeTargetHealth(any(), any(DescribeTargetHealthRequest.class), any()))
        .thenReturn(response1, response2, response3);

    // Act
    boolean result = asgSdkManager.checkAllTargetsRegistered(targetIds, targetGroupARNList, awsInternalConfig, region);

    // Assert
    assertFalse(result);
  }

  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void testCheckAllTargetsRegistered_EmptyTargetIds() {
    // Arrange
    AwsInternalConfig awsInternalConfig = AwsInternalConfig.builder().build();
    String region = "region";
    List<String> targetIds = Arrays.asList();
    List<String> targetGroupARNList = Arrays.asList("targetGroup1", "targetGroup2", "targetGroup3");
    // Act
    boolean result = asgSdkManager.checkAllTargetsRegistered(targetIds, targetGroupARNList, awsInternalConfig, region);

    // Assert
    assertTrue(result);
  }
}
