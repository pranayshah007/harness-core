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
import io.harness.engine.interrupts.InterruptService;
import io.harness.interrupts.Interrupt;
import io.harness.pms.PmsFeatureFlagService;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.events.InitiateNodeEvent;
import io.harness.pms.contracts.interrupts.InterruptType;
import io.harness.pms.events.base.PmsBaseEventHandler;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public class InitiateNodeHandler extends PmsBaseEventHandler<InitiateNodeEvent> {
  @Inject private OrchestrationEngine engine;
  @Inject private PmsFeatureFlagService pmsFeatureFlagService;

  @Inject private InterruptService interruptService;
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
    Optional<Interrupt> abortAllOrExpireAllInterrupt =
        interruptService.fetchAllInterrupts(event.getAmbiance().getPlanExecutionId())
            .stream()
            .filter(interrupt
                -> interrupt.getType() == InterruptType.ABORT_ALL || interrupt.getType() == InterruptType.EXPIRE_ALL)
            .findAny();
    // If any abortAll or expireAll interrupt is registered then do not initiate any further nodeExecution.
    if (!abortAllOrExpireAllInterrupt.isPresent()) {
      engine.initiateNode(event.getAmbiance(), event.getNodeId(), event.getRuntimeId(), null,
          event.hasStrategyMetadata() ? event.getStrategyMetadata() : null, event.getInitiateMode());
    } else {
      log.info(
          "Not initiating the NodeExecution for RuntimeId {} and planExecutionId {}. Because {} interrupt is present with interruptId {}",
          event.getRuntimeId(), event.getAmbiance().getPlanExecutionId(), abortAllOrExpireAllInterrupt.get().getType(),
          abortAllOrExpireAllInterrupt.get().getUuid());
    }
  }
}
