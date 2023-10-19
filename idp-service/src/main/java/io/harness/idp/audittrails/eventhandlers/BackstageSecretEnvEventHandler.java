/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.audittrails.eventhandlers;

import static io.harness.idp.configmanager.events.envvariables.BackstageEnvSecretCreateEvent.ENV_VARIABLE_CREATED;
import static io.harness.idp.configmanager.events.envvariables.BackstageEnvSecretDeleteEvent.ENV_VARIABLE_DELETED;
import static io.harness.idp.configmanager.events.envvariables.BackstageEnvSecretUpdateEvent.ENV_VARIABLE_UPDATED;

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
import io.harness.idp.audittrails.eventhandlers.dtos.BackstageEnvSecretDTO;
import io.harness.idp.configmanager.events.envvariables.BackstageEnvSecretCreateEvent;
import io.harness.idp.configmanager.events.envvariables.BackstageEnvSecretDeleteEvent;
import io.harness.idp.configmanager.events.envvariables.BackstageEnvSecretUpdateEvent;
import io.harness.ng.core.utils.NGYamlUtils;
import io.harness.outbox.OutboxEvent;
import io.harness.outbox.api.OutboxEventHandler;
import io.harness.spec.server.idp.v1.model.BackstageEnvSecretVariable;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class BackstageSecretEnvEventHandler implements OutboxEventHandler {
  private static final ObjectMapper objectMapper = NG_DEFAULT_OBJECT_MAPPER;
  private final AuditClientService auditClientService;

  @Inject
  public BackstageSecretEnvEventHandler(AuditClientService auditClientService) {
    this.auditClientService = auditClientService;
  }

  @Override
  public boolean handle(OutboxEvent outboxEvent) {
    try {
      switch (outboxEvent.getEventType()) {
        case ENV_VARIABLE_CREATED:
          return handleBackstageSecretEnvCreateEvent(outboxEvent);
        case ENV_VARIABLE_UPDATED:
          return handleBackstageSecretEnvUpdateEvent(outboxEvent);
        case ENV_VARIABLE_DELETED:
          return handleBackstageSecretEnvDeleteEvent(outboxEvent);
        default:
          throw new InvalidArgumentsException(String.format("Not supported event type %s", outboxEvent.getEventType()));
      }
    } catch (IOException exception) {
      log.error("Failed to handle " + outboxEvent.getEventType() + " event", exception);
      return false;
    }
  }

  private boolean handleBackstageSecretEnvCreateEvent(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();

    BackstageEnvSecretCreateEvent backstageEnvSecretCreateEvent =
        objectMapper.readValue(outboxEvent.getEventData(), BackstageEnvSecretCreateEvent.class);

    BackstageEnvSecretVariable backstageEnvSecretVariable = new BackstageEnvSecretVariable();
    backstageEnvSecretVariable.setEnvName(
        backstageEnvSecretCreateEvent.getNewBackstageEnvSecretVariable().getEnvName());
    backstageEnvSecretVariable.setHarnessSecretIdentifier(
        backstageEnvSecretCreateEvent.getNewBackstageEnvSecretVariable().getHarnessSecretIdentifier());

    AuditEntry auditEntry =
        AuditEntry.builder()
            .action(Action.CREATE)
            .module(ModuleType.CORE)
            .newYaml(getYamlStringForEnvVariables(backstageEnvSecretCreateEvent.getNewBackstageEnvSecretVariable()))
            .timestamp(outboxEvent.getCreatedAt())
            .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
            .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
            .insertId(outboxEvent.getId())
            .build();
    return auditClientService.publishAudit(auditEntry, globalContext);
  }

  private boolean handleBackstageSecretEnvUpdateEvent(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();

    BackstageEnvSecretUpdateEvent backstageEnvSecretUpdateEvent =
        objectMapper.readValue(outboxEvent.getEventData(), BackstageEnvSecretUpdateEvent.class);

    AuditEntry auditEntry =
        AuditEntry.builder()
            .action(Action.UPDATE)
            .module(ModuleType.CORE)
            .newYaml(getYamlStringForEnvVariables(backstageEnvSecretUpdateEvent.getNewBackstageEnvSecretVariable()))
            .oldYaml(getYamlStringForEnvVariables(backstageEnvSecretUpdateEvent.getOldBackstageEnvSecretVariable()))
            .timestamp(outboxEvent.getCreatedAt())
            .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
            .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
            .insertId(outboxEvent.getId())
            .build();
    return auditClientService.publishAudit(auditEntry, globalContext);
  }

  private boolean handleBackstageSecretEnvDeleteEvent(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();

    BackstageEnvSecretDeleteEvent backstageEnvSecretDeleteEvent =
        objectMapper.readValue(outboxEvent.getEventData(), BackstageEnvSecretDeleteEvent.class);

    AuditEntry auditEntry =
        AuditEntry.builder()
            .action(Action.DELETE)
            .module(ModuleType.CORE)
            .oldYaml(getYamlStringForEnvVariables(backstageEnvSecretDeleteEvent.getOldBackstageEnvSecretVariable()))
            .timestamp(outboxEvent.getCreatedAt())
            .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
            .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
            .insertId(outboxEvent.getId())
            .build();
    return auditClientService.publishAudit(auditEntry, globalContext);
  }

  private String getYamlStringForEnvVariables(BackstageEnvSecretVariable backstageEnvSecretVariable) {
    return NGYamlUtils.getYamlString(BackstageEnvSecretDTO.builder()
                                         .envVariableName(backstageEnvSecretVariable.getEnvName())
                                         .secretIdentifier(backstageEnvSecretVariable.getHarnessSecretIdentifier())
                                         .build(),
        objectMapper);
  }
}