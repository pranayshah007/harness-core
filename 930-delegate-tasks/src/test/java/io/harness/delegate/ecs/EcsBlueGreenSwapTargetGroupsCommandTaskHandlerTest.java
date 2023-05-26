/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.ecs;

import static io.harness.rule.OwnerRule.ALLU_VAMSI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.aws.AwsNgConfigMapper;
import io.harness.delegate.task.ecs.EcsCommandTaskNGHelper;
import io.harness.delegate.task.ecs.EcsCommandTypeNG;
import io.harness.delegate.task.ecs.EcsInfraConfig;
import io.harness.delegate.task.ecs.EcsInfraType;
import io.harness.delegate.task.ecs.EcsLoadBalancerConfig;
import io.harness.delegate.task.ecs.EcsTaskHelperBase;
import io.harness.delegate.task.ecs.request.EcsBlueGreenSwapTargetGroupsRequest;
import io.harness.delegate.task.ecs.response.EcsBlueGreenSwapTargetGroupsResponse;
import io.harness.ecs.EcsCommandUnitConstants;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.rule.Owner;

import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import software.amazon.awssdk.services.ecs.model.Service;
import software.amazon.awssdk.services.ecs.model.UpdateServiceResponse;

public class EcsBlueGreenSwapTargetGroupsCommandTaskHandlerTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  private final String prodListenerArn = "prodListenerArn";
  private final String prodListenerRuleArn = "prodListenerRuleArn";
  private final String stageListenerArn = "stageListenerArn";
  private final String stageListenerRuleArn = "stageListenerArn";
  private final String loadBalancer = "loadBalancer";
  private final String cluster = "cluster";
  private final String region = "us-east-1";
  private final String serviceName = "serviceName";
  private final Integer sleepTime = 30;

  @Mock private ILogStreamingTaskClient iLogStreamingTaskClient;
  @Mock private EcsTaskHelperBase ecsTaskHelperBase;
  @Mock private EcsCommandTaskNGHelper ecsCommandTaskHelper;
  @Mock private LogCallback swapTargetGroupLogCallback;
  @Mock private AwsNgConfigMapper awsNgConfigMapper;

  @Spy
  @InjectMocks
  private EcsBlueGreenSwapTargetGroupsCommandTaskHandler ecsBlueGreenSwapTargetGroupsCommandTaskHandler;

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void executeTaskInternalTest() throws Exception {
    CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();
    doReturn(swapTargetGroupLogCallback)
        .when(ecsTaskHelperBase)
        .getLogCallback(
            iLogStreamingTaskClient, EcsCommandUnitConstants.swapTargetGroup.toString(), true, commandUnitsProgress);
    EcsInfraConfig ecsInfraConfig = EcsInfraConfig.builder()
                                        .region(region)
                                        .ecsInfraType(EcsInfraType.ECS)
                                        .cluster(cluster)
                                        .awsConnectorDTO(AwsConnectorDTO.builder().build())
                                        .build();
    EcsLoadBalancerConfig ecsLoadBalancerConfig = EcsLoadBalancerConfig.builder()
                                                      .loadBalancer(loadBalancer)
                                                      .prodListenerArn(prodListenerArn)
                                                      .prodListenerRuleArn(prodListenerRuleArn)
                                                      .stageListenerArn(stageListenerArn)
                                                      .stageListenerRuleArn(stageListenerRuleArn)
                                                      .build();
    EcsBlueGreenSwapTargetGroupsRequest ecsBlueGreenSwapTargetGroupsRequest =
        EcsBlueGreenSwapTargetGroupsRequest.builder()
            .ecsCommandType(EcsCommandTypeNG.ECS_BLUE_GREEN_SWAP_TARGET_GROUPS)
            .commandUnitsProgress(commandUnitsProgress)
            .ecsInfraConfig(ecsInfraConfig)
            .ecsLoadBalancerConfig(ecsLoadBalancerConfig)
            .doNotDownsizeOldService(false)
            .oldServiceName("oldServiceName")
            .timeoutIntervalInMin(10)
            .isFirstDeployment(false)
            .downsizeOldServiceDelayInSecs(sleepTime)
            .build();

    AwsInternalConfig awsInternalConfig = AwsInternalConfig.builder().build();
    doReturn(awsInternalConfig).when(awsNgConfigMapper).createAwsInternalConfig(ecsInfraConfig.getAwsConnectorDTO());

    Service service = Service.builder().desiredCount(1).build();
    UpdateServiceResponse updateServiceResponse = UpdateServiceResponse.builder().service(service).build();

    doReturn(updateServiceResponse)
        .when(ecsCommandTaskHelper)
        .updateDesiredCount(
            ecsBlueGreenSwapTargetGroupsRequest.getOldServiceName(), ecsInfraConfig, awsInternalConfig, 0);
    EcsBlueGreenSwapTargetGroupsResponse ecsBlueGreenSwapTargetGroupsResponse =
        (EcsBlueGreenSwapTargetGroupsResponse) ecsBlueGreenSwapTargetGroupsCommandTaskHandler.executeTaskInternal(
            ecsBlueGreenSwapTargetGroupsRequest, iLogStreamingTaskClient, commandUnitsProgress);

    verify(ecsCommandTaskHelper).sleepInSeconds(ecsBlueGreenSwapTargetGroupsRequest.getDownsizeOldServiceDelayInSecs());
    assertThat(ecsBlueGreenSwapTargetGroupsResponse.getCommandExecutionStatus())
        .isEqualTo(CommandExecutionStatus.SUCCESS);
    assertThat(ecsBlueGreenSwapTargetGroupsResponse.getEcsBlueGreenSwapTargetGroupsResult().isTrafficShifted())
        .isEqualTo(true);
    assertThat(ecsBlueGreenSwapTargetGroupsResponse.getEcsBlueGreenSwapTargetGroupsResult().getRegion())
        .isEqualTo(region);
    assertThat(ecsBlueGreenSwapTargetGroupsResponse.getEcsBlueGreenSwapTargetGroupsResult().getLoadBalancer())
        .isEqualTo(ecsLoadBalancerConfig.getLoadBalancer());
    assertThat(ecsBlueGreenSwapTargetGroupsResponse.getEcsBlueGreenSwapTargetGroupsResult().getProdListenerArn())
        .isEqualTo(ecsLoadBalancerConfig.getProdListenerArn());
    assertThat(ecsBlueGreenSwapTargetGroupsResponse.getEcsBlueGreenSwapTargetGroupsResult().getProdListenerRuleArn())
        .isEqualTo(ecsLoadBalancerConfig.getProdListenerRuleArn());
    assertThat(ecsBlueGreenSwapTargetGroupsResponse.getEcsBlueGreenSwapTargetGroupsResult().getStageListenerArn())
        .isEqualTo(ecsLoadBalancerConfig.getStageListenerArn());
    assertThat(ecsBlueGreenSwapTargetGroupsResponse.getEcsBlueGreenSwapTargetGroupsResult().getStageListenerRuleArn())
        .isEqualTo(ecsLoadBalancerConfig.getStageListenerRuleArn());
  }
}
