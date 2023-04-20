/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.k8s;

import static io.harness.logging.CommandExecutionStatus.FAILURE;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.rule.OwnerRule.PRATYUSH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.featureFlag.CDFeatureFlagHelper;
import io.harness.cdng.k8s.beans.GitFetchResponsePassThroughData;
import io.harness.cdng.k8s.beans.HelmValuesFetchResponsePassThroughData;
import io.harness.cdng.k8s.beans.K8sExecutionPassThroughData;
import io.harness.cdng.k8s.beans.StepExceptionPassThroughData;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.task.k8s.K8sDeployResponse;
import io.harness.delegate.task.k8s.K8sDryRunManifestRequest;
import io.harness.delegate.task.k8s.K8sDryRunManifestResponse;
import io.harness.delegate.task.k8s.K8sTaskType;
import io.harness.exception.GeneralException;
import io.harness.exception.HintException;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.sdk.core.plan.creation.yaml.StepOutcomeGroup;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;

import java.util.stream.Collectors;
import lombok.SneakyThrows;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.CDP)
public class K8sDryRunManifestStepTest extends AbstractK8sStepExecutorTestBase {
  @Mock ExecutionSweepingOutputService executionSweepingOutputService;
  @InjectMocks private K8sDryRunManifestStep k8sDryRunManifestStep;
  @Mock private CDFeatureFlagHelper cdFeatureFlagHelper;

  @Test
  @Owner(developers = PRATYUSH)
  @Category(UnitTests.class)
  public void testExecuteK8sTask() {
    when(cdFeatureFlagHelper.isEnabled(any(), any())).thenReturn(false);
    K8sDryRunManifestStepParameters stepParameters = new K8sDryRunManifestStepParameters();
    final StepElementParameters stepElementParameters =
        StepElementParameters.builder().spec(stepParameters).timeout(ParameterField.createValueField("30m")).build();

    K8sDryRunManifestRequest request =
        executeTaskForDryRunManifest(stepElementParameters, K8sDryRunManifestRequest.class);
    assertThat(request.getTimeoutIntervalInMin()).isEqualTo(30);
    assertThat(request.getK8sInfraDelegateConfig()).isEqualTo(infraDelegateConfig);
    assertThat(request.getManifestDelegateConfig()).isEqualTo(manifestDelegateConfig);
    assertThat(request.getTaskType()).isEqualTo(K8sTaskType.DRY_RUN_MANIFEST);
    assertThat(request.getAccountId()).isEqualTo(accountId);
    assertThat(request.isInCanaryWorkflow()).isFalse();

    ArgumentCaptor<String> releaseNameCaptor = ArgumentCaptor.forClass(String.class);
    verify(k8sStepHelper, times(1)).publishReleaseNameStepDetails(eq(ambiance), releaseNameCaptor.capture());
    assertThat(releaseNameCaptor.getValue()).isEqualTo(releaseName);
  }

  @SneakyThrows
  @Test
  @Owner(developers = PRATYUSH)
  @Category(UnitTests.class)
  public void testOutcomesInResponse() {
    K8sDryRunManifestStepParameters stepParameters = new K8sDryRunManifestStepParameters();
    final StepElementParameters stepElementParameters = StepElementParameters.builder().spec(stepParameters).build();

    K8sDeployResponse k8sDeployResponse =
        K8sDeployResponse.builder()
            .k8sNGTaskResponse(K8sDryRunManifestResponse.builder().manifestDryRunYaml("YamlFile").build())
            .commandUnitsProgress(UnitProgressData.builder().build())
            .commandExecutionStatus(SUCCESS)
            .build();

    StepResponse response = k8sDryRunManifestStep.finalizeExecutionWithSecurityContext(
        ambiance, stepElementParameters, K8sExecutionPassThroughData.builder().build(), () -> k8sDeployResponse);
    assertThat(response.getStatus()).isEqualTo(Status.SUCCEEDED);
    assertThat(response.getStepOutcomes()).hasSize(1);

    StepResponse.StepOutcome outcome = response.getStepOutcomes().stream().collect(Collectors.toList()).get(0);
    assertThat(outcome.getOutcome()).isInstanceOf(K8sDryRunManifestOutcome.class);
    assertThat(outcome.getName()).isEqualTo(K8sDryRunManifestOutcome.OUTPUT_NAME);
    assertThat(outcome.getGroup()).isNull();

    ArgumentCaptor<K8sDryRunManifestOutcome> argumentCaptor = ArgumentCaptor.forClass(K8sDryRunManifestOutcome.class);
    verify(executionSweepingOutputService, times(1))
        .consume(eq(ambiance), eq(K8sDryRunManifestOutcome.OUTPUT_NAME), argumentCaptor.capture(),
            eq(StepOutcomeGroup.STEP.name()));
    assertThat(argumentCaptor.getValue().getManifestDryRun()).isEqualTo("YamlFile");
  }

  @Test
  @Owner(developers = PRATYUSH)
  @Category(UnitTests.class)
  public void testFinalizeExecutionException() throws Exception {
    final StepElementParameters stepElementParameters = StepElementParameters.builder().build();
    final Exception thrownException = new GeneralException("Something went wrong");
    final K8sExecutionPassThroughData executionPassThroughData = K8sExecutionPassThroughData.builder().build();
    final StepResponse stepResponse = StepResponse.builder().status(Status.FAILED).build();

    doReturn(stepResponse).when(k8sStepHelper).handleTaskException(ambiance, executionPassThroughData, thrownException);

    StepResponse response = k8sDryRunManifestStep.finalizeExecutionWithSecurityContext(
        ambiance, stepElementParameters, executionPassThroughData, () -> { throw thrownException; });

    assertThat(response).isEqualTo(stepResponse);

    verify(k8sStepHelper, times(1)).handleTaskException(ambiance, executionPassThroughData, thrownException);
  }

  @Test
  @Owner(developers = PRATYUSH)
  @Category(UnitTests.class)
  public void testFinalizeExecutionWithSecurityContextHintException() {
    final StepElementParameters stepElementParameters = StepElementParameters.builder().build();
    final K8sExecutionPassThroughData executionPassThroughData = K8sExecutionPassThroughData.builder().build();
    K8sDeployResponse k8sDeployResponse =
        K8sDeployResponse.builder()
            .k8sNGTaskResponse(K8sDryRunManifestResponse.builder().manifestDryRunYaml("").build())
            .commandUnitsProgress(UnitProgressData.builder().build())
            .commandExecutionStatus(SUCCESS)
            .build();

    assertThatThrownBy(()
                           -> k8sDryRunManifestStep.finalizeExecutionWithSecurityContext(
                               ambiance, stepElementParameters, executionPassThroughData, () -> k8sDeployResponse))
        .isInstanceOf(HintException.class)
        .hasMessage("The dry run manifest yaml file might be too large to process.");
  }

  @Test
  @Owner(developers = PRATYUSH)
  @Category(UnitTests.class)
  public void testFinalizeExecutionWithSecurityContext() throws Exception {
    K8sDryRunManifestStepParameters stepParameters = new K8sDryRunManifestStepParameters();
    final StepElementParameters stepElementParameters = StepElementParameters.builder().spec(stepParameters).build();

    K8sDeployResponse k8sDeployResponse =
        K8sDeployResponse.builder()
            .k8sNGTaskResponse(K8sDryRunManifestResponse.builder().manifestDryRunYaml("YamlFile").build())
            .commandUnitsProgress(UnitProgressData.builder().build())
            .commandExecutionStatus(SUCCESS)
            .build();
    when(cdStepHelper.handleGitTaskFailure(any())).thenReturn(StepResponse.builder().status(Status.SUCCEEDED).build());
    StepResponse response = k8sDryRunManifestStep.finalizeExecutionWithSecurityContext(
        ambiance, stepElementParameters, GitFetchResponsePassThroughData.builder().build(), () -> k8sDeployResponse);
    assertThat(response.getStatus()).isEqualTo(Status.SUCCEEDED);

    when(k8sStepHelper.handleHelmValuesFetchFailure(any()))
        .thenReturn(StepResponse.builder().status(Status.SUCCEEDED).build());
    assertThat(k8sDryRunManifestStep
                   .finalizeExecutionWithSecurityContext(ambiance, stepElementParameters,
                       HelmValuesFetchResponsePassThroughData.builder().build(), () -> k8sDeployResponse)
                   .getStatus())
        .isEqualTo(Status.SUCCEEDED);

    when(cdStepHelper.handleStepExceptionFailure(any()))
        .thenReturn(StepResponse.builder().status(Status.SUCCEEDED).build());
    assertThat(k8sDryRunManifestStep
                   .finalizeExecutionWithSecurityContext(ambiance, stepElementParameters,
                       StepExceptionPassThroughData.builder().build(), () -> k8sDeployResponse)
                   .getStatus())
        .isEqualTo(Status.SUCCEEDED);

    K8sDeployResponse k8sDeployResponseFail =
        K8sDeployResponse.builder()
            .k8sNGTaskResponse(K8sDryRunManifestResponse.builder().manifestDryRunYaml("YamlFile").build())
            .commandUnitsProgress(UnitProgressData.builder().build())
            .commandExecutionStatus(FAILURE)
            .build();
    assertThat(k8sDryRunManifestStep
                   .finalizeExecutionWithSecurityContext(ambiance, stepElementParameters,
                       K8sExecutionPassThroughData.builder().build(), () -> k8sDeployResponseFail)
                   .getStatus())
        .isEqualTo(Status.FAILED);
  }

  @Override
  protected K8sStepExecutor getK8sStepExecutor() {
    return k8sDryRunManifestStep;
  }
}
