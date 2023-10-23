/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.audittrails.eventhandlers;

import static io.harness.idp.allowlist.events.AllowListCreateEvent.ALLOW_LIST_CREATED;
import static io.harness.idp.allowlist.events.AllowListUpdateEvent.ALLOW_LIST_UPDATED;

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
import io.harness.idp.allowlist.events.AllowListCreateEvent;
import io.harness.idp.allowlist.events.AllowListUpdateEvent;
import io.harness.idp.audittrails.eventhandlers.dtos.AllowListConfigDTO;
import io.harness.ng.core.utils.NGYamlUtils;
import io.harness.outbox.OutboxEvent;
import io.harness.outbox.api.OutboxEventHandler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class AllowListEventHandler implements OutboxEventHandler {
  private static final ObjectMapper objectMapper = NG_DEFAULT_OBJECT_MAPPER;
  private final AuditClientService auditClientService;

  @Inject
  public AllowListEventHandler(AuditClientService auditClientService) {
    this.auditClientService = auditClientService;
  }

  @Override
  public boolean handle(OutboxEvent outboxEvent) {
    try {
      switch (outboxEvent.getEventType()) {
        case ALLOW_LIST_UPDATED:
          return handleAllowListUpdateEvent(outboxEvent);
        case ALLOW_LIST_CREATED:
          return handleAllowListSaveEvent(outboxEvent);
        default:
          throw new InvalidArgumentsException(String.format("Not supported event type %s", outboxEvent.getEventType()));
      }
    } catch (IOException exception) {
      log.error("Failed to handle " + outboxEvent.getEventType() + " event", exception);
      return false;
    }
  }

  private boolean handleAllowListUpdateEvent(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();

    AllowListUpdateEvent allowListUpdateEvent =
        objectMapper.readValue(outboxEvent.getEventData(), AllowListUpdateEvent.class);

    AuditEntry auditEntry = AuditEntry.builder()
                                .action(Action.UPDATE)
                                .module(ModuleType.IDP)
                                .newYaml(NGYamlUtils.getYamlString(
                                    AllowListConfigDTO.builder()
                                        .allowListConfig(allowListUpdateEvent.getNewAllowListAppConfig().getConfigs())
                                        .build(),
                                    objectMapper))
                                .oldYaml(NGYamlUtils.getYamlString(
                                    AllowListConfigDTO.builder()
                                        .allowListConfig(allowListUpdateEvent.getOldAllowListAppConfig().getConfigs())
                                        .build(),
                                    objectMapper))
                                .timestamp(outboxEvent.getCreatedAt())
                                .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
                                .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
                                .insertId(outboxEvent.getId())
                                .build();
    return auditClientService.publishAudit(auditEntry, globalContext);
  }

  private boolean handleAllowListSaveEvent(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();

    AllowListCreateEvent allowListCreateEvent =
        objectMapper.readValue(outboxEvent.getEventData(), AllowListCreateEvent.class);

    AuditEntry auditEntry = AuditEntry.builder()
                                .action(Action.CREATE)
                                .module(ModuleType.IDP)
                                .newYaml(NGYamlUtils.getYamlString(
                                    AllowListConfigDTO.builder()
                                        .allowListConfig(allowListCreateEvent.getNewAllowListAppConfig().getConfigs())
                                        .build(),
                                    objectMapper))
                                .timestamp(outboxEvent.getCreatedAt())
                                .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
                                .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
                                .insertId(outboxEvent.getId())
                                .build();
    return auditClientService.publishAudit(auditEntry, globalContext);
  }
}
