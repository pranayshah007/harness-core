/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.event;

import static io.harness.annotations.dev.HarnessTeam.DX;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.CREATE_ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.DELETE_ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ENTITY_TYPE;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ORGANIZATION_ENTITY;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.PROJECT_ENTITY;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.RESTORE_ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.SECRET_ENTITY;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.UPDATE_ACTION;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;

import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.validator.SecretEntityCRUDEventHandler;
import io.harness.eventsframework.NgEventLogContext;
import io.harness.eventsframework.consumer.Message;
import io.harness.eventsframework.entity_crud.EntityChangeDTO;
import io.harness.eventsframework.entity_crud.organization.OrganizationEntityChangeDTO;
import io.harness.eventsframework.entity_crud.project.ProjectEntityChangeDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.logging.AutoLogContext;
import io.harness.ng.core.api.SecretCrudService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.protobuf.InvalidProtocolBufferException;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

@OwnedBy(DX)
@Singleton
@Slf4j
public class SecretEntityCRUDStreamListener implements MessageListener {
  private final SecretCrudService secretCrudService;
  private final SecretEntityCRUDEventHandler secretEntityCRUDEventHandler;

  @Inject
  public SecretEntityCRUDStreamListener(
      SecretCrudService secretCrudService, SecretEntityCRUDEventHandler secretEntityCRUDEventHandler) {
    this.secretCrudService = secretCrudService;
    this.secretEntityCRUDEventHandler = secretEntityCRUDEventHandler;
  }

  @Override
  public boolean handleMessage(Message message) {
    if (message != null && message.hasMessage()) {
      final String messageId = message.getId();
      try (AutoLogContext ignore = new NgEventLogContext(messageId, OVERRIDE_ERROR)) {
        Map<String, String> metadataMap = message.getMessage().getMetadataMap();
        if (metadataMap != null && metadataMap.get(ENTITY_TYPE) != null) {
          String entityType = metadataMap.get(ENTITY_TYPE);
          if (SECRET_ENTITY.equals(entityType)) {
            log.info("Processing the secrets crud event with the id {}", messageId);
            EntityChangeDTO entityChangeDTO;
            try {
              entityChangeDTO = EntityChangeDTO.parseFrom(message.getMessage().getData());
            } catch (InvalidProtocolBufferException e) {
              throw new InvalidRequestException(
                  String.format("Exception in unpacking EntityChangeDTO  for id %s", messageId), e);
            }
            String action = metadataMap.get(ACTION);
            if (action != null) {
              processSecretEntityChangeEvent(entityChangeDTO, action);
              log.info("Completed processing the secrets crud event with the id {}", messageId);
              return true;
            } else {
              log.error(
                  "Unable to complete processing the secrets crud event with the id {} because ACTION for the event was null",
                  messageId);
            }
          } else if (PROJECT_ENTITY.equals(entityType)) {
            return processProjectChangeEvent(message);
          } else if (ORGANIZATION_ENTITY.equals(entityType)) {
            return processOrganizationChangeEvent(message);
          }
        }
      }
    }
    return true;
  }

  private boolean processSecretEntityChangeEvent(EntityChangeDTO entityChangeDTO, @NotNull String action) {
    switch (action) {
      case UPDATE_ACTION:
        return secretEntityCRUDEventHandler.handleUpdate(entityChangeDTO);
      default:
    }
    return true;
  }

  private boolean processOrganizationChangeEvent(Message message) {
    OrganizationEntityChangeDTO organizationEntityChangeDTO;
    try {
      organizationEntityChangeDTO = OrganizationEntityChangeDTO.parseFrom(message.getMessage().getData());
    } catch (InvalidProtocolBufferException e) {
      throw new InvalidRequestException(
          String.format("Exception in unpacking EntityChangeDTO for key %s", message.getId()), e);
    }
    String action = message.getMessage().getMetadataMap().get(ACTION);
    if (action != null) {
      switch (action) {
        case CREATE_ACTION:
          return processOrganizationCreateEvent(organizationEntityChangeDTO);
        case DELETE_ACTION:
          return processOrganizationDeleteEvent(organizationEntityChangeDTO);
        case RESTORE_ACTION:
          return processOrganizationRestoreEvent(organizationEntityChangeDTO);
        default:
      }
    }
    return true;
  }

  private boolean processOrganizationCreateEvent(OrganizationEntityChangeDTO organizationEntityChangeDTO) {
    return true;
  }

  private boolean processOrganizationDeleteEvent(OrganizationEntityChangeDTO organizationEntityChangeDTO) {
    return secretEntityCRUDEventHandler.deleteAssociatedSecrets(
        organizationEntityChangeDTO.getAccountIdentifier(), organizationEntityChangeDTO.getIdentifier(), null);
  }

  private boolean processOrganizationRestoreEvent(OrganizationEntityChangeDTO organizationEntityChangeDTO) {
    return true;
  }

  private boolean processProjectChangeEvent(Message message) {
    ProjectEntityChangeDTO projectEntityChangeDTO;
    try {
      projectEntityChangeDTO = ProjectEntityChangeDTO.parseFrom(message.getMessage().getData());
    } catch (InvalidProtocolBufferException e) {
      throw new InvalidRequestException(
          String.format("Exception in unpacking ProjectEntityChangeDTO for key %s", message.getId()), e);
    }
    String action = message.getMessage().getMetadataMap().get(ACTION);
    if (action != null) {
      switch (action) {
        case CREATE_ACTION:
          return processProjectCreateEvent(projectEntityChangeDTO);
        case DELETE_ACTION:
          return processProjectDeleteEvent(projectEntityChangeDTO);
        case RESTORE_ACTION:
          return processProjectRestoreEvent(projectEntityChangeDTO);
        default:
      }
    }
    return true;
  }

  private boolean processProjectCreateEvent(ProjectEntityChangeDTO projectEntityChangeDTO) {
    return true;
  }

  private boolean processProjectDeleteEvent(ProjectEntityChangeDTO projectEntityChangeDTO) {
    return secretEntityCRUDEventHandler.deleteAssociatedSecrets(projectEntityChangeDTO.getAccountIdentifier(),
        projectEntityChangeDTO.getOrgIdentifier(), projectEntityChangeDTO.getIdentifier());
  }

  private boolean processProjectRestoreEvent(ProjectEntityChangeDTO projectEntityChangeDTO) {
    return true;
  }
}
