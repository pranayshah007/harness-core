/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.audittrails.eventhandler;

import static io.harness.ccm.audittrails.events.RuleCreateEvent.RULE_CREATED;
import static io.harness.ccm.audittrails.events.RuleDeleteEvent.RULE_DELETED;
import static io.harness.ccm.audittrails.events.RuleUpdateEvent.RULE_UPDATED;
import static io.harness.ng.core.utils.NGYamlUtils.getYamlString;

import static io.serializer.HObjectMapper.NG_DEFAULT_OBJECT_MAPPER;

import io.harness.ModuleType;
import io.harness.audit.Action;
import io.harness.audit.beans.AuditEntry;
import io.harness.audit.beans.ResourceDTO;
import io.harness.audit.beans.ResourceScopeDTO;
import io.harness.audit.client.api.AuditClientService;
import io.harness.ccm.audittrails.events.RuleCreateEvent;
import io.harness.ccm.audittrails.events.RuleDeleteEvent;
import io.harness.ccm.audittrails.events.RuleUpdateEvent;
import io.harness.ccm.views.dto.CreateRuleDTO;
import io.harness.context.GlobalContext;
import io.harness.exception.InvalidArgumentsException;
import io.harness.outbox.OutboxEvent;
import io.harness.outbox.api.OutboxEventHandler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;

@Slf4j

public class RuleEventHandler implements OutboxEventHandler {
  private final ObjectMapper objectMapper;
  private final AuditClientService auditClientService;

  @Inject
  public RuleEventHandler(AuditClientService auditClientService) {
    this.objectMapper = NG_DEFAULT_OBJECT_MAPPER;
    this.auditClientService = auditClientService;
  }

  @Override
  public boolean handle(OutboxEvent outboxEvent) {
    try {
      switch (outboxEvent.getEventType()) {
        case RULE_CREATED:
          return handlePolicyCreateEvent(outboxEvent);
        case RULE_UPDATED:
          return handleRuleUpdateEvent(outboxEvent);
        case RULE_DELETED:
          return handleRuleDeleteEvent(outboxEvent);

        default:
          throw new InvalidArgumentsException(String.format("Not supported event type %s", outboxEvent.getEventType()));
      }
    } catch (IOException exception) {
      log.error(exception.toString());
      return false;
    }
  }

  private boolean handlePolicyCreateEvent(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();
    RuleCreateEvent ruleCreateEvent = objectMapper.readValue(outboxEvent.getEventData(), RuleCreateEvent.class);
    AuditEntry auditEntry = AuditEntry.builder()
                                .action(Action.CREATE)
                                .module(ModuleType.CE)
                                .newYaml(getYamlString(CreateRuleDTO.builder().rule(ruleCreateEvent.getRule()).build()))
                                .timestamp(outboxEvent.getCreatedAt())
                                .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
                                .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
                                .insertId(outboxEvent.getId())
                                .build();
    return auditClientService.publishAudit(auditEntry, globalContext);
  }
  private boolean handleRuleUpdateEvent(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();
    RuleUpdateEvent policyUpdateEvent = objectMapper.readValue(outboxEvent.getEventData(), RuleUpdateEvent.class);
    AuditEntry auditEntry =
        AuditEntry.builder()
            .action(Action.UPDATE)
            .module(ModuleType.CE)
            .newYaml(getYamlString(CreateRuleDTO.builder().rule(policyUpdateEvent.getRule()).build()))
            .timestamp(outboxEvent.getCreatedAt())
            .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
            .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
            .insertId(outboxEvent.getId())
            .build();
    return auditClientService.publishAudit(auditEntry, globalContext);
  }
  private boolean handleRuleDeleteEvent(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();
    RuleDeleteEvent policyDeleteEvent = objectMapper.readValue(outboxEvent.getEventData(), RuleDeleteEvent.class);
    AuditEntry auditEntry =
        AuditEntry.builder()
            .action(Action.DELETE)
            .module(ModuleType.CE)
            .newYaml(getYamlString(CreateRuleDTO.builder().rule(policyDeleteEvent.getRule()).build()))
            .timestamp(outboxEvent.getCreatedAt())
            .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
            .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
            .insertId(outboxEvent.getId())
            .build();
    return auditClientService.publishAudit(auditEntry, globalContext);
  }
}
