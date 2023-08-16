/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.consumers;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.eventsframework.EventsFrameworkConstants.ORCHESTRATION_LOG;
import static io.harness.maintenance.MaintenanceController.getMaintenanceFlag;
import static io.harness.threading.Morpheus.sleep;

import static java.time.Duration.ofSeconds;

import io.harness.annotations.dev.OwnedBy;
import io.harness.eventsframework.api.Consumer;
import io.harness.eventsframework.api.EventsFrameworkDownException;
import io.harness.eventsframework.consumer.Message;
import io.harness.logging.AutoLogContext;
import io.harness.pms.contracts.visualisation.log.OrchestrationLogEvent;
import io.harness.pms.events.base.MessageLogContext;
import io.harness.pms.events.base.PmsRedisConsumer;
import io.harness.queue.QueueController;
import io.harness.service.GraphGenerationService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.google.protobuf.InvalidProtocolBufferException;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(PIPELINE)
@Singleton
public class GraphUpdateRedisConsumer implements PmsRedisConsumer {
  private static final int WAIT_TIME_IN_SECONDS = 30;

  Consumer eventConsumer;
  GraphGenerationService graphGenerationService;
  QueueController queueController;
  ExecutorService executorService;
  private AtomicBoolean shouldStop = new AtomicBoolean(false);

  @Inject
  public GraphUpdateRedisConsumer(@Named(ORCHESTRATION_LOG) Consumer redisConsumer,
      GraphGenerationService graphGenerationService, QueueController queueController,
      @Named("OrchestrationVisualizationExecutorService") ExecutorService executorService) {
    this.eventConsumer = redisConsumer;
    this.graphGenerationService = graphGenerationService;
    this.queueController = queueController;
    this.executorService = executorService;
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
    Map<String, String> planExIdToMessageMap = mapPlanExecutionToMessages(messages);
    for (Map.Entry<String, String> entry : planExIdToMessageMap.entrySet()) {
      executorService.submit(GraphUpdateDispatcher.builder()
                                 .planExecutionId(entry.getKey())
                                 .startTs(System.currentTimeMillis())
                                 .graphGenerationService(graphGenerationService)
                                 .messageId(entry.getValue())
                                 .consumer(eventConsumer)
                                 .build());
    }
  }

  private Map<String, String> mapPlanExecutionToMessages(List<Message> messages) {
    Map<String, String> resultMap = new HashMap<>();
    for (Message message : messages) {
      try (AutoLogContext ignore = new MessageLogContext(message)) {
        log.info("Read message with message id {} from redis", message.getId());
        OrchestrationLogEvent event = buildEventFromMessage(message);
        if (event == null) {
          eventConsumer.acknowledge(message.getId());
          continue;
        }

        resultMap.compute(event.getPlanExecutionId(), (k, v) -> {
          if (v != null) {
            eventConsumer.acknowledge(v);
          }
          return message.getId();
        });
      }
    }
    return resultMap;
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
}
