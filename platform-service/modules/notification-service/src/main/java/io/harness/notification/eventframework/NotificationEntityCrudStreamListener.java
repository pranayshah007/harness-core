/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.notification.eventframework;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ACCOUNT_ENTITY;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.DELETE_ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ENTITY_TYPE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.eventsframework.consumer.Message;
import io.harness.eventsframework.entity_crud.account.AccountEntityChangeDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.event.MessageListener;
import io.harness.notification.service.api.NotificationService;
import io.harness.notification.service.api.NotificationSettingsService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.protobuf.InvalidProtocolBufferException;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PL)
@Slf4j
@Singleton
public class NotificationEntityCrudStreamListener implements MessageListener {
  private final NotificationSettingsService notificationSettingsService;
  private final NotificationService notificationService;

  @Inject
  public NotificationEntityCrudStreamListener(
      NotificationSettingsService notificationSettingsService, NotificationService notificationService) {
    this.notificationSettingsService = notificationSettingsService;
    this.notificationService = notificationService;
  }

  @Override
  public boolean handleMessage(Message message) {
    if (message != null && message.hasMessage()) {
      Map<String, String> metadataMap = message.getMessage().getMetadataMap();
      if (metadataMap != null && metadataMap.get(ENTITY_TYPE) != null) {
        String action = metadataMap.get(ACTION);
        String entityType = metadataMap.get(ENTITY_TYPE);
        AccountEntityChangeDTO entityChangeDTO;
        try {
          entityChangeDTO = AccountEntityChangeDTO.parseFrom(message.getMessage().getData());
        } catch (InvalidProtocolBufferException e) {
          throw new InvalidRequestException(
              String.format("Exception in unpacking EntityChangeDTO for key %s", message.getId()), e);
        }
        switch (entityType) {
          case ACCOUNT_ENTITY:
            processAccountEntityChangeEvent(entityChangeDTO, action);
            break;
          default:
        }
      } else {
        log.info("MetadataMap or ENTITY_TYPE came as NULL for NotificationEntityCrudStreamListener");
        return false;
      }
    }
    return true;
  }

  private void processAccountEntityChangeEvent(AccountEntityChangeDTO entityChangeDTO, String action) {
    switch (action) {
      case DELETE_ACTION:
        processDeleteEvent(entityChangeDTO);
        break;
      default:
    }
  }

  private void processDeleteEvent(AccountEntityChangeDTO entityChangeDTO) {
    log.info("Processing deleting Notification data for account:" + entityChangeDTO.getAccountId());
    try {
      notificationSettingsService.deleteByAccount(entityChangeDTO.getAccountId());
    } catch (Exception e) {
      log.error("Exception deleting Notification Settings for account " + entityChangeDTO.getAccountId());
      throw e;
    }
    try {
      notificationService.deleteByAccountIdentifier(entityChangeDTO.getAccountId());
    } catch (Exception e) {
      log.error("Exception deleting Notifications Ng data for account " + entityChangeDTO.getAccountId());
      throw e;
    }

    log.info("Processed deleting Notification data for account:" + entityChangeDTO.getAccountId());
  }
}