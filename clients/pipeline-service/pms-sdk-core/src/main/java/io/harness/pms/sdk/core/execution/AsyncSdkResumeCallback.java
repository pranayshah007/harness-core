/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.core.execution;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.pms.sdk.PmsSdkModuleUtils.CORE_EXECUTOR_NAME;

import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.AsyncChainExecutableResponse;
import io.harness.pms.contracts.execution.ExecutableResponse;
import io.harness.pms.contracts.execution.ExecutionMode;
import io.harness.pms.contracts.resume.ChainDetails;
import io.harness.pms.contracts.resume.NodeResumeEvent;
import io.harness.pms.contracts.resume.ResponseDataProto;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.execution.events.node.resume.NodeResumeEventHandler;
import io.harness.pms.sdk.core.steps.io.ResponseDataMapper;
import io.harness.tasks.ResponseData;
import io.harness.waiter.OldNotifyCallback;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import lombok.Builder;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PIPELINE)
@Value
@Builder
@Slf4j
public class AsyncSdkResumeCallback implements OldNotifyCallback {
  @Inject SdkNodeExecutionService sdkNodeExecutionService;
  @Inject NodeResumeEventHandler nodeResumeEventHandler;
  @Inject ResponseDataMapper responseDataMapper;
  @Inject @Named(CORE_EXECUTOR_NAME) ExecutorService executorService;
  byte[] ambianceBytes;
  byte[] executableResponseBytes;
  byte[] resolvedStepParameters;
  public static final String CDS_REMOVE_RESUME_EVENT_FOR_ASYNC_AND_ASYNCCHAIN_MODE =
      "CDS_REMOVE_RESUME_EVENT_FOR_ASYNC_AND_ASYNCCHAIN_MODE";

  @Override
  public void notify(Map<String, ResponseData> response) {
    // THis means new way of event got called and ambiance should be present
    notifyWithError(response, false);
  }

  @Override
  public void notifyError(Map<String, ResponseData> response) {
    notifyWithError(response, true);
  }

  private void notifyWithError(Map<String, ResponseData> response, boolean asyncError) {
    try {
      Ambiance ambiance = Ambiance.parseFrom(ambianceBytes);
      try {
        log.info("AsyncSdkResumeCallback notify is called for ambiance with nodeExecutionId {}",
            AmbianceUtils.obtainCurrentRuntimeId(ambiance));
        resumeNodeExecution(response, asyncError, ambiance);
      } catch (Exception e) {
        log.error("Exception while resuming", e);
        sdkNodeExecutionService.handleStepResponse(ambiance, NodeExecutionUtils.constructStepResponse(e));
      }
    } catch (InvalidProtocolBufferException e) {
      log.error("Not able to deserialize Ambiance bytes. Progress Callback will not be executed");
    }
  }

  private void resumeNodeExecution(Map<String, ResponseData> response, boolean asyncError, Ambiance ambiance)
      throws InvalidProtocolBufferException {
    if (shouldSendResumeSdkEvent(ambiance)) {
      sdkNodeExecutionService.resumeNodeExecution(ambiance, response, asyncError);
    } else {
      ExecutableResponse executableResponse = ExecutableResponse.parseFrom(executableResponseBytes);
      Map<String, ResponseDataProto> responseDataProtoMap = responseDataMapper.toResponseDataProtoV2(response);

      NodeResumeEvent.Builder resumeEventBuilder = NodeResumeEvent.newBuilder()
                                                       .setAmbiance(ambiance)
                                                       .setExecutionMode(getExecutionMode(executableResponse))
                                                       .setStepParameters(ByteString.copyFrom(resolvedStepParameters))
                                                       .setAsyncError(asyncError)
                                                       .putAllResponseData(responseDataProtoMap);

      setChainDetails(executableResponse, resumeEventBuilder);
      executorService.submit(() -> nodeResumeEventHandler.handleEventWithContext(resumeEventBuilder.build()));
    }
  }

  private ExecutionMode getExecutionMode(ExecutableResponse executableResponse) {
    if (executableResponse.hasAsyncChain()) {
      return ExecutionMode.ASYNC_CHAIN;
    }
    return ExecutionMode.ASYNC;
  }

  private boolean shouldSendResumeSdkEvent(Ambiance ambiance) {
    return !AmbianceUtils.checkIfFeatureFlagEnabled(ambiance, CDS_REMOVE_RESUME_EVENT_FOR_ASYNC_AND_ASYNCCHAIN_MODE)
        || executableResponseBytes == null || checkIfIdentityNode(ambiance);
  }

  // We need check for identity node and send event via normal flow to send resume sdk event for identity nodes as the
  // code to modify ambiance for identity nodes is on pipeline service only
  private boolean checkIfIdentityNode(Ambiance ambiance) {
    return AmbianceUtils.obtainNodeType(ambiance) == null
        || Objects.equals(AmbianceUtils.obtainNodeType(ambiance), "IDENTITY_PLAN_NODE");
  }

  private void setChainDetails(ExecutableResponse executableResponse, NodeResumeEvent.Builder resumeEventBuilder) {
    if (executableResponse.hasAsyncChain()) {
      AsyncChainExecutableResponse asyncChainExecutableResponse =
          Objects.requireNonNull(executableResponse).getAsyncChain();
      resumeEventBuilder.setChainDetails(ChainDetails.newBuilder()
                                             .setIsEnd(asyncChainExecutableResponse.getChainEnd())
                                             .setPassThroughData(asyncChainExecutableResponse.getPassThroughData())
                                             .build());
    }
  }

  @Override
  public void notifyTimeout(Map<String, ResponseData> responseMap) {
    responseMap.put("timeoutData", AsyncTimeoutResponseData.builder().build());
    notifyWithError(responseMap, false);
  }
}
