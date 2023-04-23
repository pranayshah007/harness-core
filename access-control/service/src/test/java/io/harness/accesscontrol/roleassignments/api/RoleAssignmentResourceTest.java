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
import static io.harness.accesscontrol.common.filter.ManagedFilter.buildFromSet;
import static io.harness.accesscontrol.principals.PrincipalType.SERVICE_ACCOUNT;
import static io.harness.accesscontrol.principals.PrincipalType.USER;
import static io.harness.accesscontrol.principals.PrincipalType.USER_GROUP;
import static io.harness.accesscontrol.roleassignments.api.RoleAssignmentDTOMapper.fromDTO;
import static io.harness.accesscontrol.scopes.harness.ScopeMapper.fromParams;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.ng.beans.PageResponse.getEmptyPageResponse;
import static io.harness.rule.OwnerRule.ASHISHSANODIA;
import static io.harness.rule.OwnerRule.JIMIT_GANDHI;
import static io.harness.rule.OwnerRule.KARAN;
import static io.harness.rule.OwnerRule.MEENAKSHI;
import static io.harness.rule.OwnerRule.REETIKA;

import static javax.validation.Validation.buildDefaultValidatorFactory;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;
import static junit.framework.TestCase.fail;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.accesscontrol.AccessControlPermissions;
import io.harness.accesscontrol.AccessControlResourceTypes;
import io.harness.accesscontrol.AccessControlTestBase;
import io.harness.accesscontrol.NGAccessDeniedException;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.accesscontrol.common.filter.ManagedFilter;
import io.harness.accesscontrol.common.validation.ValidationResult;
import io.harness.accesscontrol.commons.validation.HarnessActionValidator;
import io.harness.accesscontrol.principals.Principal;
import io.harness.accesscontrol.principals.PrincipalDTO;
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
import io.harness.accesscontrol.roleassignments.RoleAssignmentFilter;
import io.harness.accesscontrol.roleassignments.RoleAssignmentService;
import io.harness.accesscontrol.roleassignments.validator.RoleAssignmentValidationResult;
import io.harness.accesscontrol.roles.RoleService;
import io.harness.accesscontrol.roles.api.RoleDTOMapper;
import io.harness.accesscontrol.roles.filter.RoleFilter;
import io.harness.accesscontrol.scopes.ScopeDTO;
import io.harness.accesscontrol.scopes.core.Scope;
import io.harness.accesscontrol.scopes.core.ScopeService;
import io.harness.accesscontrol.scopes.harness.HarnessScopeParams;
import io.harness.accesscontrol.scopes.harness.HarnessScopeService;
import io.harness.accesscontrol.scopes.harness.ScopeMapper;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnauthorizedException;
import io.harness.ng.beans.PageRequest;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.outbox.api.OutboxService;
import io.harness.reflection.ReflectionUtils;
import io.harness.rule.Owner;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import io.serializer.HObjectMapper;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import javax.ws.rs.NotFoundException;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

@OwnedBy(PL)
public class RoleAssignmentResourceTest extends AccessControlTestBase {
  private RoleAssignmentService roleAssignmentService;
  private HarnessResourceGroupService harnessResourceGroupService;
  private HarnessUserGroupService harnessUserGroupService;
  private HarnessUserService harnessUserService;
  private HarnessServiceAccountService harnessServiceAccountService;
  private HarnessScopeService harnessScopeService;
  private ScopeService scopeService;
  private RoleService roleService;
  private ResourceGroupService resourceGroupService;
  private UserGroupService userGroupService;
  private UserService userService;
  private ServiceAccountService serviceAccountService;
  private TransactionTemplate transactionTemplate;
  private HarnessActionValidator<RoleAssignment> actionValidator;
  private AccessControlClient accessControlClient;
  private RoleAssignmentResourceImpl roleAssignmentResource;
  private PageRequest pageRequest;
  private HarnessScopeParams harnessScopeParams;
  private ResourceScope resourceScope;

  private RoleAssignmentDTOMapper roleAssignmentDTOMapper;

  @Before
  public void setup() {
    roleAssignmentService = mock(RoleAssignmentService.class);
    harnessResourceGroupService = mock(HarnessResourceGroupService.class);
    harnessUserGroupService = mock(HarnessUserGroupService.class);
    harnessUserService = mock(HarnessUserService.class);
    harnessServiceAccountService = mock(HarnessServiceAccountService.class);
    harnessScopeService = mock(HarnessScopeService.class);
    scopeService = mock(ScopeService.class);
    roleService = mock(RoleService.class);
    resourceGroupService = mock(ResourceGroupService.class);
    userGroupService = mock(UserGroupService.class);
    userService = mock(UserService.class);
    serviceAccountService = mock(ServiceAccountService.class);
    transactionTemplate = mock(TransactionTemplate.class);
    actionValidator = mock(HarnessActionValidator.class);
    accessControlClient = mock(AccessControlClient.class);
    roleAssignmentDTOMapper = mock(RoleAssignmentDTOMapper.class);
    RoleAssignmentApiUtils roleAssignmentApiUtils =
        spy(new RoleAssignmentApiUtils(buildDefaultValidatorFactory().getValidator(), harnessResourceGroupService,
            harnessUserGroupService, harnessUserService, harnessServiceAccountService, harnessScopeService,
            scopeService, resourceGroupService, userGroupService, userService, serviceAccountService,
            mock(RoleAssignmentDTOMapper.class), accessControlClient));
    roleAssignmentResource = spy(new RoleAssignmentResourceImpl(roleAssignmentService, harnessResourceGroupService,
        scopeService, roleService, resourceGroupService, userGroupService, userService, roleAssignmentDTOMapper,
        mock(RoleAssignmentAggregateMapper.class), mock(RoleDTOMapper.class), transactionTemplate, actionValidator,
        mock(OutboxService.class), roleAssignmentApiUtils));
    pageRequest = PageRequest.builder().pageIndex(0).pageSize(50).build();
    harnessScopeParams = HarnessScopeParams.builder()
                             .accountIdentifier(randomAlphabetic(10))
                             .orgIdentifier(randomAlphabetic(10))
                             .build();
    resourceScope = ResourceScope.builder()
                        .projectIdentifier(harnessScopeParams.getProjectIdentifier())
                        .orgIdentifier(harnessScopeParams.getOrgIdentifier())
                        .accountIdentifier(harnessScopeParams.getAccountIdentifier())
                        .build();
  }

  private RoleAssignmentDTO getRoleAssignmentDTO() {
    return getRoleAssignmentDTO(PrincipalType.USER);
  }

  private RoleAssignmentDTO getRoleAssignmentDTO(PrincipalType principalType) {
    return RoleAssignmentDTO.builder()
        .identifier(randomAlphabetic(10))
        .roleIdentifier(randomAlphabetic(10))
        .resourceGroupIdentifier(randomAlphabetic(10))
        .principal(PrincipalDTO.builder().identifier(randomAlphabetic(10)).type(principalType).build())
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

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testGet() {
    preViewPrincipalPermissions(true, true, true);
    Set<PrincipalType> principalTypes =
        Sets.newHashSet(PrincipalType.USER, PrincipalType.USER_GROUP, PrincipalType.SERVICE_ACCOUNT);
    when(roleAssignmentService.list(pageRequest,
             RoleAssignmentFilter.builder()
                 .scopeFilter(ScopeMapper.fromParams(harnessScopeParams).toString())
                 .principalTypeFilter(principalTypes)
                 .build()))
        .thenReturn(getEmptyPageResponse(pageRequest));

    ResponseDTO<PageResponse<RoleAssignmentResponseDTO>> responseDTO =
        roleAssignmentResource.get(pageRequest, harnessScopeParams);

    verify(accessControlClient, times(3)).hasAccess(any(ResourceScope.class), any(), any());
    verify(roleAssignmentService, times(1)).list(any(), any());
    assertEquals(0, responseDTO.getData().getContent().size());
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testGetMissingViewPrincipalPermissions() {
    preViewPrincipalPermissions(false, false, false);

    try {
      roleAssignmentResource.get(pageRequest, harnessScopeParams);
      fail();
    } catch (UnauthorizedException unauthorizedException) {
      verify(accessControlClient, times(3)).hasAccess(any(ResourceScope.class), any(), any());
      verify(roleAssignmentService, times(0)).list(any(), any());
    }
  }

  private RoleAssignmentFilterDTO getRoleAssignmentFilterDTO() {
    Set<String> resourceGroupFilter = Sets.newHashSet(randomAlphabetic(10), randomAlphabetic(10));
    Set<String> roleFilter = Sets.newHashSet(randomAlphabetic(10), randomAlphabetic(10));
    Set<PrincipalType> principalTypeFilter = Sets.newHashSet(PrincipalType.USER);
    Set<PrincipalDTO> principalFilter = Sets.newHashSet(
        PrincipalDTO.builder().type(PrincipalType.SERVICE_ACCOUNT).identifier(randomAlphabetic(10)).build());
    Set<Boolean> harnessManagedFilter = Sets.newHashSet(Boolean.TRUE);
    Set<Boolean> disabledFilter = Sets.newHashSet(Boolean.TRUE);
    return RoleAssignmentFilterDTO.builder()
        .resourceGroupFilter(resourceGroupFilter)
        .roleFilter(roleFilter)
        .principalTypeFilter(principalTypeFilter)
        .principalFilter(principalFilter)
        .harnessManagedFilter(harnessManagedFilter)
        .disabledFilter(disabledFilter)
        .build();
  }

  private void assertFilter(RoleAssignmentFilterDTO roleAssignmentFilterDTO, Set<PrincipalType> expectedPrincipalTypes,
      Set<PrincipalDTO> expectedPrincipals, RoleAssignmentFilter roleAssignmentFilter) {
    assertFalse(roleAssignmentFilter.isIncludeChildScopes());
    assertEquals(expectedPrincipalTypes, roleAssignmentFilter.getPrincipalTypeFilter());
    assertEquals(expectedPrincipals.size(), roleAssignmentFilter.getPrincipalFilter().size());
    expectedPrincipals.forEach(principalDTO -> {
      Principal principal = Principal.builder()
                                .principalType(principalDTO.getType())
                                .principalIdentifier(principalDTO.getIdentifier())
                                .build();
      assertTrue(roleAssignmentFilter.getPrincipalFilter().contains(principal));
    });
    assertEquals(roleAssignmentFilterDTO.getDisabledFilter(), roleAssignmentFilter.getDisabledFilter());
    ManagedFilter managedFilter = Objects.isNull(roleAssignmentFilterDTO.getHarnessManagedFilter())
        ? ManagedFilter.NO_FILTER
        : buildFromSet(roleAssignmentFilterDTO.getHarnessManagedFilter());
    assertEquals(managedFilter, roleAssignmentFilter.getManagedFilter());
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testGetFilter() {
    RoleAssignmentFilterDTO roleAssignmentFilterDTO = getRoleAssignmentFilterDTO();
    testGetFilterInternal(roleAssignmentFilterDTO);
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testGetFilterWithInternalRoles() {
    RoleAssignmentFilterDTO roleAssignmentFilterDTO = getRoleAssignmentFilterDTO();
    RoleAssignmentFilterDTO roleAssignmentFilterDTOClone =
        (RoleAssignmentFilterDTO) HObjectMapper.clone(roleAssignmentFilterDTO);
    preViewPrincipalPermissions(true, true, true);

    ArgumentCaptor<RoleAssignmentFilter> roleAssignmentFilterArgumentCaptor =
        ArgumentCaptor.forClass(RoleAssignmentFilter.class);
    when(roleAssignmentService.list(eq(pageRequest), any(), anyBoolean()))
        .thenReturn(getEmptyPageResponse(pageRequest));

    ResponseDTO<PageResponse<RoleAssignmentResponseDTO>> responseDTO =
        roleAssignmentResource.getFilteredRoleAssignmentsWithInternalRoles(
            pageRequest, harnessScopeParams, roleAssignmentFilterDTO);
    assertEquals(0, responseDTO.getData().getContent().size());

    verify(accessControlClient, times(3)).hasAccess(any(ResourceScope.class), any(), any());
    verify(roleAssignmentService, times(1))
        .list(eq(pageRequest), roleAssignmentFilterArgumentCaptor.capture(), eq(false));
    RoleAssignmentFilter roleAssignmentFilter = roleAssignmentFilterArgumentCaptor.getValue();
    assertFilter(roleAssignmentFilterDTOClone, roleAssignmentFilterDTOClone.getPrincipalTypeFilter(),
        roleAssignmentFilterDTOClone.getPrincipalFilter(), roleAssignmentFilter);
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testGetFilterMissingViewPrincipalPermissions() {
    RoleAssignmentFilterDTO roleAssignmentFilterDTO = getRoleAssignmentFilterDTO();
    testGetFilterMissingViewPrincipalPermissionsInternal(roleAssignmentFilterDTO);
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testGetFilterWithPrincipalTypes() throws IllegalAccessException {
    RoleAssignmentFilterDTO roleAssignmentFilterDTO = getRoleAssignmentFilterDTO();
    ReflectionUtils.setObjectField(ReflectionUtils.getFieldByName(RoleAssignmentFilterDTO.class, "principalFilter"),
        roleAssignmentFilterDTO, new HashSet<>());
    testGetFilterInternal(roleAssignmentFilterDTO);
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testGetFilterWithPrincipalTypesMissingViewPrincipalPermissions() throws IllegalAccessException {
    RoleAssignmentFilterDTO roleAssignmentFilterDTO = getRoleAssignmentFilterDTO();
    ReflectionUtils.setObjectField(ReflectionUtils.getFieldByName(RoleAssignmentFilterDTO.class, "principalFilter"),
        roleAssignmentFilterDTO, new HashSet<>());
    testGetFilterMissingViewPrincipalPermissionsInternal(roleAssignmentFilterDTO);
  }

  private void testGetFilterMissingViewPrincipalPermissionsInternal(RoleAssignmentFilterDTO roleAssignmentFilterDTO) {
    preViewPrincipalPermissions(false, false, false);
    try {
      roleAssignmentResource.get(pageRequest, harnessScopeParams, roleAssignmentFilterDTO);
      fail();
    } catch (UnauthorizedException unauthorizedException) {
      verify(accessControlClient, times(3)).hasAccess(any(ResourceScope.class), any(), any());
      verify(roleAssignmentService, times(0)).list(any(), any());
    }
  }

  private void testGetFilterInternal(RoleAssignmentFilterDTO roleAssignmentFilterDTO) {
    RoleAssignmentFilterDTO roleAssignmentFilterDTOClone =
        (RoleAssignmentFilterDTO) HObjectMapper.clone(roleAssignmentFilterDTO);
    preViewPrincipalPermissions(true, true, true);

    ArgumentCaptor<RoleAssignmentFilter> roleAssignmentFilterArgumentCaptor =
        ArgumentCaptor.forClass(RoleAssignmentFilter.class);
    when(roleAssignmentService.list(eq(pageRequest), any())).thenReturn(getEmptyPageResponse(pageRequest));

    ResponseDTO<PageResponse<RoleAssignmentResponseDTO>> responseDTO =
        roleAssignmentResource.get(pageRequest, harnessScopeParams, roleAssignmentFilterDTO);
    assertEquals(0, responseDTO.getData().getContent().size());

    verify(accessControlClient, times(3)).hasAccess(any(ResourceScope.class), any(), any());
    verify(roleAssignmentService, times(1)).list(eq(pageRequest), roleAssignmentFilterArgumentCaptor.capture());
    RoleAssignmentFilter roleAssignmentFilter = roleAssignmentFilterArgumentCaptor.getValue();
    assertFilter(roleAssignmentFilterDTOClone, roleAssignmentFilterDTOClone.getPrincipalTypeFilter(),
        roleAssignmentFilterDTOClone.getPrincipalFilter(), roleAssignmentFilter);
  }

  private void testGetAggregatedInternal(RoleAssignmentFilterDTO roleAssignmentFilterDTO) {
    Scope scope = ScopeMapper.fromParams(harnessScopeParams);
    Scope scopeClone = ScopeMapper.fromParams((HarnessScopeParams) HObjectMapper.clone(harnessScopeParams));
    RoleAssignmentFilterDTO roleAssignmentFilterDTOClone =
        (RoleAssignmentFilterDTO) HObjectMapper.clone(roleAssignmentFilterDTO);
    preViewPrincipalPermissions(true, true, true);

    ArgumentCaptor<RoleAssignmentFilter> roleAssignmentFilterArgumentCaptor =
        ArgumentCaptor.forClass(RoleAssignmentFilter.class);
    PageRequest maxPageRequest = PageRequest.builder().pageSize(1000).build();
    when(roleAssignmentService.list(eq(maxPageRequest), any())).thenReturn(getEmptyPageResponse(maxPageRequest));
    RoleFilter roleFilter = RoleFilter.builder()
                                .identifierFilter(new HashSet<>())
                                .scopeIdentifier(scope.toString())
                                .managedFilter(NO_FILTER)
                                .build();
    RoleFilter roleFilterClone = (RoleFilter) HObjectMapper.clone(roleFilter);
    when(roleService.list(maxPageRequest, roleFilter, true)).thenReturn(getEmptyPageResponse(maxPageRequest));
    when(resourceGroupService.list(new ArrayList<>(), scope.toString(), NO_FILTER)).thenReturn(new ArrayList<>());

    ResponseDTO<RoleAssignmentAggregateResponseDTO> responseDTO =
        roleAssignmentResource.getAggregated(harnessScopeParams, roleAssignmentFilterDTO);
    assertEquals(0, responseDTO.getData().getRoleAssignments().size());
    assertEquals(0, responseDTO.getData().getResourceGroups().size());
    assertEquals(0, responseDTO.getData().getRoles().size());
    assertEquals(ScopeMapper.toDTO(scope), responseDTO.getData().getScope());

    verify(accessControlClient, times(3)).hasAccess(any(ResourceScope.class), any(), any());
    verify(roleAssignmentService, times(1)).list(eq(maxPageRequest), roleAssignmentFilterArgumentCaptor.capture());
    verify(roleService, times(1)).list(maxPageRequest, roleFilterClone, true);
    verify(resourceGroupService, times(1)).list(new ArrayList<>(), scopeClone.toString(), NO_FILTER);
    RoleAssignmentFilter roleAssignmentFilter = roleAssignmentFilterArgumentCaptor.getValue();
    assertFilter(roleAssignmentFilterDTOClone, roleAssignmentFilterDTOClone.getPrincipalTypeFilter(),
        roleAssignmentFilterDTOClone.getPrincipalFilter(), roleAssignmentFilter);
  }

  private void testGetAggregatedV2Internal(RoleAssignmentFilterV2 roleAssignmentFilterV2DTO) {
    Scope scope = ScopeMapper.fromParams(harnessScopeParams);
    preViewPrincipalPermissions(true, false, false);
    boolean isUserPrincipal = roleAssignmentFilterV2DTO.getPrincipalFilter() != null
        && USER.equals(roleAssignmentFilterV2DTO.getPrincipalFilter().getType());
    if (isUserPrincipal) {
      when(userGroupService.list(roleAssignmentFilterV2DTO.getPrincipalFilter().getIdentifier()))
          .thenReturn(Lists.newArrayList(UserGroup.builder().build()));
    }
    ArgumentCaptor<RoleAssignmentFilter> roleAssignmentFilterArgumentCaptor =
        ArgumentCaptor.forClass(RoleAssignmentFilter.class);
    PageRequest maxPageRequest = PageRequest.builder().pageSize(1000).build();
    when(roleAssignmentService.list(eq(maxPageRequest), any())).thenReturn(getEmptyPageResponse(maxPageRequest));
    RoleFilter roleFilter = RoleFilter.builder()
                                .identifierFilter(new HashSet<>())
                                .scopeIdentifier(scope.toString())
                                .managedFilter(NO_FILTER)
                                .build();
    when(roleService.list(maxPageRequest, roleFilter, true)).thenReturn(getEmptyPageResponse(maxPageRequest));
    when(resourceGroupService.list(new ArrayList<>(), scope.toString(), NO_FILTER)).thenReturn(new ArrayList<>());

    ResponseDTO<PageResponse<RoleAssignmentAggregate>> responseDTO =
        roleAssignmentResource.getList(maxPageRequest, harnessScopeParams, roleAssignmentFilterV2DTO);
    if (isUserPrincipal) {
      verify(userGroupService, times(1)).list(roleAssignmentFilterV2DTO.getPrincipalFilter().getIdentifier());
    }
    verify(accessControlClient, times(3)).hasAccess(any(ResourceScope.class), any(), any());
    verify(roleAssignmentService, times(1)).list(eq(maxPageRequest), roleAssignmentFilterArgumentCaptor.capture());
  }

  private void testGetAggregatedMissingViewPrincipalPermissionsInternal(
      RoleAssignmentFilterDTO roleAssignmentFilterDTO) {
    testGetFilterMissingViewPrincipalPermissionsInternal(roleAssignmentFilterDTO);
    verify(roleService, times(0)).list(any(), any(), eq(true));
    verify(resourceGroupService, times(0)).list(any(List.class), any(), any());
  }

  @Test
  @Owner(developers = REETIKA)
  @Category(UnitTests.class)
  public void testGetAggregatedV2WithNoPrincipalFilter() {
    Set<String> resourceGroupFilter = Sets.newHashSet(randomAlphabetic(10), randomAlphabetic(10));
    Set<String> roleFilter = Sets.newHashSet(randomAlphabetic(10), randomAlphabetic(10));
    RoleAssignmentFilterV2 roleAssignmentFilterV2 =
        RoleAssignmentFilterV2.builder().resourceGroupFilter(resourceGroupFilter).roleFilter(roleFilter).build();
    testGetAggregatedV2Internal(roleAssignmentFilterV2);
  }

  @Test
  @Owner(developers = REETIKA)
  @Category(UnitTests.class)
  public void testGetAggregatedV2WithUserFilter() {
    Set<String> resourceGroupFilter = Sets.newHashSet(randomAlphabetic(10), randomAlphabetic(10));
    Set<String> roleFilter = Sets.newHashSet(randomAlphabetic(10), randomAlphabetic(10));
    RoleAssignmentFilterV2 roleAssignmentFilterV2 =
        RoleAssignmentFilterV2.builder()
            .resourceGroupFilter(resourceGroupFilter)
            .roleFilter(roleFilter)
            .principalFilter(PrincipalDTO.builder().identifier("user1").type(PrincipalType.USER).build())
            .build();
    testGetAggregatedV2Internal(roleAssignmentFilterV2);
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testGetAggregated() {
    RoleAssignmentFilterDTO roleAssignmentFilterDTO = getRoleAssignmentFilterDTO();
    testGetAggregatedInternal(roleAssignmentFilterDTO);
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testGetAggregatedMissingViewPrincipalPermissions() {
    RoleAssignmentFilterDTO roleAssignmentFilterDTO = getRoleAssignmentFilterDTO();
    testGetAggregatedMissingViewPrincipalPermissionsInternal(roleAssignmentFilterDTO);
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testGetAggregatedWithPrincipalTypes() throws IllegalAccessException {
    RoleAssignmentFilterDTO roleAssignmentFilterDTO = getRoleAssignmentFilterDTO();
    ReflectionUtils.setObjectField(ReflectionUtils.getFieldByName(RoleAssignmentFilterDTO.class, "principalFilter"),
        roleAssignmentFilterDTO, new HashSet<>());
    testGetAggregatedInternal(roleAssignmentFilterDTO);
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testGetAggregatedWithPrincipalTypesMissingViewPrincipalPermissions() throws IllegalAccessException {
    RoleAssignmentFilterDTO roleAssignmentFilterDTO = getRoleAssignmentFilterDTO();
    ReflectionUtils.setObjectField(ReflectionUtils.getFieldByName(RoleAssignmentFilterDTO.class, "principalFilter"),
        roleAssignmentFilterDTO, new HashSet<>());
    testGetAggregatedMissingViewPrincipalPermissionsInternal(roleAssignmentFilterDTO);
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testCreate() {
    RoleAssignmentDTO roleAssignmentDTO = getRoleAssignmentDTO();
    RoleAssignmentDTO roleAssignmentDTOClone = (RoleAssignmentDTO) HObjectMapper.clone(roleAssignmentDTO);
    preSyncDependencies(roleAssignmentDTO, true, true, true);
    preCheckUpdatePermission(roleAssignmentDTO);
    when(transactionTemplate.execute(any())).thenReturn(ResponseDTO.newResponse());
    roleAssignmentResource.create(harnessScopeParams, roleAssignmentDTO);
    assertSyncDependencies(roleAssignmentDTOClone, true, true, true);
    assertCheckUpdatePermission(roleAssignmentDTOClone);
    verify(transactionTemplate, times(1)).execute(any());
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testCreateSyncDependencies() {
    RoleAssignmentDTO roleAssignmentDTO = getRoleAssignmentDTO();
    RoleAssignmentDTO roleAssignmentDTOClone = (RoleAssignmentDTO) HObjectMapper.clone(roleAssignmentDTO);
    preSyncDependencies(roleAssignmentDTO, false, false, false);
    preCheckUpdatePermission(roleAssignmentDTO);
    when(transactionTemplate.execute(any())).thenReturn(ResponseDTO.newResponse());
    roleAssignmentResource.create(harnessScopeParams, roleAssignmentDTO);
    assertSyncDependencies(roleAssignmentDTOClone, false, false, false);
    assertCheckUpdatePermission(roleAssignmentDTOClone);
    verify(transactionTemplate, times(1)).execute(any());
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testCreateInvalidPrincipal() {
    RoleAssignmentDTO roleAssignmentDTO = getRoleAssignmentDTO(PrincipalType.SERVICE);
    RoleAssignmentDTO roleAssignmentDTOClone = (RoleAssignmentDTO) HObjectMapper.clone(roleAssignmentDTO);
    preSyncDependencies(roleAssignmentDTO, true, true, true);
    preCheckUpdatePermission(roleAssignmentDTO);
    try {
      roleAssignmentResource.create(harnessScopeParams, roleAssignmentDTO);
      fail();
    } catch (InvalidRequestException invalidRequestException) {
      assertSyncDependencies(roleAssignmentDTOClone, true, true, true);
      assertCheckUpdatePermission(roleAssignmentDTOClone);
      verify(transactionTemplate, times(0)).execute(any());
    }
  }

  private void preSyncDependencies(List<RoleAssignmentDTO> roleAssignmentDTOs, boolean scopePresent,
      boolean resourceGroupPresent, boolean principalPresent) {
    roleAssignmentDTOs.forEach(roleAssignmentDTO
        -> preSyncDependencies(roleAssignmentDTO, scopePresent, resourceGroupPresent, principalPresent));
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

  private void preCheckUpdatePermission(List<RoleAssignmentDTO> roleAssignmentDTOs) {
    roleAssignmentDTOs.forEach(this::preCheckUpdatePermission);
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
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testUpdate() {
    RoleAssignmentDTO roleAssignmentDTO = getRoleAssignmentDTO();
    RoleAssignmentDTO roleAssignmentDTOClone = (RoleAssignmentDTO) HObjectMapper.clone(roleAssignmentDTO);
    preCheckUpdatePermission(roleAssignmentDTO);
    when(transactionTemplate.execute(any())).thenReturn(ResponseDTO.newResponse());
    roleAssignmentResource.update(roleAssignmentDTO.getIdentifier(), harnessScopeParams, roleAssignmentDTO);
    assertCheckUpdatePermission(roleAssignmentDTOClone);
    verify(transactionTemplate, times(1)).execute(any());
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testUpdateDifferentIdentifier() {
    RoleAssignmentDTO roleAssignmentDTO = getRoleAssignmentDTO();
    roleAssignmentResource.update(
        roleAssignmentDTO.getIdentifier() + randomAlphabetic(1), harnessScopeParams, roleAssignmentDTO);
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testUpdateInvalidPrincipal() {
    RoleAssignmentDTO roleAssignmentDTO = getRoleAssignmentDTO(PrincipalType.SERVICE);
    RoleAssignmentDTO roleAssignmentDTOClone = (RoleAssignmentDTO) HObjectMapper.clone(roleAssignmentDTO);
    preCheckUpdatePermission(roleAssignmentDTO);
    try {
      roleAssignmentResource.update(roleAssignmentDTO.getIdentifier(), harnessScopeParams, roleAssignmentDTO);
      fail();
    } catch (InvalidRequestException invalidRequestException) {
      assertCheckUpdatePermission(roleAssignmentDTOClone);
      verify(transactionTemplate, times(0)).execute(any());
    }
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testCreateMulti() {
    List<RoleAssignmentDTO> roleAssignmentDTOs =
        Lists.newArrayList(getRoleAssignmentDTO(), getRoleAssignmentDTO(), getRoleAssignmentDTO());
    List<RoleAssignmentDTO> roleAssignmentDTOsClone = new ArrayList<>();
    roleAssignmentDTOs.forEach(
        roleAssignmentDTO -> roleAssignmentDTOsClone.add((RoleAssignmentDTO) HObjectMapper.clone(roleAssignmentDTO)));
    preSyncDependencies(roleAssignmentDTOs, true, true, true);
    preCheckUpdatePermission(roleAssignmentDTOs);
    when(transactionTemplate.execute(any())).thenReturn(ResponseDTO.newResponse());
    roleAssignmentResource.create(
        harnessScopeParams, RoleAssignmentCreateRequestDTO.builder().roleAssignments(roleAssignmentDTOs).build());
    for (RoleAssignmentDTO roleAssignmentDTOClone : roleAssignmentDTOsClone) {
      assertSyncDependencies(roleAssignmentDTOs.size(), roleAssignmentDTOClone, true, true, true);
      assertCheckUpdatePermission(roleAssignmentDTOs.size(), roleAssignmentDTOClone);
      verify(transactionTemplate, times(roleAssignmentDTOs.size())).execute(any());
    }
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testCreateMultiSyncDependencies() {
    List<RoleAssignmentDTO> roleAssignmentDTOs =
        Lists.newArrayList(getRoleAssignmentDTO(), getRoleAssignmentDTO(), getRoleAssignmentDTO());
    List<RoleAssignmentDTO> roleAssignmentDTOsClone = new ArrayList<>();
    roleAssignmentDTOs.forEach(
        roleAssignmentDTO -> roleAssignmentDTOsClone.add((RoleAssignmentDTO) HObjectMapper.clone(roleAssignmentDTO)));
    preSyncDependencies(roleAssignmentDTOs, false, false, false);
    preCheckUpdatePermission(roleAssignmentDTOs);
    when(transactionTemplate.execute(any())).thenReturn(ResponseDTO.newResponse());
    roleAssignmentResource.create(
        harnessScopeParams, RoleAssignmentCreateRequestDTO.builder().roleAssignments(roleAssignmentDTOs).build());
    for (RoleAssignmentDTO roleAssignmentDTOClone : roleAssignmentDTOsClone) {
      assertSyncDependencies(roleAssignmentDTOs.size(), roleAssignmentDTOClone, false, false, false);
      assertCheckUpdatePermission(roleAssignmentDTOs.size(), roleAssignmentDTOClone);
      verify(transactionTemplate, times(roleAssignmentDTOs.size())).execute(any());
    }
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testCreateMultiInvalidPrincipal() {
    List<RoleAssignmentDTO> roleAssignmentDTOs = Lists.newArrayList(getRoleAssignmentDTO(PrincipalType.SERVICE),
        getRoleAssignmentDTO(PrincipalType.SERVICE), getRoleAssignmentDTO(PrincipalType.SERVICE));
    List<RoleAssignmentDTO> roleAssignmentDTOsClone = new ArrayList<>();
    roleAssignmentDTOs.forEach(
        roleAssignmentDTO -> roleAssignmentDTOsClone.add((RoleAssignmentDTO) HObjectMapper.clone(roleAssignmentDTO)));
    preSyncDependencies(roleAssignmentDTOs, true, true, true);
    preCheckUpdatePermission(roleAssignmentDTOs);
    roleAssignmentResource.create(
        harnessScopeParams, RoleAssignmentCreateRequestDTO.builder().roleAssignments(roleAssignmentDTOs).build());
    for (RoleAssignmentDTO roleAssignmentDTOClone : roleAssignmentDTOsClone) {
      assertSyncDependencies(roleAssignmentDTOs.size(), roleAssignmentDTOClone, true, true, true);
      assertCheckUpdatePermission(roleAssignmentDTOClone);
      verify(transactionTemplate, times(0)).execute(any());
    }
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testValidate() {
    RoleAssignmentDTO roleAssignmentDTO = getRoleAssignmentDTO();
    RoleAssignmentValidationRequestDTO validationRequestDTO = RoleAssignmentValidationRequestDTO.builder()
                                                                  .roleAssignment(roleAssignmentDTO)
                                                                  .validatePrincipal(true)
                                                                  .validateResourceGroup(true)
                                                                  .validateRole(true)
                                                                  .build();
    Scope scope = ScopeMapper.fromParams(harnessScopeParams);
    doNothing().when(harnessResourceGroupService).sync(roleAssignmentDTO.getResourceGroupIdentifier(), scope);
    ValidationResult validResult = ValidationResult.VALID;
    ValidationResult invalidResult = ValidationResult.builder().valid(false).errorMessage("").build();
    RoleAssignmentValidationResult result = RoleAssignmentValidationResult.builder()
                                                .principalValidationResult(validResult)
                                                .roleValidationResult(invalidResult)
                                                .resourceGroupValidationResult(validResult)
                                                .scopeValidationResult(validResult)
                                                .build();
    when(roleAssignmentService.validate(fromDTO(scope, validationRequestDTO))).thenReturn(result);
    ResponseDTO<RoleAssignmentValidationResponseDTO> responseDTO =
        roleAssignmentResource.validate(harnessScopeParams, validationRequestDTO);
    assertTrue(responseDTO.getData().getPrincipalValidationResult().isValid());
    assertFalse(responseDTO.getData().getRoleValidationResult().isValid());
    assertTrue(responseDTO.getData().getResourceGroupValidationResult().isValid());
    verify(harnessResourceGroupService, times(1)).sync(any(), any());
    verify(roleAssignmentService, times(1)).validate(any());
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testDelete() {
    RoleAssignmentDTO roleAssignmentDTO = getRoleAssignmentDTO();
    Scope scope = ScopeMapper.fromParams(harnessScopeParams);
    RoleAssignment roleAssignment = fromDTO(scope, roleAssignmentDTO);
    when(roleAssignmentService.get(roleAssignment.getIdentifier(), roleAssignment.getScopeIdentifier()))
        .thenReturn(Optional.of(roleAssignment));
    preCheckUpdatePermission(roleAssignmentDTO);
    ValidationResult validResult = ValidationResult.VALID;
    when(actionValidator.canDelete(roleAssignment)).thenReturn(validResult);
    when(transactionTemplate.execute(any())).thenReturn(ResponseDTO.newResponse());
    roleAssignmentResource.delete(harnessScopeParams, roleAssignmentDTO.getIdentifier());
    verify(roleAssignmentService, times(1)).get(any(), any());
    assertCheckUpdatePermission(roleAssignmentDTO);
    verify(actionValidator, times(1)).canDelete(any());
    verify(transactionTemplate, times(1)).execute(any());
  }

  @Test
  @Owner(developers = MEENAKSHI)
  @Category(UnitTests.class)
  public void testBulkDelete() {
    RoleAssignmentDTO roleAssignmentDTO1 = getRoleAssignmentDTO();
    RoleAssignmentDTO roleAssignmentDTO2 = getRoleAssignmentDTO();
    RoleAssignmentDTO roleAssignmentDTO3 = getRoleAssignmentDTO();
    Scope scope = ScopeMapper.fromParams(harnessScopeParams);
    String scopeIdentifier = fromParams(harnessScopeParams).toString();
    RoleAssignment roleAssignment1 = fromDTO(scope, roleAssignmentDTO1);
    RoleAssignment roleAssignment2 = fromDTO(scope, roleAssignmentDTO2);
    RoleAssignment roleAssignment3 = fromDTO(scope, roleAssignmentDTO3);
    String id1 = roleAssignment1.getIdentifier();
    String id2 = roleAssignment2.getIdentifier();
    String id3 = roleAssignment3.getIdentifier();

    mockCallForBulkDelete(roleAssignmentDTO1, roleAssignmentDTO2, roleAssignmentDTO3);
    when(transactionTemplate.execute(any()))
        .thenAnswer(invocationOnMock
            -> invocationOnMock.getArgument(0, TransactionCallback.class)
                   .doInTransaction(new SimpleTransactionStatus()));
    ArgumentCaptor<List<String>> deleteCapture = ArgumentCaptor.forClass(List.class);
    when(roleAssignmentDTOMapper.toResponseDTO(roleAssignment1))
        .thenReturn(RoleAssignmentResponseDTO.builder()
                        .scope(ScopeDTO.builder().accountIdentifier("acc").build())
                        .roleAssignment(roleAssignmentDTO1)
                        .build());
    ResponseDTO<RoleAssignmentDeleteResponseDTO> result =
        roleAssignmentResource.bulkDelete(harnessScopeParams, Set.of(id1, id2, id3));
    verify(roleAssignmentService, times(1)).deleteMulti(eq(scopeIdentifier), deleteCapture.capture());
    List<String> deletedIds = deleteCapture.getValue();
    assertThat(deletedIds).isEqualTo(List.of(id1));
    assertThat(result).isNotNull();
    assertThat(result.getData().failedToDelete).isEqualTo(2);
    assertThat(result.getData().successfullyDeleted).isEqualTo(1);
    assertThat(result.getData().roleAssignmentErrorResponseDTOList)
        .containsAll(List.of(RoleAssignmentErrorResponseDTO.builder()
                                 .roleAssignmentId(id2)
                                 .errorMessage("Role Assignment not found or have been already deleted.")
                                 .build(),
            RoleAssignmentErrorResponseDTO.builder()
                .roleAssignmentId(id3)
                .errorMessage("Failed due to missing permission to manage users")
                .build()));
  }

  @Test
  @Owner(developers = MEENAKSHI)
  @Category(UnitTests.class)
  public void testFilterRoleAssignmentThatCanBeDeleted() {
    List<RoleAssignmentErrorResponseDTO> roleAssignmentErrorResponseDTOS = new ArrayList<>();
    List<RoleAssignment> roleAssignmentThatCanBeDeleted = new ArrayList<>();
    RoleAssignmentDTO roleAssignmentDTO1 = getRoleAssignmentDTO();
    RoleAssignmentDTO roleAssignmentDTO2 = getRoleAssignmentDTO();
    RoleAssignmentDTO roleAssignmentDTO3 = getRoleAssignmentDTO();
    Scope scope = ScopeMapper.fromParams(harnessScopeParams);

    RoleAssignment roleAssignment1 = fromDTO(scope, roleAssignmentDTO1);
    RoleAssignment roleAssignment2 = fromDTO(scope, roleAssignmentDTO2);
    RoleAssignment roleAssignment3 = fromDTO(scope, roleAssignmentDTO3);
    String id1 = roleAssignment1.getIdentifier();
    String id2 = roleAssignment2.getIdentifier();
    String id3 = roleAssignment3.getIdentifier();

    mockCallForBulkDelete(roleAssignmentDTO1, roleAssignmentDTO2, roleAssignmentDTO3);

    roleAssignmentResource.filterRoleAssignmentThatCanBeDeleted(
        harnessScopeParams, Set.of(id1, id2, id3), roleAssignmentErrorResponseDTOS, roleAssignmentThatCanBeDeleted);

    assertThat(roleAssignmentThatCanBeDeleted.size()).isEqualTo(1);
    assertThat(roleAssignmentErrorResponseDTOS.size()).isEqualTo(2);
    assertThat(roleAssignmentErrorResponseDTOS)
        .containsAll(List.of(RoleAssignmentErrorResponseDTO.builder()
                                 .roleAssignmentId(id2)
                                 .errorMessage("Role Assignment not found or have been already deleted.")
                                 .build(),
            RoleAssignmentErrorResponseDTO.builder()
                .roleAssignmentId(id3)
                .errorMessage("Failed due to missing permission to manage users")
                .build()));
  }

  private void mockCallForBulkDelete(RoleAssignmentDTO roleAssignmentDTO1, RoleAssignmentDTO roleAssignmentDTO2,
      RoleAssignmentDTO roleAssignmentDTO3) {
    Scope scope = ScopeMapper.fromParams(harnessScopeParams);
    RoleAssignment roleAssignment1 = fromDTO(scope, roleAssignmentDTO1);
    RoleAssignment roleAssignment2 = fromDTO(scope, roleAssignmentDTO2);
    RoleAssignment roleAssignment3 = fromDTO(scope, roleAssignmentDTO3);
    String id1 = roleAssignment1.getIdentifier();
    String id2 = roleAssignment2.getIdentifier();
    String id3 = roleAssignment3.getIdentifier();
    String scopeIdentifier = fromParams(harnessScopeParams).toString();
    preCheckUpdatePermission(roleAssignmentDTO1);
    ValidationResult validResult = ValidationResult.VALID;
    when(actionValidator.canDelete(roleAssignment1)).thenReturn(validResult);

    when(roleAssignmentService.get(id1, scopeIdentifier)).thenReturn(Optional.of(roleAssignment1));
    when(roleAssignmentService.get(id2, scopeIdentifier))
        .thenThrow(new NotFoundException("Role Assignment not found or have been already deleted."));
    when(roleAssignmentService.get(id3, scopeIdentifier)).thenReturn(Optional.of(roleAssignment3));

    doThrow(new NGAccessDeniedException("Failed due to missing permission to manage users", null, null))
        .when(accessControlClient)
        .checkForAccessOrThrow(ResourceScope.of(harnessScopeParams.getAccountIdentifier(),
                                   harnessScopeParams.getOrgIdentifier(), harnessScopeParams.getProjectIdentifier()),
            Resource.of(AccessControlResourceTypes.USER, roleAssignmentDTO3.getPrincipal().getIdentifier()),
            MANAGE_USER_PERMISSION);
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testDeleteNotFound() {
    RoleAssignmentDTO roleAssignmentDTO = getRoleAssignmentDTO();
    Scope scope = ScopeMapper.fromParams(harnessScopeParams);
    RoleAssignment roleAssignment = fromDTO(scope, roleAssignmentDTO);
    when(roleAssignmentService.get(roleAssignment.getIdentifier(), roleAssignment.getScopeIdentifier()))
        .thenReturn(Optional.empty());
    try {
      roleAssignmentResource.delete(harnessScopeParams, roleAssignmentDTO.getIdentifier());
      fail();
    } catch (InvalidRequestException invalidRequestException) {
      verify(roleAssignmentService, times(1)).get(any(), any());
    }
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testDeleteInvalidPrincipal() {
    RoleAssignmentDTO roleAssignmentDTO = getRoleAssignmentDTO(PrincipalType.SERVICE);
    Scope scope = ScopeMapper.fromParams(harnessScopeParams);
    RoleAssignment roleAssignment = fromDTO(scope, roleAssignmentDTO);
    when(roleAssignmentService.get(roleAssignment.getIdentifier(), roleAssignment.getScopeIdentifier()))
        .thenReturn(Optional.of(roleAssignment));
    preCheckUpdatePermission(roleAssignmentDTO);
    try {
      roleAssignmentResource.delete(harnessScopeParams, roleAssignmentDTO.getIdentifier());
      fail();
    } catch (InvalidRequestException invalidRequestException) {
      verify(roleAssignmentService, times(1)).get(any(), any());
      assertCheckUpdatePermission(roleAssignmentDTO);
      verify(transactionTemplate, times(0)).execute(any());
    }
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testDeleteActionNotAllowed() {
    RoleAssignmentDTO roleAssignmentDTO = getRoleAssignmentDTO();
    Scope scope = ScopeMapper.fromParams(harnessScopeParams);
    RoleAssignment roleAssignment = fromDTO(scope, roleAssignmentDTO);
    when(roleAssignmentService.get(roleAssignment.getIdentifier(), roleAssignment.getScopeIdentifier()))
        .thenReturn(Optional.of(roleAssignment));
    preCheckUpdatePermission(roleAssignmentDTO);
    ValidationResult invalidResult = ValidationResult.builder().valid(false).errorMessage("").build();
    when(actionValidator.canDelete(roleAssignment)).thenReturn(invalidResult);
    try {
      roleAssignmentResource.delete(harnessScopeParams, roleAssignmentDTO.getIdentifier());
      fail();
    } catch (InvalidRequestException invalidRequestException) {
      verify(roleAssignmentService, times(1)).get(any(), any());
      assertCheckUpdatePermission(roleAssignmentDTO);
      verify(actionValidator, times(1)).canDelete(any());
      verify(transactionTemplate, times(0)).execute(any());
    }
  }

  @Test
  @Owner(developers = JIMIT_GANDHI)
  @Category(UnitTests.class)
  public void listFilterV2_WithMissingViewPermission_OnPrincipalTypes_ThrowsUnauthorizedException() {
    Set<PrincipalType> principalTypes = Sets.newHashSet(USER, USER_GROUP, SERVICE_ACCOUNT);
    RoleAssignmentFilterV2 roleAssignmentFilterV2 =
        RoleAssignmentFilterV2.builder().principalTypeFilter(principalTypes).build();
    preViewPrincipalPermissions(false, false, false);
    try {
      roleAssignmentResource.getList(pageRequest, harnessScopeParams, roleAssignmentFilterV2);
      fail();
    } catch (UnauthorizedException unauthorizedException) {
      verify(accessControlClient, times(3)).hasAccess(any(ResourceScope.class), any(), any());
      verify(roleAssignmentService, times(0)).list(any(), any());
    }
  }

  @Test
  @Owner(developers = JIMIT_GANDHI)
  @Category(UnitTests.class)
  public void listV2With_AllPermitted_PrincipalTypeFilters_CallsService_With_AllPrincipalTypes() {
    Set<String> resourceGroupFilter = Sets.newHashSet(randomAlphabetic(10), randomAlphabetic(10));
    Set<String> roleFilter = Sets.newHashSet(randomAlphabetic(10), randomAlphabetic(10));
    Set<PrincipalType> principalTypes = Sets.newHashSet(USER, USER_GROUP, SERVICE_ACCOUNT);
    RoleAssignmentFilterV2 roleAssignmentFilterV2 = RoleAssignmentFilterV2.builder()
                                                        .resourceGroupFilter(resourceGroupFilter)
                                                        .roleFilter(roleFilter)
                                                        .principalTypeFilter(principalTypes)
                                                        .disabledFilter(false)
                                                        .harnessManagedFilter(false)
                                                        .build();
    preViewPrincipalPermissions(true, true, true);
    testListFilterV2(roleAssignmentFilterV2);
  }

  @Test
  @Owner(developers = JIMIT_GANDHI)
  @Category(UnitTests.class)
  public void listV2With_User_PrincipalTypeFilters_CallsService_With_OnlyUserPrincipalType() {
    Set<String> resourceGroupFilter = Sets.newHashSet(randomAlphabetic(10), randomAlphabetic(10));
    Set<String> roleFilter = Sets.newHashSet(randomAlphabetic(10), randomAlphabetic(10));
    Set<PrincipalType> principalTypes = Sets.newHashSet(USER, USER_GROUP, SERVICE_ACCOUNT);
    RoleAssignmentFilterV2 roleAssignmentFilterV2 = RoleAssignmentFilterV2.builder()
                                                        .resourceGroupFilter(resourceGroupFilter)
                                                        .roleFilter(roleFilter)
                                                        .principalTypeFilter(principalTypes)
                                                        .disabledFilter(false)
                                                        .harnessManagedFilter(false)
                                                        .build();
    preViewPrincipalPermissions(true, false, false);
    testListFilterV2(roleAssignmentFilterV2);
  }

  @Test
  @Owner(developers = JIMIT_GANDHI)
  @Category(UnitTests.class)
  public void listV2With_UserGroup_PrincipalTypeFilters_CallsService_With_OnlyUserGroupPrincipalType() {
    Set<String> resourceGroupFilter = Sets.newHashSet(randomAlphabetic(10), randomAlphabetic(10));
    Set<String> roleFilter = Sets.newHashSet(randomAlphabetic(10), randomAlphabetic(10));
    Set<PrincipalType> principalTypes = Sets.newHashSet(USER, USER_GROUP, SERVICE_ACCOUNT);
    RoleAssignmentFilterV2 roleAssignmentFilterV2 = RoleAssignmentFilterV2.builder()
                                                        .resourceGroupFilter(resourceGroupFilter)
                                                        .roleFilter(roleFilter)
                                                        .principalTypeFilter(principalTypes)
                                                        .disabledFilter(false)
                                                        .harnessManagedFilter(false)
                                                        .build();
    preViewPrincipalPermissions(false, true, false);
    testListFilterV2(roleAssignmentFilterV2);
  }

  @Test
  @Owner(developers = JIMIT_GANDHI)
  @Category(UnitTests.class)
  public void listV2With_ServiceAccount_PrincipalTypeFilters_CallsService_With_OnlyServiceAccountPrincipalType() {
    Set<String> resourceGroupFilter = Sets.newHashSet(randomAlphabetic(10), randomAlphabetic(10));
    Set<String> roleFilter = Sets.newHashSet(randomAlphabetic(10), randomAlphabetic(10));
    Set<PrincipalType> principalTypes = Sets.newHashSet(USER, USER_GROUP, SERVICE_ACCOUNT);
    RoleAssignmentFilterV2 roleAssignmentFilterV2 = RoleAssignmentFilterV2.builder()
                                                        .resourceGroupFilter(resourceGroupFilter)
                                                        .roleFilter(roleFilter)
                                                        .principalTypeFilter(principalTypes)
                                                        .build();
    preViewPrincipalPermissions(false, false, true);
    testListFilterV2(roleAssignmentFilterV2);
  }

  private void testListFilterV2(RoleAssignmentFilterV2 roleAssignmentFilterV2) {
    Scope scope = ScopeMapper.fromParams(harnessScopeParams);
    boolean isUserPrincipal = roleAssignmentFilterV2.getPrincipalFilter() != null
        && USER.equals(roleAssignmentFilterV2.getPrincipalFilter().getType());
    if (isUserPrincipal) {
      when(userGroupService.list(roleAssignmentFilterV2.getPrincipalFilter().getIdentifier()))
          .thenReturn(Lists.newArrayList(UserGroup.builder().build()));
    }
    ArgumentCaptor<RoleAssignmentFilter> roleAssignmentFilterArgumentCaptor =
        ArgumentCaptor.forClass(RoleAssignmentFilter.class);
    PageRequest maxPageRequest = PageRequest.builder().pageSize(1000).build();
    when(roleAssignmentService.list(eq(maxPageRequest), any())).thenReturn(getEmptyPageResponse(maxPageRequest));
    RoleFilter roleFilter = RoleFilter.builder()
                                .identifierFilter(new HashSet<>())
                                .scopeIdentifier(scope.toString())
                                .managedFilter(NO_FILTER)
                                .build();
    when(roleService.list(maxPageRequest, roleFilter, true)).thenReturn(getEmptyPageResponse(maxPageRequest));
    when(resourceGroupService.list(new ArrayList<>(), scope.toString(), NO_FILTER)).thenReturn(new ArrayList<>());

    ResponseDTO<PageResponse<RoleAssignmentAggregate>> responseDTO =
        roleAssignmentResource.getList(maxPageRequest, harnessScopeParams, roleAssignmentFilterV2);
    if (isUserPrincipal) {
      verify(userGroupService, times(1)).list(roleAssignmentFilterV2.getPrincipalFilter().getIdentifier());
    }
    verify(accessControlClient, times(3)).hasAccess(any(ResourceScope.class), any(), any());
    verify(roleAssignmentService, times(1)).list(eq(maxPageRequest), roleAssignmentFilterArgumentCaptor.capture());
    RoleAssignmentFilter roleAssignmentFilter = roleAssignmentFilterArgumentCaptor.getValue();
    assertFilterV2(roleAssignmentFilterV2, roleAssignmentFilter);
  }

  private void assertFilterV2(
      RoleAssignmentFilterV2 roleAssignmentFilterV2, RoleAssignmentFilter roleAssignmentFilter) {
    assertEquals(roleAssignmentFilterV2.getPrincipalTypeFilter(), roleAssignmentFilter.getPrincipalTypeFilter());
    assertEquals(Objects.isNull(roleAssignmentFilterV2.getDisabledFilter())
            ? new HashSet<>()
            : Sets.newHashSet(roleAssignmentFilterV2.getDisabledFilter()),
        roleAssignmentFilter.getDisabledFilter());
    ManagedFilter managedFilter = Objects.isNull(roleAssignmentFilterV2.getHarnessManagedFilter())
        ? ManagedFilter.NO_FILTER
        : roleAssignmentFilterV2.getHarnessManagedFilter() == Boolean.TRUE ? ManagedFilter.ONLY_MANAGED
                                                                           : ManagedFilter.ONLY_CUSTOM;
    assertEquals(managedFilter, roleAssignmentFilter.getManagedFilter());
  }
}
