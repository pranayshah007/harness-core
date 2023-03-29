/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.container;

import static io.harness.beans.outcomes.LiteEnginePodDetailsOutcome.POD_DETAILS_OUTCOME;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EnvironmentType;
import io.harness.beans.outcomes.LiteEnginePodDetailsOutcome;
import io.harness.delegate.TaskSelector;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.ci.CIInitializeTaskParams;
import io.harness.delegate.beans.ci.k8s.CiK8sTaskResponse;
import io.harness.delegate.beans.ci.k8s.K8sTaskExecutionResponse;
import io.harness.delegate.beans.ci.k8s.PodStatus;
import io.harness.encryption.Scope;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logstreaming.LogStreamingHelper;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.TaskChainExecutableResponse;
import io.harness.pms.contracts.execution.tasks.TaskCategory;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.resolver.outcome.OutcomeService;
import io.harness.pms.sdk.core.steps.executables.TaskChainResponse;
import io.harness.pms.sdk.core.steps.io.PassThroughData;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.steps.StepUtils;
import io.harness.steps.container.exception.ContainerStepExecutionException;
import io.harness.steps.container.execution.ContainerRunStepHelper;
import io.harness.steps.container.execution.ContainerStepCleanupHelper;
import io.harness.steps.container.execution.ContainerStepExecutionResponseHelper;
import io.harness.steps.container.execution.ContainerStepRbacHelper;
import io.harness.steps.executable.TaskChainExecutableWithRbac;
import io.harness.steps.plugin.ContainerStepInfo;
import io.harness.steps.plugin.ContainerStepPassThroughData;
import io.harness.supplier.ThrowingSupplier;
import io.harness.tasks.ResponseData;
import io.harness.yaml.core.timeout.Timeout;

import software.wings.beans.SerializationFormat;
import software.wings.beans.TaskType;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public class ContainerStep implements TaskChainExecutableWithRbac<StepElementParameters> {
  private final ContainerStepInitHelper containerStepInitHelper;
  private final KryoSerializer kryoSerializer;
  private final ContainerRunStepHelper containerRunStepHelper;
  private final OutcomeService outcomeService;
  private final ContainerStepCleanupHelper containerStepCleanupHelper;
  private final ContainerStepRbacHelper containerStepRbacHelper;
  private final ContainerStepExecutionResponseHelper executionResponseHelper;

  public static final StepType STEP_TYPE = StepSpecTypeConstants.CONTAINER_STEP_TYPE;

  public TaskData getTaskData(StepElementParameters stepNode, CIInitializeTaskParams buildSetupTaskParams) {
    long timeout = Timeout.fromString((String) stepNode.getTimeout().fetchFinalValue()).getTimeoutInMillis();
    SerializationFormat serializationFormat = SerializationFormat.KRYO;
    String taskType = TaskType.CONTAINER_INITIALIZATION.name();

    return TaskData.builder()
        .async(true)
        .timeout(timeout)
        .taskType(taskType)
        .serializationFormat(serializationFormat)
        .parameters(new Object[] {buildSetupTaskParams})
        .build();
  }

  @Override
  public Class<StepElementParameters> getStepParametersClass() {
    return StepElementParameters.class;
  }

  private String getLogPrefix(Ambiance ambiance) {
    LinkedHashMap<String, String> logAbstractions = StepUtils.generateLogAbstractions(ambiance, "STEP");
    return LogStreamingHelper.generateLogBaseKey(logAbstractions);
  }

  @Override
  public void handleAbort(
      Ambiance ambiance, StepElementParameters stepParameters, TaskChainExecutableResponse executableResponse) {
    containerStepCleanupHelper.sendCleanupRequest(ambiance);
  }

  @Override
  public void validateResources(Ambiance ambiance, StepElementParameters stepParameters) {
    ContainerStepInfo containerStepInfo = (ContainerStepInfo) stepParameters.getSpec();
    containerStepRbacHelper.validateResources(containerStepInfo, ambiance);
  }

  @Override
  public TaskChainResponse startChainLinkAfterRbac(
      Ambiance ambiance, StepElementParameters stepParameters, StepInputPackage inputPackage) {
    ContainerStepInfo containerStepInfo = (ContainerStepInfo) stepParameters.getSpec();
    String logPrefix = getLogPrefix(ambiance);

    CIInitializeTaskParams buildSetupTaskParams =
        containerStepInitHelper.getK8InitializeTaskParams(containerStepInfo, ambiance, logPrefix);

    String stageId = ambiance.getStageExecutionId();
    List<TaskSelector> taskSelectors = new ArrayList<>();

    TaskData taskData = getTaskData(stepParameters, buildSetupTaskParams);
    TaskRequest taskRequest = StepUtils.prepareTaskRequest(ambiance, taskData, kryoSerializer,
        TaskCategory.DELEGATE_TASK_V2, null, true, TaskType.valueOf(taskData.getTaskType()).getDisplayName(),
        taskSelectors, Scope.PROJECT, EnvironmentType.ALL, false, new ArrayList<>(), false, stageId);

    return TaskChainResponse.builder()
        .taskRequest(taskRequest)
        .passThroughData(ContainerStepPassThroughData.builder().initStepStartTime(System.currentTimeMillis()).build())
        .chainEnd(false)
        .build();
  }

  @Override
  public StepResponse finalizeExecutionWithSecurityContext(Ambiance ambiance, StepElementParameters stepParameters,
      PassThroughData passThroughData, ThrowingSupplier<ResponseData> responseDataSupplier) throws Exception {
    containerStepCleanupHelper.sendCleanupRequest(ambiance);
    ResponseData responseData = responseDataSupplier.get();
    executionResponseHelper.finalizeStepResponse(ambiance, stepParameters, responseData, null);
    return StepResponse.builder().status(Status.SUCCEEDED).build();
  }

  @Override
  public TaskChainResponse executeNextLinkWithSecurityContext(Ambiance ambiance, StepElementParameters stepParameters,
      StepInputPackage inputPackage, PassThroughData passThroughData, ThrowingSupplier<ResponseData> responseSupplier)
      throws Exception {
    ContainerStepInfo containerStepInfo = (ContainerStepInfo) stepParameters.getSpec();
    ResponseData response = responseSupplier.get();
    K8sTaskExecutionResponse k8sTaskExecutionResponse = (K8sTaskExecutionResponse) response;

    checkIfEverythingIsHealthy(k8sTaskExecutionResponse);

    long timeoutForDelegateTask =
        getTimeoutForDelegateTask(stepParameters, (ContainerStepPassThroughData) passThroughData);

    LiteEnginePodDetailsOutcome liteEnginePodDetailsOutcome =
        getPodDetailsOutcome(k8sTaskExecutionResponse.getK8sTaskResponse());

    outcomeService.consume(ambiance, POD_DETAILS_OUTCOME, liteEnginePodDetailsOutcome, StepCategory.STEP.name());

    TaskData runStepTaskData = containerRunStepHelper.getRunStepTask(ambiance, containerStepInfo,
        AmbianceUtils.getAccountId(ambiance), getLogPrefix(ambiance), timeoutForDelegateTask, null);
    String stageId = ambiance.getStageExecutionId();

    TaskRequest taskRequest = StepUtils.prepareTaskRequest(ambiance, runStepTaskData, kryoSerializer,
        TaskCategory.DELEGATE_TASK_V2, null, true, TaskType.valueOf(runStepTaskData.getTaskType()).getDisplayName(),
        new ArrayList<>(), Scope.PROJECT, EnvironmentType.ALL, false, new ArrayList<>(), false, stageId);
    return TaskChainResponse.builder()
        .chainEnd(true)
        .taskRequest(taskRequest)
        .passThroughData(ContainerStepPassThroughData.builder().build())
        .build();
  }

  private long getTimeoutForDelegateTask(
      StepElementParameters stepParameters, ContainerStepPassThroughData passThroughData) {
    long lastStepStartTime = passThroughData.getInitStepStartTime();
    long currentTime = System.currentTimeMillis();
    long timeoutInConfig =
        Timeout.fromString((String) stepParameters.getTimeout().fetchFinalValue()).getTimeoutInMillis();
    // adding buffer of 5 secs so that delegate task times out before step times out.
    return timeoutInConfig - (currentTime - lastStepStartTime - 5000);
  }

  private void checkIfEverythingIsHealthy(K8sTaskExecutionResponse k8sTaskExecutionResponse) {
    if (!k8sTaskExecutionResponse.getCommandExecutionStatus().equals(CommandExecutionStatus.SUCCESS)) {
      throw new ContainerStepExecutionException(
          String.format("Container creation ran into error: %s", k8sTaskExecutionResponse.getErrorMessage()));
    }
    if (!k8sTaskExecutionResponse.getK8sTaskResponse().getPodStatus().getStatus().equals(PodStatus.Status.RUNNING)) {
      throw new ContainerStepExecutionException(String.format("Container creation ran into error: %s",
          k8sTaskExecutionResponse.getK8sTaskResponse().getPodStatus().getErrorMessage()));
    }
  }

  private LiteEnginePodDetailsOutcome getPodDetailsOutcome(CiK8sTaskResponse ciK8sTaskResponse) {
    if (ciK8sTaskResponse != null) {
      String ip = ciK8sTaskResponse.getPodStatus().getIp();
      String namespace = ciK8sTaskResponse.getPodNamespace();
      return LiteEnginePodDetailsOutcome.builder().ipAddress(ip).namespace(namespace).build();
    }
    return null;
  }
}
