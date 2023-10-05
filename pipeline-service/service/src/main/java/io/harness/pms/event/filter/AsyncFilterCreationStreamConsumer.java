/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.event.filter;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.eventsframework.EventsFrameworkConstants.ASYNC_FILTER_CREATION;
import static io.harness.maintenance.MaintenanceController.getMaintenanceFlag;
import static io.harness.threading.Morpheus.sleep;

import static java.time.Duration.ofSeconds;

import io.harness.PipelineServiceConfiguration;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.eventsframework.api.Consumer;
import io.harness.eventsframework.api.EventsFrameworkDownException;
import io.harness.eventsframework.consumer.Message;
import io.harness.logging.AutoLogContext;
import io.harness.pms.contracts.filter.AsyncFilterCreatorEvent;
import io.harness.pms.events.base.MessageLogContext;
import io.harness.pms.events.base.PmsRedisConsumer;
import io.harness.pms.pipeline.service.PMSPipelineService;
import io.harness.pms.pipeline.service.PMSPipelineServiceHelper;
import io.harness.pms.utils.CompletableFutures;
import io.harness.queue.QueueController;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.google.protobuf.InvalidProtocolBufferException;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@OwnedBy(PIPELINE)
public class AsyncFilterCreationStreamConsumer implements PmsRedisConsumer {
  private static final int WAIT_TIME_IN_SECONDS = 30;

  private final Consumer eventConsumer;
  private final QueueController queueController;
  private final Executor executorService;

  private PMSPipelineServiceHelper pmsPipelineServiceHelper;
  private PMSPipelineService pmsPipelineService;

  private PipelineServiceConfiguration pipelineServiceConfiguration;

  private final Duration sleepMs;
  private final AtomicBoolean shouldStop = new AtomicBoolean(false);
  private static final Integer SLEEP_TIME_FOR_MAINTENANCE = 10;

  @Inject
  public AsyncFilterCreationStreamConsumer(@Named(ASYNC_FILTER_CREATION) Consumer eventConsumer,
      QueueController queueController, @Named("PipelineAsyncValidationExecutorService") Executor executorService,
      PMSPipelineServiceHelper pmsPipelineServiceHelper, PMSPipelineService pmsPipelineService,
      PipelineServiceConfiguration pipelineServiceConfiguration) {
    this.eventConsumer = eventConsumer;
    this.queueController = queueController;
    this.executorService = executorService;
    this.pmsPipelineServiceHelper = pmsPipelineServiceHelper;
    this.pmsPipelineService = pmsPipelineService;
    this.pipelineServiceConfiguration = pipelineServiceConfiguration;
    Integer sleepMs = this.pipelineServiceConfiguration.getAsyncFilterCreationConsumerSleepIntervalMs();
    this.sleepMs = Duration.ofMillis(sleepMs);
  }

  @Override
  public void shutDown() {
    shouldStop.set(true);
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
          sleep(ofSeconds(SLEEP_TIME_FOR_MAINTENANCE));
        }
        if (queueController.isNotPrimary()) {
          log.debug(this.getClass().getSimpleName()
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
      log.error("Events framework is down for Async Filter Creation stream consumer. Retrying again...", e);
      TimeUnit.SECONDS.sleep(WAIT_TIME_IN_SECONDS);
    }
  }
  private void pollAndProcessMessages() {
    List<Message> messages = eventConsumer.read(Duration.ofSeconds(WAIT_TIME_IN_SECONDS));
    if (EmptyPredicate.isEmpty(messages)) {
      return;
    }
    AsyncFilterCreationStreamConsumer.ReadResult readResult = mapPipelineIdToMessages(messages);
    log.info("read: {}, processable: {}", messages.size(), readResult.filterCreationEvents.size());

    try {
      for (PipelineDetails pipelineDetail : readResult.filterCreationEvents) {
        CompletableFutures<Void> completableFutures = new CompletableFutures<>(executorService);
        completableFutures.supplyAsync(() -> {
          Runnable run = AsyncFilterCreationDispatcher.builder()
                             .pmsPipelineService(pmsPipelineService)
                             .pmsPipelineServiceHelper(pmsPipelineServiceHelper)
                             .yamlHash(pipelineDetail.yamlHash)
                             .uuid(pipelineDetail.uuid)
                             .messageId(pipelineDetail.messageId)
                             .build();
          run.run();
          return null;
        });
        try {
          completableFutures.allOf().get(5, TimeUnit.MINUTES);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
          throw new RuntimeException(e);
        }
      }
    } finally {
      // Ack all the messages.
      try {
        if (EmptyPredicate.isNotEmpty(readResult.tobeAcked)) {
          eventConsumer.acknowledge(readResult.tobeAcked);
        }
      } catch (Exception ex) {
        log.error("error while acknowledging messages", ex);
      }

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
  AsyncFilterCreationStreamConsumer.ReadResult mapPipelineIdToMessages(List<Message> messages) {
    Set<String> toBeAcked = new HashSet<>();
    Set<PipelineDetails> pipelineIdsAndYamlHash = new HashSet<>();
    for (Message message : messages) {
      try (AutoLogContext ignore = new MessageLogContext(message)) {
        AsyncFilterCreatorEvent event = buildEventFromMessage(message);
        if (event == null) {
          toBeAcked.add(message.getId());
          continue;
        }
        pipelineIdsAndYamlHash.add(PipelineDetails.builder()
                                       .yamlHash(event.getYamlHash())
                                       .uuid(event.getUuid())
                                       .messageId(message.getId())
                                       .build());
        toBeAcked.add(message.getId());
      }
    }
    return new AsyncFilterCreationStreamConsumer.ReadResult(pipelineIdsAndYamlHash, toBeAcked.toArray(String[] ::new));
  }

  private AsyncFilterCreatorEvent buildEventFromMessage(Message message) {
    try {
      return AsyncFilterCreatorEvent.parseFrom(message.getMessage().getData());
    } catch (InvalidProtocolBufferException e) {
      log.error("Could not map message to AsyncFilterCreatorEvent", e);
      return null;
    }
  }

  @AllArgsConstructor
  static class ReadResult {
    Set<PipelineDetails> filterCreationEvents;
    String[] tobeAcked;
  }

  @Builder
  static class PipelineDetails {
    Integer yamlHash;
    String messageId;
    String uuid;
  }
}
