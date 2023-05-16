/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.search.redisConsumer;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.maintenance.MaintenanceController.getMaintenanceFlag;
import static io.harness.threading.Morpheus.sleep;

import static java.time.Duration.ofSeconds;

import io.harness.debezium.DebeziumChangeEvent;
import io.harness.eventHandler.DebeziumAbstractRedisEventHandler;
import io.harness.eventsframework.api.Consumer;
import io.harness.eventsframework.api.EventsFrameworkDownException;
import io.harness.eventsframework.consumer.Message;
import io.harness.eventsframework.impl.redis.RedisTraceConsumer;
import io.harness.lock.AcquiredLock;
import io.harness.lock.PersistentLocker;
import io.harness.queue.QueueController;
import io.harness.queue.RedisConsumerCg;

import com.google.inject.Inject;
import com.google.protobuf.InvalidProtocolBufferException;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.cache.Cache;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DebeziumAbstractRedisConsumerCg extends RedisTraceConsumer implements RedisConsumerCg {
  @Inject private PersistentLocker persistentLocker;
  private static final String LOCK_PREFIX = "DEBEZIUM_SNAPSHOT_CONSUMER_";
  private static final String CACHE_PREFIX = "DEBEZIUM_EVENT_";
  private static final int WAIT_TIME_IN_SECONDS = 30;
  private static final int TIMEOUT_IN_SECONDS = 30;
  private static final int SLEEP_SECONDS = 10;
  Consumer redisConsumer;
  DebeziumAbstractRedisEventHandler eventHandler;
  QueueController queueController;
  private AtomicBoolean shouldStop = new AtomicBoolean(false);
  Cache<String, Long> eventsCache;

  public DebeziumAbstractRedisConsumerCg(Consumer redisConsumer, QueueController queueController,
      DebeziumAbstractRedisEventHandler eventHandler, Cache<String, Long> eventsCache) {
    this.redisConsumer = redisConsumer;
    this.queueController = queueController;
    this.eventHandler = eventHandler;
    this.eventsCache = eventsCache;
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
          log.info("We are under maintenance, will try again after {} seconds", SLEEP_SECONDS);
          sleep(ofSeconds(SLEEP_SECONDS));
        }
        if (queueController.isNotPrimary()) {
          log.info(this.getClass().getSimpleName()
              + " is not running on primary deployment, will try again after some time...");
          TimeUnit.SECONDS.sleep(TIMEOUT_IN_SECONDS);
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
      log.error("Events framework is down for Redis Consumer. Retrying again...", e);
      TimeUnit.SECONDS.sleep(WAIT_TIME_IN_SECONDS);
    }
  }

  private void pollAndProcessMessages() {
    List<Message> messages = redisConsumer.read(Duration.ofSeconds(WAIT_TIME_IN_SECONDS));
    for (Message message : messages) {
      String messageId = message.getId();
      boolean messageProcessed = handleMessage(message);
      if (messageProcessed) {
        redisConsumer.acknowledge(messageId);
      }
    }
  }

  // We are only processing the latest events for a particular planExecutionId as we are getting full documents from
  // debezium. Locking helps in optimising time as once one thread has updated its timestamp, another pod will only
  // process events after that timestamp. In the future, if we only get partial updates from debezium, this design will
  // need to be reevaluated.
  @Override
  protected boolean processMessage(Message message) {
    DebeziumChangeEvent debeziumChangeEvent = Objects.requireNonNull(buildEventFromMessage(message));
    // debeziumChangeEvent.getKey() gives id of the Document which is unique
    String eventKey = CACHE_PREFIX + debeziumChangeEvent.getKey();
    String lockName = LOCK_PREFIX + eventKey;
    Long currentTimestamp = debeziumChangeEvent.getTimestamp();
    try (AcquiredLock<?> lock =
             persistentLocker.waitToAcquireLockOptional(lockName, Duration.ofSeconds(10), Duration.ofSeconds(30))) {
      if (lock == null) {
        log.debug(
            String.format("Not able to take lock on debezium consumer for lockName - %s, returning early.", lockName));
        return false;
      }
      log.debug("Acquired lock for id: {}", eventKey);
      Long lastProcessedTimestamp = eventsCache.get(eventKey);
      if (lastProcessedTimestamp == null) {
        eventsCache.put(eventKey, currentTimestamp);
        return eventHandler.handleEvent(debeziumChangeEvent);
      } else {
        if (lastProcessedTimestamp <= currentTimestamp) {
          eventsCache.put(eventKey, currentTimestamp);
          return eventHandler.handleEvent(debeziumChangeEvent);
        } else {
          log.debug("Ignoring event {} with id {} as it was already updated", message.getId(), eventKey);
          return true;
        }
      }
    } catch (Exception exception) {
      log.error(String.format("Error occurred in processing message with id %s", message.getId()), exception);
      return false;
    }
  }

  private DebeziumChangeEvent buildEventFromMessage(Message message) {
    try {
      return DebeziumChangeEvent.parseFrom(message.getMessage().getData());
    } catch (InvalidProtocolBufferException e) {
      log.error("Could not map message to DebeziumChangeEvent");
      return null;
    }
  }
  @Override
  public void shutDown() {
    shouldStop.set(true);
  }
}
