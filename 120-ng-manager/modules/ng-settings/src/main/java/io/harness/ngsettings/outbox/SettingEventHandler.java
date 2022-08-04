/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngsettings.outbox;

import static io.harness.ng.core.utils.NGYamlUtils.getYamlString;
import static io.harness.ngsettings.events.SettingRestoreEvent.SETTING_RESTORED;
import static io.harness.ngsettings.events.SettingUpdateEvent.SETTING_UPDATED;

import io.harness.ModuleType;
import io.harness.audit.Action;
import io.harness.audit.beans.AuditEntry;
import io.harness.audit.beans.ResourceDTO;
import io.harness.audit.beans.ResourceScopeDTO;
import io.harness.audit.client.api.AuditClientService;
import io.harness.context.GlobalContext;
import io.harness.exception.InvalidArgumentsException;
import io.harness.ngsettings.events.SettingRestoreEvent;
import io.harness.ngsettings.events.SettingUpdateEvent;
import io.harness.outbox.OutboxEvent;
import io.harness.outbox.api.OutboxEventHandler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import java.io.IOException;

public class SettingEventHandler implements OutboxEventHandler {
  private final ObjectMapper objectMapper;
  private final AuditClientService auditClientService;
  @Inject
  public SettingEventHandler(ObjectMapper objectMapper, AuditClientService auditClientService) {
    this.objectMapper = objectMapper;
    this.auditClientService = auditClientService;
  }

  @Override
  public boolean handle(OutboxEvent outboxEvent) {
    try {
      switch (outboxEvent.getEventType()) {
        case SETTING_UPDATED:
          return handleSettingUpdateEvent(outboxEvent);
        case SETTING_RESTORED:
          return handleSettingRestoreEvent(outboxEvent);
        default:
          throw new InvalidArgumentsException(String.format("Not supported event type %s", outboxEvent.getEventType()));
      }
    } catch (IOException exception) {
      return false;
    }
  }

  private boolean handleSettingRestoreEvent(OutboxEvent outboxEvent) throws JsonProcessingException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();
    SettingRestoreEvent settingRestoreEvent =
        objectMapper.readValue(outboxEvent.getEventData(), SettingRestoreEvent.class);
    AuditEntry auditEntry = AuditEntry.builder()
                                .action(Action.RESTORE)
                                .module(ModuleType.CORE)
                                .oldYaml(getYamlString(settingRestoreEvent.getCurrentSettingDTO()))
                                .newYaml(getYamlString(settingRestoreEvent.getUpdatedSettingDTO()))
                                .timestamp(outboxEvent.getCreatedAt())
                                .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
                                .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
                                .insertId(outboxEvent.getId())
                                .build();
    return auditClientService.publishAudit(auditEntry, globalContext);
  }

  private boolean handleSettingUpdateEvent(OutboxEvent outboxEvent) throws JsonProcessingException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();
    SettingUpdateEvent settingUpdateEvent =
        objectMapper.readValue(outboxEvent.getEventData(), SettingUpdateEvent.class);
    AuditEntry auditEntry = AuditEntry.builder()
                                .action(Action.UPDATE)
                                .module(ModuleType.CORE)
                                .oldYaml(getYamlString(settingUpdateEvent.getCurrentSettingDTO()))
                                .newYaml(getYamlString(settingUpdateEvent.getUpdatedSettingDTO()))
                                .timestamp(outboxEvent.getCreatedAt())
                                .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
                                .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
                                .insertId(outboxEvent.getId())
                                .build();
    return auditClientService.publishAudit(auditEntry, globalContext);
  }
}
