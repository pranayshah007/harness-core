/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.outbox;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.ng.core.utils.NGYamlUtils.getYamlString;

import static io.serializer.HObjectMapper.NG_DEFAULT_OBJECT_MAPPER;

import io.harness.ModuleType;
import io.harness.annotations.dev.OwnedBy;
import io.harness.audit.Action;
import io.harness.audit.beans.AuditEntry;
import io.harness.audit.beans.ResourceDTO;
import io.harness.audit.beans.ResourceScopeDTO;
import io.harness.audit.client.api.AuditClientService;
import io.harness.beans.Scope;
import io.harness.context.GlobalContext;
import io.harness.eventsframework.EventsFrameworkConstants;
import io.harness.eventsframework.EventsFrameworkMetadataConstants;
import io.harness.eventsframework.api.EventsFrameworkDownException;
import io.harness.eventsframework.api.Producer;
import io.harness.eventsframework.entity_crud.EntityChangeDTO;
import io.harness.eventsframework.producer.Message;
import io.harness.exception.InvalidArgumentsException;
import io.harness.ng.core.api.DefaultUserGroupService;
import io.harness.ng.core.dto.UserGroupDTO;
import io.harness.ng.core.dto.UserGroupRequest;
import io.harness.ng.core.events.UserGroupCreateEvent;
import io.harness.ng.core.events.UserGroupDeleteEvent;
import io.harness.ng.core.events.UserGroupUpdateEvent;
import io.harness.outbox.OutboxEvent;
import io.harness.outbox.api.OutboxEventHandler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.google.protobuf.ByteString;
import com.google.protobuf.StringValue;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PL)
@Slf4j
public class UserGroupEventHandler implements OutboxEventHandler {
  private final ObjectMapper objectMapper;
  private final Producer eventProducer;
  private final AuditClientService auditClientService;
  private final DefaultUserGroupService defaultUserGroupService;

  @Inject
  public UserGroupEventHandler(@Named(EventsFrameworkConstants.ENTITY_CRUD) Producer eventProducer,
      AuditClientService auditClientService, DefaultUserGroupService defaultUserGroupService) {
    this.objectMapper = NG_DEFAULT_OBJECT_MAPPER;
    this.eventProducer = eventProducer;
    this.auditClientService = auditClientService;
    this.defaultUserGroupService = defaultUserGroupService;
  }

  @Override
  public boolean handle(OutboxEvent outboxEvent) {
    try {
      switch (outboxEvent.getEventType()) {
        case "UserGroupCreated":
          return handleUserGroupCreateEvent(outboxEvent);
        case "UserGroupUpdated":
          return handleUserGroupUpdateEvent(outboxEvent);
        case "UserGroupDeleted":
          return handleUserGroupDeleteEvent(outboxEvent);
        default:
          throw new InvalidArgumentsException(String.format("Not supported event type %s", outboxEvent.getEventType()));
      }
    } catch (IOException exception) {
      return false;
    }
  }

  private boolean handleUserGroupCreateEvent(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();
    UserGroupCreateEvent userGroupCreateEvent =
        objectMapper.readValue(outboxEvent.getEventData(), UserGroupCreateEvent.class);
    boolean publishedToRedis =
        publishEvent(userGroupCreateEvent.getUserGroup(), EventsFrameworkMetadataConstants.CREATE_ACTION);
    UserGroupDTO userGroupDTO = userGroupCreateEvent.getUserGroup();
    Scope scope = Scope.of(
        userGroupDTO.getAccountIdentifier(), userGroupDTO.getOrgIdentifier(), userGroupDTO.getProjectIdentifier());
    if (defaultUserGroupService.isDefaultUserGroup(scope, userGroupDTO.getIdentifier())) {
      return publishedToRedis;
    }
    AuditEntry auditEntry =
        AuditEntry.builder()
            .action(Action.CREATE)
            .module(ModuleType.CORE)
            .timestamp(outboxEvent.getCreatedAt())
            .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
            .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
            .newYaml(
                getYamlString(UserGroupRequest.builder().userGroupDTO(userGroupCreateEvent.getUserGroup()).build()))
            .insertId(outboxEvent.getId())
            .build();
    return publishedToRedis && auditClientService.publishAudit(auditEntry, globalContext);
  }

  private boolean handleUserGroupUpdateEvent(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();
    UserGroupUpdateEvent userGroupUpdateEvent =
        objectMapper.readValue(outboxEvent.getEventData(), UserGroupUpdateEvent.class);
    boolean publishedToRedis =
        publishEvent(userGroupUpdateEvent.getNewUserGroup(), EventsFrameworkMetadataConstants.UPDATE_ACTION);
    UserGroupDTO userGroupDTO = userGroupUpdateEvent.getOldUserGroup();
    Scope scope = Scope.of(
        userGroupDTO.getAccountIdentifier(), userGroupDTO.getOrgIdentifier(), userGroupDTO.getProjectIdentifier());
    if (defaultUserGroupService.isDefaultUserGroup(scope, userGroupDTO.getIdentifier())) {
      return publishedToRedis;
    }
    AuditEntry auditEntry =
        AuditEntry.builder()
            .action(Action.UPDATE)
            .module(ModuleType.CORE)
            .timestamp(outboxEvent.getCreatedAt())
            .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
            .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
            .newYaml(
                getYamlString(UserGroupRequest.builder().userGroupDTO(userGroupUpdateEvent.getNewUserGroup()).build()))
            .oldYaml(
                getYamlString(UserGroupRequest.builder().userGroupDTO(userGroupUpdateEvent.getOldUserGroup()).build()))
            .insertId(outboxEvent.getId())
            .build();
    return publishedToRedis && auditClientService.publishAudit(auditEntry, globalContext);
  }

  private boolean handleUserGroupDeleteEvent(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();
    UserGroupDeleteEvent userGroupDeleteEvent =
        objectMapper.readValue(outboxEvent.getEventData(), UserGroupDeleteEvent.class);
    boolean publishedToRedis =
        publishEvent(userGroupDeleteEvent.getUserGroup(), EventsFrameworkMetadataConstants.DELETE_ACTION);
    UserGroupDTO userGroupDTO = userGroupDeleteEvent.getUserGroup();
    Scope scope = Scope.of(
        userGroupDTO.getAccountIdentifier(), userGroupDTO.getOrgIdentifier(), userGroupDTO.getProjectIdentifier());
    if (defaultUserGroupService.isDefaultUserGroup(scope, userGroupDTO.getIdentifier())) {
      return publishedToRedis;
    }
    AuditEntry auditEntry =
        AuditEntry.builder()
            .action(Action.DELETE)
            .module(ModuleType.CORE)
            .timestamp(outboxEvent.getCreatedAt())
            .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
            .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
            .oldYaml(
                getYamlString(UserGroupRequest.builder().userGroupDTO(userGroupDeleteEvent.getUserGroup()).build()))
            .insertId(outboxEvent.getId())
            .build();
    return publishedToRedis && auditClientService.publishAudit(auditEntry, globalContext);
  }

  private boolean publishEvent(UserGroupDTO userGroup, String action) {
    try {
      eventProducer.send(
          Message.newBuilder()
              .putAllMetadata(ImmutableMap.of("accountId", userGroup.getAccountIdentifier(),
                  EventsFrameworkMetadataConstants.ENTITY_TYPE, EventsFrameworkMetadataConstants.USER_GROUP,
                  EventsFrameworkMetadataConstants.ACTION, action))
              .setData(getUserGroupPayload(userGroup))
              .build());
      return true;
    } catch (EventsFrameworkDownException e) {
      log.error("Failed to send event to events framework for user group identifier {}", userGroup.getIdentifier(), e);
      return false;
    }
  }

  private ByteString getUserGroupPayload(UserGroupDTO userGroup) {
    EntityChangeDTO.Builder builder = EntityChangeDTO.newBuilder()
                                          .setAccountIdentifier(StringValue.of(userGroup.getAccountIdentifier()))
                                          .setIdentifier(StringValue.of(userGroup.getIdentifier()));
    if (isNotEmpty(userGroup.getOrgIdentifier())) {
      builder.setOrgIdentifier(StringValue.of(userGroup.getOrgIdentifier()));
    }
    if (isNotEmpty(userGroup.getProjectIdentifier())) {
      builder.setProjectIdentifier(StringValue.of(userGroup.getProjectIdentifier()));
    }
    return builder.build().toByteString();
  }
}
