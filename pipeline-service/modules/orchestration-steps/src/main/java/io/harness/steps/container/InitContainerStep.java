/*
 * Copyright 2023 Harness Inc. All rights reserved.
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
import io.harness.pms.contracts.execution.tasks.TaskCategory;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.steps.StepUtils;
import io.harness.steps.container.exception.ContainerStepExecutionException;
import io.harness.steps.container.execution.ContainerStepCleanupHelper;
import io.harness.steps.container.execution.ContainerStepRbacHelper;
import io.harness.steps.executable.TaskExecutableWithRbac;
import io.harness.steps.plugin.ContainerStepInfo;
import io.harness.steps.plugin.ContainerStepSpec;
import io.harness.supplier.ThrowingSupplier;
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
public class InitContainerStep implements TaskExecutableWithRbac<StepElementParameters, K8sTaskExecutionResponse> {
  public static final StepType STEP_TYPE = StepSpecTypeConstants.INIT_CONTAINER_STEP_TYPE;

  private final ContainerStepCleanupHelper containerStepCleanupHelper;
  private final ContainerStepInitHelper containerStepInitHelper;
  private final ContainerStepRbacHelper containerStepRbacHelper;
  private final KryoSerializer kryoSerializer;
  private final ExecutionSweepingOutputService executionSweepingOutputService;

  @Override
  public void validateResources(Ambiance ambiance, StepElementParameters stepParameters) {
    ContainerStepInfo stepParameter = (ContainerStepInfo) stepParameters.getSpec();
    containerStepRbacHelper.validateResources(stepParameter, ambiance);
  }

  @Override
  public StepResponse handleTaskResultWithSecurityContext(Ambiance ambiance, StepElementParameters stepParameters,
      ThrowingSupplier<K8sTaskExecutionResponse> responseDataSupplier) throws Exception {
    K8sTaskExecutionResponse k8sTaskExecutionResponse = responseDataSupplier.get();
    CommandExecutionStatus commandExecutionStatus = k8sTaskExecutionResponse.getCommandExecutionStatus();
    Status status = getStatus(commandExecutionStatus);
    checkIfEverythingIsHealthy(k8sTaskExecutionResponse);

    return StepResponse.builder()
        .status(status)
        .stepOutcome(StepResponse.StepOutcome.builder()
                         .name(POD_DETAILS_OUTCOME)
                         .outcome(getPodDetailsOutcome(k8sTaskExecutionResponse.getK8sTaskResponse()))
                         .group(StepCategory.STEP_GROUP.name())
                         .build())
        .build();
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
  private Status getStatus(CommandExecutionStatus commandExecutionStatus) {
    Status status;
    if (commandExecutionStatus == CommandExecutionStatus.SUCCESS) {
      status = Status.SUCCEEDED;
    } else {
      status = Status.FAILED;
    }
    return status;
  }

  private LiteEnginePodDetailsOutcome getPodDetailsOutcome(CiK8sTaskResponse ciK8sTaskResponse) {
    if (ciK8sTaskResponse != null) {
      String ip = ciK8sTaskResponse.getPodStatus().getIp();
      String namespace = ciK8sTaskResponse.getPodNamespace();
      return LiteEnginePodDetailsOutcome.builder().ipAddress(ip).namespace(namespace).build();
    }
    return null;
  }

  @Override
  public TaskRequest obtainTaskAfterRbac(
      Ambiance ambiance, StepElementParameters stepElementParameters, StepInputPackage inputPackage) {
    String logPrefix = getLogPrefix(ambiance);
    ContainerStepSpec containerStepInfo = (ContainerStepSpec) stepElementParameters.getSpec();
    CIInitializeTaskParams buildSetupTaskParams =
        containerStepInitHelper.getK8InitializeTaskParams(containerStepInfo, ambiance, logPrefix);
    String stageId = ambiance.getStageExecutionId();
    List<TaskSelector> taskSelectors = new ArrayList<>();

    TaskData taskData = getTaskData(stepElementParameters, buildSetupTaskParams);
    return StepUtils.prepareTaskRequest(ambiance, taskData, kryoSerializer, TaskCategory.DELEGATE_TASK_V2, null, true,
        TaskType.valueOf(taskData.getTaskType()).getDisplayName(), taskSelectors, Scope.PROJECT, EnvironmentType.ALL,
        false, new ArrayList<>(), false, stageId);
  }

  @Override
  public Class<StepElementParameters> getStepParametersClass() {
    return StepElementParameters.class;
  }

  private String getLogPrefix(Ambiance ambiance) {
    LinkedHashMap<String, String> logAbstractions = StepUtils.generateLogAbstractions(ambiance, "STEP");
    return LogStreamingHelper.generateLogBaseKey(logAbstractions);
  }

  public TaskData getTaskData(
      StepElementParameters stepElementParameters, CIInitializeTaskParams buildSetupTaskParams) {
    long timeout =
        Timeout.fromString((String) stepElementParameters.getTimeout().fetchFinalValue()).getTimeoutInMillis();
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
}
