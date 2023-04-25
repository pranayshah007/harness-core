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
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.tasks.TaskCategory;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.contracts.plan.PluginCreationResponse;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.sdk.core.plan.creation.yaml.StepOutcomeGroup;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.StepUtils;
import io.harness.steps.container.ContainerStepInitHelper;
import io.harness.steps.container.execution.ContainerExecutionConfig;
import io.harness.steps.container.execution.ContainerStepRbacHelper;
import io.harness.steps.executable.TaskExecutableWithRbac;
import io.harness.steps.plugin.ContainerStepConstants;
import io.harness.steps.plugin.InitContainerV2StepInfo;
import io.harness.steps.plugin.StepInfo;
import io.harness.supplier.ThrowingSupplier;
import io.harness.utils.InitialiseTaskUtils;

import software.wings.beans.TaskType;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class InitContainerV2Step implements TaskExecutableWithRbac<InitContainerV2StepInfo, K8sTaskExecutionResponse> {
  @Inject KryoSerializer kryoSerializer;
  @Inject ContainerStepInitHelper containerStepInitHelper;
  @Inject ContainerStepV2PluginProvider containerStepV2PluginProvider;
  @Inject ContainerStepRbacHelper containerStepRbacHelper;
  @Inject ContainerExecutionConfig containerExecutionConfig;
  @Inject ExecutionSweepingOutputService executionSweepingOutputService;
  @Inject InitialiseTaskUtils initialiseTaskUtils;

  @Override
  public Class<InitContainerV2StepInfo> getStepParametersClass() {
    return InitContainerV2StepInfo.class;
  }

  @Override
  public void validateResources(Ambiance ambiance, InitContainerV2StepInfo stepParameters) {
    containerStepRbacHelper.validateResources(stepParameters, ambiance);
  }

  @Override
  public StepResponse handleTaskResultWithSecurityContext(Ambiance ambiance, InitContainerV2StepInfo stepParameters,
      ThrowingSupplier<K8sTaskExecutionResponse> responseDataSupplier) throws Exception {
    return initialiseTaskUtils.handleK8sTaskExecutionResponse(responseDataSupplier.get());
  }

  @Override
  public TaskRequest obtainTaskAfterRbac(
      Ambiance ambiance, InitContainerV2StepInfo stepParameters, StepInputPackage inputPackage) {
    String logPrefix = initialiseTaskUtils.getLogPrefix(ambiance, "STEP");

    Map<StepInfo, PluginCreationResponse> pluginsData =
        containerStepV2PluginProvider.getPluginsData(stepParameters, ambiance);
    stepParameters.setPluginsData(pluginsData);
    CIInitializeTaskParams buildSetupTaskParams = containerStepInitHelper.getK8InitializeTaskParams(
        stepParameters, ambiance, logPrefix, stepParameters.getStepGroupIdentifier());

    String stageId = ambiance.getStageExecutionId();
    List<TaskSelector> taskSelectors = new ArrayList<>();
    consumeExecutionConfig(ambiance);
    initialiseTaskUtils.constructStageDetails(
        ambiance, stepParameters.getIdentifier(), stepParameters.getName(), StepOutcomeGroup.STEP_GROUP.name());

    TaskData taskData = initialiseTaskUtils.getTaskData(buildSetupTaskParams);
    return StepUtils.prepareTaskRequest(ambiance, taskData, kryoSerializer, TaskCategory.DELEGATE_TASK_V2, null, true,
        TaskType.valueOf(taskData.getTaskType()).getDisplayName(), taskSelectors, Scope.PROJECT, EnvironmentType.ALL,
        false, new ArrayList<>(), false, stageId);
  }
  private void consumeExecutionConfig(Ambiance ambiance) {
    executionSweepingOutputService.consume(ambiance, ContainerStepConstants.CONTAINER_EXECUTION_CONFIG,
        containerExecutionConfig, StepCategory.STEP_GROUP.name());
  }
}
