/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.roleassignments.api;

import static io.harness.accesscontrol.AccessControlPermissions.EDIT_SERVICEACCOUNT_PERMISSION;
import static io.harness.accesscontrol.AccessControlPermissions.MANAGE_USERGROUP_PERMISSION;
import static io.harness.accesscontrol.AccessControlPermissions.MANAGE_USER_PERMISSION;
import static io.harness.accesscontrol.common.filter.ManagedFilter.NO_FILTER;
import static io.harness.accesscontrol.roleassignments.api.RoleAssignmentDTOMapper.fromDTO;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.ng.beans.PageResponse.getEmptyPageResponse;
import static io.harness.rule.OwnerRule.ASHISHSANODIA;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.fail;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.accesscontrol.AccessControlPermissions;
import io.harness.accesscontrol.AccessControlResourceTypes;
import io.harness.accesscontrol.AccessControlTestBase;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.accesscontrol.common.validation.ValidationResult;
import io.harness.accesscontrol.commons.validation.HarnessActionValidator;
import io.harness.accesscontrol.principals.PrincipalType;
import io.harness.accesscontrol.principals.serviceaccounts.HarnessServiceAccountService;
import io.harness.accesscontrol.principals.serviceaccounts.ServiceAccount;
import io.harness.accesscontrol.principals.serviceaccounts.ServiceAccountService;
import io.harness.accesscontrol.principals.usergroups.HarnessUserGroupService;
import io.harness.accesscontrol.principals.usergroups.UserGroup;
import io.harness.accesscontrol.principals.usergroups.UserGroupService;
import io.harness.accesscontrol.principals.users.HarnessUserService;
import io.harness.accesscontrol.principals.users.User;
import io.harness.accesscontrol.principals.users.UserService;
import io.harness.accesscontrol.resources.resourcegroups.HarnessResourceGroupService;
import io.harness.accesscontrol.resources.resourcegroups.ResourceGroup;
import io.harness.accesscontrol.resources.resourcegroups.ResourceGroupService;
import io.harness.accesscontrol.roleassignments.RoleAssignment;
import io.harness.accesscontrol.roleassignments.RoleAssignmentService;
import io.harness.accesscontrol.roleassignments.persistence.RoleAssignmentDBO.RoleAssignmentDBOKeys;
import io.harness.accesscontrol.scopes.ScopeDTO;
import io.harness.accesscontrol.scopes.core.Scope;
import io.harness.accesscontrol.scopes.core.ScopeService;
import io.harness.accesscontrol.scopes.harness.HarnessScopeParams;
import io.harness.accesscontrol.scopes.harness.HarnessScopeService;
import io.harness.accesscontrol.scopes.harness.ScopeMapper;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SortOrder;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnauthorizedException;
import io.harness.ng.beans.PageRequest;
import io.harness.outbox.api.OutboxService;
import io.harness.rule.Owner;
import io.harness.spec.server.accesscontrol.v1.model.Principal;
import io.harness.spec.server.accesscontrol.v1.model.RoleAssignmentResponse;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import io.serializer.HObjectMapper;
import java.util.List;
import java.util.Optional;
import javax.validation.Validation;
import javax.validation.ValidatorFactory;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.core.Response;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.springframework.transaction.support.TransactionTemplate;

@OwnedBy(PL)
public class ProjectRoleAssignmentsApiImplTest extends AccessControlTestBase {
  private RoleAssignmentService roleAssignmentService;
  private HarnessResourceGroupService harnessResourceGroupService;
  private HarnessUserGroupService harnessUserGroupService;
  private HarnessUserService harnessUserService;
  private HarnessServiceAccountService harnessServiceAccountService;
  private HarnessScopeService harnessScopeService;
  private ScopeService scopeService;
  private ResourceGroupService resourceGroupService;
  private UserGroupService userGroupService;
  private UserService userService;
  private ServiceAccountService serviceAccountService;
  private TransactionTemplate transactionTemplate;
  private HarnessActionValidator<RoleAssignment> actionValidator;
  private AccessControlClient accessControlClient;
  private PageRequest pageRequest;
  private String org;
  private String project;
  private HarnessScopeParams harnessScopeParams;
  private ResourceScope resourceScope;

  private ProjectRoleAssignmentsApiImpl projectRoleAssignmentsApi;
  private RoleAssignmentApiUtils roleAssignmentApiUtils;
  private String account;

  @Before
  public void setup() {
    roleAssignmentService = mock(RoleAssignmentService.class);
    harnessResourceGroupService = mock(HarnessResourceGroupService.class);
    harnessUserGroupService = mock(HarnessUserGroupService.class);
    harnessUserService = mock(HarnessUserService.class);
    harnessServiceAccountService = mock(HarnessServiceAccountService.class);
    harnessScopeService = mock(HarnessScopeService.class);
    scopeService = mock(ScopeService.class);
    resourceGroupService = mock(ResourceGroupService.class);
    userGroupService = mock(UserGroupService.class);
    userService = mock(UserService.class);
    serviceAccountService = mock(ServiceAccountService.class);
    transactionTemplate = mock(TransactionTemplate.class);
    actionValidator = mock(HarnessActionValidator.class);
    accessControlClient = mock(AccessControlClient.class);

    List<SortOrder> sortOrders =
        ImmutableList.of(SortOrder.Builder.aSortOrder()
                             .withField(RoleAssignmentDBOKeys.lastModifiedAt, SortOrder.OrderType.DESC)
                             .build());
    pageRequest = PageRequest.builder().pageIndex(0).pageSize(50).sortOrders(sortOrders).build();

    ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
    roleAssignmentApiUtils = spy(new RoleAssignmentApiUtils(factory.getValidator(), harnessResourceGroupService,
        harnessUserGroupService, harnessUserService, harnessServiceAccountService, harnessScopeService, scopeService,
        resourceGroupService, userGroupService, userService, serviceAccountService,
        new RoleAssignmentDTOMapper(scopeService), accessControlClient));
    projectRoleAssignmentsApi = spy(new ProjectRoleAssignmentsApiImpl(roleAssignmentApiUtils, roleAssignmentService,
        new RoleAssignmentDTOMapper(scopeService), transactionTemplate, mock(OutboxService.class), actionValidator));
    account = randomAlphabetic(10);
    org = randomAlphabetic(10);
    project = randomAlphabetic(10);
    harnessScopeParams =
        HarnessScopeParams.builder().accountIdentifier(account).orgIdentifier(org).projectIdentifier(project).build();
    resourceScope = ResourceScope.builder()
                        .projectIdentifier(harnessScopeParams.getProjectIdentifier())
                        .orgIdentifier(harnessScopeParams.getOrgIdentifier())
                        .accountIdentifier(harnessScopeParams.getAccountIdentifier())
                        .build();
  }

  private void preViewPrincipalPermissions(
      boolean hasViewUserPermission, boolean hasViewUserGroupPermission, boolean hasViewServiceAccountPermission) {
    when(accessControlClient.hasAccess(resourceScope, Resource.of(AccessControlResourceTypes.USER, null),
             AccessControlPermissions.VIEW_USER_PERMISSION))
        .thenReturn(hasViewUserPermission);
    when(accessControlClient.hasAccess(resourceScope, Resource.of(AccessControlResourceTypes.USER_GROUP, null),
             AccessControlPermissions.VIEW_USERGROUP_PERMISSION))
        .thenReturn(hasViewUserGroupPermission);
    when(accessControlClient.hasAccess(resourceScope, Resource.of(AccessControlResourceTypes.SERVICEACCOUNT, null),
             AccessControlPermissions.VIEW_SERVICEACCOUNT_PERMISSION))
        .thenReturn(hasViewServiceAccountPermission);
  }

  private io.harness.spec.server.accesscontrol.v1.model.RoleAssignment getRoleAssignmentRequest(
      Principal.TypeEnum principalType) {
    io.harness.spec.server.accesscontrol.v1.model.RoleAssignment request =
        new io.harness.spec.server.accesscontrol.v1.model.RoleAssignment();
    request.setIdentifier(randomAlphabetic(10));
    request.setRole(randomAlphabetic(10));
    request.setResourceGroup(randomAlphabetic(10));
    request.setManaged(true);
    request.setDisabled(true);
    Principal principal = new Principal();
    principal.setIdentifier(randomAlphabetic(10));
    principal.setScopeLevel("ACCOUNT");
    principal.setType(principalType);
    request.setPrincipal(principal);
    return request;
  }

  private void preSyncDependencies(RoleAssignmentDTO roleAssignmentDTO, boolean scopePresent,
      boolean resourceGroupPresent, boolean principalPresent) {
    Scope scope = ScopeMapper.fromParams(harnessScopeParams);
    when(scopeService.isPresent(scope.toString())).thenReturn(scopePresent);
    if (!scopePresent) {
      doNothing().when(harnessScopeService).sync(scope);
    }
    when(resourceGroupService.get(roleAssignmentDTO.getResourceGroupIdentifier(), scope.toString(), NO_FILTER))
        .thenReturn(resourceGroupPresent ? Optional.of(ResourceGroup.builder().build()) : Optional.empty());
    if (!resourceGroupPresent) {
      doNothing().when(harnessResourceGroupService).sync(roleAssignmentDTO.getResourceGroupIdentifier(), scope);
    }
    if (PrincipalType.USER.equals(roleAssignmentDTO.getPrincipal().getType())) {
      when(userService.get(roleAssignmentDTO.getPrincipal().getIdentifier(), scope.toString()))
          .thenReturn(principalPresent ? Optional.of(User.builder().build()) : Optional.empty());
      if (!principalPresent) {
        doNothing().when(harnessUserService).sync(roleAssignmentDTO.getPrincipal().getIdentifier(), scope);
      }
    } else if (PrincipalType.SERVICE_ACCOUNT.equals(roleAssignmentDTO.getPrincipal().getType())) {
      when(serviceAccountService.get(roleAssignmentDTO.getPrincipal().getIdentifier(), scope.toString()))
          .thenReturn(principalPresent ? Optional.of(ServiceAccount.builder().build()) : Optional.empty());
      if (!principalPresent) {
        doNothing().when(harnessServiceAccountService).sync(roleAssignmentDTO.getPrincipal().getIdentifier(), scope);
      }
    } else if (PrincipalType.USER_GROUP.equals(roleAssignmentDTO.getPrincipal().getType())) {
      when(userGroupService.get(roleAssignmentDTO.getPrincipal().getIdentifier(), scope.toString()))
          .thenReturn(principalPresent ? Optional.of(UserGroup.builder().build()) : Optional.empty());
      if (!principalPresent) {
        doNothing().when(harnessUserGroupService).sync(roleAssignmentDTO.getPrincipal().getIdentifier(), scope);
      }
    }
  }

  private void preCheckUpdatePermission(RoleAssignmentDTO roleAssignmentDTO) {
    if (PrincipalType.USER.equals(roleAssignmentDTO.getPrincipal().getType())) {
      doNothing()
          .when(accessControlClient)
          .checkForAccessOrThrow(ResourceScope.of(harnessScopeParams.getAccountIdentifier(),
                                     harnessScopeParams.getOrgIdentifier(), harnessScopeParams.getProjectIdentifier()),
              Resource.of(AccessControlResourceTypes.USER, roleAssignmentDTO.getPrincipal().getIdentifier()),
              MANAGE_USER_PERMISSION);
    } else if (PrincipalType.SERVICE_ACCOUNT.equals(roleAssignmentDTO.getPrincipal().getType())) {
      doNothing()
          .when(accessControlClient)
          .checkForAccessOrThrow(ResourceScope.of(harnessScopeParams.getAccountIdentifier(),
                                     harnessScopeParams.getOrgIdentifier(), harnessScopeParams.getProjectIdentifier()),
              Resource.of(AccessControlResourceTypes.SERVICEACCOUNT, roleAssignmentDTO.getPrincipal().getIdentifier()),
              EDIT_SERVICEACCOUNT_PERMISSION);
    } else if (PrincipalType.USER_GROUP.equals(roleAssignmentDTO.getPrincipal().getType())) {
      doNothing()
          .when(accessControlClient)
          .checkForAccessOrThrow(ResourceScope.of(harnessScopeParams.getAccountIdentifier(),
                                     harnessScopeParams.getOrgIdentifier(), harnessScopeParams.getProjectIdentifier()),
              Resource.of(AccessControlResourceTypes.USER_GROUP, roleAssignmentDTO.getPrincipal().getIdentifier()),
              MANAGE_USERGROUP_PERMISSION);
    }
  }

  private void assertSyncDependencies(int invocations, RoleAssignmentDTO roleAssignmentDTO, boolean scopePresent,
      boolean resourceGroupPresent, boolean principalPresent) {
    verify(scopeService, times(invocations)).isPresent(any());
    if (!scopePresent) {
      verify(harnessScopeService, times(invocations)).sync(any());
    }
    verify(resourceGroupService, times(invocations)).get(any(), any(), any());
    if (!resourceGroupPresent) {
      verify(harnessResourceGroupService, times(invocations)).sync(any(), any());
    }
    if (PrincipalType.USER.equals(roleAssignmentDTO.getPrincipal().getType())) {
      verify(userService, times(invocations)).get(any(), any());
      if (!principalPresent) {
        verify(harnessUserService, times(invocations)).sync(any(), any());
      }
    } else if (PrincipalType.SERVICE_ACCOUNT.equals(roleAssignmentDTO.getPrincipal().getType())) {
      verify(serviceAccountService, times(invocations)).get(any(), any());
      if (!principalPresent) {
        verify(harnessServiceAccountService, times(invocations)).sync(any(), any());
      }
    } else if (PrincipalType.USER_GROUP.equals(roleAssignmentDTO.getPrincipal().getType())) {
      verify(userGroupService, times(invocations)).get(any(), any());
      if (!principalPresent) {
        verify(harnessUserGroupService, times(invocations)).sync(any(), any());
      }
    }
  }

  private void assertSyncDependencies(RoleAssignmentDTO roleAssignmentDTO, boolean scopePresent,
      boolean resourceGroupPresent, boolean principalPresent) {
    assertSyncDependencies(1, roleAssignmentDTO, scopePresent, resourceGroupPresent, principalPresent);
  }

  private void assertCheckUpdatePermission(int invocations, RoleAssignmentDTO roleAssignmentDTO) {
    List<PrincipalType> principalTypes =
        Lists.newArrayList(PrincipalType.USER, PrincipalType.SERVICE_ACCOUNT, PrincipalType.USER_GROUP);
    if (principalTypes.contains(roleAssignmentDTO.getPrincipal().getType())) {
      verify(accessControlClient, times(invocations)).checkForAccessOrThrow(any(), any(), any());
    } else {
      verify(accessControlClient, times(0)).checkForAccessOrThrow(any(), any(), any());
    }
  }

  private void assertCheckUpdatePermission(RoleAssignmentDTO roleAssignmentDTO) {
    assertCheckUpdatePermission(1, roleAssignmentDTO);
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testCreate() {
    io.harness.spec.server.accesscontrol.v1.model.RoleAssignment request =
        getRoleAssignmentRequest(Principal.TypeEnum.USER);
    RoleAssignmentDTO roleAssignmentDTO = roleAssignmentApiUtils.getRoleAssignmentDto(request);
    RoleAssignmentDTO roleAssignmentDTOClone = (RoleAssignmentDTO) HObjectMapper.clone(roleAssignmentDTO);
    preSyncDependencies(roleAssignmentDTO, true, true, true);
    preCheckUpdatePermission(roleAssignmentDTO);
    when(transactionTemplate.execute(any()))
        .thenReturn(RoleAssignmentResponseDTO.builder().roleAssignment(roleAssignmentDTO).build());
    projectRoleAssignmentsApi.createProjectScopedRoleAssignments(request, org, project, account);
    assertSyncDependencies(roleAssignmentDTOClone, true, true, true);
    assertCheckUpdatePermission(roleAssignmentDTOClone);
    verify(transactionTemplate, times(1)).execute(any());
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testCreateSyncDependencies() {
    io.harness.spec.server.accesscontrol.v1.model.RoleAssignment request =
        getRoleAssignmentRequest(Principal.TypeEnum.USER);
    RoleAssignmentDTO roleAssignmentDTO = roleAssignmentApiUtils.getRoleAssignmentDto(request);
    RoleAssignmentDTO roleAssignmentDTOClone = (RoleAssignmentDTO) HObjectMapper.clone(roleAssignmentDTO);
    preSyncDependencies(roleAssignmentDTO, false, false, false);
    preCheckUpdatePermission(roleAssignmentDTO);
    when(transactionTemplate.execute(any()))
        .thenReturn(RoleAssignmentResponseDTO.builder().roleAssignment(roleAssignmentDTO).build());
    projectRoleAssignmentsApi.createProjectScopedRoleAssignments(request, org, project, account);
    assertSyncDependencies(roleAssignmentDTOClone, false, false, false);
    assertCheckUpdatePermission(roleAssignmentDTOClone);
    verify(transactionTemplate, times(1)).execute(any());
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testCreateInvalidPrincipal() {
    io.harness.spec.server.accesscontrol.v1.model.RoleAssignment request =
        getRoleAssignmentRequest(Principal.TypeEnum.SERVICE);
    RoleAssignmentDTO roleAssignmentDTO = roleAssignmentApiUtils.getRoleAssignmentDto(request);
    RoleAssignmentDTO roleAssignmentDTOClone = (RoleAssignmentDTO) HObjectMapper.clone(roleAssignmentDTO);
    preSyncDependencies(roleAssignmentDTO, false, false, false);
    preCheckUpdatePermission(roleAssignmentDTO);
    try {
      projectRoleAssignmentsApi.createProjectScopedRoleAssignments(request, org, project, account);
      fail();
    } catch (InvalidRequestException invalidRequestException) {
      assertSyncDependencies(roleAssignmentDTOClone, true, true, true);
      assertCheckUpdatePermission(roleAssignmentDTOClone);
      verify(transactionTemplate, times(0)).execute(any());
    }
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testGetList() {
    preViewPrincipalPermissions(true, true, true);
    when(roleAssignmentService.list(any(), any())).thenReturn(getEmptyPageResponse(pageRequest));

    Response accountScopedRoleAssignments = projectRoleAssignmentsApi.getProjectScopedRoleAssignments(
        org, project, account, pageRequest.getPageIndex(), pageRequest.getPageSize(), "created", "DESC");

    verify(accessControlClient, times(3)).hasAccess(any(ResourceScope.class), any(), any());
    verify(roleAssignmentService, times(1)).list(any(), any());
    List<RoleAssignmentResponse> entity = (List<RoleAssignmentResponse>) accountScopedRoleAssignments.getEntity();
    assertEquals(0, entity.size());
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testGetMissingViewPrincipalPermissions() {
    preViewPrincipalPermissions(false, false, false);

    try {
      projectRoleAssignmentsApi.getProjectScopedRoleAssignments(
          org, project, account, pageRequest.getPageIndex(), pageRequest.getPageSize(), "identifier", "DESC");
      fail();
    } catch (UnauthorizedException unauthorizedException) {
      verify(accessControlClient, times(3)).hasAccess(any(ResourceScope.class), any(), any());
      verify(roleAssignmentService, times(0)).list(any(), any());
    }
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testGet() {
    io.harness.spec.server.accesscontrol.v1.model.RoleAssignment request =
        getRoleAssignmentRequest(Principal.TypeEnum.USER);
    RoleAssignmentDTO roleAssignmentDTO = roleAssignmentApiUtils.getRoleAssignmentDto(request);
    Scope scope = ScopeMapper.fromParams(harnessScopeParams);
    RoleAssignment roleAssignment = fromDTO(scope, roleAssignmentDTO);
    when(roleAssignmentService.get(any(), any())).thenReturn(Optional.of(roleAssignment));

    preViewPrincipalPermissions(true, true, true);

    Response accountScopedRoleAssignments =
        projectRoleAssignmentsApi.getProjectScopedRoleAssignment(roleAssignment.getIdentifier(), org, project, account);

    verify(accessControlClient, times(1)).hasAccess(any(ResourceScope.class), any(), any());
    verify(roleAssignmentService, times(1)).get(any(), any());
    RoleAssignmentResponse entity = (RoleAssignmentResponse) accountScopedRoleAssignments.getEntity();
    assertNotNull(entity.getRoleAssignment());
    assertEquals(entity.getRoleAssignment().getIdentifier(), roleAssignment.getIdentifier());
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testDelete() {
    io.harness.spec.server.accesscontrol.v1.model.RoleAssignment request =
        getRoleAssignmentRequest(Principal.TypeEnum.USER);
    RoleAssignmentDTO roleAssignmentDTO = roleAssignmentApiUtils.getRoleAssignmentDto(request);
    Scope scope = ScopeMapper.fromParams(harnessScopeParams);
    RoleAssignment roleAssignment = fromDTO(scope, roleAssignmentDTO);
    when(roleAssignmentService.get(roleAssignment.getIdentifier(), roleAssignment.getScopeIdentifier()))
        .thenReturn(Optional.of(roleAssignment));
    preCheckUpdatePermission(roleAssignmentDTO);
    ValidationResult validResult = ValidationResult.VALID;
    when(actionValidator.canDelete(roleAssignment)).thenReturn(validResult);
    RoleAssignmentResponseDTO roleAssignmentResponseDTO =
        RoleAssignmentResponseDTO.builder()
            .roleAssignment(roleAssignmentDTO)
            .scope(ScopeDTO.builder().accountIdentifier(account).build())
            .harnessManaged(false)
            .createdAt(1234567890L)
            .lastModifiedAt(1234567890L)
            .build();
    when(transactionTemplate.execute(any())).thenReturn(roleAssignmentResponseDTO);

    projectRoleAssignmentsApi.deleteProjectScopedRoleAssignment(request.getIdentifier(), org, project, account);

    verify(roleAssignmentService, times(1)).get(any(), any());
    assertCheckUpdatePermission(roleAssignmentDTO);
    verify(actionValidator, times(1)).canDelete(any());
    verify(transactionTemplate, times(1)).execute(any());
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testDeleteNotFound() {
    io.harness.spec.server.accesscontrol.v1.model.RoleAssignment request =
        getRoleAssignmentRequest(Principal.TypeEnum.USER);
    RoleAssignmentDTO roleAssignmentDTO = roleAssignmentApiUtils.getRoleAssignmentDto(request);
    Scope scope = ScopeMapper.fromParams(harnessScopeParams);
    RoleAssignment roleAssignment = fromDTO(scope, roleAssignmentDTO);
    when(roleAssignmentService.get(roleAssignment.getIdentifier(), roleAssignment.getScopeIdentifier()))
        .thenReturn(Optional.empty());
    try {
      projectRoleAssignmentsApi.deleteProjectScopedRoleAssignment(request.getIdentifier(), org, project, account);
      fail();
    } catch (NotFoundException notFoundException) {
      verify(roleAssignmentService, times(1)).get(any(), any());
    }
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testDeleteInvalidPrincipal() {
    io.harness.spec.server.accesscontrol.v1.model.RoleAssignment request =
        getRoleAssignmentRequest(Principal.TypeEnum.SERVICE);
    RoleAssignmentDTO roleAssignmentDTO = roleAssignmentApiUtils.getRoleAssignmentDto(request);
    Scope scope = ScopeMapper.fromParams(harnessScopeParams);
    RoleAssignment roleAssignment = fromDTO(scope, roleAssignmentDTO);
    when(roleAssignmentService.get(roleAssignment.getIdentifier(), roleAssignment.getScopeIdentifier()))
        .thenReturn(Optional.of(roleAssignment));
    preCheckUpdatePermission(roleAssignmentDTO);
    try {
      projectRoleAssignmentsApi.deleteProjectScopedRoleAssignment(request.getIdentifier(), org, project, account);
      fail();
    } catch (InvalidRequestException invalidRequestException) {
      verify(roleAssignmentService, times(1)).get(any(), any());
      assertCheckUpdatePermission(roleAssignmentDTO);
      verify(transactionTemplate, times(0)).execute(any());
    }
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testDeleteActionNotAllowed() {
    io.harness.spec.server.accesscontrol.v1.model.RoleAssignment request =
        getRoleAssignmentRequest(Principal.TypeEnum.USER);
    RoleAssignmentDTO roleAssignmentDTO = roleAssignmentApiUtils.getRoleAssignmentDto(request);
    Scope scope = ScopeMapper.fromParams(harnessScopeParams);
    RoleAssignment roleAssignment = fromDTO(scope, roleAssignmentDTO);
    when(roleAssignmentService.get(roleAssignment.getIdentifier(), roleAssignment.getScopeIdentifier()))
        .thenReturn(Optional.of(roleAssignment));
    preCheckUpdatePermission(roleAssignmentDTO);
    ValidationResult invalidResult = ValidationResult.builder().valid(false).errorMessage("").build();
    when(actionValidator.canDelete(roleAssignment)).thenReturn(invalidResult);
    try {
      projectRoleAssignmentsApi.deleteProjectScopedRoleAssignment(request.getIdentifier(), org, project, account);
      fail();
    } catch (InvalidRequestException invalidRequestException) {
      verify(roleAssignmentService, times(1)).get(any(), any());
      assertCheckUpdatePermission(roleAssignmentDTO);
      verify(actionValidator, times(1)).canDelete(any());
      verify(transactionTemplate, times(0)).execute(any());
    }
  }
}
