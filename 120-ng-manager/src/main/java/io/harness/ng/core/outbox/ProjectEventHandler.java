/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.outbox;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.ng.core.utils.NGYamlUtils.getYamlString;

import static io.serializer.HObjectMapper.NG_DEFAULT_OBJECT_MAPPER;

import io.harness.ModuleType;
import io.harness.annotations.dev.OwnedBy;
import io.harness.audit.Action;
import io.harness.audit.beans.AuditEntry;
import io.harness.audit.beans.ResourceDTO;
import io.harness.audit.beans.ResourceScopeDTO;
import io.harness.audit.client.api.AuditClientService;
import io.harness.context.GlobalContext;
import io.harness.eventsframework.EventsFrameworkConstants;
import io.harness.eventsframework.EventsFrameworkMetadataConstants;
import io.harness.eventsframework.api.EventsFrameworkDownException;
import io.harness.eventsframework.api.Producer;
import io.harness.eventsframework.entity_crud.project.ProjectEntityChangeDTO;
import io.harness.eventsframework.producer.Message;
import io.harness.exception.InvalidArgumentsException;
import io.harness.ng.core.ProjectScope;
import io.harness.ng.core.dto.ProjectRequest;
import io.harness.ng.core.events.ProjectCreateEvent;
import io.harness.ng.core.events.ProjectDeleteEvent;
import io.harness.ng.core.events.ProjectRestoreEvent;
import io.harness.ng.core.events.ProjectUpdateEvent;
import io.harness.outbox.OutboxEvent;
import io.harness.outbox.api.OutboxEventHandler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PL)
@Slf4j
public class ProjectEventHandler implements OutboxEventHandler {
  private final ObjectMapper objectMapper;
  private final Producer eventProducer;
  private final AuditClientService auditClientService;

  @Inject
  public ProjectEventHandler(
      @Named(EventsFrameworkConstants.ENTITY_CRUD) Producer eventProducer, AuditClientService auditClientService) {
    this.objectMapper = NG_DEFAULT_OBJECT_MAPPER;
    this.eventProducer = eventProducer;
    this.auditClientService = auditClientService;
  }

  public boolean handle(OutboxEvent outboxEvent) {
    try {
      switch (outboxEvent.getEventType()) {
        case "ProjectCreated":
          return handleProjectCreateEvent(outboxEvent);
        case "ProjectUpdated":
          return handleProjectUpdateEvent(outboxEvent);
        case "ProjectDeleted":
          return handleProjectDeleteEvent(outboxEvent);
        case "ProjectRestored":
          return handleProjectRestoreEvent(outboxEvent);
        default:
          throw new InvalidArgumentsException(String.format("Not supported event type %s", outboxEvent.getEventType()));
      }
    } catch (IOException exception) {
      return false;
    }
  }

  private boolean handleProjectCreateEvent(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();
    String accountIdentifier = ((ProjectScope) outboxEvent.getResourceScope()).getAccountIdentifier();
    String orgIdentifier = ((ProjectScope) outboxEvent.getResourceScope()).getOrgIdentifier();
    boolean publishedToRedis = publishEvent(accountIdentifier, orgIdentifier, outboxEvent.getResource().getIdentifier(),
        EventsFrameworkMetadataConstants.CREATE_ACTION);
    ProjectCreateEvent projectCreateEvent =
        objectMapper.readValue(outboxEvent.getEventData(), ProjectCreateEvent.class);
    AuditEntry auditEntry = AuditEntry.builder()
                                .action(Action.CREATE)
                                .module(ModuleType.CORE)
                                .newYaml(getYamlString(ProjectRequest.builder()
                                                           .project(projectCreateEvent.getProject())
                                                           .uniqueId(projectCreateEvent.getUniqueId())
                                                           .build()))
                                .timestamp(outboxEvent.getCreatedAt())
                                .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
                                .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
                                .insertId(outboxEvent.getId())
                                .build();
    return publishedToRedis && auditClientService.publishAudit(auditEntry, globalContext);
  }

  private boolean handleProjectUpdateEvent(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();
    String accountIdentifier = ((ProjectScope) outboxEvent.getResourceScope()).getAccountIdentifier();
    String orgIdentifier = ((ProjectScope) outboxEvent.getResourceScope()).getOrgIdentifier();
    boolean publishedToRedis = publishEvent(accountIdentifier, orgIdentifier, outboxEvent.getResource().getIdentifier(),
        EventsFrameworkMetadataConstants.UPDATE_ACTION);
    ProjectUpdateEvent projectUpdateEvent =
        objectMapper.readValue(outboxEvent.getEventData(), ProjectUpdateEvent.class);
    AuditEntry auditEntry =
        AuditEntry.builder()
            .action(Action.UPDATE)
            .module(ModuleType.CORE)
            .newYaml(getYamlString(ProjectRequest.builder().project(projectUpdateEvent.getNewProject()).build()))
            .oldYaml(getYamlString(ProjectRequest.builder().project(projectUpdateEvent.getOldProject()).build()))
            .timestamp(outboxEvent.getCreatedAt())
            .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
            .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
            .insertId(outboxEvent.getId())
            .build();
    return publishedToRedis && auditClientService.publishAudit(auditEntry, globalContext);
  }

  private boolean handleProjectDeleteEvent(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();
    String accountIdentifier = ((ProjectScope) outboxEvent.getResourceScope()).getAccountIdentifier();
    String orgIdentifier = ((ProjectScope) outboxEvent.getResourceScope()).getOrgIdentifier();
    boolean publishedToRedis = publishEvent(accountIdentifier, orgIdentifier, outboxEvent.getResource().getIdentifier(),
        EventsFrameworkMetadataConstants.DELETE_ACTION);
    ProjectDeleteEvent projectDeleteEvent =
        objectMapper.readValue(outboxEvent.getEventData(), ProjectDeleteEvent.class);
    AuditEntry auditEntry =
        AuditEntry.builder()
            .action(Action.DELETE)
            .module(ModuleType.CORE)
            .oldYaml(getYamlString(ProjectRequest.builder().project(projectDeleteEvent.getProject()).build()))
            .timestamp(outboxEvent.getCreatedAt())
            .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
            .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
            .insertId(outboxEvent.getId())
            .build();
    return publishedToRedis && auditClientService.publishAudit(auditEntry, globalContext);
  }

  private boolean handleProjectRestoreEvent(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();
    String accountIdentifier = ((ProjectScope) outboxEvent.getResourceScope()).getAccountIdentifier();
    String orgIdentifier = ((ProjectScope) outboxEvent.getResourceScope()).getOrgIdentifier();
    boolean publishedToRedis = publishEvent(accountIdentifier, orgIdentifier, outboxEvent.getResource().getIdentifier(),
        EventsFrameworkMetadataConstants.RESTORE_ACTION);
    ProjectRestoreEvent projectRestoreEvent =
        objectMapper.readValue(outboxEvent.getEventData(), ProjectRestoreEvent.class);
    AuditEntry auditEntry =
        AuditEntry.builder()
            .action(Action.RESTORE)
            .module(ModuleType.CORE)
            .newYaml(getYamlString(ProjectRequest.builder().project(projectRestoreEvent.getProject()).build()))
            .timestamp(outboxEvent.getCreatedAt())
            .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
            .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
            .insertId(outboxEvent.getId())
            .build();
    return publishedToRedis && auditClientService.publishAudit(auditEntry, globalContext);
  }

  private boolean publishEvent(String accountIdentifier, String orgIdentifier, String identifier, String action) {
    try {
      String eventId = eventProducer.send(
          Message.newBuilder()
              .putAllMetadata(
                  ImmutableMap.of("accountId", accountIdentifier, EventsFrameworkMetadataConstants.ENTITY_TYPE,
                      EventsFrameworkMetadataConstants.PROJECT_ENTITY, EventsFrameworkMetadataConstants.ACTION, action))
              .setData(ProjectEntityChangeDTO.newBuilder()
                           .setIdentifier(identifier)
                           .setOrgIdentifier(orgIdentifier)
                           .setAccountIdentifier(accountIdentifier)
                           .build()
                           .toByteString())
              .build());
      log.info("Produced event id:[{}] for projectId: [{}], orgId:[{}], accountId: [{}], action:[{}]", eventId,
          identifier, orgIdentifier, accountIdentifier, action);
      return true;
    } catch (EventsFrameworkDownException e) {
      log.error("Failed to send event to events framework projectIdentifier: " + identifier, e);
      return false;
    }
  }
}
