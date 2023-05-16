/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.k8s;

import static io.harness.logging.CommandExecutionStatus.FAILURE;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.rule.OwnerRule.ABOSII;
import static io.harness.rule.OwnerRule.ACHYUTH;
import static io.harness.rule.OwnerRule.ANSHUL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.featureFlag.CDFeatureFlagHelper;
import io.harness.cdng.instance.info.InstanceInfoService;
import io.harness.cdng.instance.outcome.DeploymentInfoOutcome;
import io.harness.cdng.k8s.beans.GitFetchResponsePassThroughData;
import io.harness.cdng.k8s.beans.HelmValuesFetchResponsePassThroughData;
import io.harness.cdng.k8s.beans.K8sExecutionPassThroughData;
import io.harness.cdng.k8s.beans.K8sRollingReleaseOutput;
import io.harness.cdng.k8s.beans.StepExceptionPassThroughData;
import io.harness.cdng.manifest.yaml.K8sCommandFlagType;
import io.harness.cdng.manifest.yaml.K8sStepCommandFlag;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.task.k8s.K8sDeployRequest;
import io.harness.delegate.task.k8s.K8sDeployResponse;
import io.harness.delegate.task.k8s.K8sRollingDeployRequest;
import io.harness.delegate.task.k8s.K8sRollingDeployResponse;
import io.harness.delegate.task.k8s.K8sTaskType;
import io.harness.exception.GeneralException;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.plan.creation.yaml.StepOutcomeGroup;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.StepResponse.StepOutcome;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;

import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.SneakyThrows;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.CDP)
public class K8sRollingStepTest extends AbstractK8sStepExecutorTestBase {
  @Mock ExecutionSweepingOutputService executionSweepingOutputService;
  @Mock private InstanceInfoService instanceInfoService;
  @Mock private CDFeatureFlagHelper cdFeatureFlagHelper;

  @InjectMocks private K8sRollingStep k8sRollingStep;
  final String canaryStepFqn = "canaryStep";

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testExecuteK8sTask() {
    when(cdFeatureFlagHelper.isEnabled(any(), any())).thenReturn(true);
    K8sRollingStepParameters stepParameters = new K8sRollingStepParameters();
    Map<String, String> k8sCommandFlag = ImmutableMap.of("Apply", "--server-side");
    List<K8sStepCommandFlag> commandFlags =
        Collections.singletonList(K8sStepCommandFlag.builder()
                                      .commandType(K8sCommandFlagType.Apply)
                                      .flag(ParameterField.createValueField("--server-side"))
                                      .build());
    stepParameters.setCommandFlags(commandFlags);
    stepParameters.setSkipDryRun(ParameterField.createValueField(true));
    stepParameters.setCanaryStepFqn(canaryStepFqn);
    final StepElementParameters stepElementParameters =
        StepElementParameters.builder().spec(stepParameters).timeout(ParameterField.createValueField("30m")).build();

    when(executionSweepingOutputService.resolveOptional(ambiance,
             RefObjectUtils.getSweepingOutputRefObject(
                 canaryStepFqn + "." + OutcomeExpressionConstants.K8S_CANARY_OUTCOME)))
        .thenReturn(OptionalSweepingOutput.builder().found(false).build());

    K8sRollingDeployRequest request = executeTask(stepElementParameters, K8sRollingDeployRequest.class);
    assertThat(request.isSkipDryRun()).isTrue();
    assertThat(request.getTimeoutIntervalInMin()).isEqualTo(30);
    assertThat(request.getK8sInfraDelegateConfig()).isEqualTo(infraDelegateConfig);
    assertThat(request.getManifestDelegateConfig()).isEqualTo(manifestDelegateConfig);
    assertThat(request.getTaskType()).isEqualTo(K8sTaskType.DEPLOYMENT_ROLLING);
    assertThat(request.getAccountId()).isEqualTo(accountId);
    assertThat(request.isSkipResourceVersioning()).isTrue();
    assertThat(request.isInCanaryWorkflow()).isFalse();
    assertThat(request.getK8sCommandFlags()).isEqualTo(k8sCommandFlag);
    ArgumentCaptor<K8sRollingReleaseOutput> releaseOutputCaptor =
        ArgumentCaptor.forClass(K8sRollingReleaseOutput.class);
    verify(executionSweepingOutputService, times(1))
        .consume(eq(ambiance), eq(K8sRollingReleaseOutput.OUTPUT_NAME), releaseOutputCaptor.capture(),
            eq(StepOutcomeGroup.STEP.name()));
    assertThat(releaseOutputCaptor.getValue().getName()).isEqualTo(releaseName);

    ArgumentCaptor<String> releaseNameCaptor = ArgumentCaptor.forClass(String.class);
    verify(k8sStepHelper, times(1)).publishReleaseNameStepDetails(eq(ambiance), releaseNameCaptor.capture());
    assertThat(releaseNameCaptor.getValue()).isEqualTo(releaseName);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testExecuteK8sTaskInCanaryWorkflow() {
    K8sRollingStepParameters stepParameters = new K8sRollingStepParameters();
    stepParameters.setSkipDryRun(ParameterField.createValueField(true));
    stepParameters.setCanaryStepFqn(canaryStepFqn);
    final StepElementParameters stepElementParameters =
        StepElementParameters.builder().spec(stepParameters).timeout(ParameterField.createValueField("30m")).build();

    when(executionSweepingOutputService.resolveOptional(ambiance,
             RefObjectUtils.getSweepingOutputRefObject(
                 canaryStepFqn + "." + OutcomeExpressionConstants.K8S_CANARY_OUTCOME)))
        .thenReturn(OptionalSweepingOutput.builder().found(true).build());

    K8sRollingDeployRequest request = executeTask(stepElementParameters, K8sRollingDeployRequest.class);
    assertThat(request.isSkipDryRun()).isTrue();
    assertThat(request.getTimeoutIntervalInMin()).isEqualTo(30);
    assertThat(request.getK8sInfraDelegateConfig()).isEqualTo(infraDelegateConfig);
    assertThat(request.getManifestDelegateConfig()).isEqualTo(manifestDelegateConfig);
    assertThat(request.getTaskType()).isEqualTo(K8sTaskType.DEPLOYMENT_ROLLING);
    assertThat(request.getAccountId()).isEqualTo(accountId);
    assertThat(request.isSkipResourceVersioning()).isTrue();
    assertThat(request.isInCanaryWorkflow()).isTrue();

    ArgumentCaptor<K8sRollingReleaseOutput> releaseOutputCaptor =
        ArgumentCaptor.forClass(K8sRollingReleaseOutput.class);
    verify(executionSweepingOutputService, times(1))
        .consume(eq(ambiance), eq(K8sRollingReleaseOutput.OUTPUT_NAME), releaseOutputCaptor.capture(),
            eq(StepOutcomeGroup.STEP.name()));
    assertThat(releaseOutputCaptor.getValue().getName()).isEqualTo(releaseName);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testExecuteK8sTaskNullParameterFields() {
    K8sRollingStepParameters stepParameters = new K8sRollingStepParameters();
    stepParameters.setSkipDryRun(ParameterField.ofNull());
    stepParameters.setCanaryStepFqn(canaryStepFqn);
    final StepElementParameters stepElementParameters =
        StepElementParameters.builder().spec(stepParameters).timeout(ParameterField.ofNull()).build();

    when(executionSweepingOutputService.resolveOptional(ambiance,
             RefObjectUtils.getSweepingOutputRefObject(
                 canaryStepFqn + "." + OutcomeExpressionConstants.K8S_CANARY_OUTCOME)))
        .thenReturn(OptionalSweepingOutput.builder().found(false).build());

    K8sRollingDeployRequest request = executeTask(stepElementParameters, K8sRollingDeployRequest.class);
    assertThat(request.isSkipDryRun()).isFalse();
    assertThat(request.getTimeoutIntervalInMin()).isEqualTo(CDStepHelper.getTimeoutInMin(stepElementParameters));
    assertThat(request.isSkipResourceVersioning()).isTrue();
  }

  @SneakyThrows
  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testOutcomesInResponse() {
    K8sRollingStepParameters stepParameters = new K8sRollingStepParameters();
    final StepElementParameters stepElementParameters = StepElementParameters.builder().spec(stepParameters).build();

    K8sDeployResponse k8sDeployResponse =
        K8sDeployResponse.builder()
            .k8sNGTaskResponse(
                K8sRollingDeployResponse.builder().k8sPodList(Collections.emptyList()).releaseNumber(1).build())
            .commandUnitsProgress(UnitProgressData.builder().build())
            .commandExecutionStatus(SUCCESS)
            .build();
    when(cdStepHelper.getReleaseName(any(), any())).thenReturn("releaseName");
    StepResponse.StepOutcome stepOutcome = StepResponse.StepOutcome.builder()
                                               .name(OutcomeExpressionConstants.DEPLOYMENT_INFO_OUTCOME)
                                               .outcome(DeploymentInfoOutcome.builder().build())
                                               .build();
    doReturn(stepOutcome).when(instanceInfoService).saveServerInstancesIntoSweepingOutput(any(), any());

    StepResponse response = k8sRollingStep.finalizeExecutionWithSecurityContext(
        ambiance, stepElementParameters, K8sExecutionPassThroughData.builder().build(), () -> k8sDeployResponse);
    assertThat(response.getStatus()).isEqualTo(Status.SUCCEEDED);
    assertThat(response.getStepOutcomes()).hasSize(2);

    StepOutcome outcome = response.getStepOutcomes().stream().collect(Collectors.toList()).get(0);
    assertThat(outcome.getOutcome()).isInstanceOf(K8sRollingOutcome.class);
    assertThat(outcome.getName()).isEqualTo(OutcomeExpressionConstants.OUTPUT);
    assertThat(outcome.getGroup()).isNull();

    StepOutcome deploymentInfoOutcome = new ArrayList<>(response.getStepOutcomes()).get(1);
    assertThat(deploymentInfoOutcome.getOutcome()).isInstanceOf(DeploymentInfoOutcome.class);
    assertThat(deploymentInfoOutcome.getName()).isEqualTo(OutcomeExpressionConstants.DEPLOYMENT_INFO_OUTCOME);

    ArgumentCaptor<K8sRollingOutcome> argumentCaptor = ArgumentCaptor.forClass(K8sRollingOutcome.class);
    verify(executionSweepingOutputService, times(1))
        .consume(eq(ambiance), eq(OutcomeExpressionConstants.K8S_ROLL_OUT), argumentCaptor.capture(),
            eq(StepOutcomeGroup.STEP.name()));
    assertThat(argumentCaptor.getValue().getReleaseName()).isEqualTo("releaseName");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testFinalizeExecutionException() throws Exception {
    final StepElementParameters stepElementParameters = StepElementParameters.builder().build();
    final Exception thrownException = new GeneralException("Something went wrong");
    final K8sExecutionPassThroughData executionPassThroughData = K8sExecutionPassThroughData.builder().build();
    final StepResponse stepResponse = StepResponse.builder().status(Status.FAILED).build();

    doReturn(stepResponse).when(k8sStepHelper).handleTaskException(ambiance, executionPassThroughData, thrownException);

    StepResponse response = k8sRollingStep.finalizeExecutionWithSecurityContext(
        ambiance, stepElementParameters, executionPassThroughData, () -> { throw thrownException; });

    assertThat(response).isEqualTo(stepResponse);

    verify(k8sStepHelper, times(1)).handleTaskException(ambiance, executionPassThroughData, thrownException);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testDontSaveReleaseOutputIfQueueTaskFails() {
    final K8sRollingStepParameters stepParameters = new K8sRollingStepParameters();
    stepParameters.setCanaryStepFqn(canaryStepFqn);
    final StepElementParameters stepElementParameters =
        StepElementParameters.builder().spec(stepParameters).timeout(ParameterField.createValueField("30m")).build();
    final RuntimeException thrownException = new RuntimeException("test");
    stepParameters.setSkipDryRun(ParameterField.createValueField(true));

    when(executionSweepingOutputService.resolveOptional(ambiance,
             RefObjectUtils.getSweepingOutputRefObject(
                 canaryStepFqn + "." + OutcomeExpressionConstants.K8S_CANARY_OUTCOME)))
        .thenReturn(OptionalSweepingOutput.builder().found(false).build());

    doThrow(thrownException)
        .when(k8sStepHelper)
        .queueK8sTask(eq(stepElementParameters), any(K8sDeployRequest.class), eq(ambiance),
            any(K8sExecutionPassThroughData.class));

    assertThatThrownBy(() -> executeTask(stepElementParameters, K8sRollingDeployRequest.class))
        .isSameAs(thrownException);

    verify(executionSweepingOutputService, never())
        .consume(eq(ambiance), eq(OutcomeExpressionConstants.K8S_ROLL_OUT), any(K8sRollingReleaseOutput.class),
            eq(StepOutcomeGroup.STAGE.name()));
  }

  @Test
  @Owner(developers = ACHYUTH)
  @Category(UnitTests.class)
  public void testFinalizeExecutionWithSecurityContext() throws Exception {
    K8sRollingStepParameters stepParameters = new K8sRollingStepParameters();
    final StepElementParameters stepElementParameters = StepElementParameters.builder().spec(stepParameters).build();

    K8sDeployResponse k8sDeployResponse =
        K8sDeployResponse.builder()
            .k8sNGTaskResponse(
                K8sRollingDeployResponse.builder().k8sPodList(Collections.emptyList()).releaseNumber(1).build())
            .commandUnitsProgress(UnitProgressData.builder().build())
            .commandExecutionStatus(SUCCESS)
            .build();
    when(cdStepHelper.getReleaseName(any(), any())).thenReturn("releaseName");
    StepResponse.StepOutcome stepOutcome = StepResponse.StepOutcome.builder()
                                               .name(OutcomeExpressionConstants.DEPLOYMENT_INFO_OUTCOME)
                                               .outcome(DeploymentInfoOutcome.builder().build())
                                               .build();
    doReturn(stepOutcome).when(instanceInfoService).saveServerInstancesIntoSweepingOutput(any(), any());
    when(cdStepHelper.handleGitTaskFailure(any())).thenReturn(StepResponse.builder().status(Status.SUCCEEDED).build());
    StepResponse response = k8sRollingStep.finalizeExecutionWithSecurityContext(
        ambiance, stepElementParameters, GitFetchResponsePassThroughData.builder().build(), () -> k8sDeployResponse);
    assertThat(response.getStatus()).isEqualTo(Status.SUCCEEDED);

    when(k8sStepHelper.handleHelmValuesFetchFailure(any()))
        .thenReturn(StepResponse.builder().status(Status.SUCCEEDED).build());
    assertThat(k8sRollingStep
                   .finalizeExecutionWithSecurityContext(ambiance, stepElementParameters,
                       HelmValuesFetchResponsePassThroughData.builder().build(), () -> k8sDeployResponse)
                   .getStatus())
        .isEqualTo(Status.SUCCEEDED);

    when(cdStepHelper.handleStepExceptionFailure(any()))
        .thenReturn(StepResponse.builder().status(Status.SUCCEEDED).build());
    assertThat(k8sRollingStep
                   .finalizeExecutionWithSecurityContext(ambiance, stepElementParameters,
                       StepExceptionPassThroughData.builder().build(), () -> k8sDeployResponse)
                   .getStatus())
        .isEqualTo(Status.SUCCEEDED);

    K8sDeployResponse k8sDeployResponseFail =
        K8sDeployResponse.builder()
            .k8sNGTaskResponse(
                K8sRollingDeployResponse.builder().k8sPodList(Collections.emptyList()).releaseNumber(1).build())
            .commandUnitsProgress(UnitProgressData.builder().build())
            .commandExecutionStatus(FAILURE)
            .build();
    assertThat(k8sRollingStep
                   .finalizeExecutionWithSecurityContext(ambiance, stepElementParameters,
                       K8sExecutionPassThroughData.builder().build(), () -> k8sDeployResponseFail)
                   .getStatus())
        .isEqualTo(Status.FAILED);
  }

  @Override
  protected K8sStepExecutor getK8sStepExecutor() {
    return k8sRollingStep;
  }
}
