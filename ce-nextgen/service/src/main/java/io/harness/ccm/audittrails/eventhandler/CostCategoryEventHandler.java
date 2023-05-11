/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.audittrails.eventhandler;

import static io.harness.ccm.audittrails.events.CostCategoryCreateEvent.COST_CATEGORY_CREATED;
import static io.harness.ccm.audittrails.events.CostCategoryDeleteEvent.COST_CATEGORY_DELETED;
import static io.harness.ccm.audittrails.events.CostCategoryUpdateEvent.COST_CATEGORY_UPDATED;
import static io.harness.ng.core.utils.NGYamlUtils.getYamlString;

import static io.serializer.HObjectMapper.NG_DEFAULT_OBJECT_MAPPER;

import io.harness.ModuleType;
import io.harness.audit.Action;
import io.harness.audit.beans.AuditEntry;
import io.harness.audit.beans.ResourceDTO;
import io.harness.audit.beans.ResourceScopeDTO;
import io.harness.audit.client.api.AuditClientService;
import io.harness.ccm.audittrails.events.CostCategoryCreateEvent;
import io.harness.ccm.audittrails.events.CostCategoryDeleteEvent;
import io.harness.ccm.audittrails.events.CostCategoryUpdateEvent;
import io.harness.ccm.audittrails.yamlDTOs.CostCategoryDTO;
import io.harness.ccm.views.businessmapping.entities.BusinessMapping;
import io.harness.ccm.views.businessmapping.service.intf.BusinessMappingHistoryService;
import io.harness.context.GlobalContext;
import io.harness.exception.InvalidArgumentsException;
import io.harness.outbox.OutboxEvent;
import io.harness.outbox.api.OutboxEventHandler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import java.io.IOException;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CostCategoryEventHandler implements OutboxEventHandler {
  private final ObjectMapper objectMapper;
  private final AuditClientService auditClientService;
  private final BusinessMappingHistoryService businessMappingHistoryService;

  @Inject
  public CostCategoryEventHandler(
      AuditClientService auditClientService, BusinessMappingHistoryService businessMappingHistoryService) {
    this.objectMapper = NG_DEFAULT_OBJECT_MAPPER;
    this.auditClientService = auditClientService;
    this.businessMappingHistoryService = businessMappingHistoryService;
  }

  @Override
  public boolean handle(OutboxEvent outboxEvent) {
    try {
      switch (outboxEvent.getEventType()) {
        case COST_CATEGORY_CREATED:
          return handleCostCategoryCreateEvent(outboxEvent);
        case COST_CATEGORY_UPDATED:
          return handleCostCategoryUpdateEvent(outboxEvent);
        case COST_CATEGORY_DELETED:
          return handleCostCategoryDeleteEvent(outboxEvent);
        default:
          throw new InvalidArgumentsException(String.format("Not supported event type %s", outboxEvent.getEventType()));
      }
    } catch (IOException exception) {
      log.error(exception.toString());
      return false;
    }
  }

  private boolean handleCostCategoryCreateEvent(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();
    CostCategoryCreateEvent costCategoryCreateEvent =
        objectMapper.readValue(outboxEvent.getEventData(), CostCategoryCreateEvent.class);
    BusinessMapping costCategory = costCategoryCreateEvent.getCostCategoryDTO();

    businessMappingHistoryService.handleCreateEvent(costCategory, Instant.ofEpochMilli(outboxEvent.getCreatedAt()));

    AuditEntry auditEntry = AuditEntry.builder()
                                .action(Action.CREATE)
                                .module(ModuleType.CE)
                                .newYaml(getYamlString(CostCategoryDTO.builder().costCategory(costCategory).build()))
                                .timestamp(outboxEvent.getCreatedAt())
                                .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
                                .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
                                .insertId(outboxEvent.getId())
                                .build();
    return auditClientService.publishAudit(auditEntry, globalContext);
  }

  private boolean handleCostCategoryUpdateEvent(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();
    CostCategoryUpdateEvent costCategoryUpdateEvent =
        objectMapper.readValue(outboxEvent.getEventData(), CostCategoryUpdateEvent.class);
    BusinessMapping businessMapping = costCategoryUpdateEvent.getCostCategoryDTO();

    businessMappingHistoryService.handleUpdateEvent(businessMapping, Instant.ofEpochMilli(outboxEvent.getCreatedAt()));

    AuditEntry auditEntry =
        AuditEntry.builder()
            .action(Action.UPDATE)
            .module(ModuleType.CE)
            .newYaml(getYamlString(
                CostCategoryDTO.builder().costCategory(costCategoryUpdateEvent.getCostCategoryDTO()).build()))
            .oldYaml(getYamlString(
                CostCategoryDTO.builder().costCategory(costCategoryUpdateEvent.getOldCostCategoryDTO()).build()))
            .timestamp(outboxEvent.getCreatedAt())
            .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
            .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
            .insertId(outboxEvent.getId())
            .build();

    return auditClientService.publishAudit(auditEntry, globalContext);
  }

  private boolean handleCostCategoryDeleteEvent(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();
    CostCategoryDeleteEvent costCategoryDeleteEvent =
        objectMapper.readValue(outboxEvent.getEventData(), CostCategoryDeleteEvent.class);
    BusinessMapping costCategory = costCategoryDeleteEvent.getCostCategoryDTO();

    businessMappingHistoryService.handleDeleteEvent(costCategory, Instant.ofEpochMilli(outboxEvent.getCreatedAt()));

    AuditEntry auditEntry = AuditEntry.builder()
                                .action(Action.DELETE)
                                .module(ModuleType.CE)
                                .oldYaml(getYamlString(CostCategoryDTO.builder().costCategory(costCategory).build()))
                                .timestamp(outboxEvent.getCreatedAt())
                                .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
                                .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
                                .insertId(outboxEvent.getId())
                                .build();

    return auditClientService.publishAudit(auditEntry, globalContext);
  }
}
