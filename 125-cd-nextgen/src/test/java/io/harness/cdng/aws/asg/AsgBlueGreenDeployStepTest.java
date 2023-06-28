/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.aws.asg;

import static io.harness.rule.OwnerRule.LOVISH_BANSAL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.aws.beans.AsgLoadBalancerConfig;
import io.harness.category.element.UnitTests;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.common.beans.SetupAbstractionKeys;
import io.harness.cdng.infra.beans.AsgInfrastructureOutcome;
import io.harness.cdng.instance.info.InstanceInfoService;
import io.harness.cdng.instance.outcome.DeploymentInfoOutcome;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.common.ParameterFieldHelper;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.beans.logstreaming.UnitProgressDataMapper;
import io.harness.delegate.task.aws.asg.AsgBlueGreenDeployRequest;
import io.harness.delegate.task.aws.asg.AsgBlueGreenDeployResponse;
import io.harness.delegate.task.aws.asg.AsgBlueGreenDeployResult;
import io.harness.delegate.task.aws.asg.AsgBlueGreenPrepareRollbackDataRequest;
import io.harness.delegate.task.aws.asg.AsgBlueGreenPrepareRollbackDataResponse;
import io.harness.delegate.task.aws.asg.AsgBlueGreenPrepareRollbackDataResult;
import io.harness.delegate.task.aws.asg.AsgCanaryDeployResponse;
import io.harness.delegate.task.aws.asg.AsgInfraConfig;
import io.harness.delegate.task.aws.asg.AutoScalingGroupContainer;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.UnitProgress;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.data.Outcome;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.executables.TaskChainResponse;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.tasks.ResponseData;

import software.wings.beans.TaskType;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class AsgBlueGreenDeployStepTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  private final Ambiance ambiance = Ambiance.newBuilder()
                                        .putSetupAbstractions(SetupAbstractionKeys.accountId, "test-account")
                                        .putSetupAbstractions(SetupAbstractionKeys.orgIdentifier, "test-org")
                                        .putSetupAbstractions(SetupAbstractionKeys.projectIdentifier, "test-project")
                                        .build();

  private final String accountId = "test-account";

  private final AsgBlueGreenDeployStepParameters asgSpecParameters =
      AsgBlueGreenDeployStepParameters.infoBuilder()
          .loadBalancer(ParameterField.<String>builder().value("lb").build())
          .prodListener(ParameterField.<String>builder().value("prodLis").build())
          .prodListenerRuleArn(ParameterField.<String>builder().value("prodArn").build())
          .stageListener(ParameterField.<String>builder().value("stageLis").build())
          .stageListenerRuleArn(ParameterField.<String>builder().value("stageArn").build())
          .useAlreadyRunningInstances(ParameterField.<Boolean>builder().value(false).build())
          .build();
  private final StepElementParameters stepElementParameters =
      StepElementParameters.builder().spec(asgSpecParameters).timeout(ParameterField.createValueField("10m")).build();

  UnitProgressData unitProgressData =
      UnitProgressData.builder().unitProgresses(Arrays.asList(UnitProgress.newBuilder().build())).build();

  private static final String ASG_BLUE_GREEN_DEPLOY_COMMAND_NAME = "AsgBlueGreenDeploy";
  private static final String ASG_BLUE_GREEN_PREPARE_ROLLBACK_DATA_COMMAND_NAME = "AsgBlueGreenPrepareRollbackData";
  private static final String amiImageName = "ami123";
  private static final String asgName = "asg";
  private static final String prodAsgName = "prodAsg";
  private static final AsgInfraConfig asgInfraConfig = AsgInfraConfig.builder().build();

  private static Map<String, List<String>> asgStoreManifestsContent = new HashMap<>();
  private static AsgPrepareRollbackDataPassThroughData asgPrepareRollbackDataPassThroughData;
  private static AsgInfrastructureOutcome asgInfrastructureOutcome;
  private static StepInputPackage inputPackage;
  private static AsgLoadBalancerConfig asgLoadBalancerConfig;
  private static AsgBlueGreenExecutionPassThroughData asgBlueGreenExecutionPassThroughData;
  @Spy private AsgStepCommonHelper asgStepCommonHelper;
  @Spy @InjectMocks private AsgBlueGreenDeployStep asgBlueGreenDeployStep;
  @Spy private InstanceInfoService instanceInfoService;
  @Mock ExecutionSweepingOutputService executionSweepingOutputService;

  @BeforeClass
  public static void setup() throws IOException {
    inputPackage = StepInputPackage.builder().build();

    asgStoreManifestsContent.put("AsgLaunchTemplate", Collections.singletonList("asgLaunchTemplate"));
    asgStoreManifestsContent.put("AsgConfiguration", Collections.singletonList("asgConfiguration"));
    asgStoreManifestsContent.put("AsgScalingPolicy", Collections.singletonList("asgScalingPolicy"));

    asgInfrastructureOutcome = AsgInfrastructureOutcome.builder().infrastructureKey("infraKey").build();

    asgBlueGreenExecutionPassThroughData = AsgBlueGreenExecutionPassThroughData.blueGreenBuilder()
                                               .loadBalancerConfig(asgLoadBalancerConfig)
                                               .asgName(asgName)
                                               .asgManifestFetchData(AsgManifestFetchData.builder().build())
                                               .firstDeployment(false)
                                               .infrastructure(asgInfrastructureOutcome)
                                               .build();

    asgPrepareRollbackDataPassThroughData = AsgPrepareRollbackDataPassThroughData.builder()
                                                .infrastructureOutcome(asgInfrastructureOutcome)
                                                .asgStoreManifestsContent(asgStoreManifestsContent)
                                                .build();

    asgLoadBalancerConfig = AsgLoadBalancerConfig.builder()
                                .loadBalancer("lb")
                                .prodListenerArn("prodLis")
                                .prodListenerRuleArn("prodArn")
                                .stageListenerArn("stageLis")
                                .stageListenerRuleArn("stageArn")
                                .stageTargetGroupArnsList(List.of("stageTarget"))
                                .prodTargetGroupArnsList(List.of("prodTarget"))
                                .build();
  }

  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void executeNextLinkWithSecurityContextTest() throws Exception {
    AsgBlueGreenPrepareRollbackDataResult asgBlueGreenPrepareRollbackDataResult =
        AsgBlueGreenPrepareRollbackDataResult.builder()
            .asgName(asgName)
            .prodAsgName(prodAsgName)
            .stageAsgManifestsDataForRollback(asgStoreManifestsContent)
            .prodAsgManifestsDataForRollback(asgStoreManifestsContent)
            .asgLoadBalancerConfig(asgLoadBalancerConfig)
            .build();

    ResponseData responseData = AsgBlueGreenPrepareRollbackDataResponse.builder()
                                    .unitProgressData(unitProgressData)
                                    .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                    .asgBlueGreenPrepareRollbackDataResult(asgBlueGreenPrepareRollbackDataResult)
                                    .build();

    AsgBlueGreenExecutionPassThroughData asgBlueGreenExecutionPassThroughData =
        AsgBlueGreenExecutionPassThroughData.blueGreenBuilder()
            .infrastructure(asgPrepareRollbackDataPassThroughData.getInfrastructureOutcome())
            .lastActiveUnitProgressData(unitProgressData)
            .asgName(asgBlueGreenPrepareRollbackDataResult.getAsgName())
            .loadBalancerConfig(asgBlueGreenPrepareRollbackDataResult.getAsgLoadBalancerConfig())
            .firstDeployment(false)
            .build();

    TaskChainResponse taskChainResponse1 = TaskChainResponse.builder()
                                               .chainEnd(true)
                                               .taskRequest(TaskRequest.newBuilder().build())
                                               .passThroughData(asgBlueGreenExecutionPassThroughData)
                                               .build();

    doReturn(taskChainResponse1).when(asgBlueGreenDeployStep).executeAsgTask(any(), any(), any(), any(), any());

    AsgStepExecutorParams asgStepExecutorParams = AsgStepExecutorParams.builder()
                                                      .shouldOpenFetchFilesLogStream(false)
                                                      .asgStoreManifestsContent(asgStoreManifestsContent)
                                                      .build();

    asgBlueGreenDeployStep.executeNextLinkWithSecurityContext(
        ambiance, stepElementParameters, inputPackage, asgPrepareRollbackDataPassThroughData, () -> responseData);

    verify(asgBlueGreenDeployStep)
        .executeAsgTask(
            eq(ambiance), eq(stepElementParameters), any(), eq(unitProgressData), eq(asgStepExecutorParams));
  }

  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void executeAsgTaskTest() {
    doReturn(asgInfraConfig).when(asgStepCommonHelper).getAsgInfraConfig(any(), any());
    doReturn(amiImageName).when(asgStepCommonHelper).getAmiImageId(ambiance);

    AsgStepExecutorParams asgStepExecutorParams =
        AsgStepExecutorParams.builder().asgStoreManifestsContent(asgStoreManifestsContent).build();

    TaskChainResponse taskChainResponse = TaskChainResponse.builder()
                                              .chainEnd(true)
                                              .taskRequest(TaskRequest.newBuilder().build())
                                              .passThroughData(asgBlueGreenExecutionPassThroughData)
                                              .build();

    doReturn(taskChainResponse)
        .when(asgStepCommonHelper)
        .queueAsgTask(any(), any(), any(), any(), anyBoolean(), any(TaskType.class));

    asgBlueGreenDeployStep.executeAsgTask(
        ambiance, stepElementParameters, asgBlueGreenExecutionPassThroughData, unitProgressData, asgStepExecutorParams);

    final String accountId = AmbianceUtils.getAccountId(ambiance);
    AsgBlueGreenDeployRequest asgBlueGreenDeployRequest =
        AsgBlueGreenDeployRequest.builder()
            .commandName(ASG_BLUE_GREEN_DEPLOY_COMMAND_NAME)
            .accountId(accountId)
            .asgInfraConfig(asgInfraConfig)
            .asgStoreManifestsContent(asgStoreManifestsContent)
            .commandUnitsProgress(UnitProgressDataMapper.toCommandUnitsProgress(unitProgressData))
            .timeoutIntervalInMin(CDStepHelper.getTimeoutInMin(stepElementParameters))
            .asgName(asgBlueGreenExecutionPassThroughData.getAsgName())
            .firstDeployment(asgBlueGreenExecutionPassThroughData.isFirstDeployment())
            .asgLoadBalancerConfig(asgBlueGreenExecutionPassThroughData.getLoadBalancerConfig())
            .useAlreadyRunningInstances(
                ParameterFieldHelper.getBooleanParameterFieldValue(asgSpecParameters.getUseAlreadyRunningInstances()))
            .amiImageId(amiImageName)
            .build();

    verify(asgStepCommonHelper)
        .queueAsgTask(stepElementParameters, asgBlueGreenDeployRequest, ambiance, asgBlueGreenExecutionPassThroughData,
            true, TaskType.AWS_ASG_BLUE_GREEN_DEPLOY_TASK_NG);
  }

  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void executeAsgPrepareRollbackTaskTest() {
    doReturn(asgInfraConfig).when(asgStepCommonHelper).getAsgInfraConfig(any(), any());

    TaskChainResponse taskChainResponse = TaskChainResponse.builder()
                                              .chainEnd(false)
                                              .taskRequest(TaskRequest.newBuilder().build())
                                              .passThroughData(asgPrepareRollbackDataPassThroughData)
                                              .build();

    doReturn(taskChainResponse)
        .when(asgStepCommonHelper)
        .queueAsgTask(any(), any(), any(), any(), anyBoolean(), any(TaskType.class));

    AsgLoadBalancerConfig asgLoadBalancerConfig2 =
        AsgLoadBalancerConfig.builder()
            .loadBalancer(asgSpecParameters.getLoadBalancer().getValue())
            .prodListenerArn(asgSpecParameters.getProdListener().getValue())
            .prodListenerRuleArn(asgSpecParameters.getProdListenerRuleArn().getValue())
            .stageListenerArn(asgSpecParameters.getStageListener().getValue())
            .stageListenerRuleArn(asgSpecParameters.getStageListenerRuleArn().getValue())
            .build();

    AsgBlueGreenPrepareRollbackDataRequest asgBlueGreenPrepareRollbackDataRequest =
        AsgBlueGreenPrepareRollbackDataRequest.builder()
            .commandName(ASG_BLUE_GREEN_PREPARE_ROLLBACK_DATA_COMMAND_NAME)
            .accountId(accountId)
            .asgInfraConfig(asgInfraConfig)
            .asgStoreManifestsContent(asgStoreManifestsContent)
            .commandUnitsProgress(UnitProgressDataMapper.toCommandUnitsProgress(unitProgressData))
            .timeoutIntervalInMin(CDStepHelper.getTimeoutInMin(stepElementParameters))
            .asgLoadBalancerConfig(asgLoadBalancerConfig2)
            .build();

    asgBlueGreenDeployStep.executeAsgPrepareRollbackDataTask(
        ambiance, stepElementParameters, asgPrepareRollbackDataPassThroughData, unitProgressData);

    verify(asgStepCommonHelper)
        .queueAsgTask(stepElementParameters, asgBlueGreenPrepareRollbackDataRequest, ambiance,
            asgPrepareRollbackDataPassThroughData, false, TaskType.AWS_ASG_BLUE_GREEN_PREPARE_ROLLBACK_DATA_TASK_NG);
  }

  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void finalizeExecutionWithSecurityContextAsgStepExceptionPassThroughDataTest() throws Exception {
    AsgStepExceptionPassThroughData asgStepExceptionPassThroughData =
        AsgStepExceptionPassThroughData.builder().unitProgressData(unitProgressData).errorMessage("error").build();
    ResponseData responseData = AsgCanaryDeployResponse.builder().build();

    StepResponse stepResponse = asgBlueGreenDeployStep.finalizeExecutionWithSecurityContext(
        ambiance, stepElementParameters, asgStepExceptionPassThroughData, () -> responseData);

    assertThat(stepResponse.getUnitProgressList()).isEqualTo(Arrays.asList(UnitProgress.newBuilder().build()));
    assertThat(stepResponse.getStatus()).isEqualTo(Status.FAILED);
  }

  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void finalizeExecutionWithSecurityContextCommandExecutionStatusSuccessTest() throws Exception {
    doReturn(asgInfraConfig).when(asgStepCommonHelper).getAsgInfraConfig(asgInfrastructureOutcome, ambiance);

    ResponseData responseData =
        AsgBlueGreenDeployResponse.builder()
            .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
            .asgBlueGreenDeployResult(AsgBlueGreenDeployResult.builder()
                                          .stageAutoScalingGroupContainer(
                                              AutoScalingGroupContainer.builder().autoScalingGroupName("asg").build())
                                          .prodAutoScalingGroupContainer(AutoScalingGroupContainer.builder().build())
                                          .build())
            .unitProgressData(unitProgressData)
            .errorMessage("error")
            .build();

    doReturn(StepResponse.StepOutcome.builder()
                 .outcome(DeploymentInfoOutcome.builder().build())
                 .name(OutcomeExpressionConstants.DEPLOYMENT_INFO_OUTCOME)
                 .build())
        .when(instanceInfoService)
        .saveServerInstancesIntoSweepingOutput(any(), any());

    StepResponse stepResponse = asgBlueGreenDeployStep.finalizeExecutionWithSecurityContext(
        ambiance, stepElementParameters, asgBlueGreenExecutionPassThroughData, () -> responseData);

    Map<String, Outcome> outcomeMap = stepResponse.getStepOutcomes().stream().collect(
        Collectors.toMap(StepResponse.StepOutcome::getName, StepResponse.StepOutcome::getOutcome));

    assertThat(outcomeMap.get(OutcomeExpressionConstants.OUTPUT)).isInstanceOf(AsgBlueGreenDeployOutcome.class);
    assertThat(outcomeMap.get(OutcomeExpressionConstants.DEPLOYMENT_INFO_OUTCOME))
        .isInstanceOf(DeploymentInfoOutcome.class);
  }
}