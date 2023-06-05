/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.events.eventlisteners.eventhandler;

import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ENTITY_TYPE;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.eventsframework.NgEventLogContext;
import io.harness.eventsframework.consumer.Message;
import io.harness.eventsframework.entity_crud.EntityChangeDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.idp.events.eventlisteners.factory.EventMessageHandlerFactory;
import io.harness.idp.events.eventlisteners.messagehandler.EventMessageHandler;
import io.harness.lock.AcquiredLock;
import io.harness.lock.redis.RedisPersistentLocker;
import io.harness.logging.AutoLogContext;
import io.harness.ng.core.event.MessageListener;

import com.google.inject.Inject;
import com.google.protobuf.InvalidProtocolBufferException;
import java.time.Duration;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.IDP)
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
public class EntityCrudStreamListener implements MessageListener {
  EventMessageHandlerFactory eventMessageHandlerFactory;
  RedisPersistentLocker redisLocker;

  @Override
  public boolean handleMessage(Message message) {
    if (message == null || !message.hasMessage()) {
      log.error("Unable to complete processing the crud event with the id because Message for the event was null");
      return true;
    }
    final String messageId = message.getId();
    try (AutoLogContext ignore = new NgEventLogContext(messageId, OVERRIDE_ERROR)) {
      Map<String, String> metadataMap = message.getMessage().getMetadataMap();
      String entityType = metadataMap.get(ENTITY_TYPE);
      if (entityType == null) {
        log.error(
            "Unable to complete processing the crud event with the id {} because Entity Type for the event was null",
            messageId);
        return true;
      }
      String action = metadataMap.get(ACTION);
      if (action == null) {
        log.error("Unable to complete processing the crud event with the id {} because ACTION for the event was null",
            messageId);
        return true;
      }
      EntityChangeDTO entityChangeDTO;
      EventMessageHandler eventMessageHandler = eventMessageHandlerFactory.getEventMessageHandler(entityType);

      if (eventMessageHandler != null) {
        try {
          entityChangeDTO = EntityChangeDTO.parseFrom(message.getMessage().getData());
        } catch (InvalidProtocolBufferException e) {
          throw new InvalidRequestException(
              String.format("Exception in unpacking EntityChangeDTO for id %s", messageId), e);
        }

        if (entityChangeDTO != null) {
          String accountIdentifier = entityChangeDTO.getAccountIdentifier().getValue();
          String resourceIdentifier = entityChangeDTO.getIdentifier().getValue();

          AcquiredLock lock;
          while (true) {
            lock =
                this.redisLocker.tryToAcquireLock(accountIdentifier + "_" + resourceIdentifier, Duration.ofMinutes(1));
            if (lock == null) {
              log.info(
                  "Lock not acquired for crud event for resource of type {} with identifier {} for account {} with message id - {}, waiting to acquire lock",
                  entityType, resourceIdentifier, accountIdentifier, messageId);
              Thread.sleep(1000);
              continue;
            }

            log.info(
                "Lock acquired for processing crud event for resource of type {} with identifier {} for account {} with message id - {}",
                entityType, resourceIdentifier, accountIdentifier, messageId);

            try {
              eventMessageHandler.handleMessage(message, entityChangeDTO, action);
              log.info("Completed processing the crud event with the id {}", messageId);
            } catch (Exception e) {
              log.error("Error in handling the crud event with the id {} for entity type {}", messageId, entityType, e);
            } finally {
              redisLocker.destroy(lock);
              Thread.sleep(200);
              log.debug(
                  "Lock released for processing crud event for resource of type {} with identifier {} for account {} with message id - {}",
                  entityType, resourceIdentifier, accountIdentifier, messageId);
            }
            return true;
          }
        }
      }

    } catch (Exception e) {
      log.error("Error processing the crud event with the id {} for entity type {}", messageId,
          message.getMessage().getMetadataMap().get(ENTITY_TYPE), e);
    }
    return true;
  }
}