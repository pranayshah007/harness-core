/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.outbox;

import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_NESTS;

import io.harness.ModuleType;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.audit.Action;
import io.harness.audit.beans.AuditEntry;
import io.harness.audit.beans.ResourceDTO;
import io.harness.audit.beans.ResourceScopeDTO;
import io.harness.audit.client.api.AuditClientService;
import io.harness.context.GlobalContext;
import io.harness.engine.pms.audits.events.NodeExecutionOutboxEvents;
import io.harness.logging.AutoLogContext;
import io.harness.outbox.OutboxEvent;
import io.harness.outbox.api.OutboxEventHandler;
import io.harness.pms.outbox.autoLog.OutboxLogContext;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.serializer.HObjectMapper;
import lombok.extern.slf4j.Slf4j;

/***
 * Handler Methods in this class handle events for OutboxDb for NodeExecutionEvents during a pipeline execution
 * NodeExecution can be of type Stage/Step/Pipeline etc.
 */
@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
@Singleton
public class NodeExecutionOutboxEventHandler implements OutboxEventHandler {
  private ObjectMapper objectMapper;
  private final AuditClientService auditClientService;

  @Inject
  public NodeExecutionOutboxEventHandler(AuditClientService auditClientService) {
    this.objectMapper = HObjectMapper.NG_DEFAULT_OBJECT_MAPPER;
    this.auditClientService = auditClientService;
  }

  private boolean handlePipelineStartEvent(OutboxEvent outboxEvent) throws JsonProcessingException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();
    AuditEntry auditEntry = AuditEntry.builder()
                                .action(Action.START)
                                .module(ModuleType.PMS)
                                .timestamp(outboxEvent.getCreatedAt())
                                .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
                                .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
                                .insertId(outboxEvent.getId())
                                .build();

    return auditClientService.publishAudit(auditEntry, globalContext);
  }
  private boolean handlePipelineEndEvent(OutboxEvent outboxEvent) throws JsonProcessingException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();
    AuditEntry auditEntry = AuditEntry.builder()
                                .action(Action.END)
                                .module(ModuleType.PMS)
                                .timestamp(outboxEvent.getCreatedAt())
                                .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
                                .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
                                .insertId(outboxEvent.getId())
                                .build();

    return auditClientService.publishAudit(auditEntry, globalContext);
  }

  private boolean handleStageStartEvent(OutboxEvent outboxEvent) throws JsonProcessingException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();
    AuditEntry auditEntry = AuditEntry.builder()
                                .action(Action.START)
                                .module(ModuleType.PMS)
                                .timestamp(outboxEvent.getCreatedAt())
                                .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
                                .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
                                .insertId(outboxEvent.getId())
                                .build();

    return auditClientService.publishAudit(auditEntry, globalContext);
  }
  private boolean handleStageEndEvent(OutboxEvent outboxEvent) throws JsonProcessingException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();
    AuditEntry auditEntry = AuditEntry.builder()
                                .action(Action.END)
                                .module(ModuleType.PMS)
                                .timestamp(outboxEvent.getCreatedAt())
                                .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
                                .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
                                .insertId(outboxEvent.getId())
                                .build();

    return auditClientService.publishAudit(auditEntry, globalContext);
  }

  @Override
  public boolean handle(OutboxEvent outboxEvent) {
    try (AutoLogContext ignore = new OutboxLogContext(outboxEvent.getId(), OVERRIDE_NESTS)) {
      try {
        switch (outboxEvent.getEventType()) {
          case NodeExecutionOutboxEvents.PIPELINE_START:
            return handlePipelineStartEvent(outboxEvent);
          case NodeExecutionOutboxEvents.PIPELINE_END:
            return handlePipelineEndEvent(outboxEvent);
          case NodeExecutionOutboxEvents.STAGE_START:
            return handleStageStartEvent(outboxEvent);
          case NodeExecutionOutboxEvents.STAGE_END:
            return handleStageEndEvent(outboxEvent);
          default:
            log.info(String.format("Current type of event is not supported for Audits!"));
            return false;
        }
      } catch (Exception ex) {
        log.error(String.format("Unexpected error occurred during handling of event", ex));
        return false;
      }
    }
  }
}