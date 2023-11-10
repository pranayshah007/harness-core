/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.outbox;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.ng.core.events.ServiceAccountCreateEvent.SERVICE_ACCOUNT_CREATED;
import static io.harness.ng.core.events.ServiceAccountDeleteEvent.SERVICE_ACCOUNT_DELETED;
import static io.harness.ng.core.events.ServiceAccountUpdateEvent.SERVICE_ACCOUNT_UPDATED;
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
import io.harness.eventsframework.entity_crud.serviceaccount.ServiceAccountEntityChangeDTO;
import io.harness.eventsframework.producer.Message;
import io.harness.exception.InvalidArgumentsException;
import io.harness.ng.core.Resource;
import io.harness.ng.core.dto.ServiceAccountRequest;
import io.harness.ng.core.events.ServiceAccountCreateEvent;
import io.harness.ng.core.events.ServiceAccountDeleteEvent;
import io.harness.ng.core.events.ServiceAccountUpdateEvent;
import io.harness.ng.core.mapper.ResourceScopeMapper;
import io.harness.outbox.OutboxEvent;
import io.harness.outbox.api.OutboxEventHandler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.google.protobuf.StringValue;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PL)
@Slf4j
public class ServiceAccountEventHandler implements OutboxEventHandler {
  private final ObjectMapper objectMapper;
  private final Producer eventProducer;
  private final AuditClientService auditClientService;

  @Inject
  public ServiceAccountEventHandler(
      @Named(EventsFrameworkConstants.ENTITY_CRUD) Producer eventProducer, AuditClientService auditClientService) {
    this.objectMapper = NG_DEFAULT_OBJECT_MAPPER;
    this.eventProducer = eventProducer;
    this.auditClientService = auditClientService;
  }

  @Override
  public boolean handle(OutboxEvent outboxEvent) {
    try {
      switch (outboxEvent.getEventType()) {
        case SERVICE_ACCOUNT_CREATED:
          return handleServiceAccountCreateEvent(outboxEvent);
        case SERVICE_ACCOUNT_UPDATED:
          return handleServiceAccountUpdateEvent(outboxEvent);
        case SERVICE_ACCOUNT_DELETED:
          return handleServiceAccountDeleteEvent(outboxEvent);
        default:
          throw new InvalidArgumentsException(String.format("Not supported event type %s", outboxEvent.getEventType()));
      }
    } catch (IOException exception) {
      log.error("Failed to handle " + outboxEvent.getEventType() + " event", exception);
      return false;
    }
  }

  private boolean handleServiceAccountCreateEvent(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();
    Scope scope = ResourceScopeMapper.getScopeFromResourceScope(outboxEvent.getResourceScope());
    String accountIdentifier = scope.getAccountIdentifier();
    String orgIdentifier = scope.getOrgIdentifier();
    String projectIdentifier = scope.getProjectIdentifier();
    Resource resource = outboxEvent.getResource();


    boolean publishedToRedis = publishEvent(accountIdentifier, orgIdentifier, projectIdentifier,
        outboxEvent.getResource().getIdentifier(),resource, EventsFrameworkMetadataConstants.CREATE_ACTION);

    ServiceAccountCreateEvent serviceAccountCreateEvent =
        objectMapper.readValue(outboxEvent.getEventData(), ServiceAccountCreateEvent.class);
    AuditEntry auditEntry =
        AuditEntry.builder()
            .action(Action.CREATE)
            .module(ModuleType.CORE)
            .newYaml(getYamlString(
                ServiceAccountRequest.builder().serviceAccount(serviceAccountCreateEvent.getServiceAccount()).build()))
            .timestamp(outboxEvent.getCreatedAt())
            .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
            .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
            .insertId(outboxEvent.getId())
            .build();
    return publishedToRedis && auditClientService.publishAudit(auditEntry, globalContext);
  }

  private boolean handleServiceAccountUpdateEvent(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();
    Scope scope = ResourceScopeMapper.getScopeFromResourceScope(outboxEvent.getResourceScope());
    Resource resource = outboxEvent.getResource();
    String accountIdentifier = scope.getAccountIdentifier();
    String orgIdentifier = scope.getOrgIdentifier();
    String projectIdentifier = scope.getProjectIdentifier();


    boolean publishedToRedis = publishEvent(accountIdentifier, orgIdentifier, projectIdentifier,
        outboxEvent.getResource().getIdentifier(),resource, EventsFrameworkMetadataConstants.UPDATE_ACTION);
    ServiceAccountUpdateEvent serviceAccountUpdateEvent =
        objectMapper.readValue(outboxEvent.getEventData(), ServiceAccountUpdateEvent.class);
    AuditEntry auditEntry =
        AuditEntry.builder()
            .action(Action.UPDATE)
            .module(ModuleType.CORE)
            .newYaml(getYamlString(ServiceAccountRequest.builder()
                                       .serviceAccount(serviceAccountUpdateEvent.getNewServiceAccount())
                                       .build()))
            .oldYaml(getYamlString(ServiceAccountRequest.builder()
                                       .serviceAccount(serviceAccountUpdateEvent.getOldServiceAccount())
                                       .build()))
            .timestamp(outboxEvent.getCreatedAt())
            .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
            .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
            .insertId(outboxEvent.getId())
            .build();
    return publishedToRedis && auditClientService.publishAudit(auditEntry, globalContext);
  }

  private boolean handleServiceAccountDeleteEvent(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();
    Scope scope = ResourceScopeMapper.getScopeFromResourceScope(outboxEvent.getResourceScope());
    String accountIdentifier = scope.getAccountIdentifier();
    String orgIdentifier = scope.getOrgIdentifier();
    String projectIdentifier = scope.getProjectIdentifier();
    Resource resource = outboxEvent.getResource();

    boolean publishedToRedis = publishEvent(accountIdentifier, orgIdentifier, projectIdentifier,
        outboxEvent.getResource().getIdentifier(),resource,  EventsFrameworkMetadataConstants.DELETE_ACTION);
    ServiceAccountDeleteEvent serviceAccountDeleteEvent =
        objectMapper.readValue(outboxEvent.getEventData(), ServiceAccountDeleteEvent.class);
    AuditEntry auditEntry =
        AuditEntry.builder()
            .action(Action.DELETE)
            .module(ModuleType.CORE)
            .oldYaml(getYamlString(
                ServiceAccountRequest.builder().serviceAccount(serviceAccountDeleteEvent.getServiceAccount()).build()))
            .timestamp(outboxEvent.getCreatedAt())
            .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
            .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
            .insertId(outboxEvent.getId())
            .build();
    return publishedToRedis && auditClientService.publishAudit(auditEntry, globalContext);
  }

  private boolean publishEvent(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String identifier, Resource resource, String action) {
    try {
      eventProducer.send(
          Message.newBuilder()
              .putAllMetadata(ImmutableMap.of("accountId", accountIdentifier,
                  EventsFrameworkMetadataConstants.ENTITY_TYPE, EventsFrameworkMetadataConstants.SERVICE_ACCOUNT_ENTITY,
                  EventsFrameworkMetadataConstants.ACTION, action))
              .setData(ServiceAccountEntityChangeDTO.newBuilder()
                           .setIdentifier(identifier)
                           .setOrgIdentifier(orgIdentifier)
                           .setAccountIdentifier(accountIdentifier)
                           .setProjectIdentifier(projectIdentifier)
                           .setUniqueId(resource.getUniqueId())
                           .build()
                           .toByteString())
              .build());
      return true;
    } catch (EventsFrameworkDownException e) {
      log.error("Failed to send " + action + " event to events framework service account identifier: " + identifier, e);
      return false;
    }
  }
}
