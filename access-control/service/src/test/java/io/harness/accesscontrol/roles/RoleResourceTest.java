/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.roles;

import static io.harness.accesscontrol.AccessControlPermissions.EDIT_ROLE_PERMISSION;
import static io.harness.accesscontrol.AccessControlPermissions.VIEW_ROLE_PERMISSION;
import static io.harness.accesscontrol.AccessControlResourceTypes.ROLE;
import static io.harness.accesscontrol.common.filter.ManagedFilter.NO_FILTER;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.KARAN;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.accesscontrol.AccessControlTestBase;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.accesscontrol.roles.api.RoleDTO;
import io.harness.accesscontrol.roles.api.RoleDTOMapper;
import io.harness.accesscontrol.roles.api.RoleResource;
import io.harness.accesscontrol.roles.api.RoleResourceImpl;
import io.harness.accesscontrol.roles.api.RoleResponseDTO;
import io.harness.accesscontrol.roles.filter.RoleFilter;
import io.harness.accesscontrol.scopes.core.Scope;
import io.harness.accesscontrol.scopes.core.ScopeService;
import io.harness.accesscontrol.scopes.harness.HarnessScopeLevel;
import io.harness.accesscontrol.scopes.harness.HarnessScopeParams;
import io.harness.accesscontrol.scopes.harness.ScopeMapper;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.beans.PageRequest;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.outbox.api.OutboxService;
import io.harness.rule.Owner;

import java.util.Optional;
import javax.ws.rs.NotFoundException;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.springframework.transaction.support.TransactionTemplate;

@OwnedBy(PL)
public class RoleResourceTest extends AccessControlTestBase {
  private RoleService roleService;
  private ScopeService scopeService;
  private RoleDTOMapper roleDTOMapper;
  private TransactionTemplate transactionTemplate;
  private OutboxService outboxService;
  private AccessControlClient accessControlClient;
  private RoleResource roleResource;
  private PageRequest pageRequest;
  private String accountIdentifier;
  private String orgIdentifier;
  private HarnessScopeParams harnessScopeParams;
  private ResourceScope resourceScope;

  @Before
  public void setup() {
    roleService = mock(RoleService.class);
    scopeService = mock(ScopeService.class);
    roleDTOMapper = mock(RoleDTOMapper.class);
    transactionTemplate = mock(TransactionTemplate.class);
    outboxService = mock(OutboxService.class);
    accessControlClient = mock(AccessControlClient.class);
    roleResource = new RoleResourceImpl(
        roleService, scopeService, roleDTOMapper, transactionTemplate, outboxService, accessControlClient);
    pageRequest = PageRequest.builder().pageIndex(0).pageSize(50).build();
    accountIdentifier = randomAlphabetic(10);
    orgIdentifier = randomAlphabetic(10);
    harnessScopeParams =
        HarnessScopeParams.builder().accountIdentifier(accountIdentifier).orgIdentifier(orgIdentifier).build();
    resourceScope = ResourceScope.builder()
                        .projectIdentifier(harnessScopeParams.getProjectIdentifier())
                        .orgIdentifier(harnessScopeParams.getOrgIdentifier())
                        .accountIdentifier(harnessScopeParams.getAccountIdentifier())
                        .build();
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testList() {
    String searchTerm = randomAlphabetic(10);
    doNothing()
        .when(accessControlClient)
        .checkForAccessOrThrow(resourceScope, Resource.of(ROLE, null), VIEW_ROLE_PERMISSION);
    String scopeIdentifier = ScopeMapper.fromParams(harnessScopeParams).toString();
    RoleFilter roleFilter =
        RoleFilter.builder().searchTerm(searchTerm).scopeIdentifier(scopeIdentifier).managedFilter(NO_FILTER).build();
    when(roleService.list(pageRequest, roleFilter, true)).thenReturn(PageResponse.getEmptyPageResponse(pageRequest));
    ResponseDTO<PageResponse<RoleResponseDTO>> response = roleResource.get(pageRequest, harnessScopeParams, searchTerm);
    assertTrue(response.getData().isEmpty());
    verify(accessControlClient, times(1)).checkForAccessOrThrow(any(), any(), any());
    verify(roleService, times(1)).list(any(), any(), eq(true));
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testGet() {
    String identifier = randomAlphabetic(10);
    doNothing()
        .when(accessControlClient)
        .checkForAccessOrThrow(resourceScope, Resource.of(ROLE, identifier), VIEW_ROLE_PERMISSION);
    String scopeIdentifier = ScopeMapper.fromParams(harnessScopeParams).toString();
    Role role = Role.builder().scopeIdentifier(scopeIdentifier).identifier(identifier).build();
    when(roleService.get(identifier, scopeIdentifier, NO_FILTER)).thenReturn(Optional.of(role));
    RoleResponseDTO roleResponseDTO =
        RoleResponseDTO.builder().role(RoleDTO.builder().identifier(identifier).build()).build();
    when(roleDTOMapper.toResponseDTO(role)).thenReturn(roleResponseDTO);
    ResponseDTO<RoleResponseDTO> response = roleResource.get(identifier, harnessScopeParams);
    assertEquals(roleResponseDTO, response.getData());
    verify(accessControlClient, times(1)).checkForAccessOrThrow(any(), any(), any());
    verify(roleService, times(1)).get(any(), any(), any());
    verify(roleDTOMapper, times(1)).toResponseDTO(any());
  }

  @Test(expected = NotFoundException.class)
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testGetNotFound() {
    String identifier = randomAlphabetic(10);
    doNothing()
        .when(accessControlClient)
        .checkForAccessOrThrow(resourceScope, Resource.of(ROLE, identifier), VIEW_ROLE_PERMISSION);
    String scopeIdentifier = ScopeMapper.fromParams(harnessScopeParams).toString();
    when(roleService.get(identifier, scopeIdentifier, NO_FILTER)).thenReturn(Optional.empty());
    roleResource.get(identifier, harnessScopeParams);
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testUpdate() {
    String identifier = randomAlphabetic(10);
    RoleDTO roleDTO = RoleDTO.builder().identifier(identifier).build();
    doNothing()
        .when(accessControlClient)
        .checkForAccessOrThrow(resourceScope, Resource.of(ROLE, identifier), EDIT_ROLE_PERMISSION);
    RoleResponseDTO roleResponseDTO = RoleResponseDTO.builder().role(roleDTO).build();
    when(transactionTemplate.execute(any())).thenReturn(ResponseDTO.newResponse(roleResponseDTO));
    ResponseDTO<RoleResponseDTO> response = roleResource.update(identifier, harnessScopeParams, roleDTO);
    assertEquals(roleResponseDTO, response.getData());
    verify(accessControlClient, times(1)).checkForAccessOrThrow(any(), any(), any());
    verify(transactionTemplate, times(1)).execute(any());
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testUpdateInvalidIdentifier() {
    String identifier = randomAlphabetic(10);
    RoleDTO roleDTO = RoleDTO.builder().identifier(randomAlphabetic(11)).build();
    doNothing()
        .when(accessControlClient)
        .checkForAccessOrThrow(resourceScope, Resource.of(ROLE, identifier), EDIT_ROLE_PERMISSION);
    roleResource.update(identifier, harnessScopeParams, roleDTO);
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testCreate() {
    String identifier = randomAlphabetic(10);
    RoleDTO roleDTO = RoleDTO.builder().identifier(identifier).build();
    doNothing()
        .when(accessControlClient)
        .checkForAccessOrThrow(resourceScope, Resource.of(ROLE, null), EDIT_ROLE_PERMISSION);
    when(scopeService.getOrCreate(ScopeMapper.fromParams(harnessScopeParams)))
        .thenReturn(Scope.builder().level(HarnessScopeLevel.ORGANIZATION).build());
    RoleResponseDTO roleResponseDTO = RoleResponseDTO.builder().role(roleDTO).build();
    when(transactionTemplate.execute(any())).thenReturn(ResponseDTO.newResponse(roleResponseDTO));
    ResponseDTO<RoleResponseDTO> response = roleResource.create(accountIdentifier, orgIdentifier, null, roleDTO);
    assertEquals(roleResponseDTO, response.getData());
    verify(accessControlClient, times(1)).checkForAccessOrThrow(any(), any(), any());
    verify(scopeService, times(1)).getOrCreate(any());
    verify(transactionTemplate, times(1)).execute(any());
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testDelete() {
    String identifier = randomAlphabetic(10);
    doNothing()
        .when(accessControlClient)
        .checkForAccessOrThrow(resourceScope, Resource.of(ROLE, identifier), EDIT_ROLE_PERMISSION);
    RoleResponseDTO roleResponseDTO =
        RoleResponseDTO.builder().role(RoleDTO.builder().identifier(identifier).build()).build();
    when(transactionTemplate.execute(any())).thenReturn(ResponseDTO.newResponse(roleResponseDTO));
    ResponseDTO<RoleResponseDTO> response = roleResource.delete(identifier, harnessScopeParams);
    assertEquals(roleResponseDTO, response.getData());
    verify(accessControlClient, times(1)).checkForAccessOrThrow(any(), any(), any());
    verify(transactionTemplate, times(1)).execute(any());
  }
}
