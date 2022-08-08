/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.resourcegroup.reconciliation;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.CREATE_ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.DELETE_ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ENTITY_TYPE;
import static io.harness.resourcegroup.framework.v1.beans.ResourceGroupConstants.ACCOUNT;
import static io.harness.resourcegroup.framework.v1.beans.ResourceGroupConstants.ORGANIZATION;
import static io.harness.resourcegroup.framework.v1.beans.ResourceGroupConstants.PROJECT;

import static java.util.Collections.singleton;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.eventsframework.api.Consumer;
import io.harness.eventsframework.api.EventsFrameworkDownException;
import io.harness.eventsframework.consumer.Message;
import io.harness.ng.beans.PageRequest;
import io.harness.resourcegroup.framework.v1.service.Resource;
import io.harness.resourcegroup.framework.v1.service.ResourceInfo;
import io.harness.resourcegroup.framework.v2.service.ResourceGroupService;
import io.harness.resourcegroup.v1.remote.dto.ManagedFilter;
import io.harness.resourcegroup.v1.remote.dto.ResourceGroupFilterDTO;
import io.harness.resourcegroup.v1.remote.dto.ResourceSelectorFilter;
import io.harness.resourcegroup.v2.model.ResourceFilter;
import io.harness.resourcegroup.v2.model.ResourceSelector;
import io.harness.resourcegroup.v2.remote.dto.ResourceGroupDTO;
import io.harness.resourcegroup.v2.remote.dto.ResourceGroupResponse;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.dto.ServicePrincipal;

import com.google.common.annotations.VisibleForTesting;
import java.time.Duration;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;

@OwnedBy(PL)
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class ResourceGroupSyncConciliationJob implements Runnable {
  private static final int WAIT_TIME_IN_SECONDS = 30;

  Consumer redisConsumer;
  Map<String, Resource> resourceMap;
  ResourceGroupService resourceGroupService;
  String serviceId;

  public ResourceGroupSyncConciliationJob(Consumer redisConsumer, Map<String, Resource> resourceMap,
      ResourceGroupService resourceGroupService, String serviceId) {
    this.redisConsumer = redisConsumer;
    this.resourceGroupService = resourceGroupService;
    this.serviceId = serviceId;
    this.resourceMap =
        resourceMap.values()
            .stream()
            .filter(resource -> resource.getEventFrameworkEntityType().isPresent())
            .collect(Collectors.toMap(resource -> resource.getEventFrameworkEntityType().get(), Function.identity()));
  }

  @Override
  public void run() {
    log.info("ResourceGroupSyncConciliationJob started");
    SecurityContextBuilder.setContext(new ServicePrincipal(serviceId));
    try {
      while (!Thread.currentThread().isInterrupted()) {
        readEventsFrameworkMessages();
      }
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
    } catch (Exception ex) {
      log.error("ResourceGroupSyncConciliationJob unexpectedly stopped", ex);
    } finally {
      SecurityContextBuilder.unsetCompleteContext();
    }
  }

  @VisibleForTesting
  protected void readEventsFrameworkMessages() throws InterruptedException {
    try {
      pollAndProcessMessages();
    } catch (EventsFrameworkDownException e) {
      log.error("Events framework is down. Retrying", e);
      TimeUnit.SECONDS.sleep(WAIT_TIME_IN_SECONDS);
    }
  }

  private void pollAndProcessMessages() {
    List<Message> messages;
    String messageId;
    messages = redisConsumer.read(Duration.ofSeconds(WAIT_TIME_IN_SECONDS));
    for (Message message : messages) {
      messageId = message.getId();
      if (handleMessage(message)) {
        redisConsumer.acknowledge(messageId);
      }
    }
  }

  private boolean handleMessage(Message message) {
    try {
      return processMessage(message);
    } catch (Exception ex) {
      log.error(String.format("Error occurred in processing message with id %s", message.getId()), ex);
      return false;
    }
  }

  private boolean processMessage(Message message) {
    if (!message.hasMessage()) {
      return true;
    }
    Map<String, String> metadataMap = message.getMessage().getMetadataMap();
    if (metadataMap == null || !metadataMap.containsKey(ACTION) || !metadataMap.containsKey(ENTITY_TYPE)) {
      return true;
    }

    String entityType = metadataMap.get(ENTITY_TYPE);
    if (!resourceMap.containsKey(entityType)) {
      return true;
    }
    ResourceInfo resourceInfo = resourceMap.get(entityType).getResourceInfoFromEvent(message);
    if (Objects.isNull(resourceInfo)) {
      return true;
    }
    String action = metadataMap.get(ACTION);
    return processMessage(resourceInfo, action);
  }

  private boolean processMessage(ResourceInfo resourceInfo, String action) {
    switch (action) {
      case DELETE_ACTION:
        return handleDeleteEvent(resourceInfo);
      case CREATE_ACTION:
        return handleCreateEvent(resourceInfo);
      default:
        return true;
    }
  }

  private boolean handleCreateEvent(ResourceInfo resourceInfo) {
    return true;
  }

  private boolean handleDeleteEvent(ResourceInfo resource) {
    if (isScope(resource.getResourceType())) {
      Scope scope = Scope.builder()
                        .accountIdentifier(resource.getAccountIdentifier())
                        .orgIdentifier(resource.getOrgIdentifier())
                        .projectIdentifier(resource.getProjectIdentifier())
                        .build();
      try {
        resourceGroupService.deleteByScope(scope);
      } catch (Exception e) {
        log.error("Could not delete resource groups in scope {}. Failed due to exception", scope, e);
        return false;
      }
      return true;
    }

    int counter = 0;
    int maxLimit = 50;
    while (counter < maxLimit) {
      Page<ResourceGroupResponse> resourceGroupsPage = resourceGroupService.list(
          getResourceGroupsWithResourceFilter(resource), PageRequest.builder().pageIndex(counter).pageSize(20).build());
      if (!resourceGroupsPage.hasContent()) {
        break;
      }
      for (ResourceGroupResponse resourceGroup : resourceGroupsPage.getContent()) {
        deleteResourceFromResourceGroup(resource, resourceGroup.getResourceGroup());
        resourceGroupService.update(resourceGroup.getResourceGroup(), resourceGroup.isHarnessManaged());
      }
      counter++;
    }

    return true;
  }

  private boolean isScope(String resourceType) {
    return resourceType.equals(ACCOUNT) || resourceType.equals(ORGANIZATION) || resourceType.equals(PROJECT);
  }

  private ResourceGroupFilterDTO getResourceGroupsWithResourceFilter(ResourceInfo resourceInfo) {
    return ResourceGroupFilterDTO.builder()
        .managedFilter(ManagedFilter.ONLY_CUSTOM)
        .accountIdentifier(resourceInfo.getAccountIdentifier())
        .orgIdentifier(resourceInfo.getOrgIdentifier())
        .projectIdentifier(resourceInfo.getProjectIdentifier())
        .resourceSelectorFilterList(singleton(ResourceSelectorFilter.builder()
                                                  .resourceType(resourceInfo.getResourceType())
                                                  .resourceIdentifier(resourceInfo.getResourceIdentifier())
                                                  .build()))
        .build();
  }

  private void deleteResourceFromResourceGroup(ResourceInfo resource, ResourceGroupDTO resourceGroup) {
    List<ResourceSelector> resourceSelectors =
        resourceGroup.getResourceFilter()
            .getResources()
            .stream()
            .filter(Objects::nonNull)
            .filter(rs -> rs.getResourceType().equals(resource.getResourceType()))
            .collect(Collectors.toList());
    for (Iterator<ResourceSelector> iterator = resourceSelectors.iterator(); iterator.hasNext();) {
      ResourceSelector resourceSelector = iterator.next();
      resourceSelector.getIdentifiers().remove(resource.getResourceIdentifier());
      if (resourceSelector.getIdentifiers().isEmpty()) {
        iterator.remove();
      }
    }
    resourceGroup.setResourceFilter(ResourceFilter.builder().resources(resourceSelectors).build());
  }
}
