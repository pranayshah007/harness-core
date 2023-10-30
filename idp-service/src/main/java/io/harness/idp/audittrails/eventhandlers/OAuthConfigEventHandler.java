/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.audittrails.eventhandlers;

import static io.harness.idp.plugin.events.OAuthConfigCreateEvent.OAUTH_CONFIG_CREATED;
import static io.harness.idp.plugin.events.OAuthConfigUpdateEvent.OAUTH_CONFIG_UPDATED;

import static io.serializer.HObjectMapper.NG_DEFAULT_OBJECT_MAPPER;

import io.harness.ModuleType;
import io.harness.audit.Action;
import io.harness.audit.beans.AuditEntry;
import io.harness.audit.beans.ResourceDTO;
import io.harness.audit.beans.ResourceScopeDTO;
import io.harness.audit.client.api.AuditClientService;
import io.harness.context.GlobalContext;
import io.harness.exception.InvalidArgumentsException;
import io.harness.idp.audittrails.eventhandlers.utils.OAuthEventUtils;
import io.harness.idp.plugin.events.OAuthConfigCreateEvent;
import io.harness.idp.plugin.events.OAuthConfigUpdateEvent;
import io.harness.outbox.OutboxEvent;
import io.harness.outbox.api.OutboxEventHandler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class OAuthConfigEventHandler implements OutboxEventHandler {
  private static final ObjectMapper objectMapper = NG_DEFAULT_OBJECT_MAPPER;
  private final AuditClientService auditClientService;

  @Inject
  public OAuthConfigEventHandler(AuditClientService auditClientService) {
    this.auditClientService = auditClientService;
  }

  @Override
  public boolean handle(OutboxEvent outboxEvent) {
    try {
      switch (outboxEvent.getEventType()) {
        case OAUTH_CONFIG_CREATED:
          return handleOAuthConfigCreateEvent(outboxEvent);
        case OAUTH_CONFIG_UPDATED:
          return handleOAuthConfigUpdateEvent(outboxEvent);
        default:
          throw new InvalidArgumentsException(String.format("Not supported event type %s", outboxEvent.getEventType()));
      }
    } catch (IOException exception) {
      log.error("Failed to handle " + outboxEvent.getEventType() + " event", exception);
      return false;
    }
  }

  private boolean handleOAuthConfigCreateEvent(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();

    OAuthConfigCreateEvent oAuthConfigCreateEvent =
        objectMapper.readValue(outboxEvent.getEventData(), OAuthConfigCreateEvent.class);

    AuditEntry auditEntry =
        AuditEntry.builder()
            .action(Action.CREATE)
            .module(ModuleType.IDP)
            .newYaml(OAuthEventUtils.getOAuthConfigYamlForAudit(oAuthConfigCreateEvent.getAccountIdentifier(),
                oAuthConfigCreateEvent.getNewBackstageEnvVariables(), oAuthConfigCreateEvent.getAuthId()))
            .timestamp(outboxEvent.getCreatedAt())
            .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
            .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
            .insertId(outboxEvent.getId())
            .build();
    return auditClientService.publishAudit(auditEntry, globalContext);
  }

  private boolean handleOAuthConfigUpdateEvent(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();

    OAuthConfigUpdateEvent oAuthConfigUpdateEvent =
        objectMapper.readValue(outboxEvent.getEventData(), OAuthConfigUpdateEvent.class);

    AuditEntry auditEntry =
        AuditEntry.builder()
            .action(Action.UPDATE)
            .module(ModuleType.IDP)
            .newYaml(OAuthEventUtils.getOAuthConfigYamlForAudit(oAuthConfigUpdateEvent.getAccountIdentifier(),
                oAuthConfigUpdateEvent.getNewBackstageEnvVariables(), oAuthConfigUpdateEvent.getAuthId()))
            .oldYaml(OAuthEventUtils.getOAuthConfigYamlForAudit(oAuthConfigUpdateEvent.getAccountIdentifier(),
                oAuthConfigUpdateEvent.getOldBackstageEnvVariables(), oAuthConfigUpdateEvent.getAuthId()))
            .timestamp(outboxEvent.getCreatedAt())
            .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
            .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
            .insertId(outboxEvent.getId())
            .build();
    return auditClientService.publishAudit(auditEntry, globalContext);
  }
}