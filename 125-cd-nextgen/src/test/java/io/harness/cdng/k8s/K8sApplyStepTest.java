/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.k8s;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.ABOSII;
import static io.harness.rule.OwnerRule.ACASIAN;
import static io.harness.rule.OwnerRule.ACHYUTH;
import static io.harness.rule.OwnerRule.NAMAN_TALAYCHA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.featureFlag.CDFeatureFlagHelper;
import io.harness.cdng.k8s.beans.GitFetchResponsePassThroughData;
import io.harness.cdng.k8s.beans.HelmValuesFetchResponsePassThroughData;
import io.harness.cdng.k8s.beans.K8sExecutionPassThroughData;
import io.harness.cdng.k8s.beans.StepExceptionPassThroughData;
import io.harness.cdng.manifest.steps.outcome.ManifestsOutcome;
import io.harness.cdng.manifest.yaml.K8sCommandFlagType;
import io.harness.cdng.manifest.yaml.K8sManifestOutcome;
import io.harness.cdng.manifest.yaml.K8sStepCommandFlag;
import io.harness.cdng.manifest.yaml.ManifestConfig;
import io.harness.cdng.manifest.yaml.ManifestConfigWrapper;
import io.harness.cdng.manifest.yaml.OpenshiftManifestOutcome;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.task.k8s.K8sApplyRequest;
import io.harness.delegate.task.k8s.K8sDeployResponse;
import io.harness.delegate.task.k8s.K8sTaskType;
import io.harness.exception.GeneralException;
import io.harness.exception.InvalidRequestException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;

import com.google.common.collect.ImmutableMap;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(CDP)
public class K8sApplyStepTest extends AbstractK8sStepExecutorTestBase {
  @InjectMocks private K8sApplyStep k8sApplyStep;
  private static final String COMMAND_FLAG = "--server-side";
  @Mock private CDFeatureFlagHelper cdFeatureFlagHelper;

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testExecuteTask() {
    K8sApplyStepParameters stepParameters = new K8sApplyStepParameters();
    Map<String, String> k8sCommandFlag = ImmutableMap.of("Apply", "--server-side");
    List<K8sStepCommandFlag> commandFlags =
        Collections.singletonList(K8sStepCommandFlag.builder()
                                      .commandType(K8sCommandFlagType.Apply)
                                      .flag(ParameterField.createValueField("--server-side"))
                                      .build());
    stepParameters.setSkipDryRun(ParameterField.createValueField(true));
    stepParameters.setSkipSteadyStateCheck(ParameterField.createValueField(true));
    stepParameters.setFilePaths(ParameterField.createValueField(Arrays.asList("file1.yaml", "file2.yaml")));
    stepParameters.setCommandFlags(commandFlags);
    final StepElementParameters stepElementParameters =
        StepElementParameters.builder().spec(stepParameters).timeout(ParameterField.createValueField("30m")).build();
    K8sApplyRequest request = executeTask(stepElementParameters, K8sApplyRequest.class);
    assertThat(request.getAccountId()).isEqualTo(accountId);
    assertThat(request.getFilePaths()).containsExactlyInAnyOrder("file1.yaml", "file2.yaml");
    assertThat(request.getTaskType()).isEqualTo(K8sTaskType.APPLY);
    assertThat(request.isSkipDryRun()).isTrue();
    assertThat(request.isSkipSteadyStateCheck()).isTrue();
    assertThat(request.getTimeoutIntervalInMin()).isEqualTo(30);
    assertThat(request.getK8sCommandFlags()).isEqualTo(k8sCommandFlag);

    ArgumentCaptor<String> releaseNameCaptor = ArgumentCaptor.forClass(String.class);
    verify(k8sStepHelper, times(1)).publishReleaseNameStepDetails(eq(ambiance), releaseNameCaptor.capture());
    assertThat(releaseNameCaptor.getValue()).isEqualTo(releaseName);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testExecuteNullParameterFields() {
    K8sApplyStepParameters stepParameters = new K8sApplyStepParameters();
    stepParameters.setSkipDryRun(ParameterField.ofNull());
    stepParameters.setSkipSteadyStateCheck(ParameterField.ofNull());
    stepParameters.setFilePaths(ParameterField.createValueField(Arrays.asList("file1.yaml", "file2.yaml")));
    when(cdFeatureFlagHelper.isEnabled(any(), any())).thenReturn(true);
    final StepElementParameters stepElementParameters =
        StepElementParameters.builder().spec(stepParameters).timeout(ParameterField.ofNull()).build();

    K8sApplyRequest request = executeTask(stepElementParameters, K8sApplyRequest.class);
    assertThat(request.isSkipDryRun()).isFalse();
    assertThat(request.isSkipSteadyStateCheck()).isFalse();
    assertThat(request.getTimeoutIntervalInMin()).isEqualTo(CDStepHelper.getTimeoutInMin(stepElementParameters));
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testShouldValidateFilePathsSuccess() {
    K8sApplyStepParameters stepParameters = new K8sApplyStepParameters();
    stepParameters.setFilePaths(ParameterField.createValueField(Arrays.asList("file1.yaml", "file2.yaml")));

    ManifestsOutcome manifestsOutcomes = new ManifestsOutcome();
    doReturn(manifestsOutcomes).when(k8sStepHelper).resolveManifestsOutcome(ambiance);
    doReturn(K8sManifestOutcome.builder().build()).when(k8sStepHelper).getK8sSupportedManifestOutcome(any());

    final StepElementParameters stepElementParameters =
        StepElementParameters.builder().spec(stepParameters).timeout(ParameterField.ofNull()).build();
    k8sApplyStep.startChainLink(ambiance, stepElementParameters, StepInputPackage.builder().build());

    verify(k8sStepHelper, times(1)).startChainLink(eq(k8sApplyStep), eq(ambiance), eq(stepElementParameters));
  }

  @Test
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testShouldValidateFilePathsSuccessOverrides() {
    K8sApplyStepParameters stepParameters = new K8sApplyStepParameters();
    stepParameters.setFilePaths(ParameterField.createValueField(Arrays.asList("file1.yaml", "file2.yaml")));
    stepParameters.setOverrides(
        Collections.singletonList(ManifestConfigWrapper.builder().manifest(ManifestConfig.builder().build()).build()));
    ManifestsOutcome manifestsOutcomes = new ManifestsOutcome();
    doReturn(manifestsOutcomes).when(k8sStepHelper).resolveManifestsOutcome(ambiance);
    doReturn(K8sManifestOutcome.builder().build()).when(k8sStepHelper).getK8sSupportedManifestOutcome(any());

    final StepElementParameters stepElementParameters =
        StepElementParameters.builder().spec(stepParameters).timeout(ParameterField.ofNull()).build();
    k8sApplyStep.startChainLink(ambiance, stepElementParameters, StepInputPackage.builder().build());
    verify(k8sStepHelper, times(1)).resolveManifestsConfigExpressions(eq(ambiance), eq(stepParameters.overrides));

    verify(k8sStepHelper, times(1)).startChainLink(eq(k8sApplyStep), eq(ambiance), eq(stepElementParameters));
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testShouldValidateFilePathsFailure() {
    final StepElementParameters stepElementParameters =
        StepElementParameters.builder()
            .spec(K8sApplyStepParameters.infoBuilder()
                      .filePaths(ParameterField.createValueField(Arrays.asList("file1.yaml", "")))
                      .build())
            .timeout(ParameterField.ofNull())
            .build();

    assertFilePathsValidation(stepElementParameters);

    final StepElementParameters stepElementParametersWithEmptyFilePaths =
        StepElementParameters.builder()
            .spec(K8sApplyStepParameters.infoBuilder()
                      .filePaths(ParameterField.createValueField(Collections.emptyList()))
                      .build())
            .timeout(ParameterField.ofNull())
            .build();

    assertFilePathsValidation(stepElementParametersWithEmptyFilePaths);

    final StepElementParameters stepElementParametersWithNullFilePaths =
        StepElementParameters.builder()
            .spec(K8sApplyStepParameters.infoBuilder().filePaths(ParameterField.createValueField(null)).build())
            .timeout(ParameterField.ofNull())
            .build();

    assertFilePathsValidation(stepElementParametersWithNullFilePaths);
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testShouldValidateManifestType() {
    K8sApplyStepParameters stepParameters = new K8sApplyStepParameters();
    stepParameters.setFilePaths(ParameterField.createValueField(Arrays.asList("file1.yaml", "file2.yaml")));

    ManifestsOutcome manifestsOutcomes = new ManifestsOutcome();
    doReturn(manifestsOutcomes).when(k8sStepHelper).resolveManifestsOutcome(ambiance);
    doReturn(OpenshiftManifestOutcome.builder().build()).when(k8sStepHelper).getK8sSupportedManifestOutcome(any());

    final StepElementParameters stepElementParameters =
        StepElementParameters.builder().spec(stepParameters).timeout(ParameterField.ofNull()).build();

    try {
      k8sApplyStep.startChainLink(ambiance, stepElementParameters, StepInputPackage.builder().build());
      fail("Should throw unsupported operation exception");
    } catch (Exception ex) {
      assertThat(ex).isInstanceOf(UnsupportedOperationException.class);
      assertThat(ex.getMessage()).isEqualTo("Unsupported Manifest type: [OpenshiftTemplate]");
    }
  }

  @Test
  @Owner(developers = ACHYUTH)
  @Category(UnitTests.class)
  public void testFinalizeExecutionWithSecurityContext() throws Exception {
    StepElementParameters stepElementParameters =
        StepElementParameters.builder()
            .spec(K8sApplyStepParameters.infoBuilder()
                      .filePaths(ParameterField.createValueField(Arrays.asList("file1.yaml", "")))
                      .build())
            .timeout(ParameterField.ofNull())
            .build();

    StepExceptionPassThroughData passThroughData = StepExceptionPassThroughData.builder().errorMessage("abcd").build();
    HelmValuesFetchResponsePassThroughData helmValuesFetchResponsePassThroughData =
        HelmValuesFetchResponsePassThroughData.builder().build();
    GitFetchResponsePassThroughData gitFetchResponsePassThroughData = GitFetchResponsePassThroughData.builder().build();

    K8sExecutionPassThroughData k8sExecutionPassThroughData = K8sExecutionPassThroughData.builder().build();

    final Exception thrownException = new GeneralException("Something went wrong");

    when(cdStepHelper.handleStepExceptionFailure(any()))
        .thenReturn(StepResponse.builder().status(Status.FAILED).build());
    StepResponse stepResponse = k8sApplyStep.finalizeExecutionWithSecurityContext(
        ambiance, stepElementParameters, passThroughData, () -> { throw thrownException; });
    assertThat(stepResponse.getStatus()).isEqualTo(Status.FAILED);

    when(k8sStepHelper.handleHelmValuesFetchFailure(any()))
        .thenReturn(StepResponse.builder().status(Status.FAILED).build());
    StepResponse stepResponseHelm = k8sApplyStep.finalizeExecutionWithSecurityContext(
        ambiance, stepElementParameters, helmValuesFetchResponsePassThroughData, () -> { throw thrownException; });
    assertThat(stepResponseHelm.getStatus()).isEqualTo(Status.FAILED);

    when(cdStepHelper.handleGitTaskFailure(any())).thenReturn(StepResponse.builder().status(Status.FAILED).build());
    StepResponse stepResponseGit = k8sApplyStep.finalizeExecutionWithSecurityContext(
        ambiance, stepElementParameters, gitFetchResponsePassThroughData, () -> { throw thrownException; });
    assertThat(stepResponseGit.getStatus()).isEqualTo(Status.FAILED);

    when(k8sStepHelper.handleTaskException(any(), any(), any()))
        .thenReturn(StepResponse.builder().status(Status.FAILED).build());
    assertThat(k8sApplyStep.finalizeExecutionWithSecurityContext(
                   ambiance, stepElementParameters, k8sExecutionPassThroughData, () -> { throw thrownException; }))
        .isNotNull();

    assertThat(
        k8sApplyStep.finalizeExecutionWithSecurityContext(ambiance, stepElementParameters, k8sExecutionPassThroughData,
            () -> {
              return K8sDeployResponse.builder()
                  .commandUnitsProgress(UnitProgressData.builder().build())
                  .commandExecutionStatus(CommandExecutionStatus.FAILURE)
                  .build();
            }))
        .isNotNull();

    assertThat(
        k8sApplyStep.finalizeExecutionWithSecurityContext(ambiance, stepElementParameters, k8sExecutionPassThroughData,
            () -> {
              return K8sDeployResponse.builder()
                  .commandUnitsProgress(UnitProgressData.builder().build())
                  .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                  .build();
            }))
        .isNotNull();

    verify(cdStepHelper, times(1)).handleStepExceptionFailure(any());
    verify(k8sStepHelper, times(1)).handleHelmValuesFetchFailure(any());
    verify(cdStepHelper, times(1)).handleGitTaskFailure(any());
    verify(k8sStepHelper, times(1)).handleTaskException(any(), any(), any());
  }

  private void assertFilePathsValidation(StepElementParameters stepElementParameters) {
    try {
      k8sApplyStep.startChainLink(ambiance, stepElementParameters, StepInputPackage.builder().build());
      fail("Should throw invalid request exception");
    } catch (Exception ex) {
      assertThat(ex).isInstanceOf(InvalidRequestException.class);
      assertThat(ex.getMessage()).isEqualTo("File/Folder path must be present");
    }
  }

  @Override
  protected K8sStepExecutor getK8sStepExecutor() {
    return k8sApplyStep;
  }
}
