/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.eventstream;

import static io.harness.annotations.dev.HarnessTeam.DEL;
import static io.harness.authorization.AuthorizationServiceHeader.MANAGER;
import static io.harness.eventsframework.EventsFrameworkConstants.ENTITY_CRUD;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ORGANIZATION_ENTITY;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.PROJECT_ENTITY;

import io.harness.annotations.dev.OwnedBy;
import io.harness.eventsframework.api.Consumer;
import io.harness.eventsframework.api.EventsFrameworkDownException;
import io.harness.eventsframework.consumer.Message;
import io.harness.ng.core.event.MessageListener;
import io.harness.queue.QueueController;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.dto.ServicePrincipal;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(DEL)
@Slf4j
@Singleton
public class EntityCRUDConsumer implements Runnable {
  private static final int WAIT_TIME_IN_SECONDS = 10;
  private final Consumer redisConsumer;
  private final List<MessageListener> messageListeners;
  private final QueueController queueController;

  @Inject
  public EntityCRUDConsumer(@Named(ENTITY_CRUD) Consumer redisConsumer,
      @Named(ORGANIZATION_ENTITY + ENTITY_CRUD) MessageListener organizationEntityCRUDStreamListener,
      @Named(PROJECT_ENTITY + ENTITY_CRUD) MessageListener projectEntityCRUDStreamListener,
      QueueController queueController) {
    this.redisConsumer = redisConsumer;
    this.messageListeners = Lists.newArrayList(organizationEntityCRUDStreamListener, projectEntityCRUDStreamListener);
    this.queueController = queueController;
  }

  @Override
  public void run() {
    log.info("Started the consumer for entity crud");
    try {
      SecurityContextBuilder.setContext(new ServicePrincipal(MANAGER.getServiceId()));
      while (!Thread.currentThread().isInterrupted()) {
        if (queueController.isNotPrimary()) {
          log.info("Entity crud consumer is not running on primary deployment, will try again after some time...");
          TimeUnit.SECONDS.sleep(30);
          continue;
        }
        pollAndProcessMessages();
      }
    } catch (InterruptedException ex) {
      SecurityContextBuilder.unsetCompleteContext();
      Thread.currentThread().interrupt();
    } catch (final Exception ex) {
      log.error("Entity crud consumer unexpectedly stopped", ex);
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
      log.error("Events framework is down for Entity crud consumer. Retrying again...", e);
      TimeUnit.SECONDS.sleep(WAIT_TIME_IN_SECONDS);
    }
  }

  private boolean handleMessage(final Message message) {
    try {
      return processMessage(message);
    } catch (final Exception ex) {
      // This is not evicted from events framework so that it can be processed
      // by other consumer if the error is a runtime error
      log.error(String.format("Error occurred in processing message with id %s", message.getId()), ex);
      return false;
    }
  }

  private boolean processMessage(final Message message) {
    final AtomicBoolean success = new AtomicBoolean(true);
    messageListeners.forEach(messageListener -> {
      if (!messageListener.handleMessage(message)) {
        success.set(false);
      }
    });
    return success.get();
  }
}
