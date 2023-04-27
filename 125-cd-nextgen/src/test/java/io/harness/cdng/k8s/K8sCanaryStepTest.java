/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.k8s;

import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.rule.OwnerRule.ABOSII;
import static io.harness.rule.OwnerRule.ANSHUL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.NGInstanceUnitType;
import io.harness.category.element.UnitTests;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.featureFlag.CDFeatureFlagHelper;
import io.harness.cdng.instance.info.InstanceInfoService;
import io.harness.cdng.k8s.beans.K8sExecutionPassThroughData;
import io.harness.cdng.manifest.yaml.K8sCommandFlagType;
import io.harness.cdng.manifest.yaml.K8sStepCommandFlag;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.exception.TaskNGDataException;
import io.harness.delegate.task.k8s.K8sCanaryDeployRequest;
import io.harness.delegate.task.k8s.K8sCanaryDeployResponse;
import io.harness.delegate.task.k8s.K8sDeployResponse;
import io.harness.delegate.task.k8s.K8sTaskType;
import io.harness.delegate.task.k8s.data.K8sCanaryDataException;
import io.harness.exception.GeneralException;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.sdk.core.plan.creation.yaml.StepOutcomeGroup;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
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
public class K8sCanaryStepTest extends AbstractK8sStepExecutorTestBase {
  @Mock ExecutionSweepingOutputService executionSweepingOutputService;
  @Mock InstanceInfoService instanceInfoService;
  @InjectMocks private K8sCanaryStep k8sCanaryStep;
  @Mock private CDFeatureFlagHelper cdFeatureFlagHelper;

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testExecuteTask() {
    when(cdFeatureFlagHelper.isEnabled(any(), any())).thenReturn(true);
    CountInstanceSelection instanceSelection = new CountInstanceSelection();
    instanceSelection.setCount(ParameterField.createValueField("10"));
    K8sCanaryStepParameters stepParameters = new K8sCanaryStepParameters();
    stepParameters.setSkipDryRun(ParameterField.createValueField(true));
    Map<String, String> k8sCommandFlag = ImmutableMap.of("Apply", "--server-side");
    List<K8sStepCommandFlag> commandFlags =
        Collections.singletonList(K8sStepCommandFlag.builder()
                                      .commandType(K8sCommandFlagType.Apply)
                                      .flag(ParameterField.createValueField("--server-side"))
                                      .build());
    stepParameters.setCommandFlags(commandFlags);
    stepParameters.setInstanceSelection(
        InstanceSelectionWrapper.builder().type(K8sInstanceUnitType.Count).spec(instanceSelection).build());
    final StepElementParameters stepElementParameters =
        StepElementParameters.builder().spec(stepParameters).timeout(ParameterField.createValueField("30m")).build();

    K8sCanaryDeployRequest request = executeTask(stepElementParameters, K8sCanaryDeployRequest.class);
    assertThat(request.getAccountId()).isEqualTo(accountId);
    assertThat(request.getInstances()).isEqualTo(10);
    assertThat(request.getInstanceUnitType()).isEqualTo(NGInstanceUnitType.COUNT);
    assertThat(request.getK8sInfraDelegateConfig()).isEqualTo(infraDelegateConfig);
    assertThat(request.getManifestDelegateConfig()).isEqualTo(manifestDelegateConfig);
    assertThat(request.getTaskType()).isEqualTo(K8sTaskType.CANARY_DEPLOY);
    assertThat(request.isSkipDryRun()).isTrue();
    assertThat(request.getTimeoutIntervalInMin()).isEqualTo(30);
    assertThat(request.isSkipResourceVersioning()).isTrue();
    assertThat(request.getK8sCommandFlags()).isEqualTo(k8sCommandFlag);

    ArgumentCaptor<String> releaseNameCaptor = ArgumentCaptor.forClass(String.class);
    verify(k8sStepHelper, times(1)).publishReleaseNameStepDetails(eq(ambiance), releaseNameCaptor.capture());
    assertThat(releaseNameCaptor.getValue()).isEqualTo(releaseName);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testExecuteTaskNullParameterFields() {
    PercentageInstanceSelection instanceSelection = new PercentageInstanceSelection();
    instanceSelection.setPercentage(ParameterField.createValueField("90"));
    K8sCanaryStepParameters stepParameters = new K8sCanaryStepParameters();
    stepParameters.setSkipDryRun(ParameterField.ofNull());
    stepParameters.setInstanceSelection(
        InstanceSelectionWrapper.builder().type(K8sInstanceUnitType.Percentage).spec(instanceSelection).build());
    final StepElementParameters stepElementParameters =
        StepElementParameters.builder().spec(stepParameters).timeout(ParameterField.ofNull()).build();

    K8sCanaryDeployRequest request = executeTask(stepElementParameters, K8sCanaryDeployRequest.class);
    assertThat(request.isSkipDryRun()).isFalse();
    assertThat(request.getTimeoutIntervalInMin()).isEqualTo(CDStepHelper.getTimeoutInMin(stepElementParameters));
    assertThat(request.isSkipResourceVersioning()).isTrue();
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testValidateMissingInstanceSelection() {
    K8sCanaryStepParameters canaryStepParameters = K8sCanaryStepParameters.infoBuilder().build();
    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(canaryStepParameters).build();

    StepInputPackage stepInputPackage = StepInputPackage.builder().build();
    assertThatThrownBy(() -> k8sCanaryStep.startChainLink(ambiance, stepElementParameters, stepInputPackage))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Instance selection is mandatory");

    canaryStepParameters.setInstanceSelection(InstanceSelectionWrapper.builder().build());
    assertThatThrownBy(() -> k8sCanaryStep.startChainLink(ambiance, stepElementParameters, stepInputPackage))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Instance selection is mandatory");

    canaryStepParameters.setInstanceSelection(
        InstanceSelectionWrapper.builder().type(K8sInstanceUnitType.Count).build());
    assertThatThrownBy(() -> k8sCanaryStep.startChainLink(ambiance, stepElementParameters, stepInputPackage))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Instance selection is mandatory");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testValidateMissingInstanceSelectionValue() {
    StepInputPackage stepInputPackage = StepInputPackage.builder().build();
    InstanceSelectionWrapper instanceSelection =
        InstanceSelectionWrapper.builder().type(K8sInstanceUnitType.Count).spec(new CountInstanceSelection()).build();
    K8sCanaryStepParameters canaryStepParameters =
        K8sCanaryStepParameters.infoBuilder().instanceSelection(instanceSelection).build();
    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(canaryStepParameters).build();

    assertThatThrownBy(() -> k8sCanaryStep.startChainLink(ambiance, stepElementParameters, stepInputPackage))
        .isInstanceOf(InvalidArgumentsException.class)
        .hasMessageContaining("Instance selection count value is mandatory");

    instanceSelection.setType(K8sInstanceUnitType.Percentage);
    instanceSelection.setSpec(new PercentageInstanceSelection());
    assertThatThrownBy(() -> k8sCanaryStep.startChainLink(ambiance, stepElementParameters, stepInputPackage))
        .isInstanceOf(InvalidArgumentsException.class)
        .hasMessageContaining("Instance selection percentage value is mandatory");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testValidateInvalidInstanceSelectionValue() {
    StepInputPackage stepInputPackage = StepInputPackage.builder().build();
    CountInstanceSelection countSpec = new CountInstanceSelection();
    PercentageInstanceSelection percentageSpec = new PercentageInstanceSelection();
    countSpec.setCount(ParameterField.createValueField("0"));
    percentageSpec.setPercentage(ParameterField.createValueField("0"));
    InstanceSelectionWrapper instanceSelection =
        InstanceSelectionWrapper.builder().type(K8sInstanceUnitType.Count).spec(countSpec).build();
    K8sCanaryStepParameters canaryStepParameters =
        K8sCanaryStepParameters.infoBuilder().instanceSelection(instanceSelection).build();
    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(canaryStepParameters).build();

    assertThatThrownBy(() -> k8sCanaryStep.startChainLink(ambiance, stepElementParameters, stepInputPackage))
        .isInstanceOf(InvalidArgumentsException.class)
        .hasMessageContaining("Instance selection count value cannot be less than 1");

    instanceSelection.setType(K8sInstanceUnitType.Percentage);
    instanceSelection.setSpec(percentageSpec);

    assertThatThrownBy(() -> k8sCanaryStep.startChainLink(ambiance, stepElementParameters, stepInputPackage))
        .isInstanceOf(InvalidArgumentsException.class)
        .hasMessageContaining("Instance selection percentage value cannot be less than 1");
  }

  @SneakyThrows
  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testOutcomesInResponse() {
    K8sCanaryStepParameters stepParameters = new K8sCanaryStepParameters();
    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(stepParameters).build();

    K8sDeployResponse k8sDeployResponse = K8sDeployResponse.builder()
                                              .k8sNGTaskResponse(K8sCanaryDeployResponse.builder()
                                                                     .canaryWorkload("canaryWorkload")
                                                                     .releaseNumber(1)
                                                                     .k8sPodList(new ArrayList<>())
                                                                     .build())
                                              .commandUnitsProgress(UnitProgressData.builder().build())
                                              .commandExecutionStatus(SUCCESS)
                                              .build();
    when(cdStepHelper.getReleaseName(any(), any())).thenReturn("releaseName");

    StepResponse response = k8sCanaryStep.finalizeExecutionWithSecurityContext(
        ambiance, stepElementParameters, K8sExecutionPassThroughData.builder().build(), () -> k8sDeployResponse);
    assertThat(response.getStatus()).isEqualTo(Status.SUCCEEDED);
    assertThat(response.getStepOutcomes()).hasSize(1);

    StepOutcome outcome = response.getStepOutcomes().stream().collect(Collectors.toList()).get(0);
    assertThat(outcome.getOutcome()).isInstanceOf(K8sCanaryOutcome.class);
    assertThat(outcome.getName()).isEqualTo(OutcomeExpressionConstants.OUTPUT);
    assertThat(outcome.getGroup()).isNull();

    ArgumentCaptor<K8sCanaryOutcome> argumentCaptor = ArgumentCaptor.forClass(K8sCanaryOutcome.class);
    verify(executionSweepingOutputService, times(1))
        .consume(eq(ambiance), eq(OutcomeExpressionConstants.K8S_CANARY_OUTCOME), argumentCaptor.capture(),
            eq(StepOutcomeGroup.STEP.name()));
    assertThat(argumentCaptor.getValue().getReleaseName()).isEqualTo("releaseName");
    assertThat(argumentCaptor.getValue().getCanaryWorkload()).isEqualTo("canaryWorkload");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testFinalizeExecutionExceptionDelegateException() throws Exception {
    final StepElementParameters stepElementParameters = StepElementParameters.builder().build();
    final Exception thrownException = new GeneralException("Something went wrong");
    final K8sExecutionPassThroughData executionPassThroughData = K8sExecutionPassThroughData.builder().build();
    final StepResponse stepResponse = StepResponse.builder().status(Status.FAILED).build();

    doReturn(stepResponse).when(k8sStepHelper).handleTaskException(ambiance, executionPassThroughData, thrownException);

    StepResponse response = k8sCanaryStep.finalizeExecutionWithSecurityContext(
        ambiance, stepElementParameters, executionPassThroughData, () -> { throw thrownException; });

    assertThat(response).isEqualTo(stepResponse);

    verify(k8sStepHelper, times(1)).handleTaskException(ambiance, executionPassThroughData, thrownException);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testFinalizeExecutionExceptionTaskException() throws Exception {
    final String canaryWorkload = "deployment/deployment-canary";
    final StepElementParameters stepElementParameters = StepElementParameters.builder().build();
    final K8sExecutionPassThroughData executionPassThroughData = K8sExecutionPassThroughData.builder().build();
    final Exception cause = new RuntimeException("Error while executing task");
    final Exception taskException = new TaskNGDataException(
        UnitProgressData.builder().build(), new K8sCanaryDataException(canaryWorkload, true, cause));

    doThrow(taskException).when(k8sStepHelper).handleTaskException(ambiance, executionPassThroughData, taskException);
    try {
      k8sCanaryStep.finalizeExecutionWithSecurityContext(
          ambiance, stepElementParameters, executionPassThroughData, () -> { throw taskException; });

    } catch (Exception e) {
      assertThat(e).isSameAs(taskException);
    }
    ArgumentCaptor<K8sCanaryOutcome> canaryOutcomeCaptor = ArgumentCaptor.forClass(K8sCanaryOutcome.class);
    verify(executionSweepingOutputService, times(1))
        .consume(eq(ambiance), eq(OutcomeExpressionConstants.K8S_CANARY_OUTCOME), canaryOutcomeCaptor.capture(),
            eq(StepOutcomeGroup.STEP.name()));
    K8sCanaryOutcome canaryOutcome = canaryOutcomeCaptor.getValue();
    assertThat(canaryOutcome.getCanaryWorkload()).isEqualTo(canaryWorkload);
    assertThat(canaryOutcome.isCanaryWorkloadDeployed()).isTrue();
  }

  @Override
  protected K8sStepExecutor getK8sStepExecutor() {
    return k8sCanaryStep;
  }
}
