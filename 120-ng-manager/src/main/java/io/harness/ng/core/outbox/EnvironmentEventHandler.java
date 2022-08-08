/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.outbox;

import static io.harness.AuthorizationServiceHeader.NG_MANAGER;
import static io.harness.audit.beans.AuthenticationInfoDTO.fromSecurityPrincipal;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
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
import io.harness.ng.core.environment.beans.Environment;
import io.harness.ng.core.environment.mappers.EnvironmentMapper;
import io.harness.ng.core.events.EnvironmentCreateEvent;
import io.harness.ng.core.events.EnvironmentDeleteEvent;
import io.harness.ng.core.events.EnvironmentUpdatedEvent;
import io.harness.ng.core.events.EnvironmentUpsertEvent;
import io.harness.ng.core.events.OutboxEventConstants;
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
public class EnvironmentEventHandler implements OutboxEventHandler {
  private final ObjectMapper objectMapper;
  private final AuditClientService auditClientService;

  @Inject
  EnvironmentEventHandler(AuditClientService auditClientService) {
    this.auditClientService = auditClientService;
    this.objectMapper = NG_DEFAULT_OBJECT_MAPPER;
  }

  private boolean handlerEnvironmentCreated(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();
    EnvironmentCreateEvent environmentCreateEvent =
        objectMapper.readValue(outboxEvent.getEventData(), EnvironmentCreateEvent.class);
    final Environment environment = environmentCreateEvent.getEnvironment();
    AuditEntry auditEntry = AuditEntry.builder()
                                .action(Action.CREATE)
                                .module(ModuleType.CORE)
                                .insertId(outboxEvent.getId())
                                .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
                                .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
                                .timestamp(outboxEvent.getCreatedAt())
                                .newYaml(isNotEmpty(environment.getYaml())
                                        ? environment.getYaml()
                                        : getYamlString(EnvironmentMapper.toNGEnvironmentConfig(environment)))
                                .build();

    Principal principal = null;
    if (globalContext.get(PRINCIPAL_CONTEXT) == null) {
      principal = new ServicePrincipal(NG_MANAGER.getServiceId());
    } else if (globalContext.get(PRINCIPAL_CONTEXT) != null) {
      principal = ((PrincipalContextData) globalContext.get(PRINCIPAL_CONTEXT)).getPrincipal();
    }
    return auditClientService.publishAudit(auditEntry, fromSecurityPrincipal(principal), globalContext);
  }

  private boolean handlerEnvironmentUpserted(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();
    EnvironmentUpsertEvent environmentUpsertEvent =
        objectMapper.readValue(outboxEvent.getEventData(), EnvironmentUpsertEvent.class);
    final Environment environment = environmentUpsertEvent.getEnvironment();
    AuditEntry auditEntry = AuditEntry.builder()
                                .action(Action.UPSERT)
                                .module(ModuleType.CORE)
                                .insertId(outboxEvent.getId())
                                .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
                                .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
                                .timestamp(outboxEvent.getCreatedAt())
                                .newYaml(isNotEmpty(environment.getYaml())
                                        ? environment.getYaml()
                                        : getYamlString(EnvironmentMapper.toNGEnvironmentConfig(environment)))
                                .build();

    Principal principal = null;
    if (globalContext.get(PRINCIPAL_CONTEXT) == null) {
      principal = new ServicePrincipal(NG_MANAGER.getServiceId());
    } else if (globalContext.get(PRINCIPAL_CONTEXT) != null) {
      principal = ((PrincipalContextData) globalContext.get(PRINCIPAL_CONTEXT)).getPrincipal();
    }
    return auditClientService.publishAudit(auditEntry, fromSecurityPrincipal(principal), globalContext);
  }
  private boolean handlerEnvironmentUpdated(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();
    EnvironmentUpdatedEvent environmentUpdateEvent =
        objectMapper.readValue(outboxEvent.getEventData(), EnvironmentUpdatedEvent.class);
    final Environment newEnvironment = environmentUpdateEvent.getNewEnvironment();
    final Environment oldEnvironment = environmentUpdateEvent.getOldEnvironment();
    AuditEntry auditEntry = AuditEntry.builder()
                                .action(Action.UPDATE)
                                .module(ModuleType.CORE)
                                .insertId(outboxEvent.getId())
                                .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
                                .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
                                .timestamp(outboxEvent.getCreatedAt())
                                .newYaml(isNotEmpty(newEnvironment.getYaml())
                                        ? newEnvironment.getYaml()
                                        : getYamlString(EnvironmentMapper.toNGEnvironmentConfig(newEnvironment)))
                                .oldYaml(isNotEmpty(oldEnvironment.getYaml())
                                        ? oldEnvironment.getYaml()
                                        : getYamlString(EnvironmentMapper.toNGEnvironmentConfig(oldEnvironment)))
                                .build();

    Principal principal = null;
    if (globalContext.get(PRINCIPAL_CONTEXT) == null) {
      principal = new ServicePrincipal(NG_MANAGER.getServiceId());
    } else if (globalContext.get(PRINCIPAL_CONTEXT) != null) {
      principal = ((PrincipalContextData) globalContext.get(PRINCIPAL_CONTEXT)).getPrincipal();
    }
    return auditClientService.publishAudit(auditEntry, fromSecurityPrincipal(principal), globalContext);
  }
  private boolean handlerEnvironmentDeleted(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();
    EnvironmentDeleteEvent environmentDeleteEvent =
        objectMapper.readValue(outboxEvent.getEventData(), EnvironmentDeleteEvent.class);
    final Environment environment = environmentDeleteEvent.getEnvironment();
    AuditEntry auditEntry = AuditEntry.builder()
                                .action(Action.DELETE)
                                .module(ModuleType.CORE)
                                .insertId(outboxEvent.getId())
                                .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
                                .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
                                .timestamp(outboxEvent.getCreatedAt())
                                .oldYaml(isNotEmpty(environment.getYaml())
                                        ? environment.getYaml()
                                        : getYamlString(EnvironmentMapper.toNGEnvironmentConfig(environment)))
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
    try {
      switch (outboxEvent.getEventType()) {
        case OutboxEventConstants.ENVIRONMENT_CREATED:
          return handlerEnvironmentCreated(outboxEvent);
        case OutboxEventConstants.ENVIRONMENT_UPSERTED:
          return handlerEnvironmentUpserted(outboxEvent);
        case OutboxEventConstants.ENVIRONMENT_UPDATED:
          return handlerEnvironmentUpdated(outboxEvent);
        case OutboxEventConstants.ENVIRONMENT_DELETED:
          return handlerEnvironmentDeleted(outboxEvent);
        default:
          return false;
      }

    } catch (IOException ex) {
      return false;
    }
  }
}
