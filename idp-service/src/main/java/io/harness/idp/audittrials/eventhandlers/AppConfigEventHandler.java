/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.audittrials.eventhandlers;

import static io.harness.idp.configmanager.events.AppConfigCreateEvent.APP_CONFIG_CREATED;
import static io.harness.idp.configmanager.events.AppConfigUpdateEvent.APP_CONFIG_UPDATED;

import static io.serializer.HObjectMapper.NG_DEFAULT_OBJECT_MAPPER;

import io.harness.ModuleType;
import io.harness.audit.Action;
import io.harness.audit.beans.AuditEntry;
import io.harness.audit.beans.ResourceDTO;
import io.harness.audit.beans.ResourceScopeDTO;
import io.harness.audit.client.api.AuditClientService;
import io.harness.context.GlobalContext;
import io.harness.exception.InvalidArgumentsException;
import io.harness.idp.audittrials.eventhandlers.dtos.ConfigDTO;
import io.harness.idp.configmanager.events.AppConfigCreateEvent;
import io.harness.idp.configmanager.events.AppConfigUpdateEvent;
import io.harness.ng.core.utils.NGYamlUtils;
import io.harness.outbox.OutboxEvent;
import io.harness.outbox.api.OutboxEventHandler;
import io.harness.spec.server.idp.v1.model.AppConfig;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AppConfigEventHandler implements OutboxEventHandler {
  private static final ObjectMapper objectMapper = NG_DEFAULT_OBJECT_MAPPER;
  private final AuditClientService auditClientService;

  @Inject
  public AppConfigEventHandler(AuditClientService auditClientService) {
    this.auditClientService = auditClientService;
  }

  @Override
  public boolean handle(OutboxEvent outboxEvent) {
    try {
      switch (outboxEvent.getEventType()) {
        case APP_CONFIG_UPDATED:
          return handleAppConfigUpdateEvent(outboxEvent);
        case APP_CONFIG_CREATED:
          return handleAppConfigSaveEvent(outboxEvent);
        default:
          throw new InvalidArgumentsException(String.format("Not supported event type %s", outboxEvent.getEventType()));
      }
    } catch (IOException exception) {
      log.error("Failed to handle " + outboxEvent.getEventType() + " event", exception);
      return false;
    }
  }

  private boolean handleAppConfigUpdateEvent(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();

    AppConfigUpdateEvent appConfigUpdateEvent =
        objectMapper.readValue(outboxEvent.getEventData(), AppConfigUpdateEvent.class);
    AppConfig newAppConfig = new AppConfig();
    newAppConfig.setConfigs(appConfigUpdateEvent.getNewAppConfig().getConfigs());

    AppConfig oldAppConfig = new AppConfig();
    oldAppConfig.setConfigs(appConfigUpdateEvent.getOldAppConfig().getConfigs());

    AuditEntry auditEntry =
        AuditEntry.builder()
            .action(Action.UPDATE)
            .module(ModuleType.CORE)
            .newYaml(NGYamlUtils.getYamlString(ConfigDTO.builder().appConfig(newAppConfig).build(), objectMapper))
            .oldYaml(NGYamlUtils.getYamlString(ConfigDTO.builder().appConfig(oldAppConfig).build(), objectMapper))
            .timestamp(outboxEvent.getCreatedAt())
            .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
            .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
            .insertId(outboxEvent.getId())
            .build();
    return auditClientService.publishAudit(auditEntry, globalContext);
  }

  private boolean handleAppConfigSaveEvent(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();

    AppConfigCreateEvent appConfigSaveEvent =
        objectMapper.readValue(outboxEvent.getEventData(), AppConfigCreateEvent.class);

    AppConfig newAppConfig = new AppConfig();
    newAppConfig.setConfigs(appConfigSaveEvent.getNewAppConfig().getConfigs());

    AuditEntry auditEntry =
        AuditEntry.builder()
            .action(Action.CREATE)
            .module(ModuleType.CORE)
            .newYaml(NGYamlUtils.getYamlString(ConfigDTO.builder().appConfig(newAppConfig).build(), objectMapper))
            .timestamp(outboxEvent.getCreatedAt())
            .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
            .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
            .insertId(outboxEvent.getId())
            .build();
    return auditClientService.publishAudit(auditEntry, globalContext);
  }
}