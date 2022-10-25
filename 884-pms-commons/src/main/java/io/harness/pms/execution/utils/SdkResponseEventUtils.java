/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.execution.utils;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.logging.logcontext.AutoLogContext;
import io.harness.logging.logcontext.AutoLogContext.OverrideBehavior;
import io.harness.pms.contracts.execution.events.SdkResponseEventProto;

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
}
