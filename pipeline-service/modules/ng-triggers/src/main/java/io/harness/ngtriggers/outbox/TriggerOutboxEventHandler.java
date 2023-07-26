/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.outbox;

import io.harness.ModuleType;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.audit.Action;
import io.harness.audit.beans.AuditEntry;
import io.harness.audit.beans.ResourceDTO;
import io.harness.audit.beans.ResourceScopeDTO;
import io.harness.audit.client.api.AuditClientService;
import io.harness.context.GlobalContext;
import io.harness.ngtriggers.events.TriggerCreateEvent;
import io.harness.ngtriggers.events.TriggerDeleteEvent;
import io.harness.ngtriggers.events.TriggerOutboxEvents;
import io.harness.ngtriggers.events.TriggerUpdateEvent;
import io.harness.outbox.OutboxEvent;
import io.harness.outbox.api.OutboxEventHandler;
import io.harness.pms.yaml.YamlUtils;

import com.google.inject.Inject;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.CI)
public class TriggerOutboxEventHandler implements OutboxEventHandler {
  private final AuditClientService auditClientService;
  @Inject
  public TriggerOutboxEventHandler(AuditClientService auditClientService) {
    this.auditClientService = auditClientService;
  }

  private boolean handleTriggerCreateEvent(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();
    TriggerCreateEvent event =
        YamlUtils.readWithDefaultObjectMapper(outboxEvent.getEventData(), TriggerCreateEvent.class);
    AuditEntry auditEntry = AuditEntry.builder()
                                .action(Action.CREATE)
                                .module(ModuleType.CORE)
                                .newYaml(event.getTriggerEntity().getYaml())
                                .timestamp(outboxEvent.getCreatedAt())
                                .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
                                .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
                                .insertId(outboxEvent.getId())
                                .build();

    return auditClientService.publishAudit(auditEntry, globalContext);
  }
  private boolean handleTriggerUpdateEvent(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();
    TriggerUpdateEvent event =
        YamlUtils.readWithDefaultObjectMapper(outboxEvent.getEventData(), TriggerUpdateEvent.class);
    AuditEntry auditEntry = AuditEntry.builder()
                                .action(Action.UPDATE)
                                .module(ModuleType.CORE)
                                .newYaml(event.getNewTriggerEntity().getYaml().replace("---\n", ""))
                                .oldYaml(event.getOldTriggerEntity().getYaml())
                                .timestamp(outboxEvent.getCreatedAt())
                                .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
                                .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
                                .insertId(outboxEvent.getId())
                                .build();
    return auditClientService.publishAudit(auditEntry, globalContext);
  }

  private boolean handleTriggerDeleteEvent(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();
    TriggerDeleteEvent event =
        YamlUtils.readWithDefaultObjectMapper(outboxEvent.getEventData(), TriggerDeleteEvent.class);
    AuditEntry auditEntry = AuditEntry.builder()
                                .action(Action.DELETE)
                                .module(ModuleType.CORE)
                                .oldYaml(event.getTriggerEntity().getYaml())
                                .timestamp(outboxEvent.getCreatedAt())
                                .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
                                .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
                                .insertId(outboxEvent.getId())
                                .build();
    return auditClientService.publishAudit(auditEntry, globalContext);
  }
  @Override
  public boolean handle(OutboxEvent outboxEvent) {
    try {
      switch (outboxEvent.getEventType()) {
        case TriggerOutboxEvents.TRIGGER_CREATED:
          return handleTriggerCreateEvent(outboxEvent);
        case TriggerOutboxEvents.TRIGGER_UPDATED:
          return handleTriggerUpdateEvent(outboxEvent);
        case TriggerOutboxEvents.TRIGGER_DELETED:
          return handleTriggerDeleteEvent(outboxEvent);
        default:
          return false;
      }
    } catch (IOException ex) {
      return false;
    }
  }
}
