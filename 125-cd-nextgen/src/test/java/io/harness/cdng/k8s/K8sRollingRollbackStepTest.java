/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.k8s;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.ABOSII;
import static io.harness.rule.OwnerRule.ACHYUTH;
import static io.harness.rule.OwnerRule.ANSHUL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.account.services.AccountService;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.featureFlag.CDFeatureFlagHelper;
import io.harness.cdng.instance.info.InstanceInfoService;
import io.harness.cdng.k8s.beans.K8sExecutionPassThroughData;
import io.harness.cdng.k8s.beans.K8sRollingReleaseOutput;
import io.harness.cdng.manifest.steps.outcome.ManifestsOutcome;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.task.k8s.K8sDeployRequest;
import io.harness.delegate.task.k8s.K8sDeployResponse;
import io.harness.delegate.task.k8s.K8sRollingDeployRollbackResponse;
import io.harness.delegate.task.k8s.K8sRollingRollbackDeployRequest;
import io.harness.k8s.model.K8sPod;
import io.harness.logging.CommandExecutionStatus;
import io.harness.ng.core.dto.AccountDTO;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.plan.creation.yaml.StepOutcomeGroup;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outcome.OutcomeService;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.executables.TaskChainResponse;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.steps.StepHelper;
import io.harness.telemetry.TelemetryReporter;

import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(CDP)
public class K8sRollingRollbackStepTest extends CategoryTest {
  private final Level level =
      Level.newBuilder().setIdentifier("TestRollbackStep").setGroup(StepOutcomeGroup.STEP.name()).build();
  private final Ambiance ambiance = Ambiance.newBuilder().addLevels(level).build();
  private final StepInputPackage stepInputPackage = StepInputPackage.builder().build();

  @Mock private CDFeatureFlagHelper cdFeatureFlagHelper;
  @Mock private OutcomeService outcomeService;
  @Mock private ExecutionSweepingOutputService executionSweepingOutputService;
  @Mock private K8sStepHelper k8sStepHelper;
  @Mock private CDStepHelper cdStepHelper;
  @Mock private InstanceInfoService instanceInfoService;
  @InjectMocks private K8sRollingRollbackStep k8sRollingRollbackStep;
  @Mock private AccountService accountService;
  @Mock private StepHelper stepHelper;
  @Mock private TelemetryReporter telemetryReporter;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    ManifestsOutcome mockOutcomes = mock(ManifestsOutcome.class);
    doReturn(mockOutcomes).when(k8sStepHelper).resolveManifestsOutcome(any());
    doReturn(null).when(k8sStepHelper).getK8sSupportedManifestOutcome(any());
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testSkippingOfRollbackStep() {
    String rollingFqn = "pipeline.stages.deploy.spec.execution.steps.rolloutDeployment";
    K8sRollingRollbackStepParameters stepParameters =
        K8sRollingRollbackStepParameters.infoBuilder().rollingStepFqn(rollingFqn).build();
    final StepElementParameters stepElementParameters = StepElementParameters.builder().spec(stepParameters).build();

    OptionalSweepingOutput optionalSweepingOutput = OptionalSweepingOutput.builder().found(false).build();
    doReturn(optionalSweepingOutput)
        .when(executionSweepingOutputService)
        .resolveOptional(ambiance,
            RefObjectUtils.getSweepingOutputRefObject(rollingFqn + "." + K8sRollingReleaseOutput.OUTPUT_NAME));

    TaskRequest taskRequest = k8sRollingRollbackStep.obtainTask(ambiance, stepElementParameters, stepInputPackage);
    assertThat(taskRequest).isNotNull();
    assertThat(taskRequest.getSkipTaskRequest().getMessage())
        .isEqualTo("K8s Rollout Deploy step was not executed. Skipping rollback.");
    verify(stepHelper, times(0)).sendRollbackTelemetryEvent(any(), any(), any());
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testRollback() {
    OptionalSweepingOutput releaseOutput = OptionalSweepingOutput.builder()
                                               .found(true)
                                               .output(K8sRollingReleaseOutput.builder().name("test").build())
                                               .build();
    OptionalSweepingOutput deploymentOutput =
        OptionalSweepingOutput.builder()
            .found(true)
            .output(K8sRollingOutcome.builder().releaseName("test").releaseNumber(3).build())
            .build();

    testRollback(releaseOutput, deploymentOutput, "test", 3);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testRollbackNoRollingOutcome() {
    OptionalSweepingOutput releaseOutput = OptionalSweepingOutput.builder()
                                               .found(true)
                                               .output(K8sRollingReleaseOutput.builder().name("test").build())
                                               .build();
    OptionalSweepingOutput deploymentOutput = OptionalSweepingOutput.builder().found(false).build();

    testRollback(releaseOutput, deploymentOutput, "test", null);
  }

  @Test
  @Owner(developers = ACHYUTH)
  @Category(UnitTests.class)
  public void testHandleTaskResultWithSecurityContext() throws Exception {
    AccountDTO accountDTO = AccountDTO.builder().name("TestAccountName").build();
    doReturn(accountDTO).when(accountService).getAccount(any());

    StepResponse stepResponse = k8sRollingRollbackStep.handleTaskResultWithSecurityContext(
        ambiance, StepElementParameters.builder().build(), () -> {
          return K8sDeployResponse.builder()
              .commandUnitsProgress(UnitProgressData.builder().build())
              .commandExecutionStatus(CommandExecutionStatus.FAILURE)
              .build();
        });
    assertThat(stepResponse.getStatus()).isEqualTo(Status.FAILED);
    verify(stepHelper, times(1)).sendRollbackTelemetryEvent(any(), any(), any());

    List<K8sPod> k8sPodList = new ArrayList<>();
    k8sPodList.add(K8sPod.builder().name("Pod").namespace("default").build());

    when(instanceInfoService.saveServerInstancesIntoSweepingOutput(any(), any()))
        .thenReturn(StepResponse.StepOutcome.builder().name("abc").build());

    stepResponse = k8sRollingRollbackStep.handleTaskResultWithSecurityContext(
        ambiance, StepElementParameters.builder().build(), () -> {
          return K8sDeployResponse.builder()
              .k8sNGTaskResponse(K8sRollingDeployRollbackResponse.builder().k8sPodList(k8sPodList).build())
              .commandUnitsProgress(UnitProgressData.builder().build())
              .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
              .build();
        });
    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
    assertThat(stepResponse.getStepOutcomes().contains(StepResponse.StepOutcome.builder().name("abc").build()));
    verify(stepHelper, times(2)).sendRollbackTelemetryEvent(any(), any(), any());
  }

  private void testRollback(OptionalSweepingOutput releaseOutput, OptionalSweepingOutput deploymentOutput,
      String expectedReleaseName, Integer expectedReleaseNumber) {
    String rollingFqn = "pipeline.stages.deploy.spec.execution.steps.rolloutDeployment";
    final K8sRollingRollbackStepParameters stepParameters =
        K8sRollingRollbackStepParameters.infoBuilder().rollingStepFqn(rollingFqn).build();
    final StepElementParameters stepElementParameters =
        StepElementParameters.builder().spec(stepParameters).timeout(ParameterField.createValueField("10m")).build();
    final TaskRequest taskRequest = TaskRequest.newBuilder().build();
    final TaskChainResponse taskChainResponse = TaskChainResponse.builder().taskRequest(taskRequest).build();

    doReturn(releaseOutput)
        .when(executionSweepingOutputService)
        .resolveOptional(ambiance,
            RefObjectUtils.getSweepingOutputRefObject(rollingFqn + "." + K8sRollingReleaseOutput.OUTPUT_NAME));
    doReturn(deploymentOutput)
        .when(executionSweepingOutputService)
        .resolveOptional(ambiance,
            RefObjectUtils.getSweepingOutputRefObject(rollingFqn + "." + OutcomeExpressionConstants.K8S_ROLL_OUT));

    doReturn(taskChainResponse)
        .when(k8sStepHelper)
        .queueK8sTask(eq(stepElementParameters), any(K8sDeployRequest.class), eq(ambiance),
            any(K8sExecutionPassThroughData.class));

    TaskRequest result = k8sRollingRollbackStep.obtainTask(ambiance, stepElementParameters, stepInputPackage);
    assertThat(result).isNotNull();
    assertThat(result).isSameAs(taskRequest);

    ArgumentCaptor<K8sDeployRequest> requestArgumentCaptor = ArgumentCaptor.forClass(K8sDeployRequest.class);
    verify(k8sStepHelper, times(1))
        .queueK8sTask(eq(stepElementParameters), requestArgumentCaptor.capture(), eq(ambiance),
            any(K8sExecutionPassThroughData.class));
    K8sRollingRollbackDeployRequest request = (K8sRollingRollbackDeployRequest) requestArgumentCaptor.getValue();
    assertThat(request.getReleaseName()).isEqualTo(expectedReleaseName);
    assertThat(request.getReleaseNumber()).isEqualTo(expectedReleaseNumber);
    verify(stepHelper, times(0)).sendRollbackTelemetryEvent(any(), any(), any());
  }
}
