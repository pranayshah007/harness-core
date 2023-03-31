/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.states;

import static io.harness.rule.OwnerRule.ALEKSANDAR;
import static io.harness.rule.OwnerRule.HARSH;
import static io.harness.rule.OwnerRule.SHUBHAM;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.environment.ServiceDefinitionInfo;
import io.harness.beans.steps.stepinfo.InitializeStepInfo;
import io.harness.category.element.UnitTests;
import io.harness.ci.buildstate.BuildSetupUtils;
import io.harness.ci.execution.BackgroundTaskUtility;
import io.harness.ci.executionplan.CIExecutionPlanTestHelper;
import io.harness.ci.executionplan.CIExecutionTestBase;
import io.harness.ci.integrationstage.BuildJobEnvInfoBuilder;
import io.harness.ci.integrationstage.K8InitializeServiceUtils;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.ci.k8s.CIContainerStatus;
import io.harness.delegate.beans.ci.k8s.CIK8InitializeTaskParams;
import io.harness.delegate.beans.ci.k8s.CiK8sTaskResponse;
import io.harness.delegate.beans.ci.k8s.K8sTaskExecutionResponse;
import io.harness.delegate.beans.ci.k8s.PodStatus;
import io.harness.delegate.beans.ci.vm.VmTaskExecutionResponse;
import io.harness.helper.SerializedResponseDataHelper;
import io.harness.logging.CommandExecutionStatus;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import lombok.SneakyThrows;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.CI)
public class InitializeTaskStepTest extends CIExecutionTestBase {
  @Mock private BuildSetupUtils buildSetupUtils;
  @Mock private CIDelegateTaskExecutor ciDelegateTaskExecutor;
  @Mock private ExecutionSweepingOutputService executionSweepingOutputResolver;
  @Mock private KryoSerializer kryoSerializer;
  @Mock private BuildJobEnvInfoBuilder buildJobEnvInfoBuilder;
  @Mock private SerializedResponseDataHelper serializedResponseDataHelper;
  @InjectMocks private InitializeTaskStep initializeTaskStep;
  @Inject private CIExecutionPlanTestHelper ciExecutionPlanTestHelper;
  @Mock private K8InitializeServiceUtils k8InitializeServiceUtils;
  @Mock private BackgroundTaskUtility backgroundTaskUtility;
  private Ambiance ambiance;
  private InitializeStepInfo initializeStepInfo;
  private StepElementParameters stepElementParameters;

  @Before
  public void setUp() {
    Map<String, String> setupAbstractions = new HashMap<>();
    setupAbstractions.put("accountId", "accountId");
    ambiance = Ambiance.newBuilder()
                   .putAllSetupAbstractions(setupAbstractions)
                   .addLevels(Level.newBuilder().setStepType(InitializeTaskStep.STEP_TYPE).build())
                   .build();
    initializeStepInfo = InitializeStepInfo.builder()
                             .stageElementConfig(ciExecutionPlanTestHelper.getIntegrationStageConfig())
                             .executionSource(ciExecutionPlanTestHelper.getCIExecutionArgs().getExecutionSource())
                             .executionElementConfig(ciExecutionPlanTestHelper.getExecutionElementConfig())
                             .build();
    stepElementParameters = StepElementParameters.builder()
                                .timeout(ParameterField.createValueField("10m"))
                                .name("name")
                                .spec(initializeStepInfo)
                                .build();
  }

  @SneakyThrows
  @Test
  @Owner(developers = HARSH)
  @Category(UnitTests.class)
  public void shouldHandleBufferTime() {
    TaskData taskData =
        initializeTaskStep.getTaskData(stepElementParameters, CIK8InitializeTaskParams.builder().build());
    assertThat(taskData.getTimeout()).isEqualTo(630 * 1000L);
  }

  @SneakyThrows
  @Test
  @Owner(developers = ALEKSANDAR)
  @Category(UnitTests.class)
  public void shouldHandleSuccessfulTaskResult() {
    PodStatus podStatus = PodStatus.builder().build();
    CiK8sTaskResponse taskResponse =
        CiK8sTaskResponse.builder().podName("test").podNamespace("test").podStatus(podStatus).build();
    K8sTaskExecutionResponse executionResponse = K8sTaskExecutionResponse.builder()
                                                     .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                                     .k8sTaskResponse(taskResponse)
                                                     .build();
    when(serializedResponseDataHelper.deserialize(any())).thenReturn(executionResponse);
    when(executionSweepingOutputResolver.resolveOptional(any(), any()))
        .thenReturn(OptionalSweepingOutput.builder().found(false).build());
    when(k8InitializeServiceUtils.getServiceInfos(any())).thenReturn(new ArrayList<>());
    StepResponse stepResponse = initializeTaskStep.handleTaskResultWithSecurityContext(
        ambiance, stepElementParameters, () -> executionResponse);
    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
  }

  @SneakyThrows
  @Test
  @Owner(developers = ALEKSANDAR)
  @Category(UnitTests.class)
  public void shouldHandleFailedTaskResult() {
    PodStatus podStatus = PodStatus.builder().build();
    CiK8sTaskResponse taskResponse =
        CiK8sTaskResponse.builder().podName("test").podNamespace("test").podStatus(podStatus).build();

    K8sTaskExecutionResponse executionResponse = K8sTaskExecutionResponse.builder()
                                                     .commandExecutionStatus(CommandExecutionStatus.FAILURE)
                                                     .k8sTaskResponse(taskResponse)
                                                     .build();

    when(serializedResponseDataHelper.deserialize(any())).thenReturn(executionResponse);
    when(executionSweepingOutputResolver.resolveOptional(any(), any()))
        .thenReturn(OptionalSweepingOutput.builder().found(false).build());
    when(k8InitializeServiceUtils.getServiceInfos(any())).thenReturn(new ArrayList<>());
    StepResponse stepResponse = initializeTaskStep.handleTaskResultWithSecurityContext(
        ambiance, stepElementParameters, () -> executionResponse);
    assertThat(stepResponse.getStatus()).isEqualTo(Status.FAILED);
  }

  @SneakyThrows
  @Test
  @Owner(developers = SHUBHAM)
  @Category(UnitTests.class)
  public void shouldHandleSuccessfulTaskResultWithServices() {
    String containerName = "ctr";
    String errMsg = "Terminated";
    String image = "redis:latest";
    String stepId = "cache";
    String stepName = "cache";
    CIContainerStatus ciContainerStatus = CIContainerStatus.builder()
                                              .status(CIContainerStatus.Status.ERROR)
                                              .name(containerName)
                                              .errorMsg(errMsg)
                                              .image(image)
                                              .build();
    PodStatus podStatus = PodStatus.builder().ciContainerStatusList(Arrays.asList(ciContainerStatus)).build();
    CiK8sTaskResponse taskResponse =
        CiK8sTaskResponse.builder().podName("test").podNamespace("test").podStatus(podStatus).build();

    ServiceDefinitionInfo serviceDefinitionInfo = ServiceDefinitionInfo.builder()
                                                      .identifier(stepId)
                                                      .name(stepName)
                                                      .containerName(containerName)
                                                      .image(image)
                                                      .build();
    K8sTaskExecutionResponse executionResponse = K8sTaskExecutionResponse.builder()
                                                     .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                                     .k8sTaskResponse(taskResponse)
                                                     .build();

    when(serializedResponseDataHelper.deserialize(any())).thenReturn(executionResponse);
    when(executionSweepingOutputResolver.resolveOptional(any(), any()))
        .thenReturn(OptionalSweepingOutput.builder().found(false).build());
    when(k8InitializeServiceUtils.getServiceInfos(any())).thenReturn(Arrays.asList(serviceDefinitionInfo));
    StepResponse stepResponse = initializeTaskStep.handleTaskResultWithSecurityContext(
        ambiance, stepElementParameters, () -> executionResponse);
    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
  }

  @SneakyThrows
  @Test
  @Owner(developers = SHUBHAM)
  @Category(UnitTests.class)
  public void shouldHandleSuccessfulTaskResultWithServicesNoPodStatus() {
    String containerName = "ctr";
    String stepId = "cache";
    String stepName = "cache";
    String image = "redis";
    PodStatus podStatus = PodStatus.builder().build();
    CiK8sTaskResponse taskResponse =
        CiK8sTaskResponse.builder().podNamespace("test").podName("test").podStatus(podStatus).build();

    ServiceDefinitionInfo serviceDefinitionInfo = ServiceDefinitionInfo.builder()
                                                      .identifier(stepId)
                                                      .name(stepName)
                                                      .containerName(containerName)
                                                      .image(image)
                                                      .build();

    K8sTaskExecutionResponse executionResponse = K8sTaskExecutionResponse.builder()
                                                     .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                                     .k8sTaskResponse(taskResponse)
                                                     .build();

    when(serializedResponseDataHelper.deserialize(any())).thenReturn(executionResponse);
    when(executionSweepingOutputResolver.resolveOptional(any(), any()))
        .thenReturn(OptionalSweepingOutput.builder().found(false).build());
    when(k8InitializeServiceUtils.getServiceInfos(any())).thenReturn(Arrays.asList(serviceDefinitionInfo));
    StepResponse stepResponse = initializeTaskStep.handleTaskResultWithSecurityContext(
        ambiance, stepElementParameters, () -> executionResponse);
    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
  }

  @SneakyThrows
  @Test
  @Owner(developers = SHUBHAM)
  @Category(UnitTests.class)
  public void handleVmTaskSuccessWithSecurityContext() {
    VmTaskExecutionResponse executionResponse =
        VmTaskExecutionResponse.builder().commandExecutionStatus(CommandExecutionStatus.SUCCESS).build();
    when(executionSweepingOutputResolver.resolveOptional(any(), any()))
        .thenReturn(OptionalSweepingOutput.builder().found(false).build());
    when(serializedResponseDataHelper.deserialize(any())).thenReturn(executionResponse);
    StepResponse stepResponse = initializeTaskStep.handleTaskResultWithSecurityContext(
        ambiance, stepElementParameters, () -> executionResponse);
    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
  }

  @SneakyThrows
  @Test
  @Owner(developers = SHUBHAM)
  @Category(UnitTests.class)
  public void handleVmTaskFailureWithSecurityContext() {
    VmTaskExecutionResponse executionResponse =
        VmTaskExecutionResponse.builder().commandExecutionStatus(CommandExecutionStatus.FAILURE).build();

    when(serializedResponseDataHelper.deserialize(any())).thenReturn(executionResponse);
    when(executionSweepingOutputResolver.resolveOptional(any(), any()))
        .thenReturn(OptionalSweepingOutput.builder().found(false).build());
    StepResponse stepResponse = initializeTaskStep.handleTaskResultWithSecurityContext(
        ambiance, stepElementParameters, () -> executionResponse);
    assertThat(stepResponse.getStatus()).isEqualTo(Status.FAILED);
  }
}
