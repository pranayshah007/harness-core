/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.core.execution.invokers;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.AsyncExecutableResponse;
import io.harness.pms.contracts.execution.ExecutableResponse;
import io.harness.pms.contracts.execution.ExecutionMode;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.execution.utils.StatusUtils;
import io.harness.pms.sdk.core.execution.AsyncSdkProgressCallback;
import io.harness.pms.sdk.core.execution.AsyncSdkResumeCallback;
import io.harness.pms.sdk.core.execution.AsyncSdkSingleCallback;
import io.harness.pms.sdk.core.execution.InvokerPackage;
import io.harness.pms.sdk.core.execution.ProgressableStrategy;
import io.harness.pms.sdk.core.execution.ResumePackage;
import io.harness.pms.sdk.core.registries.StepRegistry;
import io.harness.pms.sdk.core.steps.executables.AsyncExecutable;
import io.harness.pms.sdk.core.steps.io.StepParameters;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.StepResponseMapper;
import io.harness.pms.sdk.core.waiter.AsyncWaitEngine;
import io.harness.pms.serializer.recaster.RecastOrchestrationUtils;
import io.harness.tasks.ResponseData;

import com.google.inject.Inject;
import com.google.protobuf.ByteString;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@SuppressWarnings({"rawtypes", "unchecked"})
@OwnedBy(PIPELINE)
@Slf4j
public class AsyncStrategy extends ProgressableStrategy {
  @Inject private StepRegistry stepRegistry;
  @Inject private AsyncWaitEngine asyncWaitEngine;

  @Override
  public void start(InvokerPackage invokerPackage) {
    Ambiance ambiance = invokerPackage.getAmbiance();
    AsyncExecutable asyncExecutable = extractStep(ambiance);
    AsyncExecutableResponse asyncExecutableResponse = asyncExecutable.executeAsync(ambiance,
        invokerPackage.getStepParameters(), invokerPackage.getInputPackage(), invokerPackage.getPassThroughData());
    // Status should be in non-final state
    if (!StatusUtils.resumableStatuses().contains(asyncExecutableResponse.getStatus())) {
      log.warn("Skipping Handle Response as status in AsyncExecutionResponse is of final state {} ",
          asyncExecutableResponse.getStatus());
      asyncExecutableResponse = asyncExecutableResponse.toBuilder().setStatus(Status.NO_OP).build();
    }
    // This is only for handling non-final state
    handleResponse(
        ambiance, invokerPackage.getExecutionMode(), invokerPackage.getStepParameters(), asyncExecutableResponse);
  }

  @Override
  public void resume(ResumePackage resumePackage) {
    Ambiance ambiance = resumePackage.getAmbiance();
    AsyncExecutable asyncExecutable = extractStep(ambiance);
    log.info("Handling async response for nodeExecution with nodeExecutionId: {} and planExecutionId: {}",
        AmbianceUtils.obtainCurrentRuntimeId(ambiance), ambiance.getPlanExecutionId());
    StepResponse stepResponse = asyncExecutable.handleAsyncResponse(
        ambiance, resumePackage.getStepParameters(), resumePackage.getResponseDataMap());
    sdkNodeExecutionService.handleStepResponse(ambiance, StepResponseMapper.toStepResponseProto(stepResponse));
  }

  public void resumeSingle(
      Ambiance ambiance, StepParameters parameters, List<String> allCallbackIds, String callbackId, ResponseData data) {
    AsyncExecutable asyncExecutable = extractStep(ambiance);
    asyncExecutable.handleForCallbackId(ambiance, parameters, allCallbackIds, callbackId, data);
  }

  private void handleResponse(
      Ambiance ambiance, ExecutionMode mode, StepParameters stepParameters, AsyncExecutableResponse response) {
    String nodeExecutionId = AmbianceUtils.obtainCurrentRuntimeId(ambiance);
    String stepParamString = RecastOrchestrationUtils.toJson(stepParameters);

    // TODO : This is the last use of add executable response need to remove it as causing issues. Find a way to remove
    // this
    sdkNodeExecutionService.addExecutableResponse(ambiance, ExecutableResponse.newBuilder().setAsync(response).build());

    if (isEmpty(response.getCallbackIdsList())) {
      log.warn("StepResponse has no callbackIds - currentState : " + AmbianceUtils.obtainStepIdentifier(ambiance)
          + ", nodeExecutionId: " + nodeExecutionId);
      sdkNodeExecutionService.resumeNodeExecution(ambiance, Collections.emptyMap(), false);
      return;
    }
    queueCallbacks(ambiance, mode, response, stepParamString);
  }

  private void queueCallbacks(
      Ambiance ambiance, ExecutionMode mode, AsyncExecutableResponse response, String stepParamString) {
    byte[] parameterBytes =
        stepParamString == null ? new byte[] {} : ByteString.copyFromUtf8(stepParamString).toByteArray();
    byte[] ambianceBytes = ambiance.toByteArray();
    if (response.getCallbackIdsList().size() > 1) {
      for (String callbackId : response.getCallbackIdsList()) {
        // This is per callback Id callback
        AsyncSdkSingleCallback singleCallback = AsyncSdkSingleCallback.builder()
                                                    .ambianceBytes(ambianceBytes)
                                                    .stepParameters(parameterBytes)
                                                    .allCallbackIds(new ArrayList<>(response.getCallbackIdsList()))
                                                    .build();
        // Giving timeout 0 in this callback. Timeout to be handled by the overall callback.
        asyncWaitEngine.waitForAllOn(singleCallback, null, Collections.singletonList(callbackId), 0);
      }
    }
    // This is overall callback will be called once all the responses are received
    AsyncSdkResumeCallback callback = AsyncSdkResumeCallback.builder().ambianceBytes(ambianceBytes).build();
    AsyncSdkProgressCallback progressCallback = AsyncSdkProgressCallback.builder()
                                                    .ambianceBytes(ambianceBytes)
                                                    .stepParameters(parameterBytes)
                                                    .mode(mode)
                                                    .build();
    asyncWaitEngine.waitForAllOn(callback, progressCallback, response.getCallbackIdsList(), response.getTimeout());
  }

  @Override
  public AsyncExecutable extractStep(Ambiance ambiance) {
    return (AsyncExecutable) stepRegistry.obtain(AmbianceUtils.getCurrentStepType(ambiance));
  }
}
