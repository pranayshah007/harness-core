/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.connector.events;

import static io.harness.annotations.dev.HarnessTeam.DX;
import static io.harness.connector.ConnectorEvent.CONNECTOR_CREATED;
import static io.harness.connector.ConnectorEvent.CONNECTOR_DELETED;
import static io.harness.connector.ConnectorEvent.CONNECTOR_FORCE_DELETED;
import static io.harness.connector.ConnectorEvent.CONNECTOR_UPDATED;

import static io.serializer.HObjectMapper.NG_DEFAULT_OBJECT_MAPPER;

import io.harness.ModuleType;
import io.harness.annotations.dev.OwnedBy;
import io.harness.audit.Action;
import io.harness.audit.beans.AuditEntry;
import io.harness.audit.beans.ResourceDTO;
import io.harness.audit.beans.ResourceScopeDTO;
import io.harness.audit.client.api.AuditClientService;
import io.harness.connector.ConnectorDTO;
import io.harness.context.GlobalContext;
import io.harness.ng.core.utils.NGYamlUtils;
import io.harness.outbox.OutboxEvent;
import io.harness.outbox.api.OutboxEventHandler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(DX)
@Slf4j
public class ConnectorEventHandler implements OutboxEventHandler {
  private final ObjectMapper objectMapper;
  private final AuditClientService auditClientService;

  @Inject
  public ConnectorEventHandler(AuditClientService auditClientService) {
    this.objectMapper = NG_DEFAULT_OBJECT_MAPPER;
    this.auditClientService = auditClientService;
  }

  @Override
  public boolean handle(OutboxEvent outboxEvent) {
    try {
      switch (outboxEvent.getEventType()) {
        case CONNECTOR_CREATED:
          return handleConnectorCreateEvent(outboxEvent);
        case CONNECTOR_UPDATED:
          return handleConnectorUpdateEvent(outboxEvent);
        case CONNECTOR_DELETED:
          return handleConnectorDeleteEvent(outboxEvent);
        case CONNECTOR_FORCE_DELETED:
          return handleConnectorForceDeleteEvent(outboxEvent);
        default:
          return false;
      }
    } catch (IOException ex) {
      return false;
    }
  }

  private boolean handleConnectorForceDeleteEvent(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();
    ConnectorForceDeleteEvent connectorDeleteEvent =
        objectMapper.readValue(outboxEvent.getEventData(), ConnectorForceDeleteEvent.class);
    AuditEntry auditEntry =
        AuditEntry.builder()
            .action(Action.FORCE_DELETE)
            .module(ModuleType.CORE)
            .oldYaml(NGYamlUtils.getYamlString(
                ConnectorDTO.builder().connectorInfo(connectorDeleteEvent.getConnectorDTO()).build(), objectMapper))
            .timestamp(outboxEvent.getCreatedAt())
            .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
            .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
            .insertId(outboxEvent.getId())
            .build();
    return auditClientService.publishAudit(auditEntry, globalContext);
  }

  private boolean handleConnectorDeleteEvent(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();
    ConnectorDeleteEvent connectorDeleteEvent =
        objectMapper.readValue(outboxEvent.getEventData(), ConnectorDeleteEvent.class);
    AuditEntry auditEntry =
        AuditEntry.builder()
            .action(Action.DELETE)
            .module(ModuleType.CORE)
            .oldYaml(NGYamlUtils.getYamlString(
                ConnectorDTO.builder().connectorInfo(connectorDeleteEvent.getConnectorDTO()).build(), objectMapper))
            .timestamp(outboxEvent.getCreatedAt())
            .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
            .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
            .insertId(outboxEvent.getId())
            .build();
    return auditClientService.publishAudit(auditEntry, globalContext);
  }

  private boolean handleConnectorUpdateEvent(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();
    ConnectorUpdateEvent connectorUpdateEvent =
        objectMapper.readValue(outboxEvent.getEventData(), ConnectorUpdateEvent.class);
    AuditEntry auditEntry =
        AuditEntry.builder()
            .action(Action.UPDATE)
            .module(ModuleType.CORE)
            .newYaml(NGYamlUtils.getYamlString(
                ConnectorDTO.builder().connectorInfo(connectorUpdateEvent.getNewConnector()).build(), objectMapper))
            .oldYaml(NGYamlUtils.getYamlString(
                ConnectorDTO.builder().connectorInfo(connectorUpdateEvent.getOldConnector()).build(), objectMapper))
            .timestamp(outboxEvent.getCreatedAt())
            .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
            .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
            .insertId(outboxEvent.getId())
            .build();
    return auditClientService.publishAudit(auditEntry, globalContext);
  }

  private boolean handleConnectorCreateEvent(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();
    ConnectorCreateEvent connectorCreateEvent =
        objectMapper.readValue(outboxEvent.getEventData(), ConnectorCreateEvent.class);
    AuditEntry auditEntry =
        AuditEntry.builder()
            .action(Action.CREATE)
            .module(ModuleType.CORE)
            .newYaml(NGYamlUtils.getYamlString(
                ConnectorDTO.builder().connectorInfo(connectorCreateEvent.getConnectorDTO()).build(), objectMapper))
            .timestamp(outboxEvent.getCreatedAt())
            .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
            .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
            .insertId(outboxEvent.getId())
            .build();
    return auditClientService.publishAudit(auditEntry, globalContext);
  }
}
