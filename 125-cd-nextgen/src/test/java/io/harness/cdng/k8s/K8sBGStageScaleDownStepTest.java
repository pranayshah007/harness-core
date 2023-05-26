/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.k8s;

import static io.harness.cdng.k8s.K8sBGStageScaleDownStep.K8S_BG_STAGE_SCALE_COMMAND_NAME;
import static io.harness.logging.CommandExecutionStatus.FAILURE;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.rule.OwnerRule.PRATYUSH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.k8s.beans.K8sExecutionPassThroughData;
import io.harness.cdng.manifest.steps.outcome.ManifestsOutcome;
import io.harness.cdng.service.beans.ServiceOutcome;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.task.k8s.K8sBlueGreenStageScaleDownRequest;
import io.harness.delegate.task.k8s.K8sDeployRequest;
import io.harness.delegate.task.k8s.K8sDeployResponse;
import io.harness.delegate.task.k8s.K8sInfraDelegateConfig;
import io.harness.delegate.task.k8s.K8sTaskType;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outcome.OutcomeService;
import io.harness.pms.sdk.core.steps.executables.TaskChainResponse;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;

import software.wings.beans.TaskType;

import lombok.SneakyThrows;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.CDP)
public class K8sBGStageScaleDownStepTest extends CategoryTest {
  @InjectMocks private K8sBGStageScaleDownStep k8sBGStageScaleDownStep;
  @Mock private OutcomeService outcomeService;
  @Mock private CDStepHelper cdStepHelper;
  @Mock private K8sStepHelper k8sStepHelper;
  @Mock private InfrastructureOutcome infrastructureOutcome;
  @Mock private K8sInfraDelegateConfig infraDelegateConfig;
  @Mock ServiceOutcome serviceOutcome;
  private final Ambiance ambiance = Ambiance.newBuilder().build();
  private final StepInputPackage stepInputPackage = StepInputPackage.builder().build();

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    doReturn(infraDelegateConfig).when(cdStepHelper).getK8sInfraDelegateConfig(infrastructureOutcome, ambiance);
  }

  @Test
  @Owner(developers = PRATYUSH)
  @Category(UnitTests.class)
  public void testObtainTaskAfterRBAC() {
    final K8sBGStageScaleDownStepParameters stepParameters = K8sBGStageScaleDownStepParameters.infoBuilder().build();
    final K8sExecutionPassThroughData executionPassThroughData =
        K8sExecutionPassThroughData.builder().infrastructure(infrastructureOutcome).build();
    final StepElementParameters stepElementParameters =
        StepElementParameters.builder().spec(stepParameters).timeout(ParameterField.createValueField("10m")).build();

    final TaskRequest taskRequest = TaskRequest.newBuilder().build();
    doReturn(TaskChainResponse.builder().taskRequest(taskRequest).build())
        .when(k8sStepHelper)
        .queueK8sTask(eq(stepElementParameters), any(K8sDeployRequest.class), eq(ambiance),
            eq(executionPassThroughData), eq(TaskType.K8S_BLUE_GREEN_STAGE_SCALE_DOWN_TASK));

    doReturn(new ManifestsOutcome(serviceOutcome.getManifestResults()))
        .when(outcomeService)
        .resolve(ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.MANIFESTS));
    doReturn(infrastructureOutcome)
        .when(outcomeService)
        .resolve(ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.INFRASTRUCTURE_OUTCOME));
    doReturn("release-name").when(cdStepHelper).getReleaseName(ambiance, infrastructureOutcome);

    k8sBGStageScaleDownStep.obtainTask(ambiance, stepElementParameters, stepInputPackage);
    ArgumentCaptor<K8sBlueGreenStageScaleDownRequest> scaleRequestArgumentCaptor =
        ArgumentCaptor.forClass(K8sBlueGreenStageScaleDownRequest.class);

    verify(k8sStepHelper, times(1))
        .queueK8sTask(eq(stepElementParameters), scaleRequestArgumentCaptor.capture(), eq(ambiance),
            eq(executionPassThroughData), eq(TaskType.K8S_BLUE_GREEN_STAGE_SCALE_DOWN_TASK));
    K8sBlueGreenStageScaleDownRequest k8sBlueGreenStageScaleDownRequest = scaleRequestArgumentCaptor.getValue();
    assertThat(k8sBlueGreenStageScaleDownRequest).isNotNull();
    assertThat(k8sBlueGreenStageScaleDownRequest.getCommandName()).isEqualTo(K8S_BG_STAGE_SCALE_COMMAND_NAME);
    assertThat(k8sBlueGreenStageScaleDownRequest.getTaskType()).isEqualTo(K8sTaskType.BLUE_GREEN_STAGE_SCALE_DOWN);
    assertThat(k8sBlueGreenStageScaleDownRequest.getReleaseName()).isEqualTo("release-name");
    assertThat(k8sBlueGreenStageScaleDownRequest.getTimeoutIntervalInMin()).isEqualTo(10);
    assertThat(k8sBlueGreenStageScaleDownRequest.getK8sInfraDelegateConfig()).isEqualTo(infraDelegateConfig);

    // We need this null as K8sBlueGreenStageScaleDownRequest does not depend upon Manifests
    assertThat(k8sBlueGreenStageScaleDownRequest.getManifestDelegateConfig()).isNull();

    ArgumentCaptor<String> releaseNameCaptor = ArgumentCaptor.forClass(String.class);
    verify(k8sStepHelper, times(1)).publishReleaseNameStepDetails(eq(ambiance), releaseNameCaptor.capture());
    assertThat(releaseNameCaptor.getValue()).isEqualTo("release-name");
  }

  @SneakyThrows
  @Test
  @Owner(developers = PRATYUSH)
  @Category(UnitTests.class)
  public void testHandleTaskResultSucceeded() {
    K8sBGStageScaleDownStepParameters stepParameters = K8sBGStageScaleDownStepParameters.infoBuilder().build();
    final StepElementParameters stepElementParameters =
        StepElementParameters.builder().spec(stepParameters).timeout(ParameterField.createValueField("10m")).build();

    K8sDeployResponse responseData = K8sDeployResponse.builder()
                                         .commandExecutionStatus(SUCCESS)
                                         .commandUnitsProgress(UnitProgressData.builder().build())
                                         .build();

    StepResponse response = k8sBGStageScaleDownStep.handleTaskResultWithSecurityContext(
        ambiance, stepElementParameters, () -> responseData);
    assertThat(response.getStatus()).isEqualTo(Status.SUCCEEDED);
  }

  @Test
  @Owner(developers = PRATYUSH)
  @Category(UnitTests.class)
  public void testHandleTaskResultFailed() throws Exception {
    K8sBGStageScaleDownStepParameters stepParameters = K8sBGStageScaleDownStepParameters.infoBuilder().build();
    final StepElementParameters stepElementParameters =
        StepElementParameters.builder().spec(stepParameters).timeout(ParameterField.createValueField("10m")).build();

    K8sDeployResponse responseData = K8sDeployResponse.builder()
                                         .errorMessage("Execution failed.")
                                         .commandExecutionStatus(FAILURE)
                                         .commandUnitsProgress(UnitProgressData.builder().build())
                                         .build();

    StepResponse response = k8sBGStageScaleDownStep.handleTaskResultWithSecurityContext(
        ambiance, stepElementParameters, () -> responseData);
    assertThat(response.getStatus()).isEqualTo(Status.FAILED);
    assertThat(response.getFailureInfo().getErrorMessage()).isEqualTo("Execution failed.");
  }

  @Test
  @Owner(developers = PRATYUSH)
  @Category(UnitTests.class)
  public void testGetK8sBGStageScaleDownStepParameters() {
    assertThat(k8sBGStageScaleDownStep.getStepParametersClass()).isEqualTo(StepElementParameters.class);
  }
}
