/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.k8s;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.cdng.k8s.K8sCanaryDeleteStep.K8S_CANARY_DELETE_ALREADY_DELETED;
import static io.harness.cdng.k8s.K8sCanaryDeleteStep.K8S_CANARY_STEP_MISSING;
import static io.harness.cdng.k8s.K8sCanaryDeleteStep.SKIP_K8S_CANARY_DELETE_STEP_EXECUTION;
import static io.harness.logging.CommandExecutionStatus.FAILURE;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.pms.contracts.execution.Status.FAILED;
import static io.harness.pms.contracts.execution.Status.SUCCEEDED;
import static io.harness.rule.OwnerRule.ABOSII;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.k8s.beans.K8sCanaryExecutionOutput;
import io.harness.cdng.k8s.beans.K8sExecutionPassThroughData;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.task.k8s.K8sCanaryDeleteRequest;
import io.harness.delegate.task.k8s.K8sDeployResponse;
import io.harness.delegate.task.k8s.K8sTaskType;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.executables.TaskChainResponse;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;

import lombok.SneakyThrows;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(CDP)
public class K8sCanaryDeleteStepTest extends CategoryTest {
  @Mock private K8sStepHelper k8sStepHelper;
  @Mock private CDStepHelper cdStepHelper;
  @InjectMocks private K8sCanaryDeleteStep canaryDeleteStep;

  @Mock private InfrastructureOutcome infrastructureOutcome;
  @Mock private ExecutionSweepingOutputService executionSweepingOutputService;

  private final Ambiance ambiance = Ambiance.newBuilder().build();
  private final StepInputPackage stepInputPackage = StepInputPackage.builder().build();
  private final String canaryStepFqn = "canaryStep";
  private final String canaryDeleteStepFqn = "canaryDeleteStep";
  private String releaseName = "test-release-name";

  private final Ambiance rollback = Ambiance.newBuilder()
                                        .addLevels(Level.newBuilder()
                                                       .setStepType(StepType.newBuilder()
                                                                        .setType("ROLLBACK_OPTIONAL_CHILD_CHAIN")
                                                                        .setStepCategory(StepCategory.STEP)
                                                                        .build())
                                                       .build())
                                        .build();

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);

    doReturn(infrastructureOutcome).when(cdStepHelper).getInfrastructureOutcome(any());
    doReturn(releaseName).when(cdStepHelper).getReleaseName(ambiance, infrastructureOutcome);
    doReturn(releaseName).when(cdStepHelper).getReleaseName(rollback, infrastructureOutcome);
    doReturn(OptionalSweepingOutput.builder().found(false).build())
        .when(executionSweepingOutputService)
        .resolveOptional(ambiance,
            RefObjectUtils.getSweepingOutputRefObject(
                canaryStepFqn + "." + OutcomeExpressionConstants.K8S_CANARY_OUTCOME));
    doReturn(OptionalSweepingOutput.builder().found(false).build())
        .when(executionSweepingOutputService)
        .resolveOptional(ambiance,
            RefObjectUtils.getSweepingOutputRefObject(canaryStepFqn + "." + K8sCanaryExecutionOutput.OUTPUT_NAME));

    doReturn(OptionalSweepingOutput.builder().found(false).build())
        .when(executionSweepingOutputService)
        .resolveOptional(rollback,
            RefObjectUtils.getSweepingOutputRefObject(canaryStepFqn + "." + K8sCanaryExecutionOutput.OUTPUT_NAME));
    doReturn(OptionalSweepingOutput.builder().found(false).build())
        .when(executionSweepingOutputService)
        .resolveOptional(rollback,
            RefObjectUtils.getSweepingOutputRefObject(
                canaryStepFqn + "." + OutcomeExpressionConstants.K8S_CANARY_OUTCOME));
    doReturn(OptionalSweepingOutput.builder().found(false).build())
        .when(executionSweepingOutputService)
        .resolveOptional(rollback,
            RefObjectUtils.getSweepingOutputRefObject(
                canaryDeleteStepFqn + "." + OutcomeExpressionConstants.K8S_CANARY_DELETE_OUTCOME));
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testObtainTask() {
    final String canaryWorkload = "default/Deployment/canary-deployment";
    final K8sCanaryDeleteStepParameters stepParameters = K8sCanaryDeleteStepParameters.infoBuilder().build();
    stepParameters.setCanaryStepFqn(canaryStepFqn);
    stepParameters.setCanaryDeleteStepFqn(canaryDeleteStepFqn);
    final StepElementParameters stepElementParameters =
        StepElementParameters.builder().spec(stepParameters).timeout(ParameterField.createValueField("10m")).build();

    final K8sCanaryOutcome k8sCanaryOutcome = K8sCanaryOutcome.builder()
                                                  .canaryWorkload(canaryWorkload)
                                                  .targetInstances(4)
                                                  .releaseNumber(2)
                                                  .releaseName("canary-deployment")
                                                  .build();
    final TaskChainResponse response =
        TaskChainResponse.builder().taskRequest(TaskRequest.newBuilder().build()).build();
    final K8sExecutionPassThroughData expectedPassThroughData =
        K8sExecutionPassThroughData.builder().infrastructure(infrastructureOutcome).build();

    doReturn(response)
        .when(k8sStepHelper)
        .queueK8sTask(eq(stepElementParameters), any(), eq(ambiance), eq(expectedPassThroughData));
    doReturn(OptionalSweepingOutput.builder().found(true).output(k8sCanaryOutcome).build())
        .when(executionSweepingOutputService)
        .resolveOptional(ambiance,
            RefObjectUtils.getSweepingOutputRefObject(
                canaryStepFqn + "." + OutcomeExpressionConstants.K8S_CANARY_OUTCOME));

    canaryDeleteStep.obtainTask(ambiance, stepElementParameters, stepInputPackage);
    ArgumentCaptor<K8sCanaryDeleteRequest> requestCaptor = ArgumentCaptor.forClass(K8sCanaryDeleteRequest.class);
    verify(k8sStepHelper)
        .queueK8sTask(eq(stepElementParameters), requestCaptor.capture(), eq(ambiance), eq(expectedPassThroughData));

    K8sCanaryDeleteRequest k8sDeleteRequest = requestCaptor.getValue();
    assertThat(k8sDeleteRequest.getCanaryWorkloads()).isEqualTo(canaryWorkload);
    assertThat(k8sDeleteRequest.getTaskType()).isEqualTo(K8sTaskType.CANARY_DELETE);
    assertThat(k8sDeleteRequest.getTimeoutIntervalInMin()).isEqualTo(10);

    ArgumentCaptor<String> releaseNameCaptor = ArgumentCaptor.forClass(String.class);
    verify(k8sStepHelper, times(1)).publishReleaseNameStepDetails(eq(ambiance), releaseNameCaptor.capture());
    assertThat(releaseNameCaptor.getValue()).isEqualTo(releaseName);
  }

  @SneakyThrows
  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testHandleTaskResult() {
    K8sCanaryDeleteStepParameters stepParameters = K8sCanaryDeleteStepParameters.infoBuilder().build();
    final StepElementParameters stepElementParameters =
        StepElementParameters.builder().spec(stepParameters).timeout(ParameterField.createValueField("10m")).build();

    K8sDeployResponse responseData = K8sDeployResponse.builder()
                                         .commandExecutionStatus(SUCCESS)
                                         .commandUnitsProgress(UnitProgressData.builder().build())
                                         .build();

    StepResponse stepResponse =
        canaryDeleteStep.handleTaskResultWithSecurityContext(ambiance, stepElementParameters, () -> responseData);
    assertThat(stepResponse.getStatus()).isEqualTo(SUCCEEDED);
  }

  @SneakyThrows
  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testHandleTaskResultFailed() {
    K8sCanaryDeleteStepParameters stepParameters = K8sCanaryDeleteStepParameters.infoBuilder().build();
    final StepElementParameters stepElementParameters =
        StepElementParameters.builder().spec(stepParameters).timeout(ParameterField.createValueField("10m")).build();

    K8sDeployResponse responseData = K8sDeployResponse.builder()
                                         .commandExecutionStatus(FAILURE)
                                         .errorMessage("task failed")
                                         .commandUnitsProgress(UnitProgressData.builder().build())
                                         .build();

    StepResponse stepResponse =
        canaryDeleteStep.handleTaskResultWithSecurityContext(ambiance, stepElementParameters, () -> responseData);
    assertThat(stepResponse.getStatus()).isEqualTo(FAILED);
    assertThat(stepResponse.getFailureInfo().getErrorMessage()).isEqualTo("task failed");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testSkipRollbackCanaryWorkloadNotDeployed() {
    final StepElementParameters stepElementParameters = StepElementParameters.builder()
                                                            .spec(K8sCanaryDeleteStepParameters.infoBuilder()
                                                                      .canaryStepFqn(canaryStepFqn)
                                                                      .canaryDeleteStepFqn(canaryDeleteStepFqn)
                                                                      .build())
                                                            .build();

    doReturn(OptionalSweepingOutput.builder().found(true).output(K8sCanaryExecutionOutput.builder().build()).build())
        .when(executionSweepingOutputService)
        .resolveOptional(rollback,
            RefObjectUtils.getSweepingOutputRefObject(canaryStepFqn + "." + K8sCanaryExecutionOutput.OUTPUT_NAME));
    doReturn(OptionalSweepingOutput.builder()
                 .found(true)
                 .output(K8sCanaryOutcome.builder().canaryWorkloadDeployed(false).build())
                 .build())
        .when(executionSweepingOutputService)
        .resolveOptional(rollback,
            RefObjectUtils.getSweepingOutputRefObject(
                canaryStepFqn + "." + OutcomeExpressionConstants.K8S_CANARY_OUTCOME));

    TaskRequest result = canaryDeleteStep.obtainTask(rollback, stepElementParameters, stepInputPackage);

    assertThat(result.getSkipTaskRequest()).isNotNull();
    assertThat(result.getSkipTaskRequest().getMessage()).isEqualTo(SKIP_K8S_CANARY_DELETE_STEP_EXECUTION);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testSkipRollbackCanaryWorkloadAlreadyDeleted() {
    final StepElementParameters stepElementParameters = StepElementParameters.builder()
                                                            .spec(K8sCanaryDeleteStepParameters.infoBuilder()
                                                                      .canaryStepFqn(canaryStepFqn)
                                                                      .canaryDeleteStepFqn(canaryDeleteStepFqn)
                                                                      .build())
                                                            .timeout(ParameterField.createValueField("10m"))
                                                            .build();

    doReturn(
        OptionalSweepingOutput.builder()
            .found(true)
            .output(K8sCanaryOutcome.builder().canaryWorkloadDeployed(true).canaryWorkload("test-workload").build())
            .build())
        .when(executionSweepingOutputService)
        .resolveOptional(rollback,
            RefObjectUtils.getSweepingOutputRefObject(
                canaryStepFqn + "." + OutcomeExpressionConstants.K8S_CANARY_OUTCOME));

    doReturn(OptionalSweepingOutput.builder().found(true).build())
        .when(executionSweepingOutputService)
        .resolveOptional(rollback,
            RefObjectUtils.getSweepingOutputRefObject(
                canaryDeleteStepFqn + "." + OutcomeExpressionConstants.K8S_CANARY_DELETE_OUTCOME));

    TaskRequest result = canaryDeleteStep.obtainTask(rollback, stepElementParameters, stepInputPackage);

    assertThat(result.getSkipTaskRequest()).isNotNull();
    assertThat(result.getSkipTaskRequest().getMessage()).isEqualTo(K8S_CANARY_DELETE_ALREADY_DELETED);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testObtainTaskNoCanaryWorkloadDeployed() {
    final StepElementParameters stepElementParameters = StepElementParameters.builder()
                                                            .spec(K8sCanaryDeleteStepParameters.infoBuilder()
                                                                      .canaryStepFqn(canaryStepFqn)
                                                                      .canaryDeleteStepFqn(canaryDeleteStepFqn)
                                                                      .build())
                                                            .build();

    assertThatThrownBy(() -> canaryDeleteStep.obtainTask(ambiance, stepElementParameters, stepInputPackage))
        .hasMessageContaining(K8S_CANARY_STEP_MISSING);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testObtainTaskNoCanaryWorkloadDeployedInRollback() {
    final StepElementParameters stepElementParameters = StepElementParameters.builder()
                                                            .spec(K8sCanaryDeleteStepParameters.infoBuilder()
                                                                      .canaryStepFqn(canaryStepFqn)
                                                                      .canaryDeleteStepFqn(canaryDeleteStepFqn)
                                                                      .build())
                                                            .build();
    doReturn(OptionalSweepingOutput.builder()
                 .found(true)
                 .output(K8sCanaryOutcome.builder().canaryWorkloadDeployed(false).build())
                 .build())
        .when(executionSweepingOutputService)
        .resolveOptional(rollback,
            RefObjectUtils.getSweepingOutputRefObject(
                canaryStepFqn + "." + OutcomeExpressionConstants.K8S_CANARY_OUTCOME));

    TaskRequest result = canaryDeleteStep.obtainTask(rollback, stepElementParameters, stepInputPackage);

    assertThat(result.getSkipTaskRequest()).isNotNull();
    assertThat(result.getSkipTaskRequest().getMessage()).isEqualTo(SKIP_K8S_CANARY_DELETE_STEP_EXECUTION);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testObtainTaskUsingK8sCanaryReleaseName() {
    final StepElementParameters stepElementParameters = StepElementParameters.builder()
                                                            .spec(K8sCanaryDeleteStepParameters.infoBuilder()
                                                                      .canaryStepFqn(canaryStepFqn)
                                                                      .canaryDeleteStepFqn(canaryDeleteStepFqn)
                                                                      .build())
                                                            .timeout(ParameterField.createValueField("10m"))
                                                            .build();
    final K8sExecutionPassThroughData expectedPassThroughData =
        K8sExecutionPassThroughData.builder().infrastructure(infrastructureOutcome).build();
    final TaskChainResponse response =
        TaskChainResponse.builder().taskRequest(TaskRequest.newBuilder().build()).build();

    doReturn(response)
        .when(k8sStepHelper)
        .queueK8sTask(eq(stepElementParameters), any(), eq(rollback), eq(expectedPassThroughData));

    doReturn(OptionalSweepingOutput.builder().found(true).output(K8sCanaryExecutionOutput.builder().build()).build())
        .when(executionSweepingOutputService)
        .resolveOptional(rollback,
            RefObjectUtils.getSweepingOutputRefObject(canaryStepFqn + "." + K8sCanaryExecutionOutput.OUTPUT_NAME));

    canaryDeleteStep.obtainTask(rollback, stepElementParameters, stepInputPackage);
    ArgumentCaptor<K8sCanaryDeleteRequest> requestCaptor = ArgumentCaptor.forClass(K8sCanaryDeleteRequest.class);
    verify(k8sStepHelper)
        .queueK8sTask(eq(stepElementParameters), requestCaptor.capture(), eq(rollback), eq(expectedPassThroughData));

    K8sCanaryDeleteRequest k8sDeleteRequest = requestCaptor.getValue();
    assertThat(k8sDeleteRequest.getReleaseName()).isEqualTo(releaseName);
    assertThat(k8sDeleteRequest.getCanaryWorkloads()).isNullOrEmpty();
  }
}
