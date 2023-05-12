/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.resourcegroup.reconciliation;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.KARAN;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.category.element.UnitTests;
import io.harness.eventsframework.EventsFrameworkMetadataConstants;
import io.harness.eventsframework.api.Consumer;
import io.harness.eventsframework.consumer.Message;
import io.harness.eventsframework.entity_crud.EntityChangeDTO;
import io.harness.eventsframework.entity_crud.organization.OrganizationEntityChangeDTO;
import io.harness.eventsframework.entity_crud.resourcegroup.ResourceGroupEntityChangeDTO;
import io.harness.ng.beans.PageRequest;
import io.harness.outbox.api.OutboxService;
import io.harness.resourcegroup.ResourceGroupTestBase;
import io.harness.resourcegroup.framework.v1.service.Resource;
import io.harness.resourcegroup.framework.v2.remote.mapper.ResourceGroupMapper;
import io.harness.resourcegroup.framework.v2.repositories.spring.ResourceGroupV2Repository;
import io.harness.resourcegroup.framework.v2.service.ResourceGroupService;
import io.harness.resourcegroup.framework.v2.service.impl.ResourceGroupServiceImpl;
import io.harness.resourcegroup.framework.v2.service.impl.ResourceGroupValidatorImpl;
import io.harness.resourcegroup.v1.remote.dto.ManagedFilter;
import io.harness.resourcegroup.v1.remote.dto.ResourceGroupFilterDTO;
import io.harness.resourcegroup.v1.remote.dto.ResourceSelectorFilter;
import io.harness.resourcegroup.v2.model.ResourceFilter;
import io.harness.resourcegroup.v2.model.ResourceGroup;
import io.harness.resourcegroup.v2.model.ResourceSelector;
import io.harness.resourcegroup.v2.remote.dto.ResourceGroupDTO;
import io.harness.resourcegroup.v2.remote.dto.ResourceGroupResponse;
import io.harness.rule.Owner;
import io.harness.utils.PageTestUtils;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.protobuf.ByteString;
import com.google.protobuf.StringValue;
import io.serializer.HObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.springframework.transaction.support.TransactionTemplate;

@OwnedBy(PL)
public class ResourceGroupSyncConciliationJobTest extends ResourceGroupTestBase {
  private Consumer redisConsumer;
  @Inject ResourceGroupV2Repository resourceGroupV2Repository;
  @Inject ResourceGroupValidatorImpl resourceGroupValidatorImpl;
  @Inject OutboxService outboxService;
  @Inject TransactionTemplate transactionTemplate;
  AccessControlClient accessControlClient;
  @Inject private Map<String, Resource> resourceMap;
  private ResourceGroupService resourceGroupService;
  private ResourceGroupService resourceGroupServiceMock;
  private String serviceId;
  private ResourceGroupSyncConciliationJob resourceGroupSyncConciliationJob;
  private ResourceGroupSyncConciliationJob resourceGroupSyncConciliationJobMockService;

  @Before
  public void setup() {
    redisConsumer = mock(Consumer.class);
    resourceGroupServiceMock = mock(ResourceGroupService.class);
    accessControlClient = mock(AccessControlClient.class);
    resourceGroupService = new ResourceGroupServiceImpl(
        resourceGroupV2Repository, resourceGroupValidatorImpl, outboxService, transactionTemplate, accessControlClient);
    serviceId = "ResourceGroupService";
    resourceGroupSyncConciliationJob =
        new ResourceGroupSyncConciliationJob(redisConsumer, resourceMap, resourceGroupService, serviceId);
    resourceGroupSyncConciliationJobMockService =
        new ResourceGroupSyncConciliationJob(redisConsumer, resourceMap, resourceGroupServiceMock, serviceId);
    when(accessControlClient.hasAccess(any(), any(), any())).thenReturn(true);
  }

  private Map<String, String> getMetadataMap(String accountIdentifier, String entityType, String action) {
    Map<String, String> metadataMap;
    if (isNotBlank(accountIdentifier)) {
      metadataMap = ImmutableMap.of("accountId", accountIdentifier, EventsFrameworkMetadataConstants.ENTITY_TYPE,
          entityType, EventsFrameworkMetadataConstants.ACTION, action);

    } else {
      metadataMap = ImmutableMap.of(
          EventsFrameworkMetadataConstants.ENTITY_TYPE, entityType, EventsFrameworkMetadataConstants.ACTION, action);
    }
    return metadataMap;
  }

  private ByteString getOrganizationPayload(String accountIdentifier, String identifier) {
    return OrganizationEntityChangeDTO.newBuilder()
        .setIdentifier(identifier)
        .setAccountIdentifier(accountIdentifier)
        .build()
        .toByteString();
  }

  private Message getOrganizationConsumerMessage(
      String consumerMessageId, String accountIdentifier, String identifier, String action) {
    Map<String, String> metadataMap =
        getMetadataMap(accountIdentifier, EventsFrameworkMetadataConstants.ORGANIZATION_ENTITY, action);
    io.harness.eventsframework.producer.Message producerMessage =
        io.harness.eventsframework.producer.Message.newBuilder()
            .putAllMetadata(metadataMap)
            .setData(getOrganizationPayload(accountIdentifier, identifier))
            .build();
    return Message.newBuilder().setId(consumerMessageId).setMessage(producerMessage).build();
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testScopeCreate() throws InterruptedException {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String consumerMessageId = randomAlphabetic(10);
    Message consumerMessage = getOrganizationConsumerMessage(
        consumerMessageId, accountIdentifier, orgIdentifier, EventsFrameworkMetadataConstants.CREATE_ACTION);

    when(redisConsumer.read(any())).thenReturn(Lists.newArrayList(consumerMessage));
    doNothing().when(redisConsumer).acknowledge(consumerMessageId);

    resourceGroupSyncConciliationJobMockService.readEventsFrameworkMessages();

    verify(redisConsumer, times(1)).read(any());
    verify(redisConsumer, times(1)).acknowledge(any());
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testScopeUpdate() throws InterruptedException {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String consumerMessageId = randomAlphabetic(10);
    Message consumerMessage = getOrganizationConsumerMessage(
        consumerMessageId, accountIdentifier, orgIdentifier, EventsFrameworkMetadataConstants.CREATE_ACTION);

    when(redisConsumer.read(any())).thenReturn(Lists.newArrayList(consumerMessage));
    doNothing().when(redisConsumer).acknowledge(consumerMessageId);

    resourceGroupSyncConciliationJobMockService.readEventsFrameworkMessages();

    verify(redisConsumer, times(1)).read(any());
    verify(redisConsumer, times(1)).acknowledge(any());
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testScopeDelete() throws InterruptedException {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String consumerMessageId = randomAlphabetic(10);
    Message consumerMessage = getOrganizationConsumerMessage(
        consumerMessageId, accountIdentifier, orgIdentifier, EventsFrameworkMetadataConstants.DELETE_ACTION);

    when(redisConsumer.read(any())).thenReturn(Lists.newArrayList(consumerMessage));
    doNothing().when(resourceGroupServiceMock).deleteByScope(Scope.of(accountIdentifier, orgIdentifier, null));
    doNothing().when(redisConsumer).acknowledge(consumerMessageId);

    resourceGroupSyncConciliationJobMockService.readEventsFrameworkMessages();

    verify(redisConsumer, times(1)).read(any());
    verify(resourceGroupServiceMock, times(1)).deleteByScope(any());
    verify(redisConsumer, times(1)).acknowledge(any());
  }

  private ByteString getResourcePayload(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String identifier) {
    EntityChangeDTO.Builder secretEntityChangeDTOBuilder = EntityChangeDTO.newBuilder()
                                                               .setAccountIdentifier(StringValue.of(accountIdentifier))
                                                               .setIdentifier(StringValue.of(identifier));
    if (isNotBlank(orgIdentifier)) {
      secretEntityChangeDTOBuilder.setOrgIdentifier(StringValue.of(orgIdentifier));
    }
    if (isNotBlank(projectIdentifier)) {
      secretEntityChangeDTOBuilder.setProjectIdentifier(StringValue.of(projectIdentifier));
    }
    return secretEntityChangeDTOBuilder.build().toByteString();
  }

  private Message getResourceConsumerMessage(String consumerMessageId, String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String identifier, String entityType, String action) {
    Map<String, String> metadataMap = getMetadataMap(accountIdentifier, entityType, action);
    io.harness.eventsframework.producer.Message producerMessage =
        io.harness.eventsframework.producer.Message.newBuilder()
            .putAllMetadata(metadataMap)
            .setData(getResourcePayload(accountIdentifier, orgIdentifier, projectIdentifier, identifier))
            .build();
    return Message.newBuilder().setId(consumerMessageId).setMessage(producerMessage).build();
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testResourceCreate() throws InterruptedException {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String identifier = randomAlphabetic(10);
    String consumerMessageId = randomAlphabetic(10);
    Message consumerMessage = getResourceConsumerMessage(consumerMessageId, accountIdentifier, orgIdentifier, null,
        identifier, EventsFrameworkMetadataConstants.SECRET_ENTITY, EventsFrameworkMetadataConstants.CREATE_ACTION);

    when(redisConsumer.read(any())).thenReturn(Lists.newArrayList(consumerMessage));
    doNothing().when(redisConsumer).acknowledge(consumerMessageId);

    resourceGroupSyncConciliationJobMockService.readEventsFrameworkMessages();

    verify(redisConsumer, times(1)).read(any());
    verify(redisConsumer, times(1)).acknowledge(any());
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testResourceDeleteNoResourceGroup() throws InterruptedException {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String identifier = randomAlphabetic(10);
    String consumerMessageId = randomAlphabetic(10);
    Message consumerMessage = getResourceConsumerMessage(consumerMessageId, accountIdentifier, orgIdentifier, null,
        identifier, EventsFrameworkMetadataConstants.SECRET_ENTITY, EventsFrameworkMetadataConstants.DELETE_ACTION);

    when(redisConsumer.read(any())).thenReturn(Lists.newArrayList(consumerMessage));
    doNothing().when(redisConsumer).acknowledge(consumerMessageId);

    resourceGroupSyncConciliationJob.readEventsFrameworkMessages();

    verify(redisConsumer, times(1)).read(any());
    verify(redisConsumer, times(1)).acknowledge(any());
  }

  private List<ResourceGroupResponse> getResourceGroupsWithResource(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String resourceType, String resourceIdentifier, int count) {
    List<ResourceGroup> resourceGroups = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      ResourceGroup resourceGroup = ResourceGroup.builder()
                                        .accountIdentifier(accountIdentifier)
                                        .orgIdentifier(orgIdentifier)
                                        .projectIdentifier(projectIdentifier)
                                        .identifier(randomAlphabetic(10))
                                        .allowedScopeLevels(emptySet())
                                        .build();
      resourceGroup.setResourceFilter(
          ResourceFilter.builder()
              .resources(Lists.newArrayList(ResourceSelector.builder()
                                                .resourceType(resourceType)
                                                .identifiers(Lists.newArrayList(resourceIdentifier))
                                                .build()))
              .build());

      resourceGroups.add(resourceGroup);
    }
    return resourceGroups.stream().map(ResourceGroupMapper::toResponseWrapper).collect(Collectors.toList());
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testResourceDelete() throws InterruptedException {
    String accountIdentifier = randomAlphabetic(10);
    String identifier = randomAlphabetic(10);
    String consumerMessageId = randomAlphabetic(10);
    String secretResourceType = "SECRET";
    Message consumerMessage = getResourceConsumerMessage(consumerMessageId, accountIdentifier, null, null, identifier,
        EventsFrameworkMetadataConstants.SECRET_ENTITY, EventsFrameworkMetadataConstants.DELETE_ACTION);

    when(redisConsumer.read(any())).thenReturn(Lists.newArrayList(consumerMessage));
    ResourceGroupFilterDTO resourceGroupFilterDTO =
        getResourceGroupsWithResourceFilter(accountIdentifier, null, null, secretResourceType, identifier);
    PageRequest pageRequest = PageRequest.builder().pageIndex(0).pageSize(20).build();
    PageRequest secondPageRequest = PageRequest.builder().pageIndex(1).pageSize(20).build();
    List<io.harness.resourcegroup.v2.remote.dto.ResourceGroupResponse> resourceGroups =
        getResourceGroupsWithResource(accountIdentifier, null, null, secretResourceType, identifier, 5)
            .stream()
            .collect(Collectors.toList());
    when(resourceGroupServiceMock.list(resourceGroupFilterDTO, pageRequest))
        .thenReturn(PageTestUtils.getPage(resourceGroups, resourceGroups.size()));
    when(resourceGroupServiceMock.list(resourceGroupFilterDTO, secondPageRequest))
        .thenReturn(PageTestUtils.getPage(emptyList(), 0));
    resourceGroups.forEach(resourceGroupResponse -> {
      ResourceGroupDTO resourceGroupDTO =
          (ResourceGroupDTO) HObjectMapper.clone(resourceGroupResponse.getResourceGroup());
      resourceGroupDTO.setResourceFilter(
          ResourceFilter.builder()
              .resources(singletonList(
                  ResourceSelector.builder().resourceType(secretResourceType).identifiers(emptyList()).build()))
              .build());
      resourceGroupDTO.setAllowedScopeLevels(emptySet());
      when(resourceGroupServiceMock.update(resourceGroupDTO, false)).thenReturn(Optional.empty());
    });
    doNothing().when(redisConsumer).acknowledge(consumerMessageId);

    resourceGroupSyncConciliationJobMockService.readEventsFrameworkMessages();

    verify(redisConsumer, times(1)).read(any());
    verify(resourceGroupServiceMock, times(2)).list(any(), any());
    resourceGroups.forEach(resourceGroupResponse -> {
      ResourceGroupDTO resourceGroupDTO =
          (ResourceGroupDTO) HObjectMapper.clone(resourceGroupResponse.getResourceGroup());
      resourceGroupDTO.setResourceFilter(ResourceFilter.builder().resources(new ArrayList<>()).build());
      resourceGroupDTO.setAllowedScopeLevels(emptySet());
      verify(resourceGroupServiceMock, times(1)).update(resourceGroupDTO, false);
    });
    verify(redisConsumer, times(1)).acknowledge(any());
  }

  private ResourceGroupFilterDTO getResourceGroupsWithResourceFilter(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String resourceType, String resourceIdentifier) {
    return ResourceGroupFilterDTO.builder()
        .managedFilter(ManagedFilter.ONLY_CUSTOM)
        .accountIdentifier(accountIdentifier)
        .orgIdentifier(orgIdentifier)
        .projectIdentifier(projectIdentifier)
        .resourceSelectorFilterList(singleton(
            ResourceSelectorFilter.builder().resourceType(resourceType).resourceIdentifier(resourceIdentifier).build()))
        .build();
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testResourceMappingMissing() throws InterruptedException {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String identifier = randomAlphabetic(10);
    String entityType = randomAlphabetic(10);
    String consumerMessageId = randomAlphabetic(10);
    Message consumerMessage = getResourceConsumerMessage(consumerMessageId, accountIdentifier, orgIdentifier, null,
        identifier, entityType, EventsFrameworkMetadataConstants.DELETE_ACTION);

    when(redisConsumer.read(any())).thenReturn(Lists.newArrayList(consumerMessage));
    doNothing().when(redisConsumer).acknowledge(consumerMessageId);

    resourceGroupSyncConciliationJob.readEventsFrameworkMessages();

    verify(redisConsumer, times(1)).read(any());
    verify(redisConsumer, times(1)).acknowledge(any());
  }

  private ByteString getResourceGroupPayload(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String identifier) {
    ResourceGroupEntityChangeDTO.Builder resourceGroupEntityChangeDTOBuilder =
        ResourceGroupEntityChangeDTO.newBuilder().setIdentifier(identifier);
    if (isNotBlank(accountIdentifier)) {
      resourceGroupEntityChangeDTOBuilder.setAccountIdentifier(accountIdentifier);
    }
    if (isNotBlank(orgIdentifier)) {
      resourceGroupEntityChangeDTOBuilder.setOrgIdentifier(orgIdentifier);
    }
    if (isNotBlank(projectIdentifier)) {
      resourceGroupEntityChangeDTOBuilder.setProjectIdentifier(projectIdentifier);
    }
    return resourceGroupEntityChangeDTOBuilder.build().toByteString();
  }

  private Message getResourceGroupConsumerMessage(String consumerMessageId, String accountIdentifier,
      String orgIdentifier, String projectIdentifier, String identifier, String action) {
    Map<String, String> metadataMap =
        getMetadataMap(accountIdentifier, EventsFrameworkMetadataConstants.RESOURCE_GROUP, action);
    io.harness.eventsframework.producer.Message producerMessage =
        io.harness.eventsframework.producer.Message.newBuilder()
            .putAllMetadata(metadataMap)
            .setData(getResourceGroupPayload(accountIdentifier, orgIdentifier, projectIdentifier, identifier))
            .build();
    return Message.newBuilder().setId(consumerMessageId).setMessage(producerMessage).build();
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testResourceInfoNull() throws InterruptedException {
    String identifier = randomAlphabetic(10);
    String consumerMessageId = randomAlphabetic(10);
    Message consumerMessage = getResourceGroupConsumerMessage(
        consumerMessageId, null, null, null, identifier, EventsFrameworkMetadataConstants.DELETE_ACTION);

    when(redisConsumer.read(any())).thenReturn(Lists.newArrayList(consumerMessage));
    doNothing().when(redisConsumer).acknowledge(consumerMessageId);

    resourceGroupSyncConciliationJob.readEventsFrameworkMessages();

    verify(redisConsumer, times(1)).read(any());
    verify(redisConsumer, times(1)).acknowledge(any());
  }
}
