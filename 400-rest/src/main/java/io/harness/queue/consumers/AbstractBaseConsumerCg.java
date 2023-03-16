/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.queue.consumers;

import static io.harness.authorization.AuthorizationServiceHeader.MANAGER;

import io.harness.eventsframework.api.Consumer;
import io.harness.eventsframework.api.EventsFrameworkDownException;
import io.harness.eventsframework.consumer.Message;
import io.harness.eventsframework.impl.redis.RedisTraceConsumer;
import io.harness.maintenance.MaintenanceController;
import io.harness.ng.core.event.MessageListener;
import io.harness.queue.QueueController;
import io.harness.queue.RedisConsumerCg;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.dto.ServicePrincipal;

import com.google.inject.Inject;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.cache.Cache;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class AbstractBaseConsumerCg extends RedisTraceConsumer implements RedisConsumerCg {
  private static final String CACHE_KEY = "%s_%s";
  private static final int WAIT_TIME_IN_SECONDS = 10;
  private static final int SLEEP_SECONDS = 10;
  private final Consumer redisConsumer;
  private final MessageListener messageListener;
  private final QueueController queueController;

  private final AtomicBoolean shouldStop;

  private Cache<String, Integer> eventsCache;

  @Inject
  protected AbstractBaseConsumerCg(Consumer redisConsumer, MessageListener messageListener,
      QueueController queueController, Cache<String, Integer> eventsCache) {
    this.redisConsumer = redisConsumer;
    this.queueController = queueController;
    this.messageListener = messageListener;
    this.eventsCache = eventsCache;
    this.shouldStop = new AtomicBoolean(false);
  }

  @Override
  public void run() {
    log.info("Started the consumer for setup usage stream");
    try {
      SecurityContextBuilder.setContext(new ServicePrincipal(MANAGER.getServiceId()));
      while (!Thread.currentThread().isInterrupted() && !shouldStop.get()) {
        if (queueController.isNotPrimary()) {
          log.info("Setup usage consumer is not running on primary deployment, will try again after some time...");
          TimeUnit.SECONDS.sleep(SLEEP_SECONDS);
          continue;
        }
        if (MaintenanceController.getMaintenanceFlag()) {
          log.info("We are under maintenance, will try again after {} seconds", SLEEP_SECONDS);
          TimeUnit.SECONDS.sleep(SLEEP_SECONDS);
          continue;
        }
        readEventsFrameworkMessages();
      }
    } catch (InterruptedException ex) {
      SecurityContextBuilder.unsetCompleteContext();
      Thread.currentThread().interrupt();
    } catch (Exception ex) {
      log.error("Setup Usage consumer unexpectedly stopped", ex);
    } finally {
      SecurityContextBuilder.unsetCompleteContext();
    }
  }

  private void readEventsFrameworkMessages() throws InterruptedException {
    try {
      pollAndProcessMessages();
    } catch (EventsFrameworkDownException e) {
      log.error("Events framework is down for Setup Usage consumer. Retrying again...", e);
      TimeUnit.SECONDS.sleep(WAIT_TIME_IN_SECONDS);
    }
  }

  private void pollAndProcessMessages() {
    List<Message> messages;
    String messageId;
    boolean messageProcessed;
    messages = redisConsumer.read(Duration.ofSeconds(WAIT_TIME_IN_SECONDS));
    for (Message message : messages) {
      messageId = message.getId();
      messageProcessed = handleMessage(message);
      if (messageProcessed) {
        redisConsumer.acknowledge(messageId);
      }
    }
  }

  @Override
  protected boolean processMessage(Message message) {
    if (isAlreadyProcessed(message)) {
      return true;
    }

    if (messageListener.handleMessage(message)) {
      insertMessageInCache(message.getId());
      return true;
    }

    return false;
  }

  private void insertMessageInCache(String messageId) {
    try {
      eventsCache.put(String.format(CACHE_KEY, this.getClass().getSimpleName(), messageId), 1);
    } catch (Exception ex) {
      log.error("Exception occurred while storing message id in cache", ex);
    }
  }

  private boolean isAlreadyProcessed(Message message) {
    try {
      String key = String.format(CACHE_KEY, this.getClass().getSimpleName(), message.getId());
      boolean isProcessed = eventsCache.containsKey(key);
      if (isProcessed) {
        log.warn(String.format("Duplicate redis notification received to consumer [%s] with messageId [%s]",
            this.getClass().getSimpleName(), message.getId()));
        Integer count = eventsCache.get(key);
        if (count != null) {
          eventsCache.put(String.format(CACHE_KEY, this.getClass().getSimpleName(), message.getId()), count + 1);
        }
      }
      return isProcessed;
    } catch (Exception ex) {
      log.error("Exception occurred while checking for duplicate notification", ex);
      return false;
    }
  }

  @Override
  public void shutDown() {
    shouldStop.set(true);
  }
}
