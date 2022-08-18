/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.audittrails.eventhandler;

import static io.harness.ccm.audittrails.events.PerspectiveFolderCreateEvent.PERSPECTIVE_FOLDER_CREATED;
import static io.harness.ccm.audittrails.events.PerspectiveFolderDeleteEvent.PERSPECTIVE_FOLDER_DELETED;
import static io.harness.ccm.audittrails.events.PerspectiveFolderUpdateEvent.PERSPECTIVE_FOLDER_UPDATED;
import static io.harness.ng.core.utils.NGYamlUtils.getYamlString;

import static io.serializer.HObjectMapper.NG_DEFAULT_OBJECT_MAPPER;

import io.harness.ModuleType;
import io.harness.audit.Action;
import io.harness.audit.beans.AuditEntry;
import io.harness.audit.beans.ResourceDTO;
import io.harness.audit.beans.ResourceScopeDTO;
import io.harness.audit.client.api.AuditClientService;
import io.harness.ccm.audittrails.events.PerspectiveFolderCreateEvent;
import io.harness.ccm.audittrails.events.PerspectiveFolderDeleteEvent;
import io.harness.ccm.audittrails.events.PerspectiveFolderUpdateEvent;
import io.harness.ccm.audittrails.yamlDTOs.PerspectiveFolderDTO;
import io.harness.context.GlobalContext;
import io.harness.exception.InvalidArgumentsException;
import io.harness.outbox.OutboxEvent;
import io.harness.outbox.api.OutboxEventHandler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PerspectiveFolderEventHandler implements OutboxEventHandler {
  private final ObjectMapper objectMapper;
  private final AuditClientService auditClientService;

  @Inject
  public PerspectiveFolderEventHandler(AuditClientService auditClientService) {
    this.objectMapper = NG_DEFAULT_OBJECT_MAPPER;
    this.auditClientService = auditClientService;
  }

  @Override
  public boolean handle(OutboxEvent outboxEvent) {
    try {
      switch (outboxEvent.getEventType()) {
        case PERSPECTIVE_FOLDER_CREATED:
          return handlePerspectiveFolderCreateEvent(outboxEvent);
        case PERSPECTIVE_FOLDER_UPDATED:
          return handlePerspectiveFolderUpdateEvent(outboxEvent);
        case PERSPECTIVE_FOLDER_DELETED:
          return handlePerspectiveFolderDeleteEvent(outboxEvent);
        default:
          throw new InvalidArgumentsException(String.format("Not supported event type %s", outboxEvent.getEventType()));
      }
    } catch (IOException exception) {
      log.error(exception.toString());
      return false;
    }
  }

  private boolean handlePerspectiveFolderCreateEvent(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();
    PerspectiveFolderCreateEvent perspectiveFolderCreateEvent =
        objectMapper.readValue(outboxEvent.getEventData(), PerspectiveFolderCreateEvent.class);
    AuditEntry auditEntry =
        AuditEntry.builder()
            .action(Action.CREATE)
            .module(ModuleType.CE)
            .newYaml(getYamlString(PerspectiveFolderDTO.builder()
                                       .perspectiveFolder(perspectiveFolderCreateEvent.getPerspectiveFolderDTO())
                                       .build()))
            .timestamp(outboxEvent.getCreatedAt())
            .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
            .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
            .insertId(outboxEvent.getId())
            .build();
    return auditClientService.publishAudit(auditEntry, globalContext);
  }

  private boolean handlePerspectiveFolderUpdateEvent(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();
    PerspectiveFolderUpdateEvent perspectiveFolderUpdateEvent =
        objectMapper.readValue(outboxEvent.getEventData(), PerspectiveFolderUpdateEvent.class);
    AuditEntry auditEntry =
        AuditEntry.builder()
            .action(Action.UPDATE)
            .module(ModuleType.CE)
            .newYaml(getYamlString(PerspectiveFolderDTO.builder()
                                       .perspectiveFolder(perspectiveFolderUpdateEvent.getPerspectiveFolderDTO())
                                       .build()))
            .oldYaml(getYamlString(PerspectiveFolderDTO.builder()
                                       .perspectiveFolder(perspectiveFolderUpdateEvent.getOldPerspectiveFolderDTO())
                                       .build()))
            .timestamp(outboxEvent.getCreatedAt())
            .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
            .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
            .insertId(outboxEvent.getId())
            .build();

    return auditClientService.publishAudit(auditEntry, globalContext);
  }

  private boolean handlePerspectiveFolderDeleteEvent(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();
    PerspectiveFolderDeleteEvent perspectiveFolderDeleteEvent =
        objectMapper.readValue(outboxEvent.getEventData(), PerspectiveFolderDeleteEvent.class);
    AuditEntry auditEntry =
        AuditEntry.builder()
            .action(Action.DELETE)
            .module(ModuleType.CE)
            .oldYaml(getYamlString(PerspectiveFolderDTO.builder()
                                       .perspectiveFolder(perspectiveFolderDeleteEvent.getPerspectiveFolderDTO())
                                       .build()))
            .timestamp(outboxEvent.getCreatedAt())
            .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
            .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
            .insertId(outboxEvent.getId())
            .build();

    return auditClientService.publishAudit(auditEntry, globalContext);
  }
}
