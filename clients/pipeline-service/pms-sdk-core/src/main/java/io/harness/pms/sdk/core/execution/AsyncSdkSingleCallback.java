/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.core.execution;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.sdk.core.execution.invokers.AsyncStrategy;
import io.harness.pms.sdk.core.steps.io.StepParameters;
import io.harness.pms.serializer.recaster.RecastOrchestrationUtils;
import io.harness.tasks.ResponseData;
import io.harness.waiter.OldNotifyCallback;

import com.google.inject.Inject;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

/**
 * When AyncExecutable has multiple callbacksIds then this callback provides a way to respond to step as an when
 * a single callbackId task is finished.
 *
 * This will not be used if the response only contains a single task
 *
 * This can execute in a parallel thread to {@link
 * io.harness.pms.sdk.core.steps.executables.AsyncExecutable#handleAsyncResponse}
 */
@Builder
@OwnedBy(PIPELINE)
@Slf4j
public class AsyncSdkSingleCallback implements OldNotifyCallback {
  @Inject AsyncStrategy strategy;

  byte[] ambianceBytes;
  byte[] stepParameters;
  List<String> allCallbackIds;

  @Override
  public void notify(Map<String, ResponseData> response) {
    notifyCallee(response);
  }

  @Override
  public void notifyError(Map<String, ResponseData> response) {
    notifyCallee(response);
  }

  private void notifyCallee(Map<String, ResponseData> response) {
    try {
      Ambiance ambiance = Ambiance.parseFrom(ambianceBytes);
      String stepParamsJson = ByteString.copyFrom(stepParameters).toStringUtf8();
      StepParameters stepParameters = RecastOrchestrationUtils.fromJson(stepParamsJson, StepParameters.class);
      String callbackId = response.keySet().iterator().next();
      ResponseData responseData = response.values().iterator().next();
      strategy.resumeSingle(ambiance, stepParameters, allCallbackIds, callbackId, responseData);
    } catch (InvalidProtocolBufferException e) {
      log.error("Not able to deserialize Node Execution from bytes. AsyncSdkSingleCallback will not be executed");
    }
  }
}
