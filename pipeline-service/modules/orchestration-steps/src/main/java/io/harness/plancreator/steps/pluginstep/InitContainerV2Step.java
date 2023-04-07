/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.plancreator.steps.pluginstep;

import io.harness.beans.EnvironmentType;
import io.harness.delegate.TaskSelector;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.ci.CIInitializeTaskParams;
import io.harness.delegate.beans.ci.k8s.K8sTaskExecutionResponse;
import io.harness.encryption.Scope;
import io.harness.logstreaming.LogStreamingHelper;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.tasks.TaskCategory;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.contracts.plan.PluginCreationResponse;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.StepUtils;
import io.harness.steps.container.ContainerStepInitHelper;
import io.harness.steps.executable.TaskExecutableWithRbac;
import io.harness.steps.plugin.ContainerStepConstants;
import io.harness.steps.plugin.InitContainerV2StepInfo;
import io.harness.supplier.ThrowingSupplier;

import software.wings.beans.SerializationFormat;
import software.wings.beans.TaskType;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class InitContainerV2Step implements TaskExecutableWithRbac<InitContainerV2StepInfo, K8sTaskExecutionResponse> {
  @Inject KryoSerializer kryoSerializer;
  @Inject ContainerStepInitHelper containerStepInitHelper;
  @Inject ContainerStepV2PluginProvider containerStepV2PluginProvider;

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
    return null;
  }

  @Override
  public TaskRequest obtainTaskAfterRbac(
      Ambiance ambiance, InitContainerV2StepInfo stepParameters, StepInputPackage inputPackage) {
    String logPrefix = getLogPrefix(ambiance);

    Map<String, PluginCreationResponse> pluginsData =
        containerStepV2PluginProvider.getPluginsData(stepParameters, ambiance);
    stepParameters.setPluginsData(pluginsData);
    CIInitializeTaskParams buildSetupTaskParams =
        containerStepInitHelper.getK8InitializeTaskParams(stepParameters, ambiance, logPrefix);

    String stageId = ambiance.getStageExecutionId();
    List<TaskSelector> taskSelectors = new ArrayList<>();

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
