/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.events.consumers;

import static io.harness.authorization.AuthorizationServiceHeader.IDP_SERVICE;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ENTITY_TYPE;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.eventsframework.api.Consumer;
import io.harness.eventsframework.api.EventsFrameworkDownException;
import io.harness.eventsframework.consumer.Message;
import io.harness.eventsframework.impl.redis.RedisTraceConsumer;
import io.harness.idp.events.eventlisteners.eventhandler.utils.ResourceLocker;
import io.harness.lock.AcquiredLock;
import io.harness.queue.QueueController;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.SourcePrincipalContextBuilder;
import io.harness.security.dto.ServicePrincipal;

import com.google.protobuf.ByteString;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@AllArgsConstructor
@Slf4j
@OwnedBy(HarnessTeam.IDP)
public abstract class AbstractIdpServiceRedisStreamConsumer extends RedisTraceConsumer implements IdpRedisConsumer {
  private static final int WAIT_TIME_IN_SECONDS = 10;
  private Consumer redisConsumer;
  private QueueController queueController;
  private final AtomicBoolean shouldStop = new AtomicBoolean(false);
  private ResourceLocker resourceLocker;

  protected abstract boolean processMessage(Message message);

  protected boolean entityTypeAndActionValidation(
      String consumerName, Message message, String entityTypeToCheckAgainst, String actionToCheckAgainst) {
    return entityTypeAndActionValidation(
        consumerName, message, entityTypeToCheckAgainst, Collections.singletonList(actionToCheckAgainst));
  }

  protected boolean entityTypeAndActionValidation(
      String consumerName, Message message, String entityTypeToCheckAgainst, List<String> actionsToCheckAgainst) {
    Map<String, String> metadataMap = message.getMessage().getMetadataMap();
    String entityType = metadataMap.get(ENTITY_TYPE);
    String action = metadataMap.get(ACTION);
    if (!StringUtils.equals(entityType, entityTypeToCheckAgainst)
        || (action == null || !actionsToCheckAgainst.contains(action))) {
      log.error("Unable to process messageId {} in {} entityType = {} action = {}", message.getId(), consumerName,
          entityType, action);
      return false;
    }
    return true;
  }

  protected void lockAndProcessData(String lockName, ByteString data) throws Exception {
    AcquiredLock<?> lock = null;
    try {
      lock = resourceLocker.acquireLock(lockName);
      processInternal(data);
    } finally {
      if (lock != null) {
        resourceLocker.releaseLock(lock);
      }
    }
  }

  protected abstract void processInternal(ByteString data) throws Exception;

  @Override
  public void run() {
    log.info("Started the consumer for idp-service misc redis stream");
    try {
      SecurityContextBuilder.setContext(new ServicePrincipal(IDP_SERVICE.getServiceId()));
      SourcePrincipalContextBuilder.setSourcePrincipal(new ServicePrincipal(IDP_SERVICE.getServiceId()));
      while (!Thread.currentThread().isInterrupted() && !shouldStop.get()) {
        if (queueController.isNotPrimary()) {
          log.info(this.getClass().getSimpleName()
              + " is not running on primary deployment, will try again after some time...");
          TimeUnit.SECONDS.sleep(30);
          continue;
        }
        readEventsFrameworkMessages();
      }
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
    } catch (Exception ex) {
      log.error("idp-service misc redis stream consumer unexpectedly stopped", ex);
    } finally {
      SecurityContextBuilder.unsetCompleteContext();
    }
  }

  @Override
  public void shutDown() {
    shouldStop.set(true);
  }

  private void readEventsFrameworkMessages() throws InterruptedException {
    try {
      pollAndProcessMessages();
    } catch (EventsFrameworkDownException e) {
      log.error("Events framework is down for idp-service misc redis stream consumer. Retrying again...", e);
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
}
