/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.outbox;

import static io.harness.ng.core.utils.NGYamlUtils.getYamlString;

import io.harness.ModuleType;
import io.harness.audit.Action;
import io.harness.audit.beans.AuditEntry;
import io.harness.audit.beans.ResourceDTO;
import io.harness.audit.beans.ResourceScopeDTO;
import io.harness.audit.client.api.AuditClientService;
import io.harness.context.GlobalContext;
import io.harness.cvng.events.servicelevelobjective.ServiceLevelObjectiveCreateEvent;
import io.harness.cvng.events.servicelevelobjective.ServiceLevelObjectiveDeleteEvent;
import io.harness.cvng.events.servicelevelobjective.ServiceLevelObjectiveUpdateEvent;
import io.harness.outbox.OutboxEvent;
import io.harness.outbox.api.OutboxEventHandler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import io.serializer.HObjectMapper;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ServiceLevelObjectiveOutboxEventHandler implements OutboxEventHandler {
  private final AuditClientService auditClientService;
  private final ObjectMapper objectMapper;

  @Inject
  ServiceLevelObjectiveOutboxEventHandler(AuditClientService auditClientService) {
    this.auditClientService = auditClientService;
    this.objectMapper = HObjectMapper.NG_DEFAULT_OBJECT_MAPPER;
  }

  @Override
  public boolean handle(OutboxEvent outboxEvent) {
    log.info("Outbox event handler: {}", outboxEvent);
    GlobalContext globalContext = outboxEvent.getGlobalContext();
    try {
      switch (outboxEvent.getEventType()) {
        case "ServiceLevelObjectiveCreateEvent":
          return handleMonitoredServiceCreateEvent(outboxEvent, globalContext);
        case "ServiceLevelObjectiveUpdateEvent":
          return handleMonitoredServiceUpdateEvent(outboxEvent, globalContext);
        case "ServiceLevelObjectiveDeleteEvent":
          return handleMonitoredServiceDeleteEvent(outboxEvent, globalContext);
        default:
          return false;
      }
    } catch (IOException exception) {
      return false;
    }
  }

  private boolean handleMonitoredServiceCreateEvent(OutboxEvent outboxEvent, GlobalContext globalContext)
      throws IOException {
    ServiceLevelObjectiveCreateEvent serviceLevelObjectiveCreateEvent =
        objectMapper.readValue(outboxEvent.getEventData(), ServiceLevelObjectiveCreateEvent.class);
    AuditEntry auditEntry =
        AuditEntry.builder()
            .action(Action.CREATE)
            .module(ModuleType.CV)
            .newYaml(getYamlString(serviceLevelObjectiveCreateEvent.getNewServiceLevelObjectiveDTO()))
            .timestamp(outboxEvent.getCreatedAt())
            .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
            .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
            .insertId(outboxEvent.getId())
            .build();
    return auditClientService.publishAudit(auditEntry, globalContext);
  }

  private boolean handleMonitoredServiceUpdateEvent(OutboxEvent outboxEvent, GlobalContext globalContext)
      throws IOException {
    ServiceLevelObjectiveUpdateEvent monitoredServiceUpdateEvent =
        objectMapper.readValue(outboxEvent.getEventData(), ServiceLevelObjectiveUpdateEvent.class);
    AuditEntry auditEntry = AuditEntry.builder()
                                .action(Action.UPDATE)
                                .module(ModuleType.CV)
                                .oldYaml(getYamlString(monitoredServiceUpdateEvent.getOldServiceLevelObjectiveDTO()))
                                .newYaml(getYamlString(monitoredServiceUpdateEvent.getNewServiceLevelObjectiveDTO()))
                                .timestamp(outboxEvent.getCreatedAt())
                                .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
                                .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
                                .insertId(outboxEvent.getId())
                                .build();
    return auditClientService.publishAudit(auditEntry, globalContext);
  }

  private boolean handleMonitoredServiceDeleteEvent(OutboxEvent outboxEvent, GlobalContext globalContext)
      throws IOException {
    ServiceLevelObjectiveDeleteEvent monitoredServiceDeleteEvent =
        objectMapper.readValue(outboxEvent.getEventData(), ServiceLevelObjectiveDeleteEvent.class);
    AuditEntry auditEntry = AuditEntry.builder()
                                .action(Action.DELETE)
                                .module(ModuleType.CV)
                                .oldYaml(getYamlString(monitoredServiceDeleteEvent.getOldServiceLevelObjectiveDTO()))
                                .timestamp(outboxEvent.getCreatedAt())
                                .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
                                .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
                                .insertId(outboxEvent.getId())
                                .build();
    return auditClientService.publishAudit(auditEntry, globalContext);
  }
}
