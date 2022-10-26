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

import io.harness.CategoryTest;
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.aws.AwsNgConfigMapper;
import io.harness.delegate.task.ecs.EcsCommandTaskNGHelper;
import io.harness.delegate.task.ecs.EcsInfraConfig;
import io.harness.delegate.task.ecs.EcsInfraType;
import io.harness.delegate.task.ecs.EcsLoadBalancerConfig;
import io.harness.delegate.task.ecs.EcsTaskHelperBase;
import io.harness.delegate.task.ecs.request.EcsBlueGreenRollbackRequest;
import io.harness.delegate.task.ecs.request.EcsRollingRollbackRequest;
import io.harness.delegate.task.ecs.response.EcsBlueGreenRollbackResponse;
import io.harness.ecs.EcsCommandUnitConstants;
import io.harness.exception.InvalidArgumentsException;
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

public class EcsBlueGreenRollbackCommandTaskHandlerTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  private final String prodListenerArn = "prodListenerArn";
  private final String prodListenerRuleArn = "prodListenerRuleArn";
  private final String stageListenerArn = "stageListenerArn";
  private final String stageListenerRuleArn = "stageListenerArn";
  private final String loadBalancer = "loadBalancer";
  private final String cluster = "cluster";
  private final String region = "us-east-1";
  private final String targetGroupArn = "targetGroupArn";

  private final CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();
  private final EcsInfraConfig ecsInfraConfig = EcsInfraConfig.builder()
                                                    .region(region)
                                                    .ecsInfraType(EcsInfraType.ECS)
                                                    .cluster(cluster)
                                                    .awsConnectorDTO(AwsConnectorDTO.builder().build())
                                                    .build();
  private final EcsLoadBalancerConfig ecsLoadBalancerConfig = EcsLoadBalancerConfig.builder()
                                                                  .loadBalancer(loadBalancer)
                                                                  .prodListenerArn(prodListenerArn)
                                                                  .prodListenerRuleArn(prodListenerRuleArn)
                                                                  .prodTargetGroupArn(targetGroupArn)
                                                                  .stageListenerArn(stageListenerArn)
                                                                  .stageListenerRuleArn(stageListenerRuleArn)
                                                                  .stageTargetGroupArn(targetGroupArn)
                                                                  .build();

  @Mock private ILogStreamingTaskClient iLogStreamingTaskClient;
  @Mock private EcsTaskHelperBase ecsTaskHelperBase;
  @Mock private EcsCommandTaskNGHelper ecsCommandTaskHelper;
  @Mock private LogCallback prepareRollbackLogCallback;
  @Mock private AwsNgConfigMapper awsNgConfigMapper;
  @Mock private LogCallback rollbackCallback;

  @Spy @InjectMocks private EcsBlueGreenRollbackCommandTaskHandler ecsBlueGreenRollbackCommandTaskHandler;

  @Test(expected = InvalidArgumentsException.class)
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void executeTaskInternalNotEcsBlueGreenRollbackRequestTest() throws Exception {
    EcsRollingRollbackRequest ecsRollingRollbackRequest = EcsRollingRollbackRequest.builder().build();
    ecsBlueGreenRollbackCommandTaskHandler.executeTaskInternal(
        ecsRollingRollbackRequest, iLogStreamingTaskClient, commandUnitsProgress);
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void executeTaskInternalTargetShiftStartedFalseIsFirstDeploymentFalseTest() throws Exception {
    EcsBlueGreenRollbackRequest ecsBlueGreenRollbackRequest = EcsBlueGreenRollbackRequest.builder()
                                                                  .commandUnitsProgress(commandUnitsProgress)
                                                                  .ecsInfraConfig(ecsInfraConfig)
                                                                  .timeoutIntervalInMin(10)
                                                                  .ecsLoadBalancerConfig(ecsLoadBalancerConfig)
                                                                  .isTargetShiftStarted(false)
                                                                  .isFirstDeployment(false)
                                                                  .build();

    doReturn(rollbackCallback)
        .when(ecsTaskHelperBase)
        .getLogCallback(
            iLogStreamingTaskClient, EcsCommandUnitConstants.rollback.toString(), true, commandUnitsProgress);
    AwsInternalConfig awsInternalConfig = AwsInternalConfig.builder().build();
    doReturn(awsInternalConfig).when(awsNgConfigMapper).createAwsInternalConfig(ecsInfraConfig.getAwsConnectorDTO());
    doReturn("targetGroup")
        .when(ecsCommandTaskHelper)
        .getTargetGroupArnFromLoadBalancer(ecsInfraConfig,
            ecsBlueGreenRollbackRequest.getEcsLoadBalancerConfig().getProdListenerArn(),
            ecsBlueGreenRollbackRequest.getEcsLoadBalancerConfig().getProdListenerRuleArn(),
            ecsBlueGreenRollbackRequest.getEcsLoadBalancerConfig().getLoadBalancer(), awsInternalConfig);
    doReturn("targetGroup")
        .when(ecsCommandTaskHelper)
        .getTargetGroupArnFromLoadBalancer(ecsInfraConfig,
            ecsBlueGreenRollbackRequest.getEcsLoadBalancerConfig().getStageListenerArn(),
            ecsBlueGreenRollbackRequest.getEcsLoadBalancerConfig().getStageListenerRuleArn(),
            ecsBlueGreenRollbackRequest.getEcsLoadBalancerConfig().getLoadBalancer(), awsInternalConfig);

    Service service = Service.builder().build();
    UpdateServiceResponse updateServiceResponse = UpdateServiceResponse.builder().service(service).build();
    doReturn(updateServiceResponse)
        .when(ecsCommandTaskHelper)
        .updateDesiredCount(ecsBlueGreenRollbackRequest.getNewServiceName(), ecsInfraConfig, awsInternalConfig, 0);
    EcsBlueGreenRollbackResponse ecsBlueGreenRollbackResponse =
        (EcsBlueGreenRollbackResponse) ecsBlueGreenRollbackCommandTaskHandler.executeTaskInternal(
            ecsBlueGreenRollbackRequest, iLogStreamingTaskClient, commandUnitsProgress);

    assertThat(ecsBlueGreenRollbackResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    assertThat(ecsBlueGreenRollbackResponse.getEcsBlueGreenRollbackResult().isFirstDeployment()).isEqualTo(false);
    assertThat(ecsBlueGreenRollbackResponse.getEcsBlueGreenRollbackResult().getLoadBalancer())
        .isEqualTo(ecsLoadBalancerConfig.getLoadBalancer());
    assertThat(ecsBlueGreenRollbackResponse.getEcsBlueGreenRollbackResult().getProdListenerArn())
        .isEqualTo(ecsLoadBalancerConfig.getProdListenerArn());
    assertThat(ecsBlueGreenRollbackResponse.getEcsBlueGreenRollbackResult().getProdListenerRuleArn())
        .isEqualTo(ecsLoadBalancerConfig.getProdListenerRuleArn());
    assertThat(ecsBlueGreenRollbackResponse.getEcsBlueGreenRollbackResult().getStageListenerArn())
        .isEqualTo(ecsLoadBalancerConfig.getStageListenerArn());
    assertThat(ecsBlueGreenRollbackResponse.getEcsBlueGreenRollbackResult().getStageListenerRuleArn())
        .isEqualTo(ecsLoadBalancerConfig.getStageListenerRuleArn());
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void executeTaskInternalTargetShiftStartedTrueIsFirstDeploymentTrueTest() throws Exception {
    EcsBlueGreenRollbackRequest ecsBlueGreenRollbackRequest = EcsBlueGreenRollbackRequest.builder()
                                                                  .commandUnitsProgress(commandUnitsProgress)
                                                                  .ecsInfraConfig(ecsInfraConfig)
                                                                  .timeoutIntervalInMin(10)
                                                                  .ecsLoadBalancerConfig(ecsLoadBalancerConfig)
                                                                  .isTargetShiftStarted(true)
                                                                  .isFirstDeployment(true)
                                                                  .build();

    doReturn(rollbackCallback)
        .when(ecsTaskHelperBase)
        .getLogCallback(
            iLogStreamingTaskClient, EcsCommandUnitConstants.rollback.toString(), true, commandUnitsProgress);
    AwsInternalConfig awsInternalConfig = AwsInternalConfig.builder().build();
    doReturn(awsInternalConfig).when(awsNgConfigMapper).createAwsInternalConfig(ecsInfraConfig.getAwsConnectorDTO());
    doReturn("targetGroupArn")
        .when(ecsCommandTaskHelper)
        .getTargetGroupArnFromLoadBalancer(ecsInfraConfig,
            ecsBlueGreenRollbackRequest.getEcsLoadBalancerConfig().getProdListenerArn(),
            ecsBlueGreenRollbackRequest.getEcsLoadBalancerConfig().getProdListenerRuleArn(),
            ecsBlueGreenRollbackRequest.getEcsLoadBalancerConfig().getLoadBalancer(), awsInternalConfig);
    doReturn("targetGroupArn")
        .when(ecsCommandTaskHelper)
        .getTargetGroupArnFromLoadBalancer(ecsInfraConfig,
            ecsBlueGreenRollbackRequest.getEcsLoadBalancerConfig().getStageListenerArn(),
            ecsBlueGreenRollbackRequest.getEcsLoadBalancerConfig().getStageListenerRuleArn(),
            ecsBlueGreenRollbackRequest.getEcsLoadBalancerConfig().getLoadBalancer(), awsInternalConfig);
    Service service = Service.builder().build();
    UpdateServiceResponse updateServiceResponse = UpdateServiceResponse.builder().service(service).build();
    doReturn(updateServiceResponse)
        .when(ecsCommandTaskHelper)
        .updateDesiredCount(ecsBlueGreenRollbackRequest.getNewServiceName(), ecsInfraConfig, awsInternalConfig, 0);

    EcsBlueGreenRollbackResponse ecsBlueGreenRollbackResponse =
        (EcsBlueGreenRollbackResponse) ecsBlueGreenRollbackCommandTaskHandler.executeTaskInternal(
            ecsBlueGreenRollbackRequest, iLogStreamingTaskClient, commandUnitsProgress);

    assertThat(ecsBlueGreenRollbackResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    assertThat(ecsBlueGreenRollbackResponse.getEcsBlueGreenRollbackResult().isFirstDeployment()).isEqualTo(true);
    assertThat(ecsBlueGreenRollbackResponse.getEcsBlueGreenRollbackResult().getLoadBalancer())
        .isEqualTo(ecsLoadBalancerConfig.getLoadBalancer());
    assertThat(ecsBlueGreenRollbackResponse.getEcsBlueGreenRollbackResult().getProdListenerArn())
        .isEqualTo(ecsLoadBalancerConfig.getProdListenerArn());
    assertThat(ecsBlueGreenRollbackResponse.getEcsBlueGreenRollbackResult().getProdListenerRuleArn())
        .isEqualTo(ecsLoadBalancerConfig.getProdListenerRuleArn());
    assertThat(ecsBlueGreenRollbackResponse.getEcsBlueGreenRollbackResult().getStageListenerArn())
        .isEqualTo(ecsLoadBalancerConfig.getStageListenerArn());
    assertThat(ecsBlueGreenRollbackResponse.getEcsBlueGreenRollbackResult().getStageListenerRuleArn())
        .isEqualTo(ecsLoadBalancerConfig.getStageListenerRuleArn());
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void executeTaskInternalTargetShiftStartedTrueIsFirstDeploymentFalseTest() throws Exception {
    EcsBlueGreenRollbackRequest ecsBlueGreenRollbackRequest = EcsBlueGreenRollbackRequest.builder()
                                                                  .commandUnitsProgress(commandUnitsProgress)
                                                                  .ecsInfraConfig(ecsInfraConfig)
                                                                  .timeoutIntervalInMin(10)
                                                                  .ecsLoadBalancerConfig(ecsLoadBalancerConfig)
                                                                  .isTargetShiftStarted(true)
                                                                  .isFirstDeployment(false)
                                                                  .build();

    doReturn(rollbackCallback)
        .when(ecsTaskHelperBase)
        .getLogCallback(
            iLogStreamingTaskClient, EcsCommandUnitConstants.rollback.toString(), true, commandUnitsProgress);
    AwsInternalConfig awsInternalConfig = AwsInternalConfig.builder().build();
    doReturn(awsInternalConfig).when(awsNgConfigMapper).createAwsInternalConfig(ecsInfraConfig.getAwsConnectorDTO());
    doReturn("targetGroupArn")
        .when(ecsCommandTaskHelper)
        .getTargetGroupArnFromLoadBalancer(ecsInfraConfig,
            ecsBlueGreenRollbackRequest.getEcsLoadBalancerConfig().getProdListenerArn(),
            ecsBlueGreenRollbackRequest.getEcsLoadBalancerConfig().getProdListenerRuleArn(),
            ecsBlueGreenRollbackRequest.getEcsLoadBalancerConfig().getLoadBalancer(), awsInternalConfig);
    doReturn("targetGroupArn")
        .when(ecsCommandTaskHelper)
        .getTargetGroupArnFromLoadBalancer(ecsInfraConfig,
            ecsBlueGreenRollbackRequest.getEcsLoadBalancerConfig().getStageListenerArn(),
            ecsBlueGreenRollbackRequest.getEcsLoadBalancerConfig().getStageListenerRuleArn(),
            ecsBlueGreenRollbackRequest.getEcsLoadBalancerConfig().getLoadBalancer(), awsInternalConfig);

    Service service = Service.builder().build();
    UpdateServiceResponse updateServiceResponse = UpdateServiceResponse.builder().service(service).build();
    doReturn(updateServiceResponse)
        .when(ecsCommandTaskHelper)
        .updateDesiredCount(ecsBlueGreenRollbackRequest.getNewServiceName(), ecsInfraConfig, awsInternalConfig, 0);

    EcsBlueGreenRollbackResponse ecsBlueGreenRollbackResponse =
        (EcsBlueGreenRollbackResponse) ecsBlueGreenRollbackCommandTaskHandler.executeTaskInternal(
            ecsBlueGreenRollbackRequest, iLogStreamingTaskClient, commandUnitsProgress);

    assertThat(ecsBlueGreenRollbackResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    assertThat(ecsBlueGreenRollbackResponse.getEcsBlueGreenRollbackResult().isFirstDeployment()).isEqualTo(false);
    assertThat(ecsBlueGreenRollbackResponse.getEcsBlueGreenRollbackResult().getLoadBalancer())
        .isEqualTo(ecsLoadBalancerConfig.getLoadBalancer());
    assertThat(ecsBlueGreenRollbackResponse.getEcsBlueGreenRollbackResult().getProdListenerArn())
        .isEqualTo(ecsLoadBalancerConfig.getProdListenerArn());
    assertThat(ecsBlueGreenRollbackResponse.getEcsBlueGreenRollbackResult().getProdListenerRuleArn())
        .isEqualTo(ecsLoadBalancerConfig.getProdListenerRuleArn());
    assertThat(ecsBlueGreenRollbackResponse.getEcsBlueGreenRollbackResult().getStageListenerArn())
        .isEqualTo(ecsLoadBalancerConfig.getStageListenerArn());
    assertThat(ecsBlueGreenRollbackResponse.getEcsBlueGreenRollbackResult().getStageListenerRuleArn())
        .isEqualTo(ecsLoadBalancerConfig.getStageListenerRuleArn());
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void executeTaskInternalTargetShiftStartedFalseIsFirstDeploymentTrueTest() throws Exception {
    EcsBlueGreenRollbackRequest ecsBlueGreenRollbackRequest = EcsBlueGreenRollbackRequest.builder()
                                                                  .commandUnitsProgress(commandUnitsProgress)
                                                                  .ecsInfraConfig(ecsInfraConfig)
                                                                  .timeoutIntervalInMin(10)
                                                                  .ecsLoadBalancerConfig(ecsLoadBalancerConfig)
                                                                  .isTargetShiftStarted(false)
                                                                  .isFirstDeployment(true)
                                                                  .build();

    doReturn(rollbackCallback)
        .when(ecsTaskHelperBase)
        .getLogCallback(
            iLogStreamingTaskClient, EcsCommandUnitConstants.rollback.toString(), true, commandUnitsProgress);
    AwsInternalConfig awsInternalConfig = AwsInternalConfig.builder().build();
    doReturn(awsInternalConfig).when(awsNgConfigMapper).createAwsInternalConfig(ecsInfraConfig.getAwsConnectorDTO());
    doReturn("targetGroupArn")
        .when(ecsCommandTaskHelper)
        .getTargetGroupArnFromLoadBalancer(ecsInfraConfig,
            ecsBlueGreenRollbackRequest.getEcsLoadBalancerConfig().getProdListenerArn(),
            ecsBlueGreenRollbackRequest.getEcsLoadBalancerConfig().getProdListenerRuleArn(),
            ecsBlueGreenRollbackRequest.getEcsLoadBalancerConfig().getLoadBalancer(), awsInternalConfig);
    doReturn("targetGroupArn")
        .when(ecsCommandTaskHelper)
        .getTargetGroupArnFromLoadBalancer(ecsInfraConfig,
            ecsBlueGreenRollbackRequest.getEcsLoadBalancerConfig().getStageListenerArn(),
            ecsBlueGreenRollbackRequest.getEcsLoadBalancerConfig().getStageListenerRuleArn(),
            ecsBlueGreenRollbackRequest.getEcsLoadBalancerConfig().getLoadBalancer(), awsInternalConfig);

    Service service = Service.builder().build();
    UpdateServiceResponse updateServiceResponse = UpdateServiceResponse.builder().service(service).build();
    doReturn(updateServiceResponse)
        .when(ecsCommandTaskHelper)
        .updateDesiredCount(ecsBlueGreenRollbackRequest.getNewServiceName(), ecsInfraConfig, awsInternalConfig, 0);

    EcsBlueGreenRollbackResponse ecsBlueGreenRollbackResponse =
        (EcsBlueGreenRollbackResponse) ecsBlueGreenRollbackCommandTaskHandler.executeTaskInternal(
            ecsBlueGreenRollbackRequest, iLogStreamingTaskClient, commandUnitsProgress);

    assertThat(ecsBlueGreenRollbackResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    assertThat(ecsBlueGreenRollbackResponse.getEcsBlueGreenRollbackResult().isFirstDeployment()).isEqualTo(true);
    assertThat(ecsBlueGreenRollbackResponse.getEcsBlueGreenRollbackResult().getLoadBalancer())
        .isEqualTo(ecsLoadBalancerConfig.getLoadBalancer());
    assertThat(ecsBlueGreenRollbackResponse.getEcsBlueGreenRollbackResult().getProdListenerArn())
        .isEqualTo(ecsLoadBalancerConfig.getProdListenerArn());
    assertThat(ecsBlueGreenRollbackResponse.getEcsBlueGreenRollbackResult().getProdListenerRuleArn())
        .isEqualTo(ecsLoadBalancerConfig.getProdListenerRuleArn());
    assertThat(ecsBlueGreenRollbackResponse.getEcsBlueGreenRollbackResult().getStageListenerArn())
        .isEqualTo(ecsLoadBalancerConfig.getStageListenerArn());
    assertThat(ecsBlueGreenRollbackResponse.getEcsBlueGreenRollbackResult().getStageListenerRuleArn())
        .isEqualTo(ecsLoadBalancerConfig.getStageListenerRuleArn());
  }
}
