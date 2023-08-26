/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.ecs;

import static io.harness.eraro.ErrorCode.GENERAL_ERROR;
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
import io.harness.cdng.ecs.beans.EcsExecutionPassThroughData;
import io.harness.cdng.ecs.beans.EcsGitFetchFailurePassThroughData;
import io.harness.cdng.ecs.beans.EcsPrepareRollbackDataPassThroughData;
import io.harness.cdng.ecs.beans.EcsStepExceptionPassThroughData;
import io.harness.cdng.ecs.beans.EcsStepExecutorParams;
import io.harness.cdng.featureFlag.CDFeatureFlagHelper;
import io.harness.cdng.infra.beans.EcsInfrastructureOutcome;
import io.harness.cdng.instance.info.InstanceInfoService;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.data.structure.HarnessStringUtils;
import io.harness.delegate.beans.ecs.EcsBlueGreenCreateServiceResult;
import io.harness.delegate.beans.instancesync.ServerInstanceInfo;
import io.harness.delegate.beans.instancesync.info.EcsServerInstanceInfo;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.beans.logstreaming.UnitProgressDataMapper;
import io.harness.delegate.task.ecs.EcsCommandTypeNG;
import io.harness.delegate.task.ecs.EcsInfraConfig;
import io.harness.delegate.task.ecs.EcsLoadBalancerConfig;
import io.harness.delegate.task.ecs.request.EcsBlueGreenCreateServiceRequest;
import io.harness.delegate.task.ecs.request.EcsBlueGreenPrepareRollbackRequest;
import io.harness.delegate.task.ecs.response.EcsBlueGreenCreateServiceResponse;
import io.harness.delegate.task.ecs.response.EcsBlueGreenPrepareRollbackDataResponse;
import io.harness.delegate.task.ecs.response.EcsGitFetchResponse;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.UnitProgress;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureData;
import io.harness.pms.contracts.execution.failure.FailureType;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.plan.creation.yaml.StepOutcomeGroup;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.executables.TaskChainResponse;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.v1.StepBaseParameters;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.supplier.ThrowingSupplier;
import io.harness.tasks.ResponseData;

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

public class EcsBlueGreenCreateServiceStepTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  private final Ambiance ambiance = Ambiance.newBuilder()
                                        .putSetupAbstractions(SetupAbstractionKeys.accountId, "test-account")
                                        .putSetupAbstractions(SetupAbstractionKeys.orgIdentifier, "test-org")
                                        .putSetupAbstractions(SetupAbstractionKeys.projectIdentifier, "test-project")
                                        .build();
  private final EcsBlueGreenCreateServiceStepParameters ecsSpecParameters =
      EcsBlueGreenCreateServiceStepParameters.infoBuilder()
          .loadBalancer(ParameterField.<String>builder().value("lb").build())
          .prodListener(ParameterField.<String>builder().value("prod").build())
          .prodListenerRuleArn(ParameterField.<String>builder().value("arnProd").build())
          .stageListener(ParameterField.<String>builder().value("lis").build())
          .stageListenerRuleArn(ParameterField.<String>builder().value("arnLis").build())
          .build();
  private final StepElementParameters stepElementParameters =
      StepElementParameters.builder().spec(ecsSpecParameters).timeout(ParameterField.createValueField("10m")).build();
  private final String ECS_BLUE_GREEN__CREATE_SERVICE_COMMAND_NAME = "EcsBlueGreenCreateService";
  private final String ECS_BLUE_GREEN_PREPARE_ROLLBACK_COMMAND_NAME = "EcsBlueGreenPrepareRollback";

  @Spy private EcsStepCommonHelper ecsStepCommonHelper;
  @Mock private ExecutionSweepingOutputService executionSweepingOutputService;
  @Mock private InstanceInfoService instanceInfoService;
  @Mock private CDFeatureFlagHelper cdFeatureFlagHelper;

  @Spy @InjectMocks private EcsBlueGreenCreateServiceStep ecsBlueGreenCreateServiceStep;

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void executeEcsTaskTest() {
    EcsInfrastructureOutcome infrastructureOutcome = EcsInfrastructureOutcome.builder().build();
    EcsExecutionPassThroughData ecsExecutionPassThroughData =
        EcsExecutionPassThroughData.builder().infrastructure(infrastructureOutcome).build();
    UnitProgressData unitProgressData =
        UnitProgressData.builder().unitProgresses(Arrays.asList(UnitProgress.newBuilder().build())).build();
    EcsStepExecutorParams ecsStepExecutorParams = EcsStepExecutorParams.builder()
                                                      .ecsTaskDefinitionManifestContent("taskDef")
                                                      .ecsServiceDefinitionManifestContent("service")
                                                      .targetGroupArnKey("targetArn")
                                                      .build();
    EcsInfraConfig ecsInfraConfig = EcsInfraConfig.builder().build();
    doReturn(ecsInfraConfig).when(ecsStepCommonHelper).getEcsInfraConfig(infrastructureOutcome, ambiance);

    TaskChainResponse taskChainResponse = TaskChainResponse.builder()
                                              .chainEnd(false)
                                              .taskRequest(TaskRequest.newBuilder().build())
                                              .passThroughData(ecsExecutionPassThroughData)
                                              .build();

    doReturn(taskChainResponse)
        .when(ecsStepCommonHelper)
        .queueEcsTask(any(), any(), any(), any(), anyBoolean(), eq(TaskType.ECS_COMMAND_TASK_NG));

    ecsBlueGreenCreateServiceStep.executeEcsTask(
        ambiance, stepElementParameters, ecsExecutionPassThroughData, unitProgressData, ecsStepExecutorParams);
    EcsBlueGreenCreateServiceStepParameters ecsBlueGreenCreateServiceStepParameters =
        (EcsBlueGreenCreateServiceStepParameters) stepElementParameters.getSpec();
    EcsLoadBalancerConfig ecsLoadBalancerConfig =
        EcsLoadBalancerConfig.builder()
            .loadBalancer(ecsBlueGreenCreateServiceStepParameters.getLoadBalancer().getValue())
            .prodListenerArn(ecsBlueGreenCreateServiceStepParameters.getProdListener().getValue())
            .prodListenerRuleArn(ecsBlueGreenCreateServiceStepParameters.getProdListenerRuleArn().getValue())
            .stageListenerArn(ecsBlueGreenCreateServiceStepParameters.getStageListener().getValue())
            .stageListenerRuleArn(ecsBlueGreenCreateServiceStepParameters.getStageListenerRuleArn().getValue())
            .prodTargetGroupArn(ecsStepExecutorParams.getProdTargetGroupArn())
            .stageTargetGroupArn(ecsStepExecutorParams.getStageTargetGroupArn())
            .build();
    final String accountId = AmbianceUtils.getAccountId(ambiance);
    EcsBlueGreenCreateServiceRequest ecsBlueGreenCreateServiceRequest =
        EcsBlueGreenCreateServiceRequest.builder()
            .accountId(accountId)
            .ecsCommandType(EcsCommandTypeNG.ECS_BLUE_GREEN_CREATE_SERVICE)
            .commandName(ECS_BLUE_GREEN__CREATE_SERVICE_COMMAND_NAME)
            .commandUnitsProgress(UnitProgressDataMapper.toCommandUnitsProgress(unitProgressData))
            .ecsInfraConfig(ecsStepCommonHelper.getEcsInfraConfig(infrastructureOutcome, ambiance))
            .timeoutIntervalInMin(CDStepHelper.getTimeoutInMin(stepElementParameters))
            .ecsTaskDefinitionManifestContent(ecsStepExecutorParams.getEcsTaskDefinitionManifestContent())
            .ecsServiceDefinitionManifestContent(ecsStepExecutorParams.getEcsServiceDefinitionManifestContent())
            .ecsScalableTargetManifestContentList(ecsStepExecutorParams.getEcsScalableTargetManifestContentList())
            .ecsScalingPolicyManifestContentList(ecsStepExecutorParams.getEcsScalingPolicyManifestContentList())
            .ecsLoadBalancerConfig(ecsLoadBalancerConfig)
            .targetGroupArnKey(ecsStepExecutorParams.getTargetGroupArnKey())
            .build();
    verify(ecsStepCommonHelper)
        .queueEcsTask(stepElementParameters, ecsBlueGreenCreateServiceRequest, ambiance, ecsExecutionPassThroughData,
            true, TaskType.ECS_COMMAND_TASK_NG);
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void executeEcsPrepareRollbackTaskTest() {
    UnitProgressData unitProgressData =
        UnitProgressData.builder().unitProgresses(Arrays.asList(UnitProgress.newBuilder().build())).build();
    EcsInfrastructureOutcome infrastructureOutcome = EcsInfrastructureOutcome.builder().build();
    EcsPrepareRollbackDataPassThroughData ecsStepPassThroughData =
        EcsPrepareRollbackDataPassThroughData.builder().infrastructureOutcome(infrastructureOutcome).build();
    EcsInfraConfig ecsInfraConfig = EcsInfraConfig.builder().build();

    doReturn(ecsInfraConfig).when(ecsStepCommonHelper).getEcsInfraConfig(infrastructureOutcome, ambiance);

    TaskChainResponse taskChainResponseMock = TaskChainResponse.builder()
                                                  .chainEnd(false)
                                                  .taskRequest(TaskRequest.newBuilder().build())
                                                  .passThroughData(ecsStepPassThroughData)
                                                  .build();

    doReturn(taskChainResponseMock)
        .when(ecsStepCommonHelper)
        .queueEcsTask(any(), any(), any(), any(), anyBoolean(), eq(TaskType.ECS_COMMAND_TASK_NG));
    doReturn(false).when(cdFeatureFlagHelper).isEnabled(any(), any());
    ecsBlueGreenCreateServiceStep.executeEcsPrepareRollbackTask(
        ambiance, stepElementParameters, ecsStepPassThroughData, unitProgressData);
    final String accountId = AmbianceUtils.getAccountId(ambiance);
    EcsBlueGreenCreateServiceStepParameters ecsBlueGreenCreateServiceStepParameters =
        (EcsBlueGreenCreateServiceStepParameters) stepElementParameters.getSpec();
    EcsLoadBalancerConfig ecsLoadBalancerConfig =
        EcsLoadBalancerConfig.builder()
            .loadBalancer(ecsBlueGreenCreateServiceStepParameters.getLoadBalancer().getValue())
            .prodListenerArn(ecsBlueGreenCreateServiceStepParameters.getProdListener().getValue())
            .prodListenerRuleArn(ecsBlueGreenCreateServiceStepParameters.getProdListenerRuleArn().getValue())
            .stageListenerArn(ecsBlueGreenCreateServiceStepParameters.getStageListener().getValue())
            .stageListenerRuleArn(ecsBlueGreenCreateServiceStepParameters.getStageListenerRuleArn().getValue())
            .build();
    EcsBlueGreenPrepareRollbackRequest ecsBlueGreenPrepareRollbackRequest =
        EcsBlueGreenPrepareRollbackRequest.builder()
            .commandName(ECS_BLUE_GREEN_PREPARE_ROLLBACK_COMMAND_NAME)
            .accountId(accountId)
            .ecsCommandType(EcsCommandTypeNG.ECS_BLUE_GREEN_PREPARE_ROLLBACK_DATA)
            .ecsInfraConfig(ecsStepCommonHelper.getEcsInfraConfig(infrastructureOutcome, ambiance))
            .ecsServiceDefinitionManifestContent(ecsStepPassThroughData.getEcsServiceDefinitionManifestContent())
            .commandUnitsProgress(UnitProgressDataMapper.toCommandUnitsProgress(unitProgressData))
            .timeoutIntervalInMin(CDStepHelper.getTimeoutInMin(stepElementParameters))
            .ecsLoadBalancerConfig(ecsLoadBalancerConfig)
            .build();
    verify(ecsStepCommonHelper)
        .queueEcsTask(stepElementParameters, ecsBlueGreenPrepareRollbackRequest, ambiance, ecsStepPassThroughData,
            false, TaskType.ECS_COMMAND_TASK_NG);
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void getStepParametersClassTest() {
    Class<StepBaseParameters> stepElementParametersClass = ecsBlueGreenCreateServiceStep.getStepParametersClass();
    assertThat(stepElementParametersClass).isEqualTo(StepBaseParameters.class);
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void executeNextLinkWithSecurityContextTest() throws Exception {
    ResponseData responseData = EcsBlueGreenPrepareRollbackDataResponse.builder().build();
    EcsPrepareRollbackDataPassThroughData ecsStepPassThroughData =
        EcsPrepareRollbackDataPassThroughData.builder().build();
    StepInputPackage inputPackage = StepInputPackage.builder().build();

    ThrowingSupplier<ResponseData> responseSupplier = () -> responseData;
    ecsBlueGreenCreateServiceStep.executeNextLinkWithSecurityContext(
        ambiance, stepElementParameters, inputPackage, ecsStepPassThroughData, responseSupplier);

    verify(ecsStepCommonHelper)
        .executeNextLinkBlueGreen(
            ecsBlueGreenCreateServiceStep, ambiance, stepElementParameters, ecsStepPassThroughData, responseSupplier);
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void finalizeExecutionWithSecurityContextGitFetchFailureTest() throws Exception {
    ResponseData responseData = EcsBlueGreenPrepareRollbackDataResponse.builder().build();
    EcsGitFetchFailurePassThroughData ecsGitFetchFailurePassThroughData =
        EcsGitFetchFailurePassThroughData.builder()
            .unitProgressData(
                UnitProgressData.builder().unitProgresses(Arrays.asList(UnitProgress.newBuilder().build())).build())
            .errorMsg("error")
            .build();

    StepResponse stepResponse = ecsBlueGreenCreateServiceStep.finalizeExecutionWithSecurityContext(
        ambiance, stepElementParameters, ecsGitFetchFailurePassThroughData, () -> responseData);

    assertThat(stepResponse.getUnitProgressList())
        .isEqualTo(ecsGitFetchFailurePassThroughData.getUnitProgressData().getUnitProgresses());
    assertThat(stepResponse.getFailureInfo().getErrorMessage())
        .isEqualTo(ecsGitFetchFailurePassThroughData.getErrorMsg());
    assertThat(stepResponse.getStatus()).isEqualTo(Status.FAILED);
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void finalizeExecutionWithSecurityContextEcsStepExceptionPassThroughDataTest() throws Exception {
    EcsStepExceptionPassThroughData ecsStepExceptionPassThroughData =
        EcsStepExceptionPassThroughData.builder()
            .unitProgressData(
                UnitProgressData.builder().unitProgresses(Arrays.asList(UnitProgress.newBuilder().build())).build())
            .errorMessage("error")
            .build();
    ResponseData responseData = EcsGitFetchResponse.builder().build();

    StepResponse stepResponse = ecsBlueGreenCreateServiceStep.finalizeExecutionWithSecurityContext(
        ambiance, stepElementParameters, ecsStepExceptionPassThroughData, () -> responseData);

    FailureData failureData =
        FailureData.newBuilder()
            .addFailureTypes(FailureType.APPLICATION_FAILURE)
            .setLevel(io.harness.eraro.Level.ERROR.name())
            .setCode(GENERAL_ERROR.name())
            .setMessage(HarnessStringUtils.emptyIfNull(ecsStepExceptionPassThroughData.getErrorMessage()))
            .build();
    assertThat(stepResponse.getUnitProgressList())
        .isEqualTo(ecsStepExceptionPassThroughData.getUnitProgressData().getUnitProgresses());
    assertThat(stepResponse.getFailureInfo().getFailureTypes(0)).isEqualTo(failureData.getFailureTypes(0));
    assertThat(stepResponse.getFailureInfo().getErrorMessage()).isEqualTo(failureData.getMessage());
    assertThat(stepResponse.getFailureInfo().getFailureData(0).getCode()).isEqualTo(failureData.getCode());
    assertThat(stepResponse.getFailureInfo().getFailureData(0).getFailureTypes(0))
        .isEqualTo(failureData.getFailureTypes(0));
    assertThat(stepResponse.getFailureInfo().getFailureData(0).getMessage()).isEqualTo(failureData.getMessage());
    assertThat(stepResponse.getStatus()).isEqualTo(Status.FAILED);
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void finalizeExecutionWithSecurityContextCommandExecutionStatusFailureTest() throws Exception {
    EcsInfrastructureOutcome infrastructureOutcome = EcsInfrastructureOutcome.builder().build();
    EcsExecutionPassThroughData ecsExecutionPassThroughData =
        EcsExecutionPassThroughData.builder().infrastructure(infrastructureOutcome).build();
    UnitProgressData unitProgressData =
        UnitProgressData.builder().unitProgresses(Arrays.asList(UnitProgress.newBuilder().build())).build();
    EcsBlueGreenCreateServiceResponse responseData = EcsBlueGreenCreateServiceResponse.builder()
                                                         .unitProgressData(unitProgressData)
                                                         .commandExecutionStatus(CommandExecutionStatus.FAILURE)
                                                         .errorMessage("error")
                                                         .build();

    StepResponse stepResponse = ecsBlueGreenCreateServiceStep.finalizeExecutionWithSecurityContext(
        ambiance, stepElementParameters, ecsExecutionPassThroughData, () -> responseData);
    FailureData failureData = FailureData.newBuilder()
                                  .addFailureTypes(FailureType.APPLICATION_FAILURE)
                                  .setLevel(io.harness.eraro.Level.ERROR.name())
                                  .setCode(GENERAL_ERROR.name())
                                  .setMessage(HarnessStringUtils.emptyIfNull(responseData.getErrorMessage()))
                                  .build();
    assertThat(stepResponse.getUnitProgressList()).isEqualTo(responseData.getUnitProgressData().getUnitProgresses());
    assertThat(stepResponse.getFailureInfo().getErrorMessage()).isEqualTo(failureData.getMessage());
    assertThat(stepResponse.getStatus()).isEqualTo(Status.FAILED);
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void finalizeExecutionWithSecurityContextCommandExecutionStatusSuccessTest() throws Exception {
    EcsInfrastructureOutcome infrastructureOutcome =
        EcsInfrastructureOutcome.builder().infrastructureKey("infraKey").build();
    EcsExecutionPassThroughData ecsExecutionPassThroughData =
        EcsExecutionPassThroughData.builder().infrastructure(infrastructureOutcome).build();
    UnitProgressData unitProgressData =
        UnitProgressData.builder().unitProgresses(Arrays.asList(UnitProgress.newBuilder().build())).build();
    EcsBlueGreenCreateServiceResult ecsBlueGreenCreateServiceResult = EcsBlueGreenCreateServiceResult.builder().build();
    EcsBlueGreenCreateServiceResponse responseData =
        EcsBlueGreenCreateServiceResponse.builder()
            .unitProgressData(unitProgressData)
            .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
            .ecsBlueGreenCreateServiceResult(ecsBlueGreenCreateServiceResult)
            .build();

    List<ServerInstanceInfo> serverInstanceInfos = Arrays.asList(EcsServerInstanceInfo.builder().build());
    doReturn(serverInstanceInfos)
        .when(ecsStepCommonHelper)
        .getServerInstanceInfos(responseData, infrastructureOutcome.getInfrastructureKey());
    EcsBlueGreenCreateServiceDataOutcome ecsBlueGreenCreateServiceDataOutcome =
        EcsBlueGreenCreateServiceDataOutcome.builder().build();

    ecsBlueGreenCreateServiceStep.finalizeExecutionWithSecurityContext(
        ambiance, stepElementParameters, ecsExecutionPassThroughData, () -> responseData);

    verify(executionSweepingOutputService)
        .consume(ambiance, OutcomeExpressionConstants.ECS_BLUE_GREEN_CREATE_SERVICE_OUTCOME,
            ecsBlueGreenCreateServiceDataOutcome, StepOutcomeGroup.STEP.name());
    verify(instanceInfoService).saveServerInstancesIntoSweepingOutput(ambiance, serverInstanceInfos);
  }
}
