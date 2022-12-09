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
import io.harness.pms.contracts.execution.tasks.TaskCategory;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.resolver.outcome.OutcomeService;
import io.harness.pms.sdk.core.steps.executables.TaskChainResponse;
import io.harness.pms.sdk.core.steps.io.PassThroughData;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepParameters;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.steps.StepUtils;
import io.harness.steps.container.exception.ContainerStepExecutionException;
import io.harness.steps.container.execution.ContainerRunStepHelper;
import io.harness.steps.executable.TaskChainExecutableWithRbac;
import io.harness.steps.plugin.ContainerStepInfo;
import io.harness.steps.plugin.ContainerStepPassThroughData;
import io.harness.supplier.ThrowingSupplier;
import io.harness.yaml.core.timeout.Timeout;

import software.wings.beans.SerializationFormat;
import software.wings.beans.TaskType;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public class ContainerStep implements TaskChainExecutableWithRbac {
  private final ContainerStepInitHelper containerStepInitHelper;
  private final KryoSerializer kryoSerializer;
  private final ContainerRunStepHelper containerRunStepHelper;
  private final OutcomeService outcomeService;

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
  public void handleAbort(Ambiance ambiance, StepParameters stepParameters, Object executableResponse) {}

  @Override
  public void validateResources(Ambiance ambiance, StepParameters stepParameters) {}

  @Override
  public TaskChainResponse startChainLinkAfterRbac(
      Ambiance ambiance, StepParameters stepParameters, StepInputPackage inputPackage) {
    StepElementParameters stepParameters1 = (StepElementParameters) stepParameters;
    ContainerStepInfo containerStepInfo = (ContainerStepInfo) stepParameters1.getSpec();
    String logPrefix = getLogPrefix(ambiance);

    CIInitializeTaskParams buildSetupTaskParams =
        containerStepInitHelper.getK8InitializeTaskParams(containerStepInfo, ambiance, logPrefix);

    String stageId = ambiance.getStageExecutionId();
    List<TaskSelector> taskSelectors = new ArrayList<>();

    TaskRequest taskRequest = StepUtils.prepareTaskRequest(ambiance, getTaskData(stepParameters1, buildSetupTaskParams),
        kryoSerializer, TaskCategory.DELEGATE_TASK_V2, Collections.emptyList(), true, null, taskSelectors,
        Scope.PROJECT, EnvironmentType.ALL, false, new ArrayList<>(), false, stageId);
    return TaskChainResponse.builder()
        .taskRequest(taskRequest)
        .passThroughData(ContainerStepPassThroughData.builder().build())
        .chainEnd(false)
        .build();
  }

  @Override
  public StepResponse finalizeExecutionWithSecurityContext(Ambiance ambiance, StepParameters stepParameters,
      PassThroughData passThroughData, ThrowingSupplier responseDataSupplier) throws Exception {
    return null;
  }

  @Override
  public TaskChainResponse executeNextLinkWithSecurityContext(Ambiance ambiance, StepParameters stepParameters,
      StepInputPackage inputPackage, PassThroughData passThroughData, ThrowingSupplier responseSupplier)
      throws Exception {
    StepElementParameters stepParameters1 = (StepElementParameters) stepParameters;
    ContainerStepInfo containerStepInfo = (ContainerStepInfo) stepParameters1.getSpec();
    Object o = responseSupplier.get();
    K8sTaskExecutionResponse k8sTaskExecutionResponse = (K8sTaskExecutionResponse) o;

    checkIfEverythingIsHealthy(k8sTaskExecutionResponse);

    long timeout = Timeout.fromString((String) ((StepElementParameters) stepParameters).getTimeout().fetchFinalValue())
                       .getTimeoutInMillis();
    LiteEnginePodDetailsOutcome liteEnginePodDetailsOutcome =
        getPodDetailsOutcome(k8sTaskExecutionResponse.getK8sTaskResponse());

    outcomeService.consume(ambiance, POD_DETAILS_OUTCOME, liteEnginePodDetailsOutcome, StepCategory.STEP.name());

    TaskData runStepTaskData = containerRunStepHelper.getRunStepTask(
        ambiance, containerStepInfo, AmbianceUtils.getAccountId(ambiance), getLogPrefix(ambiance), timeout);
    String stageId = ambiance.getStageExecutionId();

    TaskRequest taskRequest = StepUtils.prepareTaskRequest(ambiance, runStepTaskData, kryoSerializer,
        TaskCategory.DELEGATE_TASK_V2, Collections.emptyList(), true, null, new ArrayList<>(), Scope.PROJECT,
        EnvironmentType.ALL, false, new ArrayList<>(), false, stageId);

    return TaskChainResponse.builder()
        .chainEnd(true)
        .taskRequest(taskRequest)
        .passThroughData(ContainerStepPassThroughData.builder().build())
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

  private LiteEnginePodDetailsOutcome getPodDetailsOutcome(CiK8sTaskResponse ciK8sTaskResponse) {
    if (ciK8sTaskResponse != null && ciK8sTaskResponse.getPodStatus() != null) {
      String ip = ciK8sTaskResponse.getPodStatus().getIp();
      String namespace = ciK8sTaskResponse.getPodNamespace();
      return LiteEnginePodDetailsOutcome.builder().ipAddress(ip).namespace(namespace).build();
    }
    return null;
  }
}
