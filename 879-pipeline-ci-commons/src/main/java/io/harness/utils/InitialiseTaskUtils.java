/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.utils;

import static io.harness.beans.outcomes.LiteEnginePodDetailsOutcome.POD_DETAILS_OUTCOME;

import io.harness.beans.outcomes.LiteEnginePodDetailsOutcome;
import io.harness.beans.sweepingoutputs.ContextElement;
import io.harness.beans.sweepingoutputs.K8PodDetails;
import io.harness.beans.sweepingoutputs.StageDetails;
import io.harness.beans.yaml.extended.infrastrucutre.Infrastructure;
import io.harness.beans.yaml.extended.infrastrucutre.K8sDirectInfraYaml;
import io.harness.beans.yaml.extended.infrastrucutre.OSType;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.ci.CIInitializeTaskParams;
import io.harness.delegate.beans.ci.k8s.CiK8sTaskResponse;
import io.harness.delegate.beans.ci.k8s.K8sTaskExecutionResponse;
import io.harness.delegate.beans.ci.k8s.PodStatus;
import io.harness.exception.ngexception.CIStageExecutionException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logstreaming.LogStreamingHelper;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.steps.StepUtils;
import io.harness.steps.container.exception.ContainerStepExecutionException;
import io.harness.steps.plugin.ContainerStepConstants;

import software.wings.beans.SerializationFormat;
import software.wings.beans.TaskType;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.LinkedHashMap;

@Singleton
public class InitialiseTaskUtils {
  @Inject ExecutionSweepingOutputService executionSweepingOutputService;

  public OSType getOS(Infrastructure infrastructure) {
    if (infrastructure.getType() != Infrastructure.Type.KUBERNETES_DIRECT) {
      return OSType.Linux;
    }

    if (((K8sDirectInfraYaml) infrastructure).getSpec() == null) {
      throw new CIStageExecutionException("Input infrastructure can not be empty");
    }

    K8sDirectInfraYaml k8sDirectInfraYaml = (K8sDirectInfraYaml) infrastructure;
    return resolveOSType(k8sDirectInfraYaml.getSpec().getOs());
  }

  private OSType resolveOSType(ParameterField<OSType> osType) {
    if (osType == null || osType.isExpression() || osType.getValue() == null) {
      return OSType.Linux;
    } else {
      return OSType.fromString(osType.fetchFinalValue().toString());
    }
  }

  public void constructStageDetails(Ambiance ambiance, String stageIdentifier, String stageName, String group) {
    StageDetails stageDetails = StageDetails.builder()
                                    .stageID(stageIdentifier)
                                    .stageRuntimeID(AmbianceUtils.obtainCurrentRuntimeId(ambiance))
                                    .accountId(AmbianceUtils.getAccountId(ambiance))
                                    .build();

    K8PodDetails k8PodDetails = K8PodDetails.builder()
                                    .stageID(stageIdentifier)
                                    .stageName(stageName)
                                    .accountId(AmbianceUtils.getAccountId(ambiance))
                                    .build();

    executionSweepingOutputService.consume(ambiance, ContextElement.podDetails, k8PodDetails, group);

    executionSweepingOutputService.consume(ambiance, ContextElement.stageDetails, stageDetails, group);
  }

  public TaskData getTaskData(CIInitializeTaskParams buildSetupTaskParams) {
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

  public String getLogPrefix(Ambiance ambiance, String lastGroup) {
    LinkedHashMap<String, String> logAbstractions = StepUtils.generateLogAbstractions(ambiance, lastGroup);
    return LogStreamingHelper.generateLogBaseKey(logAbstractions);
  }

  public void checkIfEverythingIsHealthy(K8sTaskExecutionResponse k8sTaskExecutionResponse) {
    if (!k8sTaskExecutionResponse.getCommandExecutionStatus().equals(CommandExecutionStatus.SUCCESS)) {
      throw new ContainerStepExecutionException(
          String.format("Container creation ran into error: %s", k8sTaskExecutionResponse.getErrorMessage()));
    }
    if (!k8sTaskExecutionResponse.getK8sTaskResponse().getPodStatus().getStatus().equals(PodStatus.Status.RUNNING)) {
      throw new ContainerStepExecutionException(String.format("Container creation ran into error: %s",
          k8sTaskExecutionResponse.getK8sTaskResponse().getPodStatus().getErrorMessage()));
    }
  }

  public LiteEnginePodDetailsOutcome getPodDetailsOutcome(CiK8sTaskResponse ciK8sTaskResponse) {
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

  public StepResponse handleK8sTaskExecutionResponse(K8sTaskExecutionResponse k8sTaskExecutionResponse) {
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
}
