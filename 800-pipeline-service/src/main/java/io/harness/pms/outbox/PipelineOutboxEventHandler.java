/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.outbox;

import static io.harness.AuthorizationServiceHeader.PIPELINE_SERVICE;
import static io.harness.audit.beans.AuthenticationInfoDTO.fromSecurityPrincipal;
import static io.harness.security.PrincipalContextData.PRINCIPAL_CONTEXT;

import io.harness.ModuleType;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.audit.Action;
import io.harness.audit.beans.AuditEntry;
import io.harness.audit.beans.ResourceDTO;
import io.harness.audit.beans.ResourceScopeDTO;
import io.harness.audit.client.api.AuditClientService;
import io.harness.context.GlobalContext;
import io.harness.observer.Subject;
import io.harness.outbox.OutboxEvent;
import io.harness.outbox.api.OutboxEventHandler;
import io.harness.pms.events.PipelineCreateEvent;
import io.harness.pms.events.PipelineDeleteEvent;
import io.harness.pms.events.PipelineOutboxEvents;
import io.harness.pms.events.PipelineUpdateEvent;
import io.harness.pms.pipeline.observer.PipelineActionObserver;
import io.harness.security.PrincipalContextData;
import io.harness.security.dto.Principal;
import io.harness.security.dto.ServicePrincipal;

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
                                .oldYaml(event.getOldPipeline().getYaml())
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
    if (event.getIsForOldGitSync()) {
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

  @Override
  public boolean handle(OutboxEvent outboxEvent) {
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
        default:
          return false;
      }
    } catch (IOException ex) {
      return false;
    }
  }
}
