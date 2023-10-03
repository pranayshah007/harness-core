/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.execution.utils;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.logging.AutoLogContext;
import io.harness.logging.AutoLogContext.OverrideBehavior;
import io.harness.pms.contracts.advisers.AdviserResponse;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.events.AdviserResponseRequest;
import io.harness.pms.contracts.execution.events.EventErrorRequest;
import io.harness.pms.contracts.execution.events.SdkResponseEventProto;
import io.harness.pms.contracts.execution.events.SdkResponseEventType;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.plan.NodeExecutionEventType;

import java.util.HashMap;
import java.util.Map;
import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(HarnessTeam.PIPELINE)
public class SdkResponseEventUtils {
  public AutoLogContext obtainLogContext(SdkResponseEventProto sdkResponseEvent) {
    return new AutoLogContext(logContextMap(sdkResponseEvent), OverrideBehavior.OVERRIDE_NESTS);
  }

  private Map<String, String> logContextMap(SdkResponseEventProto sdkResponseEvent) {
    Map<String, String> logContext = new HashMap<>();
    logContext.put("sdkResponseEventType", sdkResponseEvent.getSdkResponseEventType().name());
    logContext.putAll(AmbianceUtils.logContextMap(sdkResponseEvent.getAmbiance()));
    return logContext;
  }

  public static String getNodeExecutionId(SdkResponseEventProto event) {
    return AmbianceUtils.obtainCurrentRuntimeId(event.getAmbiance());
  }

  public static String getPlanExecutionId(SdkResponseEventProto event) {
    return event.getAmbiance().getPlanExecutionId();
  }

  public SdkResponseEventProto getSdkResponse(Ambiance ambiance, String notifyId, AdviserResponse adviserResponse) {
    return SdkResponseEventProto.newBuilder()
        .setSdkResponseEventType(SdkResponseEventType.HANDLE_ADVISER_RESPONSE)
        .setAdviserResponseRequest(
            AdviserResponseRequest.newBuilder().setAdviserResponse(adviserResponse).setNotifyId(notifyId).build())
        .setAmbiance(ambiance)
        .build();
  }

  public SdkResponseEventProto getSdkErrorResponse(
      NodeExecutionEventType eventType, Ambiance ambiance, String eventNotifyId, FailureInfo failureInfo) {
    return SdkResponseEventProto.newBuilder()
        .setSdkResponseEventType(SdkResponseEventType.HANDLE_EVENT_ERROR)
        .setAmbiance(ambiance)
        .setEventErrorRequest(EventErrorRequest.newBuilder()
                                  .setEventNotifyId(eventNotifyId)
                                  .setEventType(eventType)
                                  .setFailureInfo(failureInfo)
                                  .build())

        .build();
  }
}
