/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.consumers.graph;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.eventsframework.EventsFrameworkConstants.ORCHESTRATION_LOG;
import static io.harness.maintenance.MaintenanceController.getMaintenanceFlag;
import static io.harness.threading.Morpheus.sleep;

import static java.time.Duration.ofSeconds;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.data.structure.EmptyPredicate;
import io.harness.eventsframework.api.Consumer;
import io.harness.eventsframework.api.EventsFrameworkDownException;
import io.harness.eventsframework.consumer.Message;
import io.harness.logging.AutoLogContext;
import io.harness.pms.contracts.visualisation.log.OrchestrationLogEvent;
import io.harness.pms.events.base.MessageLogContext;
import io.harness.pms.events.base.PmsRedisConsumer;
import io.harness.queue.QueueController;
import io.harness.service.GraphGenerationService;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.google.protobuf.InvalidProtocolBufferException;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(PIPELINE)
@Singleton
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = HarnessModuleComponent.CDS_PIPELINE)
public class GraphUpdateRedisConsumer implements PmsRedisConsumer {
  private static final int WAIT_TIME_IN_SECONDS = 30;

  private final Consumer eventConsumer;
  private final GraphGenerationService graphGenerationService;
  private final QueueController queueController;
  private final ExecutorService executorService;

  private final Duration sleepMs;
  private final AtomicBoolean shouldStop = new AtomicBoolean(false);

  @Inject
  public GraphUpdateRedisConsumer(@Named(ORCHESTRATION_LOG) Consumer redisConsumer,
      GraphGenerationService graphGenerationService, QueueController queueController,
      @Named("OrchestrationVisualizationExecutorService") ExecutorService executorService,
      @Named("GraphConsumerSleepMs") int sleepMs) {
    this.eventConsumer = redisConsumer;
    this.graphGenerationService = graphGenerationService;
    this.queueController = queueController;
    this.executorService = executorService;
    this.sleepMs = Duration.ofMillis(sleepMs);
  }

  @Override
  public void run() {
    log.info("Started the Consumer {}", this.getClass().getSimpleName());
    String threadName = this.getClass().getSimpleName() + "-handler-" + generateUuid();
    log.debug("Setting thread name to {}", threadName);
    Thread.currentThread().setName(threadName);

    try {
      do {
        while (getMaintenanceFlag()) {
          sleep(ofSeconds(1));
        }
        if (queueController.isNotPrimary()) {
          log.info(this.getClass().getSimpleName()
              + " is not running on primary deployment, will try again after some time...");
          TimeUnit.SECONDS.sleep(30);
          continue;
        }

        readEventsFrameworkMessages();
      } while (!Thread.currentThread().isInterrupted() && !shouldStop.get());
    } catch (Exception ex) {
      log.error("Consumer {} unexpectedly stopped", this.getClass().getSimpleName(), ex);
    }
  }

  private void readEventsFrameworkMessages() throws InterruptedException {
    try {
      pollAndProcessMessages();
    } catch (EventsFrameworkDownException e) {
      log.error("Events framework is down for Orchestration Log consumer. Retrying again...", e);
      TimeUnit.SECONDS.sleep(WAIT_TIME_IN_SECONDS);
    }
  }

  private void pollAndProcessMessages() {
    List<Message> messages = eventConsumer.read(Duration.ofSeconds(WAIT_TIME_IN_SECONDS));
    if (EmptyPredicate.isEmpty(messages)) {
      return;
    }
    ReadResult readResult = mapPlanExecutionToMessages(messages);
    log.info("read: {}, processable: {}, bulkAcking : {}", messages.size(), readResult.planExecutionIds.size(),
        readResult.tobeAcked.length);

    try {
      if (EmptyPredicate.isNotEmpty(readResult.tobeAcked)) {
        eventConsumer.acknowledge(readResult.tobeAcked);
      }
    } catch (Exception ex) {
      log.error("error while acknowledging messages", ex);
    }

    try {
      for (String planExecutionId : readResult.planExecutionIds) {
        executorService.submit(GraphUpdateDispatcher.builder()
                                   .planExecutionId(planExecutionId)
                                   .startTs(System.currentTimeMillis())
                                   .graphGenerationService(graphGenerationService)
                                   .build());
      }
    } finally {
      if (messages.size() < eventConsumer.getBatchSize()) {
        // Adding thread sleep when the events read are less than the batch-size. This way when the load is high,
        // consumer will query the events quickly. And in case of low load, thread will sleep for some time.
        log.info("Sleeping the thread for {}", sleepMs);
        if (!sleepMs.isNegative() && !sleepMs.isZero()) {
          sleep(sleepMs);
        }
      }
    }
  }

  @VisibleForTesting
  ReadResult mapPlanExecutionToMessages(List<Message> messages) {
    Set<String> toBeAcked = new HashSet<>();
    Set<String> planExecutionIds = new HashSet<>();
    for (Message message : messages) {
      try (AutoLogContext ignore = new MessageLogContext(message)) {
        OrchestrationLogEvent event = buildEventFromMessage(message);
        if (event == null) {
          toBeAcked.add(message.getId());
          continue;
        }
        planExecutionIds.add(event.getPlanExecutionId());
        toBeAcked.add(message.getId());
      }
    }
    return new ReadResult(planExecutionIds, toBeAcked.toArray(String[] ::new));
  }

  private OrchestrationLogEvent buildEventFromMessage(Message message) {
    try {
      return OrchestrationLogEvent.parseFrom(message.getMessage().getData());
    } catch (InvalidProtocolBufferException e) {
      log.error("Could not map message to OrchestrationLogEvent");
      return null;
    }
  }

  @Override
  public void shutDown() {
    shouldStop.set(true);
  }

  @AllArgsConstructor
  static class ReadResult {
    Set<String> planExecutionIds;
    String[] tobeAcked;
  }
}
