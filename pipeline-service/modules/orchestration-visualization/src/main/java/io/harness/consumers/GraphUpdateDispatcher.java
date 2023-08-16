/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.consumers;

import io.harness.eventsframework.api.Consumer;
import io.harness.logging.AutoLogContext;
import io.harness.service.GraphGenerationService;

import com.google.common.collect.ImmutableMap;
import java.time.Duration;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class GraphUpdateDispatcher implements Runnable {
  private static final Duration THRESHOLD_PROCESS_DURATION = Duration.ofMillis(100);

  private final String planExecutionId;
  private final GraphGenerationService graphGenerationService;
  private final long startTs;
  private final String messageId;
  private final Consumer consumer;

  @Builder
  public GraphUpdateDispatcher(String planExecutionId, GraphGenerationService graphGenerationService, long startTs,
      String messageId, Consumer consumer) {
    this.planExecutionId = planExecutionId;
    this.graphGenerationService = graphGenerationService;
    this.startTs = startTs;
    this.messageId = messageId;
    this.consumer = consumer;
  }

  @Override
  public void run() {
    try (AutoLogContext ignore =
             new AutoLogContext(ImmutableMap.of("planExecutionId", planExecutionId, "messageId", messageId),
                 AutoLogContext.OverrideBehavior.OVERRIDE_NESTS)) {
      log.info("Start processing graph update via dispatcher for {} messageId", messageId);
      checkAndLogSchedulingDelays(planExecutionId, startTs);
      boolean shouldAck = graphGenerationService.updateGraph(planExecutionId);
      if (shouldAck) {
        consumer.acknowledge(messageId);
        log.debug("Successfully acked the messageId: {}", messageId);
        return;
      }
      log.info("Graph update failed not acking the following message id {}", messageId);
    }
  }

  private void checkAndLogSchedulingDelays(String planExecutionId, long startTs) {
    Duration scheduleDuration = Duration.ofMillis(System.currentTimeMillis() - startTs);
    if (THRESHOLD_PROCESS_DURATION.compareTo(scheduleDuration) < 0) {
      log.warn("[PMS_MESSAGE_LISTENER] Handler for graphUpdate event with planExecutionId {} called after {} delay",
          planExecutionId, scheduleDuration);
    }
  }
}
