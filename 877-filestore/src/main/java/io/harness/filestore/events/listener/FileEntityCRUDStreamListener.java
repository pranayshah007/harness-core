/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.filestore.events.listener;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ACCOUNT_ENTITY;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.DELETE_ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ENTITY_TYPE;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ORGANIZATION_ENTITY;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.PROJECT_ENTITY;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;

import io.harness.annotations.dev.OwnedBy;
import io.harness.eventsframework.NgEventLogContext;
import io.harness.eventsframework.consumer.Message;
import io.harness.eventsframework.entity_crud.account.AccountEntityChangeDTO;
import io.harness.eventsframework.entity_crud.organization.OrganizationEntityChangeDTO;
import io.harness.eventsframework.entity_crud.project.ProjectEntityChangeDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.filestore.events.handler.FileEntityCRUDEventHandler;
import io.harness.logging.AutoLogContext;
import io.harness.ng.core.event.MessageListener;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.protobuf.InvalidProtocolBufferException;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDP)
@Singleton
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
public class FileEntityCRUDStreamListener implements MessageListener {
  private FileEntityCRUDEventHandler fileEntityCRUDEventHandler;

  @Override
  public boolean handleMessage(Message message) {
    if (message != null && message.hasMessage()) {
      final String messageId = message.getId();
      try (AutoLogContext ignore = new NgEventLogContext(messageId, OVERRIDE_ERROR)) {
        Map<String, String> metadataMap = message.getMessage().getMetadataMap();
        if (metadataMap.get(ENTITY_TYPE) != null) {
          String entityType = metadataMap.get(ENTITY_TYPE);

          if (ACCOUNT_ENTITY.equals(entityType)) {
            return processAccountChangeEvent(message);
          } else if (ORGANIZATION_ENTITY.equals(entityType)) {
            return processOrganizationChangeEvent(message);
          } else if (PROJECT_ENTITY.equals(entityType)) {
            return processProjectChangeEvent(message);
          }
        }
      }
    }
    return true;
  }

  private boolean processAccountChangeEvent(Message message) {
    if (!(containsAction(message) && DELETE_ACTION.equals(getAction(message)))) {
      return true;
    }

    AccountEntityChangeDTO accountEntityChangeDTO;
    try {
      accountEntityChangeDTO = AccountEntityChangeDTO.parseFrom(message.getMessage().getData());
    } catch (InvalidProtocolBufferException e) {
      throw new InvalidRequestException(
          String.format("Exception in unpacking EntityChangeDTO for key %s", message.getId()), e);
    }
    return processAccountDeleteEvent(accountEntityChangeDTO);
  }

  private boolean processAccountDeleteEvent(AccountEntityChangeDTO accountEntityChangeDTO) {
    return fileEntityCRUDEventHandler.deleteAssociatedFiles(accountEntityChangeDTO.getAccountId(), null, null);
  }

  private boolean processOrganizationChangeEvent(Message message) {
    if (!(containsAction(message) && DELETE_ACTION.equals(getAction(message)))) {
      return true;
    }

    OrganizationEntityChangeDTO organizationEntityChangeDTO;
    try {
      organizationEntityChangeDTO = OrganizationEntityChangeDTO.parseFrom(message.getMessage().getData());
    } catch (InvalidProtocolBufferException e) {
      throw new InvalidRequestException(
          String.format("Exception in unpacking EntityChangeDTO for key %s", message.getId()), e);
    }

    return processOrganizationDeleteEvent(organizationEntityChangeDTO);
  }

  private boolean processOrganizationDeleteEvent(OrganizationEntityChangeDTO organizationEntityChangeDTO) {
    return fileEntityCRUDEventHandler.deleteAssociatedFiles(
        organizationEntityChangeDTO.getAccountIdentifier(), organizationEntityChangeDTO.getIdentifier(), null);
  }

  private boolean processProjectChangeEvent(Message message) {
    if (!(containsAction(message) && DELETE_ACTION.equals(getAction(message)))) {
      return true;
    }

    ProjectEntityChangeDTO projectEntityChangeDTO;
    try {
      projectEntityChangeDTO = ProjectEntityChangeDTO.parseFrom(message.getMessage().getData());
    } catch (InvalidProtocolBufferException e) {
      throw new InvalidRequestException(
          String.format("Exception in unpacking ProjectEntityChangeDTO for key %s", message.getId()), e);
    }

    return processProjectDeleteEvent(projectEntityChangeDTO);
  }

  private boolean processProjectDeleteEvent(ProjectEntityChangeDTO projectEntityChangeDTO) {
    return fileEntityCRUDEventHandler.deleteAssociatedFiles(projectEntityChangeDTO.getAccountIdentifier(),
        projectEntityChangeDTO.getOrgIdentifier(), projectEntityChangeDTO.getIdentifier());
  }

  private String getAction(Message message) {
    return hasMessage(message) ? message.getMessage().getMetadataMap().get(ACTION) : null;
  }

  private boolean containsAction(Message message) {
    return hasMessage(message) && message.getMessage().getMetadataMap().containsKey(ACTION);
  }

  private boolean hasMessage(Message message) {
    return message != null && message.hasMessage();
  }
}
