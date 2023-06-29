/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.outbox;

import static io.harness.audit.beans.AuthenticationInfoDTO.fromSecurityPrincipal;
import static io.harness.authorization.AuthorizationServiceHeader.NG_MANAGER;
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
import io.harness.ng.core.events.EnvironmentForceDeleteEvent;
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
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

@Slf4j
@OwnedBy(HarnessTeam.CDC)
public class EnvironmentOutboxEventHandler implements OutboxEventHandler {
  private final ObjectMapper objectMapper;
  private final AuditClientService auditClientService;

  private static final String OLD_YAML = "oldYaml";
  private static final String NEW_YAML = "newYaml";
  private static final String EMPTY_YAML = "";

  @Inject
  EnvironmentOutboxEventHandler(AuditClientService auditClientService) {
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
    Map<String, String> yamls = updateYaml(outboxEvent);

    AuditEntry auditEntry = AuditEntry.builder()
                                .action(Action.UPDATE)
                                .module(ModuleType.CORE)
                                .insertId(outboxEvent.getId())
                                .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
                                .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
                                .timestamp(outboxEvent.getCreatedAt())
                                .newYaml(isNotEmpty(yamls.get(NEW_YAML)) ? yamls.get(NEW_YAML) : null)
                                .oldYaml(isNotEmpty(yamls.get(OLD_YAML)) ? yamls.get(OLD_YAML) : null)
                                .build();

    Principal principal = null;
    if (globalContext.get(PRINCIPAL_CONTEXT) == null) {
      principal = new ServicePrincipal(NG_MANAGER.getServiceId());
    } else if (globalContext.get(PRINCIPAL_CONTEXT) != null) {
      principal = ((PrincipalContextData) globalContext.get(PRINCIPAL_CONTEXT)).getPrincipal();
    }
    return auditClientService.publishAudit(auditEntry, fromSecurityPrincipal(principal), globalContext);
  }

  private Map<String, String> updateYaml(OutboxEvent outboxEvent) throws IOException {
    Map<String, String> yamls = new HashMap<>();
    EnvironmentUpdatedEvent environmentUpdateEvent =
        objectMapper.readValue(outboxEvent.getEventData(), EnvironmentUpdatedEvent.class);

    switch (environmentUpdateEvent.getResourceType()) {
      case SERVICE_OVERRIDE:
        return getAuditYamlDiffForServiceOverride(environmentUpdateEvent);
      case INFRASTRUCTURE:
        yamls.put(OLD_YAML,
            environmentUpdateEvent.getOldInfrastructureEntity() == null
                ? EMPTY_YAML
                : environmentUpdateEvent.getOldInfrastructureEntity().getYaml());
        yamls.put(NEW_YAML,
            environmentUpdateEvent.getNewInfrastructureEntity() == null
                ? EMPTY_YAML
                : environmentUpdateEvent.getNewInfrastructureEntity().getYaml());
        return yamls;
      default:
        yamls.put(OLD_YAML,
            isNotEmpty(environmentUpdateEvent.getOldEnvironment().getYaml())
                ? environmentUpdateEvent.getOldEnvironment().getYaml()
                : getYamlString(EnvironmentMapper.toNGEnvironmentConfig(environmentUpdateEvent.getOldEnvironment())));
        yamls.put(NEW_YAML,
            isNotEmpty(environmentUpdateEvent.getNewEnvironment().getYaml())
                ? environmentUpdateEvent.getNewEnvironment().getYaml()
                : getYamlString(EnvironmentMapper.toNGEnvironmentConfig(environmentUpdateEvent.getNewEnvironment())));
        return yamls;
    }
  }

  @NotNull
  private Map<String, String> getAuditYamlDiffForServiceOverride(EnvironmentUpdatedEvent environmentUpdateEvent) {
    Map<String, String> yamls = new HashMap<>();
    if (environmentUpdateEvent.isOverrideAuditV2()) {
      yamls.put(OLD_YAML,
          environmentUpdateEvent.getOldOverrideAuditEventDTO() == null
              ? EMPTY_YAML
              : environmentUpdateEvent.getOldOverrideAuditEventDTO().getYaml());
      yamls.put(NEW_YAML,
          environmentUpdateEvent.getNewOverrideAuditEventDTO() == null
              ? EMPTY_YAML
              : environmentUpdateEvent.getNewOverrideAuditEventDTO().getYaml());
    } else {
      yamls.put(OLD_YAML,
          environmentUpdateEvent.getOldServiceOverridesEntity() == null
              ? EMPTY_YAML
              : environmentUpdateEvent.getOldServiceOverridesEntity().getYaml());
      yamls.put(NEW_YAML,
          environmentUpdateEvent.getNewServiceOverridesEntity() == null
              ? EMPTY_YAML
              : environmentUpdateEvent.getNewServiceOverridesEntity().getYaml());
    }
    return yamls;
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

  private boolean handlerEnvironmentForceDeleted(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();
    EnvironmentForceDeleteEvent environmentForceDeleteEvent =
        objectMapper.readValue(outboxEvent.getEventData(), EnvironmentForceDeleteEvent.class);
    final Environment environment = environmentForceDeleteEvent.getEnvironment();
    AuditEntry auditEntry = AuditEntry.builder()
                                .action(Action.FORCE_DELETE)
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
        case OutboxEventConstants.ENVIRONMENT_FORCE_DELETED:
          return handlerEnvironmentForceDeleted(outboxEvent);
        default:
          return false;
      }

    } catch (IOException ex) {
      return false;
    }
  }
}
