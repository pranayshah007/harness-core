/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.outbox;

import static io.harness.audit.beans.AuthenticationInfoDTO.fromSecurityPrincipal;
import static io.harness.authorization.AuthorizationServiceHeader.NG_MANAGER;
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
import io.harness.cdng.envGroup.beans.EnvironmentGroupEntity;
import io.harness.cdng.events.EnvironmentGroupCreateEvent;
import io.harness.cdng.events.EnvironmentGroupDeleteEvent;
import io.harness.cdng.events.EnvironmentGroupUpdateEvent;
import io.harness.context.GlobalContext;
import io.harness.ng.core.events.OutboxEventConstants;
import io.harness.outbox.OutboxEvent;
import io.harness.outbox.api.OutboxEventHandler;
import io.harness.security.PrincipalContextData;
import io.harness.security.dto.Principal;
import io.harness.security.dto.ServicePrincipal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import java.io.IOException;

@OwnedBy(HarnessTeam.CDC)
public class EnvironmentGroupOutboxEventHandler implements OutboxEventHandler {
  private final ObjectMapper objectMapper;
  private final AuditClientService auditClientService;

  @Inject
  EnvironmentGroupOutboxEventHandler(AuditClientService auditClientService) {
    this.auditClientService = auditClientService;
    this.objectMapper = NG_DEFAULT_OBJECT_MAPPER;
  }
  @Override
  public boolean handle(OutboxEvent outboxEvent) {
    try {
      switch (outboxEvent.getEventType()) {
        case OutboxEventConstants.ENVIRONMENT_GROUP_CREATED:
          return handleCreated(outboxEvent);
        case OutboxEventConstants.ENVIRONMENT_GROUP_UPDATED:
          return handleUpdated(outboxEvent);
        case OutboxEventConstants.ENVIRONMENT_GROUP_DELETED:
          return handleDeleted(outboxEvent);
        default:
          return false;
      }

    } catch (IOException ex) {
      return false;
    }
  }

  private boolean handleCreated(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();
    EnvironmentGroupCreateEvent createEvent =
        objectMapper.readValue(outboxEvent.getEventData(), EnvironmentGroupCreateEvent.class);
    final EnvironmentGroupEntity environmentGroup = createEvent.getEnvironmentGroupEntity();
    AuditEntry auditEntry = AuditEntry.builder()
                                .action(Action.CREATE)
                                .module(ModuleType.CORE)
                                .insertId(outboxEvent.getId())
                                .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
                                .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
                                .timestamp(outboxEvent.getCreatedAt())
                                .newYaml(environmentGroup.getYaml())
                                .build();

    return publishEntry(globalContext, auditEntry);
  }

  private boolean handleUpdated(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();
    final EnvironmentGroupUpdateEvent updateEvent =
        objectMapper.readValue(outboxEvent.getEventData(), EnvironmentGroupUpdateEvent.class);

    AuditEntry auditEntry = AuditEntry.builder()
                                .action(Action.UPDATE)
                                .module(ModuleType.CORE)
                                .insertId(outboxEvent.getId())
                                .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
                                .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
                                .timestamp(outboxEvent.getCreatedAt())
                                .newYaml(updateEvent.getNewEnvironmentGroupEntity().getYaml())
                                .oldYaml(updateEvent.getOldEnvironmentGroupEntity().getYaml())
                                .build();

    return publishEntry(globalContext, auditEntry);
  }

  private boolean handleDeleted(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();
    EnvironmentGroupDeleteEvent deleteEvent =
        objectMapper.readValue(outboxEvent.getEventData(), EnvironmentGroupDeleteEvent.class);
    AuditEntry auditEntry = AuditEntry.builder()
                                .action(Action.DELETE)
                                .module(ModuleType.CORE)
                                .insertId(outboxEvent.getId())
                                .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
                                .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
                                .timestamp(outboxEvent.getCreatedAt())
                                .oldYaml(deleteEvent.getEnvironmentGroupEntity().getYaml())
                                .build();

    return publishEntry(globalContext, auditEntry);
  }

  private boolean publishEntry(GlobalContext globalContext, AuditEntry auditEntry) {
    Principal principal = null;
    if (globalContext.get(PRINCIPAL_CONTEXT) == null) {
      principal = new ServicePrincipal(NG_MANAGER.getServiceId());
    } else if (globalContext.get(PRINCIPAL_CONTEXT) != null) {
      principal = ((PrincipalContextData) globalContext.get(PRINCIPAL_CONTEXT)).getPrincipal();
    }
    return auditClientService.publishAudit(auditEntry, fromSecurityPrincipal(principal), globalContext);
  }
}
