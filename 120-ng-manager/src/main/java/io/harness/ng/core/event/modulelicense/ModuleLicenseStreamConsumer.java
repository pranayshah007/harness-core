/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.event.modulelicense;

import static io.harness.annotations.dev.HarnessTeam.GTM;
import static io.harness.authorization.AuthorizationServiceHeader.NG_MANAGER;
import static io.harness.eventsframework.EventsFrameworkConstants.MODULE_LICENSE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.eventsframework.api.Consumer;
import io.harness.eventsframework.api.EventsFrameworkDownException;
import io.harness.eventsframework.consumer.Message;
import io.harness.eventsframework.impl.redis.RedisTraceConsumer;
import io.harness.ng.core.event.MessageListener;
import io.harness.queue.QueueController;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.dto.ServicePrincipal;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(GTM)
@Slf4j
@Singleton
public class ModuleLicenseStreamConsumer extends RedisTraceConsumer {
  private static final int WAIT_TIME_IN_SECONDS = 30;
  private final Consumer eventConsumer;
  private final List<MessageListener> messageListenersList;
  private final QueueController queueController;

  @Inject
  public ModuleLicenseStreamConsumer(@Named(MODULE_LICENSE) Consumer eventConsumer,
      @Named(MODULE_LICENSE) MessageListener moduleLicenseStreamListener, QueueController queueController) {
    this.eventConsumer = eventConsumer;
    messageListenersList = new ArrayList<>();
    messageListenersList.add(moduleLicenseStreamListener);
    this.queueController = queueController;
  }

  @Override
  public void run() {
    log.info("Started the consumer for " + MODULE_LICENSE + " stream");
    try {
      SecurityContextBuilder.setContext(new ServicePrincipal(NG_MANAGER.getServiceId()));
      while (!Thread.currentThread().isInterrupted()) {
        if (queueController.isNotPrimary()) {
          log.info(
              "ModuleLicense stream consumer is not running on primary deployment, will try again after some time...");
          TimeUnit.SECONDS.sleep(WAIT_TIME_IN_SECONDS);
          continue;
        }
        readEventsFrameworkMessages();
      }
    } catch (InterruptedException ex) {
      log.error(MODULE_LICENSE + " stream consumer unexpectedly interrupted", ex);
      Thread.currentThread().interrupt();
    } catch (Exception ex) {
      log.error(MODULE_LICENSE + " stream consumer unexpectedly stopped", ex);
    } finally {
      SecurityContextBuilder.unsetCompleteContext();
    }
  }

  private void readEventsFrameworkMessages() throws InterruptedException {
    try {
      pollAndProcessMessages();
    } catch (EventsFrameworkDownException e) {
      log.error("Events framework is down for " + MODULE_LICENSE + " stream consumer. Retrying...", e);
      TimeUnit.SECONDS.sleep(WAIT_TIME_IN_SECONDS);
    }
  }

  private void pollAndProcessMessages() {
    String messageId;
    boolean messageProcessed;
    List<Message> messages = eventConsumer.read(Duration.ofSeconds(WAIT_TIME_IN_SECONDS));

    for (Message message : messages) {
      messageId = message.getId();
      messageProcessed = handleMessage(message);

      if (messageProcessed) {
        eventConsumer.acknowledge(messageId);
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
