/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.aws.delegate;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.ADWAIT;
import static io.harness.rule.OwnerRule.RAGHVENDRA;
import static io.harness.rule.OwnerRule.SATYAM;

import static software.wings.service.impl.aws.model.AwsConstants.MAX_TRAFFIC_SHIFT_WEIGHT;
import static software.wings.service.impl.aws.model.AwsConstants.MIN_TRAFFIC_SHIFT_WEIGHT;

import static com.amazonaws.services.elasticloadbalancingv2.model.ActionTypeEnum.Forward;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.Optional.empty;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import io.harness.annotations.dev.OwnedBy;
import io.harness.aws.util.AwsCallTracker;
import io.harness.category.element.UnitTests;
import io.harness.delegate.task.aws.AwsElbListener;
import io.harness.delegate.task.aws.AwsElbListenerRuleData;
import io.harness.delegate.task.aws.AwsLoadBalancerDetails;
import io.harness.delegate.task.aws.LbDetailsForAlbTrafficShift;
import io.harness.exception.InvalidRequestException;
import io.harness.rule.Owner;
import io.harness.spotinst.model.SpotInstConstants;

import software.wings.WingsBaseTest;
import software.wings.beans.AwsConfig;
import software.wings.beans.command.ExecutionLogCallback;
import software.wings.service.intfc.security.EncryptionService;

import com.amazonaws.services.elasticloadbalancing.model.DescribeInstanceHealthResult;
import com.amazonaws.services.elasticloadbalancing.model.InstanceState;
import com.amazonaws.services.elasticloadbalancing.model.LoadBalancerDescription;
import com.amazonaws.services.elasticloadbalancingv2.AmazonElasticLoadBalancing;
import com.amazonaws.services.elasticloadbalancingv2.AmazonElasticLoadBalancingClient;
import com.amazonaws.services.elasticloadbalancingv2.model.Action;
import com.amazonaws.services.elasticloadbalancingv2.model.ActionTypeEnum;
import com.amazonaws.services.elasticloadbalancingv2.model.CreateTargetGroupResult;
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeListenersResult;
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeLoadBalancersResult;
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeRulesResult;
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeTargetGroupsResult;
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeTargetHealthResult;
import com.amazonaws.services.elasticloadbalancingv2.model.ForwardActionConfig;
import com.amazonaws.services.elasticloadbalancingv2.model.Listener;
import com.amazonaws.services.elasticloadbalancingv2.model.LoadBalancer;
import com.amazonaws.services.elasticloadbalancingv2.model.ModifyListenerRequest;
import com.amazonaws.services.elasticloadbalancingv2.model.ModifyRuleRequest;
import com.amazonaws.services.elasticloadbalancingv2.model.Rule;
import com.amazonaws.services.elasticloadbalancingv2.model.TargetDescription;
import com.amazonaws.services.elasticloadbalancingv2.model.TargetGroup;
import com.amazonaws.services.elasticloadbalancingv2.model.TargetGroupTuple;
import com.amazonaws.services.elasticloadbalancingv2.model.TargetHealth;
import com.amazonaws.services.elasticloadbalancingv2.model.TargetHealthDescription;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;

@OwnedBy(CDP)
public class AwsElbHelperServiceDelegateImplTest extends WingsBaseTest {
  @Mock private EncryptionService mockEncryptionService;
  @Mock private AwsCallTracker mockTracker;
  @Spy @InjectMocks private AwsElbHelperServiceDelegateImpl awsElbHelperServiceDelegate;

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testListClassicLoadBalancers() {
    com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancingClient mockClassicClient =
        mock(com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancingClient.class);
    doReturn(mockClassicClient).when(awsElbHelperServiceDelegate).getClassicElbClient(any(), any());
    doReturn(null).when(mockEncryptionService).decrypt(any(), anyList(), eq(false));
    doReturn(new com.amazonaws.services.elasticloadbalancing.model.DescribeLoadBalancersResult()
                 .withLoadBalancerDescriptions(new LoadBalancerDescription().withLoadBalancerName("lb1"),
                     new LoadBalancerDescription().withLoadBalancerName("lb2")))
        .when(mockClassicClient)
        .describeLoadBalancers(any());
    doNothing().when(mockTracker).trackELBCall(anyString());
    doNothing().when(mockTracker).trackClassicELBCall(anyString());
    List<String> classisLbNames =
        awsElbHelperServiceDelegate.listClassicLoadBalancers(AwsConfig.builder().build(), emptyList(), "us-east-1");
    assertThat(classisLbNames).isNotNull();
    assertThat(classisLbNames.size()).isEqualTo(2);
    assertThat(classisLbNames.get(0)).isEqualTo("lb1");
    assertThat(classisLbNames.get(1)).isEqualTo("lb2");
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testListApplicationLoadBalancers() {
    AmazonElasticLoadBalancingClient mockV2Client = mock(AmazonElasticLoadBalancingClient.class);
    doReturn(mockV2Client).when(awsElbHelperServiceDelegate).getAmazonElasticLoadBalancingClientV2(any(), any());
    doReturn(null).when(mockEncryptionService).decrypt(any(), anyList(), eq(false));
    doReturn(new DescribeLoadBalancersResult().withLoadBalancers(
                 new LoadBalancer().withLoadBalancerName("lb1").withType("application"),
                 new LoadBalancer().withLoadBalancerName("lb2").withType("application"),
                 new LoadBalancer().withLoadBalancerName("lb3").withType("network")))
        .when(mockV2Client)
        .describeLoadBalancers(any());
    doNothing().when(mockTracker).trackELBCall(anyString());
    doNothing().when(mockTracker).trackClassicELBCall(anyString());
    List<AwsLoadBalancerDetails> appLbNames = awsElbHelperServiceDelegate.listApplicationLoadBalancerDetails(
        AwsConfig.builder().build(), emptyList(), "us-east-1");
    assertThat(appLbNames).isNotNull();
    assertThat(appLbNames.size()).isEqualTo(2);
    assertThat(appLbNames.get(0).getName()).isEqualTo("lb1");
    assertThat(appLbNames.get(0).getType()).isEqualTo("application");
    assertThat(appLbNames.get(1).getName()).isEqualTo("lb2");
    assertThat(appLbNames.get(1).getType()).isEqualTo("application");
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testListEleasticLoadBalancers() {
    AmazonElasticLoadBalancingClient mockV2Client = mock(AmazonElasticLoadBalancingClient.class);
    doReturn(mockV2Client).when(awsElbHelperServiceDelegate).getAmazonElasticLoadBalancingClientV2(any(), any());
    doReturn(null).when(mockEncryptionService).decrypt(any(), anyList(), eq(false));
    doReturn(new DescribeLoadBalancersResult().withLoadBalancers(
                 new LoadBalancer().withLoadBalancerName("lb1").withType("application"),
                 new LoadBalancer().withLoadBalancerName("lb2").withType("application"),
                 new LoadBalancer().withLoadBalancerName("lb3").withType("network")))
        .when(mockV2Client)
        .describeLoadBalancers(any());
    doNothing().when(mockTracker).trackELBCall(anyString());
    doNothing().when(mockTracker).trackClassicELBCall(anyString());
    List<AwsLoadBalancerDetails> appLbNames = awsElbHelperServiceDelegate.listElasticLoadBalancerDetails(
        AwsConfig.builder().build(), emptyList(), "us-east-1");
    assertThat(appLbNames).isNotNull();
    assertThat(appLbNames.size()).isEqualTo(3);
    assertThat(appLbNames.get(0).getName()).isEqualTo("lb1");
    assertThat(appLbNames.get(0).getType()).isEqualTo("application");
    assertThat(appLbNames.get(1).getName()).isEqualTo("lb2");
    assertThat(appLbNames.get(1).getType()).isEqualTo("application");
    assertThat(appLbNames.get(2).getName()).isEqualTo("lb3");
    assertThat(appLbNames.get(2).getType()).isEqualTo("network");
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testListNetworkLoadBalancers() {
    AmazonElasticLoadBalancingClient mockv2Client = mock(AmazonElasticLoadBalancingClient.class);
    doReturn(mockv2Client).when(awsElbHelperServiceDelegate).getAmazonElasticLoadBalancingClientV2(any(), any());
    doReturn(new DescribeLoadBalancersResult().withLoadBalancers(
                 new LoadBalancer().withLoadBalancerName("lb1").withType("application"),
                 new LoadBalancer().withLoadBalancerName("lb2").withType("application"),
                 new LoadBalancer().withLoadBalancerName("lb3").withType("network")))
        .when(mockv2Client)
        .describeLoadBalancers(any());
    doReturn(null).when(mockEncryptionService).decrypt(any(), anyList(), eq(false));
    doNothing().when(mockTracker).trackELBCall(anyString());
    doNothing().when(mockTracker).trackClassicELBCall(anyString());
    List<AwsLoadBalancerDetails> appLbNames = awsElbHelperServiceDelegate.listNetworkLoadBalancerDetails(
        AwsConfig.builder().build(), emptyList(), "us-east-1");
    assertThat(appLbNames).isNotNull();
    assertThat(appLbNames.size()).isEqualTo(1);
    assertThat(appLbNames.get(0).getName()).isEqualTo("lb3");
    assertThat(appLbNames.get(0).getType()).isEqualTo("network");
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testListTargetGroupsForAlb() {
    AmazonElasticLoadBalancingClient mockV2Client = mock(AmazonElasticLoadBalancingClient.class);
    doReturn(mockV2Client).when(awsElbHelperServiceDelegate).getAmazonElasticLoadBalancingClientV2(any(), any());
    doReturn(null).when(mockEncryptionService).decrypt(any(), anyList(), eq(false));
    doReturn(new DescribeTargetGroupsResult().withTargetGroups(
                 new TargetGroup().withTargetGroupArn("a1").withTargetGroupName("n1"),
                 new TargetGroup().withTargetGroupArn("a2").withTargetGroupName("n2")))
        .when(mockV2Client)
        .describeTargetGroups(any());
    doReturn(new DescribeLoadBalancersResult().withLoadBalancers(new LoadBalancer().withLoadBalancerArn("arn1")))
        .when(mockV2Client)
        .describeLoadBalancers(any());
    doNothing().when(mockTracker).trackELBCall(anyString());
    doNothing().when(mockTracker).trackClassicELBCall(anyString());
    Map<String, String> result = awsElbHelperServiceDelegate.listTargetGroupsForAlb(
        AwsConfig.builder().build(), emptyList(), "us-east-1", "lbName");
    assertThat(result).isNotNull();
    assertThat(result.size()).isEqualTo(2);
    assertThat(result.get("a1")).isEqualTo("n1");
    assertThat(result.get("a2")).isEqualTo("n2");
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testAllInstancesRegistered() {
    com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancingClient mockClassicClient =
        mock(com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancingClient.class);
    ExecutionLogCallback mockCallback = mock(ExecutionLogCallback.class);
    doReturn(new DescribeInstanceHealthResult().withInstanceStates(
                 new InstanceState().withState("Unknown").withInstanceId("id")))
        .doReturn(new DescribeInstanceHealthResult().withInstanceStates(
            new InstanceState().withState("InService").withInstanceId("id")))
        .when(mockClassicClient)
        .describeInstanceHealth(any());
    doNothing().when(mockCallback).saveExecutionLog(anyString());
    doNothing().when(mockTracker).trackELBCall(anyString());
    doNothing().when(mockTracker).trackClassicELBCall(anyString());
    boolean result = awsElbHelperServiceDelegate.allInstancesRegistered(
        mockClassicClient, singletonList("id"), "classicLb", mockCallback);
    assertThat(result).isFalse();
    result = awsElbHelperServiceDelegate.allInstancesRegistered(
        mockClassicClient, singletonList("id"), "classicLb", mockCallback);
    assertThat(result).isTrue();
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testAllTargetsRegistered() {
    AmazonElasticLoadBalancingClient mockV2Client = mock(AmazonElasticLoadBalancingClient.class);
    ExecutionLogCallback mockCallback = mock(ExecutionLogCallback.class);
    doNothing().when(mockCallback).saveExecutionLog(anyString());
    doReturn(new DescribeTargetHealthResult().withTargetHealthDescriptions(
                 new TargetHealthDescription()
                     .withTargetHealth(new TargetHealth().withState("Initial"))
                     .withTarget(new TargetDescription().withId("id"))))
        .doReturn(new DescribeTargetHealthResult().withTargetHealthDescriptions(
            new TargetHealthDescription()
                .withTargetHealth(new TargetHealth().withState("Healthy"))
                .withTarget(new TargetDescription().withId("id"))))
        .when(mockV2Client)
        .describeTargetHealth(any());
    doNothing().when(mockTracker).trackELBCall(anyString());
    doNothing().when(mockTracker).trackClassicELBCall(anyString());
    boolean result = awsElbHelperServiceDelegate.allTargetsRegistered(
        mockV2Client, singletonList("id"), "targetGroup", mockCallback);
    assertThat(result).isFalse();
    result = awsElbHelperServiceDelegate.allTargetsRegistered(
        mockV2Client, singletonList("id"), "targetGroup", mockCallback);
    assertThat(result).isTrue();
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testAllTargetsDeRegistered() {
    AmazonElasticLoadBalancingClient mockV2Client = mock(AmazonElasticLoadBalancingClient.class);
    ExecutionLogCallback mockCallback = mock(ExecutionLogCallback.class);
    doNothing().when(mockCallback).saveExecutionLog(anyString());
    doReturn(new DescribeTargetHealthResult().withTargetHealthDescriptions(
                 new TargetHealthDescription()
                     .withTargetHealth(new TargetHealth().withState("Healthy"))
                     .withTarget(new TargetDescription().withId("id"))))
        .doReturn(new DescribeTargetHealthResult())
        .when(mockV2Client)
        .describeTargetHealth(any());
    doNothing().when(mockTracker).trackELBCall(anyString());
    doNothing().when(mockTracker).trackClassicELBCall(anyString());
    boolean result = awsElbHelperServiceDelegate.allTargetsDeRegistered(
        mockV2Client, singletonList("id"), "targetGroup", mockCallback);
    assertThat(result).isFalse();
    result = awsElbHelperServiceDelegate.allTargetsDeRegistered(
        mockV2Client, singletonList("id"), "targetGroup", mockCallback);
    assertThat(result).isTrue();
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testAllInstancesDeRegistered() {
    com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancingClient mockClassicClient =
        mock(com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancingClient.class);
    ExecutionLogCallback mockCallback = mock(ExecutionLogCallback.class);
    doReturn(new DescribeInstanceHealthResult().withInstanceStates(
                 new InstanceState().withState("Healthy").withInstanceId("id")))
        .doReturn(new DescribeInstanceHealthResult())
        .when(mockClassicClient)
        .describeInstanceHealth(any());
    doNothing().when(mockCallback).saveExecutionLog(anyString());
    doNothing().when(mockTracker).trackELBCall(anyString());
    doNothing().when(mockTracker).trackClassicELBCall(anyString());
    boolean result = awsElbHelperServiceDelegate.allInstancesDeRegistered(
        mockClassicClient, singletonList("id"), "classicLb", mockCallback);
    assertThat(result).isFalse();
    result = awsElbHelperServiceDelegate.allInstancesDeRegistered(
        mockClassicClient, singletonList("id"), "classicLb", mockCallback);
    assertThat(result).isTrue();
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testCloneTargetGroup() {
    AmazonElasticLoadBalancingClient mockV2Client = mock(AmazonElasticLoadBalancingClient.class);
    doReturn(mockV2Client).when(awsElbHelperServiceDelegate).getAmazonElasticLoadBalancingClientV2(any(), any());
    doReturn(null).when(mockEncryptionService).decrypt(any(), anyList(), eq(false));
    doReturn(new DescribeTargetGroupsResult().withTargetGroups(new TargetGroup().withTargetType("ip").withPort(80)))
        .when(mockV2Client)
        .describeTargetGroups(any());
    ExecutionLogCallback mockCallback = mock(ExecutionLogCallback.class);
    doNothing().when(mockCallback).saveExecutionLog(anyString());
    doReturn(new CreateTargetGroupResult().withTargetGroups(new TargetGroup().withTargetGroupArn("arn1")))
        .when(mockV2Client)
        .createTargetGroup(any());
    doNothing().when(mockTracker).trackELBCall(anyString());
    doNothing().when(mockTracker).trackClassicELBCall(anyString());
    awsElbHelperServiceDelegate.cloneTargetGroup(
        AwsConfig.builder().build(), emptyList(), "us-east-1", "arn", "stageTargetGroup");
    verify(mockV2Client).createTargetGroup(any());
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testGetElbListenersForLoadBalaner() {
    AmazonElasticLoadBalancingClient mockV2Client = mock(AmazonElasticLoadBalancingClient.class);
    doReturn(mockV2Client).when(awsElbHelperServiceDelegate).getAmazonElasticLoadBalancingClientV2(any(), any());
    doReturn(null).when(mockEncryptionService).decrypt(any(), anyList(), eq(false));
    doReturn(new DescribeLoadBalancersResult().withLoadBalancers(new LoadBalancer().withLoadBalancerArn("lbArn")))
        .when(mockV2Client)
        .describeLoadBalancers(any());
    doReturn(new DescribeListenersResult().withListeners(
                 new Listener().withListenerArn("listArn").withPort(8080).withProtocol("HTTP")))
        .when(mockV2Client)
        .describeListeners(any());
    doReturn(new DescribeRulesResult().withRules(new Rule()
                                                     .withRuleArn("ruleArn")
                                                     .withPriority("rulePriority")
                                                     .withIsDefault(true)
                                                     .withActions(new Action().withTargetGroupArn("targetArn"))))
        .when(mockV2Client)
        .describeRules(any());
    List<AwsElbListener> listeners = awsElbHelperServiceDelegate.getElbListenersForLoadBalaner(
        AwsConfig.builder().build(), emptyList(), "us-east-1", "lbName");
    assertThat(listeners).isNotNull();
    assertThat(listeners.size()).isEqualTo(1);
    AwsElbListener listener = listeners.get(0);
    assertThat(listener.getListenerArn()).isEqualTo("listArn");
    assertThat(listener.getLoadBalancerArn()).isEqualTo("lbArn");
    assertThat(listener.getPort()).isEqualTo(8080);
    assertThat(listener.getProtocol()).isEqualTo("HTTP");
    List<AwsElbListenerRuleData> rules = listener.getRules();
    assertThat(rules).isNotNull();
    assertThat(rules.size()).isEqualTo(1);
    AwsElbListenerRuleData rule = rules.get(0);
    assertThat(rule.getRuleArn()).isEqualTo("ruleArn");
    assertThat(rule.getRulePriority()).isEqualTo("rulePriority");
    assertThat(rule.getRuleTargetGroupArn()).isEqualTo("targetArn");
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testModifySpecificRule() {
    AmazonElasticLoadBalancingClient mockV2Client = mock(AmazonElasticLoadBalancingClient.class);
    doReturn(mockV2Client).when(awsElbHelperServiceDelegate).getAmazonElasticLoadBalancingClientV2(any(), any());
    doReturn(null).when(mockEncryptionService).decrypt(any(), anyList(), eq(false));
    ExecutionLogCallback mockCallback = mock(ExecutionLogCallback.class);
    doNothing().when(mockCallback).saveExecutionLog(anyString());
    doNothing().when(mockTracker).trackELBCall(anyString());
    String ruleArn = "RULE_ARN";
    String tgtArn = "TGT_ARN";
    awsElbHelperServiceDelegate.modifySpecificRule(
        AwsConfig.builder().build(), emptyList(), "us-east-1", ruleArn, tgtArn, mockCallback);
    ArgumentCaptor<ModifyRuleRequest> captor = ArgumentCaptor.forClass(ModifyRuleRequest.class);
    verify(mockV2Client).modifyRule(captor.capture());
    ModifyRuleRequest request = captor.getValue();
    assertThat(request).isNotNull();
    assertThat(request.getRuleArn()).isEqualTo(ruleArn);
    List<Action> actions = request.getActions();
    assertThat(actions).isNotNull();
    assertThat(actions.size()).isEqualTo(1);
    assertThat(actions.get(0).getTargetGroupArn()).isEqualTo(tgtArn);
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testFetchTargetGroupForSpecificRules() {
    AmazonElasticLoadBalancingClient mockV2Client = mock(AmazonElasticLoadBalancingClient.class);
    doReturn(mockV2Client).when(awsElbHelperServiceDelegate).getAmazonElasticLoadBalancingClientV2(any(), any());
    doReturn(null).when(mockEncryptionService).decrypt(any(), anyList(), eq(false));
    ExecutionLogCallback mockCallback = mock(ExecutionLogCallback.class);
    doNothing().when(mockCallback).saveExecutionLog(anyString());
    doNothing().when(mockTracker).trackELBCall(anyString());
    AwsElbListener listener = AwsElbListener.builder()
                                  .listenerArn("LIST_ARN")
                                  .loadBalancerArn("LB_ARN")
                                  .port(8080)
                                  .protocol("HTTP")
                                  .rules(singletonList(AwsElbListenerRuleData.builder()
                                                           .ruleArn("RULE_ARN")
                                                           .ruleTargetGroupArn("TGT_ARN")
                                                           .rulePriority("1")
                                                           .build()))
                                  .build();
    doReturn(new DescribeTargetGroupsResult().withTargetGroups(
                 new TargetGroup().withTargetGroupName("TGT_NAME").withTargetGroupArn("TGT_ARN")))
        .when(mockV2Client)
        .describeTargetGroups(any());
    TargetGroup targetGroup = awsElbHelperServiceDelegate.fetchTargetGroupForSpecificRules(
        listener, "RULE_ARN", mockCallback, AwsConfig.builder().build(), "us-east-1", emptyList());
    assertThat(targetGroup).isNotNull();
    assertThat(targetGroup.getTargetGroupArn()).isEqualTo("TGT_ARN");
    assertThat(targetGroup.getTargetGroupName()).isEqualTo("TGT_NAME");
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testFetchRequiredTargetGroup() {
    AwsElbHelperServiceDelegateImpl service = spy(AwsElbHelperServiceDelegateImpl.class);
    doReturn(empty()).when(service).getTargetGroup(any(), anyList(), anyString(), anyString());
    ExecutionLogCallback mockCallback = mock(ExecutionLogCallback.class);
    doNothing().when(mockCallback).saveExecutionLog(anyString(), any());
    assertThatThrownBy(()
                           -> service.fetchRequiredTargetGroup(
                               AwsConfig.builder().build(), emptyList(), "us-east-1", "arn", mockCallback))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testValidateActionAndGetTuples() {
    AwsElbHelperServiceDelegateImpl service = spy(AwsElbHelperServiceDelegateImpl.class);
    ExecutionLogCallback mockCallback = mock(ExecutionLogCallback.class);
    doNothing().when(mockCallback).saveExecutionLog(anyString(), any());
    Action forwardingAction1 = new Action().withType(ActionTypeEnum.FixedResponse.name());
    assertThatThrownBy(() -> service.validateActionAndGetTuples(forwardingAction1, mockCallback))
        .isInstanceOf(InvalidRequestException.class);
    Action forwardingAction2 = new Action().withType(ActionTypeEnum.Forward.name());
    assertThatThrownBy(() -> service.validateActionAndGetTuples(forwardingAction2, mockCallback))
        .isInstanceOf(InvalidRequestException.class);
    Action forwardingAction3 = new Action()
                                   .withType(ActionTypeEnum.Forward.name())
                                   .withForwardConfig(new ForwardActionConfig().withTargetGroups(emptyList()));
    assertThatThrownBy(() -> service.validateActionAndGetTuples(forwardingAction3, mockCallback))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testGetFinalAction() {
    AwsElbHelperServiceDelegateImpl service = spy(AwsElbHelperServiceDelegateImpl.class);
    ExecutionLogCallback mockCallback = mock(ExecutionLogCallback.class);
    doNothing().when(mockCallback).saveExecutionLog(anyString(), any());
    AmazonElasticLoadBalancing mockClient = mock(AmazonElasticLoadBalancing.class);
    doReturn(new DescribeRulesResult())
        .doReturn(new DescribeRulesResult().withRules(new Rule()))
        .when(mockClient)
        .describeRules(any());
    doReturn(new Listener().withDefaultActions(new Action().withType(ActionTypeEnum.Forward.name())))
        .when(service)
        .getElbListener(any(), anyList(), anyString(), anyString());
    LbDetailsForAlbTrafficShift details0 =
        LbDetailsForAlbTrafficShift.builder().useSpecificRule(true).ruleArn("ruleArn").build();
    assertThatThrownBy(()
                           -> service.getFinalAction(mockClient, details0, mockCallback, AwsConfig.builder().build(),
                               emptyList(), "us-east-1"))
        .isInstanceOf(InvalidRequestException.class);
    assertThatThrownBy(()
                           -> service.getFinalAction(mockClient, details0, mockCallback, AwsConfig.builder().build(),
                               emptyList(), "us-east-1"))
        .isInstanceOf(InvalidRequestException.class);
    LbDetailsForAlbTrafficShift details1 =
        LbDetailsForAlbTrafficShift.builder().useSpecificRule(false).listenerArn("listArn").build();
    assertThat(service.getFinalAction(
                   mockClient, details1, mockCallback, AwsConfig.builder().build(), emptyList(), "us-east-1"))
        .isNotNull();
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testLoadTrafficShiftTargetGroupData() {
    AwsElbHelperServiceDelegateImpl service = spy(AwsElbHelperServiceDelegateImpl.class);
    EncryptionService mockEncryptionService = mock(EncryptionService.class);
    on(service).set("encryptionService", mockEncryptionService);
    doReturn(null).when(mockEncryptionService).decrypt(any(), anyList(), eq(false));
    AmazonElasticLoadBalancingClient mockClient = mock(AmazonElasticLoadBalancingClient.class);
    doReturn(mockClient).when(service).getAmazonElasticLoadBalancingClientV2(any(), any());
    ExecutionLogCallback mockCallback = mock(ExecutionLogCallback.class);
    doNothing().when(mockCallback).saveExecutionLog(anyString(), any());
    doNothing().when(mockCallback).saveExecutionLog(anyString());
    doReturn(new Action().withForwardConfig(new ForwardActionConfig()))
        .when(service)
        .getFinalAction(any(), any(), any(), any(), anyList(), anyString());
    doReturn(asList(new TargetGroupTuple().withTargetGroupArn("arnProd").withWeight(MAX_TRAFFIC_SHIFT_WEIGHT),
                 new TargetGroupTuple().withTargetGroupArn("arnStage").withWeight(MIN_TRAFFIC_SHIFT_WEIGHT)))
        .when(service)
        .validateActionAndGetTuples(any(), any());
    doReturn(new TargetGroup())
        .doReturn(new TargetGroup())
        .when(service)
        .fetchRequiredTargetGroup(any(), anyList(), anyString(), anyString(), any());
    LbDetailsForAlbTrafficShift originalDetails = LbDetailsForAlbTrafficShift.builder()
                                                      .loadBalancerName("lbName")
                                                      .loadBalancerArn("lbArn")
                                                      .listenerPort("port")
                                                      .listenerArn("listArn")
                                                      .useSpecificRule(true)
                                                      .ruleArn("ruleArn")
                                                      .build();
    LbDetailsForAlbTrafficShift finalDetails = service.loadTrafficShiftTargetGroupData(
        AwsConfig.builder().build(), "us-east-1", emptyList(), originalDetails, mockCallback);
    assertThat(finalDetails).isNotNull();
    assertThat(finalDetails.getProdTargetGroupArn()).isEqualTo("arnProd");
    assertThat(finalDetails.getStageTargetGroupArn()).isEqualTo("arnStage");
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testUpdateRulesForAlbTrafficShift() {
    AwsElbHelperServiceDelegateImpl service = spy(AwsElbHelperServiceDelegateImpl.class);
    EncryptionService mockEncryptionService = mock(EncryptionService.class);
    AwsCallTracker mockAwsCallTracker = mock(AwsCallTracker.class);
    on(service).set("encryptionService", mockEncryptionService);
    on(service).set("tracker", mockAwsCallTracker);
    doReturn(null).when(mockEncryptionService).decrypt(any(), anyList(), eq(false));
    AmazonElasticLoadBalancingClient mockClient = mock(AmazonElasticLoadBalancingClient.class);
    doReturn(mockClient).when(service).getAmazonElasticLoadBalancingClientV2(any(), any());
    DescribeRulesResult describeRulesResult = new DescribeRulesResult();
    doReturn(describeRulesResult).when(mockClient).describeRules(any());
    ExecutionLogCallback mockCallback = mock(ExecutionLogCallback.class);
    doNothing().when(mockCallback).saveExecutionLog(anyString(), any());
    doNothing().when(mockCallback).saveExecutionLog(anyString());
    List<LbDetailsForAlbTrafficShift> details = asList(LbDetailsForAlbTrafficShift.builder()
                                                           .useSpecificRule(true)
                                                           .ruleArn("ruleArn")
                                                           .prodTargetGroupArn("prodTg1")
                                                           .stageTargetGroupArn("stageTg1")
                                                           .build(),
        LbDetailsForAlbTrafficShift.builder()
            .useSpecificRule(false)
            .prodTargetGroupArn("progTg2")
            .stageTargetGroupArn("stageTg2")
            .build());
    service.updateRulesForAlbTrafficShift(AwsConfig.builder().build(), "us-east-1", emptyList(), details, mockCallback,
        10, SpotInstConstants.ELASTI_GROUP);
    verify(mockClient).modifyRule(any());
    verify(mockClient).modifyListener(any());
  }

  @Test
  @Owner(developers = RAGHVENDRA)
  @Category(UnitTests.class)
  public void testUpdateRulesForAlbTrafficShiftMixedRulesCase() {
    AwsElbHelperServiceDelegateImpl service = spy(AwsElbHelperServiceDelegateImpl.class);
    EncryptionService mockEncryptionService = mock(EncryptionService.class);
    AwsCallTracker mockAwsCallTracker = mock(AwsCallTracker.class);
    on(service).set("encryptionService", mockEncryptionService);
    on(service).set("tracker", mockAwsCallTracker);
    doReturn(null).when(mockEncryptionService).decrypt(any(), anyList(), eq(false));
    AmazonElasticLoadBalancingClient mockClient = mock(AmazonElasticLoadBalancingClient.class);
    doReturn(mockClient).when(service).getAmazonElasticLoadBalancingClientV2(any(), any());
    DescribeRulesResult describeRulesResult = new DescribeRulesResult();
    Rule defaultRule = new Rule();
    TargetGroupTuple newTuple = new TargetGroupTuple().withWeight(10);
    TargetGroupTuple oldTuple = new TargetGroupTuple().withWeight(90);
    Action forwardAction = new Action().withType(Forward).withForwardConfig(
        new ForwardActionConfig().withTargetGroups(newTuple, oldTuple));
    defaultRule.withActions(forwardAction);
    defaultRule.setIsDefault(true);
    defaultRule.setRuleArn("ruleArnDefault");
    describeRulesResult.setRules(singletonList(defaultRule));
    doReturn(describeRulesResult).when(mockClient).describeRules(any());
    ExecutionLogCallback mockCallback = mock(ExecutionLogCallback.class);
    doNothing().when(mockCallback).saveExecutionLog(anyString(), any());
    doNothing().when(mockCallback).saveExecutionLog(anyString());
    List<LbDetailsForAlbTrafficShift> details = asList(LbDetailsForAlbTrafficShift.builder()
                                                           .useSpecificRule(true)
                                                           .ruleArn("ruleArnDefault")
                                                           .prodTargetGroupArn("prodTg1")
                                                           .stageTargetGroupArn("stageTg1")
                                                           .listenerArn("listenerArn")
                                                           .build());
    oldTuple.setTargetGroupArn(details.get(0).getProdTargetGroupArn());
    newTuple.setTargetGroupArn(details.get(0).getStageTargetGroupArn());
    service.updateRulesForAlbTrafficShift(AwsConfig.builder().build(), "us-east-1", emptyList(), details, mockCallback,
        10, SpotInstConstants.ELASTI_GROUP);
    ModifyListenerRequest modifyListenerRequest = new ModifyListenerRequest();
    modifyListenerRequest.withDefaultActions(forwardAction).withListenerArn("listenerArn");
    verify(mockClient).modifyListener(modifyListenerRequest);
  }
}
