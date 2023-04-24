/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.outbox;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.CREATE_ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.DELETE_ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ENTITY_TYPE;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.UPDATE_ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.USER_GROUP;
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
import io.harness.beans.Scope;
import io.harness.category.element.UnitTests;
import io.harness.eventsframework.api.Producer;
import io.harness.eventsframework.producer.Message;
import io.harness.ng.core.api.DefaultUserGroupService;
import io.harness.ng.core.dto.UserGroupDTO;
import io.harness.ng.core.dto.UserGroupRequest;
import io.harness.ng.core.events.UserGroupCreateEvent;
import io.harness.ng.core.events.UserGroupDeleteEvent;
import io.harness.ng.core.events.UserGroupUpdateEvent;
import io.harness.ng.core.notification.EmailConfigDTO;
import io.harness.ng.core.notification.MicrosoftTeamsConfigDTO;
import io.harness.ng.core.notification.NotificationSettingConfigDTO;
import io.harness.ng.core.notification.PagerDutyConfigDTO;
import io.harness.ng.core.notification.SlackConfigDTO;
import io.harness.outbox.OutboxEvent;
import io.harness.rule.Owner;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;

@OwnedBy(PL)
public class UserGroupEventHandlerTest extends CategoryTest {
  private ObjectMapper objectMapper;
  private ObjectMapper yamlMapper;
  private Producer producer;
  private AuditClientService auditClientService;
  private UserGroupEventHandler userGroupEventHandler;
  private DefaultUserGroupService defaultUserGroupService;

  @Before
  public void setup() {
    objectMapper = NG_DEFAULT_OBJECT_MAPPER;
    yamlMapper = new YAMLMapper();
    producer = mock(Producer.class);
    auditClientService = mock(AuditClientService.class);
    defaultUserGroupService = mock(DefaultUserGroupService.class);
    userGroupEventHandler = spy(new UserGroupEventHandler(producer, auditClientService, defaultUserGroupService));
  }

  private UserGroupDTO getUserGroupDTO(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String identifier) {
    List<String> users = new ArrayList<>();
    users.add(randomAlphabetic(10));
    users.add(randomAlphabetic(10));

    List<NotificationSettingConfigDTO> notificationSettingConfigDTOs = new ArrayList<>();
    notificationSettingConfigDTOs.add(new SlackConfigDTO(randomAlphabetic(10)));
    notificationSettingConfigDTOs.add(new PagerDutyConfigDTO(randomAlphabetic(10)));
    notificationSettingConfigDTOs.add(new MicrosoftTeamsConfigDTO(randomAlphabetic(10)));
    notificationSettingConfigDTOs.add(new EmailConfigDTO(randomAlphabetic(10), true));

    return UserGroupDTO.builder()
        .accountIdentifier(accountIdentifier)
        .orgIdentifier(orgIdentifier)
        .projectIdentifier(projectIdentifier)
        .identifier(identifier)
        .name(randomAlphabetic(10))
        .users(users)
        .notificationConfigs(notificationSettingConfigDTOs)
        .build();
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testCreate() throws IOException {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String identifier = randomAlphabetic(10);
    UserGroupDTO userGroupDTO = getUserGroupDTO(accountIdentifier, orgIdentifier, null, identifier);
    UserGroupCreateEvent userGroupCreateEvent = new UserGroupCreateEvent(accountIdentifier, userGroupDTO);
    String eventData = objectMapper.writeValueAsString(userGroupCreateEvent);
    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .id(randomAlphabetic(10))
                                  .blocked(false)
                                  .eventType("UserGroupCreated")
                                  .eventData(eventData)
                                  .resourceScope(userGroupCreateEvent.getResourceScope())
                                  .resource(userGroupCreateEvent.getResource())
                                  .createdAt(Long.parseLong(randomNumeric(5)))
                                  .build();

    final ArgumentCaptor<Message> messageArgumentCaptor = ArgumentCaptor.forClass(Message.class);
    final ArgumentCaptor<AuditEntry> auditEntryArgumentCaptor = ArgumentCaptor.forClass(AuditEntry.class);
    when(producer.send(any())).thenReturn("");
    when(auditClientService.publishAudit(any(), any())).thenReturn(true);
    Scope scope = Scope.of(accountIdentifier, orgIdentifier, null);
    when(defaultUserGroupService.isDefaultUserGroup(scope, identifier)).thenReturn(false);

    userGroupEventHandler.handle(outboxEvent);

    verify(producer, times(1)).send(messageArgumentCaptor.capture());
    verify(auditClientService, times(1)).publishAudit(auditEntryArgumentCaptor.capture(), any());

    Message message = messageArgumentCaptor.getValue();
    assertNotNull(message.getMetadataMap());
    Map<String, String> metadataMap = message.getMetadataMap();
    assertEquals(accountIdentifier, metadataMap.get("accountId"));
    assertEquals(USER_GROUP, metadataMap.get(ENTITY_TYPE));
    assertEquals(CREATE_ACTION, metadataMap.get(ACTION));

    AuditEntry auditEntry = auditEntryArgumentCaptor.getValue();
    assertNotNull(auditEntry);
    assertEquals(outboxEvent.getId(), auditEntry.getInsertId());
    assertEquals(ResourceTypeConstants.USER_GROUP, auditEntry.getResource().getType());
    assertEquals(identifier, auditEntry.getResource().getIdentifier());
    assertEquals(accountIdentifier, auditEntry.getResourceScope().getAccountIdentifier());
    assertEquals(orgIdentifier, auditEntry.getResourceScope().getOrgIdentifier());
    assertNull(auditEntry.getResourceScope().getProjectIdentifier());
    assertEquals(Action.CREATE, auditEntry.getAction());
    assertEquals(ModuleType.CORE, auditEntry.getModule());
    UserGroupDTO userGroupDTOFromYAML =
        yamlMapper.readValue(auditEntry.getNewYaml(), UserGroupRequest.class).getUserGroupDTO();
    assertEquals(userGroupDTOFromYAML, userGroupDTO);
    assertNull(auditEntry.getOldYaml());
    assertEquals(outboxEvent.getCreatedAt().longValue(), auditEntry.getTimestamp());
    assertNull(auditEntry.getEnvironment());
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testUpdate() throws IOException {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String identifier = randomAlphabetic(10);
    UserGroupDTO oldUserGroupDTO = getUserGroupDTO(accountIdentifier, orgIdentifier, null, identifier);
    UserGroupDTO newUserGroupDTO = getUserGroupDTO(accountIdentifier, orgIdentifier, null, identifier);
    UserGroupUpdateEvent userGroupUpdateEvent =
        new UserGroupUpdateEvent(accountIdentifier, newUserGroupDTO, oldUserGroupDTO);
    String eventData = objectMapper.writeValueAsString(userGroupUpdateEvent);
    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .id(randomAlphabetic(10))
                                  .blocked(false)
                                  .eventType("UserGroupUpdated")
                                  .eventData(eventData)
                                  .resourceScope(userGroupUpdateEvent.getResourceScope())
                                  .resource(userGroupUpdateEvent.getResource())
                                  .createdAt(Long.parseLong(randomNumeric(5)))
                                  .build();

    final ArgumentCaptor<Message> messageArgumentCaptor = ArgumentCaptor.forClass(Message.class);
    final ArgumentCaptor<AuditEntry> auditEntryArgumentCaptor = ArgumentCaptor.forClass(AuditEntry.class);
    when(producer.send(any())).thenReturn("");
    when(auditClientService.publishAudit(any(), any())).thenReturn(true);
    Scope scope = Scope.of(accountIdentifier, orgIdentifier, null);
    when(defaultUserGroupService.isDefaultUserGroup(scope, identifier)).thenReturn(false);
    userGroupEventHandler.handle(outboxEvent);

    verify(producer, times(1)).send(messageArgumentCaptor.capture());
    verify(auditClientService, times(1)).publishAudit(auditEntryArgumentCaptor.capture(), any());

    Message message = messageArgumentCaptor.getValue();
    assertNotNull(message.getMetadataMap());
    Map<String, String> metadataMap = message.getMetadataMap();
    assertEquals(accountIdentifier, metadataMap.get("accountId"));
    assertEquals(USER_GROUP, metadataMap.get(ENTITY_TYPE));
    assertEquals(UPDATE_ACTION, metadataMap.get(ACTION));

    AuditEntry auditEntry = auditEntryArgumentCaptor.getValue();
    assertNotNull(auditEntry);
    assertEquals(outboxEvent.getId(), auditEntry.getInsertId());
    assertEquals(ResourceTypeConstants.USER_GROUP, auditEntry.getResource().getType());
    assertEquals(identifier, auditEntry.getResource().getIdentifier());
    assertEquals(accountIdentifier, auditEntry.getResourceScope().getAccountIdentifier());
    assertEquals(orgIdentifier, auditEntry.getResourceScope().getOrgIdentifier());
    assertNull(auditEntry.getResourceScope().getProjectIdentifier());
    assertEquals(Action.UPDATE, auditEntry.getAction());
    assertEquals(ModuleType.CORE, auditEntry.getModule());
    UserGroupDTO newUserGroupDTOFromYAML =
        yamlMapper.readValue(auditEntry.getNewYaml(), UserGroupRequest.class).getUserGroupDTO();
    assertEquals(newUserGroupDTOFromYAML, newUserGroupDTO);
    UserGroupDTO oldUserGroupDTOFromYAML =
        yamlMapper.readValue(auditEntry.getOldYaml(), UserGroupRequest.class).getUserGroupDTO();
    assertEquals(oldUserGroupDTOFromYAML, oldUserGroupDTO);
    assertEquals(outboxEvent.getCreatedAt().longValue(), auditEntry.getTimestamp());
    assertNull(auditEntry.getEnvironment());
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testDelete() throws IOException {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String identifier = randomAlphabetic(10);
    UserGroupDTO userGroupDTO = getUserGroupDTO(accountIdentifier, orgIdentifier, null, identifier);
    UserGroupDeleteEvent userGroupDeleteEvent = new UserGroupDeleteEvent(accountIdentifier, userGroupDTO);
    String eventData = objectMapper.writeValueAsString(userGroupDeleteEvent);
    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .id(randomAlphabetic(10))
                                  .blocked(false)
                                  .eventType("UserGroupDeleted")
                                  .eventData(eventData)
                                  .resourceScope(userGroupDeleteEvent.getResourceScope())
                                  .resource(userGroupDeleteEvent.getResource())
                                  .createdAt(Long.parseLong(randomNumeric(5)))
                                  .build();

    final ArgumentCaptor<Message> messageArgumentCaptor = ArgumentCaptor.forClass(Message.class);
    final ArgumentCaptor<AuditEntry> auditEntryArgumentCaptor = ArgumentCaptor.forClass(AuditEntry.class);
    when(producer.send(any())).thenReturn("");
    when(auditClientService.publishAudit(any(), any())).thenReturn(true);
    Scope scope = Scope.of(accountIdentifier, orgIdentifier, null);
    when(defaultUserGroupService.isDefaultUserGroup(scope, identifier)).thenReturn(false);

    userGroupEventHandler.handle(outboxEvent);

    verify(producer, times(1)).send(messageArgumentCaptor.capture());
    verify(auditClientService, times(1)).publishAudit(auditEntryArgumentCaptor.capture(), any());

    Message message = messageArgumentCaptor.getValue();
    assertNotNull(message.getMetadataMap());
    Map<String, String> metadataMap = message.getMetadataMap();
    assertEquals(accountIdentifier, metadataMap.get("accountId"));
    assertEquals(USER_GROUP, metadataMap.get(ENTITY_TYPE));
    assertEquals(DELETE_ACTION, metadataMap.get(ACTION));

    AuditEntry auditEntry = auditEntryArgumentCaptor.getValue();
    assertNotNull(auditEntry);
    assertEquals(outboxEvent.getId(), auditEntry.getInsertId());
    assertEquals(ResourceTypeConstants.USER_GROUP, auditEntry.getResource().getType());
    assertEquals(identifier, auditEntry.getResource().getIdentifier());
    assertEquals(accountIdentifier, auditEntry.getResourceScope().getAccountIdentifier());
    assertEquals(orgIdentifier, auditEntry.getResourceScope().getOrgIdentifier());
    assertNull(auditEntry.getResourceScope().getProjectIdentifier());
    assertEquals(Action.DELETE, auditEntry.getAction());
    assertEquals(ModuleType.CORE, auditEntry.getModule());
    UserGroupDTO userGroupDTOFromYAML =
        yamlMapper.readValue(auditEntry.getOldYaml(), UserGroupRequest.class).getUserGroupDTO();
    assertEquals(userGroupDTOFromYAML, userGroupDTO);
    assertEquals(outboxEvent.getCreatedAt().longValue(), auditEntry.getTimestamp());
    assertNull(auditEntry.getEnvironment());
  }
}
