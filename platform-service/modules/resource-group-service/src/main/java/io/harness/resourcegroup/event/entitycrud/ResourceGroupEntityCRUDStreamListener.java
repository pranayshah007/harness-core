/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.resourcegroup.event.entitycrud;

import static io.harness.eventsframework.EventsFrameworkMetadataConstants.*;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ACCOUNT_ENTITY;

import io.harness.eventHandlers.ResourceGroupCRUDEventHandler;
import io.harness.eventsframework.consumer.Message;
import io.harness.eventsframework.entity_crud.account.AccountEntityChangeDTO;
import io.harness.eventsframework.entity_crud.organization.OrganizationEntityChangeDTO;
import io.harness.eventsframework.entity_crud.project.ProjectEntityChangeDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.event.MessageListener;

import com.google.inject.Inject;
import com.google.protobuf.InvalidProtocolBufferException;
import java.util.Map;

public class ResourceGroupEntityCRUDStreamListener implements MessageListener {
  ResourceGroupCRUDEventHandler resourceGroupCRUDEventHandler;

  @Inject
  public ResourceGroupEntityCRUDStreamListener(ResourceGroupCRUDEventHandler resourceGroupCRUDEventHandler) {
    this.resourceGroupCRUDEventHandler = resourceGroupCRUDEventHandler;
  }

  @Override
  public boolean handleMessage(Message message) {
    if (message != null && message.hasMessage()) {
      Map<String, String> metadataMap = message.getMessage().getMetadataMap();
      if (metadataMap != null && metadataMap.get(ENTITY_TYPE) != null) {
        String entityType = metadataMap.get(ENTITY_TYPE);
        switch (entityType) {
          case ORGANIZATION_ENTITY:
            return processOrganizationChangeEvent(message);
          case PROJECT_ENTITY:
            return processProjectChangeEvent(message);
          case ACCOUNT_ENTITY:
            return processAccountChangeEvent(message);
          default:
        }
      }
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
        case RESTORE_ACTION:
          break;
        case DELETE_ACTION:
          return processOrganizationDeleteEvent(organizationEntityChangeDTO);
        default:
      }
    }
    return true;
  }

  private boolean processOrganizationDeleteEvent(OrganizationEntityChangeDTO organizationEntityChangeDTO) {
    return resourceGroupCRUDEventHandler.deleteAssociatedResourceGroup(
        organizationEntityChangeDTO.getAccountIdentifier(), organizationEntityChangeDTO.getIdentifier(), null);
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
        case RESTORE_ACTION:
          break;
        case DELETE_ACTION:
          return processProjectDeleteEvent(projectEntityChangeDTO);
        default:
      }
    }
    return true;
  }

  private boolean processProjectDeleteEvent(ProjectEntityChangeDTO projectEntityChangeDTO) {
    return resourceGroupCRUDEventHandler.deleteAssociatedResourceGroup(projectEntityChangeDTO.getAccountIdentifier(),
        projectEntityChangeDTO.getOrgIdentifier(), projectEntityChangeDTO.getIdentifier());
  }

  private boolean processAccountChangeEvent(Message message) {
    if (!(message.getMessage().getMetadataMap().containsKey(ACTION)
            && DELETE_ACTION.equals(message.getMessage().getMetadataMap().get(ACTION)))) {
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
    return resourceGroupCRUDEventHandler.deleteAssociatedResourceGroup(
        accountEntityChangeDTO.getAccountId(), null, null);
  }
}
