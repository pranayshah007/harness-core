/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution;

import static io.harness.authorization.AuthorizationServiceHeader.MANAGER;
import static io.harness.eventsframework.EventsFrameworkConstants.OBSERVER_EVENT_CHANNEL;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.DELEGATE_ENTITY;

import io.harness.eventsframework.api.Consumer;
import io.harness.eventsframework.api.EventsFrameworkDownException;
import io.harness.eventsframework.consumer.Message;
import io.harness.ng.core.event.MessageListener;
import io.harness.queue.QueueController;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.dto.ServicePrincipal;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import io.fabric8.utils.Lists;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class ObserverEventConsumer implements Runnable {
  private static final int WAIT_TIME_IN_SECONDS = 10;
  private final Consumer redisConsumer;
  private final List<MessageListener> messageListeners;
  private final QueueController queueController;

  @Inject
  public ObserverEventConsumer(@Named(OBSERVER_EVENT_CHANNEL) Consumer redisConsumer,
      @Named(DELEGATE_ENTITY + OBSERVER_EVENT_CHANNEL) MessageListener delegateTaskEventListener,
      QueueController queueController) {
    this.redisConsumer = redisConsumer;
    this.messageListeners = Lists.newArrayList(delegateTaskEventListener);
    this.queueController = queueController;
  }

  @Override
  public void run() {
    log.info("Started the consumer for observer_event_channel");
    try {
      SecurityContextBuilder.setContext(new ServicePrincipal(MANAGER.getServiceId()));
      while (!Thread.currentThread().isInterrupted()) {
        if (queueController.isNotPrimary()) {
          log.info(
              "observer_event_channel consumer is not running on primary deployment, will try again after some time...");
          TimeUnit.SECONDS.sleep(30);
          continue;
        }
        pollAndProcessMessages();
      }
    } catch (InterruptedException ex) {
      SecurityContextBuilder.unsetCompleteContext();
      Thread.currentThread().interrupt();
    } catch (final Exception ex) {
      log.error("observer_event_channel consumer unexpectedly stopped", ex);
    } finally {
      SecurityContextBuilder.unsetCompleteContext();
    }
  }

  private void pollAndProcessMessages() throws InterruptedException {
    try {
      for (final Message message : redisConsumer.read(Duration.ofSeconds(WAIT_TIME_IN_SECONDS))) {
        final String messageId = message.getId();
        if (handleMessage(message)) {
          redisConsumer.acknowledge(messageId);
        }
      }
    } catch (final EventsFrameworkDownException e) {
      log.error("Exception occurred", e);
      TimeUnit.SECONDS.sleep(WAIT_TIME_IN_SECONDS);
    }
  }

  private boolean handleMessage(final Message message) {
    try {
      return processMessage(message);
    } catch (final Exception ex) {
      log.error(String.format("Error occurred in processing message with id %s", message.getId()), ex);
      return false;
    }
  }

  private boolean processMessage(final Message message) {
    final AtomicBoolean success = new AtomicBoolean(false);
    messageListeners.forEach(messageListener -> {
      if (messageListener.handleMessage(message)) {
        success.set(true);
      }
    });
    return success.get();
  }
}
