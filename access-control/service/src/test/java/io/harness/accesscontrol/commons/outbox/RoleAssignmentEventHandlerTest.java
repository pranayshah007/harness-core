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
import static io.harness.audit.Action.ROLE_ASSIGNMENT_CREATED;
import static io.harness.audit.Action.ROLE_ASSIGNMENT_DELETED;
import static io.harness.audit.Action.ROLE_ASSIGNMENT_UPDATED;
import static io.harness.rule.OwnerRule.JIMIT_GANDHI;
import static io.harness.rule.OwnerRule.KARAN;

import static io.serializer.HObjectMapper.NG_DEFAULT_OBJECT_MAPPER;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertNull;
import static junit.framework.TestCase.fail;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.apache.commons.lang3.RandomStringUtils.randomNumeric;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.ModuleType;
import io.harness.accesscontrol.principals.PrincipalDTO;
import io.harness.accesscontrol.principals.PrincipalType;
import io.harness.accesscontrol.principals.usergroups.UserGroup;
import io.harness.accesscontrol.principals.usergroups.UserGroupService;
import io.harness.accesscontrol.roleassignments.RoleAssignment;
import io.harness.accesscontrol.roleassignments.api.RoleAssignmentDTO;
import io.harness.accesscontrol.roleassignments.api.RoleAssignmentDTOMapper;
import io.harness.accesscontrol.roleassignments.events.RoleAssignmentCreateEvent;
import io.harness.accesscontrol.roleassignments.events.RoleAssignmentCreateEventV2;
import io.harness.accesscontrol.roleassignments.events.RoleAssignmentDeleteEvent;
import io.harness.accesscontrol.roleassignments.events.RoleAssignmentDeleteEventV2;
import io.harness.accesscontrol.roleassignments.events.RoleAssignmentUpdateEvent;
import io.harness.accesscontrol.roleassignments.events.RoleAssignmentUpdateEventV2;
import io.harness.accesscontrol.scopes.ScopeDTO;
import io.harness.accesscontrol.scopes.core.Scope;
import io.harness.accesscontrol.scopes.core.ScopeService;
import io.harness.aggregator.consumers.AccessControlChangeConsumer;
import io.harness.aggregator.consumers.RoleAssignmentChangeConsumer;
import io.harness.aggregator.models.RoleAssignmentChangeEventData;
import io.harness.annotations.dev.OwnedBy;
import io.harness.audit.Action;
import io.harness.audit.ResourceTypeConstants;
import io.harness.audit.beans.AuditEntry;
import io.harness.audit.client.api.AuditClientService;
import io.harness.category.element.UnitTests;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.user.remote.dto.UserMetadataDTO;
import io.harness.outbox.OutboxEvent;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;
import io.harness.serviceaccount.ServiceAccountDTO;
import io.harness.serviceaccount.remote.ServiceAccountClient;
import io.harness.usermembership.remote.UserMembershipClient;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import retrofit2.Call;
import retrofit2.Response;

@OwnedBy(PL)
public class RoleAssignmentEventHandlerTest extends CategoryTest {
  private ObjectMapper objectMapper;
  private AuditClientService auditClientService;
  private RoleAssignmentEventHandler roleassignmentEventHandler;
  private UserGroupService userGroupService;
  private UserMembershipClient userMembershipClient;
  private ServiceAccountClient serviceAccountClient;
  private OutboxEventHelper outboxEventHelper;
  private RoleAssignmentDTOMapper roleAssignmentDTOMapper;
  private AccessControlChangeConsumer<RoleAssignmentChangeEventData> roleAssignmentChangeConsumer;
  private ScopeService scopeService;

  @Before
  public void setup() {
    objectMapper = NG_DEFAULT_OBJECT_MAPPER;
    auditClientService = mock(AuditClientService.class);
    userGroupService = mock(UserGroupService.class);
    userMembershipClient = mock(UserMembershipClient.class);
    serviceAccountClient = mock(ServiceAccountClient.class);
    scopeService = mock(ScopeService.class);
    outboxEventHelper = new OutboxEventHelper(scopeService);
    roleAssignmentDTOMapper = new RoleAssignmentDTOMapper(scopeService);
    roleAssignmentChangeConsumer = mock(RoleAssignmentChangeConsumer.class);
    roleassignmentEventHandler =
        spy(new RoleAssignmentEventHandler(auditClientService, userGroupService, userMembershipClient,
            serviceAccountClient, outboxEventHelper, roleAssignmentDTOMapper, false, roleAssignmentChangeConsumer));
  }

  private RoleAssignmentDTO getRoleAssignmentDTO(String identifier, PrincipalDTO principalDTO) {
    return RoleAssignmentDTO.builder()
        .identifier(identifier)
        .roleIdentifier(randomAlphabetic(10))
        .principal(principalDTO)
        .build();
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
  public void testCreate() throws IOException {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String identifier = randomAlphabetic(10);
    String principalIdentifier = randomAlphabetic(10);
    String email = randomAlphabetic(10);
    PrincipalDTO principalDTO = PrincipalDTO.builder().type(PrincipalType.USER).identifier(principalIdentifier).build();
    RoleAssignmentDTO roleassignmentDTO = getRoleAssignmentDTO(identifier, principalDTO);
    ScopeDTO scopeDTO = getScopeDTO(accountIdentifier, orgIdentifier, null);
    RoleAssignmentCreateEvent roleassignmentCreateEvent =
        new RoleAssignmentCreateEvent(accountIdentifier, roleassignmentDTO, scopeDTO);
    String eventData = objectMapper.writeValueAsString(roleassignmentCreateEvent);
    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .id(randomAlphabetic(10))
                                  .blocked(false)
                                  .eventType("RoleAssignmentCreated")
                                  .eventData(eventData)
                                  .resourceScope(roleassignmentCreateEvent.getResourceScope())
                                  .resource(roleassignmentCreateEvent.getResource())
                                  .createdAt(Long.parseLong(randomNumeric(5)))
                                  .build();
    Call<RestResponse<UserMetadataDTO>> request = mock(Call.class);
    doReturn(request).when(userMembershipClient).getUser(any(), any());
    doReturn(Response.success(ResponseDTO.newResponse(
                 UserMetadataDTO.builder().name(randomAlphabetic(10)).uuid(principalIdentifier).email(email).build())))
        .when(request)
        .execute();
    final ArgumentCaptor<AuditEntry> auditEntryArgumentCaptor = ArgumentCaptor.forClass(AuditEntry.class);
    when(auditClientService.publishAudit(any(), any())).thenReturn(true);
    roleassignmentEventHandler.handle(outboxEvent);
    verify(auditClientService, times(1)).publishAudit(auditEntryArgumentCaptor.capture(), any());
    AuditEntry auditEntry = auditEntryArgumentCaptor.getValue();
    assertAuditEntry(accountIdentifier, orgIdentifier, email, auditEntry, outboxEvent, ROLE_ASSIGNMENT_CREATED);
    assertNull(auditEntry.getOldYaml());
    assertNotNull(auditEntry.getNewYaml());
  }

  @Test
  @Owner(developers = JIMIT_GANDHI)
  @Category(UnitTests.class)
  public void create_EventV2() throws IOException {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String identifier = randomAlphabetic(10);
    String principalIdentifier = randomAlphabetic(10);
    String email = randomAlphabetic(10);
    PrincipalDTO principalDTO = PrincipalDTO.builder().type(PrincipalType.USER).identifier(principalIdentifier).build();
    RoleAssignmentDTO roleassignmentDTO = getRoleAssignmentDTO(identifier, principalDTO);
    ScopeDTO scopeDTO = getScopeDTO(accountIdentifier, orgIdentifier, null);
    Scope scope = fromDTO(scopeDTO);
    RoleAssignment roleAssignment = RoleAssignmentDTOMapper.fromDTO(scope, roleassignmentDTO);
    RoleAssignmentCreateEventV2 roleassignmentCreateEventV2 =
        new RoleAssignmentCreateEventV2(roleAssignment, scope.toString());
    String eventData = objectMapper.writeValueAsString(roleassignmentCreateEventV2);
    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .id(randomAlphabetic(10))
                                  .blocked(false)
                                  .eventType("RoleAssignmentCreated")
                                  .eventData(eventData)
                                  .resourceScope(roleassignmentCreateEventV2.getResourceScope())
                                  .resource(roleassignmentCreateEventV2.getResource())
                                  .createdAt(Long.parseLong(randomNumeric(5)))
                                  .build();
    Call<RestResponse<UserMetadataDTO>> request = mock(Call.class);
    when(scopeService.buildScopeFromScopeIdentifier(outboxEvent.getResourceScope().getScope())).thenReturn(scope);
    doReturn(request).when(userMembershipClient).getUser(any(), any());
    doReturn(Response.success(ResponseDTO.newResponse(
                 UserMetadataDTO.builder().name(randomAlphabetic(10)).uuid(principalIdentifier).email(email).build())))
        .when(request)
        .execute();
    final ArgumentCaptor<AuditEntry> auditEntryArgumentCaptor = ArgumentCaptor.forClass(AuditEntry.class);
    when(auditClientService.publishAudit(any(), any())).thenReturn(true);
    roleassignmentEventHandler.handle(outboxEvent);
    verify(auditClientService, times(1)).publishAudit(auditEntryArgumentCaptor.capture(), any());
    AuditEntry auditEntry = auditEntryArgumentCaptor.getValue();
    assertAuditEntry(accountIdentifier, orgIdentifier, email, auditEntry, outboxEvent, ROLE_ASSIGNMENT_CREATED);
    assertNull(auditEntry.getOldYaml());
    assertNotNull(auditEntry.getNewYaml());
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testUpdate() throws JsonProcessingException {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String identifier = randomAlphabetic(10);
    String principalIdentifier = randomAlphabetic(10);
    PrincipalDTO principalDTO =
        PrincipalDTO.builder().type(PrincipalType.USER_GROUP).identifier(principalIdentifier).build();
    RoleAssignmentDTO oldRoleAssignmentDTO = getRoleAssignmentDTO(identifier, principalDTO);
    RoleAssignmentDTO newRoleAssignmentDTO = getRoleAssignmentDTO(identifier, principalDTO);
    ScopeDTO scopeDTO = getScopeDTO(accountIdentifier, orgIdentifier, null);
    String roleAssignmentId = randomAlphabetic(10);
    RoleAssignmentUpdateEvent roleassignmentUpdateEvent = new RoleAssignmentUpdateEvent(
        accountIdentifier, newRoleAssignmentDTO, oldRoleAssignmentDTO, scopeDTO, roleAssignmentId);
    String eventData = objectMapper.writeValueAsString(roleassignmentUpdateEvent);
    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .id(randomAlphabetic(10))
                                  .blocked(false)
                                  .eventType("RoleAssignmentUpdated")
                                  .eventData(eventData)
                                  .resourceScope(roleassignmentUpdateEvent.getResourceScope())
                                  .resource(roleassignmentUpdateEvent.getResource())
                                  .createdAt(Long.parseLong(randomNumeric(5)))
                                  .build();
    when(userGroupService.get(any(), any()))
        .thenReturn(
            Optional.of(UserGroup.builder().identifier(principalIdentifier).name(randomAlphabetic(10)).build()));
    final ArgumentCaptor<AuditEntry> auditEntryArgumentCaptor = ArgumentCaptor.forClass(AuditEntry.class);
    when(auditClientService.publishAudit(any(), any())).thenReturn(true);
    roleassignmentEventHandler.handle(outboxEvent);
    verify(auditClientService, times(1)).publishAudit(auditEntryArgumentCaptor.capture(), any());
    AuditEntry auditEntry = auditEntryArgumentCaptor.getValue();
    assertAuditEntry(
        accountIdentifier, orgIdentifier, principalIdentifier, auditEntry, outboxEvent, ROLE_ASSIGNMENT_UPDATED);
    assertNotNull(auditEntry.getOldYaml());
    assertNotNull(auditEntry.getNewYaml());
  }

  @Test
  @Owner(developers = JIMIT_GANDHI)
  @Category(UnitTests.class)
  public void update_EventV1_ACLProcessingNotEnabled() throws JsonProcessingException {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String identifier = randomAlphabetic(10);
    String principalIdentifier = randomAlphabetic(10);
    PrincipalDTO principalDTO =
        PrincipalDTO.builder().type(PrincipalType.USER_GROUP).identifier(principalIdentifier).build();
    RoleAssignmentDTO oldRoleAssignmentDTO = getRoleAssignmentDTO(identifier, principalDTO);
    RoleAssignmentDTO newRoleAssignmentDTO = getRoleAssignmentDTO(identifier, principalDTO);
    ScopeDTO scopeDTO = getScopeDTO(accountIdentifier, orgIdentifier, null);
    String roleAssignmentId = randomAlphabetic(10);
    RoleAssignmentUpdateEvent roleassignmentUpdateEvent = new RoleAssignmentUpdateEvent(
        accountIdentifier, newRoleAssignmentDTO, oldRoleAssignmentDTO, scopeDTO, roleAssignmentId);
    String eventData = objectMapper.writeValueAsString(roleassignmentUpdateEvent);
    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .id(randomAlphabetic(10))
                                  .blocked(false)
                                  .eventType("RoleAssignmentUpdated")
                                  .eventData(eventData)
                                  .resourceScope(roleassignmentUpdateEvent.getResourceScope())
                                  .resource(roleassignmentUpdateEvent.getResource())
                                  .createdAt(Long.parseLong(randomNumeric(5)))
                                  .build();
    when(userGroupService.get(any(), any()))
        .thenReturn(
            Optional.of(UserGroup.builder().identifier(principalIdentifier).name(randomAlphabetic(10)).build()));
    final ArgumentCaptor<AuditEntry> auditEntryArgumentCaptor = ArgumentCaptor.forClass(AuditEntry.class);
    when(auditClientService.publishAudit(any(), any())).thenReturn(true);
    roleassignmentEventHandler.handle(outboxEvent);
    verify(auditClientService, times(1)).publishAudit(auditEntryArgumentCaptor.capture(), any());
    AuditEntry auditEntry = auditEntryArgumentCaptor.getValue();
    assertAuditEntry(
        accountIdentifier, orgIdentifier, principalIdentifier, auditEntry, outboxEvent, ROLE_ASSIGNMENT_UPDATED);
    assertNotNull(auditEntry.getOldYaml());
    assertNotNull(auditEntry.getNewYaml());
    verify(roleAssignmentChangeConsumer, never()).consumeEvent(any(), any(), any());
  }

  @Test
  @Owner(developers = JIMIT_GANDHI)
  @Category(UnitTests.class)
  public void update_EventV1_ACLProcessingEnabled() throws JsonProcessingException {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String identifier = randomAlphabetic(10);
    String principalIdentifier = randomAlphabetic(10);
    PrincipalDTO principalDTO =
        PrincipalDTO.builder().type(PrincipalType.USER_GROUP).identifier(principalIdentifier).build();
    RoleAssignmentDTO oldRoleAssignmentDTO = getRoleAssignmentDTO(identifier, principalDTO);
    RoleAssignmentDTO newRoleAssignmentDTO = getRoleAssignmentDTO(identifier, principalDTO);
    ScopeDTO scopeDTO = getScopeDTO(accountIdentifier, orgIdentifier, null);
    Scope scope = fromDTO(scopeDTO);
    RoleAssignment oldRoleAssignment = RoleAssignmentDTOMapper.fromDTO(scope, oldRoleAssignmentDTO);
    RoleAssignment newRoleAssignment = RoleAssignmentDTOMapper.fromDTO(scope, newRoleAssignmentDTO);
    String roleAssignmentId = randomAlphabetic(10);
    RoleAssignmentUpdateEvent roleassignmentUpdateEvent = new RoleAssignmentUpdateEvent(
        accountIdentifier, newRoleAssignmentDTO, oldRoleAssignmentDTO, scopeDTO, roleAssignmentId);
    String eventData = objectMapper.writeValueAsString(roleassignmentUpdateEvent);
    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .id(randomAlphabetic(10))
                                  .blocked(false)
                                  .eventType("RoleAssignmentUpdated")
                                  .eventData(eventData)
                                  .resourceScope(roleassignmentUpdateEvent.getResourceScope())
                                  .resource(roleassignmentUpdateEvent.getResource())
                                  .createdAt(Long.parseLong(randomNumeric(5)))
                                  .build();
    roleassignmentEventHandler =
        spy(new RoleAssignmentEventHandler(auditClientService, userGroupService, userMembershipClient,
            serviceAccountClient, outboxEventHelper, roleAssignmentDTOMapper, true, roleAssignmentChangeConsumer));
    when(userGroupService.get(any(), any()))
        .thenReturn(
            Optional.of(UserGroup.builder().identifier(principalIdentifier).name(randomAlphabetic(10)).build()));
    final ArgumentCaptor<AuditEntry> auditEntryArgumentCaptor = ArgumentCaptor.forClass(AuditEntry.class);
    when(auditClientService.publishAudit(any(), any())).thenReturn(true);
    roleassignmentEventHandler.handle(outboxEvent);
    verify(auditClientService, times(1)).publishAudit(auditEntryArgumentCaptor.capture(), any());
    AuditEntry auditEntry = auditEntryArgumentCaptor.getValue();
    assertAuditEntry(
        accountIdentifier, orgIdentifier, principalIdentifier, auditEntry, outboxEvent, ROLE_ASSIGNMENT_UPDATED);
    assertNotNull(auditEntry.getOldYaml());
    assertNotNull(auditEntry.getNewYaml());
    RoleAssignmentChangeEventData roleAssignmentChangeEventData = RoleAssignmentChangeEventData.builder()
                                                                      .newRoleAssignment(newRoleAssignment)
                                                                      .updatedRoleAssignment(oldRoleAssignment)
                                                                      .build();
    verify(roleAssignmentChangeConsumer, times(1)).consumeEvent(UPDATE_ACTION, null, roleAssignmentChangeEventData);
  }

  @Test
  @Owner(developers = JIMIT_GANDHI)
  @Category(UnitTests.class)
  public void update_EventV2_ACLProcessingNotEnabled() throws JsonProcessingException {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String identifier = randomAlphabetic(10);
    String principalIdentifier = randomAlphabetic(10);
    PrincipalDTO principalDTO =
        PrincipalDTO.builder().type(PrincipalType.USER_GROUP).identifier(principalIdentifier).build();
    RoleAssignmentDTO oldRoleAssignmentDTO = getRoleAssignmentDTO(identifier, principalDTO);
    RoleAssignmentDTO newRoleAssignmentDTO = getRoleAssignmentDTO(identifier, principalDTO);
    ScopeDTO scopeDTO = getScopeDTO(accountIdentifier, orgIdentifier, null);
    Scope scope = fromDTO(scopeDTO);
    RoleAssignment oldRoleAssignment = RoleAssignmentDTOMapper.fromDTO(scope, oldRoleAssignmentDTO);
    RoleAssignment newRoleAssignment = RoleAssignmentDTOMapper.fromDTO(scope, newRoleAssignmentDTO);
    RoleAssignmentUpdateEventV2 roleAssignmentUpdateEventV2 =
        new RoleAssignmentUpdateEventV2(oldRoleAssignment, newRoleAssignment, scope.toString());
    String eventData = objectMapper.writeValueAsString(roleAssignmentUpdateEventV2);
    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .id(randomAlphabetic(10))
                                  .blocked(false)
                                  .eventType("RoleAssignmentUpdated")
                                  .eventData(eventData)
                                  .resourceScope(roleAssignmentUpdateEventV2.getResourceScope())
                                  .resource(roleAssignmentUpdateEventV2.getResource())
                                  .createdAt(Long.parseLong(randomNumeric(5)))
                                  .build();
    when(scopeService.buildScopeFromScopeIdentifier(outboxEvent.getResourceScope().getScope())).thenReturn(scope);
    when(userGroupService.get(any(), any()))
        .thenReturn(
            Optional.of(UserGroup.builder().identifier(principalIdentifier).name(randomAlphabetic(10)).build()));
    final ArgumentCaptor<AuditEntry> auditEntryArgumentCaptor = ArgumentCaptor.forClass(AuditEntry.class);
    when(auditClientService.publishAudit(any(), any())).thenReturn(true);
    roleassignmentEventHandler.handle(outboxEvent);
    verify(auditClientService, times(1)).publishAudit(auditEntryArgumentCaptor.capture(), any());
    AuditEntry auditEntry = auditEntryArgumentCaptor.getValue();
    assertAuditEntry(
        accountIdentifier, orgIdentifier, principalIdentifier, auditEntry, outboxEvent, ROLE_ASSIGNMENT_UPDATED);
    assertNotNull(auditEntry.getOldYaml());
    assertNotNull(auditEntry.getNewYaml());
    verify(roleAssignmentChangeConsumer, never()).consumeEvent(any(), any(), any());
  }

  @Test
  @Owner(developers = JIMIT_GANDHI)
  @Category(UnitTests.class)
  public void update_EventV2_ACLProcessingEnabled() throws JsonProcessingException {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String identifier = randomAlphabetic(10);
    String principalIdentifier = randomAlphabetic(10);
    PrincipalDTO principalDTO =
        PrincipalDTO.builder().type(PrincipalType.USER_GROUP).identifier(principalIdentifier).build();
    RoleAssignmentDTO oldRoleAssignmentDTO = getRoleAssignmentDTO(identifier, principalDTO);
    RoleAssignmentDTO newRoleAssignmentDTO = getRoleAssignmentDTO(identifier, principalDTO);
    ScopeDTO scopeDTO = getScopeDTO(accountIdentifier, orgIdentifier, null);
    Scope scope = fromDTO(scopeDTO);
    RoleAssignment oldRoleAssignment = RoleAssignmentDTOMapper.fromDTO(scope, oldRoleAssignmentDTO);
    RoleAssignment newRoleAssignment = RoleAssignmentDTOMapper.fromDTO(scope, newRoleAssignmentDTO);
    RoleAssignmentUpdateEventV2 roleAssignmentUpdateEventV2 =
        new RoleAssignmentUpdateEventV2(oldRoleAssignment, newRoleAssignment, scope.toString());
    roleassignmentEventHandler =
        spy(new RoleAssignmentEventHandler(auditClientService, userGroupService, userMembershipClient,
            serviceAccountClient, outboxEventHelper, roleAssignmentDTOMapper, true, roleAssignmentChangeConsumer));
    String eventData = objectMapper.writeValueAsString(roleAssignmentUpdateEventV2);
    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .id(randomAlphabetic(10))
                                  .blocked(false)
                                  .eventType("RoleAssignmentUpdated")
                                  .eventData(eventData)
                                  .resourceScope(roleAssignmentUpdateEventV2.getResourceScope())
                                  .resource(roleAssignmentUpdateEventV2.getResource())
                                  .createdAt(Long.parseLong(randomNumeric(5)))
                                  .build();
    when(scopeService.buildScopeFromScopeIdentifier(outboxEvent.getResourceScope().getScope())).thenReturn(scope);
    when(userGroupService.get(any(), any()))
        .thenReturn(
            Optional.of(UserGroup.builder().identifier(principalIdentifier).name(randomAlphabetic(10)).build()));
    final ArgumentCaptor<AuditEntry> auditEntryArgumentCaptor = ArgumentCaptor.forClass(AuditEntry.class);
    when(auditClientService.publishAudit(any(), any())).thenReturn(true);
    roleassignmentEventHandler.handle(outboxEvent);
    verify(auditClientService, times(1)).publishAudit(auditEntryArgumentCaptor.capture(), any());
    AuditEntry auditEntry = auditEntryArgumentCaptor.getValue();
    assertAuditEntry(
        accountIdentifier, orgIdentifier, principalIdentifier, auditEntry, outboxEvent, ROLE_ASSIGNMENT_UPDATED);
    assertNotNull(auditEntry.getOldYaml());
    assertNotNull(auditEntry.getNewYaml());
    RoleAssignmentChangeEventData roleAssignmentChangeEventData = RoleAssignmentChangeEventData.builder()
                                                                      .newRoleAssignment(newRoleAssignment)
                                                                      .updatedRoleAssignment(oldRoleAssignment)
                                                                      .build();
    verify(roleAssignmentChangeConsumer, times(1)).consumeEvent(UPDATE_ACTION, null, roleAssignmentChangeEventData);
  }

  @Test
  @Owner(developers = {KARAN, JIMIT_GANDHI})
  @Category(UnitTests.class)
  public void testDelete() throws IOException {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String identifier = randomAlphabetic(10);
    String principalIdentifier = randomAlphabetic(10);
    PrincipalDTO principalDTO =
        PrincipalDTO.builder().type(PrincipalType.SERVICE_ACCOUNT).identifier(principalIdentifier).build();
    RoleAssignmentDTO roleassignmentDTO = getRoleAssignmentDTO(identifier, principalDTO);
    ScopeDTO scopeDTO = getScopeDTO(accountIdentifier, orgIdentifier, null);
    RoleAssignmentDeleteEvent roleassignmentDeleteEvent =
        new RoleAssignmentDeleteEvent(accountIdentifier, roleassignmentDTO, scopeDTO);
    String eventData = objectMapper.writeValueAsString(roleassignmentDeleteEvent);
    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .id(randomAlphabetic(10))
                                  .blocked(false)
                                  .eventType("RoleAssignmentDeleted")
                                  .eventData(eventData)
                                  .resourceScope(roleassignmentDeleteEvent.getResourceScope())
                                  .resource(roleassignmentDeleteEvent.getResource())
                                  .createdAt(Long.parseLong(randomNumeric(5)))
                                  .build();
    Call<RestResponse<List<ServiceAccountDTO>>> request = mock(Call.class);
    doReturn(request).when(serviceAccountClient).listServiceAccounts(any(), any(), any(), any());
    doReturn(Response.success(ResponseDTO.newResponse(Lists.newArrayList(
                 ServiceAccountDTO.builder().name(randomAlphabetic(10)).identifier(principalIdentifier).build()))))
        .when(request)
        .execute();
    final ArgumentCaptor<AuditEntry> auditEntryArgumentCaptor = ArgumentCaptor.forClass(AuditEntry.class);
    when(auditClientService.publishAudit(any(), any())).thenReturn(true);
    roleassignmentEventHandler.handle(outboxEvent);
    verify(auditClientService, times(1)).publishAudit(auditEntryArgumentCaptor.capture(), any());
    AuditEntry auditEntry = auditEntryArgumentCaptor.getValue();
    assertAuditEntry(
        accountIdentifier, orgIdentifier, principalIdentifier, auditEntry, outboxEvent, ROLE_ASSIGNMENT_DELETED);
    assertNotNull(auditEntry.getOldYaml());
    assertNull(auditEntry.getNewYaml());
  }

  @Test
  @Owner(developers = JIMIT_GANDHI)
  @Category(UnitTests.class)
  public void deleteEventV1_LogAudit() throws IOException {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String identifier = randomAlphabetic(10);
    String principalIdentifier = randomAlphabetic(10);
    PrincipalDTO principalDTO =
        PrincipalDTO.builder().type(PrincipalType.SERVICE_ACCOUNT).identifier(principalIdentifier).build();
    RoleAssignmentDTO roleassignmentDTO = getRoleAssignmentDTO(identifier, principalDTO);
    ScopeDTO scopeDTO = getScopeDTO(accountIdentifier, orgIdentifier, null);
    String roleAssignmentId = randomAlphabetic(10);
    RoleAssignmentDeleteEvent roleassignmentDeleteEvent =
        new RoleAssignmentDeleteEvent(accountIdentifier, roleassignmentDTO, scopeDTO, false, roleAssignmentId);
    String eventData = objectMapper.writeValueAsString(roleassignmentDeleteEvent);
    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .id(randomAlphabetic(10))
                                  .blocked(false)
                                  .eventType("RoleAssignmentDeleted")
                                  .eventData(eventData)
                                  .resourceScope(roleassignmentDeleteEvent.getResourceScope())
                                  .resource(roleassignmentDeleteEvent.getResource())
                                  .createdAt(Long.parseLong(randomNumeric(5)))
                                  .build();
    Call<RestResponse<List<ServiceAccountDTO>>> request = mock(Call.class);
    doReturn(request).when(serviceAccountClient).listServiceAccounts(any(), any(), any(), any());
    doReturn(Response.success(ResponseDTO.newResponse(Lists.newArrayList(
                 ServiceAccountDTO.builder().name(randomAlphabetic(10)).identifier(principalIdentifier).build()))))
        .when(request)
        .execute();
    final ArgumentCaptor<AuditEntry> auditEntryArgumentCaptor = ArgumentCaptor.forClass(AuditEntry.class);
    when(auditClientService.publishAudit(any(), any())).thenReturn(true);
    roleassignmentEventHandler.handle(outboxEvent);
    verify(auditClientService, times(1)).publishAudit(auditEntryArgumentCaptor.capture(), any());
    AuditEntry auditEntry = auditEntryArgumentCaptor.getValue();
    assertAuditEntry(
        accountIdentifier, orgIdentifier, principalIdentifier, auditEntry, outboxEvent, ROLE_ASSIGNMENT_DELETED);
    assertNotNull(auditEntry.getOldYaml());
    assertNull(auditEntry.getNewYaml());
  }

  @Test
  @Owner(developers = JIMIT_GANDHI)
  @Category(UnitTests.class)
  public void deleteEventV1_SkipAudit() throws IOException {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String identifier = randomAlphabetic(10);
    String principalIdentifier = randomAlphabetic(10);
    PrincipalDTO principalDTO =
        PrincipalDTO.builder().type(PrincipalType.SERVICE_ACCOUNT).identifier(principalIdentifier).build();
    RoleAssignmentDTO roleassignmentDTO = getRoleAssignmentDTO(identifier, principalDTO);
    ScopeDTO scopeDTO = getScopeDTO(accountIdentifier, orgIdentifier, null);
    String roleAssignmentId = randomAlphabetic(10);
    RoleAssignmentDeleteEvent roleassignmentDeleteEvent =
        new RoleAssignmentDeleteEvent(accountIdentifier, roleassignmentDTO, scopeDTO, true, roleAssignmentId);
    String eventData = objectMapper.writeValueAsString(roleassignmentDeleteEvent);
    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .id(randomAlphabetic(10))
                                  .blocked(false)
                                  .eventType("RoleAssignmentDeleted")
                                  .eventData(eventData)
                                  .resourceScope(roleassignmentDeleteEvent.getResourceScope())
                                  .resource(roleassignmentDeleteEvent.getResource())
                                  .createdAt(Long.parseLong(randomNumeric(5)))
                                  .build();
    Call<RestResponse<List<ServiceAccountDTO>>> request = mock(Call.class);
    doReturn(request).when(serviceAccountClient).listServiceAccounts(any(), any(), any(), any());
    doReturn(Response.success(ResponseDTO.newResponse(Lists.newArrayList(
                 ServiceAccountDTO.builder().name(randomAlphabetic(10)).identifier(principalIdentifier).build()))))
        .when(request)
        .execute();
    roleassignmentEventHandler.handle(outboxEvent);
    verify(auditClientService, never()).publishAudit(any(), any());
  }

  @Test
  @Owner(developers = JIMIT_GANDHI)
  @Category(UnitTests.class)
  public void deleteV2_SkipAudit() throws IOException {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String identifier = randomAlphabetic(10);
    String principalIdentifier = randomAlphabetic(10);
    PrincipalDTO principalDTO =
        PrincipalDTO.builder().type(PrincipalType.SERVICE_ACCOUNT).identifier(principalIdentifier).build();
    RoleAssignmentDTO roleassignmentDTO = getRoleAssignmentDTO(identifier, principalDTO);
    ScopeDTO scopeDTO = getScopeDTO(accountIdentifier, orgIdentifier, null);
    Scope scope = fromDTO(scopeDTO);
    RoleAssignment roleAssignment = RoleAssignmentDTOMapper.fromDTO(scope, roleassignmentDTO);
    RoleAssignmentDeleteEventV2 roleAssignmentDeleteEventV2 =
        new RoleAssignmentDeleteEventV2(roleAssignment, scope.toString(), true);
    String eventData = objectMapper.writeValueAsString(roleAssignmentDeleteEventV2);
    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .id(randomAlphabetic(10))
                                  .blocked(false)
                                  .eventType("RoleAssignmentDeleted")
                                  .eventData(eventData)
                                  .resourceScope(roleAssignmentDeleteEventV2.getResourceScope())
                                  .resource(roleAssignmentDeleteEventV2.getResource())
                                  .createdAt(Long.parseLong(randomNumeric(5)))
                                  .build();
    Call<RestResponse<List<ServiceAccountDTO>>> request = mock(Call.class);
    when(scopeService.buildScopeFromScopeIdentifier(outboxEvent.getResourceScope().getScope())).thenReturn(scope);
    doReturn(request).when(serviceAccountClient).listServiceAccounts(any(), any(), any(), any());
    doReturn(Response.success(ResponseDTO.newResponse(Lists.newArrayList(
                 ServiceAccountDTO.builder().name(randomAlphabetic(10)).identifier(principalIdentifier).build()))))
        .when(request)
        .execute();
    roleassignmentEventHandler.handle(outboxEvent);
    verify(auditClientService, never()).publishAudit(any(), any());
  }

  @Test
  @Owner(developers = JIMIT_GANDHI)
  @Category(UnitTests.class)
  public void deleteV2_LogAudit() throws IOException {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String identifier = randomAlphabetic(10);
    String principalIdentifier = randomAlphabetic(10);
    PrincipalDTO principalDTO =
        PrincipalDTO.builder().type(PrincipalType.SERVICE_ACCOUNT).identifier(principalIdentifier).build();
    RoleAssignmentDTO roleassignmentDTO = getRoleAssignmentDTO(identifier, principalDTO);
    ScopeDTO scopeDTO = getScopeDTO(accountIdentifier, orgIdentifier, null);
    Scope scope = fromDTO(scopeDTO);
    RoleAssignment roleAssignment = RoleAssignmentDTOMapper.fromDTO(scope, roleassignmentDTO);
    RoleAssignmentDeleteEventV2 roleAssignmentDeleteEventV2 =
        new RoleAssignmentDeleteEventV2(roleAssignment, scope.toString(), false);
    String eventData = objectMapper.writeValueAsString(roleAssignmentDeleteEventV2);
    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .id(randomAlphabetic(10))
                                  .blocked(false)
                                  .eventType("RoleAssignmentDeleted")
                                  .eventData(eventData)
                                  .resourceScope(roleAssignmentDeleteEventV2.getResourceScope())
                                  .resource(roleAssignmentDeleteEventV2.getResource())
                                  .createdAt(Long.parseLong(randomNumeric(5)))
                                  .build();
    Call<RestResponse<List<ServiceAccountDTO>>> request = mock(Call.class);
    final ArgumentCaptor<AuditEntry> auditEntryArgumentCaptor = ArgumentCaptor.forClass(AuditEntry.class);
    when(auditClientService.publishAudit(any(), any())).thenReturn(true);
    when(scopeService.buildScopeFromScopeIdentifier(outboxEvent.getResourceScope().getScope())).thenReturn(scope);
    doReturn(request).when(serviceAccountClient).listServiceAccounts(any(), any(), any(), any());
    doReturn(Response.success(ResponseDTO.newResponse(Lists.newArrayList(
                 ServiceAccountDTO.builder().name(randomAlphabetic(10)).identifier(principalIdentifier).build()))))
        .when(request)
        .execute();
    roleassignmentEventHandler.handle(outboxEvent);
    verify(auditClientService, times(1)).publishAudit(auditEntryArgumentCaptor.capture(), any());
    AuditEntry auditEntry = auditEntryArgumentCaptor.getValue();
    assertAuditEntry(
        accountIdentifier, orgIdentifier, principalIdentifier, auditEntry, outboxEvent, ROLE_ASSIGNMENT_DELETED);
    assertNotNull(auditEntry.getOldYaml());
    assertNull(auditEntry.getNewYaml());
  }

  private void assertAuditEntry(String accountIdentifier, String orgIdentifier, String auditResourceIdentifier,
      AuditEntry auditEntry, OutboxEvent outboxEvent, Action action) {
    assertNotNull(auditEntry);
    assertEquals(outboxEvent.getId(), auditEntry.getInsertId());
    switch (auditEntry.getResource().getType()) {
      case ResourceTypeConstants.USER:
        assertEquals(ResourceTypeConstants.USER, auditEntry.getResource().getType());
        break;
      case ResourceTypeConstants.USER_GROUP:
        assertEquals(ResourceTypeConstants.USER_GROUP, auditEntry.getResource().getType());
        break;
      case ResourceTypeConstants.SERVICE_ACCOUNT:
        assertEquals(ResourceTypeConstants.SERVICE_ACCOUNT, auditEntry.getResource().getType());
        break;
      default:
        fail();
    }
    assertEquals(auditResourceIdentifier, auditEntry.getResource().getIdentifier());
    assertEquals(accountIdentifier, auditEntry.getResourceScope().getAccountIdentifier());
    assertEquals(orgIdentifier, auditEntry.getResourceScope().getOrgIdentifier());
    assertNull(auditEntry.getResourceScope().getProjectIdentifier());
    assertEquals(ModuleType.CORE, auditEntry.getModule());
    assertEquals(outboxEvent.getCreatedAt().longValue(), auditEntry.getTimestamp());
    assertNull(auditEntry.getEnvironment());
    assertEquals(action, auditEntry.getAction());
  }
}
