/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.audittrails.eventhandler;

import static io.harness.ccm.audittrails.events.BudgetGroup.BudgetGroupCreateEvent.BUDGET_GROUP_CREATED;
import static io.harness.ccm.audittrails.events.BudgetGroup.BudgetGroupDeleteEvent.BUDGET_GROUP_DELETED;
import static io.harness.ccm.audittrails.events.BudgetGroup.BudgetGroupUpdateEvent.BUDGET_GROUP_UPDATED;
import static io.harness.ng.core.utils.NGYamlUtils.getYamlString;

import static io.serializer.HObjectMapper.NG_DEFAULT_OBJECT_MAPPER;

import io.harness.ModuleType;
import io.harness.audit.Action;
import io.harness.audit.beans.AuditEntry;
import io.harness.audit.beans.ResourceDTO;
import io.harness.audit.beans.ResourceScopeDTO;
import io.harness.audit.client.api.AuditClientService;
import io.harness.ccm.audittrails.events.BudgetGroup.BudgetGroupCreateEvent;
import io.harness.ccm.audittrails.events.BudgetGroup.BudgetGroupDeleteEvent;
import io.harness.ccm.audittrails.events.BudgetGroup.BudgetGroupUpdateEvent;
import io.harness.ccm.audittrails.yamlDTOs.BudgetGroupDTO;
import io.harness.context.GlobalContext;
import io.harness.exception.InvalidArgumentsException;
import io.harness.outbox.OutboxEvent;
import io.harness.outbox.api.OutboxEventHandler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BudgetGroupEventHandler implements OutboxEventHandler {
  private final ObjectMapper objectMapper;
  private final AuditClientService auditClientService;

  @Inject
  public BudgetGroupEventHandler(AuditClientService auditClientService) {
    this.objectMapper = NG_DEFAULT_OBJECT_MAPPER;
    this.auditClientService = auditClientService;
  }

  @Override
  public boolean handle(OutboxEvent outboxEvent) {
    try {
      switch (outboxEvent.getEventType()) {
        case BUDGET_GROUP_CREATED:
          return handleBudgetGroupCreateEvent(outboxEvent);
        case BUDGET_GROUP_UPDATED:
          return handleBudgetGroupUpdateEvent(outboxEvent);
        case BUDGET_GROUP_DELETED:
          return handleBudgetGroupDeleteEvent(outboxEvent);
        default:
          throw new InvalidArgumentsException(String.format("Not supported event type %s", outboxEvent.getEventType()));
      }
    } catch (IOException exception) {
      log.error(exception.toString());
      return false;
    }
  }

  private boolean handleBudgetGroupCreateEvent(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();
    BudgetGroupCreateEvent budgetGroupCreateEvent =
        objectMapper.readValue(outboxEvent.getEventData(), BudgetGroupCreateEvent.class);
    AuditEntry auditEntry =
        AuditEntry.builder()
            .action(Action.CREATE)
            .module(ModuleType.CE)
            .newYaml(
                getYamlString(BudgetGroupDTO.builder().budgetGroup(budgetGroupCreateEvent.getBudgetGroupDTO()).build()))
            .timestamp(outboxEvent.getCreatedAt())
            .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
            .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
            .insertId(outboxEvent.getId())
            .build();
    return auditClientService.publishAudit(auditEntry, globalContext);
  }

  private boolean handleBudgetGroupUpdateEvent(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();
    BudgetGroupUpdateEvent budgetGroupUpdateEvent =
        objectMapper.readValue(outboxEvent.getEventData(), BudgetGroupUpdateEvent.class);
    AuditEntry auditEntry =
        AuditEntry.builder()
            .action(Action.UPDATE)
            .module(ModuleType.CE)
            .newYaml(
                getYamlString(BudgetGroupDTO.builder().budgetGroup(budgetGroupUpdateEvent.getBudgetGroupDTO()).build()))
            .oldYaml(getYamlString(
                BudgetGroupDTO.builder().budgetGroup(budgetGroupUpdateEvent.getOldBudgetGroupDTO()).build()))
            .timestamp(outboxEvent.getCreatedAt())
            .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
            .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
            .insertId(outboxEvent.getId())
            .build();

    return auditClientService.publishAudit(auditEntry, globalContext);
  }

  private boolean handleBudgetGroupDeleteEvent(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();
    BudgetGroupDeleteEvent budgetGroupDeleteEvent =
        objectMapper.readValue(outboxEvent.getEventData(), BudgetGroupDeleteEvent.class);
    AuditEntry auditEntry =
        AuditEntry.builder()
            .action(Action.DELETE)
            .module(ModuleType.CE)
            .oldYaml(
                getYamlString(BudgetGroupDTO.builder().budgetGroup(budgetGroupDeleteEvent.getBudgetGroupDTO()).build()))
            .timestamp(outboxEvent.getCreatedAt())
            .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
            .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
            .insertId(outboxEvent.getId())
            .build();

    return auditClientService.publishAudit(auditEntry, globalContext);
  }
}
