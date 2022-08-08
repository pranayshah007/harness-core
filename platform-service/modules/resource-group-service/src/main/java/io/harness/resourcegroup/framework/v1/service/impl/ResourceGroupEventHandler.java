/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.resourcegroup.framework.v1.service.impl;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.ng.core.utils.NGYamlUtils.getYamlString;

import static io.serializer.HObjectMapper.NG_DEFAULT_OBJECT_MAPPER;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.ModuleType;
import io.harness.annotations.dev.OwnedBy;
import io.harness.audit.Action;
import io.harness.audit.beans.AuditEntry;
import io.harness.audit.beans.ResourceDTO;
import io.harness.audit.beans.ResourceScopeDTO;
import io.harness.audit.client.api.AuditClientService;
import io.harness.context.GlobalContext;
import io.harness.eventsframework.EventsFrameworkConstants;
import io.harness.eventsframework.EventsFrameworkMetadataConstants;
import io.harness.eventsframework.api.EventsFrameworkDownException;
import io.harness.eventsframework.api.Producer;
import io.harness.eventsframework.entity_crud.resourcegroup.ResourceGroupEntityChangeDTO;
import io.harness.eventsframework.producer.Message;
import io.harness.exception.InvalidArgumentsException;
import io.harness.outbox.OutboxEvent;
import io.harness.outbox.api.OutboxEventHandler;
import io.harness.resourcegroup.framework.v1.events.ResourceGroupCreateEvent;
import io.harness.resourcegroup.framework.v1.events.ResourceGroupDeleteEvent;
import io.harness.resourcegroup.framework.v1.events.ResourceGroupUpdateEvent;
import io.harness.resourcegroup.v2.remote.dto.ResourceGroupDTO;
import io.harness.resourcegroup.v2.remote.dto.ResourceGroupRequest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.google.protobuf.ByteString;
import java.io.IOException;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PL)
@Slf4j
public class ResourceGroupEventHandler implements OutboxEventHandler {
  private final ObjectMapper objectMapper;
  private final Producer eventProducer;
  private final AuditClientService auditClientService;

  @Inject
  public ResourceGroupEventHandler(
      @Named(EventsFrameworkConstants.ENTITY_CRUD) Producer eventProducer, AuditClientService auditClientService) {
    this.objectMapper = NG_DEFAULT_OBJECT_MAPPER;
    this.eventProducer = eventProducer;
    this.auditClientService = auditClientService;
  }

  @Override
  public boolean handle(OutboxEvent outboxEvent) {
    try {
      switch (outboxEvent.getEventType()) {
        case "ResourceGroupCreated":
          return handleResourceGroupCreateEvent(outboxEvent);
        case "ResourceGroupUpdated":
          return handleResourceGroupUpdateEvent(outboxEvent);
        case "ResourceGroupDeleted":
          return handleResourceGroupDeleteEvent(outboxEvent);
        default:
          throw new InvalidArgumentsException(String.format("Not supported event type %s", outboxEvent.getEventType()));
      }
    } catch (IOException exception) {
      log.error("Couldn't handle resource group outboxevent {}", outboxEvent, exception);
      return false;
    }
  }

  private boolean handleResourceGroupCreateEvent(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();
    ResourceGroupCreateEvent resourceGroupCreateEvent =
        objectMapper.readValue(outboxEvent.getEventData(), ResourceGroupCreateEvent.class);
    boolean publishedToRedis =
        publishEvent(resourceGroupCreateEvent.getResourceGroupV2(), EventsFrameworkMetadataConstants.CREATE_ACTION);
    AuditEntry auditEntry =
        AuditEntry.builder()
            .action(Action.CREATE)
            .module(ModuleType.CORE)
            .newYaml(getYamlString(
                ResourceGroupRequest.builder().resourceGroup(resourceGroupCreateEvent.getResourceGroupV2()).build()))
            .timestamp(outboxEvent.getCreatedAt())
            .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
            .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
            .insertId(outboxEvent.getId())
            .build();
    return publishedToRedis && auditClientService.publishAudit(auditEntry, globalContext);
  }

  private boolean handleResourceGroupUpdateEvent(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();
    ResourceGroupUpdateEvent resourceGroupUpdateEvent =
        objectMapper.readValue(outboxEvent.getEventData(), ResourceGroupUpdateEvent.class);
    boolean publishedToRedis =
        publishEvent(resourceGroupUpdateEvent.getNewResourceGroupV2(), EventsFrameworkMetadataConstants.UPDATE_ACTION);
    AuditEntry auditEntry =
        AuditEntry.builder()
            .action(Action.UPDATE)
            .module(ModuleType.CORE)
            .newYaml(getYamlString(
                ResourceGroupRequest.builder().resourceGroup(resourceGroupUpdateEvent.getNewResourceGroupV2()).build()))
            .oldYaml(getYamlString(
                ResourceGroupRequest.builder().resourceGroup(resourceGroupUpdateEvent.getOldResourceGroupV2()).build()))
            .timestamp(outboxEvent.getCreatedAt())
            .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
            .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
            .insertId(outboxEvent.getId())
            .build();
    return publishedToRedis && auditClientService.publishAudit(auditEntry, globalContext);
  }

  private boolean handleResourceGroupDeleteEvent(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();
    ResourceGroupDeleteEvent resourceGroupDeleteEvent =
        objectMapper.readValue(outboxEvent.getEventData(), ResourceGroupDeleteEvent.class);
    boolean publishedToRedis =
        publishEvent(resourceGroupDeleteEvent.getResourceGroupV2(), EventsFrameworkMetadataConstants.DELETE_ACTION);
    AuditEntry auditEntry =
        AuditEntry.builder()
            .action(Action.DELETE)
            .module(ModuleType.CORE)
            .oldYaml(getYamlString(
                ResourceGroupRequest.builder().resourceGroup(resourceGroupDeleteEvent.getResourceGroupV2()).build()))
            .timestamp(outboxEvent.getCreatedAt())
            .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
            .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
            .insertId(outboxEvent.getId())
            .build();
    return publishedToRedis && auditClientService.publishAudit(auditEntry, globalContext);
  }

  private boolean publishEvent(ResourceGroupDTO resourceGroup, String action) {
    try {
      Map<String, String> metadataMap;
      if (isNotBlank(resourceGroup.getAccountIdentifier())) {
        metadataMap = ImmutableMap.of("accountId", resourceGroup.getAccountIdentifier(),
            EventsFrameworkMetadataConstants.ENTITY_TYPE, EventsFrameworkMetadataConstants.RESOURCE_GROUP,
            EventsFrameworkMetadataConstants.ACTION, action);

      } else {
        metadataMap = ImmutableMap.of(EventsFrameworkMetadataConstants.ENTITY_TYPE,
            EventsFrameworkMetadataConstants.RESOURCE_GROUP, EventsFrameworkMetadataConstants.ACTION, action);
      }
      eventProducer.send(
          Message.newBuilder().putAllMetadata(metadataMap).setData(getResourceGroupPayload(resourceGroup)).build());
      return true;
    } catch (EventsFrameworkDownException e) {
      log.error(
          "Failed to send event to events framework for resourcegroup Identifier {}", resourceGroup.getIdentifier(), e);
      return false;
    }
  }

  private ByteString getResourceGroupPayload(ResourceGroupDTO resourceGroup) {
    ResourceGroupEntityChangeDTO.Builder resourceGroupEntityChangeDTOBuilder =
        ResourceGroupEntityChangeDTO.newBuilder().setIdentifier(resourceGroup.getIdentifier());
    if (isNotBlank(resourceGroup.getAccountIdentifier())) {
      resourceGroupEntityChangeDTOBuilder.setAccountIdentifier(resourceGroup.getAccountIdentifier());
    }
    if (isNotBlank(resourceGroup.getOrgIdentifier())) {
      resourceGroupEntityChangeDTOBuilder.setOrgIdentifier(resourceGroup.getOrgIdentifier());
    }
    if (isNotBlank(resourceGroup.getProjectIdentifier())) {
      resourceGroupEntityChangeDTOBuilder.setProjectIdentifier(resourceGroup.getProjectIdentifier());
    }
    return resourceGroupEntityChangeDTOBuilder.build().toByteString();
  }
}
