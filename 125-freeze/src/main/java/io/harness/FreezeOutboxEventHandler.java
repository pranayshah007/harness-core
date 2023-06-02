/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.events.FreezeEntityCreateEvent.DEPLOYMENT_FREEZE_CREATED;
import static io.harness.events.FreezeEntityDeleteEvent.DEPLOYMENT_FREEZE_DELETED;
import static io.harness.events.FreezeEntityUpdateEvent.DEPLOYMENT_FREEZE_UPDATED;

import static io.serializer.HObjectMapper.NG_DEFAULT_OBJECT_MAPPER;

import io.harness.annotations.dev.OwnedBy;
import io.harness.audit.Action;
import io.harness.audit.beans.AuditEntry;
import io.harness.audit.beans.ResourceDTO;
import io.harness.audit.beans.ResourceScopeDTO;
import io.harness.audit.client.api.AuditClientService;
import io.harness.context.GlobalContext;
import io.harness.events.FreezeEntityCreateEvent;
import io.harness.events.FreezeEntityDeleteEvent;
import io.harness.events.FreezeEntityUpdateEvent;
import io.harness.exception.InvalidArgumentsException;
import io.harness.freeze.beans.FreezeType;
import io.harness.outbox.OutboxEvent;
import io.harness.outbox.api.OutboxEventHandler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDC)
@Slf4j
public class FreezeOutboxEventHandler implements OutboxEventHandler {
  private final ObjectMapper objectMapper;
  private final AuditClientService auditClientService;

  @Inject
  public FreezeOutboxEventHandler(AuditClientService auditClientService) {
    this.objectMapper = NG_DEFAULT_OBJECT_MAPPER;
    this.auditClientService = auditClientService;
  }

  @Override
  public boolean handle(OutboxEvent outboxEvent) {
    try {
      switch (outboxEvent.getEventType()) {
        case DEPLOYMENT_FREEZE_CREATED:
          return handleFreezeCreateEvent(outboxEvent);
        case DEPLOYMENT_FREEZE_UPDATED:
          return handleFreezeUpdateEvent(outboxEvent);
        case DEPLOYMENT_FREEZE_DELETED:
          return handleFreezeDeleteEvent(outboxEvent);
        default:
          throw new InvalidArgumentsException(String.format("Not supported event type %s", outboxEvent.getEventType()));
      }
    } catch (IOException e) {
      log.info("Exception caught while publishing audit", e);
      return false;
    }
  }

  private boolean handleFreezeCreateEvent(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();
    FreezeEntityCreateEvent freezeEntityCreateEvent =
        objectMapper.readValue(outboxEvent.getEventData(), FreezeEntityCreateEvent.class);
    if (FreezeType.GLOBAL.equals(freezeEntityCreateEvent.getCreatedFreeze().getType())) {
      return true;
    }
    AuditEntry auditEntry = AuditEntry.builder()
                                .action(Action.CREATE)
                                .module(ModuleType.CORE)
                                .newYaml(freezeEntityCreateEvent.getCreatedFreeze().getYaml())
                                .timestamp(outboxEvent.getCreatedAt())
                                .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
                                .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
                                .insertId(outboxEvent.getId())
                                .build();
    return auditClientService.publishAudit(auditEntry, globalContext);
  }

  private boolean handleFreezeUpdateEvent(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();
    FreezeEntityUpdateEvent freezeEntityUpdateEvent =
        objectMapper.readValue(outboxEvent.getEventData(), FreezeEntityUpdateEvent.class);
    AuditEntry auditEntry = AuditEntry.builder()
                                .action(Action.UPDATE)
                                .module(ModuleType.CORE)
                                .oldYaml(freezeEntityUpdateEvent.getOldFreeze().getYaml())
                                .newYaml(freezeEntityUpdateEvent.getNewFreeze().getYaml())
                                .timestamp(outboxEvent.getCreatedAt())
                                .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
                                .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
                                .insertId(outboxEvent.getId())
                                .build();
    return auditClientService.publishAudit(auditEntry, globalContext);
  }

  private boolean handleFreezeDeleteEvent(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();
    FreezeEntityDeleteEvent freezeEntityDeleteEvent =
        objectMapper.readValue(outboxEvent.getEventData(), FreezeEntityDeleteEvent.class);
    AuditEntry auditEntry = AuditEntry.builder()
                                .action(Action.DELETE)
                                .module(ModuleType.CORE)
                                .oldYaml(freezeEntityDeleteEvent.getDeletedFreeze().getYaml())
                                .timestamp(outboxEvent.getCreatedAt())
                                .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
                                .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
                                .insertId(outboxEvent.getId())
                                .build();
    return auditClientService.publishAudit(auditEntry, globalContext);
  }
}
