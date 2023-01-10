/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.template.event;

import static io.harness.authorization.AuthorizationServiceHeader.TEMPLATE_SERVICE;

import io.harness.eventsframework.api.Consumer;
import io.harness.eventsframework.api.EventsFrameworkDownException;
import io.harness.eventsframework.consumer.Message;
import io.harness.eventsframework.impl.redis.RedisTraceConsumer;
import io.harness.ng.core.event.MessageListener;
import io.harness.queue.QueueController;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.dto.ServicePrincipal;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor
@Slf4j
public abstract class AbstractStreamConsumer extends RedisTraceConsumer {
  private int maxWaitTimeInSeconds = 10;
  private Consumer consumer;
  private QueueController queueController;
  private List<MessageListener> messageListenersList;

  public AbstractStreamConsumer(
      Consumer consumer, QueueController queueController, List<MessageListener> messageListenersList) {
    this.consumer = consumer;
    this.queueController = queueController;
    this.messageListenersList = messageListenersList;
  }

  @Override
  public void run() {
    log.info("Started the Consumer {}", this.getClass().getSimpleName());
    try {
      SecurityContextBuilder.setContext(new ServicePrincipal(TEMPLATE_SERVICE.getServiceId()));
      while (!Thread.currentThread().isInterrupted()) {
        if (queueController.isNotPrimary()) {
          log.info(this.getClass().getSimpleName()
              + " is not running on primary deployment, will try again after some time...");
          TimeUnit.SECONDS.sleep(30);
          continue;
        }
        readEventsFrameworkMessages();
      }
    } catch (InterruptedException ex) {
      SecurityContextBuilder.unsetCompleteContext();
      Thread.currentThread().interrupt();
    } catch (Exception ex) {
      log.error("Consumer {} unexpectedly stopped", this.getClass().getSimpleName(), ex);
    } finally {
      SecurityContextBuilder.unsetCompleteContext();
    }
  }

  private void readEventsFrameworkMessages() throws InterruptedException {
    try {
      pollAndProcessMessages();
    } catch (EventsFrameworkDownException e) {
      log.error("Events framework is down for " + this.getClass().getSimpleName() + " consumer. Retrying again...", e);
      TimeUnit.SECONDS.sleep(maxWaitTimeInSeconds);
    }
  }

  private void pollAndProcessMessages() {
    List<Message> messages;
    String messageId;
    boolean messageProcessed;
    messages = consumer.read(Duration.ofSeconds(maxWaitTimeInSeconds));
    for (Message message : messages) {
      messageId = message.getId();
      messageProcessed = handleMessage(message);
      if (messageProcessed) {
        consumer.acknowledge(messageId);
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
}
