/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.outbox;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.ipallowlist.events.IPAllowlistConfigCreateEvent.IP_ALLOWLIST_CONFIG_CREATED;
import static io.harness.ipallowlist.events.IPAllowlistConfigDeleteEvent.IP_ALLOWLIST_CONFIG_DELETED;
import static io.harness.ipallowlist.events.IPAllowlistConfigUpdateEvent.IP_ALLOWLIST_CONFIG_UPDATED;
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
import io.harness.exception.InvalidArgumentsException;
import io.harness.ipallowlist.dto.IPAllowlistDTO;
import io.harness.ipallowlist.events.IPAllowlistConfigCreateEvent;
import io.harness.ipallowlist.events.IPAllowlistConfigDeleteEvent;
import io.harness.ipallowlist.events.IPAllowlistConfigUpdateEvent;
import io.harness.outbox.OutboxEvent;
import io.harness.outbox.api.OutboxEventHandler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PL)
@Slf4j
public class IPAllowlistConfigEventHandler implements OutboxEventHandler {
  private final ObjectMapper objectMapper;
  private final AuditClientService auditClientService;

  @Inject
  public IPAllowlistConfigEventHandler(AuditClientService auditClientService) {
    this.objectMapper = NG_DEFAULT_OBJECT_MAPPER;
    this.auditClientService = auditClientService;
  }

  @Override
  public boolean handle(OutboxEvent outboxEvent) {
    try {
      switch (outboxEvent.getEventType()) {
        case IP_ALLOWLIST_CONFIG_CREATED:
          return handleIpAllowlistConfigCreateEvent(outboxEvent);
        case IP_ALLOWLIST_CONFIG_UPDATED:
          return handleIpAllowlistConfigUpdateEvent(outboxEvent);
        case IP_ALLOWLIST_CONFIG_DELETED:
          return handleIpAllowlistConfigDeleteEvent(outboxEvent);
        default:
          throw new InvalidArgumentsException(String.format("Not supported event type %s", outboxEvent.getEventType()));
      }
    } catch (IOException exception) {
      return false;
    }
  }

  private boolean handleIpAllowlistConfigCreateEvent(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();
    IPAllowlistConfigCreateEvent ipAllowlistConfigCreateEvent =
        objectMapper.readValue(outboxEvent.getEventData(), IPAllowlistConfigCreateEvent.class);
    AuditEntry auditEntry =
        AuditEntry.builder()
            .action(Action.CREATE)
            .module(ModuleType.CORE)
            .newYaml(getYamlString(IPAllowlistDTO.builder()
                                       .ipAllowlistConfig(ipAllowlistConfigCreateEvent.getIpAllowlistConfig())
                                       .build()))
            .timestamp(outboxEvent.getCreatedAt())
            .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
            .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
            .insertId(outboxEvent.getId())
            .build();
    return auditClientService.publishAudit(auditEntry, globalContext);
  }

  private boolean handleIpAllowlistConfigUpdateEvent(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();
    IPAllowlistConfigUpdateEvent ipAllowlistConfigUpdateEvent =
        objectMapper.readValue(outboxEvent.getEventData(), IPAllowlistConfigUpdateEvent.class);
    AuditEntry auditEntry =
        AuditEntry.builder()
            .action(Action.UPDATE)
            .module(ModuleType.CORE)
            .oldYaml(getYamlString(IPAllowlistDTO.builder()
                                       .ipAllowlistConfig(ipAllowlistConfigUpdateEvent.getOldIpAllowlistConfig())
                                       .build()))
            .newYaml(getYamlString(IPAllowlistDTO.builder()
                                       .ipAllowlistConfig(ipAllowlistConfigUpdateEvent.getNewIpAllowlistConfig())
                                       .build()))
            .timestamp(outboxEvent.getCreatedAt())
            .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
            .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
            .insertId(outboxEvent.getId())
            .build();
    return auditClientService.publishAudit(auditEntry, globalContext);
  }
  private boolean handleIpAllowlistConfigDeleteEvent(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();
    IPAllowlistConfigDeleteEvent ipAllowlistConfigDeleteEvent =
        objectMapper.readValue(outboxEvent.getEventData(), IPAllowlistConfigDeleteEvent.class);
    AuditEntry auditEntry =
        AuditEntry.builder()
            .action(Action.DELETE)
            .module(ModuleType.CORE)
            .oldYaml(getYamlString(IPAllowlistDTO.builder()
                                       .ipAllowlistConfig(ipAllowlistConfigDeleteEvent.getIpAllowlistConfig())
                                       .build()))
            .timestamp(outboxEvent.getCreatedAt())
            .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
            .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
            .insertId(outboxEvent.getId())
            .build();
    return auditClientService.publishAudit(auditEntry, globalContext);
  }
}
