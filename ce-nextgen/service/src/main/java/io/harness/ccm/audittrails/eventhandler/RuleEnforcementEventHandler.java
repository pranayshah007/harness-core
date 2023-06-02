/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.audittrails.eventhandler;

import static io.harness.ccm.audittrails.events.RuleEnforcementCreateEvent.RULE_ENFORCEMENT_CREATED;
import static io.harness.ccm.audittrails.events.RuleEnforcementDeleteEvent.RULE_ENFORCEMENT_DELETED;
import static io.harness.ccm.audittrails.events.RuleEnforcementUpdateEvent.RULE_ENFORCEMENT_UPDATED;
import static io.harness.ng.core.utils.NGYamlUtils.getYamlString;

import static io.serializer.HObjectMapper.NG_DEFAULT_OBJECT_MAPPER;

import io.harness.ModuleType;
import io.harness.audit.Action;
import io.harness.audit.beans.AuditEntry;
import io.harness.audit.beans.ResourceDTO;
import io.harness.audit.beans.ResourceScopeDTO;
import io.harness.audit.client.api.AuditClientService;
import io.harness.ccm.audittrails.events.RuleEnforcementCreateEvent;
import io.harness.ccm.audittrails.events.RuleEnforcementDeleteEvent;
import io.harness.ccm.audittrails.events.RuleEnforcementUpdateEvent;
import io.harness.ccm.audittrails.yamlDTOs.RuleEnforcementDTO;
import io.harness.context.GlobalContext;
import io.harness.exception.InvalidArgumentsException;
import io.harness.outbox.OutboxEvent;
import io.harness.outbox.api.OutboxEventHandler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;

@Slf4j

public class RuleEnforcementEventHandler implements OutboxEventHandler {
  private final ObjectMapper objectMapper;
  private final AuditClientService auditClientService;

  @Inject
  public RuleEnforcementEventHandler(AuditClientService auditClientService) {
    this.objectMapper = NG_DEFAULT_OBJECT_MAPPER;
    this.auditClientService = auditClientService;
  }

  @Override
  public boolean handle(OutboxEvent outboxEvent) {
    try {
      switch (outboxEvent.getEventType()) {
        case RULE_ENFORCEMENT_CREATED:
          return handleRuleEnforcementCreateEvent(outboxEvent);
        case RULE_ENFORCEMENT_UPDATED:
          return handleRuleEnforcementUpdateEvent(outboxEvent);
        case RULE_ENFORCEMENT_DELETED:
          return handleRuleEnforcementDeleteEvent(outboxEvent);

        default:
          throw new InvalidArgumentsException(String.format("Not supported event type %s", outboxEvent.getEventType()));
      }
    } catch (IOException exception) {
      log.error(exception.toString());
      return false;
    }
  }

  private boolean handleRuleEnforcementCreateEvent(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();
    RuleEnforcementCreateEvent ruleEnforcementCreateEvent =
        objectMapper.readValue(outboxEvent.getEventData(), RuleEnforcementCreateEvent.class);
    AuditEntry auditEntry =
        AuditEntry.builder()
            .action(Action.CREATE)
            .module(ModuleType.CE)
            .newYaml(getYamlString(
                RuleEnforcementDTO.builder().ruleEnforcement(ruleEnforcementCreateEvent.getRuleEnforcement()).build()))
            .timestamp(outboxEvent.getCreatedAt())
            .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
            .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
            .insertId(outboxEvent.getId())
            .build();
    return auditClientService.publishAudit(auditEntry, globalContext);
  }
  private boolean handleRuleEnforcementUpdateEvent(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();
    RuleEnforcementUpdateEvent ruleEnforcementUpdateEvent =
        objectMapper.readValue(outboxEvent.getEventData(), RuleEnforcementUpdateEvent.class);
    AuditEntry auditEntry =
        AuditEntry.builder()
            .action(Action.UPDATE)
            .module(ModuleType.CE)
            .newYaml(getYamlString(
                RuleEnforcementDTO.builder().ruleEnforcement(ruleEnforcementUpdateEvent.getRuleEnforcement()).build()))
            .oldYaml(getYamlString(RuleEnforcementDTO.builder()
                                       .ruleEnforcement(ruleEnforcementUpdateEvent.getOldRuleEnforcement())
                                       .build()))
            .timestamp(outboxEvent.getCreatedAt())
            .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
            .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
            .insertId(outboxEvent.getId())
            .build();
    return auditClientService.publishAudit(auditEntry, globalContext);
  }
  private boolean handleRuleEnforcementDeleteEvent(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();
    RuleEnforcementDeleteEvent ruleEnforcementDeleteEvent =
        objectMapper.readValue(outboxEvent.getEventData(), RuleEnforcementDeleteEvent.class);
    AuditEntry auditEntry =
        AuditEntry.builder()
            .action(Action.DELETE)
            .module(ModuleType.CE)
            .oldYaml(getYamlString(
                RuleEnforcementDTO.builder().ruleEnforcement(ruleEnforcementDeleteEvent.getRuleEnforcement()).build()))
            .timestamp(outboxEvent.getCreatedAt())
            .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
            .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
            .insertId(outboxEvent.getId())
            .build();
    return auditClientService.publishAudit(auditEntry, globalContext);
  }
}
