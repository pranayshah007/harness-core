/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.event;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ACCOUNT_ENTITY;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.DELETE_ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ENTITY_TYPE;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ORGANIZATION_ENTITY;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.PROJECT_ENTITY;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;

import static org.apache.commons.lang3.StringUtils.stripToNull;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.eventsframework.NgEventLogContext;
import io.harness.eventsframework.consumer.Message;
import io.harness.eventsframework.entity_crud.account.AccountEntityChangeDTO;
import io.harness.eventsframework.entity_crud.organization.OrganizationEntityChangeDTO;
import io.harness.eventsframework.entity_crud.project.ProjectEntityChangeDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.logging.AutoLogContext;

import com.google.protobuf.InvalidProtocolBufferException;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PL)
@Slf4j
public abstract class AbstractEntityCleanupListener implements MessageListener {
  public boolean cleanupAllScopes(Message message) {
    if (message != null && message.hasMessage()) {
      try (AutoLogContext ignore1 = new NgEventLogContext(message.getId(), OVERRIDE_ERROR)) {
        Map<String, String> metadataMap = message.getMessage().getMetadataMap();
        if (metadataMap != null && metadataMap.get(ENTITY_TYPE) != null) {
          String entityType = metadataMap.get(ENTITY_TYPE);
          switch (entityType) {
            case ACCOUNT_ENTITY:
              return processAccountChangeEvent(message);
            case ORGANIZATION_ENTITY:
              return processOrganizationChangeEvent(message);
            case PROJECT_ENTITY:
              return processProjectChangeEvent(message);
            default:
          }
        }
      } catch (Exception e) {
        log.error("Encountered error while handling the cleanup message {}", message.getId(), e);
        throw new InvalidRequestException(
            String.format("Exception in processing cleanup event for the push message %s", message.getId()), e);
      }
    } else {
      log.error("The message for processing cleanup event was null or has no message {}", message);
    }
    return true;
  }

  public boolean cleanupAccountScope(Message message) {
    if (message != null && message.hasMessage()) {
      try (AutoLogContext ignore1 = new NgEventLogContext(message.getId(), OVERRIDE_ERROR)) {
        Map<String, String> metadataMap = message.getMessage().getMetadataMap();
        if (metadataMap != null && metadataMap.get(ENTITY_TYPE) != null) {
          String entityType = metadataMap.get(ENTITY_TYPE);
          switch (entityType) {
            case ACCOUNT_ENTITY:
              return processAccountChangeEvent(message);
            default:
          }
        }
      } catch (Exception e) {
        log.error("Encountered error while handling the cleanup message {}", message.getId(), e);
        throw new InvalidRequestException(
            String.format("Exception in processing cleanup event for the push message %s", message.getId()), e);
      }
    } else {
      log.error("The message for processing cleanup event was null or has no message {}", message);
    }
    return true;
  }

  public boolean processAccountChangeEvent(Message message) {
    AccountEntityChangeDTO accountEntityChangeDTO;
    try {
      accountEntityChangeDTO = AccountEntityChangeDTO.parseFrom(message.getMessage().getData());
    } catch (InvalidProtocolBufferException e) {
      throw new InvalidRequestException(
          String.format("Exception in unpacking AccountEntityChangeDTO for key %s", message.getId()), e);
    }
    String action = message.getMessage().getMetadataMap().get(ACTION);
    Scope scope = Scope.builder().accountIdentifier(stripToNull(accountEntityChangeDTO.getAccountId())).build();
    if (action != null && action.equals(DELETE_ACTION)) {
      return processDeleteEvent(scope);
    }
    return true;
  }

  public boolean processOrganizationChangeEvent(Message message) {
    OrganizationEntityChangeDTO organizationEntityChangeDTO;
    try {
      organizationEntityChangeDTO = OrganizationEntityChangeDTO.parseFrom(message.getMessage().getData());
    } catch (InvalidProtocolBufferException e) {
      throw new InvalidRequestException(
          String.format("Exception in unpacking EntityChangeDTO for key %s", message.getId()), e);
    }
    String action = message.getMessage().getMetadataMap().get(ACTION);
    Scope scope = Scope.builder()
                      .accountIdentifier(stripToNull(organizationEntityChangeDTO.getAccountIdentifier()))
                      .orgIdentifier(stripToNull(organizationEntityChangeDTO.getIdentifier()))
                      .build();
    if (action != null && action.equals(DELETE_ACTION)) {
      return processDeleteEvent(scope);
    }
    return true;
  }

  public boolean processProjectChangeEvent(Message message) {
    ProjectEntityChangeDTO projectEntityChangeDTO;
    try {
      projectEntityChangeDTO = ProjectEntityChangeDTO.parseFrom(message.getMessage().getData());
    } catch (InvalidProtocolBufferException e) {
      throw new InvalidRequestException(
          String.format("Exception in unpacking ProjectEntityChangeDTO for key %s", message.getId()), e);
    }
    String action = message.getMessage().getMetadataMap().get(ACTION);
    Scope scope = Scope.builder()
                      .accountIdentifier(stripToNull(projectEntityChangeDTO.getAccountIdentifier()))
                      .orgIdentifier(stripToNull(projectEntityChangeDTO.getOrgIdentifier()))
                      .projectIdentifier(stripToNull(projectEntityChangeDTO.getIdentifier()))
                      .build();
    if (action != null && action.equals(DELETE_ACTION)) {
      return processDeleteEvent(scope);
    }
    return true;
  }

  public abstract boolean processDeleteEvent(Scope scope);
}
