/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.commons.outbox;

import static io.harness.accesscontrol.resources.resourcegroups.events.ResourceGroupCreateEvent.RESOURCE_GROUP_CREATE_EVENT;
import static io.harness.accesscontrol.resources.resourcegroups.events.ResourceGroupDeleteEvent.RESOURCE_GROUP_DELETE_EVENT;
import static io.harness.accesscontrol.resources.resourcegroups.events.ResourceGroupUpdateEvent.RESOURCE_GROUP_UPDATE_EVENT;
import static io.harness.aggregator.ACLEventProcessingConstants.UPDATE_ACTION;
import static io.harness.annotations.dev.HarnessTeam.PL;

import static io.serializer.HObjectMapper.NG_DEFAULT_OBJECT_MAPPER;

import io.harness.accesscontrol.resources.resourcegroups.ResourceGroup;
import io.harness.accesscontrol.resources.resourcegroups.ResourceSelector;
import io.harness.accesscontrol.resources.resourcegroups.events.ResourceGroupUpdateEvent;
import io.harness.aggregator.consumers.AccessControlChangeConsumer;
import io.harness.aggregator.models.ResourceGroupChangeEventData;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.UnexpectedException;
import io.harness.outbox.OutboxEvent;
import io.harness.outbox.api.OutboxEventHandler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PL)
@Slf4j
public class ResourceGroupEventHandler implements OutboxEventHandler {
  private final ObjectMapper objectMapper;
  private final AccessControlChangeConsumer<ResourceGroupChangeEventData> resourceGroupChangeConsumer;
  private final boolean enableAclProcessingThroughOutbox;

  @Inject
  public ResourceGroupEventHandler(
      AccessControlChangeConsumer<ResourceGroupChangeEventData> resourceGroupChangeConsumer,
      @Named("enableAclProcessingThroughOutbox") boolean enableAclProcessingThroughOutbox) {
    this.objectMapper = NG_DEFAULT_OBJECT_MAPPER;
    this.resourceGroupChangeConsumer = resourceGroupChangeConsumer;
    this.enableAclProcessingThroughOutbox = enableAclProcessingThroughOutbox;
  }

  @Override
  public boolean handle(OutboxEvent outboxEvent) {
    try {
      switch (outboxEvent.getEventType()) {
        case RESOURCE_GROUP_CREATE_EVENT:
        case RESOURCE_GROUP_DELETE_EVENT:
          return true;
        case RESOURCE_GROUP_UPDATE_EVENT:
          return handleResourceGroupUpdateEvent(outboxEvent);
        default:
          return false;
      }
    } catch (Exception exception) {
      return false;
    }
  }

  private boolean handleResourceGroupUpdateEvent(OutboxEvent outboxEvent) {
    try {
      if (enableAclProcessingThroughOutbox) {
        ResourceGroupUpdateEvent resourceGroupUpdateEvent =
            objectMapper.readValue(outboxEvent.getEventData(), ResourceGroupUpdateEvent.class);
        Set<ResourceSelector> resourceSelectorsAdded = ResourceGroup.getDiffOfResourceSelectors(
            resourceGroupUpdateEvent.getNewResourceGroup(), resourceGroupUpdateEvent.getOldResourceGroup());
        Set<ResourceSelector> resourceSelectorsDeleted = ResourceGroup.getDiffOfResourceSelectors(
            resourceGroupUpdateEvent.getOldResourceGroup(), resourceGroupUpdateEvent.getNewResourceGroup());
        ResourceGroupChangeEventData resourceGroupChangeEventData =
            ResourceGroupChangeEventData.builder()
                .addedResourceSelectors(resourceSelectorsAdded)
                .removedResourceSelectors(resourceSelectorsDeleted)
                .updatedResourceGroup(resourceGroupUpdateEvent.getNewResourceGroup())
                .build();
        resourceGroupChangeConsumer.consumeEvent(UPDATE_ACTION, null, resourceGroupChangeEventData);
      }
      return true;
    } catch (Exception ex) {
      log.error("ResourceGroupEventHandler: Error occured during acl processing of resource group update", ex);
      throw new UnexpectedException(
          "ResourceGroupEventHandler: Error occured during acl processing of resource group update", ex);
    }
  }
}
