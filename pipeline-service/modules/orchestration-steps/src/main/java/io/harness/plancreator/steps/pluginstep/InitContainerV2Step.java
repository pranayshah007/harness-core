/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.plancreator.steps.pluginstep;

import static io.harness.beans.outcomes.LiteEnginePodDetailsOutcome.POD_DETAILS_OUTCOME;
import static io.harness.beans.sweepingoutputs.PodCleanupDetails.CLEANUP_DETAILS;
import static io.harness.beans.sweepingoutputs.StageInfraDetails.STAGE_INFRA_DETAILS;
import static io.harness.ci.commonconstants.CICommonPodConstants.POD_NAME_PREFIX;

import static java.lang.Character.toLowerCase;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.CharUtils.isAsciiAlphanumeric;

import io.harness.beans.EnvironmentType;
import io.harness.beans.outcomes.LiteEnginePodDetailsOutcome;
import io.harness.beans.sweepingoutputs.ContextElement;
import io.harness.beans.sweepingoutputs.K8PodDetails;
import io.harness.beans.sweepingoutputs.K8StageInfraDetails;
import io.harness.beans.sweepingoutputs.PodCleanupDetails;
import io.harness.beans.sweepingoutputs.StageDetails;
import io.harness.beans.yaml.extended.infrastrucutre.Infrastructure;
import io.harness.delegate.TaskSelector;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.ci.CIInitializeTaskParams;
import io.harness.delegate.beans.ci.k8s.CiK8sTaskResponse;
import io.harness.delegate.beans.ci.k8s.K8sTaskExecutionResponse;
import io.harness.delegate.beans.ci.k8s.PodStatus;
import io.harness.delegate.beans.ci.pod.CIK8ContainerParams;
import io.harness.encryption.Scope;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logstreaming.LogStreamingHelper;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.tasks.TaskCategory;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.contracts.plan.PluginCreationResponse;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.plan.creation.yaml.StepOutcomeGroup;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.StepUtils;
import io.harness.steps.container.ContainerStepInitHelper;
import io.harness.steps.container.exception.ContainerStepExecutionException;
import io.harness.steps.executable.TaskExecutableWithRbac;
import io.harness.steps.plugin.ContainerStepConstants;
import io.harness.steps.plugin.InitContainerV2StepInfo;
import io.harness.steps.plugin.StepInfo;
import io.harness.steps.plugin.infrastructure.ContainerK8sInfra;
import io.harness.supplier.ThrowingSupplier;
import io.harness.yaml.extended.ci.codebase.CodeBase;

import software.wings.beans.SerializationFormat;
import software.wings.beans.TaskType;

import com.google.inject.Inject;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.tuple.Pair;

public class InitContainerV2Step implements TaskExecutableWithRbac<InitContainerV2StepInfo, K8sTaskExecutionResponse> {
  @Inject KryoSerializer kryoSerializer;
  @Inject ContainerStepInitHelper containerStepInitHelper;
  @Inject ContainerStepV2PluginProvider containerStepV2PluginProvider;
  @Inject ExecutionSweepingOutputService executionSweepingOutputService;

  @Override
  public Class<InitContainerV2StepInfo> getStepParametersClass() {
    return InitContainerV2StepInfo.class;
  }

  @Override
  public void validateResources(Ambiance ambiance, InitContainerV2StepInfo stepParameters) {
    // todo :implement
  }

  @Override
  public StepResponse handleTaskResultWithSecurityContext(Ambiance ambiance, InitContainerV2StepInfo stepParameters,
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

  private LiteEnginePodDetailsOutcome getPodDetailsOutcome(CiK8sTaskResponse ciK8sTaskResponse) {
    if (ciK8sTaskResponse != null) {
      String ip = ciK8sTaskResponse.getPodStatus().getIp();
      String namespace = ciK8sTaskResponse.getPodNamespace();
      return LiteEnginePodDetailsOutcome.builder().ipAddress(ip).namespace(namespace).build();
    }
    return null;
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

  @Override
  public TaskRequest obtainTaskAfterRbac(
      Ambiance ambiance, InitContainerV2StepInfo stepParameters, StepInputPackage inputPackage) {
    String logPrefix = getLogPrefix(ambiance);

    Map<StepInfo, PluginCreationResponse> pluginsData =
        containerStepV2PluginProvider.getPluginsData(stepParameters, ambiance);
    stepParameters.setPluginsData(pluginsData);
    CIInitializeTaskParams buildSetupTaskParams =
        containerStepInitHelper.getK8InitializeTaskParams(stepParameters, ambiance, logPrefix);

    String stageId = ambiance.getStageExecutionId();
    List<TaskSelector> taskSelectors = new ArrayList<>();

    StageDetails stageDetails = StageDetails.builder()
                                    .stageID(stepParameters.getIdentifier())
                                    .stageRuntimeID(AmbianceUtils.obtainCurrentRuntimeId(ambiance))
                                    .accountId(AmbianceUtils.getAccountId(ambiance))
                                    .build();

    K8PodDetails k8PodDetails = K8PodDetails.builder()
                                    .stageID(stepParameters.getIdentifier())
                                    .stageName(stepParameters.getName())
                                    .accountId(AmbianceUtils.getAccountId(ambiance))
                                    .build();

    executionSweepingOutputService.consume(
        ambiance, ContextElement.podDetails, k8PodDetails, StepOutcomeGroup.STEP_GROUP.name());

    executionSweepingOutputService.consume(
        ambiance, ContextElement.stageDetails, stageDetails, StepOutcomeGroup.STEP_GROUP.name());

    TaskData taskData = getTaskData(stepParameters, buildSetupTaskParams);
    return StepUtils.prepareTaskRequest(ambiance, taskData, kryoSerializer, TaskCategory.DELEGATE_TASK_V2, null, true,
        TaskType.valueOf(taskData.getTaskType()).getDisplayName(), taskSelectors, Scope.PROJECT, EnvironmentType.ALL,
        false, new ArrayList<>(), false, stageId);
  }

  private String getLogPrefix(Ambiance ambiance) {
    LinkedHashMap<String, String> logAbstractions = StepUtils.generateLogAbstractions(ambiance, "STEP");
    return LogStreamingHelper.generateLogBaseKey(logAbstractions);
  }

  public TaskData getTaskData(
      InitContainerV2StepInfo stepElementParameters, CIInitializeTaskParams buildSetupTaskParams) {
    long timeout = ContainerStepConstants.DEFAULT_TIMEOUT;
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
