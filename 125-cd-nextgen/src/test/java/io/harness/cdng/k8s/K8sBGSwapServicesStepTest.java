/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.k8s;

import static io.harness.cdng.k8s.K8sBGSwapServicesStep.BG_STEP_MISSING_ERROR;
import static io.harness.cdng.k8s.K8sBGSwapServicesStep.SKIP_BG_SWAP_SERVICES_STEP_EXECUTION;
import static io.harness.cdng.k8s.K8sBGSwapServicesStep.SKIP_BG_SWAP_WHEN_STAGE_DEPLOYMENT_SKIPPED;
import static io.harness.cdng.k8s.K8sStepHelper.MISSING_INFRASTRUCTURE_ERROR;
import static io.harness.logging.CommandExecutionStatus.FAILURE;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.pms.contracts.execution.Status.FAILED;
import static io.harness.pms.contracts.execution.Status.SUCCEEDED;
import static io.harness.rule.OwnerRule.ABHINAV2;
import static io.harness.rule.OwnerRule.ABOSII;
import static io.harness.rule.OwnerRule.ANSHUL;
import static io.harness.rule.OwnerRule.TARUN_UBA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.k8s.beans.K8sExecutionPassThroughData;
import io.harness.cdng.pipeline.steps.RollbackOptionalChildChainStep;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.task.k8s.K8sDeployRequest;
import io.harness.delegate.task.k8s.K8sDeployResponse;
import io.harness.delegate.task.k8s.K8sInfraDelegateConfig;
import io.harness.delegate.task.k8s.K8sSwapServiceSelectorsRequest;
import io.harness.exception.InvalidRequestException;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.data.OptionalOutcome;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outcome.OutcomeService;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.executables.TaskChainResponse;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.StepResponse.StepOutcome;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;

import java.util.stream.Collectors;
import lombok.SneakyThrows;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.CDP)
public class K8sBGSwapServicesStepTest extends CategoryTest {
  @Mock private K8sStepHelper k8sStepHelper;
  @Mock private CDStepHelper cdStepHelper;
  @Mock private OutcomeService outcomeService;
  @Mock private InfrastructureOutcome infrastructureOutcome;
  @Mock private K8sInfraDelegateConfig infraDelegateConfig;
  @Mock private ExecutionSweepingOutputService executionSweepingOutputService;

  @InjectMocks private K8sBGSwapServicesStep k8sBGSwapServicesStep;

  private final Ambiance ambiance = Ambiance.newBuilder().build();
  private final StepInputPackage stepInputPackage = StepInputPackage.builder().build();
  final String primaryService = "k8s-svc";
  final String stageService = "k8s-svc-stage";
  final K8sBlueGreenOutcome blueGreenOutcome =
      K8sBlueGreenOutcome.builder().primaryServiceName(primaryService).stageServiceName(stageService).build();
  final K8sBlueGreenOutcome blueGreenOutcomeWhenSkip =
      K8sBlueGreenOutcome.builder().stageDeploymentSkipped(Boolean.TRUE).build();
  final TaskRequest createdTaskRequest = TaskRequest.newBuilder().build();
  final String bgStepFqn = "bgStep";
  final String bgSwapServicesStepFqn = "bgSwapServicesStep";
  final K8sBGSwapServicesStepParameters stepParameters = K8sBGSwapServicesStepParameters.infoBuilder()
                                                             .blueGreenStepFqn(bgStepFqn)
                                                             .blueGreenSwapServicesFqn(bgSwapServicesStepFqn)
                                                             .build();
  final StepElementParameters stepElementParameters =
      StepElementParameters.builder().spec(stepParameters).timeout(ParameterField.createValueField("10m")).build();
  private String releaseName = "test-release-name";

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    doReturn(infraDelegateConfig).when(cdStepHelper).getK8sInfraDelegateConfig(infrastructureOutcome, ambiance);
    doReturn(releaseName).when(cdStepHelper).getReleaseName(ambiance, infrastructureOutcome);
    doReturn(true).when(k8sStepHelper).isDeclarativeRollbackEnabled(ambiance);
  }

  private void setupPreConditions(Ambiance ambiance) {
    doReturn(TaskChainResponse.builder().taskRequest(createdTaskRequest).build())
        .when(k8sStepHelper)
        .queueK8sTask(eq(stepElementParameters), any(K8sDeployRequest.class), eq(ambiance),
            eq(K8sExecutionPassThroughData.builder().infrastructure(infrastructureOutcome).build()));
    doReturn(OptionalSweepingOutput.builder().found(true).output(blueGreenOutcome).build())
        .when(executionSweepingOutputService)
        .resolveOptional(ambiance,
            RefObjectUtils.getSweepingOutputRefObject(
                bgStepFqn + "." + OutcomeExpressionConstants.K8S_BLUE_GREEN_OUTCOME));
    doReturn(infrastructureOutcome).when(cdStepHelper).getInfrastructureOutcome(ambiance);
  }

  private void setupPreConditionsForSkipStageDeployment(Ambiance ambiance) {
    doReturn(TaskChainResponse.builder().taskRequest(createdTaskRequest).build())
        .when(k8sStepHelper)
        .queueK8sTask(eq(stepElementParameters), any(K8sDeployRequest.class), eq(ambiance),
            eq(K8sExecutionPassThroughData.builder().infrastructure(infrastructureOutcome).build()));
    doReturn(OptionalSweepingOutput.builder().found(true).output(blueGreenOutcomeWhenSkip).build())
        .when(executionSweepingOutputService)
        .resolveOptional(ambiance,
            RefObjectUtils.getSweepingOutputRefObject(
                bgStepFqn + "." + OutcomeExpressionConstants.K8S_BLUE_GREEN_OUTCOME));
    doReturn(infrastructureOutcome).when(cdStepHelper).getInfrastructureOutcome(ambiance);
  }
  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testObtainTask() {
    setupPreConditions(ambiance);

    final OptionalOutcome optionalOutcome = OptionalOutcome.builder().found(false).build();
    doReturn(optionalOutcome)
        .when(outcomeService)
        .resolveOptional(ambiance,
            RefObjectUtils.getOutcomeRefObject(
                bgSwapServicesStepFqn + "." + OutcomeExpressionConstants.K8S_BG_SWAP_SERVICES_OUTCOME));

    k8sBGSwapServicesStep.obtainTask(ambiance, stepElementParameters, stepInputPackage);
    ArgumentCaptor<K8sSwapServiceSelectorsRequest> requestArgumentCaptor =
        ArgumentCaptor.forClass(K8sSwapServiceSelectorsRequest.class);

    verify(k8sStepHelper, times(1))
        .queueK8sTask(eq(stepElementParameters), requestArgumentCaptor.capture(), eq(ambiance),
            eq(K8sExecutionPassThroughData.builder().infrastructure(infrastructureOutcome).build()));
    K8sSwapServiceSelectorsRequest request = requestArgumentCaptor.getValue();
    assertThat(request).isNotNull();
    assertThat(request.getService1()).isEqualTo(primaryService);
    assertThat(request.getService2()).isEqualTo(stageService);
    assertThat(request.getK8sInfraDelegateConfig()).isEqualTo(infraDelegateConfig);
    assertThat(request.getCommandName()).isEqualTo(K8sBGSwapServicesStep.K8S_BG_SWAP_SERVICES_COMMAND_NAME);
    // We need this null as K8sBGSwapServicesStep does not depend upon Manifests
    assertThat(request.getManifestDelegateConfig()).isNull();

    ArgumentCaptor<String> releaseNameCaptor = ArgumentCaptor.forClass(String.class);
    verify(k8sStepHelper, times(1)).publishReleaseNameStepDetails(eq(ambiance), releaseNameCaptor.capture());
    assertThat(releaseNameCaptor.getValue()).isEqualTo(releaseName);
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testObtainTaskInRollbackWhenSwapServicesExecutedInForwardPhase() {
    Ambiance ambiance =
        Ambiance.newBuilder()
            .addLevels(
                Level.newBuilder()
                    .setStepType(
                        StepType.newBuilder().setType(RollbackOptionalChildChainStep.STEP_TYPE.getType()).build())
                    .build())
            .build();
    final OptionalOutcome optionalOutcome = OptionalOutcome.builder().found(true).build();
    doReturn(optionalOutcome)
        .when(outcomeService)
        .resolveOptional(ambiance,
            RefObjectUtils.getOutcomeRefObject(
                bgSwapServicesStepFqn + "." + OutcomeExpressionConstants.K8S_BG_SWAP_SERVICES_OUTCOME));

    setupPreConditions(ambiance);

    k8sBGSwapServicesStep.obtainTask(ambiance, stepElementParameters, stepInputPackage);
    ArgumentCaptor<K8sSwapServiceSelectorsRequest> requestArgumentCaptor =
        ArgumentCaptor.forClass(K8sSwapServiceSelectorsRequest.class);

    verify(k8sStepHelper, times(1))
        .queueK8sTask(eq(stepElementParameters), requestArgumentCaptor.capture(), eq(ambiance),
            eq(K8sExecutionPassThroughData.builder().infrastructure(infrastructureOutcome).build()));
    K8sSwapServiceSelectorsRequest request = requestArgumentCaptor.getValue();
    assertThat(request).isNotNull();
    assertThat(request.getService1()).isEqualTo(primaryService);
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testObtainTaskInRollbackWhenSwapServicesDidNotExecuteInForwardPhase() {
    Ambiance ambiance = getAmbianceForRollback();
    OptionalOutcome optionalOutcome = OptionalOutcome.builder().found(false).build();
    doReturn(optionalOutcome)
        .when(outcomeService)
        .resolveOptional(ambiance,
            RefObjectUtils.getOutcomeRefObject(
                bgSwapServicesStepFqn + "." + OutcomeExpressionConstants.K8S_BG_SWAP_SERVICES_OUTCOME));

    TaskRequest taskRequest = k8sBGSwapServicesStep.obtainTask(ambiance, stepElementParameters, stepInputPackage);
    assertThat(taskRequest).isNotNull();
    assertThat(taskRequest.getSkipTaskRequest()).isNotNull();
    assertThat(taskRequest.getSkipTaskRequest().getMessage()).isEqualTo(SKIP_BG_SWAP_SERVICES_STEP_EXECUTION);
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testObtainTaskInRollbackWhenSwapServicesIsMissingFromPipeline() {
    Ambiance ambiance = getAmbianceForRollback();
    K8sBGSwapServicesStepParameters stepParams =
        K8sBGSwapServicesStepParameters.infoBuilder().blueGreenStepFqn(bgStepFqn).build();
    StepElementParameters stepElementParams =
        StepElementParameters.builder().spec(stepParams).timeout(ParameterField.createValueField("10m")).build();
    OptionalOutcome optionalOutcome = OptionalOutcome.builder().found(false).build();
    doReturn(optionalOutcome)
        .when(outcomeService)
        .resolveOptional(ambiance,
            RefObjectUtils.getOutcomeRefObject("null." + OutcomeExpressionConstants.K8S_BG_SWAP_SERVICES_OUTCOME));

    TaskRequest taskRequest = k8sBGSwapServicesStep.obtainTask(ambiance, stepElementParams, stepInputPackage);
    assertThat(taskRequest).isNotNull();
    assertThat(taskRequest.getSkipTaskRequest()).isNotNull();
    assertThat(taskRequest.getSkipTaskRequest().getMessage()).isEqualTo(SKIP_BG_SWAP_SERVICES_STEP_EXECUTION);
  }

  @Test
  @Owner(developers = TARUN_UBA)
  @Category(UnitTests.class)
  public void testObtainTaskInSwapServicesWhenNoStageDeployment() {
    setupPreConditionsForSkipStageDeployment(ambiance);
    K8sBGSwapServicesStepParameters stepParams =
        K8sBGSwapServicesStepParameters.infoBuilder().blueGreenStepFqn(bgStepFqn).build();
    StepElementParameters stepElementParams =
        StepElementParameters.builder().spec(stepParams).timeout(ParameterField.createValueField("10m")).build();
    OptionalOutcome optionalOutcome = OptionalOutcome.builder().found(false).build();
    doReturn(optionalOutcome)
        .when(outcomeService)
        .resolveOptional(ambiance,
            RefObjectUtils.getOutcomeRefObject("null." + OutcomeExpressionConstants.K8S_BG_SWAP_SERVICES_OUTCOME));

    TaskRequest taskRequest = k8sBGSwapServicesStep.obtainTask(ambiance, stepElementParams, stepInputPackage);
    assertThat(taskRequest).isNotNull();
    assertThat(taskRequest.getSkipTaskRequest()).isNotNull();
    assertThat(taskRequest.getSkipTaskRequest().getMessage()).isEqualTo(SKIP_BG_SWAP_WHEN_STAGE_DEPLOYMENT_SKIPPED);
  }

  @SneakyThrows
  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testHandleTaskResult() {
    K8sDeployResponse responseData = K8sDeployResponse.builder()
                                         .commandUnitsProgress(UnitProgressData.builder().build())
                                         .commandExecutionStatus(SUCCESS)
                                         .build();

    StepResponse response =
        k8sBGSwapServicesStep.handleTaskResultWithSecurityContext(ambiance, stepElementParameters, () -> responseData);
    assertThat(response.getStatus()).isEqualTo(SUCCEEDED);
    StepOutcome outcome = response.getStepOutcomes().stream().collect(Collectors.toList()).get(0);
    assertThat(outcome.getOutcome()).isInstanceOf(K8sBGSwapServicesOutcome.class);
    assertThat(outcome.getName()).isEqualTo(OutcomeExpressionConstants.K8S_BG_SWAP_SERVICES_OUTCOME);
  }

  @SneakyThrows
  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testHandleTaskResultInRollback() {
    Ambiance ambiance = getAmbianceForRollback();

    K8sDeployResponse responseData = K8sDeployResponse.builder()
                                         .commandUnitsProgress(UnitProgressData.builder().build())
                                         .commandExecutionStatus(SUCCESS)
                                         .build();

    StepResponse response =
        k8sBGSwapServicesStep.handleTaskResultWithSecurityContext(ambiance, stepElementParameters, () -> responseData);
    assertThat(response.getStatus()).isEqualTo(SUCCEEDED);
    assertThat(response.getStepOutcomes()).isEmpty();
  }

  @SneakyThrows
  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testHandleTaskResultFailed() {
    K8sDeployResponse responseData = K8sDeployResponse.builder()
                                         .commandExecutionStatus(FAILURE)
                                         .commandUnitsProgress(UnitProgressData.builder().build())
                                         .build();

    StepResponse response =
        k8sBGSwapServicesStep.handleTaskResultWithSecurityContext(ambiance, stepElementParameters, () -> responseData);
    assertThat(response.getStatus()).isEqualTo(FAILED);
    assertThat(response.getStepOutcomes()).isEmpty();
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testObtainTaskWithInfraStrucutreOutcomeNotPresent() {
    setupPreConditions(ambiance);
    final OptionalOutcome optionalOutcome = OptionalOutcome.builder().found(false).build();
    doReturn(optionalOutcome)
        .when(outcomeService)
        .resolveOptional(ambiance,
            RefObjectUtils.getOutcomeRefObject(
                bgSwapServicesStepFqn + "." + OutcomeExpressionConstants.K8S_BG_SWAP_SERVICES_OUTCOME));

    doThrow(new InvalidRequestException(MISSING_INFRASTRUCTURE_ERROR))
        .when(cdStepHelper)
        .getInfrastructureOutcome(ambiance);
    assertThatThrownBy(() -> k8sBGSwapServicesStep.obtainTask(ambiance, stepElementParameters, stepInputPackage))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining(MISSING_INFRASTRUCTURE_ERROR);

    doReturn(OptionalSweepingOutput.builder().found(false).build())
        .when(executionSweepingOutputService)
        .resolveOptional(ambiance,
            RefObjectUtils.getSweepingOutputRefObject(
                bgStepFqn + "." + OutcomeExpressionConstants.K8S_BLUE_GREEN_OUTCOME));
    assertThatThrownBy(() -> k8sBGSwapServicesStep.obtainTask(ambiance, stepElementParameters, stepInputPackage))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining(BG_STEP_MISSING_ERROR);
  }

  private Ambiance getAmbianceForRollback() {
    return Ambiance.newBuilder()
        .addLevels(Level.newBuilder()
                       .setStepType(StepType.newBuilder()
                                        .setType(RollbackOptionalChildChainStep.STEP_TYPE.getType())
                                        .setStepCategory(StepCategory.STEP)
                                        .build())
                       .build())
        .build();
  }
}
