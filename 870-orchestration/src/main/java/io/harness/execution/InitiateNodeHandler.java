/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.execution;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.engine.OrchestrationEngine;
import io.harness.pms.PmsFeatureFlagService;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.events.InitiateNodeEvent;
import io.harness.pms.events.base.PmsBaseEventHandler;
import io.harness.pms.execution.utils.AmbianceUtils;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public class InitiateNodeHandler extends PmsBaseEventHandler<InitiateNodeEvent> {
  @Inject private OrchestrationEngine engine;
  @Inject private PmsFeatureFlagService pmsFeatureFlagService;

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
  protected void handleEventWithContext(InitiateNodeEvent event) {
    if (pmsFeatureFlagService.isEnabled(AmbianceUtils.getAccountId(event.getAmbiance()), FeatureName.PIPELINE_MATRIX)) {
      engine.initiateNode(event.getAmbiance(), event.getNodeId(), event.getRuntimeId(), null,
          event.hasStrategyMetadata() ? event.getStrategyMetadata() : null, event.getInitiateMode());
    } else {
      engine.initiateNode(event.getAmbiance(), event.getNodeId(), event.getRuntimeId(), null);
    }
  }
}
