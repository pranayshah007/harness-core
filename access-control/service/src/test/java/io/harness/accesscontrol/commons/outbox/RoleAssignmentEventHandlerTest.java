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
import static junit.framework.TestCase.fail;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.apache.commons.lang3.RandomStringUtils.randomNumeric;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
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
import io.harness.accesscontrol.roleassignments.api.RoleAssignmentDTO;
import io.harness.accesscontrol.roleassignments.events.RoleAssignmentCreateEvent;
import io.harness.accesscontrol.roleassignments.events.RoleAssignmentDeleteEvent;
import io.harness.accesscontrol.roleassignments.events.RoleAssignmentUpdateEvent;
import io.harness.accesscontrol.scopes.ScopeDTO;
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

  @Before
  public void setup() {
    objectMapper = NG_DEFAULT_OBJECT_MAPPER;
    auditClientService = mock(AuditClientService.class);
    userGroupService = mock(UserGroupService.class);
    userMembershipClient = mock(UserMembershipClient.class);
    serviceAccountClient = mock(ServiceAccountClient.class);
    roleassignmentEventHandler = spy(new RoleAssignmentEventHandler(
        auditClientService, userGroupService, userMembershipClient, serviceAccountClient));
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
    assertAuditEntry(accountIdentifier, orgIdentifier, email, auditEntry, outboxEvent);
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
    RoleAssignmentUpdateEvent roleassignmentUpdateEvent =
        new RoleAssignmentUpdateEvent(accountIdentifier, newRoleAssignmentDTO, oldRoleAssignmentDTO, scopeDTO);
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
    assertAuditEntry(accountIdentifier, orgIdentifier, principalIdentifier, auditEntry, outboxEvent);
    assertNotNull(auditEntry.getOldYaml());
    assertNotNull(auditEntry.getNewYaml());
  }

  @Test
  @Owner(developers = KARAN)
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
    assertAuditEntry(accountIdentifier, orgIdentifier, principalIdentifier, auditEntry, outboxEvent);
    assertNotNull(auditEntry.getOldYaml());
    assertNull(auditEntry.getNewYaml());
  }

  private void assertAuditEntry(String accountIdentifier, String orgIdentifier, String auditResourceIdentifier,
      AuditEntry auditEntry, OutboxEvent outboxEvent) {
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
    assertEquals(Action.UPDATE, auditEntry.getAction());
  }
}
