/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.audittrials.eventhandlers;

import static io.harness.idp.configmanager.events.BackstageEnvSecretSaveEvent.ENV_VARIABLE_CREATED;

import static io.serializer.HObjectMapper.NG_DEFAULT_OBJECT_MAPPER;

import io.harness.ModuleType;
import io.harness.audit.Action;
import io.harness.audit.beans.AuditEntry;
import io.harness.audit.beans.ResourceDTO;
import io.harness.audit.beans.ResourceScopeDTO;
import io.harness.audit.client.api.AuditClientService;
import io.harness.context.GlobalContext;
import io.harness.exception.InvalidArgumentsException;
import io.harness.idp.audittrials.eventhandlers.dtos.BackstageEnvSecretDTO;
import io.harness.idp.configmanager.events.BackstageEnvSecretSaveEvent;
import io.harness.ng.core.utils.NGYamlUtils;
import io.harness.outbox.OutboxEvent;
import io.harness.outbox.api.OutboxEventHandler;
import io.harness.spec.server.idp.v1.model.BackstageEnvSecretVariable;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
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
          return handleBackstageSecretEnvSaveEvent(outboxEvent);
        default:
          throw new InvalidArgumentsException(String.format("Not supported event type %s", outboxEvent.getEventType()));
      }
    } catch (IOException exception) {
      log.error("Failed to handle " + outboxEvent.getEventType() + " event", exception);
      return false;
    }
  }

  private boolean handleBackstageSecretEnvSaveEvent(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();

    BackstageEnvSecretSaveEvent backstageEnvSecretSaveEvent =
        objectMapper.readValue(outboxEvent.getEventData(), BackstageEnvSecretSaveEvent.class);

    BackstageEnvSecretVariable backstageEnvSecretVariable = new BackstageEnvSecretVariable();
    backstageEnvSecretVariable.setEnvName(backstageEnvSecretSaveEvent.getNewBackstageEnvSecretVariable().getEnvName());
    backstageEnvSecretVariable.setHarnessSecretIdentifier(
        backstageEnvSecretSaveEvent.getNewBackstageEnvSecretVariable().getHarnessSecretIdentifier());

    AuditEntry auditEntry = AuditEntry.builder()
                                .action(Action.CREATE)
                                .module(ModuleType.CORE)
                                .newYaml(NGYamlUtils.getYamlString(
                                    BackstageEnvSecretDTO.builder()
                                        .envVariableName(backstageEnvSecretVariable.getEnvName())
                                        .secretIdentifier(backstageEnvSecretVariable.getHarnessSecretIdentifier())
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