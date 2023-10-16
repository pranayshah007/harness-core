/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.commons.outbox;

import static io.harness.accesscontrol.scopes.harness.ScopeMapper.fromDTO;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.JIMIT_GANDHI;
import static io.harness.rule.OwnerRule.KARAN;

import static io.serializer.HObjectMapper.NG_DEFAULT_OBJECT_MAPPER;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertNull;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.apache.commons.lang3.RandomStringUtils.randomNumeric;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.ModuleType;
import io.harness.accesscontrol.roles.Role;
import io.harness.accesscontrol.roles.api.RoleDTO;
import io.harness.accesscontrol.roles.api.RoleDTOMapper;
import io.harness.accesscontrol.roles.events.RoleCreateEvent;
import io.harness.accesscontrol.roles.events.RoleCreateEventV2;
import io.harness.accesscontrol.roles.events.RoleDeleteEvent;
import io.harness.accesscontrol.roles.events.RoleDeleteEventV2;
import io.harness.accesscontrol.roles.events.RoleUpdateEvent;
import io.harness.accesscontrol.roles.events.RoleUpdateEventV2;
import io.harness.accesscontrol.scopes.ScopeDTO;
import io.harness.accesscontrol.scopes.core.Scope;
import io.harness.accesscontrol.scopes.core.ScopeService;
import io.harness.aggregator.consumers.RoleChangeConsumer;
import io.harness.aggregator.models.RoleChangeEventData;
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
import com.google.common.collect.Sets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;

@OwnedBy(PL)
public class RoleEventHandlerTest extends CategoryTest {
  private ObjectMapper objectMapper;
  private AuditClientService auditClientService;
  private RoleEventHandler roleEventHandler;
  private RoleChangeConsumer roleChangeConsumer;
  private ScopeService scopeService;

  @Before
  public void setup() {
    objectMapper = NG_DEFAULT_OBJECT_MAPPER;
    auditClientService = mock(AuditClientService.class);
    roleChangeConsumer = mock(RoleChangeConsumer.class);
    scopeService = mock(ScopeService.class);
    roleEventHandler = spy(new RoleEventHandler(auditClientService, roleChangeConsumer, false, scopeService));
  }

  private Role getRole(String identifier, String scopeIdentifier) {
    Set<String> permissions =
        new HashSet<>(Arrays.asList(randomAlphabetic(10), randomAlphabetic(10), randomAlphabetic(10)));
    return Role.builder()
        .identifier(identifier)
        .name(randomAlphabetic(10))
        .permissions(permissions)
        .scopeIdentifier(scopeIdentifier)
        .build();
  }

  private RoleDTO getRoleDTO(String identifier) {
    Set<String> permissions =
        new HashSet<>(Arrays.asList(randomAlphabetic(10), randomAlphabetic(10), randomAlphabetic(10)));
    return RoleDTO.builder().identifier(identifier).name(randomAlphabetic(10)).permissions(permissions).build();
  }

  private ScopeDTO getScopeDTO(String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    return ScopeDTO.builder()
        .accountIdentifier(accountIdentifier)
        .orgIdentifier(orgIdentifier)
        .projectIdentifier(projectIdentifier)
        .build();
  }

  @Test
  @Owner(developers = {KARAN, JIMIT_GANDHI})
  @Category(UnitTests.class)
  public void testCreate() throws JsonProcessingException {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String identifier = randomAlphabetic(10);
    ScopeDTO scopeDTO = getScopeDTO(accountIdentifier, orgIdentifier, null);
    Scope scope = fromDTO(scopeDTO);
    RoleDTO roleDTO = getRoleDTO(identifier);
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
    when(scopeService.buildScopeFromScopeIdentifier(any())).thenReturn(scope);
    when(auditClientService.publishAudit(any(), any())).thenReturn(true);
    roleEventHandler.handle(outboxEvent);
    verify(auditClientService, times(1)).publishAudit(auditEntryArgumentCaptor.capture(), any());
    AuditEntry auditEntry = auditEntryArgumentCaptor.getValue();
    assertAuditEntry(accountIdentifier, orgIdentifier, identifier, auditEntry, outboxEvent);
    assertEquals(Action.CREATE, auditEntry.getAction());
    assertNull(auditEntry.getOldYaml());
  }

  @Test
  @Owner(developers = {KARAN, JIMIT_GANDHI})
  @Category(UnitTests.class)
  public void testCreate_EventV2() throws JsonProcessingException {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String identifier = randomAlphabetic(10);
    ScopeDTO scopeDTO = getScopeDTO(accountIdentifier, orgIdentifier, null);
    Scope scope = fromDTO(scopeDTO);
    Role role = getRole(identifier, scope.toString());
    RoleCreateEventV2 roleCreateEvent = new RoleCreateEventV2(scope.toString(), role);
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
    when(scopeService.buildScopeFromScopeIdentifier(any())).thenReturn(scope);
    when(auditClientService.publishAudit(any(), any())).thenReturn(true);
    roleEventHandler.handle(outboxEvent);
    verify(auditClientService, times(1)).publishAudit(auditEntryArgumentCaptor.capture(), any());
    AuditEntry auditEntry = auditEntryArgumentCaptor.getValue();
    assertAuditEntry(accountIdentifier, orgIdentifier, identifier, auditEntry, outboxEvent);
    assertEquals(Action.CREATE, auditEntry.getAction());
    assertNull(auditEntry.getOldYaml());
  }

  @Test
  @Owner(developers = {KARAN, JIMIT_GANDHI})
  @Category(UnitTests.class)
  public void testCreate_ForManagedRole_EventV2() throws JsonProcessingException {
    String identifier = randomAlphabetic(10);
    Role role = getRole(identifier, null);
    RoleCreateEventV2 roleCreateEvent = new RoleCreateEventV2(null, role);
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
    when(auditClientService.publishAudit(any(), any())).thenReturn(true);
    roleEventHandler.handle(outboxEvent);
    verify(auditClientService, never()).publishAudit(any(), any());
  }

  @Test
  @Owner(developers = {KARAN, JIMIT_GANDHI})
  @Category(UnitTests.class)
  public void testCreate_ForManagedRole_EventV1() throws JsonProcessingException {
    String identifier = randomAlphabetic(10);
    RoleDTO roleDTO = getRoleDTO(identifier);
    RoleCreateEvent roleCreateEvent = new RoleCreateEvent(null, roleDTO, null);
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
    when(auditClientService.publishAudit(any(), any())).thenReturn(true);
    roleEventHandler.handle(outboxEvent);
    verify(auditClientService, never()).publishAudit(any(), any());
  }

  @Test
  @Owner(developers = {KARAN, JIMIT_GANDHI})
  @Category(UnitTests.class)
  public void testUpdate() throws JsonProcessingException {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String identifier = randomAlphabetic(10);
    ScopeDTO scopeDTO = getScopeDTO(accountIdentifier, orgIdentifier, null);
    Scope scope = fromDTO(scopeDTO);
    RoleDTO oldRole = getRoleDTO(identifier);
    RoleDTO newRole = getRoleDTO(identifier);
    RoleUpdateEvent roleUpdateEvent = new RoleUpdateEvent(accountIdentifier, newRole, oldRole, scopeDTO);
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
    when(scopeService.buildScopeFromScopeIdentifier(any())).thenReturn(scope);
    when(auditClientService.publishAudit(any(), any())).thenReturn(true);
    roleEventHandler.handle(outboxEvent);
    verify(auditClientService, times(1)).publishAudit(auditEntryArgumentCaptor.capture(), any());
    AuditEntry auditEntry = auditEntryArgumentCaptor.getValue();
    assertAuditEntry(accountIdentifier, orgIdentifier, identifier, auditEntry, outboxEvent);
    assertEquals(Action.UPDATE, auditEntry.getAction());
  }

  @Test
  @Owner(developers = {KARAN, JIMIT_GANDHI})
  @Category(UnitTests.class)
  public void testUpdate_EventV2() throws JsonProcessingException {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String identifier = randomAlphabetic(10);
    ScopeDTO scopeDTO = getScopeDTO(accountIdentifier, orgIdentifier, null);
    Scope scope = fromDTO(scopeDTO);
    Role oldRole = getRole(identifier, scope.toString());
    Role newRole = getRole(identifier, scope.toString());
    RoleUpdateEventV2 roleUpdateEvent = new RoleUpdateEventV2(scope.toString(), oldRole, newRole);
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
    when(scopeService.buildScopeFromScopeIdentifier(any())).thenReturn(scope);
    when(auditClientService.publishAudit(any(), any())).thenReturn(true);
    roleEventHandler.handle(outboxEvent);
    verify(auditClientService, times(1)).publishAudit(auditEntryArgumentCaptor.capture(), any());
    AuditEntry auditEntry = auditEntryArgumentCaptor.getValue();
    assertAuditEntry(accountIdentifier, orgIdentifier, identifier, auditEntry, outboxEvent);
    assertEquals(Action.UPDATE, auditEntry.getAction());
  }

  @Test
  @Owner(developers = {KARAN, JIMIT_GANDHI})
  @Category(UnitTests.class)
  public void updateWithAclProcessingNotEnabled_DoesNotDoAclProcessing() throws JsonProcessingException {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String identifier = randomAlphabetic(10);
    ScopeDTO scopeDTO = getScopeDTO(accountIdentifier, orgIdentifier, null);
    Scope scope = fromDTO(scopeDTO);
    RoleDTO oldRole = getRoleDTO(identifier);
    RoleDTO newRole = getRoleDTO(identifier);
    RoleUpdateEvent roleUpdateEvent = new RoleUpdateEvent(accountIdentifier, newRole, oldRole, scopeDTO);
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
    when(scopeService.buildScopeFromScopeIdentifier(any())).thenReturn(scope);
    when(auditClientService.publishAudit(any(), any())).thenReturn(true);
    roleEventHandler.handle(outboxEvent);
    verify(auditClientService, times(1)).publishAudit(auditEntryArgumentCaptor.capture(), any());
    verify(roleChangeConsumer, never()).consumeUpdateEvent(any(), any());
    AuditEntry auditEntry = auditEntryArgumentCaptor.getValue();
    assertAuditEntry(accountIdentifier, orgIdentifier, identifier, auditEntry, outboxEvent);
    assertEquals(Action.UPDATE, auditEntry.getAction());
  }

  @Test
  @Owner(developers = {KARAN, JIMIT_GANDHI})
  @Category(UnitTests.class)
  public void updateWithAclProcessingNotEnabled_EventV2_DoesNotDoAclProcessing() throws JsonProcessingException {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String identifier = randomAlphabetic(10);
    ScopeDTO scopeDTO = getScopeDTO(accountIdentifier, orgIdentifier, null);
    Scope scope = fromDTO(scopeDTO);
    Role oldRole = getRole(identifier, scope.toString());
    Role newRole = getRole(identifier, scope.toString());
    RoleUpdateEventV2 roleUpdateEvent = new RoleUpdateEventV2(scope.toString(), oldRole, newRole);
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
    when(scopeService.buildScopeFromScopeIdentifier(any())).thenReturn(scope);
    when(auditClientService.publishAudit(any(), any())).thenReturn(true);
    roleEventHandler.handle(outboxEvent);
    verify(auditClientService, times(1)).publishAudit(auditEntryArgumentCaptor.capture(), any());
    verify(roleChangeConsumer, never()).consumeUpdateEvent(any(), any());
    AuditEntry auditEntry = auditEntryArgumentCaptor.getValue();
    assertAuditEntry(accountIdentifier, orgIdentifier, identifier, auditEntry, outboxEvent);
    assertEquals(Action.UPDATE, auditEntry.getAction());
  }

  @Test
  @Owner(developers = {KARAN, JIMIT_GANDHI})
  @Category(UnitTests.class)
  public void updateManagedRoleWithAclProcessingNotEnabled_EventV1_DoesNotDoAclProcessing()
      throws JsonProcessingException {
    String identifier = randomAlphabetic(10);
    RoleDTO oldRole = getRoleDTO(identifier);
    RoleDTO newRole = getRoleDTO(identifier);
    RoleUpdateEvent roleUpdateEvent = new RoleUpdateEvent(null, newRole, oldRole, null);
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
    roleEventHandler.handle(outboxEvent);
    verify(auditClientService, never()).publishAudit(any(), any());
    verify(scopeService, never()).buildScopeFromScopeIdentifier(any());
    verify(roleChangeConsumer, never()).consumeUpdateEvent(any(), any());
  }

  @Test
  @Owner(developers = {KARAN, JIMIT_GANDHI})
  @Category(UnitTests.class)
  public void updateManagedRoleWithAclProcessingNotEnabled_EventV2_DoesNotDoAclProcessing()
      throws JsonProcessingException {
    String identifier = randomAlphabetic(10);
    Role oldRole = getRole(identifier, null);
    Role newRole = getRole(identifier, null);
    RoleUpdateEventV2 roleUpdateEvent = new RoleUpdateEventV2(null, oldRole, newRole);
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
    roleEventHandler.handle(outboxEvent);
    verify(auditClientService, never()).publishAudit(any(), any());
    verify(scopeService, never()).buildScopeFromScopeIdentifier(any());
    verify(roleChangeConsumer, never()).consumeUpdateEvent(any(), any());
  }

  @Test
  @Owner(developers = {KARAN, JIMIT_GANDHI})
  @Category(UnitTests.class)
  public void updateWithAclProcessingEnabled_DoesAclProcessing() throws JsonProcessingException {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String identifier = randomAlphabetic(10);
    ScopeDTO scopeDTO = getScopeDTO(accountIdentifier, orgIdentifier, null);
    Scope scope = fromDTO(scopeDTO);
    roleEventHandler = spy(new RoleEventHandler(auditClientService, roleChangeConsumer, true, scopeService));
    RoleDTO oldRole = getRoleDTO(identifier);
    RoleDTO newRole = getRoleDTO(identifier);
    Role newCoreRole = RoleDTOMapper.fromDTO(scope.toString(), newRole);
    RoleUpdateEvent roleUpdateEvent = new RoleUpdateEvent(accountIdentifier, newRole, oldRole, scopeDTO);
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
    Set<String> permissionsAddedToRole = Sets.difference(newRole.getPermissions(), oldRole.getPermissions());
    Set<String> permissionsRemovedFromRole = Sets.difference(oldRole.getPermissions(), newRole.getPermissions());
    final ArgumentCaptor<AuditEntry> auditEntryArgumentCaptor = ArgumentCaptor.forClass(AuditEntry.class);
    RoleChangeEventData roleChangeEventData = RoleChangeEventData.builder()
                                                  .updatedRole(newCoreRole)
                                                  .permissionsAdded(permissionsAddedToRole)
                                                  .permissionsRemoved(permissionsRemovedFromRole)
                                                  .build();
    when(scopeService.buildScopeFromScopeIdentifier(any())).thenReturn(scope);
    when(auditClientService.publishAudit(any(), any())).thenReturn(true);
    roleEventHandler.handle(outboxEvent);
    verify(auditClientService, times(1)).publishAudit(auditEntryArgumentCaptor.capture(), any());
    verify(roleChangeConsumer, times(1)).consumeUpdateEvent(null, roleChangeEventData);
    AuditEntry auditEntry = auditEntryArgumentCaptor.getValue();
    assertAuditEntry(accountIdentifier, orgIdentifier, identifier, auditEntry, outboxEvent);
    assertEquals(Action.UPDATE, auditEntry.getAction());
  }

  @Test
  @Owner(developers = {KARAN, JIMIT_GANDHI})
  @Category(UnitTests.class)
  public void updateWithAclProcessingEnabledAnd_EventV2_DoesAclProcessing() throws JsonProcessingException {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String identifier = randomAlphabetic(10);
    ScopeDTO scopeDTO = getScopeDTO(accountIdentifier, orgIdentifier, null);
    Scope scope = fromDTO(scopeDTO);
    Role oldRole = getRole(identifier, scope.toString());
    Role newRole = getRole(identifier, scope.toString());
    RoleUpdateEventV2 roleUpdateEvent = new RoleUpdateEventV2(scope.toString(), oldRole, newRole);
    roleEventHandler = spy(new RoleEventHandler(auditClientService, roleChangeConsumer, true, scopeService));
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
    Set<String> permissionsAddedToRole = Sets.difference(newRole.getPermissions(), oldRole.getPermissions());
    Set<String> permissionsRemovedFromRole = Sets.difference(oldRole.getPermissions(), newRole.getPermissions());
    final ArgumentCaptor<AuditEntry> auditEntryArgumentCaptor = ArgumentCaptor.forClass(AuditEntry.class);
    RoleChangeEventData roleChangeEventData = RoleChangeEventData.builder()
                                                  .updatedRole(newRole)
                                                  .permissionsAdded(permissionsAddedToRole)
                                                  .permissionsRemoved(permissionsRemovedFromRole)
                                                  .build();
    when(scopeService.buildScopeFromScopeIdentifier(any())).thenReturn(scope);
    when(auditClientService.publishAudit(any(), any())).thenReturn(true);
    roleEventHandler.handle(outboxEvent);
    verify(auditClientService, times(1)).publishAudit(auditEntryArgumentCaptor.capture(), any());
    verify(roleChangeConsumer, times(1)).consumeUpdateEvent(null, roleChangeEventData);
    AuditEntry auditEntry = auditEntryArgumentCaptor.getValue();
    assertAuditEntry(accountIdentifier, orgIdentifier, identifier, auditEntry, outboxEvent);
    assertEquals(Action.UPDATE, auditEntry.getAction());
  }

  @Test
  @Owner(developers = {KARAN, JIMIT_GANDHI})
  @Category(UnitTests.class)
  public void updateManagedRole_WithAclProcessingEnabledAnd_EventV1_DoesAclProcessing() throws JsonProcessingException {
    String identifier = randomAlphabetic(10);
    roleEventHandler = spy(new RoleEventHandler(auditClientService, roleChangeConsumer, true, scopeService));
    RoleDTO oldRole = getRoleDTO(identifier);
    RoleDTO newRole = getRoleDTO(identifier);
    RoleUpdateEvent roleUpdateEvent = new RoleUpdateEvent(null, newRole, oldRole, null);
    String eventData = objectMapper.writeValueAsString(roleUpdateEvent);
    roleEventHandler = spy(new RoleEventHandler(auditClientService, roleChangeConsumer, true, scopeService));
    Role newCoreRole = RoleDTOMapper.fromDTO(null, newRole);

    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .id(randomAlphabetic(10))
                                  .blocked(false)
                                  .eventType("RoleUpdated")
                                  .eventData(eventData)
                                  .resourceScope(roleUpdateEvent.getResourceScope())
                                  .resource(roleUpdateEvent.getResource())
                                  .createdAt(Long.parseLong(randomNumeric(5)))
                                  .build();
    Set<String> permissionsAddedToRole = Sets.difference(newRole.getPermissions(), oldRole.getPermissions());
    Set<String> permissionsRemovedFromRole = Sets.difference(oldRole.getPermissions(), newRole.getPermissions());
    RoleChangeEventData roleChangeEventData = RoleChangeEventData.builder()
                                                  .updatedRole(newCoreRole)
                                                  .permissionsAdded(permissionsAddedToRole)
                                                  .permissionsRemoved(permissionsRemovedFromRole)
                                                  .build();
    roleEventHandler.handle(outboxEvent);
    verify(auditClientService, never()).publishAudit(any(), any());
    verify(roleChangeConsumer, times(1)).consumeUpdateEvent(null, roleChangeEventData);
  }

  @Test
  @Owner(developers = {KARAN, JIMIT_GANDHI})
  @Category(UnitTests.class)
  public void updateManagedRole_WithAclProcessingEnabledAnd_EventV2_DoesAclProcessing() throws JsonProcessingException {
    String identifier = randomAlphabetic(10);
    Role oldRole = getRole(identifier, null);
    Role newRole = getRole(identifier, null);
    RoleUpdateEventV2 roleUpdateEvent = new RoleUpdateEventV2(null, oldRole, newRole);
    roleEventHandler = spy(new RoleEventHandler(auditClientService, roleChangeConsumer, true, scopeService));
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
    Set<String> permissionsAddedToRole = Sets.difference(newRole.getPermissions(), oldRole.getPermissions());
    Set<String> permissionsRemovedFromRole = Sets.difference(oldRole.getPermissions(), newRole.getPermissions());
    RoleChangeEventData roleChangeEventData = RoleChangeEventData.builder()
                                                  .updatedRole(newRole)
                                                  .permissionsAdded(permissionsAddedToRole)
                                                  .permissionsRemoved(permissionsRemovedFromRole)
                                                  .build();
    roleEventHandler.handle(outboxEvent);
    verify(auditClientService, never()).publishAudit(any(), any());
    verify(roleChangeConsumer, times(1)).consumeUpdateEvent(null, roleChangeEventData);
  }

  @Test
  @Owner(developers = {KARAN, JIMIT_GANDHI})
  @Category(UnitTests.class)
  public void testDelete() throws JsonProcessingException {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String identifier = randomAlphabetic(10);
    ScopeDTO scopeDTO = getScopeDTO(accountIdentifier, orgIdentifier, null);
    Scope scope = fromDTO(scopeDTO);
    RoleDTO roleDTO = getRoleDTO(identifier);
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
    when(scopeService.buildScopeFromScopeIdentifier(any())).thenReturn(scope);
    when(auditClientService.publishAudit(any(), any())).thenReturn(true);
    roleEventHandler.handle(outboxEvent);
    verify(auditClientService, times(1)).publishAudit(auditEntryArgumentCaptor.capture(), any());
    AuditEntry auditEntry = auditEntryArgumentCaptor.getValue();
    assertAuditEntry(accountIdentifier, orgIdentifier, identifier, auditEntry, outboxEvent);
    assertEquals(Action.DELETE, auditEntry.getAction());
    assertNull(auditEntry.getOldYaml());
  }

  @Test
  @Owner(developers = {KARAN, JIMIT_GANDHI})
  @Category(UnitTests.class)
  public void testDelete_EventV2() throws JsonProcessingException {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String identifier = randomAlphabetic(10);
    ScopeDTO scopeDTO = getScopeDTO(accountIdentifier, orgIdentifier, null);
    Scope scope = fromDTO(scopeDTO);
    Role role = getRole(identifier, scope.toString());
    RoleDeleteEventV2 roleDeleteEvent = new RoleDeleteEventV2(scope.toString(), role);
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
    when(scopeService.buildScopeFromScopeIdentifier(any())).thenReturn(scope);
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
