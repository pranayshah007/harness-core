/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.k8s;

import static io.harness.cdng.k8s.K8sScaleStep.K8S_SCALE_COMMAND_NAME;
import static io.harness.logging.CommandExecutionStatus.FAILURE;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.rule.OwnerRule.ACASIAN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
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
import io.harness.cdng.instance.info.InstanceInfoService;
import io.harness.cdng.instance.outcome.DeploymentInfoOutcome;
import io.harness.cdng.k8s.beans.K8sExecutionPassThroughData;
import io.harness.cdng.manifest.steps.outcome.ManifestsOutcome;
import io.harness.cdng.manifest.yaml.K8sManifestOutcome;
import io.harness.cdng.manifest.yaml.ManifestOutcome;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfig;
import io.harness.cdng.service.beans.ServiceOutcome;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.task.k8s.K8sDeployRequest;
import io.harness.delegate.task.k8s.K8sDeployResponse;
import io.harness.delegate.task.k8s.K8sInfraDelegateConfig;
import io.harness.delegate.task.k8s.K8sScaleRequest;
import io.harness.delegate.task.k8s.K8sScaleResponse;
import io.harness.delegate.task.k8s.K8sTaskType;
import io.harness.delegate.task.k8s.ManifestDelegateConfig;
import io.harness.exception.InvalidRequestException;
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

import java.util.Collection;
import java.util.Collections;
import lombok.SneakyThrows;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.CDP)
public class K8sScaleStepTest extends CategoryTest {
  @Mock private OutcomeService outcomeService;
  @Mock private K8sStepHelper k8sStepHelper;
  @Mock private CDStepHelper cdStepHelper;
  @Mock private InfrastructureOutcome infrastructureOutcome;
  @Mock private K8sInfraDelegateConfig infraDelegateConfig;
  @Mock private ManifestDelegateConfig manifestDelegateConfig;
  @Mock StoreConfig storeConfig;
  @Mock ServiceOutcome serviceOutcome;
  @Mock private InstanceInfoService instanceInfoService;
  private final ManifestOutcome manifestOutcome = K8sManifestOutcome.builder().store(storeConfig).build();
  private final Ambiance ambiance = Ambiance.newBuilder().build();
  private final StepInputPackage stepInputPackage = StepInputPackage.builder().build();

  @InjectMocks private K8sScaleStep scaleStep;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    doReturn(infraDelegateConfig).when(cdStepHelper).getK8sInfraDelegateConfig(infrastructureOutcome, ambiance);
    doReturn(manifestDelegateConfig).when(k8sStepHelper).getManifestDelegateConfig(manifestOutcome, ambiance);
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testShouldFailIfCountInstanceSelectionNotANumber() {
    CountInstanceSelection spec = new CountInstanceSelection();
    spec.setCount(ParameterField.createValueField("2.2"));

    final K8sScaleStepParameter stepParameters =
        K8sScaleStepParameter.infoBuilder()
            .instanceSelection(InstanceSelectionWrapper.builder().spec(spec).type(K8sInstanceUnitType.Count).build())
            .skipSteadyStateCheck(ParameterField.createValueField(false))
            .workload(ParameterField.createValueField("Deployment/test-scale-count-deployment"))
            .build();

    final K8sExecutionPassThroughData executionPassThroughData =
        K8sExecutionPassThroughData.builder().infrastructure(infrastructureOutcome).build();
    final StepElementParameters stepElementParameters =
        StepElementParameters.builder().spec(stepParameters).timeout(ParameterField.createValueField("10m")).build();

    final TaskRequest taskRequest = TaskRequest.newBuilder().build();
    doReturn(TaskChainResponse.builder().taskRequest(taskRequest).build())
        .when(k8sStepHelper)
        .queueK8sTask(
            eq(stepElementParameters), any(K8sDeployRequest.class), eq(ambiance), eq(executionPassThroughData));

    doReturn(new ManifestsOutcome(serviceOutcome.getManifestResults()))
        .when(outcomeService)
        .resolve(ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.MANIFESTS));
    doReturn(infrastructureOutcome)
        .when(outcomeService)
        .resolve(ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.INFRASTRUCTURE_OUTCOME));

    assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(() -> scaleStep.obtainTask(ambiance, stepElementParameters, stepInputPackage))
        .withMessageContaining("Count value: [2.2] is not an integer");
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testShouldFailIfPercentageInstanceSelectionNotANumber() {
    PercentageInstanceSelection spec = new PercentageInstanceSelection();
    spec.setPercentage(ParameterField.createValueField("80.5"));

    final K8sScaleStepParameter stepParameters =
        K8sScaleStepParameter.infoBuilder()
            .instanceSelection(
                InstanceSelectionWrapper.builder().spec(spec).type(K8sInstanceUnitType.Percentage).build())
            .skipSteadyStateCheck(ParameterField.createValueField(false))
            .workload(ParameterField.createValueField("Deployment/test-scale-percentage-deployment"))
            .build();

    final K8sExecutionPassThroughData executionPassThroughData =
        K8sExecutionPassThroughData.builder().infrastructure(infrastructureOutcome).build();
    final StepElementParameters stepElementParameters =
        StepElementParameters.builder().spec(stepParameters).timeout(ParameterField.createValueField("10m")).build();

    final TaskRequest taskRequest = TaskRequest.newBuilder().build();
    doReturn(TaskChainResponse.builder().taskRequest(taskRequest).build())
        .when(k8sStepHelper)
        .queueK8sTask(
            eq(stepElementParameters), any(K8sDeployRequest.class), eq(ambiance), eq(executionPassThroughData));

    doReturn(new ManifestsOutcome(serviceOutcome.getManifestResults()))
        .when(outcomeService)
        .resolve(ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.MANIFESTS));
    doReturn(infrastructureOutcome)
        .when(outcomeService)
        .resolve(ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.INFRASTRUCTURE_OUTCOME));

    assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(() -> scaleStep.obtainTask(ambiance, stepElementParameters, stepInputPackage))
        .withMessageContaining("Percentage value: [80.5] is not an integer");
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testObtainTaskWithCountInstanceSelection() {
    CountInstanceSelection spec = new CountInstanceSelection();
    spec.setCount(ParameterField.createValueField("2"));

    final K8sScaleStepParameter stepParameters =
        K8sScaleStepParameter.infoBuilder()
            .instanceSelection(InstanceSelectionWrapper.builder().spec(spec).type(K8sInstanceUnitType.Count).build())
            .skipSteadyStateCheck(ParameterField.createValueField(false))
            .workload(ParameterField.createValueField("Deployment/test-scale-count-deployment"))
            .build();

    final K8sExecutionPassThroughData executionPassThroughData =
        K8sExecutionPassThroughData.builder().infrastructure(infrastructureOutcome).build();
    final StepElementParameters stepElementParameters =
        StepElementParameters.builder().spec(stepParameters).timeout(ParameterField.createValueField("10m")).build();

    final TaskRequest taskRequest = TaskRequest.newBuilder().build();
    doReturn(TaskChainResponse.builder().taskRequest(taskRequest).build())
        .when(k8sStepHelper)
        .queueK8sTask(
            eq(stepElementParameters), any(K8sDeployRequest.class), eq(ambiance), eq(executionPassThroughData));

    doReturn(new ManifestsOutcome(serviceOutcome.getManifestResults()))
        .when(outcomeService)
        .resolve(ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.MANIFESTS));
    doReturn(infrastructureOutcome)
        .when(outcomeService)
        .resolve(ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.INFRASTRUCTURE_OUTCOME));
    doReturn("test-scale-count-release").when(cdStepHelper).getReleaseName(ambiance, infrastructureOutcome);
    doReturn(manifestOutcome).when(k8sStepHelper).getK8sSupportedManifestOutcome(any(Collection.class));

    scaleStep.obtainTask(ambiance, stepElementParameters, stepInputPackage);
    ArgumentCaptor<K8sScaleRequest> scaleRequestArgumentCaptor = ArgumentCaptor.forClass(K8sScaleRequest.class);

    verify(k8sStepHelper, times(1))
        .queueK8sTask(eq(stepElementParameters), scaleRequestArgumentCaptor.capture(), eq(ambiance),
            eq(executionPassThroughData));
    K8sScaleRequest scaleRequest = scaleRequestArgumentCaptor.getValue();
    assertThat(scaleRequest).isNotNull();
    assertThat(scaleRequest.getCommandName()).isEqualTo(K8S_SCALE_COMMAND_NAME);
    assertThat(scaleRequest.getTaskType()).isEqualTo(K8sTaskType.SCALE);
    assertThat(scaleRequest.getReleaseName()).isEqualTo("test-scale-count-release");
    assertThat(scaleRequest.getInstances()).isEqualTo(2);
    assertThat(scaleRequest.getWorkload()).isEqualTo("Deployment/test-scale-count-deployment");
    assertThat(scaleRequest.getTimeoutIntervalInMin()).isEqualTo(10);
    assertThat(scaleRequest.getK8sInfraDelegateConfig()).isEqualTo(infraDelegateConfig);

    // We need this null as K8sScaleStep does not depend upon Manifests
    assertThat(scaleRequest.getManifestDelegateConfig()).isNull();

    ArgumentCaptor<String> releaseNameCaptor = ArgumentCaptor.forClass(String.class);
    verify(k8sStepHelper, times(1)).publishReleaseNameStepDetails(eq(ambiance), releaseNameCaptor.capture());
    assertThat(releaseNameCaptor.getValue()).isEqualTo("test-scale-count-release");
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testObtainTaskWithPercentageInstanceSelection() {
    PercentageInstanceSelection spec = new PercentageInstanceSelection();
    spec.setPercentage(ParameterField.createValueField("80.0"));

    final K8sScaleStepParameter stepParameters =
        K8sScaleStepParameter.infoBuilder()
            .instanceSelection(
                InstanceSelectionWrapper.builder().spec(spec).type(K8sInstanceUnitType.Percentage).build())
            .skipSteadyStateCheck(ParameterField.createValueField(false))
            .workload(ParameterField.createValueField("Deployment/test-scale-percentage-deployment"))
            .build();

    final K8sExecutionPassThroughData executionPassThroughData =
        K8sExecutionPassThroughData.builder().infrastructure(infrastructureOutcome).build();
    final StepElementParameters stepElementParameters =
        StepElementParameters.builder().spec(stepParameters).timeout(ParameterField.createValueField("10m")).build();

    final TaskRequest taskRequest = TaskRequest.newBuilder().build();
    doReturn(TaskChainResponse.builder().taskRequest(taskRequest).build())
        .when(k8sStepHelper)
        .queueK8sTask(
            eq(stepElementParameters), any(K8sDeployRequest.class), eq(ambiance), eq(executionPassThroughData));

    doReturn(new ManifestsOutcome(serviceOutcome.getManifestResults()))
        .when(outcomeService)
        .resolve(ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.MANIFESTS));
    doReturn(infrastructureOutcome)
        .when(outcomeService)
        .resolve(ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.INFRASTRUCTURE_OUTCOME));
    doReturn("test-scale-percentage-release").when(cdStepHelper).getReleaseName(ambiance, infrastructureOutcome);
    doReturn(manifestOutcome).when(k8sStepHelper).getK8sSupportedManifestOutcome(any(Collection.class));

    scaleStep.obtainTask(ambiance, stepElementParameters, stepInputPackage);
    ArgumentCaptor<K8sScaleRequest> scaleRequestArgumentCaptor = ArgumentCaptor.forClass(K8sScaleRequest.class);

    verify(k8sStepHelper, times(1))
        .queueK8sTask(eq(stepElementParameters), scaleRequestArgumentCaptor.capture(), eq(ambiance),
            eq(executionPassThroughData));
    K8sScaleRequest scaleRequest = scaleRequestArgumentCaptor.getValue();
    assertThat(scaleRequest).isNotNull();
    assertThat(scaleRequest.getCommandName()).isEqualTo(K8S_SCALE_COMMAND_NAME);
    assertThat(scaleRequest.getTaskType()).isEqualTo(K8sTaskType.SCALE);
    assertThat(scaleRequest.getReleaseName()).isEqualTo("test-scale-percentage-release");
    assertThat(scaleRequest.getInstances()).isEqualTo(80);
    assertThat(scaleRequest.getWorkload()).isEqualTo("Deployment/test-scale-percentage-deployment");
    assertThat(scaleRequest.getTimeoutIntervalInMin()).isEqualTo(10);
    assertThat(scaleRequest.getK8sInfraDelegateConfig()).isEqualTo(infraDelegateConfig);

    // We need this null as K8sScaleStep does not depend upon Manifests
    assertThat(scaleRequest.getManifestDelegateConfig()).isNull();
    ArgumentCaptor<String> releaseNameCaptor = ArgumentCaptor.forClass(String.class);
    verify(k8sStepHelper, times(1)).publishReleaseNameStepDetails(eq(ambiance), releaseNameCaptor.capture());
    assertThat(releaseNameCaptor.getValue()).isEqualTo("test-scale-percentage-release");
  }

  @SneakyThrows
  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testHandleTaskResultSucceeded() {
    K8sScaleStepParameter stepParameters = K8sScaleStepParameter.infoBuilder().build();
    final StepElementParameters stepElementParameters =
        StepElementParameters.builder().spec(stepParameters).timeout(ParameterField.createValueField("10m")).build();

    K8sDeployResponse responseData =
        K8sDeployResponse.builder()
            .commandExecutionStatus(SUCCESS)
            .commandUnitsProgress(UnitProgressData.builder().build())
            .k8sNGTaskResponse(K8sScaleResponse.builder().k8sPodList(Collections.emptyList()).build())
            .build();
    StepResponse.StepOutcome stepOutcome = StepResponse.StepOutcome.builder()
                                               .name(OutcomeExpressionConstants.DEPLOYMENT_INFO_OUTCOME)
                                               .outcome(DeploymentInfoOutcome.builder().build())
                                               .build();
    doReturn(stepOutcome).when(instanceInfoService).saveServerInstancesIntoSweepingOutput(any(), any());

    StepResponse response =
        scaleStep.handleTaskResultWithSecurityContext(ambiance, stepElementParameters, () -> responseData);
    assertThat(response.getStatus()).isEqualTo(Status.SUCCEEDED);
    assertThat(response.getStepOutcomes()).contains(stepOutcome);
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testHandleTaskResultFailed() throws Exception {
    K8sScaleStepParameter stepParameters = K8sScaleStepParameter.infoBuilder().build();
    final StepElementParameters stepElementParameters =
        StepElementParameters.builder().spec(stepParameters).timeout(ParameterField.createValueField("10m")).build();

    K8sDeployResponse responseData = K8sDeployResponse.builder()
                                         .errorMessage("Execution failed.")
                                         .commandExecutionStatus(FAILURE)
                                         .commandUnitsProgress(UnitProgressData.builder().build())
                                         .build();

    StepResponse response =
        scaleStep.handleTaskResultWithSecurityContext(ambiance, stepElementParameters, () -> responseData);
    assertThat(response.getStatus()).isEqualTo(Status.FAILED);
    assertThat(response.getFailureInfo().getErrorMessage()).isEqualTo("Execution failed.");
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testGetK8sScaleStepParameter() {
    assertThat(scaleStep.getStepParametersClass()).isEqualTo(StepElementParameters.class);
  }
}
