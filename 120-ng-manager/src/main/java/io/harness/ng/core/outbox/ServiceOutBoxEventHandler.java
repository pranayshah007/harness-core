/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.outbox;

import static io.harness.audit.beans.AuthenticationInfoDTO.fromSecurityPrincipal;
import static io.harness.authorization.AuthorizationServiceHeader.NG_MANAGER;
import static io.harness.ng.core.utils.NGYamlUtils.getYamlString;
import static io.harness.security.PrincipalContextData.PRINCIPAL_CONTEXT;

import static io.serializer.HObjectMapper.NG_DEFAULT_OBJECT_MAPPER;

import io.harness.ModuleType;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.audit.Action;
import io.harness.audit.beans.AuditEntry;
import io.harness.audit.beans.ResourceDTO;
import io.harness.audit.beans.ResourceScopeDTO;
import io.harness.audit.client.api.AuditClientService;
import io.harness.context.GlobalContext;
import io.harness.ng.core.events.OutboxEventConstants;
import io.harness.ng.core.events.ServiceCreateEvent;
import io.harness.ng.core.events.ServiceDeleteEvent;
import io.harness.ng.core.events.ServiceForceDeleteEvent;
import io.harness.ng.core.events.ServiceUpdateEvent;
import io.harness.ng.core.events.ServiceUpsertEvent;
import io.harness.ng.core.service.entity.ServiceRequest;
import io.harness.outbox.OutboxEvent;
import io.harness.outbox.api.OutboxEventHandler;
import io.harness.security.PrincipalContextData;
import io.harness.security.dto.Principal;
import io.harness.security.dto.ServicePrincipal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public class ServiceOutBoxEventHandler implements OutboxEventHandler {
  private final ObjectMapper objectMapper;
  private final AuditClientService auditClientService;

  @Inject
  ServiceOutBoxEventHandler(AuditClientService auditClientService) {
    this.auditClientService = auditClientService;
    this.objectMapper = NG_DEFAULT_OBJECT_MAPPER;
  }

  private boolean handlerServiceCreated(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();
    ServiceCreateEvent serviceCreateEvent =
        objectMapper.readValue(outboxEvent.getEventData(), ServiceCreateEvent.class);
    AuditEntry auditEntry =
        AuditEntry.builder()
            .action(Action.CREATE)
            .module(ModuleType.CORE)
            .insertId(outboxEvent.getId())
            .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
            .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
            .timestamp(outboxEvent.getCreatedAt())
            .newYaml(getYamlString(ServiceRequest.builder().service(serviceCreateEvent.getService()).build()))
            .build();

    Principal principal = null;
    if (globalContext.get(PRINCIPAL_CONTEXT) == null) {
      principal = new ServicePrincipal(NG_MANAGER.getServiceId());
    } else if (globalContext.get(PRINCIPAL_CONTEXT) != null) {
      principal = ((PrincipalContextData) globalContext.get(PRINCIPAL_CONTEXT)).getPrincipal();
    }
    return auditClientService.publishAudit(auditEntry, fromSecurityPrincipal(principal), globalContext);
  }

  private boolean handlerServiceUpserted(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();
    ServiceUpsertEvent serviceUpsertEvent =
        objectMapper.readValue(outboxEvent.getEventData(), ServiceUpsertEvent.class);
    AuditEntry auditEntry =
        AuditEntry.builder()
            .action(Action.UPSERT)
            .module(ModuleType.CORE)
            .insertId(outboxEvent.getId())
            .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
            .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
            .timestamp(outboxEvent.getCreatedAt())
            .newYaml(getYamlString(ServiceRequest.builder().service(serviceUpsertEvent.getService()).build()))
            .oldYaml(serviceUpsertEvent.getOldService() != null
                    ? getYamlString(ServiceRequest.builder().service(serviceUpsertEvent.getOldService()).build())
                    : null)
            .build();

    Principal principal = null;
    if (globalContext.get(PRINCIPAL_CONTEXT) == null) {
      principal = new ServicePrincipal(NG_MANAGER.getServiceId());
    } else if (globalContext.get(PRINCIPAL_CONTEXT) != null) {
      principal = ((PrincipalContextData) globalContext.get(PRINCIPAL_CONTEXT)).getPrincipal();
    }

    log.info(String.format(
        "Handing service upsert flow for audits with outbox event id: [%s], resource type: [%s], resource id: [%s]}",
        outboxEvent.getId(), outboxEvent.getResource().getType(), outboxEvent.getResource().getIdentifier()));

    return auditClientService.publishAudit(auditEntry, fromSecurityPrincipal(principal), globalContext);
  }
  private boolean handlerServiceUpdated(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();
    ServiceUpdateEvent serviceUpdateEvent =
        objectMapper.readValue(outboxEvent.getEventData(), ServiceUpdateEvent.class);
    AuditEntry auditEntry =
        AuditEntry.builder()
            .action(Action.UPDATE)
            .module(ModuleType.CORE)
            .insertId(outboxEvent.getId())
            .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
            .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
            .timestamp(outboxEvent.getCreatedAt())
            .newYaml(getYamlString(ServiceRequest.builder().service(serviceUpdateEvent.getNewService()).build()))
            .oldYaml(getYamlString(ServiceRequest.builder().service(serviceUpdateEvent.getOldService()).build()))
            .build();

    Principal principal = null;
    if (globalContext.get(PRINCIPAL_CONTEXT) == null) {
      principal = new ServicePrincipal(NG_MANAGER.getServiceId());
    } else if (globalContext.get(PRINCIPAL_CONTEXT) != null) {
      principal = ((PrincipalContextData) globalContext.get(PRINCIPAL_CONTEXT)).getPrincipal();
    }
    return auditClientService.publishAudit(auditEntry, fromSecurityPrincipal(principal), globalContext);
  }
  private boolean handlerServiceDeleted(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();
    ServiceDeleteEvent serviceDeleteEvent =
        objectMapper.readValue(outboxEvent.getEventData(), ServiceDeleteEvent.class);
    AuditEntry auditEntry =
        AuditEntry.builder()
            .action(Action.DELETE)
            .module(ModuleType.CORE)
            .insertId(outboxEvent.getId())
            .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
            .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
            .timestamp(outboxEvent.getCreatedAt())
            .oldYaml(getYamlString(ServiceRequest.builder().service(serviceDeleteEvent.getService()).build()))
            .build();

    Principal principal = null;
    if (globalContext.get(PRINCIPAL_CONTEXT) == null) {
      principal = new ServicePrincipal(NG_MANAGER.getServiceId());
    } else if (globalContext.get(PRINCIPAL_CONTEXT) != null) {
      principal = ((PrincipalContextData) globalContext.get(PRINCIPAL_CONTEXT)).getPrincipal();
    }
    return auditClientService.publishAudit(auditEntry, fromSecurityPrincipal(principal), globalContext);
  }
  private boolean handlerServiceForceDeleted(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();

    ServiceForceDeleteEvent serviceForceDeleteEvent =
        objectMapper.readValue(outboxEvent.getEventData(), ServiceForceDeleteEvent.class);

    AuditEntry auditEntry =
        AuditEntry.builder()
            .action(Action.FORCE_DELETE)
            .module(ModuleType.CORE)
            .insertId(outboxEvent.getId())
            .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
            .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
            .timestamp(outboxEvent.getCreatedAt())
            .oldYaml(getYamlString(ServiceRequest.builder().service(serviceForceDeleteEvent.getService()).build()))
            .build();

    Principal principal = null;
    if (globalContext.get(PRINCIPAL_CONTEXT) == null) {
      principal = new ServicePrincipal(NG_MANAGER.getServiceId());
    } else if (globalContext.get(PRINCIPAL_CONTEXT) != null) {
      principal = ((PrincipalContextData) globalContext.get(PRINCIPAL_CONTEXT)).getPrincipal();
    }
    return auditClientService.publishAudit(auditEntry, fromSecurityPrincipal(principal), globalContext);
  }

  @Override
  public boolean handle(OutboxEvent outboxEvent) {
    log.info(String.format(
        "starting service outbox flow for audits with outbox event id: [%s], resource type: [%s], resource id: [%s]}",
        outboxEvent.getId(), outboxEvent.getResource().getType(), outboxEvent.getResource().getIdentifier()));
    try {
      switch (outboxEvent.getEventType()) {
        case OutboxEventConstants.SERVICE_CREATED:
          return handlerServiceCreated(outboxEvent);
        case OutboxEventConstants.SERVICE_UPSERTED:
          return handlerServiceUpserted(outboxEvent);
        case OutboxEventConstants.SERVICE_UPDATED:
          return handlerServiceUpdated(outboxEvent);
        case OutboxEventConstants.SERVICE_DELETED:
          return handlerServiceDeleted(outboxEvent);
        case OutboxEventConstants.SERVICE_FORCE_DELETED:
          return handlerServiceForceDeleted(outboxEvent);
        default:
          return false;
      }

    } catch (IOException ex) {
      log.info(String.format(
          "Exception while handling service outbox flow for audits with outbox event id: [%s], resource type: [%s], resource id: [%s]}",
          outboxEvent.getId(), outboxEvent.getResource().getType(), outboxEvent.getResource().getIdentifier()));
      return false;
    } catch (Exception e) {
      log.info(String.format(
          "Exception while handling service outbox flow for audits with outbox event id: [%s], resource type: [%s], resource id: [%s]}",
          outboxEvent.getId(), outboxEvent.getResource().getType(), outboxEvent.getResource().getIdentifier()));
      throw e;
    }
  }
}
