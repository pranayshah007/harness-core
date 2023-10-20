/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.audittrails.eventhandlers;

import static io.harness.idp.configmanager.events.hostproxy.ProxyHostCreateEvent.PROXY_HOST_CREATED;
import static io.harness.idp.configmanager.events.hostproxy.ProxyHostDeleteEvent.PROXY_HOST_DELETED;
import static io.harness.idp.configmanager.events.hostproxy.ProxyHostUpdateEvent.PROXY_HOST_UPDATED;

import static io.serializer.HObjectMapper.NG_DEFAULT_OBJECT_MAPPER;

import io.harness.ModuleType;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.audit.Action;
import io.harness.audit.beans.AuditEntry;
import io.harness.audit.beans.ResourceDTO;
import io.harness.audit.beans.ResourceScopeDTO;
import io.harness.audit.client.api.AuditClientService;
import io.harness.context.GlobalContext;
import io.harness.exception.InvalidArgumentsException;
import io.harness.idp.audittrails.eventhandlers.dtos.ProxyHostDTO;
import io.harness.idp.configmanager.events.hostproxy.ProxyHostCreateEvent;
import io.harness.idp.configmanager.events.hostproxy.ProxyHostDeleteEvent;
import io.harness.idp.configmanager.events.hostproxy.ProxyHostUpdateEvent;
import io.harness.ng.core.utils.NGYamlUtils;
import io.harness.outbox.OutboxEvent;
import io.harness.outbox.api.OutboxEventHandler;
import io.harness.spec.server.idp.v1.model.ProxyHostDetail;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class ProxyHostDetailsEventHandler implements OutboxEventHandler {
  private static final ObjectMapper objectMapper = NG_DEFAULT_OBJECT_MAPPER;
  private final AuditClientService auditClientService;

  @Inject
  public ProxyHostDetailsEventHandler(AuditClientService auditClientService) {
    this.auditClientService = auditClientService;
  }

  @Override
  public boolean handle(OutboxEvent outboxEvent) {
    try {
      switch (outboxEvent.getEventType()) {
        case PROXY_HOST_CREATED:
          return handleProxyHostCreateEvent(outboxEvent);
        case PROXY_HOST_UPDATED:
          return handleProxyHostUpdateEvent(outboxEvent);
        case PROXY_HOST_DELETED:
          return handleProxyHostDeleteEvent(outboxEvent);
        default:
          throw new InvalidArgumentsException(String.format("Not supported event type %s", outboxEvent.getEventType()));
      }
    } catch (IOException exception) {
      log.error("Failed to handle " + outboxEvent.getEventType() + " event", exception);
      return false;
    }
  }

  private boolean handleProxyHostCreateEvent(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();

    ProxyHostCreateEvent proxyHostCreateEvent =
        objectMapper.readValue(outboxEvent.getEventData(), ProxyHostCreateEvent.class);

    AuditEntry auditEntry = AuditEntry.builder()
                                .action(Action.CREATE)
                                .module(ModuleType.IDP)
                                .newYaml(getYamlStringForProxyHost(proxyHostCreateEvent.getNewProxyHostDetail()))
                                .timestamp(outboxEvent.getCreatedAt())
                                .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
                                .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
                                .insertId(outboxEvent.getId())
                                .build();
    return auditClientService.publishAudit(auditEntry, globalContext);
  }

  private boolean handleProxyHostUpdateEvent(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();

    ProxyHostUpdateEvent proxyHostUpdateEvent =
        objectMapper.readValue(outboxEvent.getEventData(), ProxyHostUpdateEvent.class);

    AuditEntry auditEntry = AuditEntry.builder()
                                .action(Action.UPDATE)
                                .module(ModuleType.IDP)
                                .newYaml(getYamlStringForProxyHost(proxyHostUpdateEvent.getNewProxyHostDetail()))
                                .oldYaml(getYamlStringForProxyHost(proxyHostUpdateEvent.getOldProxyHostDetail()))
                                .timestamp(outboxEvent.getCreatedAt())
                                .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
                                .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
                                .insertId(outboxEvent.getId())
                                .build();
    return auditClientService.publishAudit(auditEntry, globalContext);
  }

  private boolean handleProxyHostDeleteEvent(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();

    ProxyHostDeleteEvent proxyHostDeleteEvent =
        objectMapper.readValue(outboxEvent.getEventData(), ProxyHostDeleteEvent.class);

    AuditEntry auditEntry = AuditEntry.builder()
                                .action(Action.DELETE)
                                .module(ModuleType.IDP)
                                .oldYaml(getYamlStringForProxyHost(proxyHostDeleteEvent.getOldProxyHostDetail()))
                                .timestamp(outboxEvent.getCreatedAt())
                                .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
                                .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
                                .insertId(outboxEvent.getId())
                                .build();
    return auditClientService.publishAudit(auditEntry, globalContext);
  }

  private String getYamlStringForProxyHost(ProxyHostDetail proxyHostDetail) {
    return NGYamlUtils.getYamlString(ProxyHostDTO.builder()
                                         .pluginId(proxyHostDetail.getPluginId())
                                         .host(proxyHostDetail.getHost())
                                         .delegateSelectors(proxyHostDetail.getSelectors())
                                         .build(),
        objectMapper);
  }
}