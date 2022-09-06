/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.event;

import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ACCOUNT_ENTITY;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.CREATE_ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.DELETE_ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ENTITY_TYPE;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ORGANIZATION_ENTITY;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.PROJECT_ENTITY;

import static org.apache.commons.lang3.StringUtils.stripToNull;

import io.harness.beans.Scope;
import io.harness.eventsframework.consumer.Message;
import io.harness.eventsframework.entity_crud.account.AccountEntityChangeDTO;
import io.harness.eventsframework.entity_crud.organization.OrganizationEntityChangeDTO;
import io.harness.eventsframework.entity_crud.project.ProjectEntityChangeDTO;
import io.harness.exception.DuplicateFieldException;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.api.DefaultUserGroupService;
import io.harness.ng.core.api.UserGroupService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.protobuf.InvalidProtocolBufferException;
import java.util.Collections;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class UserGroupEntityCRUDStreamListener implements MessageListener {
  private final UserGroupService userGroupService;
  private final DefaultUserGroupService defaultUserGroupService;

  @Inject
  public UserGroupEntityCRUDStreamListener(
      UserGroupService userGroupService, DefaultUserGroupService defaultUserGroupService) {
    this.userGroupService = userGroupService;
    this.defaultUserGroupService = defaultUserGroupService;
  }

  @Override
  public boolean handleMessage(Message message) {
    if (message != null && message.hasMessage()) {
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
    }
    return true;
  }

  private boolean processAccountChangeEvent(Message message) {
    AccountEntityChangeDTO accountEntityChangeDTO;
    try {
      accountEntityChangeDTO = AccountEntityChangeDTO.parseFrom(message.getMessage().getData());
    } catch (InvalidProtocolBufferException e) {
      throw new InvalidRequestException(
          String.format("Exception in unpacking AccountEntityChangeDTO for key %s", message.getId()), e);
    }
    String action = message.getMessage().getMetadataMap().get(ACTION);
    Scope scope = Scope.builder().accountIdentifier(stripToNull(accountEntityChangeDTO.getAccountId())).build();
    if (action != null) {
      if (action.equals(DELETE_ACTION)) {
        return processDeleteEvent(scope);
      }
      if (action.equals(CREATE_ACTION)) {
        return processCreateEvent(scope);
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
    Scope scope = Scope.builder()
                      .accountIdentifier(stripToNull(organizationEntityChangeDTO.getAccountIdentifier()))
                      .orgIdentifier(stripToNull(organizationEntityChangeDTO.getIdentifier()))
                      .build();
    if (action != null) {
      if (action.equals(DELETE_ACTION)) {
        return processDeleteEvent(scope);
      }
      if (action.equals(CREATE_ACTION)) {
        processCreateEvent(scope);
      }
    }
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
    Scope scope = Scope.builder()
                      .accountIdentifier(stripToNull(projectEntityChangeDTO.getAccountIdentifier()))
                      .orgIdentifier(stripToNull(projectEntityChangeDTO.getOrgIdentifier()))
                      .projectIdentifier(stripToNull(projectEntityChangeDTO.getIdentifier()))
                      .build();
    if (action != null) {
      if (action.equals(DELETE_ACTION)) {
        return processDeleteEvent(scope);
      }
      if (action.equals(CREATE_ACTION)) {
        return processCreateEvent(scope);
      }
    }
    return true;
  }

  private boolean processDeleteEvent(Scope scope) {
    try {
      userGroupService.deleteByScope(scope);
    } catch (Exception e) {
      log.error("Could not process scope delete event for user group. Exception", e);
      return false;
    }
    return true;
  }

  private boolean processCreateEvent(Scope scope) {
    try {
      defaultUserGroupService.create(scope, Collections.emptyList());
      log.info("processed scope create event for user group.");
    } catch (DuplicateFieldException ex) {
      // this is safe to assume Default UG is created.
      log.info("this is safe to assume Default UG is created.");
    } catch (Exception ex) {
      log.error("Could not process scope create event for user group.", ex);
      return false;
    }
    return true;
  }
}
