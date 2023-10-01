/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.core.execution.invokers;
import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.pms.sdk.core.execution.invokers.StrategyHelper.buildResponseDataSupplier;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.AsyncChainExecutableResponse;
import io.harness.pms.contracts.execution.ExecutableResponse;
import io.harness.pms.contracts.execution.ExecutionMode;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.execution.AsyncSdkProgressCallback;
import io.harness.pms.sdk.core.execution.AsyncSdkResumeCallback;
import io.harness.pms.sdk.core.execution.ChainDetails;
import io.harness.pms.sdk.core.execution.InvokerPackage;
import io.harness.pms.sdk.core.execution.ProgressableStrategy;
import io.harness.pms.sdk.core.execution.ResumePackage;
import io.harness.pms.sdk.core.registries.StepRegistry;
import io.harness.pms.sdk.core.steps.executables.AsyncChainExecutable;
import io.harness.pms.sdk.core.steps.io.StepParameters;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.StepResponseMapper;
import io.harness.pms.sdk.core.waiter.AsyncWaitEngine;
import io.harness.pms.serializer.recaster.RecastOrchestrationUtils;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import com.google.protobuf.ByteString;
import java.util.Collections;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(PIPELINE)
@Slf4j
public class AsyncChainStrategy extends ProgressableStrategy {
  @Inject private StepRegistry stepRegistry;
  @Inject private AsyncWaitEngine asyncWaitEngine;
  @Inject private StrategyHelper strategyHelper;

  @Override
  public void start(InvokerPackage invokerPackage) {
    Ambiance ambiance = invokerPackage.getAmbiance();
    AsyncChainExecutable asyncExecutable = extractStep(ambiance);
    AsyncChainExecutableResponse asyncChainExecutableResponse =
        asyncExecutable.startChainLink(ambiance, invokerPackage.getStepParameters(), invokerPackage.getInputPackage());

    handleResponse(
        ambiance, invokerPackage.getExecutionMode(), invokerPackage.getStepParameters(), asyncChainExecutableResponse);
  }

  @Override
  public void resume(ResumePackage resumePackage) {
    Ambiance ambiance = resumePackage.getAmbiance();
    ChainDetails chainDetails = resumePackage.getChainDetails();
    String nodeExecutionId = AmbianceUtils.obtainCurrentRuntimeId(ambiance);
    StepParameters stepParameters = resumePackage.getStepParameters();
    AsyncChainExecutable asyncChainExecutable = extractStep(ambiance);

    if (chainDetails.isShouldEnd()) {
      StepResponse stepResponse;
      try {
        log.info("Chain end true: Calling finalizeExecution, nodeExecutionId: {}", nodeExecutionId);
        stepResponse = asyncChainExecutable.finalizeExecution(
            ambiance, stepParameters, buildResponseDataSupplier(resumePackage.getResponseDataMap()));
      } catch (Exception e) {
        log.error("Exception occurred while calling finalizeExecution, nodeExecutionId: {}", nodeExecutionId, e);
        stepResponse = strategyHelper.handleException(e);
      }
      sdkNodeExecutionService.handleStepResponse(ambiance, StepResponseMapper.toStepResponseProto(stepResponse));
    } else {
      try {
        AsyncChainExecutableResponse asyncChainExecutableResponse =
            asyncChainExecutable.executeNextLink(ambiance, stepParameters, resumePackage.getStepInputPackage(),
                buildResponseDataSupplier(resumePackage.getResponseDataMap()));
        handleResponse(ambiance, ExecutionMode.ASYNC_CHAIN, stepParameters, asyncChainExecutableResponse);
      } catch (Exception e) {
        log.error("Exception occurred while calling executeNextLink, nodeExecutionId: {}", nodeExecutionId, e);
        sdkNodeExecutionService.handleStepResponse(
            ambiance, StepResponseMapper.toStepResponseProto(strategyHelper.handleException(e)));
      }
    }
  }

  private void handleResponse(Ambiance ambiance, ExecutionMode mode, StepParameters stepParameters,
      AsyncChainExecutableResponse asyncChainExecutableResponse) {
    String nodeExecutionId = AmbianceUtils.obtainCurrentRuntimeId(ambiance);
    String stepParamString = RecastOrchestrationUtils.toJson(stepParameters);

    sdkNodeExecutionService.addExecutableResponse(
        ambiance, ExecutableResponse.newBuilder().setAsyncChain(asyncChainExecutableResponse).build());

    if (isEmpty(asyncChainExecutableResponse.getCallbackId())) {
      log.warn("StepResponse has no callbackIds - currentState : " + AmbianceUtils.obtainStepIdentifier(ambiance)
          + ", nodeExecutionId: " + nodeExecutionId);
      sdkNodeExecutionService.resumeNodeExecution(ambiance, Collections.emptyMap(), false);
      return;
    }
    log.info("Processing Async Chain Step for {} with CallbackId {}", nodeExecutionId,
        asyncChainExecutableResponse.getCallbackId());
    queueCallbacks(ambiance, mode, asyncChainExecutableResponse, stepParamString);
  }

  private void queueCallbacks(
      Ambiance ambiance, ExecutionMode mode, AsyncChainExecutableResponse response, String stepParamString) {
    byte[] parameterBytes =
        stepParamString == null ? new byte[] {} : ByteString.copyFromUtf8(stepParamString).toByteArray();
    byte[] ambianceBytes = ambiance.toByteArray();

    AsyncSdkResumeCallback callback = AsyncSdkResumeCallback.builder().ambianceBytes(ambianceBytes).build();
    AsyncSdkProgressCallback progressCallback = AsyncSdkProgressCallback.builder()
                                                    .ambianceBytes(ambianceBytes)
                                                    .stepParameters(parameterBytes)
                                                    .mode(mode)
                                                    .build();
    asyncWaitEngine.waitForAllOn(
        callback, progressCallback, ImmutableList.of(response.getCallbackId()), response.getTimeout());
  }

  @Override
  public AsyncChainExecutable extractStep(Ambiance ambiance) {
    return (AsyncChainExecutable) stepRegistry.obtain(AmbianceUtils.getCurrentStepType(ambiance));
  }
}
