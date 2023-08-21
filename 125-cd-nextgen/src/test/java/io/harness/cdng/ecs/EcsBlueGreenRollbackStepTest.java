/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.ecs;

import static io.harness.cdng.ecs.EcsBlueGreenRollbackStep.ECS_BLUE_GREEN_CREATE_SERVICE_STEP_MISSING;
import static io.harness.rule.OwnerRule.ALLU_VAMSI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.common.beans.SetupAbstractionKeys;
import io.harness.cdng.ecs.beans.EcsBlueGreenCreateServiceDataOutcome;
import io.harness.cdng.ecs.beans.EcsBlueGreenPrepareRollbackDataOutcome;
import io.harness.cdng.ecs.beans.EcsBlueGreenRollbackOutcome;
import io.harness.cdng.ecs.beans.EcsExecutionPassThroughData;
import io.harness.cdng.featureFlag.CDFeatureFlagHelper;
import io.harness.cdng.infra.beans.EcsInfrastructureOutcome;
import io.harness.cdng.instance.info.InstanceInfoService;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.delegate.beans.ecs.EcsBlueGreenRollbackResult;
import io.harness.delegate.beans.instancesync.ServerInstanceInfo;
import io.harness.delegate.beans.instancesync.info.EcsServerInstanceInfo;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.task.ecs.EcsCommandTypeNG;
import io.harness.delegate.task.ecs.EcsInfraConfig;
import io.harness.delegate.task.ecs.EcsLoadBalancerConfig;
import io.harness.delegate.task.ecs.request.EcsBlueGreenRollbackRequest;
import io.harness.delegate.task.ecs.request.EcsBlueGreenRollbackRequest.EcsBlueGreenRollbackRequestBuilder;
import io.harness.delegate.task.ecs.response.EcsBlueGreenRollbackResponse;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.UnitProgress;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.tasks.SkipTaskRequest;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outcome.OutcomeService;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.executables.TaskChainResponse;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.v1.StepBaseParameters;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;

import software.wings.beans.TaskType;

import java.util.Arrays;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class EcsBlueGreenRollbackStepTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  private final Ambiance ambiance = Ambiance.newBuilder()
                                        .putSetupAbstractions(SetupAbstractionKeys.accountId, "test-account")
                                        .putSetupAbstractions(SetupAbstractionKeys.orgIdentifier, "test-org")
                                        .putSetupAbstractions(SetupAbstractionKeys.projectIdentifier, "test-project")
                                        .build();
  private final EcsBlueGreenRollbackStepParameters ecsSpecParameters =
      EcsBlueGreenRollbackStepParameters.infoBuilder().ecsBlueGreenCreateServiceFnq("serviceFnq").build();
  private final StepElementParameters stepElementParameters =
      StepElementParameters.builder().spec(ecsSpecParameters).timeout(ParameterField.createValueField("10m")).build();
  public static final String ECS_BLUE_GREEN_ROLLBACK_COMMAND_NAME = "EcsBlueGreenRollback";

  @Mock private EcsStepCommonHelper ecsStepCommonHelper;
  @Mock private InstanceInfoService instanceInfoService;
  @Mock private ExecutionSweepingOutputService executionSweepingOutputService;
  @Mock private OutcomeService outcomeService;
  @Mock private CDFeatureFlagHelper cdFeatureFlagHelper;

  @Spy @InjectMocks private EcsBlueGreenRollbackStep ecsBlueGreenRollbackStep;
  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void getStepParametersClassTest() {
    Class<StepBaseParameters> stepElementParametersClass = ecsBlueGreenRollbackStep.getStepParametersClass();
    assertThat(stepElementParametersClass).isEqualTo(StepBaseParameters.class);
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void handleTaskResultWithSecurityContextCommandExecutionStatusFailureTest() throws Exception {
    UnitProgressData unitProgressData =
        UnitProgressData.builder().unitProgresses(Arrays.asList(UnitProgress.newBuilder().build())).build();
    EcsBlueGreenRollbackResponse responseData = EcsBlueGreenRollbackResponse.builder()
                                                    .commandExecutionStatus(CommandExecutionStatus.FAILURE)
                                                    .errorMessage("error")
                                                    .unitProgressData(unitProgressData)
                                                    .build();
    StepResponse stepResponse = ecsBlueGreenRollbackStep.handleTaskResultWithSecurityContext(
        ambiance, stepElementParameters, () -> responseData);

    assertThat(stepResponse.getFailureInfo().getErrorMessage()).isEqualTo(responseData.getErrorMessage());
    assertThat(stepResponse.getStatus()).isEqualTo(Status.FAILED);
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void handleTaskResultWithSecurityContextCommandExecutionStatusSuccessTest() throws Exception {
    UnitProgressData unitProgressData =
        UnitProgressData.builder().unitProgresses(Arrays.asList(UnitProgress.newBuilder().build())).build();
    EcsBlueGreenRollbackResult ecsBlueGreenSwapTargetGroupsResult = EcsBlueGreenRollbackResult.builder()
                                                                        .infrastructureKey("infraKey")
                                                                        .loadBalancer("lb")
                                                                        .prodListenerArn("prodArn")
                                                                        .prodListenerRuleArn("prodLisArn")
                                                                        .stageListenerArn("stageLisArn")
                                                                        .stageTargetGroupArn("stageGroupArn")
                                                                        .build();
    EcsBlueGreenRollbackResponse responseData = EcsBlueGreenRollbackResponse.builder()
                                                    .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                                    .unitProgressData(unitProgressData)
                                                    .ecsBlueGreenRollbackResult(ecsBlueGreenSwapTargetGroupsResult)
                                                    .build();

    List<ServerInstanceInfo> serverInstanceInfos = Arrays.asList(EcsServerInstanceInfo.builder().build());
    doReturn(serverInstanceInfos)
        .when(ecsStepCommonHelper)
        .getServerInstanceInfos(responseData, ecsBlueGreenSwapTargetGroupsResult.getInfrastructureKey());

    EcsBlueGreenRollbackOutcome ecsBlueGreenRollbackOutcome = EcsBlueGreenRollbackOutcome.builder().build();
    StepResponse.StepOutcome stepOutcomeMock = StepResponse.StepOutcome.builder()
                                                   .name(OutcomeExpressionConstants.OUTPUT)
                                                   .outcome(ecsBlueGreenRollbackOutcome)
                                                   .build();
    doReturn(stepOutcomeMock)
        .when(instanceInfoService)
        .saveServerInstancesIntoSweepingOutput(ambiance, serverInstanceInfos);
    instanceInfoService.saveServerInstancesIntoSweepingOutput(ambiance, serverInstanceInfos);

    StepResponse stepResponse = ecsBlueGreenRollbackStep.handleTaskResultWithSecurityContext(
        ambiance, stepElementParameters, () -> responseData);
    StepResponse.StepOutcome stepOutcome = stepResponse.getStepOutcomes().stream().findFirst().get();
    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
    assertThat(stepOutcome.getOutcome()).isEqualTo(stepOutcomeMock.getOutcome());
    assertThat(stepOutcome.getName()).isEqualTo(stepOutcomeMock.getName());
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void obtainTaskAfterRbacSkipTaskRequestTest() {
    StepInputPackage inputPackage = StepInputPackage.builder().build();
    EcsBlueGreenRollbackStepParameters ecsBlueGreenRollbackStepParameters =
        EcsBlueGreenRollbackStepParameters.infoBuilder().build();
    StepElementParameters stepParameters = StepElementParameters.builder()
                                               .spec(ecsBlueGreenRollbackStepParameters)
                                               .timeout(ParameterField.createValueField("10m"))
                                               .build();
    TaskRequest taskRequest = ecsBlueGreenRollbackStep.obtainTaskAfterRbac(ambiance, stepParameters, inputPackage);
    TaskRequest taskRequestAssert =
        TaskRequest.newBuilder()
            .setSkipTaskRequest(
                SkipTaskRequest.newBuilder().setMessage(ECS_BLUE_GREEN_CREATE_SERVICE_STEP_MISSING).build())
            .build();
    assertThat(taskRequest.getSkipTaskRequest()).isEqualTo(taskRequestAssert.getSkipTaskRequest());
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void obtainTaskAfterRbacTest() {
    StepInputPackage inputPackage = StepInputPackage.builder().build();
    EcsBlueGreenPrepareRollbackDataOutcome ecsBlueGreenPrepareRollbackDataOutcome =
        EcsBlueGreenPrepareRollbackDataOutcome.builder()
            .loadBalancer("lb")
            .prodListenerArn("lisArn")
            .prodListenerRuleArn("lisRuleArn")
            .prodTargetGroupArn("grpArn")
            .isFirstDeployment(true)
            .serviceName("service__1")
            .build();
    OptionalSweepingOutput ecsBlueGreenPrepareRollbackDataOptionalOutput =
        OptionalSweepingOutput.builder().found(true).output(ecsBlueGreenPrepareRollbackDataOutcome).build();

    doReturn(ecsBlueGreenPrepareRollbackDataOptionalOutput)
        .when(executionSweepingOutputService)
        .resolveOptional(ambiance,
            RefObjectUtils.getSweepingOutputRefObject(ecsSpecParameters.getEcsBlueGreenCreateServiceFnq() + "."
                + OutcomeExpressionConstants.ECS_BLUE_GREEN_PREPARE_ROLLBACK_DATA_OUTCOME));

    EcsBlueGreenCreateServiceDataOutcome ecsBlueGreenCreateServiceDataOutcome =
        EcsBlueGreenCreateServiceDataOutcome.builder().serviceName("service__2").build();
    OptionalSweepingOutput ecsBlueGreenSwapTargetGroupsDataOptionalOutput =
        OptionalSweepingOutput.builder().found(true).output(ecsBlueGreenCreateServiceDataOutcome).build();
    doReturn(ecsBlueGreenSwapTargetGroupsDataOptionalOutput)
        .when(executionSweepingOutputService)
        .resolveOptional(ambiance,
            RefObjectUtils.getSweepingOutputRefObject(ecsSpecParameters.getEcsBlueGreenCreateServiceFnq() + "."
                + OutcomeExpressionConstants.ECS_BLUE_GREEN_CREATE_SERVICE_OUTCOME));
    EcsInfrastructureOutcome infrastructureOutcome = EcsInfrastructureOutcome.builder().build();
    doReturn(infrastructureOutcome)
        .when(outcomeService)
        .resolve(ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.INFRASTRUCTURE_OUTCOME));
    doReturn(EcsInfraConfig.builder().build())
        .when(ecsStepCommonHelper)
        .getEcsInfraConfig(infrastructureOutcome, ambiance);
    EcsExecutionPassThroughData ecsExecutionPassThroughData =
        EcsExecutionPassThroughData.builder().infrastructure(infrastructureOutcome).build();
    TaskChainResponse taskChainResponseMock = TaskChainResponse.builder()
                                                  .chainEnd(false)
                                                  .taskRequest(TaskRequest.newBuilder().build())
                                                  .passThroughData(ecsExecutionPassThroughData)
                                                  .build();
    doReturn(taskChainResponseMock)
        .when(ecsStepCommonHelper)
        .queueEcsTask(any(), any(), any(), any(), anyBoolean(), eq(TaskType.ECS_COMMAND_TASK_NG));

    doReturn(false).when(cdFeatureFlagHelper).isEnabled(any(), any());

    ecsBlueGreenRollbackStep.obtainTaskAfterRbac(ambiance, stepElementParameters, inputPackage);

    final String accountId = AmbianceUtils.getAccountId(ambiance);
    EcsLoadBalancerConfig ecsLoadBalancerConfig =
        EcsLoadBalancerConfig.builder()
            .loadBalancer(ecsBlueGreenPrepareRollbackDataOutcome.getLoadBalancer())
            .prodListenerArn(ecsBlueGreenPrepareRollbackDataOutcome.getProdListenerArn())
            .prodListenerRuleArn(ecsBlueGreenPrepareRollbackDataOutcome.getProdListenerRuleArn())
            .prodTargetGroupArn(ecsBlueGreenPrepareRollbackDataOutcome.getProdTargetGroupArn())
            .stageListenerArn(ecsBlueGreenPrepareRollbackDataOutcome.getStageListenerArn())
            .stageListenerRuleArn(ecsBlueGreenPrepareRollbackDataOutcome.getStageListenerRuleArn())
            .stageTargetGroupArn(ecsBlueGreenPrepareRollbackDataOutcome.getStageTargetGroupArn())
            .build();

    EcsBlueGreenRollbackRequestBuilder ecsBlueGreenRollbackRequestBuilder =
        EcsBlueGreenRollbackRequest.builder()
            .accountId(accountId)
            .ecsCommandType(EcsCommandTypeNG.ECS_BLUE_GREEN_ROLLBACK)
            .commandName(ECS_BLUE_GREEN_ROLLBACK_COMMAND_NAME)
            .commandUnitsProgress(CommandUnitsProgress.builder().build())
            .ecsInfraConfig(ecsStepCommonHelper.getEcsInfraConfig(infrastructureOutcome, ambiance))
            .timeoutIntervalInMin(CDStepHelper.getTimeoutInMin(stepElementParameters))
            .oldServiceName(ecsBlueGreenPrepareRollbackDataOutcome.getServiceName())
            .newServiceName(ecsBlueGreenCreateServiceDataOutcome.getServiceName())
            .isFirstDeployment(ecsBlueGreenPrepareRollbackDataOutcome.isFirstDeployment())
            .isNewServiceCreated(ecsBlueGreenCreateServiceDataOutcome.isNewServiceCreated())
            .oldServiceCreateRequestBuilderString(
                ecsBlueGreenPrepareRollbackDataOutcome.getCreateServiceRequestBuilderString())
            .oldServiceScalableTargetManifestContentList(
                ecsBlueGreenPrepareRollbackDataOutcome.getRegisterScalableTargetRequestBuilderStrings())
            .oldServiceScalingPolicyManifestContentList(
                ecsBlueGreenPrepareRollbackDataOutcome.getRegisterScalingPolicyRequestBuilderStrings())
            .ecsLoadBalancerConfig(ecsLoadBalancerConfig);
    verify(ecsStepCommonHelper)
        .queueEcsTask(stepElementParameters, ecsBlueGreenRollbackRequestBuilder.build(), ambiance,
            EcsExecutionPassThroughData.builder().infrastructure(infrastructureOutcome).build(), true,
            TaskType.ECS_COMMAND_TASK_NG);
  }
}
