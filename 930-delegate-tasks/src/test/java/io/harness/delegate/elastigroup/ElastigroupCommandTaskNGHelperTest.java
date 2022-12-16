/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.elastigroup;

import static io.harness.rule.OwnerRule.PIYUSH_BHUWALKA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.aws.v2.ecs.ElbV2Client;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.aws.AwsNgConfigMapper;
import io.harness.delegate.task.aws.LoadBalancerDetailsForBGDeployment;
import io.harness.delegate.task.elastigroup.ElastigroupBGTaskHelper;
import io.harness.delegate.task.elastigroup.ElastigroupCommandTaskNGHelper;
import io.harness.delegate.task.elastigroup.request.AwsConnectedCloudProvider;
import io.harness.delegate.task.elastigroup.request.AwsLoadBalancerConfig;
import io.harness.delegate.task.elastigroup.request.ElastigroupSetupCommandRequest;
import io.harness.logging.LogCallback;
import io.harness.rule.Owner;
import io.harness.security.encryption.SecretDecryptionService;
import io.harness.spotinst.SpotInstHelperServiceDelegate;

import java.util.Arrays;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.Action;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.ActionTypeEnum;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DescribeListenersRequest;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DescribeListenersResponse;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DescribeLoadBalancersRequest;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DescribeLoadBalancersResponse;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DescribeRulesRequest;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DescribeRulesResponse;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DescribeTargetGroupsRequest;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DescribeTargetGroupsResponse;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.Listener;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.LoadBalancer;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.ModifyListenerRequest;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.TargetGroup;

public class ElastigroupCommandTaskNGHelperTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock private SecretDecryptionService secretDecryptionService;
  @Mock private ElbV2Client elbV2Client;
  @Mock private AwsNgConfigMapper awsNgConfigMapper;
  @Mock private ILogStreamingTaskClient iLogStreamingTaskClient;
  @Mock private LogCallback createServiceLogCallback;
  @Mock protected SpotInstHelperServiceDelegate spotInstHelperServiceDelegate;
  @Mock private ElastigroupBGTaskHelper elastigroupDeployTaskHelper;

  private final String prodListenerArn = "prodListenerArn";
  private final String prodListenerRuleArn = "prodListenerRuleArn";
  private final String stageListenerArn = "stageListenerArn";
  private final String stageListenerRuleArn = "stageListenerArn";
  private final String loadBalancer = "loadBalancer";
  private final String prodTargetGroupArn = "prodGroupArn";
  private final String stageTargetGroupArn = "stageGroupArn";
  private final String prodTargetGroupName = "prodGroupArn";
  private final String stageTargetGroupName = "stageGroupArn";
  private final String targetGroupArn = "stageGroupArn";
  private final String prodListenerPort = "1";
  private final String stageListenerPort = "2";
  private final String loadBalancerArn = "loadBalancerArn";

  @InjectMocks @Spy private ElastigroupCommandTaskNGHelper elastigroupCommandTaskNGHelper;

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void getAwsInternalConfigTest() throws Exception {
    AwsConnectorDTO awsConnectorDTO = AwsConnectorDTO.builder().build();
    String region = "region";
    AwsInternalConfig awsInternalConfig = AwsInternalConfig.builder().defaultRegion(region).build();
    doReturn(awsInternalConfig).when(awsNgConfigMapper).createAwsInternalConfig(awsConnectorDTO);
    assertThat(elastigroupCommandTaskNGHelper.getAwsInternalConfig(awsConnectorDTO, region))
        .isEqualTo(awsInternalConfig);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void fetchAllLoadBalancerDetailsTest() throws Exception {
    String region = "region";
    AwsInternalConfig awsInternalConfig = AwsInternalConfig.builder().defaultRegion(region).build();
    String loadBalancerName = "name";
    LoadBalancerDetailsForBGDeployment loadBalancerDetailsForBGDeployment = LoadBalancerDetailsForBGDeployment.builder()
                                                                                .loadBalancerName(loadBalancerName)
                                                                                .prodRuleArn(prodListenerRuleArn)
                                                                                .stageRuleArn(stageListenerRuleArn)
                                                                                .prodListenerPort(prodListenerPort)
                                                                                .stageListenerPort(stageListenerPort)
                                                                                .build();
    AwsLoadBalancerConfig awsLoadBalancerConfig =
        AwsLoadBalancerConfig.builder().loadBalancerDetails(Arrays.asList(loadBalancerDetailsForBGDeployment)).build();
    ElastigroupSetupCommandRequest setupTaskParameters =
        ElastigroupSetupCommandRequest.builder()
            .connectedCloudProvider(AwsConnectedCloudProvider.builder().region(region).build())
            .loadBalancerConfig(awsLoadBalancerConfig)
            .build();
    String nextToken = null;
    DescribeLoadBalancersRequest describeLoadBalancersRequest =
        DescribeLoadBalancersRequest.builder().names(loadBalancerName).build();
    LoadBalancer loadBalancer = LoadBalancer.builder().loadBalancerArn(loadBalancerArn).build();
    DescribeLoadBalancersResponse describeLoadBalancersResponse =
        DescribeLoadBalancersResponse.builder().loadBalancers(Arrays.asList(loadBalancer)).build();

    DescribeListenersRequest describeListenersRequest =
        DescribeListenersRequest.builder().loadBalancerArn(loadBalancerArn).marker(nextToken).pageSize(10).build();
    Listener prodListener =
        Listener.builder().listenerArn(prodListenerArn).port(Integer.parseInt(prodListenerPort)).build();
    Listener stageListener =
        Listener.builder().listenerArn(stageListenerArn).port(Integer.parseInt(stageListenerPort)).build();
    DescribeListenersResponse describeListenersResponse =
        DescribeListenersResponse.builder().listeners(Arrays.asList(prodListener, stageListener)).build();

    doReturn(describeLoadBalancersResponse)
        .when(elbV2Client)
        .describeLoadBalancer(awsInternalConfig, describeLoadBalancersRequest, region);
    doReturn(describeListenersResponse)
        .when(elbV2Client)
        .describeListener(awsInternalConfig, describeListenersRequest, region);

    DescribeRulesRequest prodDescribeRulesRequest =
        DescribeRulesRequest.builder().listenerArn(prodListenerArn).marker(nextToken).pageSize(10).build();
    DescribeRulesRequest stageDescribeRulesRequest =
        DescribeRulesRequest.builder().listenerArn(stageListenerArn).marker(nextToken).pageSize(10).build();
    software.amazon.awssdk.services.elasticloadbalancingv2.model.Rule prodRule =
        software.amazon.awssdk.services.elasticloadbalancingv2.model.Rule.builder()
            .ruleArn(prodListenerRuleArn)
            .actions(Action.builder().targetGroupArn(prodTargetGroupArn).build())
            .build();
    software.amazon.awssdk.services.elasticloadbalancingv2.model.Rule stageRule =
        software.amazon.awssdk.services.elasticloadbalancingv2.model.Rule.builder()
            .ruleArn(stageListenerRuleArn)
            .actions(Action.builder().targetGroupArn(stageTargetGroupArn).build())
            .build();
    DescribeRulesResponse prodDescribeRulesResponse = DescribeRulesResponse.builder().rules(prodRule).build();
    DescribeRulesResponse stageDescribeRulesResponse = DescribeRulesResponse.builder().rules(stageRule).build();
    doReturn(prodDescribeRulesResponse)
        .when(elbV2Client)
        .describeRules(awsInternalConfig, prodDescribeRulesRequest, region);
    doReturn(stageDescribeRulesResponse)
        .when(elbV2Client)
        .describeRules(awsInternalConfig, stageDescribeRulesRequest, region);

    DescribeTargetGroupsRequest prodDescribeTargetGroupsRequest = DescribeTargetGroupsRequest.builder()
                                                                      .targetGroupArns(prodTargetGroupArn)
                                                                      .marker(nextToken)
                                                                      .pageSize(10)
                                                                      .build();
    DescribeTargetGroupsRequest stageDescribeTargetGroupsRequest = DescribeTargetGroupsRequest.builder()
                                                                       .targetGroupArns(stageTargetGroupArn)
                                                                       .marker(nextToken)
                                                                       .pageSize(10)
                                                                       .build();
    DescribeTargetGroupsResponse prodDescribeTargetGroupsResponse =
        DescribeTargetGroupsResponse.builder()
            .targetGroups(TargetGroup.builder().targetGroupArn(prodTargetGroupArn).build())
            .build();
    DescribeTargetGroupsResponse stageDescribeTargetGroupsResponse =
        DescribeTargetGroupsResponse.builder()
            .targetGroups(TargetGroup.builder().targetGroupArn(stageTargetGroupArn).build())
            .build();
    doReturn(prodDescribeTargetGroupsResponse)
        .when(elbV2Client)
        .describeTargetGroups(awsInternalConfig, prodDescribeTargetGroupsRequest, region);
    doReturn(stageDescribeTargetGroupsResponse)
        .when(elbV2Client)
        .describeTargetGroups(awsInternalConfig, stageDescribeTargetGroupsRequest, region);

    LoadBalancerDetailsForBGDeployment loadBalancerDetailsForBGDeployment1 =
        LoadBalancerDetailsForBGDeployment.builder()
            .loadBalancerName(loadBalancerName)
            .prodListenerArn(prodListenerArn)
            .prodTargetGroupArn(prodTargetGroupArn)
            .stageListenerArn(stageListenerArn)
            .stageTargetGroupArn(stageTargetGroupArn)
            .prodListenerPort(prodListenerPort)
            .stageListenerPort(stageListenerPort)
            .useSpecificRules(false)
            .prodRuleArn(prodListenerRuleArn)
            .stageRuleArn(stageListenerRuleArn)
            .build();
    assertThat(elastigroupCommandTaskNGHelper.fetchAllLoadBalancerDetails(
                   setupTaskParameters, awsInternalConfig, createServiceLogCallback))
        .isEqualTo(Arrays.asList(loadBalancerDetailsForBGDeployment1));
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void swapTargetGroupsTest() {
    String region = "region";
    AwsInternalConfig awsInternalConfig = AwsInternalConfig.builder().defaultRegion(region).build();
    ModifyListenerRequest modifyListenerRequest =
        ModifyListenerRequest.builder()
            .listenerArn(prodListenerArn)
            .defaultActions(Action.builder().type(ActionTypeEnum.FORWARD).targetGroupArn(targetGroupArn).build())
            .build();
    String nextToken = null;
    DescribeRulesRequest describeRulesRequestProd =
        DescribeRulesRequest.builder().listenerArn(prodListenerArn).marker(nextToken).pageSize(10).build();
    DescribeRulesRequest describeRulesRequestStage =
        DescribeRulesRequest.builder().listenerArn(stageListenerArn).marker(nextToken).pageSize(10).build();
    software.amazon.awssdk.services.elasticloadbalancingv2.model.Rule rule =
        software.amazon.awssdk.services.elasticloadbalancingv2.model.Rule.builder()
            .ruleArn(prodListenerRuleArn)
            .isDefault(true)
            .build();
    DescribeRulesResponse describeRulesResponse = DescribeRulesResponse.builder().rules(rule).build();
    doReturn(describeRulesResponse)
        .when(elbV2Client)
        .describeRules(awsInternalConfig, describeRulesRequestProd, region);
    doReturn(describeRulesResponse)
        .when(elbV2Client)
        .describeRules(awsInternalConfig, describeRulesRequestStage, region);
    elbV2Client.modifyListener(awsInternalConfig, modifyListenerRequest, region);

    String loadBalancerName = "name";
    LoadBalancerDetailsForBGDeployment loadBalancerDetailsForBGDeployment = LoadBalancerDetailsForBGDeployment.builder()
                                                                                .loadBalancerName(loadBalancerName)
                                                                                .stageListenerArn(stageListenerArn)
                                                                                .prodListenerArn(prodListenerArn)
                                                                                .prodRuleArn(prodListenerRuleArn)
                                                                                .stageRuleArn(stageListenerRuleArn)
                                                                                .prodTargetGroupArn(targetGroupArn)
                                                                                .stageTargetGroupArn(targetGroupArn)
                                                                                .build();

    elastigroupCommandTaskNGHelper.swapTargetGroups(
        region, createServiceLogCallback, loadBalancerDetailsForBGDeployment, awsInternalConfig);

    verify(elastigroupCommandTaskNGHelper)
        .modifyListenerRule(
            region, prodListenerArn, prodListenerRuleArn, targetGroupArn, awsInternalConfig, createServiceLogCallback);
    verify(elastigroupCommandTaskNGHelper)
        .modifyListenerRule(region, stageListenerArn, stageListenerRuleArn, targetGroupArn, awsInternalConfig,
            createServiceLogCallback);
  }
}
