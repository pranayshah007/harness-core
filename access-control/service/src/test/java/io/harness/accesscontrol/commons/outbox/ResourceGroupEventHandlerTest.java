/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.commons.outbox;

import static io.harness.accesscontrol.scopes.harness.ScopeMapper.fromDTO;
import static io.harness.aggregator.ACLEventProcessingConstants.UPDATE_ACTION;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.JIMIT_GANDHI;

import static io.serializer.HObjectMapper.NG_DEFAULT_OBJECT_MAPPER;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.apache.commons.lang3.RandomStringUtils.randomNumeric;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.accesscontrol.permissions.persistence.repositories.InMemoryPermissionRepository;
import io.harness.accesscontrol.resources.resourcegroups.ResourceGroup;
import io.harness.accesscontrol.resources.resourcegroups.ResourceSelector;
import io.harness.accesscontrol.resources.resourcegroups.events.ResourceGroupUpdateEvent;
import io.harness.accesscontrol.scopes.ScopeDTO;
import io.harness.accesscontrol.scopes.core.Scope;
import io.harness.aggregator.consumers.ResourceGroupChangeConsumer;
import io.harness.aggregator.models.ResourceGroupChangeEventData;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.outbox.OutboxEvent;
import io.harness.rule.Owner;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(PL)
public class ResourceGroupEventHandlerTest extends CategoryTest {
  private ObjectMapper objectMapper;
  private ResourceGroupChangeConsumer resourceGroupChangeConsumer;
  private InMemoryPermissionRepository inMemoryPermissionRepository;
  @Inject @Named("batchSizeForACLCreation") private int batchSizeForACLCreation;
  private ResourceGroupEventHandler resourceGroupEventHandler;

  @Before
  public void setup() {
    objectMapper = NG_DEFAULT_OBJECT_MAPPER;
    resourceGroupChangeConsumer = mock(ResourceGroupChangeConsumer.class);
    resourceGroupEventHandler = spy(new ResourceGroupEventHandler(resourceGroupChangeConsumer, false));
  }

  @Test
  @Owner(developers = JIMIT_GANDHI)
  @Category(UnitTests.class)
  public void updateWithAclProcessingNotEnabled_DoesNotDoAclProcessing() throws JsonProcessingException {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String identifier = randomAlphabetic(10);
    ScopeDTO scopeDTO = getScopeDTO(accountIdentifier, orgIdentifier, null);
    Scope scope = fromDTO(scopeDTO);
    ResourceGroup oldResourceGroup = getResourceGroup(identifier, scope.toString());
    ResourceGroup newResourceGroup = getResourceGroup(identifier, scope.toString());
    ResourceGroupUpdateEvent resourceGroupUpdateEvent =
        new ResourceGroupUpdateEvent(oldResourceGroup, newResourceGroup, scope.toString());
    String eventData = objectMapper.writeValueAsString(resourceGroupUpdateEvent);
    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .id(randomAlphabetic(10))
                                  .blocked(false)
                                  .eventType("ResourceGroupUpdated")
                                  .eventData(eventData)
                                  .resourceScope(resourceGroupUpdateEvent.getResourceScope())
                                  .resource(resourceGroupUpdateEvent.getResource())
                                  .createdAt(Long.parseLong(randomNumeric(5)))
                                  .build();
    resourceGroupEventHandler.handle(outboxEvent);
    verify(resourceGroupChangeConsumer, never()).consumeEvent(eq(UPDATE_ACTION), any(), any());
  }

  @Test
  @Owner(developers = JIMIT_GANDHI)
  @Category(UnitTests.class)
  public void updateWithAclProcessingEnabled_DoesAclProcessing() throws JsonProcessingException {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String identifier = randomAlphabetic(10);
    ScopeDTO scopeDTO = getScopeDTO(accountIdentifier, orgIdentifier, null);
    Scope scope = fromDTO(scopeDTO);
    ResourceGroup oldResourceGroup = getResourceGroup(identifier, scope.toString());
    ResourceGroup newResourceGroup = getResourceGroup(identifier, scope.toString());
    ResourceGroupUpdateEvent resourceGroupUpdateEvent =
        new ResourceGroupUpdateEvent(oldResourceGroup, newResourceGroup, scope.toString());
    String eventData = objectMapper.writeValueAsString(resourceGroupUpdateEvent);
    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .id(randomAlphabetic(10))
                                  .blocked(false)
                                  .eventType("ResourceGroupUpdated")
                                  .eventData(eventData)
                                  .resourceScope(resourceGroupUpdateEvent.getResourceScope())
                                  .resource(resourceGroupUpdateEvent.getResource())
                                  .createdAt(Long.parseLong(randomNumeric(5)))
                                  .build();
    resourceGroupEventHandler = spy(new ResourceGroupEventHandler(resourceGroupChangeConsumer, true));
    Set<ResourceSelector> resourceSelectorsAdded = ResourceGroup.getDiffOfResourceSelectors(
        resourceGroupUpdateEvent.getNewResourceGroup(), resourceGroupUpdateEvent.getOldResourceGroup());
    Set<ResourceSelector> resourceSelectorsDeleted = ResourceGroup.getDiffOfResourceSelectors(
        resourceGroupUpdateEvent.getOldResourceGroup(), resourceGroupUpdateEvent.getNewResourceGroup());

    ResourceGroupChangeEventData resourceGroupChangeEventData = ResourceGroupChangeEventData.builder()
                                                                    .updatedResourceGroup(newResourceGroup)
                                                                    .addedResourceSelectors(resourceSelectorsAdded)
                                                                    .removedResourceSelectors(resourceSelectorsDeleted)
                                                                    .build();
    when(resourceGroupChangeConsumer.consumeUpdateEvent(null, resourceGroupChangeEventData)).thenReturn(true);
    resourceGroupEventHandler.handle(outboxEvent);
    verify(resourceGroupChangeConsumer, times(1)).consumeEvent(UPDATE_ACTION, null, resourceGroupChangeEventData);
  }

  private ResourceGroup getResourceGroup(String identifier, String scopeIdentifier) {
    Set<ResourceSelector> resourceSelectors =
        new HashSet<>(Arrays.asList(getResourceSelector(), getResourceSelector(), getResourceSelector()));
    return ResourceGroup.builder()
        .resourceSelectorsV2(resourceSelectors)
        .identifier(identifier)
        .scopeIdentifier(scopeIdentifier)
        .build();
  }

  private ResourceSelector getResourceSelector() {
    return ResourceSelector.builder().selector(randomAlphabetic(10)).conditional(false).condition(null).build();
  }

  private ScopeDTO getScopeDTO(String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    return ScopeDTO.builder()
        .accountIdentifier(accountIdentifier)
        .orgIdentifier(orgIdentifier)
        .projectIdentifier(projectIdentifier)
        .build();
  }
}
