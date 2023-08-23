/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.audittrails.eventhandler;

import static io.harness.ccm.audittrails.events.BudgetCreateEvent.BUDGET_CREATED;
import static io.harness.ccm.audittrails.events.BudgetDeleteEvent.BUDGET_DELETED;
import static io.harness.ccm.audittrails.events.BudgetUpdateEvent.BUDGET_UPDATED;
import static io.harness.ng.core.utils.NGYamlUtils.getYamlString;

import static io.serializer.HObjectMapper.NG_DEFAULT_OBJECT_MAPPER;

import io.harness.ModuleType;
import io.harness.audit.Action;
import io.harness.audit.beans.AuditEntry;
import io.harness.audit.beans.ResourceDTO;
import io.harness.audit.beans.ResourceScopeDTO;
import io.harness.audit.client.api.AuditClientService;
import io.harness.ccm.audittrails.events.BudgetCreateEvent;
import io.harness.ccm.audittrails.events.BudgetDeleteEvent;
import io.harness.ccm.audittrails.events.BudgetUpdateEvent;
import io.harness.ccm.audittrails.yamlDTOs.BudgetDTO;
import io.harness.ccm.commons.entities.billing.Budget;
import io.harness.context.GlobalContext;
import io.harness.eventsframework.EventsFrameworkConstants;
import io.harness.eventsframework.EventsFrameworkMetadataConstants;
import io.harness.eventsframework.api.EventsFrameworkDownException;
import io.harness.eventsframework.api.Producer;
import io.harness.eventsframework.entity_crud.EntityChangeDTO;
import io.harness.eventsframework.producer.Message;
import io.harness.exception.InvalidArgumentsException;
import io.harness.outbox.OutboxEvent;
import io.harness.outbox.api.OutboxEventHandler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.google.protobuf.ByteString;
import com.google.protobuf.StringValue;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BudgetEventHandler implements OutboxEventHandler {
  private final ObjectMapper objectMapper;
  private final Producer eventProducer;
  private final AuditClientService auditClientService;

  @Inject
  public BudgetEventHandler(
      @Named(EventsFrameworkConstants.ENTITY_CRUD) Producer eventProducer, AuditClientService auditClientService) {
    this.objectMapper = NG_DEFAULT_OBJECT_MAPPER;
    this.eventProducer = eventProducer;
    this.auditClientService = auditClientService;
  }

  @Override
  public boolean handle(OutboxEvent outboxEvent) {
    try {
      switch (outboxEvent.getEventType()) {
        case BUDGET_CREATED:
          return handleBudgetCreateEvent(outboxEvent);
        case BUDGET_UPDATED:
          return handleBudgetUpdateEvent(outboxEvent);
        case BUDGET_DELETED:
          return handleBudgetDeleteEvent(outboxEvent);
        default:
          throw new InvalidArgumentsException(String.format("Not supported event type %s", outboxEvent.getEventType()));
      }
    } catch (IOException exception) {
      log.error(exception.toString());
      return false;
    }
  }

  private boolean handleBudgetCreateEvent(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();
    BudgetCreateEvent budgetCreateEvent = objectMapper.readValue(outboxEvent.getEventData(), BudgetCreateEvent.class);
    boolean publishedToRedis =
        publishEvent(budgetCreateEvent.getBudgetDTO(), EventsFrameworkMetadataConstants.CREATE_ACTION);
    AuditEntry auditEntry =
        AuditEntry.builder()
            .action(Action.CREATE)
            .module(ModuleType.CE)
            .newYaml(getYamlString(BudgetDTO.builder().budget(budgetCreateEvent.getBudgetDTO()).build()))
            .timestamp(outboxEvent.getCreatedAt())
            .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
            .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
            .insertId(outboxEvent.getId())
            .build();
    return publishedToRedis && auditClientService.publishAudit(auditEntry, globalContext);
  }

  private boolean handleBudgetUpdateEvent(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();
    BudgetUpdateEvent budgetUpdateEvent = objectMapper.readValue(outboxEvent.getEventData(), BudgetUpdateEvent.class);
    boolean publishedToRedis =
        publishEvent(budgetUpdateEvent.getBudgetDTO(), EventsFrameworkMetadataConstants.UPDATE_ACTION);
    AuditEntry auditEntry =
        AuditEntry.builder()
            .action(Action.UPDATE)
            .module(ModuleType.CE)
            .newYaml(getYamlString(BudgetDTO.builder().budget(budgetUpdateEvent.getBudgetDTO()).build()))
            .oldYaml(getYamlString(BudgetDTO.builder().budget(budgetUpdateEvent.getOldBudgetDTO()).build()))
            .timestamp(outboxEvent.getCreatedAt())
            .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
            .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
            .insertId(outboxEvent.getId())
            .build();
    return publishedToRedis && auditClientService.publishAudit(auditEntry, globalContext);
  }

  private boolean handleBudgetDeleteEvent(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();
    BudgetDeleteEvent budgetDeleteEvent = objectMapper.readValue(outboxEvent.getEventData(), BudgetDeleteEvent.class);
    AuditEntry auditEntry =
        AuditEntry.builder()
            .action(Action.DELETE)
            .module(ModuleType.CE)
            .oldYaml(getYamlString(BudgetDTO.builder().budget(budgetDeleteEvent.getBudgetDTO()).build()))
            .timestamp(outboxEvent.getCreatedAt())
            .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
            .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
            .insertId(outboxEvent.getId())
            .build();

    return auditClientService.publishAudit(auditEntry, globalContext);
  }

  private boolean publishEvent(Budget budget, String action) {
    try {
      String eventId = eventProducer.send(
          Message.newBuilder()
              .putAllMetadata(
                  ImmutableMap.of("accountId", budget.getAccountId(), EventsFrameworkMetadataConstants.ENTITY_TYPE,
                      EventsFrameworkMetadataConstants.CCM_BUDGET, EventsFrameworkMetadataConstants.ACTION, action))
              .setData(getBudgetPayload(budget))
              .build());
      log.info("Produced event id:[{}] for budget:[{}], action:[{}]", eventId, budget, action);
      return true;
    } catch (EventsFrameworkDownException e) {
      log.error("Failed to send event to events framework for budget identifier {}", budget.getUuid(), e);
      return false;
    }
  }

  private ByteString getBudgetPayload(Budget budget) {
    EntityChangeDTO.Builder builder = EntityChangeDTO.newBuilder()
                                          .setAccountIdentifier(StringValue.of(budget.getAccountId()))
                                          .setIdentifier(StringValue.of(budget.getUuid()));
    return builder.build().toByteString();
  }
}
