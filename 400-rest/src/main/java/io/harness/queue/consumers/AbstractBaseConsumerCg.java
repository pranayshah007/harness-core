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
import io.harness.ng.core.event.MessageListener;
import io.harness.queue.QueueController;
import io.harness.queue.RedisConsumerCg;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.dto.ServicePrincipal;

import com.google.inject.Inject;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class AbstractBaseConsumerCg extends RedisTraceConsumer implements RedisConsumerCg {
  private static final int WAIT_TIME_IN_SECONDS = 10;
  private static final int SLEEP_SECONDS = 10;
  private final Consumer redisConsumer;
  private final List<MessageListener> messageListenersList;
  private final QueueController queueController;

  private final AtomicBoolean shouldStop;

  @Inject
  protected AbstractBaseConsumerCg(
      Consumer redisConsumer, MessageListener messageListener, QueueController queueController) {
    this.redisConsumer = redisConsumer;
    this.queueController = queueController;
    this.messageListenersList = Collections.singletonList(messageListener);
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
    AtomicBoolean success = new AtomicBoolean(true);
    messageListenersList.forEach(messageListener -> {
      if (!messageListener.handleMessage(message)) {
        success.set(false);
      }
    });

    return success.get();
  }

  @Override
  public void shutDown() {
    shouldStop.set(true);
  }
}
