/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.audittrials.eventhandlers;

import static io.harness.idp.gitintegration.events.catalogconnector.CatalogConnectorCreateEvent.CATALOG_CONNECTOR_CREATED;
import static io.harness.idp.gitintegration.events.catalogconnector.CatalogConnectorUpdateEvent.CATALOG_CONNECTOR_UPDATED;

import static io.serializer.HObjectMapper.NG_DEFAULT_OBJECT_MAPPER;

import io.harness.ModuleType;
import io.harness.audit.Action;
import io.harness.audit.beans.AuditEntry;
import io.harness.audit.beans.ResourceDTO;
import io.harness.audit.beans.ResourceScopeDTO;
import io.harness.audit.client.api.AuditClientService;
import io.harness.context.GlobalContext;
import io.harness.exception.InvalidArgumentsException;
import io.harness.idp.audittrials.eventhandlers.dtos.CatalogConnectorDTO;
import io.harness.idp.gitintegration.entities.CatalogConnectorEntity;
import io.harness.idp.gitintegration.events.catalogconnector.CatalogConnectorCreateEvent;
import io.harness.idp.gitintegration.events.catalogconnector.CatalogConnectorUpdateEvent;
import io.harness.ng.core.utils.NGYamlUtils;
import io.harness.outbox.OutboxEvent;
import io.harness.outbox.api.OutboxEventHandler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CatalogConnectorEventHandler implements OutboxEventHandler {
  private static final ObjectMapper objectMapper = NG_DEFAULT_OBJECT_MAPPER;
  private final AuditClientService auditClientService;

  @Inject
  public CatalogConnectorEventHandler(AuditClientService auditClientService) {
    this.auditClientService = auditClientService;
  }

  @Override
  public boolean handle(OutboxEvent outboxEvent) {
    try {
      switch (outboxEvent.getEventType()) {
        case CATALOG_CONNECTOR_CREATED:
          return handleCatalogConnectorCreateEvent(outboxEvent);
        case CATALOG_CONNECTOR_UPDATED:
          return handleCatalogConnectorUpdateEvent(outboxEvent);
        default:
          throw new InvalidArgumentsException(String.format("Not supported event type %s", outboxEvent.getEventType()));
      }
    } catch (IOException exception) {
      log.error("Failed to handle " + outboxEvent.getEventType() + " event", exception);
      return false;
    }
  }

  private boolean handleCatalogConnectorCreateEvent(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();

    CatalogConnectorCreateEvent catalogConnectorCreateEvent =
        objectMapper.readValue(outboxEvent.getEventData(), CatalogConnectorCreateEvent.class);

    AuditEntry auditEntry =
        AuditEntry.builder()
            .action(Action.CREATE)
            .module(ModuleType.CORE)
            .newYaml(getYamlStringForCatalogConnector(catalogConnectorCreateEvent.getNewCatalogConnectorEntity()))
            .timestamp(outboxEvent.getCreatedAt())
            .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
            .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
            .insertId(outboxEvent.getId())
            .build();
    return auditClientService.publishAudit(auditEntry, globalContext);
  }

  private boolean handleCatalogConnectorUpdateEvent(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();

    CatalogConnectorUpdateEvent catalogConnectorUpdateEvent =
        objectMapper.readValue(outboxEvent.getEventData(), CatalogConnectorUpdateEvent.class);

    AuditEntry auditEntry =
        AuditEntry.builder()
            .action(Action.UPDATE)
            .module(ModuleType.CORE)
            .newYaml(getYamlStringForCatalogConnector(catalogConnectorUpdateEvent.getNewCatalogConnectorEntity()))
            .oldYaml(getYamlStringForCatalogConnector(catalogConnectorUpdateEvent.getOldCatalogConnectorEntity()))
            .timestamp(outboxEvent.getCreatedAt())
            .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
            .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
            .insertId(outboxEvent.getId())
            .build();
    return auditClientService.publishAudit(auditEntry, globalContext);
  }

  private String getYamlStringForCatalogConnector(CatalogConnectorEntity catalogConnectorEntity) {
    return NGYamlUtils.getYamlString(CatalogConnectorDTO.builder()
                                         .accountIdentifier(catalogConnectorEntity.getAccountIdentifier())
                                         .connectorIdentifier(catalogConnectorEntity.getConnectorIdentifier())
                                         .connectorProviderType(catalogConnectorEntity.getConnectorProviderType())
                                         .delegateSelectors(catalogConnectorEntity.getDelegateSelectors())
                                         .host(catalogConnectorEntity.getHost())
                                         .build(),
        objectMapper);
  }
}