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
import io.harness.pms.contracts.execution.events.InitiateNodeEvent;
import io.harness.pms.events.base.PmsBaseEventHandler;
import io.harness.pms.sdk.execution.events.EventHandlerResult;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public class InitiateNodeHandler extends PmsBaseEventHandler<InitiateNodeEvent, PmsNodeExecution> {
  @Inject private OrchestrationEngine engine;

  @Override
  protected Map<String, String> extractMetricContext(Map<String, String> metadataMap, InitiateNodeEvent event) {
    return ImmutableMap.of("eventType", "TRIGGER_NODE");
  }

  @Override
  protected String getMetricPrefix(InitiateNodeEvent message) {
    return "trigger_node_event";
  }

  @Override
  protected Map<String, String> extraLogProperties(InitiateNodeEvent event) {
    return ImmutableMap.of();
  }

  @Override
  protected Ambiance extractAmbiance(InitiateNodeEvent event) {
    return event.getAmbiance();
  }

  @Override
  protected EventHandlerResult<PmsNodeExecution> handleEventWithContext(InitiateNodeEvent event) {
    PmsNodeExecution pmsNodeExecution =
        engine.initiateNode(event.getAmbiance(), event.getNodeId(), event.getRuntimeId(), null,
            event.hasStrategyMetadata() ? event.getStrategyMetadata() : null, event.getInitiateMode());
    return EventHandlerResult.<PmsNodeExecution>builder().data(pmsNodeExecution).success(true).build();
  }
}
