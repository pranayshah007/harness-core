/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.core.plugin;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.callback.DelegateCallbackToken;
import io.harness.data.structure.CollectionUtils;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.ci.k8s.K8sTaskExecutionResponse;
import io.harness.helper.SerializedResponseDataHelper;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logstreaming.LogStreamingHelper;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.AsyncExecutableResponse;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outcome.OutcomeService;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepParameters;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.product.ci.engine.proto.UnitStep;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.StepUtils;
import io.harness.steps.container.execution.ContainerExecutionConfig;
import io.harness.steps.executable.AsyncExecutableWithRbac;
import io.harness.steps.plugin.ContainerStepConstants;
import io.harness.tasks.BinaryResponseData;
import io.harness.tasks.ResponseData;
import io.harness.utils.PluginUtils;
import io.harness.waiter.WaitNotifyEngine;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Singleton
@OwnedBy(HarnessTeam.PIPELINE)
@Slf4j
public abstract class AbstractContainerStepV2<T extends StepParameters> implements AsyncExecutableWithRbac<T> {
  @Inject private SerializedResponseDataHelper serializedResponseDataHelper;
  @Inject private WaitNotifyEngine waitNotifyEngine;
  @Inject private ContainerDelegateTaskHelper containerDelegateTaskHelper;
  @Inject private ContainerStepExecutionResponseHelper containerStepExecutionResponseHelper;
  @Inject @Named("referenceFalseKryoSerializer") private KryoSerializer referenceFalseKryoSerializer;
  @Inject OutcomeService outcomeService;
  @Inject ContainerPortHelper containerPortHelper;
  @Inject Supplier<DelegateCallbackToken> delegateCallbackTokenSupplier;
  @Inject ExecutionSweepingOutputService executionSweepingOutputService;

  @Inject PluginUtils pluginUtils;
  public static String DELEGATE_SVC_ENDPOINT = "delegate-service:8080";

  @Override
  public AsyncExecutableResponse executeAsyncAfterRbac(
      Ambiance ambiance, T stepElementParameters, StepInputPackage inputPackage) {
    log.info("Starting run in container step");
    String accountId = AmbianceUtils.getAccountId(ambiance);

    long timeout = getTimeout(ambiance, stepElementParameters);
    timeout = Math.max(timeout, 100);

    String parkedTaskId = containerDelegateTaskHelper.queueParkedDelegateTask(ambiance, timeout, accountId);

    TaskData runStepTaskData = getStepTask(ambiance, stepElementParameters, AmbianceUtils.getAccountId(ambiance),
        getLogPrefix(ambiance), timeout, parkedTaskId);
    String liteEngineTaskId = containerDelegateTaskHelper.queueTask(ambiance, runStepTaskData, accountId);
    log.info("Created parked task {} and lite engine task {}", parkedTaskId, liteEngineTaskId);

    return AsyncExecutableResponse.newBuilder()
        .addCallbackIds(parkedTaskId)
        .addCallbackIds(liteEngineTaskId)
        .addAllLogKeys(CollectionUtils.emptyIfNull(Collections.singletonList(getLogPrefix(ambiance))))
        .build();
  }

  @Override
  public void handleAbort(Ambiance ambiance, T stepParameters, AsyncExecutableResponse executableResponse) {
    // can be overriden by child methods
  }

  @Override
  public void handleForCallbackId(Ambiance ambiance, T containerStepInfo, List<String> allCallbackIds,
      String callbackId, ResponseData responseData) {
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

  @Override
  public StepResponse handleAsyncResponse(
      Ambiance ambiance, T stepParameters, Map<String, ResponseData> responseDataMap) {
    StepResponse.StepOutcome extraOutcome = getAnyOutComeForStep(ambiance, stepParameters, responseDataMap);
    return containerStepExecutionResponseHelper.handleAsyncResponseInternal(ambiance, responseDataMap, extraOutcome);
  }

  private String getLogPrefix(Ambiance ambiance) {
    LinkedHashMap<String, String> logAbstractions = StepUtils.generateLogAbstractions(ambiance, "STEP");
    return LogStreamingHelper.generateLogBaseKey(logAbstractions);
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

  public TaskData getStepTask(
      Ambiance ambiance, T containerStepInfo, String accountId, String logKey, long timeout, String parkedTaskId) {
    UnitStep unitStep = getSerialisedStep(ambiance, containerStepInfo, accountId, logKey, timeout, parkedTaskId);
    boolean isLocal = false;
    String delegateSvcEndpoint = DELEGATE_SVC_ENDPOINT;
    OptionalSweepingOutput optionalSweepingOutput = executionSweepingOutputService.resolveOptional(
        ambiance, RefObjectUtils.getSweepingOutputRefObject(ContainerStepConstants.CONTAINER_EXECUTION_CONFIG));
    if (optionalSweepingOutput.isFound()) {
      ContainerExecutionConfig output = (ContainerExecutionConfig) optionalSweepingOutput.getOutput();
      isLocal = output.isLocal();
      delegateSvcEndpoint = output.getDelegateServiceEndpointVariableValue();
    }
    return pluginUtils.getDelegateTaskForPluginStep(ambiance, unitStep,
        PluginStepMetadata.builder()
            .delegateServiceEndpoint(delegateSvcEndpoint)
            .isLocal(isLocal)
            .timeout(timeout)
            .build());
  }

  public Integer getPort(Ambiance ambiance, String stepIdentifier) {
    return containerPortHelper.getPort(ambiance, stepIdentifier);
  }

  public abstract long getTimeout(Ambiance ambiance, T stepElementParameters);

  public abstract UnitStep getSerialisedStep(
      Ambiance ambiance, T containerStepInfo, String accountId, String logKey, long timeout, String parkedTaskId);

  public abstract StepResponse.StepOutcome getAnyOutComeForStep(
      Ambiance ambiance, T stepParameters, Map<String, ResponseData> responseDataMap);

  @Override public abstract Class<T> getStepParametersClass();
}
