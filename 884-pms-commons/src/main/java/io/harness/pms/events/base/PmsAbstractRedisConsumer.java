/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.events.base;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.maintenance.MaintenanceController.getMaintenanceFlag;
import static io.harness.threading.Morpheus.sleep;

import static java.time.Duration.ofSeconds;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.data.structure.EmptyPredicate;
import io.harness.eventsframework.api.Consumer;
import io.harness.eventsframework.api.EventsFrameworkDownException;
import io.harness.eventsframework.consumer.Message;
import io.harness.eventsframework.impl.redis.RedisTraceConsumer;
import io.harness.logging.AutoLogContext;
import io.harness.queue.QueueController;

import com.google.common.annotations.VisibleForTesting;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import javax.cache.Cache;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(HarnessTeam.PIPELINE)
@Slf4j
public abstract class PmsAbstractRedisConsumer<T extends PmsAbstractMessageListener>
    extends RedisTraceConsumer implements PmsRedisConsumer {
  private static final int WAIT_TIME_IN_SECONDS = 1;
  private static final int THREAD_SLEEP_TIME_IN_MILLIS = 200;
  private static final int THREAD_SLEEP_MILLIS_WHEN_CONSUMER_IS_BUSY =
      EmptyPredicate.isNotEmpty(System.getenv("THREAD_SLEEP_MILLIS_WHEN_CONSUMER_IS_BUSY"))
      ? Integer.parseInt(System.getenv("THREAD_SLEEP_MILLIS_WHEN_CONSUMER_IS_BUSY"))
      : 0;

  private static final Duration THRESHOLD_PROCESS_DURATION = Duration.ofMillis(100);
  private static final int SLEEP_SECONDS = 10;
  private static final String CACHE_KEY = "%s_%s";
  private final Consumer redisConsumer;
  private final T messageListener;
  private final ExecutorService executorService;
  private final QueueController queueController;
  private final AtomicBoolean shouldStop = new AtomicBoolean(false);
  private final Cache<String, Integer> eventsCache;

  public PmsAbstractRedisConsumer(Consumer redisConsumer, T messageListener, Cache<String, Integer> eventsCache,
      QueueController queueController, ExecutorService executorService) {
    this.redisConsumer = redisConsumer;
    this.messageListener = messageListener;
    this.eventsCache = eventsCache;
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
      preThreadHandler();
      do {
        if (getMaintenanceFlag()) {
          log.info("We are currently under maintenance");
          while (getMaintenanceFlag()) {
            sleep(ofSeconds(SLEEP_SECONDS));
          }
          log.info("Maintenance is finished. We are in working state again");
        }
        if (queueController.isNotPrimary()) {
          TimeUnit.SECONDS.sleep(30);
          continue;
        }
        readEventsFrameworkMessages();
      } while (!Thread.currentThread().isInterrupted() && !shouldStop.get());
    } catch (Exception ex) {
      log.error("Consumer {} unexpectedly stopped", this.getClass().getSimpleName(), ex);
    } finally {
      postThreadCompletion();
    }
  }

  public void preThreadHandler() {}

  public void postThreadCompletion() {}

  protected void readEventsFrameworkMessages() throws InterruptedException {
    try {
      pollAndProcessMessages();
    } catch (EventsFrameworkDownException e) {
      log.error("Events framework is down for " + this.getClass().getSimpleName() + " consumer. Retrying again...", e);
      TimeUnit.SECONDS.sleep(WAIT_TIME_IN_SECONDS);
    }
  }

  @VisibleForTesting
  void pollAndProcessMessages() throws InterruptedException {
    List<Message> messages;
    String messageId;
    boolean messageProcessed;
    messages = redisConsumer.read(Duration.ofSeconds(WAIT_TIME_IN_SECONDS));
    List<Message> processableMessages =
        messages.stream().filter(messageListener::isProcessable).collect(Collectors.toList());
    List<String> processableMessageIds = processableMessages.stream().map(Message::getId).collect(Collectors.toList());
    if (processableMessages.size() > 0) {
      log.info("Read message with messages with ids [{}] from redis", processableMessageIds);
    }
    for (Message message : processableMessages) {
      messageId = message.getId();
      messageProcessed = handleMessage(message);
      if (messageProcessed) {
        redisConsumer.acknowledge(messageId);
      }
    }
    List<Message> notProcessableMessages = messages.stream()
                                               .filter(message -> !processableMessageIds.contains(message.getId()))
                                               .collect(Collectors.toList());
    for (Message message : notProcessableMessages) {
      redisConsumer.acknowledge(message.getId());
    }
    if (messages.size() < this.redisConsumer.getBatchSize()) {
      // Adding thread sleep when the events read are less than the batch-size. This way when the load is high, consumer
      // will query the events quickly. And in case of low load, thread will sleep for some time.
      TimeUnit.MILLISECONDS.sleep(THREAD_SLEEP_TIME_IN_MILLIS);
    } else {
      // Remove this log after one release.
      log.info("Not sleeping the thread after reading one batch of events because redis has more events to be read");
      if (THREAD_SLEEP_MILLIS_WHEN_CONSUMER_IS_BUSY > 0) {
        TimeUnit.MILLISECONDS.sleep(THREAD_SLEEP_MILLIS_WHEN_CONSUMER_IS_BUSY);
      }
    }
  }

  @Override
  protected boolean processMessage(Message message) {
    AtomicBoolean success = new AtomicBoolean(true);
    if (!isAlreadyProcessed(message)) {
      long readTs = System.currentTimeMillis();
      executorService.submit(() -> {
        try (AutoLogContext ignore = new MessageLogContext(message)) {
          // Check and log for time taken to schedule the thread
          checkAndLogSchedulingDelays(message.getId(), readTs);
          messageListener.handleMessage(message, readTs);
        } catch (Exception ex) {
          log.error("[PMS_MESSAGE_LISTENER] Exception occurred while processing {} event with messageId: {}",
              messageListener.getClass().getSimpleName(), message.getId(), ex);
        }
      });
    }
    return success.get();
  }

  private boolean isAlreadyProcessed(Message message) {
    try {
      String key = String.format(CACHE_KEY, this.getClass().getSimpleName(), message.getId());
      boolean isProcessed = !eventsCache.putIfAbsent(key, 1);
      if (isProcessed) {
        log.warn(String.format("Duplicate redis notification received to consumer [%s] with messageId [%s]",
            this.getClass().getSimpleName(), message.getId()));
      }
      return isProcessed;
    } catch (Exception ex) {
      log.error("Exception occurred while checking for duplicate notification", ex);
      return false;
    }
  }

  private void checkAndLogSchedulingDelays(String messageId, long startTs) {
    Duration scheduleDuration = Duration.ofMillis(System.currentTimeMillis() - startTs);
    if (THRESHOLD_PROCESS_DURATION.compareTo(scheduleDuration) < 0) {
      log.warn("[PMS_MESSAGE_LISTENER] Handler for {} event with messageId {} called after {} delay",
          messageListener.getClass().getSimpleName(), messageId, scheduleDuration);
    }
  }

  public void shutDown() {
    shouldStop.set(true);
  }
}
