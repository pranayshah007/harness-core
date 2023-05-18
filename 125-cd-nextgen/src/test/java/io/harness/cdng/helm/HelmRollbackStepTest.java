/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.helm;

import static io.harness.rule.OwnerRule.ABOSII;
import static io.harness.rule.OwnerRule.ACHYUTH;
import static io.harness.rule.OwnerRule.ANSHUL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.account.services.AccountService;
import io.harness.category.element.UnitTests;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.featureFlag.CDFeatureFlagHelper;
import io.harness.cdng.helm.beans.NativeHelmExecutionPassThroughData;
import io.harness.cdng.helm.rollback.HelmRollbackStepParams;
import io.harness.cdng.instance.info.InstanceInfoService;
import io.harness.cdng.manifest.steps.outcome.ManifestsOutcome;
import io.harness.cdng.manifest.yaml.HelmChartManifestOutcome;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.container.ContainerInfo;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.task.helm.HelmCmdExecResponseNG;
import io.harness.delegate.task.helm.HelmCommandRequestNG;
import io.harness.delegate.task.helm.HelmInstallCmdResponseNG;
import io.harness.delegate.task.helm.HelmRollbackCommandRequestNG;
import io.harness.delegate.task.k8s.HelmChartManifestDelegateConfig;
import io.harness.delegate.task.k8s.K8sInfraDelegateConfig;
import io.harness.k8s.model.HelmVersion;
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

import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class HelmRollbackStepTest extends CategoryTest {
  private final Level level =
      Level.newBuilder().setIdentifier("TestRollbackStep").setGroup(StepOutcomeGroup.STEP.name()).build();
  private final Ambiance ambiance = Ambiance.newBuilder().addLevels(level).build();
  private final StepInputPackage stepInputPackage = StepInputPackage.builder().build();

  @Mock private OutcomeService outcomeService;
  @Mock private ExecutionSweepingOutputService executionSweepingOutputService;
  @Mock private NativeHelmStepHelper nativeHelmStepHelper;
  @Mock private CDStepHelper cdStepHelper;
  @Mock private InstanceInfoService instanceInfoService;
  @Mock private K8sInfraDelegateConfig infraDelegateConfig;
  protected final HelmChartManifestDelegateConfig manifestDelegateConfig =
      HelmChartManifestDelegateConfig.builder().build();
  protected final UnitProgressData unitProgressData = UnitProgressData.builder().build();
  @Mock private CDFeatureFlagHelper cdFeatureFlagHelper;
  @InjectMocks private HelmRollbackStep helmRollbackStep;
  @Mock private AccountService accountService;
  @Mock private StepHelper stepHelper;
  @Mock private TelemetryReporter telemetryReporter;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    when(nativeHelmStepHelper.resolveManifestsOutcome(any()))
        .thenReturn(new ManifestsOutcome(new HashMap<>(
            ImmutableMap.of("entityType", HelmChartManifestOutcome.builder().helmVersion(HelmVersion.V3).build()))));
    when(nativeHelmStepHelper.getHelmSupportedManifestOutcome(any()))
        .thenReturn(HelmChartManifestOutcome.builder().helmVersion(HelmVersion.V3).build());
    doReturn(infraDelegateConfig).when(cdStepHelper).getK8sInfraDelegateConfig(any(), eq(ambiance));
    doReturn(manifestDelegateConfig).when(nativeHelmStepHelper).getManifestDelegateConfig(any(), eq(ambiance));
    when(cdFeatureFlagHelper.isEnabled(any(), any())).thenReturn(true);
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testSkippingOfRollbackStep() {
    String helmDeployFqn = "pipeline.stages.deploy.spec.execution.steps.helmDeployment";
    HelmRollbackStepParams stepParameters = HelmRollbackStepParams.infoBuilder().helmRollbackFqn(helmDeployFqn).build();
    final StepElementParameters stepElementParameters = StepElementParameters.builder().spec(stepParameters).build();

    OptionalSweepingOutput optionalSweepingOutput = OptionalSweepingOutput.builder().found(false).build();
    doReturn(optionalSweepingOutput)
        .when(executionSweepingOutputService)
        .resolveOptional(ambiance,
            RefObjectUtils.getSweepingOutputRefObject(
                helmDeployFqn + "." + OutcomeExpressionConstants.HELM_DEPLOY_OUTCOME));

    TaskRequest taskRequest = helmRollbackStep.obtainTask(ambiance, stepElementParameters, stepInputPackage);
    assertThat(taskRequest).isNotNull();
    assertThat(taskRequest.getSkipTaskRequest().getMessage())
        .isEqualTo("Helm Deploy step was not executed. Skipping rollback.");
    verify(stepHelper, times(0)).sendRollbackTelemetryEvent(any(), any());
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testRollback() {
    OptionalSweepingOutput releaseOutput =
        OptionalSweepingOutput.builder()
            .found(true)
            .output(NativeHelmDeployOutcome.builder().hasInstallUpgradeStarted(true).releaseName("test").build())
            .build();
    OptionalSweepingOutput deploymentOutput =
        OptionalSweepingOutput.builder()
            .found(true)
            .output(NativeHelmDeployOutcome.builder().hasInstallUpgradeStarted(true).releaseName("test").build())
            .build();

    testRollback(releaseOutput, deploymentOutput, "test");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testRollbackNoRollingOutcome() {
    OptionalSweepingOutput releaseOutput = OptionalSweepingOutput.builder()
                                               .found(true)
                                               .output(NativeHelmDeployOutcome.builder().releaseName("test").build())
                                               .build();
    OptionalSweepingOutput deploymentOutput = OptionalSweepingOutput.builder().found(false).build();

    String deployFqn = "pipeline.stages.deploy.spec.execution.steps.helmDeployment";
    final HelmRollbackStepParams stepParameters =
        HelmRollbackStepParams.infoBuilder().helmRollbackFqn(deployFqn).build();
    final StepElementParameters stepElementParameters =
        StepElementParameters.builder().spec(stepParameters).timeout(ParameterField.createValueField("10m")).build();

    doReturn(releaseOutput)
        .when(executionSweepingOutputService)
        .resolveOptional(ambiance,
            RefObjectUtils.getSweepingOutputRefObject(
                deployFqn + "." + OutcomeExpressionConstants.HELM_DEPLOY_OUTCOME));
    doReturn(deploymentOutput)
        .when(executionSweepingOutputService)
        .resolveOptional(ambiance,
            RefObjectUtils.getSweepingOutputRefObject(
                deployFqn + "." + OutcomeExpressionConstants.HELM_DEPLOY_OUTCOME));

    TaskRequest result = helmRollbackStep.obtainTask(ambiance, stepElementParameters, stepInputPackage);

    assertThat(result.getSkipTaskRequest().getMessage())
        .contains("Helm Deploy step was not executed. Skipping rollback.");
  }

  @Test
  @Owner(developers = ACHYUTH)
  @Category(UnitTests.class)
  public void testHandleTaskResultWithSecurityContext() throws Exception {
    AccountDTO accountDTO = AccountDTO.builder().name("TestAccountName").build();
    doReturn(accountDTO).when(accountService).getAccount(any());

    StepResponse stepResponse =
        helmRollbackStep.handleTaskResultWithSecurityContext(ambiance, StepElementParameters.builder().build(), () -> {
          return HelmCmdExecResponseNG.builder()
              .commandUnitsProgress(UnitProgressData.builder().build())
              .commandExecutionStatus(CommandExecutionStatus.FAILURE)
              .build();
        });
    assertThat(stepResponse.getStatus()).isEqualTo(Status.FAILED);
    verify(stepHelper, times(1)).sendRollbackTelemetryEvent(any(), any());

    List<ContainerInfo> containerInfoList = new ArrayList<>();
    containerInfoList.add(ContainerInfo.builder().podName("Pod").namespace("default").build());

    when(instanceInfoService.saveServerInstancesIntoSweepingOutput(any(), any()))
        .thenReturn(StepResponse.StepOutcome.builder().name("abc").build());

    stepResponse =
        helmRollbackStep.handleTaskResultWithSecurityContext(ambiance, StepElementParameters.builder().build(), () -> {
          return HelmCmdExecResponseNG.builder()
              .helmCommandResponse(HelmInstallCmdResponseNG.builder().containerInfoList(containerInfoList).build())
              .commandUnitsProgress(UnitProgressData.builder().build())
              .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
              .build();
        });
    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
    assertThat(stepResponse.getStepOutcomes().contains(StepResponse.StepOutcome.builder().name("abc").build()));
    verify(stepHelper, times(2)).sendRollbackTelemetryEvent(any(), any());
  }

  private void testRollback(
      OptionalSweepingOutput releaseOutput, OptionalSweepingOutput deploymentOutput, String expectedReleaseName) {
    String deployFqn = "pipeline.stages.deploy.spec.execution.steps.helmDeployment";
    final HelmRollbackStepParams stepParameters =
        HelmRollbackStepParams.infoBuilder().helmRollbackFqn(deployFqn).build();
    final StepElementParameters stepElementParameters =
        StepElementParameters.builder().spec(stepParameters).timeout(ParameterField.createValueField("10m")).build();
    final TaskRequest taskRequest = TaskRequest.newBuilder().build();
    final TaskChainResponse taskChainResponse = TaskChainResponse.builder().taskRequest(taskRequest).build();

    doReturn(releaseOutput)
        .when(executionSweepingOutputService)
        .resolveOptional(ambiance,
            RefObjectUtils.getSweepingOutputRefObject(
                deployFqn + "." + OutcomeExpressionConstants.HELM_DEPLOY_OUTCOME));
    doReturn(deploymentOutput)
        .when(executionSweepingOutputService)
        .resolveOptional(ambiance,
            RefObjectUtils.getSweepingOutputRefObject(
                deployFqn + "." + OutcomeExpressionConstants.HELM_DEPLOY_OUTCOME));

    doReturn(taskChainResponse)
        .when(nativeHelmStepHelper)
        .queueNativeHelmTask(eq(stepElementParameters), any(HelmCommandRequestNG.class), eq(ambiance),
            any(NativeHelmExecutionPassThroughData.class));

    doReturn("test").when(cdStepHelper).getReleaseName(eq(ambiance), any());

    TaskRequest result = helmRollbackStep.obtainTask(ambiance, stepElementParameters, stepInputPackage);
    assertThat(result).isNotNull();

    ArgumentCaptor<HelmCommandRequestNG> requestArgumentCaptor = ArgumentCaptor.forClass(HelmCommandRequestNG.class);
    verify(nativeHelmStepHelper, times(1))
        .queueNativeHelmTask(eq(stepElementParameters), requestArgumentCaptor.capture(), eq(ambiance),
            any(NativeHelmExecutionPassThroughData.class));
    HelmRollbackCommandRequestNG request = (HelmRollbackCommandRequestNG) requestArgumentCaptor.getValue();
    assertThat(request.getReleaseName()).isEqualTo(expectedReleaseName);
    verify(stepHelper, times(0)).sendRollbackTelemetryEvent(any(), any());
  }
}
