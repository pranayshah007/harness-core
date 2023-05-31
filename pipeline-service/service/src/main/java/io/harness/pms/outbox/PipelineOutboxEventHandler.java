/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.outbox;

import static io.harness.audit.beans.AuthenticationInfoDTO.fromSecurityPrincipal;
import static io.harness.authorization.AuthorizationServiceHeader.PIPELINE_SERVICE;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_NESTS;
import static io.harness.security.PrincipalContextData.PRINCIPAL_CONTEXT;

import io.harness.ModuleType;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.audit.Action;
import io.harness.audit.beans.AuditEntry;
import io.harness.audit.beans.ResourceDTO;
import io.harness.audit.beans.ResourceScopeDTO;
import io.harness.audit.beans.custom.executions.NodeExecutionEventData;
import io.harness.audit.client.api.AuditClientService;
import io.harness.context.GlobalContext;
import io.harness.engine.pms.audits.events.NodeExecutionOutboxEventConstants;
import io.harness.engine.pms.audits.events.PipelineAbortEvent;
import io.harness.engine.pms.audits.events.PipelineEndEvent;
import io.harness.engine.pms.audits.events.PipelineStartEvent;
import io.harness.engine.pms.audits.events.PipelineTimeoutEvent;
import io.harness.engine.pms.audits.events.StageEndEvent;
import io.harness.engine.pms.audits.events.StageStartEvent;
import io.harness.logging.AutoLogContext;
import io.harness.observer.Subject;
import io.harness.outbox.OutboxEvent;
import io.harness.outbox.api.OutboxEventHandler;
import io.harness.pms.events.PipelineCreateEvent;
import io.harness.pms.events.PipelineDeleteEvent;
import io.harness.pms.events.PipelineOutboxEvents;
import io.harness.pms.events.PipelineUpdateEvent;
import io.harness.pms.notification.orchestration.NodeExecutionEventUtils;
import io.harness.pms.outbox.autoLog.OutboxLogContext;
import io.harness.pms.pipeline.observer.PipelineActionObserver;
import io.harness.security.PrincipalContextData;
import io.harness.security.dto.Principal;
import io.harness.security.dto.ServicePrincipal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.serializer.HObjectMapper;
import java.io.IOException;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
@Singleton
public class PipelineOutboxEventHandler implements OutboxEventHandler {
  private ObjectMapper objectMapper;
  private final AuditClientService auditClientService;
  private final InputSetEventHandler inputSetEventHandler;

  @Getter private final Subject<PipelineActionObserver> pipelineActionObserverSubject = new Subject<>();

  @Inject
  PipelineOutboxEventHandler(AuditClientService auditClientService, InputSetEventHandler inputSetEventHandler) {
    this.objectMapper = HObjectMapper.NG_DEFAULT_OBJECT_MAPPER;
    this.auditClientService = auditClientService;
    this.inputSetEventHandler = inputSetEventHandler;
  }

  private boolean handlePipelineCreateEvent(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();
    PipelineCreateEvent event = objectMapper.readValue(outboxEvent.getEventData(), PipelineCreateEvent.class);
    AuditEntry auditEntry = AuditEntry.builder()
                                .action(Action.CREATE)
                                .module(ModuleType.PMS)
                                .newYaml(event.getPipeline().getYaml())
                                .timestamp(outboxEvent.getCreatedAt())
                                .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
                                .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
                                .insertId(outboxEvent.getId())
                                .build();
    Principal principal = null;
    if (globalContext.get(PRINCIPAL_CONTEXT) == null) {
      principal = new ServicePrincipal(PIPELINE_SERVICE.getServiceId());
    } else if (globalContext.get(PRINCIPAL_CONTEXT) != null) {
      principal = ((PrincipalContextData) globalContext.get(PRINCIPAL_CONTEXT)).getPrincipal();
    }
    pipelineActionObserverSubject.fireInform(PipelineActionObserver::onCreate, event);
    if (event.getIsForOldGitSync()) {
      return true;
    }
    return auditClientService.publishAudit(auditEntry, fromSecurityPrincipal(principal), globalContext);
  }

  private boolean handlePipelineUpdateEvent(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();
    PipelineUpdateEvent event = objectMapper.readValue(outboxEvent.getEventData(), PipelineUpdateEvent.class);
    AuditEntry auditEntry = AuditEntry.builder()
                                .action(Action.UPDATE)
                                .module(ModuleType.PMS)
                                .newYaml(event.getNewPipeline().getYaml())
                                .oldYaml(event.getOldPipeline() == null ? null : event.getOldPipeline().getYaml())
                                .timestamp(outboxEvent.getCreatedAt())
                                .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
                                .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
                                .insertId(outboxEvent.getId())
                                .build();
    Principal principal = null;
    if (globalContext.get(PRINCIPAL_CONTEXT) == null) {
      principal = new ServicePrincipal(PIPELINE_SERVICE.getServiceId());
    } else if (globalContext.get(PRINCIPAL_CONTEXT) != null) {
      principal = ((PrincipalContextData) globalContext.get(PRINCIPAL_CONTEXT)).getPrincipal();
    }
    pipelineActionObserverSubject.fireInform(PipelineActionObserver::onUpdate, event);
    if (event.getIsForOldGitSync() || event.getOldPipeline() == null) {
      return true;
    }
    return auditClientService.publishAudit(auditEntry, fromSecurityPrincipal(principal), globalContext);
  }

  private boolean handlePipelineDeleteEvent(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();
    PipelineDeleteEvent event = objectMapper.readValue(outboxEvent.getEventData(), PipelineDeleteEvent.class);

    Principal principal = null;
    if (globalContext.get(PRINCIPAL_CONTEXT) == null) {
      principal = new ServicePrincipal(PIPELINE_SERVICE.getServiceId());
    } else if (globalContext.get(PRINCIPAL_CONTEXT) != null) {
      principal = ((PrincipalContextData) globalContext.get(PRINCIPAL_CONTEXT)).getPrincipal();
    }
    pipelineActionObserverSubject.fireInform(PipelineActionObserver::onDelete, event);
    if (event.getIsForOldGitSync()) {
      return true;
    }
    AuditEntry auditEntry = AuditEntry.builder()
                                .action(Action.DELETE)
                                .module(ModuleType.PMS)
                                .oldYaml(event.getPipeline().getYaml())
                                .timestamp(outboxEvent.getCreatedAt())
                                .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
                                .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
                                .insertId(outboxEvent.getId())
                                .build();
    return auditClientService.publishAudit(auditEntry, fromSecurityPrincipal(principal), globalContext);
  }

  private boolean handlePipelineStartEvent(OutboxEvent outboxEvent) throws JsonProcessingException {
    PipelineStartEvent pipelineStartEvent =
        objectMapper.readValue(outboxEvent.getEventData(), PipelineStartEvent.class);
    NodeExecutionEventData nodeExecutionEventData =
        NodeExecutionEventUtils.mapPipelineStartEventToNodeExecutionEventData(pipelineStartEvent);

    return publishAuditEntry(outboxEvent, nodeExecutionEventData, Action.START);
  }

  private boolean handlePipelineTimeoutEvent(OutboxEvent outboxEvent) throws JsonProcessingException {
    PipelineTimeoutEvent pipelineTimeoutEvent =
        objectMapper.readValue(outboxEvent.getEventData(), PipelineTimeoutEvent.class);
    NodeExecutionEventData nodeExecutionEventData =
        NodeExecutionEventUtils.mapPipelineTimeoutEventToNodeExecutionEventData(pipelineTimeoutEvent);

    return publishAuditEntry(outboxEvent, nodeExecutionEventData, Action.TIMEOUT);
  }

  private boolean handlePipelineEndEvent(OutboxEvent outboxEvent) throws JsonProcessingException {
    PipelineEndEvent pipelineEndEvent = objectMapper.readValue(outboxEvent.getEventData(), PipelineEndEvent.class);
    NodeExecutionEventData nodeExecutionEventData =
        NodeExecutionEventUtils.mapPipelineEndEventToNodeExecutionEventData(pipelineEndEvent);

    return publishAuditEntry(outboxEvent, nodeExecutionEventData, Action.END);
  }

  private boolean handleStageStartEvent(OutboxEvent outboxEvent) throws JsonProcessingException {
    StageStartEvent stageStartEvent = objectMapper.readValue(outboxEvent.getEventData(), StageStartEvent.class);
    NodeExecutionEventData nodeExecutionEventData =
        NodeExecutionEventUtils.mapStageStartEventToNodeExecutionEventData(stageStartEvent);

    return publishAuditEntry(outboxEvent, nodeExecutionEventData, Action.STAGE_START);
  }
  private boolean handleStageEndEvent(OutboxEvent outboxEvent) throws JsonProcessingException {
    StageEndEvent stageEndEvent = objectMapper.readValue(outboxEvent.getEventData(), StageEndEvent.class);
    NodeExecutionEventData nodeExecutionEventData =
        NodeExecutionEventUtils.mapStageEndEventToNodeExecutionEventData(stageEndEvent);

    return publishAuditEntry(outboxEvent, nodeExecutionEventData, Action.STAGE_END);
  }

  private boolean handlePipelineAbortEvent(OutboxEvent outboxEvent) throws JsonProcessingException {
    PipelineAbortEvent pipelineAbortEvent =
        objectMapper.readValue(outboxEvent.getEventData(), PipelineAbortEvent.class);
    NodeExecutionEventData nodeExecutionEventData =
        NodeExecutionEventUtils.mapPipelineAbortEventToNodeExecutionEventData(pipelineAbortEvent);

    return publishAuditEntry(outboxEvent, nodeExecutionEventData, Action.ABORT);
  }

  private boolean publishAuditEntry(
      OutboxEvent outboxEvent, NodeExecutionEventData nodeExecutionEventData, Action action) {
    GlobalContext globalContext = outboxEvent.getGlobalContext();

    Principal principal = new ServicePrincipal(PIPELINE_SERVICE.getServiceId());
    if (globalContext.get(PRINCIPAL_CONTEXT) != null) {
      principal = ((PrincipalContextData) globalContext.get(PRINCIPAL_CONTEXT)).getPrincipal();
    }
    AuditEntry auditEntry = AuditEntry.builder()
                                .action(action)
                                .module(ModuleType.PMS)
                                .timestamp(outboxEvent.getCreatedAt())
                                .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
                                .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
                                .auditEventData(nodeExecutionEventData)
                                .insertId(outboxEvent.getId())
                                .build();

    return auditClientService.publishAudit(auditEntry, fromSecurityPrincipal(principal), globalContext);
  }

  @Override
  public boolean handle(OutboxEvent outboxEvent) {
    try (AutoLogContext ignore = new OutboxLogContext(outboxEvent.getId(), OVERRIDE_NESTS)) {
      try {
        switch (outboxEvent.getEventType()) {
          case PipelineOutboxEvents.PIPELINE_CREATED:
            return handlePipelineCreateEvent(outboxEvent);
          case PipelineOutboxEvents.PIPELINE_UPDATED:
            return handlePipelineUpdateEvent(outboxEvent);
          case PipelineOutboxEvents.PIPELINE_DELETED:
            return handlePipelineDeleteEvent(outboxEvent);
          case PipelineOutboxEvents.INPUT_SET_CREATED:
            return inputSetEventHandler.handleInputSetCreateEvent(outboxEvent);
          case PipelineOutboxEvents.INPUT_SET_UPDATED:
            return inputSetEventHandler.handleInputSetUpdateEvent(outboxEvent);
          case PipelineOutboxEvents.INPUT_SET_DELETED:
            return inputSetEventHandler.handleInputSetDeleteEvent(outboxEvent);
          case NodeExecutionOutboxEventConstants.PIPELINE_START:
            return handlePipelineStartEvent(outboxEvent);
          case NodeExecutionOutboxEventConstants.PIPELINE_END:
            return handlePipelineEndEvent(outboxEvent);
          case NodeExecutionOutboxEventConstants.PIPELINE_ABORT:
            return handlePipelineAbortEvent(outboxEvent);
          case NodeExecutionOutboxEventConstants.PIPELINE_TIMEOUT:
            return handlePipelineTimeoutEvent(outboxEvent);
          case NodeExecutionOutboxEventConstants.STAGE_START:
            return handleStageStartEvent(outboxEvent);
          case NodeExecutionOutboxEventConstants.STAGE_END:
            return handleStageEndEvent(outboxEvent);
          default:
            log.info("Current type of event is not supported for Audits!");
            return false;
        }
      } catch (Exception ex) {
        log.error("Unexpected error occurred during handling of event", ex);
        return false;
      }
    }
  }
}
