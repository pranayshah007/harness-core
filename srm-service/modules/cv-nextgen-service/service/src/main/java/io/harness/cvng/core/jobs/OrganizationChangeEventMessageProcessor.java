/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.jobs;

import io.harness.eventsframework.EventsFrameworkMetadataConstants;
import io.harness.eventsframework.consumer.Message;
import io.harness.eventsframework.entity_crud.organization.OrganizationEntityChangeDTO;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Singleton;
import com.google.protobuf.InvalidProtocolBufferException;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class OrganizationChangeEventMessageProcessor extends EntityChangeEventMessageProcessor {
  @Inject private Injector injector;

  @Override
  public void processMessage(Message message) {
    Preconditions.checkState(
        validateMessage(message), "Invalid message received by Organization Change Event Processor");

    OrganizationEntityChangeDTO organizationEntityChangeDTO;
    try {
      organizationEntityChangeDTO = OrganizationEntityChangeDTO.parseFrom(message.getMessage().getData());
    } catch (InvalidProtocolBufferException e) {
      log.error("Exception in unpacking EntityChangeDTO for key {}", message.getId(), e);
      throw new IllegalStateException(e);
    }

    Map<String, String> metadataMap = message.getMessage().getMetadataMap();
    if (metadataMap.containsKey(EventsFrameworkMetadataConstants.ACTION)) {
      switch (metadataMap.get(EventsFrameworkMetadataConstants.ACTION)) {
        case EventsFrameworkMetadataConstants.DELETE_ACTION:
          processDeleteAction(organizationEntityChangeDTO);
          return;
        default:
      }
    }
  }

  @VisibleForTesting
  void processDeleteAction(OrganizationEntityChangeDTO organizationEntityChangeDTO) {
    ENTITIES_MAP.forEach((entity, handler) -> {
      log.info("Deleting all records of entity {} for accountId {} orgIdentifier {}", entity.getSimpleName(),
          organizationEntityChangeDTO.getAccountIdentifier(), organizationEntityChangeDTO.getIdentifier());
      try {
        injector.getInstance(handler).deleteByOrgIdentifier(
            entity, organizationEntityChangeDTO.getAccountIdentifier(), organizationEntityChangeDTO.getIdentifier());
        log.info("Deleted all records of entity {} for accountId {} orgIdentifier {}", entity.getSimpleName(),
            organizationEntityChangeDTO.getAccountIdentifier(), organizationEntityChangeDTO.getIdentifier());
      } catch (Exception exception) {
        log.error("Error while deleting all records of entity {} for accountId {} orgIdentifier {}",
            entity.getSimpleName(), organizationEntityChangeDTO.getAccountIdentifier(),
            organizationEntityChangeDTO.getIdentifier(), exception);
      }
    });
  }

  private boolean validateMessage(Message message) {
    return message != null && message.hasMessage() && message.getMessage().getMetadataMap() != null
        && message.getMessage().getMetadataMap().containsKey(EventsFrameworkMetadataConstants.ENTITY_TYPE)
        && EventsFrameworkMetadataConstants.ORGANIZATION_ENTITY.equals(
            message.getMessage().getMetadataMap().get(EventsFrameworkMetadataConstants.ENTITY_TYPE));
  }
}
