/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.outbox;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.eventsframework.EventsFrameworkConstants.USERMEMBERSHIP;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.CREATE_ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.DELETE_ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ENTITY_TYPE;
import static io.harness.ng.core.user.UserMembershipUpdateMechanism.ACCEPTED_INVITE;
import static io.harness.ng.core.user.UserMembershipUpdateMechanism.SYSTEM;
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
import io.harness.ng.core.events.AddCollaboratorEvent;
import io.harness.ng.core.events.RemoveCollaboratorEvent;
import io.harness.ng.core.events.UserInviteCreateEvent;
import io.harness.ng.core.events.UserInviteDeleteEvent;
import io.harness.ng.core.events.UserInviteUpdateEvent;
import io.harness.ng.core.events.UserMembershipAddEvent;
import io.harness.ng.core.events.UserMembershipRemoveEvent;
import io.harness.ng.core.invites.dto.InviteDTO;
import io.harness.ng.core.invites.dto.RoleBinding;
import io.harness.ng.core.user.UserMembershipUpdateSource;
import io.harness.outbox.OutboxEvent;
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
public class UserEventHandlerTest extends CategoryTest {
  private ObjectMapper objectMapper;
  private Producer producer;
  private AuditClientService auditClientService;
  private UserEventHandler userEventHandler;

  @Before
  public void setup() {
    objectMapper = NG_DEFAULT_OBJECT_MAPPER;
    producer = mock(Producer.class);
    auditClientService = mock(AuditClientService.class);
    userEventHandler = spy(new UserEventHandler(producer, auditClientService));
  }

  private InviteDTO getInviteDTO(String email) {
    List<RoleBinding> roleBindings = new ArrayList<>();
    roleBindings.add(RoleBinding.builder().build());

    return InviteDTO.builder()
        .id(randomAlphabetic(10))
        .name(randomAlphabetic(10))
        .email(email)
        .roleBindings(roleBindings)
        .inviteType(null)
        .build();
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testInviteCreate() throws JsonProcessingException {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String email = randomAlphabetic(10);
    InviteDTO inviteDTO = getInviteDTO(email);
    UserInviteCreateEvent userInviteCreateEvent =
        new UserInviteCreateEvent(accountIdentifier, orgIdentifier, null, inviteDTO);
    String eventData = objectMapper.writeValueAsString(userInviteCreateEvent);
    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .id(randomAlphabetic(10))
                                  .blocked(false)
                                  .eventType("UserInviteCreated")
                                  .eventData(eventData)
                                  .resourceScope(userInviteCreateEvent.getResourceScope())
                                  .resource(userInviteCreateEvent.getResource())
                                  .createdAt(Long.parseLong(randomNumeric(5)))
                                  .build();

    final ArgumentCaptor<AuditEntry> auditEntryArgumentCaptor = ArgumentCaptor.forClass(AuditEntry.class);
    when(auditClientService.publishAudit(any(), any())).thenReturn(true);

    userEventHandler.handle(outboxEvent);

    verify(auditClientService, times(1)).publishAudit(auditEntryArgumentCaptor.capture(), any());

    AuditEntry auditEntry = auditEntryArgumentCaptor.getValue();
    assertAuditEntry(accountIdentifier, orgIdentifier, email, auditEntry, outboxEvent);
    assertEquals(Action.INVITE, auditEntry.getAction());
    assertNull(auditEntry.getOldYaml());
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testInviteUpdate() throws JsonProcessingException {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String email = randomAlphabetic(10);
    InviteDTO oldInviteDTO = getInviteDTO(email);
    InviteDTO newInviteDTO = getInviteDTO(email);
    UserInviteUpdateEvent userInviteUpdateEvent =
        new UserInviteUpdateEvent(accountIdentifier, orgIdentifier, null, oldInviteDTO, newInviteDTO);
    String eventData = objectMapper.writeValueAsString(userInviteUpdateEvent);
    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .id(randomAlphabetic(10))
                                  .blocked(false)
                                  .eventType("UserInviteUpdated")
                                  .eventData(eventData)
                                  .resourceScope(userInviteUpdateEvent.getResourceScope())
                                  .resource(userInviteUpdateEvent.getResource())
                                  .createdAt(Long.parseLong(randomNumeric(5)))
                                  .build();

    final ArgumentCaptor<AuditEntry> auditEntryArgumentCaptor = ArgumentCaptor.forClass(AuditEntry.class);
    when(auditClientService.publishAudit(any(), any())).thenReturn(true);

    userEventHandler.handle(outboxEvent);

    verify(auditClientService, times(1)).publishAudit(auditEntryArgumentCaptor.capture(), any());

    AuditEntry auditEntry = auditEntryArgumentCaptor.getValue();
    assertAuditEntry(accountIdentifier, orgIdentifier, email, auditEntry, outboxEvent);
    assertEquals(Action.RESEND_INVITE, auditEntry.getAction());
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testInviteDelete() throws JsonProcessingException {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String email = randomAlphabetic(10);
    InviteDTO inviteDTO = getInviteDTO(email);
    UserInviteDeleteEvent userInviteDeleteEvent =
        new UserInviteDeleteEvent(accountIdentifier, orgIdentifier, null, inviteDTO);
    String eventData = objectMapper.writeValueAsString(userInviteDeleteEvent);
    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .id(randomAlphabetic(10))
                                  .blocked(false)
                                  .eventType("UserInviteDeleted")
                                  .eventData(eventData)
                                  .resourceScope(userInviteDeleteEvent.getResourceScope())
                                  .resource(userInviteDeleteEvent.getResource())
                                  .createdAt(Long.parseLong(randomNumeric(5)))
                                  .build();

    final ArgumentCaptor<AuditEntry> auditEntryArgumentCaptor = ArgumentCaptor.forClass(AuditEntry.class);
    when(auditClientService.publishAudit(any(), any())).thenReturn(true);

    userEventHandler.handle(outboxEvent);

    verify(auditClientService, times(1)).publishAudit(auditEntryArgumentCaptor.capture(), any());

    AuditEntry auditEntry = auditEntryArgumentCaptor.getValue();
    assertAuditEntry(accountIdentifier, orgIdentifier, email, auditEntry, outboxEvent);
    assertEquals(Action.REVOKE_INVITE, auditEntry.getAction());
    assertNull(auditEntry.getNewYaml());
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testMembershipAdd() throws JsonProcessingException {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String email = randomAlphabetic(10);
    UserMembershipAddEvent userMembershipAddEvent = new UserMembershipAddEvent(accountIdentifier,
        Scope.builder().accountIdentifier(accountIdentifier).orgIdentifier(orgIdentifier).build(), email,
        randomAlphabetic(10), ACCEPTED_INVITE);
    String eventData = objectMapper.writeValueAsString(userMembershipAddEvent);
    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .id(randomAlphabetic(10))
                                  .blocked(false)
                                  .eventType("UserMembershipAdded")
                                  .eventData(eventData)
                                  .resourceScope(userMembershipAddEvent.getResourceScope())
                                  .resource(userMembershipAddEvent.getResource())
                                  .createdAt(Long.parseLong(randomNumeric(5)))
                                  .build();

    final ArgumentCaptor<Message> messageArgumentCaptor = ArgumentCaptor.forClass(Message.class);
    final ArgumentCaptor<AuditEntry> auditEntryArgumentCaptor = ArgumentCaptor.forClass(AuditEntry.class);
    when(producer.send(any())).thenReturn("");
    when(auditClientService.publishAudit(any(), any(), any())).thenReturn(true);

    userEventHandler.handle(outboxEvent);

    verify(producer, times(1)).send(messageArgumentCaptor.capture());
    verify(auditClientService, times(1)).publishAudit(auditEntryArgumentCaptor.capture(), any(), any());

    Message message = messageArgumentCaptor.getValue();
    assertNotNull(message.getMetadataMap());
    Map<String, String> metadataMap = message.getMetadataMap();
    assertEquals(accountIdentifier, metadataMap.get("accountId"));
    assertEquals(USERMEMBERSHIP, metadataMap.get(ENTITY_TYPE));
    assertEquals(CREATE_ACTION, metadataMap.get(ACTION));

    AuditEntry auditEntry = auditEntryArgumentCaptor.getValue();
    assertAuditEntry(accountIdentifier, orgIdentifier, email, auditEntry, outboxEvent);
    assertEquals(Action.ADD_COLLABORATOR, auditEntry.getAction());
    assertNotNull(auditEntry.getAuditEventData());
    assertNull(auditEntry.getOldYaml());
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testMembershipRemove() throws JsonProcessingException {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String email = randomAlphabetic(10);
    UserMembershipRemoveEvent userMembershipRemoveEvent = new UserMembershipRemoveEvent(accountIdentifier,
        Scope.builder().accountIdentifier(accountIdentifier).orgIdentifier(orgIdentifier).build(), email,
        randomAlphabetic(10), SYSTEM);
    String eventData = objectMapper.writeValueAsString(userMembershipRemoveEvent);
    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .id(randomAlphabetic(10))
                                  .blocked(false)
                                  .eventType("UserMembershipRemoved")
                                  .eventData(eventData)
                                  .resourceScope(userMembershipRemoveEvent.getResourceScope())
                                  .resource(userMembershipRemoveEvent.getResource())
                                  .createdAt(Long.parseLong(randomNumeric(5)))
                                  .build();

    final ArgumentCaptor<Message> messageArgumentCaptor = ArgumentCaptor.forClass(Message.class);
    final ArgumentCaptor<AuditEntry> auditEntryArgumentCaptor = ArgumentCaptor.forClass(AuditEntry.class);
    when(producer.send(any())).thenReturn("");
    when(auditClientService.publishAudit(any(), any(), any())).thenReturn(true);

    userEventHandler.handle(outboxEvent);

    verify(producer, times(1)).send(messageArgumentCaptor.capture());
    verify(auditClientService, times(1)).publishAudit(auditEntryArgumentCaptor.capture(), any(), any());

    Message message = messageArgumentCaptor.getValue();
    assertNotNull(message.getMetadataMap());
    Map<String, String> metadataMap = message.getMetadataMap();
    assertEquals(accountIdentifier, metadataMap.get("accountId"));
    assertEquals(USERMEMBERSHIP, metadataMap.get(ENTITY_TYPE));
    assertEquals(DELETE_ACTION, metadataMap.get(ACTION));

    AuditEntry auditEntry = auditEntryArgumentCaptor.getValue();
    assertAuditEntry(accountIdentifier, orgIdentifier, email, auditEntry, outboxEvent);
    assertEquals(Action.REMOVE_COLLABORATOR, auditEntry.getAction());
    assertNull(auditEntry.getNewYaml());
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testCollaboratorAdd() throws JsonProcessingException {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String email = randomAlphabetic(10);
    String userName = randomAlphabetic(10);
    AddCollaboratorEvent addCollaboratorEvent = new AddCollaboratorEvent(accountIdentifier,
        Scope.builder().accountIdentifier(accountIdentifier).orgIdentifier(orgIdentifier).build(), email,
        randomAlphabetic(10), userName, UserMembershipUpdateSource.ACCEPTED_INVITE);
    String eventData = objectMapper.writeValueAsString(addCollaboratorEvent);
    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .id(randomAlphabetic(10))
                                  .blocked(false)
                                  .eventType("CollaboratorAdded")
                                  .eventData(eventData)
                                  .resourceScope(addCollaboratorEvent.getResourceScope())
                                  .resource(addCollaboratorEvent.getResource())
                                  .createdAt(Long.parseLong(randomNumeric(5)))
                                  .build();

    final ArgumentCaptor<Message> messageArgumentCaptor = ArgumentCaptor.forClass(Message.class);
    final ArgumentCaptor<AuditEntry> auditEntryArgumentCaptor = ArgumentCaptor.forClass(AuditEntry.class);

    verifyInvocation(messageArgumentCaptor, auditEntryArgumentCaptor, outboxEvent);
    assertMessage(messageArgumentCaptor, accountIdentifier, USERMEMBERSHIP, CREATE_ACTION);

    AuditEntry auditEntry = auditEntryArgumentCaptor.getValue();
    assertAuditEntry(accountIdentifier, orgIdentifier, email, auditEntry, outboxEvent);
    assertEquals(Action.ADD_COLLABORATOR, auditEntry.getAction());
    assertNotNull(auditEntry.getAuditEventData());
    assertNull(auditEntry.getOldYaml());
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testCollaboratorRemove() throws JsonProcessingException {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String email = randomAlphabetic(10);
    String userName = randomAlphabetic(10);
    RemoveCollaboratorEvent removeCollaboratorEvent = new RemoveCollaboratorEvent(accountIdentifier,
        Scope.builder().accountIdentifier(accountIdentifier).orgIdentifier(orgIdentifier).build(), email,
        randomAlphabetic(10), userName, UserMembershipUpdateSource.SYSTEM);
    String eventData = objectMapper.writeValueAsString(removeCollaboratorEvent);
    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .id(randomAlphabetic(10))
                                  .blocked(false)
                                  .eventType("CollaboratorRemoved")
                                  .eventData(eventData)
                                  .resourceScope(removeCollaboratorEvent.getResourceScope())
                                  .resource(removeCollaboratorEvent.getResource())
                                  .createdAt(Long.parseLong(randomNumeric(5)))
                                  .build();

    final ArgumentCaptor<Message> messageArgumentCaptor = ArgumentCaptor.forClass(Message.class);
    final ArgumentCaptor<AuditEntry> auditEntryArgumentCaptor = ArgumentCaptor.forClass(AuditEntry.class);

    verifyInvocation(messageArgumentCaptor, auditEntryArgumentCaptor, outboxEvent);
    assertMessage(messageArgumentCaptor, accountIdentifier, USERMEMBERSHIP, DELETE_ACTION);

    AuditEntry auditEntry = auditEntryArgumentCaptor.getValue();
    assertAuditEntry(accountIdentifier, orgIdentifier, email, auditEntry, outboxEvent);
    assertEquals(Action.REMOVE_COLLABORATOR, auditEntry.getAction());
    assertNull(auditEntry.getNewYaml());
  }

  private void verifyInvocation(ArgumentCaptor<Message> messageArgumentCaptor,
      ArgumentCaptor<AuditEntry> auditEntryArgumentCaptor, OutboxEvent outboxEvent) {
    when(producer.send(any())).thenReturn("");
    when(auditClientService.publishAudit(any(), any(), any())).thenReturn(true);

    userEventHandler.handle(outboxEvent);

    verify(producer, times(1)).send(messageArgumentCaptor.capture());
    verify(auditClientService, times(1)).publishAudit(auditEntryArgumentCaptor.capture(), any(), any());
  }

  private void assertMessage(
      ArgumentCaptor<Message> messageArgumentCaptor, String accountIdentifier, String entityType, String action) {
    Message message = messageArgumentCaptor.getValue();
    assertNotNull(message.getMetadataMap());
    Map<String, String> metadataMap = message.getMetadataMap();
    assertEquals(accountIdentifier, metadataMap.get("accountId"));
    assertEquals(entityType, metadataMap.get(ENTITY_TYPE));
    assertEquals(action, metadataMap.get(ACTION));
  }

  private void assertAuditEntry(
      String accountIdentifier, String orgIdentifier, String email, AuditEntry auditEntry, OutboxEvent outboxEvent) {
    assertNotNull(auditEntry);
    assertEquals(outboxEvent.getId(), auditEntry.getInsertId());
    assertEquals(ResourceTypeConstants.USER, auditEntry.getResource().getType());
    assertEquals(email, auditEntry.getResource().getIdentifier());
    assertEquals(accountIdentifier, auditEntry.getResourceScope().getAccountIdentifier());
    assertEquals(orgIdentifier, auditEntry.getResourceScope().getOrgIdentifier());
    assertNull(auditEntry.getResourceScope().getProjectIdentifier());
    assertEquals(ModuleType.CORE, auditEntry.getModule());
    assertEquals(outboxEvent.getCreatedAt().longValue(), auditEntry.getTimestamp());
    assertNull(auditEntry.getEnvironment());
  }
}
