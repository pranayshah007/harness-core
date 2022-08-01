/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.execution;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.engine.OrchestrationEngine;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.events.SdkResponseEventProto;
import io.harness.pms.events.base.PmsBaseEventHandler;
import io.harness.pms.execution.utils.SdkResponseEventUtils;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public class SdkResponseHandler extends PmsBaseEventHandler<SdkResponseEventProto> {
  @Inject private OrchestrationEngine engine;

  @Override
  protected Map<String, String> extraLogProperties(SdkResponseEventProto event) {
    return ImmutableMap.of("eventType", event.getSdkResponseEventType().name(), "nodeExecutionId",
        SdkResponseEventUtils.getNodeExecutionId(event), "planExecutionId",
        SdkResponseEventUtils.getPlanExecutionId(event));
  }

  @Override
  protected Ambiance extractAmbiance(SdkResponseEventProto event) {
    return event.getAmbiance();
  }

  @Override
  protected Map<String, String> extractMetricContext(Map<String, String> metadataMap, SdkResponseEventProto event) {
    return ImmutableMap.of("eventType", event.getSdkResponseEventType().name());
  }

  @Override
  protected String getMetricPrefix(SdkResponseEventProto message) {
    return "sdk_response_event";
  }

  @Override
  protected void handleEventWithContext(SdkResponseEventProto event) {
    // This is the event for new execution
    engine.handleSdkResponseEvent(event);
  }
}
