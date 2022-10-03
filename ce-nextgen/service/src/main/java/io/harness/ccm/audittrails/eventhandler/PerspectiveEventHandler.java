/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.audittrails.eventhandler;

import static io.harness.ccm.audittrails.events.PerspectiveCreateEvent.PERSPECTIVE_CREATED;
import static io.harness.ccm.audittrails.events.PerspectiveDeleteEvent.PERSPECTIVE_DELETED;
import static io.harness.ccm.audittrails.events.PerspectiveUpdateEvent.PERSPECTIVE_UPDATED;
import static io.harness.ng.core.utils.NGYamlUtils.getYamlString;

import static io.serializer.HObjectMapper.NG_DEFAULT_OBJECT_MAPPER;

import io.harness.ModuleType;
import io.harness.audit.Action;
import io.harness.audit.beans.AuditEntry;
import io.harness.audit.beans.ResourceDTO;
import io.harness.audit.beans.ResourceScopeDTO;
import io.harness.audit.client.api.AuditClientService;
import io.harness.ccm.audittrails.events.PerspectiveCreateEvent;
import io.harness.ccm.audittrails.events.PerspectiveDeleteEvent;
import io.harness.ccm.audittrails.events.PerspectiveUpdateEvent;
import io.harness.ccm.audittrails.yamlDTOs.PerspectiveDTO;
import io.harness.context.GlobalContext;
import io.harness.exception.InvalidArgumentsException;
import io.harness.outbox.OutboxEvent;
import io.harness.outbox.api.OutboxEventHandler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PerspectiveEventHandler implements OutboxEventHandler {
  private final ObjectMapper objectMapper;
  private final AuditClientService auditClientService;

  @Inject
  public PerspectiveEventHandler(AuditClientService auditClientService) {
    this.objectMapper = NG_DEFAULT_OBJECT_MAPPER;
    this.auditClientService = auditClientService;
  }

  @Override
  public boolean handle(OutboxEvent outboxEvent) {
    try {
      switch (outboxEvent.getEventType()) {
        case PERSPECTIVE_CREATED:
          return handlePerspectiveCreateEvent(outboxEvent);
        case PERSPECTIVE_UPDATED:
          return handlePerspectiveUpdateEvent(outboxEvent);
        case PERSPECTIVE_DELETED:
          return handlePerspectiveDeleteEvent(outboxEvent);
        default:
          throw new InvalidArgumentsException(String.format("Not supported event type %s", outboxEvent.getEventType()));
      }
    } catch (IOException exception) {
      log.error(exception.toString());
      return false;
    }
  }

  private boolean handlePerspectiveCreateEvent(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();
    PerspectiveCreateEvent perspectiveCreateEvent =
        objectMapper.readValue(outboxEvent.getEventData(), PerspectiveCreateEvent.class);
    AuditEntry auditEntry =
        AuditEntry.builder()
            .action(Action.CREATE)
            .module(ModuleType.CE)
            .newYaml(
                getYamlString(PerspectiveDTO.builder().perspective(perspectiveCreateEvent.getPerspectiveDTO()).build()))
            .timestamp(outboxEvent.getCreatedAt())
            .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
            .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
            .insertId(outboxEvent.getId())
            .build();
    return auditClientService.publishAudit(auditEntry, globalContext);
  }

  private boolean handlePerspectiveUpdateEvent(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();
    PerspectiveUpdateEvent perspectiveUpdateEvent =
        objectMapper.readValue(outboxEvent.getEventData(), PerspectiveUpdateEvent.class);
    AuditEntry auditEntry =
        AuditEntry.builder()
            .action(Action.UPDATE)
            .module(ModuleType.CE)
            .newYaml(
                getYamlString(PerspectiveDTO.builder().perspective(perspectiveUpdateEvent.getPerspectiveDTO()).build()))
            .oldYaml(getYamlString(
                PerspectiveDTO.builder().perspective(perspectiveUpdateEvent.getOldPerspectiveDTO()).build()))
            .timestamp(outboxEvent.getCreatedAt())
            .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
            .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
            .insertId(outboxEvent.getId())
            .build();

    return auditClientService.publishAudit(auditEntry, globalContext);
  }

  private boolean handlePerspectiveDeleteEvent(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();
    PerspectiveDeleteEvent perspectiveDeleteEvent =
        objectMapper.readValue(outboxEvent.getEventData(), PerspectiveDeleteEvent.class);
    AuditEntry auditEntry =
        AuditEntry.builder()
            .action(Action.DELETE)
            .module(ModuleType.CE)
            .oldYaml(
                getYamlString(PerspectiveDTO.builder().perspective(perspectiveDeleteEvent.getPerspectiveDTO()).build()))
            .timestamp(outboxEvent.getCreatedAt())
            .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
            .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
            .insertId(outboxEvent.getId())
            .build();

    return auditClientService.publishAudit(auditEntry, globalContext);
  }
}
