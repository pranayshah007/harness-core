/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.resourcegroup.outbox;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.CREATE_ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.DELETE_ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ENTITY_TYPE;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.RESOURCE_GROUP;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.UPDATE_ACTION;
import static io.harness.ng.core.utils.NGYamlUtils.getYamlString;
import static io.harness.rule.OwnerRule.KARAN;

import static io.serializer.HObjectMapper.NG_DEFAULT_OBJECT_MAPPER;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertNull;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.apache.commons.lang3.RandomStringUtils.randomNumeric;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.ModuleType;
import io.harness.annotations.dev.OwnedBy;
import io.harness.audit.Action;
import io.harness.audit.ResourceTypeConstants;
import io.harness.audit.beans.AuditEntry;
import io.harness.audit.client.api.AuditClientService;
import io.harness.category.element.UnitTests;
import io.harness.eventsframework.api.Producer;
import io.harness.eventsframework.producer.Message;
import io.harness.outbox.OutboxEvent;
import io.harness.resourcegroup.framework.v1.events.ResourceGroupCreateEvent;
import io.harness.resourcegroup.framework.v1.events.ResourceGroupDeleteEvent;
import io.harness.resourcegroup.framework.v1.events.ResourceGroupUpdateEvent;
import io.harness.resourcegroup.framework.v1.service.impl.ResourceGroupEventHandler;
import io.harness.resourcegroup.v2.model.ResourceFilter;
import io.harness.resourcegroup.v2.model.ResourceSelector;
import io.harness.resourcegroup.v2.remote.dto.ResourceGroupDTO;
import io.harness.resourcegroup.v2.remote.dto.ResourceGroupRequest;
import io.harness.rule.Owner;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;

@OwnedBy(PL)
public class ResourceGroupEventHandlerTest extends CategoryTest {
  private ObjectMapper objectMapper;
  private Producer producer;
  private AuditClientService auditClientService;
  private ResourceGroupEventHandler resourceGroupEventHandler;

  @Before
  public void setup() {
    objectMapper = NG_DEFAULT_OBJECT_MAPPER;
    producer = mock(Producer.class);
    auditClientService = mock(AuditClientService.class);
    resourceGroupEventHandler = spy(new ResourceGroupEventHandler(producer, auditClientService));
  }

  private ResourceGroupDTO getResourceGroupDTO(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String identifier) {
    List<String> identifiers = new ArrayList<>();
    identifiers.add(randomAlphabetic(10));
    identifiers.add(randomAlphabetic(10));
    List<ResourceSelector> resourceSelectorDTOs = new ArrayList<>();
    resourceSelectorDTOs.add(
        ResourceSelector.builder().identifiers(identifiers).resourceType(randomAlphabetic(10)).build());
    resourceSelectorDTOs.add(ResourceSelector.builder().resourceType(randomAlphabetic(10)).build());
    resourceSelectorDTOs.add(ResourceSelector.builder().resourceType(randomAlphabetic(10)).build());
    ResourceFilter resourceFilter =
        ResourceFilter.builder().includeAllResources(false).resources(resourceSelectorDTOs).build();

    return ResourceGroupDTO.builder()
        .accountIdentifier(accountIdentifier)
        .orgIdentifier(orgIdentifier)
        .projectIdentifier(projectIdentifier)
        .identifier(identifier)
        .name(randomAlphabetic(10))
        .resourceFilter(resourceFilter)
        .build();
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testCreate() throws JsonProcessingException {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String identifier = randomAlphabetic(10);
    ResourceGroupDTO resourceGroupDTO = getResourceGroupDTO(accountIdentifier, orgIdentifier, null, identifier);
    ResourceGroupCreateEvent resourceGroupCreateEvent =
        new ResourceGroupCreateEvent(accountIdentifier, null, resourceGroupDTO);
    String eventData = objectMapper.writeValueAsString(resourceGroupCreateEvent);
    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .id(randomAlphabetic(10))
                                  .blocked(false)
                                  .eventType("ResourceGroupCreated")
                                  .eventData(eventData)
                                  .resourceScope(resourceGroupCreateEvent.getResourceScope())
                                  .resource(resourceGroupCreateEvent.getResource())
                                  .createdAt(Long.parseLong(randomNumeric(5)))
                                  .build();

    String newYaml = getYamlString(ResourceGroupRequest.builder().resourceGroup(resourceGroupDTO).build());

    final ArgumentCaptor<Message> messageArgumentCaptor = ArgumentCaptor.forClass(Message.class);
    final ArgumentCaptor<AuditEntry> auditEntryArgumentCaptor = ArgumentCaptor.forClass(AuditEntry.class);
    when(producer.send(any())).thenReturn("");
    when(auditClientService.publishAudit(any(), any())).thenReturn(true);

    resourceGroupEventHandler.handle(outboxEvent);

    verify(producer, times(1)).send(messageArgumentCaptor.capture());
    verify(auditClientService, times(1)).publishAudit(auditEntryArgumentCaptor.capture(), any());

    Message message = messageArgumentCaptor.getValue();
    assertNotNull(message.getMetadataMap());
    Map<String, String> metadataMap = message.getMetadataMap();
    assertEquals(accountIdentifier, metadataMap.get("accountId"));
    assertEquals(RESOURCE_GROUP, metadataMap.get(ENTITY_TYPE));
    assertEquals(CREATE_ACTION, metadataMap.get(ACTION));

    AuditEntry auditEntry = auditEntryArgumentCaptor.getValue();
    assertNotNull(auditEntry);
    assertEquals(outboxEvent.getId(), auditEntry.getInsertId());
    assertEquals(ResourceTypeConstants.RESOURCE_GROUP, auditEntry.getResource().getType());
    assertEquals(identifier, auditEntry.getResource().getIdentifier());
    assertEquals(accountIdentifier, auditEntry.getResourceScope().getAccountIdentifier());
    assertEquals(orgIdentifier, auditEntry.getResourceScope().getOrgIdentifier());
    assertNull(auditEntry.getResourceScope().getProjectIdentifier());
    assertEquals(Action.CREATE, auditEntry.getAction());
    assertEquals(ModuleType.CORE, auditEntry.getModule());
    assertEquals(outboxEvent.getCreatedAt().longValue(), auditEntry.getTimestamp());
    assertNull(auditEntry.getEnvironment());
    assertNull(auditEntry.getOldYaml());
    assertEquals(newYaml, auditEntry.getNewYaml());
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testUpdate() throws JsonProcessingException {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String identifier = randomAlphabetic(10);
    ResourceGroupDTO oldResourceGroupDTO = getResourceGroupDTO(accountIdentifier, orgIdentifier, null, identifier);
    ResourceGroupDTO newResourceGroupDTO = getResourceGroupDTO(accountIdentifier, orgIdentifier, null, identifier);
    ResourceGroupUpdateEvent resourceGroupUpdateEvent =
        new ResourceGroupUpdateEvent(accountIdentifier, null, null, newResourceGroupDTO, oldResourceGroupDTO);
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

    String oldYaml = getYamlString(ResourceGroupRequest.builder().resourceGroup(oldResourceGroupDTO).build());
    String newYaml = getYamlString(ResourceGroupRequest.builder().resourceGroup(newResourceGroupDTO).build());

    final ArgumentCaptor<Message> messageArgumentCaptor = ArgumentCaptor.forClass(Message.class);
    final ArgumentCaptor<AuditEntry> auditEntryArgumentCaptor = ArgumentCaptor.forClass(AuditEntry.class);
    when(producer.send(any())).thenReturn("");
    when(auditClientService.publishAudit(any(), any())).thenReturn(true);

    resourceGroupEventHandler.handle(outboxEvent);

    verify(producer, times(1)).send(messageArgumentCaptor.capture());
    verify(auditClientService, times(1)).publishAudit(auditEntryArgumentCaptor.capture(), any());

    Message message = messageArgumentCaptor.getValue();
    assertNotNull(message.getMetadataMap());
    Map<String, String> metadataMap = message.getMetadataMap();
    assertEquals(accountIdentifier, metadataMap.get("accountId"));
    assertEquals(RESOURCE_GROUP, metadataMap.get(ENTITY_TYPE));
    assertEquals(UPDATE_ACTION, metadataMap.get(ACTION));

    AuditEntry auditEntry = auditEntryArgumentCaptor.getValue();
    assertNotNull(auditEntry);
    assertEquals(outboxEvent.getId(), auditEntry.getInsertId());
    assertEquals(ResourceTypeConstants.RESOURCE_GROUP, auditEntry.getResource().getType());
    assertEquals(identifier, auditEntry.getResource().getIdentifier());
    assertEquals(accountIdentifier, auditEntry.getResourceScope().getAccountIdentifier());
    assertEquals(orgIdentifier, auditEntry.getResourceScope().getOrgIdentifier());
    assertNull(auditEntry.getResourceScope().getProjectIdentifier());
    assertEquals(Action.UPDATE, auditEntry.getAction());
    assertEquals(ModuleType.CORE, auditEntry.getModule());
    assertEquals(outboxEvent.getCreatedAt().longValue(), auditEntry.getTimestamp());
    assertNull(auditEntry.getEnvironment());
    assertEquals(oldYaml, auditEntry.getOldYaml());
    assertEquals(newYaml, auditEntry.getNewYaml());
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testDelete() throws JsonProcessingException {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String identifier = randomAlphabetic(10);
    ResourceGroupDTO resourceGroupDTO = getResourceGroupDTO(accountIdentifier, orgIdentifier, null, identifier);
    ResourceGroupDeleteEvent resourceGroupDeleteEvent =
        new ResourceGroupDeleteEvent(accountIdentifier, null, resourceGroupDTO);
    String eventData = objectMapper.writeValueAsString(resourceGroupDeleteEvent);
    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .id(randomAlphabetic(10))
                                  .blocked(false)
                                  .eventType("ResourceGroupDeleted")
                                  .eventData(eventData)
                                  .resourceScope(resourceGroupDeleteEvent.getResourceScope())
                                  .resource(resourceGroupDeleteEvent.getResource())
                                  .createdAt(Long.parseLong(randomNumeric(5)))
                                  .build();

    String oldYaml = getYamlString(ResourceGroupRequest.builder().resourceGroup(resourceGroupDTO).build());

    final ArgumentCaptor<Message> messageArgumentCaptor = ArgumentCaptor.forClass(Message.class);
    final ArgumentCaptor<AuditEntry> auditEntryArgumentCaptor = ArgumentCaptor.forClass(AuditEntry.class);
    when(producer.send(any())).thenReturn("");
    when(auditClientService.publishAudit(any(), any())).thenReturn(true);

    resourceGroupEventHandler.handle(outboxEvent);

    verify(producer, times(1)).send(messageArgumentCaptor.capture());
    verify(auditClientService, times(1)).publishAudit(auditEntryArgumentCaptor.capture(), any());

    Message message = messageArgumentCaptor.getValue();
    assertNotNull(message.getMetadataMap());
    Map<String, String> metadataMap = message.getMetadataMap();
    assertEquals(accountIdentifier, metadataMap.get("accountId"));
    assertEquals(RESOURCE_GROUP, metadataMap.get(ENTITY_TYPE));
    assertEquals(DELETE_ACTION, metadataMap.get(ACTION));

    AuditEntry auditEntry = auditEntryArgumentCaptor.getValue();
    assertNotNull(auditEntry);
    assertEquals(outboxEvent.getId(), auditEntry.getInsertId());
    assertEquals(ResourceTypeConstants.RESOURCE_GROUP, auditEntry.getResource().getType());
    assertEquals(identifier, auditEntry.getResource().getIdentifier());
    assertEquals(accountIdentifier, auditEntry.getResourceScope().getAccountIdentifier());
    assertEquals(orgIdentifier, auditEntry.getResourceScope().getOrgIdentifier());
    assertNull(auditEntry.getResourceScope().getProjectIdentifier());
    assertEquals(Action.DELETE, auditEntry.getAction());
    assertEquals(ModuleType.CORE, auditEntry.getModule());
    assertEquals(outboxEvent.getCreatedAt().longValue(), auditEntry.getTimestamp());
    assertNull(auditEntry.getEnvironment());
    assertNull(auditEntry.getNewYaml());
    assertEquals(oldYaml, auditEntry.getOldYaml());
  }
}
