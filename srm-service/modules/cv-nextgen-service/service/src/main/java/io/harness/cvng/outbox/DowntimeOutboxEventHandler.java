/*
 * Copyright 2023 Harness Inc. All rights reserved.
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
import io.harness.cvng.events.downtime.DowntimeCreateEvent;
import io.harness.cvng.events.downtime.DowntimeDeleteEvent;
import io.harness.cvng.events.downtime.DowntimeUpdateEvent;
import io.harness.outbox.OutboxEvent;
import io.harness.outbox.api.OutboxEventHandler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import io.serializer.HObjectMapper;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DowntimeOutboxEventHandler implements OutboxEventHandler {
  private final AuditClientService auditClientService;
  private final ObjectMapper objectMapper;

  @Inject
  DowntimeOutboxEventHandler(AuditClientService auditClientService) {
    this.auditClientService = auditClientService;
    this.objectMapper = HObjectMapper.NG_DEFAULT_OBJECT_MAPPER;
  }

  @Override
  public boolean handle(OutboxEvent outboxEvent) {
    log.info("Outbox event handler: {}", outboxEvent);
    GlobalContext globalContext = outboxEvent.getGlobalContext();
    try {
      switch (outboxEvent.getEventType()) {
        case "DowntimeCreateEvent":
          return handleDowntimeCreateEvent(outboxEvent, globalContext);
        case "DowntimeUpdateEvent":
          return handleDowntimeUpdateEvent(outboxEvent, globalContext);
        case "DowntimeDeleteEvent":
          return handleDowntimeDeleteEvent(outboxEvent, globalContext);
        default:
          return false;
      }
    } catch (IOException exception) {
      return false;
    }
  }

  private boolean handleDowntimeCreateEvent(OutboxEvent outboxEvent, GlobalContext globalContext) throws IOException {
    DowntimeCreateEvent downtimeCreateEvent =
        objectMapper.readValue(outboxEvent.getEventData(), DowntimeCreateEvent.class);
    AuditEntry auditEntry = AuditEntry.builder()
                                .action(Action.CREATE)
                                .module(ModuleType.CV)
                                .newYaml(getYamlString(downtimeCreateEvent.getDowntimeDTO()))
                                .timestamp(outboxEvent.getCreatedAt())
                                .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
                                .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
                                .insertId(outboxEvent.getId())
                                .build();
    return auditClientService.publishAudit(auditEntry, globalContext);
  }

  private boolean handleDowntimeUpdateEvent(OutboxEvent outboxEvent, GlobalContext globalContext) throws IOException {
    DowntimeUpdateEvent downtimeUpdateEvent =
        objectMapper.readValue(outboxEvent.getEventData(), DowntimeUpdateEvent.class);
    AuditEntry auditEntry = AuditEntry.builder()
                                .action(Action.UPDATE)
                                .module(ModuleType.CV)
                                .oldYaml(getYamlString(downtimeUpdateEvent.getOldDowntimeDTO()))
                                .newYaml(getYamlString(downtimeUpdateEvent.getNewDowntimeDTO()))
                                .timestamp(outboxEvent.getCreatedAt())
                                .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
                                .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
                                .insertId(outboxEvent.getId())
                                .build();
    return auditClientService.publishAudit(auditEntry, globalContext);
  }

  private boolean handleDowntimeDeleteEvent(OutboxEvent outboxEvent, GlobalContext globalContext) throws IOException {
    DowntimeDeleteEvent downtimeDeleteEvent =
        objectMapper.readValue(outboxEvent.getEventData(), DowntimeDeleteEvent.class);
    AuditEntry auditEntry = AuditEntry.builder()
                                .action(Action.DELETE)
                                .module(ModuleType.CV)
                                .oldYaml(getYamlString(downtimeDeleteEvent.getDowntimeDTO()))
                                .timestamp(outboxEvent.getCreatedAt())
                                .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
                                .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
                                .insertId(outboxEvent.getId())
                                .build();
    return auditClientService.publishAudit(auditEntry, globalContext);
  }
}
