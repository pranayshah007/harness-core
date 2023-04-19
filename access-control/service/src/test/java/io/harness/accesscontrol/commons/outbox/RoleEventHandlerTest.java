/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.commons.outbox;

import static io.harness.annotations.dev.HarnessTeam.PL;
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
import io.harness.accesscontrol.roles.api.RoleDTO;
import io.harness.accesscontrol.roles.events.RoleCreateEvent;
import io.harness.accesscontrol.roles.events.RoleDeleteEvent;
import io.harness.accesscontrol.roles.events.RoleUpdateEvent;
import io.harness.accesscontrol.scopes.ScopeDTO;
import io.harness.annotations.dev.OwnedBy;
import io.harness.audit.Action;
import io.harness.audit.ResourceTypeConstants;
import io.harness.audit.beans.AuditEntry;
import io.harness.audit.client.api.AuditClientService;
import io.harness.category.element.UnitTests;
import io.harness.outbox.OutboxEvent;
import io.harness.rule.Owner;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;

@OwnedBy(PL)
public class RoleEventHandlerTest extends CategoryTest {
  private ObjectMapper objectMapper;
  private AuditClientService auditClientService;
  private RoleEventHandler roleEventHandler;

  @Before
  public void setup() {
    objectMapper = NG_DEFAULT_OBJECT_MAPPER;
    auditClientService = mock(AuditClientService.class);
    roleEventHandler = spy(new RoleEventHandler(auditClientService));
  }

  private RoleDTO getRoleDTO(String identifier) {
    return RoleDTO.builder().identifier(identifier).name(randomAlphabetic(10)).build();
  }

  private ScopeDTO getScopeDTO(String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    return ScopeDTO.builder()
        .accountIdentifier(accountIdentifier)
        .orgIdentifier(orgIdentifier)
        .projectIdentifier(projectIdentifier)
        .build();
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testCreate() throws JsonProcessingException {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String identifier = randomAlphabetic(10);
    RoleDTO roleDTO = getRoleDTO(identifier);
    ScopeDTO scopeDTO = getScopeDTO(accountIdentifier, orgIdentifier, null);
    RoleCreateEvent roleCreateEvent = new RoleCreateEvent(accountIdentifier, roleDTO, scopeDTO);
    String eventData = objectMapper.writeValueAsString(roleCreateEvent);
    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .id(randomAlphabetic(10))
                                  .blocked(false)
                                  .eventType("RoleCreated")
                                  .eventData(eventData)
                                  .resourceScope(roleCreateEvent.getResourceScope())
                                  .resource(roleCreateEvent.getResource())
                                  .createdAt(Long.parseLong(randomNumeric(5)))
                                  .build();
    final ArgumentCaptor<AuditEntry> auditEntryArgumentCaptor = ArgumentCaptor.forClass(AuditEntry.class);
    when(auditClientService.publishAudit(any(), any())).thenReturn(true);
    roleEventHandler.handle(outboxEvent);
    verify(auditClientService, times(1)).publishAudit(auditEntryArgumentCaptor.capture(), any());
    AuditEntry auditEntry = auditEntryArgumentCaptor.getValue();
    assertAuditEntry(accountIdentifier, orgIdentifier, identifier, auditEntry, outboxEvent);
    assertEquals(Action.CREATE, auditEntry.getAction());
    assertNull(auditEntry.getOldYaml());
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testUpdate() throws JsonProcessingException {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String identifier = randomAlphabetic(10);
    RoleDTO oldRoleDTO = getRoleDTO(identifier);
    RoleDTO newRoleDTO = getRoleDTO(identifier);
    ScopeDTO scopeDTO = getScopeDTO(accountIdentifier, orgIdentifier, null);
    RoleUpdateEvent roleUpdateEvent = new RoleUpdateEvent(accountIdentifier, newRoleDTO, oldRoleDTO, scopeDTO);
    String eventData = objectMapper.writeValueAsString(roleUpdateEvent);
    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .id(randomAlphabetic(10))
                                  .blocked(false)
                                  .eventType("RoleUpdated")
                                  .eventData(eventData)
                                  .resourceScope(roleUpdateEvent.getResourceScope())
                                  .resource(roleUpdateEvent.getResource())
                                  .createdAt(Long.parseLong(randomNumeric(5)))
                                  .build();
    final ArgumentCaptor<AuditEntry> auditEntryArgumentCaptor = ArgumentCaptor.forClass(AuditEntry.class);
    when(auditClientService.publishAudit(any(), any())).thenReturn(true);
    roleEventHandler.handle(outboxEvent);
    verify(auditClientService, times(1)).publishAudit(auditEntryArgumentCaptor.capture(), any());
    AuditEntry auditEntry = auditEntryArgumentCaptor.getValue();
    assertAuditEntry(accountIdentifier, orgIdentifier, identifier, auditEntry, outboxEvent);
    assertEquals(Action.UPDATE, auditEntry.getAction());
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testDelete() throws JsonProcessingException {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String identifier = randomAlphabetic(10);
    RoleDTO roleDTO = getRoleDTO(identifier);
    ScopeDTO scopeDTO = getScopeDTO(accountIdentifier, orgIdentifier, null);
    RoleDeleteEvent roleDeleteEvent = new RoleDeleteEvent(accountIdentifier, roleDTO, scopeDTO);
    String eventData = objectMapper.writeValueAsString(roleDeleteEvent);
    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .id(randomAlphabetic(10))
                                  .blocked(false)
                                  .eventType("RoleDeleted")
                                  .eventData(eventData)
                                  .resourceScope(roleDeleteEvent.getResourceScope())
                                  .resource(roleDeleteEvent.getResource())
                                  .createdAt(Long.parseLong(randomNumeric(5)))
                                  .build();
    final ArgumentCaptor<AuditEntry> auditEntryArgumentCaptor = ArgumentCaptor.forClass(AuditEntry.class);
    when(auditClientService.publishAudit(any(), any())).thenReturn(true);
    roleEventHandler.handle(outboxEvent);
    verify(auditClientService, times(1)).publishAudit(auditEntryArgumentCaptor.capture(), any());
    AuditEntry auditEntry = auditEntryArgumentCaptor.getValue();
    assertAuditEntry(accountIdentifier, orgIdentifier, identifier, auditEntry, outboxEvent);
    assertEquals(Action.DELETE, auditEntry.getAction());
    assertNull(auditEntry.getOldYaml());
  }

  private void assertAuditEntry(String accountIdentifier, String orgIdentifier, String identifier,
      AuditEntry auditEntry, OutboxEvent outboxEvent) {
    assertNotNull(auditEntry);
    assertEquals(outboxEvent.getId(), auditEntry.getInsertId());
    assertEquals(ResourceTypeConstants.ROLE, auditEntry.getResource().getType());
    assertEquals(identifier, auditEntry.getResource().getIdentifier());
    assertEquals(accountIdentifier, auditEntry.getResourceScope().getAccountIdentifier());
    assertEquals(orgIdentifier, auditEntry.getResourceScope().getOrgIdentifier());
    assertNull(auditEntry.getResourceScope().getProjectIdentifier());
    assertEquals(ModuleType.CORE, auditEntry.getModule());
    assertEquals(outboxEvent.getCreatedAt().longValue(), auditEntry.getTimestamp());
    assertNull(auditEntry.getEnvironment());
  }
}
