/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.container.execution;

import static io.harness.beans.sweepingoutputs.ContainerPortDetails.PORT_DETAILS;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.LITE_ENGINE_PORT;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.TMP_PATH;
import static io.harness.steps.container.ContainerStepInitHelper.getKubernetesStandardPodName;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.outcomes.LiteEnginePodDetailsOutcome;
import io.harness.beans.sweepingoutputs.ContainerPortDetails;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.ci.k8s.CIK8ExecuteStepTaskParams;
import io.harness.delegate.beans.ci.k8s.K8sTaskExecutionResponse;
import io.harness.helper.SerializedResponseDataHelper;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logstreaming.LogStreamingHelper;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outcome.OutcomeService;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.product.ci.engine.proto.ExecuteStepRequest;
import io.harness.product.ci.engine.proto.UnitStep;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.StepUtils;
import io.harness.steps.container.exception.ContainerStepExecutionException;
import io.harness.steps.container.execution.plugin.PluginStepSerializer;
import io.harness.tasks.BinaryResponseData;
import io.harness.tasks.ResponseData;
import io.harness.waiter.WaitNotifyEngine;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public class ContainerStepBaseHelper {
  @Inject private SerializedResponseDataHelper serializedResponseDataHelper;
  @Inject @Named("referenceFalseKryoSerializer") private KryoSerializer referenceFalseKryoSerializer;
  @Inject private WaitNotifyEngine waitNotifyEngine;
  @Inject OutcomeService outcomeService;
  @Inject ContainerExecutionConfig containerExecutionConfig;
  @Inject PluginStepSerializer pluginStepSerializer;
  @Inject ContainerDelegateTaskHelper containerDelegateTaskHelper;

  @Inject ExecutionSweepingOutputService executionSweepingOutputService;

  public void handleForCallbackId(Ambiance ambiance, StepElementParameters containerStepInfo,
      List<String> allCallbackIds, String callbackId, ResponseData responseData) {
    responseData = serializedResponseDataHelper.deserialize(responseData);
    Object response = responseData;
    if (responseData instanceof BinaryResponseData) {
      response = referenceFalseKryoSerializer.asInflatedObject(((BinaryResponseData) responseData).getData());
    }
    if (response instanceof K8sTaskExecutionResponse
        && (((K8sTaskExecutionResponse) response).getCommandExecutionStatus() == CommandExecutionStatus.FAILURE
            || ((K8sTaskExecutionResponse) response).getCommandExecutionStatus() == CommandExecutionStatus.SKIPPED)) {
      abortTasks(allCallbackIds, callbackId, ambiance);
    }
    if (response instanceof ErrorNotifyResponseData) {
      abortTasks(allCallbackIds, callbackId, ambiance);
    }
  }

  public String getLogPrefix(Ambiance ambiance) {
    LinkedHashMap<String, String> logAbstractions = StepUtils.generateLogAbstractions(ambiance, "STEP");
    return LogStreamingHelper.generateLogBaseKey(logAbstractions);
  }

  public TaskData getRunStepTask(Ambiance ambiance, UnitStep unitStep, long timeout) {
    LiteEnginePodDetailsOutcome liteEnginePodDetailsOutcome = (LiteEnginePodDetailsOutcome) outcomeService.resolve(
        ambiance, RefObjectUtils.getOutcomeRefObject(LiteEnginePodDetailsOutcome.POD_DETAILS_OUTCOME));
    String ip = liteEnginePodDetailsOutcome.getIpAddress();

    ExecuteStepRequest executeStepRequest = ExecuteStepRequest.newBuilder()
                                                .setExecutionId(ambiance.getPlanExecutionId())
                                                .setStep(unitStep)
                                                .setTmpFilePath(TMP_PATH)
                                                .build();
    CIK8ExecuteStepTaskParams params =
        CIK8ExecuteStepTaskParams.builder()
            .ip(ip)
            .port(LITE_ENGINE_PORT)
            .serializedStep(executeStepRequest.toByteArray())
            .isLocal(containerExecutionConfig.isLocal())
            .delegateSvcEndpoint(containerExecutionConfig.getDelegateServiceEndpointVariableValue())
            .build();
    return containerDelegateTaskHelper.getDelegateTaskDataForExecuteStep(ambiance, timeout, params);
  }

  private void abortTasks(List<String> allCallbackIds, String callbackId, Ambiance ambiance) {
    List<String> callBackIds =
        allCallbackIds.stream().filter(cid -> !cid.equals(callbackId)).collect(Collectors.toList());
    callBackIds.forEach(callbackId1
        -> waitNotifyEngine.doneWith(callbackId1,
            ErrorNotifyResponseData.builder()
                .errorMessage("Delegate is not able to connect to created build farm")
                .build()));
  }

  public Integer getPort(Ambiance ambiance, String stepIdentifier) {
    // Ports are assigned in lite engine step
    ContainerPortDetails containerPortDetails = (ContainerPortDetails) executionSweepingOutputService.resolve(
        ambiance, RefObjectUtils.getSweepingOutputRefObject(PORT_DETAILS));

    List<Integer> ports = containerPortDetails.getPortDetails().get(getKubernetesStandardPodName(stepIdentifier));

    if (ports.size() != 1) {
      throw new ContainerStepExecutionException(format("Step [%s] should map to single port", stepIdentifier));
    }

    return ports.get(0);
  }
}
