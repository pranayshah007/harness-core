/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.event;

import static io.harness.AuthorizationServiceHeader.NG_MANAGER;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.eventsframework.EventsFrameworkConstants.ENTITY_CRUD;
import static io.harness.eventsframework.EventsFrameworkConstants.GIT_SYNC_ENTITY_STREAM;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.CONNECTOR_ENTITY;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ENTITY_TYPE;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ENVIRONMENT_GROUP_ENTITY;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.GITOPS_CLUSTER_ENTITY;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.PROJECT_ENTITY;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.SECRET_ENTITY;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.SETUP_USAGE_ENTITY;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.USER_ENTITY;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.USER_GROUP;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.USER_SCOPE_RECONCILIATION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.VARIABLE_ENTITY;

import io.harness.annotations.dev.OwnedBy;
import io.harness.eventsframework.api.Consumer;
import io.harness.eventsframework.api.EventsFrameworkDownException;
import io.harness.eventsframework.consumer.Message;
import io.harness.queue.QueueController;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.dto.ServicePrincipal;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PL)
@Slf4j
@Singleton
public class EntityCRUDStreamConsumer implements Runnable {
  private static final int WAIT_TIME_IN_SECONDS = 10;
  private final Consumer redisConsumer;
  private final Map<String, MessageProcessor> processorMap;
  private final List<MessageListener> messageListenersList;
  private final QueueController queueController;

  @Inject
  public EntityCRUDStreamConsumer(@Named(ENTITY_CRUD) Consumer redisConsumer,
      @Named(PROJECT_ENTITY + ENTITY_CRUD) MessageListener projectEntityCRUDStreamListener,
      @Named(CONNECTOR_ENTITY + ENTITY_CRUD) MessageListener connectorEntityCRUDStreamListener,
      @Named(ENVIRONMENT_GROUP_ENTITY + ENTITY_CRUD) MessageListener environmentGroupEntityCRUDStreamListener,
      @Named(SETUP_USAGE_ENTITY) MessageProcessor setupUsageChangeEventMessageProcessor,
      @Named(USER_ENTITY + ENTITY_CRUD) MessageListener userEntityCRUDStreamListener,
      @Named(SECRET_ENTITY + ENTITY_CRUD) MessageListener secretEntityCRUDStreamListner,
      @Named(VARIABLE_ENTITY + ENTITY_CRUD) MessageListener variableEntityCRUDStreamListener,
      @Named(USER_GROUP + ENTITY_CRUD) MessageListener userGroupEntityCRUDStreamListener,
      @Named(USER_SCOPE_RECONCILIATION) MessageListener userMembershipReconciliationMessageProcessor,
      @Named(GIT_SYNC_ENTITY_STREAM + ENTITY_CRUD) MessageListener gitSyncProjectCleanup,
      @Named(GITOPS_CLUSTER_ENTITY + ENTITY_CRUD) MessageListener gitopsClusterCleanupProcessor,
      QueueController queueController) {
    this.redisConsumer = redisConsumer;
    this.queueController = queueController;
    messageListenersList = new ArrayList<>();
    messageListenersList.add(projectEntityCRUDStreamListener);
    messageListenersList.add(gitSyncProjectCleanup);
    messageListenersList.add(connectorEntityCRUDStreamListener);
    messageListenersList.add(environmentGroupEntityCRUDStreamListener);
    messageListenersList.add(userEntityCRUDStreamListener);
    messageListenersList.add(secretEntityCRUDStreamListner);
    messageListenersList.add(variableEntityCRUDStreamListener);
    messageListenersList.add(userGroupEntityCRUDStreamListener);
    messageListenersList.add(userMembershipReconciliationMessageProcessor);
    messageListenersList.add(gitopsClusterCleanupProcessor);
    processorMap = new HashMap<>();
    processorMap.put(SETUP_USAGE_ENTITY, setupUsageChangeEventMessageProcessor);
  }

  @Override
  public void run() {
    log.info("Started the consumer for entity crud stream");
    try {
      SecurityContextBuilder.setContext(new ServicePrincipal(NG_MANAGER.getServiceId()));
      while (!Thread.currentThread().isInterrupted()) {
        if (queueController.isNotPrimary()) {
          log.info("Entity crud consumer is not running on primary deployment, will try again after some time...");
          TimeUnit.SECONDS.sleep(30);
          continue;
        }
        readEventsFrameworkMessages();
      }
    } catch (InterruptedException ex) {
      SecurityContextBuilder.unsetCompleteContext();
      Thread.currentThread().interrupt();
    } catch (Exception ex) {
      log.error("Entity crud stream consumer unexpectedly stopped", ex);
    } finally {
      SecurityContextBuilder.unsetCompleteContext();
    }
  }

  private void readEventsFrameworkMessages() throws InterruptedException {
    try {
      pollAndProcessMessages();
    } catch (EventsFrameworkDownException e) {
      log.error("Events framework is down for Entity crud stream consumer. Retrying again...", e);
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

  private boolean handleMessage(Message message) {
    try {
      return processMessage(message);
    } catch (Exception ex) {
      // This is not evicted from events framework so that it can be processed
      // by other consumer if the error is a runtime error
      log.error(String.format("Error occurred in processing message with id %s", message.getId()), ex);
      return false;
    }
  }

  private boolean processMessage(Message message) {
    AtomicBoolean success = new AtomicBoolean(true);
    messageListenersList.forEach(messageListener -> {
      if (!messageListener.handleMessage(message)) {
        success.set(false);
      }
    });

    if (message.hasMessage()) {
      Map<String, String> metadataMap = message.getMessage().getMetadataMap();
      if (metadataMap != null && metadataMap.get(ENTITY_TYPE) != null) {
        String entityType = metadataMap.get(ENTITY_TYPE);
        if (processorMap.get(entityType) != null) {
          if (!processorMap.get(entityType).processMessage(message)) {
            success.set(false);
          }
        }
      }
    }
    return success.get();
  }
}
