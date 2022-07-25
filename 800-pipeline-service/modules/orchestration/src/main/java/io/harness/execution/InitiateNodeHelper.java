/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.execution;

import static io.harness.OrchestrationEventsFrameworkConstants.INITIATE_NODE_EVENT_PRODUCER;

import io.harness.eventsframework.api.Producer;
import io.harness.eventsframework.producer.Message;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.StrategyMetadata;
import io.harness.pms.contracts.execution.events.InitiateMode;
import io.harness.pms.contracts.execution.events.InitiateNodeEvent;
import io.harness.pms.execution.utils.AmbianceUtils;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
public class InitiateNodeHelper {
  @Inject @Named(INITIATE_NODE_EVENT_PRODUCER) private Producer producer;

  public String publishEvent(Ambiance ambiance, String nodeId, String runtimeId) {
    ImmutableMap<String, String> eventMetadata = ImmutableMap.<String, String>builder()
                                                     .put("eventType", "TRIGGER_NODE")
                                                     .put("newNodeId", nodeId)
                                                     .put("newRuntimeId", runtimeId)
                                                     .putAll(AmbianceUtils.logContextMap(ambiance))
                                                     .build();
    InitiateNodeEvent event =
        InitiateNodeEvent.newBuilder().setAmbiance(ambiance).setNodeId(nodeId).setRuntimeId(runtimeId).build();
    return producer.send(Message.newBuilder().putAllMetadata(eventMetadata).setData(event.toByteString()).build());
  }

  public String publishEvent(Ambiance ambiance, String nodeId, String runtimeId, StrategyMetadata strategyMetadata,
      InitiateMode initiateMode) {
    ImmutableMap<String, String> eventMetadata = ImmutableMap.<String, String>builder()
                                                     .put("eventType", "TRIGGER_NODE")
                                                     .put("newNodeId", nodeId)
                                                     .put("newRuntimeId", runtimeId)
                                                     .putAll(AmbianceUtils.logContextMap(ambiance))
                                                     .build();
    InitiateNodeEvent event = InitiateNodeEvent.newBuilder()
                                  .setAmbiance(ambiance)
                                  .setNodeId(nodeId)
                                  .setRuntimeId(runtimeId)
                                  .setStrategyMetadata(strategyMetadata)
                                  .setInitiateMode(initiateMode)
                                  .build();
    return producer.send(Message.newBuilder().putAllMetadata(eventMetadata).setData(event.toByteString()).build());
  }
}
