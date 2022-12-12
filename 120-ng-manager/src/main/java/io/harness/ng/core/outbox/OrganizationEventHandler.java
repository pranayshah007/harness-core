/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.outbox;

import static io.harness.NGConstants.DEFAULT_ORG_IDENTIFIER;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.audit.beans.AuthenticationInfoDTO.fromSecurityPrincipal;
import static io.harness.authorization.AuthorizationServiceHeader.NG_MANAGER;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ORGANIZATION_ENTITY;
import static io.harness.ng.core.utils.NGYamlUtils.getYamlString;
import static io.harness.security.PrincipalContextData.PRINCIPAL_CONTEXT;

import static io.serializer.HObjectMapper.NG_DEFAULT_OBJECT_MAPPER;

import io.harness.ModuleType;
import io.harness.annotations.dev.OwnedBy;
import io.harness.audit.Action;
import io.harness.audit.beans.AuditEntry;
import io.harness.audit.beans.ResourceDTO;
import io.harness.audit.beans.ResourceScopeDTO;
import io.harness.audit.client.api.AuditClientService;
import io.harness.context.GlobalContext;
import io.harness.eventsframework.EventsFrameworkConstants;
import io.harness.eventsframework.EventsFrameworkMetadataConstants;
import io.harness.eventsframework.api.EventsFrameworkDownException;
import io.harness.eventsframework.api.Producer;
import io.harness.eventsframework.entity_crud.organization.OrganizationEntityChangeDTO;
import io.harness.eventsframework.producer.Message;
import io.harness.exception.InvalidArgumentsException;
import io.harness.ng.core.OrgScope;
import io.harness.ng.core.dto.OrganizationRequest;
import io.harness.ng.core.events.OrganizationCreateEvent;
import io.harness.ng.core.events.OrganizationDeleteEvent;
import io.harness.ng.core.events.OrganizationRestoreEvent;
import io.harness.ng.core.events.OrganizationUpdateEvent;
import io.harness.outbox.OutboxEvent;
import io.harness.outbox.api.OutboxEventHandler;
import io.harness.security.PrincipalContextData;
import io.harness.security.dto.Principal;
import io.harness.security.dto.ServicePrincipal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.google.protobuf.ByteString;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PL)
@Slf4j
public class OrganizationEventHandler implements OutboxEventHandler {
  private final ObjectMapper objectMapper;
  private final Producer eventProducer;
  private final AuditClientService auditClientService;

  @Inject
  public OrganizationEventHandler(
      @Named(EventsFrameworkConstants.ENTITY_CRUD) Producer eventProducer, AuditClientService auditClientService) {
    this.objectMapper = NG_DEFAULT_OBJECT_MAPPER;
    this.eventProducer = eventProducer;
    this.auditClientService = auditClientService;
  }

  public boolean handle(OutboxEvent outboxEvent) {
    try {
      switch (outboxEvent.getEventType()) {
        case "OrganizationCreated":
          return handleOrganizationCreateEvent(outboxEvent);
        case "OrganizationUpdated":
          return handleOrganizationUpdateEvent(outboxEvent);
        case "OrganizationDeleted":
          return handleOrganizationDeleteEvent(outboxEvent);
        case "OrganizationRestored":
          return handleOrganizationRestoreEvent(outboxEvent);
        default:
          throw new InvalidArgumentsException(String.format("Not supported event type %s", outboxEvent.getEventType()));
      }
    } catch (IOException ioe) {
      return false;
    }
  }

  private boolean handleOrganizationCreateEvent(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();
    String accountIdentifier = ((OrgScope) outboxEvent.getResourceScope()).getAccountIdentifier();
    boolean publishedToRedis = publishOrganizationChangeEventToRedis(
        accountIdentifier, outboxEvent.getResource().getIdentifier(), EventsFrameworkMetadataConstants.CREATE_ACTION);
    OrganizationCreateEvent organizationCreateEvent =
        objectMapper.readValue(outboxEvent.getEventData(), OrganizationCreateEvent.class);
    AuditEntry auditEntry =
        AuditEntry.builder()
            .action(Action.CREATE)
            .module(ModuleType.CORE)
            .newYaml(getYamlString(
                OrganizationRequest.builder().organization(organizationCreateEvent.getOrganization()).build()))
            .timestamp(outboxEvent.getCreatedAt())
            .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
            .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
            .insertId(outboxEvent.getId())
            .build();
    Principal principal = null;
    if (globalContext.get(PRINCIPAL_CONTEXT) == null
        && DEFAULT_ORG_IDENTIFIER.equals(outboxEvent.getResource().getIdentifier())) {
      principal = new ServicePrincipal(NG_MANAGER.getServiceId());
    } else if (globalContext.get(PRINCIPAL_CONTEXT) != null) {
      principal = ((PrincipalContextData) globalContext.get(PRINCIPAL_CONTEXT)).getPrincipal();
    }
    return publishedToRedis
        && auditClientService.publishAudit(auditEntry, fromSecurityPrincipal(principal), globalContext);
  }

  private boolean handleOrganizationUpdateEvent(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();
    String accountIdentifier = ((OrgScope) outboxEvent.getResourceScope()).getAccountIdentifier();
    boolean publishedToRedis = publishOrganizationChangeEventToRedis(
        accountIdentifier, outboxEvent.getResource().getIdentifier(), EventsFrameworkMetadataConstants.UPDATE_ACTION);
    OrganizationUpdateEvent organizationUpdateEvent =
        objectMapper.readValue(outboxEvent.getEventData(), OrganizationUpdateEvent.class);
    AuditEntry auditEntry =
        AuditEntry.builder()
            .action(Action.UPDATE)
            .module(ModuleType.CORE)
            .newYaml(getYamlString(
                OrganizationRequest.builder().organization(organizationUpdateEvent.getNewOrganization()).build()))
            .oldYaml(getYamlString(
                OrganizationRequest.builder().organization(organizationUpdateEvent.getOldOrganization()).build()))
            .timestamp(outboxEvent.getCreatedAt())
            .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
            .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
            .insertId(outboxEvent.getId())
            .build();

    return publishedToRedis && auditClientService.publishAudit(auditEntry, globalContext);
  }

  private boolean handleOrganizationDeleteEvent(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();
    String accountIdentifier = ((OrgScope) outboxEvent.getResourceScope()).getAccountIdentifier();
    boolean publishedToRedis = publishOrganizationChangeEventToRedis(
        accountIdentifier, outboxEvent.getResource().getIdentifier(), EventsFrameworkMetadataConstants.DELETE_ACTION);
    OrganizationDeleteEvent organizationDeleteEvent =
        objectMapper.readValue(outboxEvent.getEventData(), OrganizationDeleteEvent.class);
    AuditEntry auditEntry =
        AuditEntry.builder()
            .action(Action.DELETE)
            .module(ModuleType.CORE)
            .oldYaml(getYamlString(
                OrganizationRequest.builder().organization(organizationDeleteEvent.getOrganization()).build()))
            .timestamp(outboxEvent.getCreatedAt())
            .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
            .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
            .insertId(outboxEvent.getId())
            .build();

    return publishedToRedis && auditClientService.publishAudit(auditEntry, globalContext);
  }

  private boolean handleOrganizationRestoreEvent(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();
    String accountIdentifier = ((OrgScope) outboxEvent.getResourceScope()).getAccountIdentifier();
    boolean publishedToRedis = publishOrganizationChangeEventToRedis(
        accountIdentifier, outboxEvent.getResource().getIdentifier(), EventsFrameworkMetadataConstants.RESTORE_ACTION);
    OrganizationRestoreEvent organizationRestoreEvent =
        objectMapper.readValue(outboxEvent.getEventData(), OrganizationRestoreEvent.class);
    AuditEntry auditEntry =
        AuditEntry.builder()
            .action(Action.RESTORE)
            .module(ModuleType.CORE)
            .newYaml(getYamlString(
                OrganizationRequest.builder().organization(organizationRestoreEvent.getOrganization()).build()))
            .timestamp(outboxEvent.getCreatedAt())
            .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
            .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
            .insertId(outboxEvent.getId())
            .build();

    return publishedToRedis && auditClientService.publishAudit(auditEntry, globalContext);
  }

  private boolean publishOrganizationChangeEventToRedis(String accountIdentifier, String identifier, String action) {
    try {
      String eventId = eventProducer.send(Message.newBuilder()
                                              .putAllMetadata(ImmutableMap.of("accountId", accountIdentifier,
                                                  EventsFrameworkMetadataConstants.ENTITY_TYPE, ORGANIZATION_ENTITY,
                                                  EventsFrameworkMetadataConstants.ACTION, action))
                                              .setData(getOrganizationPayload(accountIdentifier, identifier))
                                              .build());
      log.info("Produced event id:[{}] for orgId:[{}], accountId: [{}], action:[{}]", eventId, identifier,
          accountIdentifier, action);
    } catch (EventsFrameworkDownException e) {
      log.error("Failed to send event to events framework orgIdentifier: " + identifier, e);
      return false;
    }
    return true;
  }

  private ByteString getOrganizationPayload(String accountIdentifier, String identifier) {
    return OrganizationEntityChangeDTO.newBuilder()
        .setIdentifier(identifier)
        .setAccountIdentifier(accountIdentifier)
        .build()
        .toByteString();
  }
}
