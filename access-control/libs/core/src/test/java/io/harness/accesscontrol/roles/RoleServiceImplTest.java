/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.roles;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.outbox.TransactionOutboxModule.OUTBOX_TRANSACTION_TEMPLATE;
import static io.harness.rule.OwnerRule.ADITYA;
import static io.harness.rule.OwnerRule.JIMIT_GANDHI;
import static io.harness.rule.OwnerRule.KARAN;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;
import static junit.framework.TestCase.fail;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.accesscontrol.AccessControlCoreTestBase;
import io.harness.accesscontrol.common.filter.ManagedFilter;
import io.harness.accesscontrol.permissions.Permission;
import io.harness.accesscontrol.permissions.PermissionFilter;
import io.harness.accesscontrol.permissions.PermissionService;
import io.harness.accesscontrol.permissions.PermissionStatus;
import io.harness.accesscontrol.principals.PrincipalType;
import io.harness.accesscontrol.roleassignments.RoleAssignment;
import io.harness.accesscontrol.roleassignments.RoleAssignmentFilter;
import io.harness.accesscontrol.roleassignments.RoleAssignmentService;
import io.harness.accesscontrol.roles.filter.RoleFilter;
import io.harness.accesscontrol.roles.persistence.RoleDao;
import io.harness.accesscontrol.scopes.TestScopeLevels;
import io.harness.accesscontrol.scopes.core.Scope;
import io.harness.accesscontrol.scopes.core.ScopeService;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.beans.PageRequest;
import io.harness.ng.beans.PageResponse;
import io.harness.outbox.api.OutboxService;
import io.harness.rule.Owner;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.inject.name.Named;
import io.serializer.HObjectMapper;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.springframework.transaction.support.TransactionTemplate;

@OwnedBy(PL)
public class RoleServiceImplTest extends AccessControlCoreTestBase {
  private RoleDao roleDao;
  private PermissionService permissionService;
  private ScopeService scopeService;
  private RoleAssignmentService roleAssignmentService;
  private TransactionTemplate transactionTemplate;
  private RoleServiceImpl roleService;
  @Named(OUTBOX_TRANSACTION_TEMPLATE) private TransactionTemplate outboxTransactionTemplate;
  private OutboxService outboxService;

  private static final Set<PermissionStatus> ALLOWED_PERMISSION_STATUS =
      Sets.newHashSet(PermissionStatus.EXPERIMENTAL, PermissionStatus.ACTIVE, PermissionStatus.DEPRECATED);

  @Before
  public void setup() {
    roleDao = mock(RoleDao.class);
    permissionService = mock(PermissionService.class);
    scopeService = mock(ScopeService.class);
    roleAssignmentService = mock(RoleAssignmentService.class);
    transactionTemplate = mock(TransactionTemplate.class);
    outboxService = mock(OutboxService.class);
    outboxTransactionTemplate = mock(TransactionTemplate.class);
    roleService = spy(new RoleServiceImpl(roleDao, permissionService, scopeService, roleAssignmentService,
        transactionTemplate, outboxTransactionTemplate, outboxService));
  }

  private Role getRole(int count, boolean managed) {
    Set<String> permissions = new HashSet<>();
    for (int i = 0; i < count; i++) {
      permissions.add(randomAlphabetic(10));
    }
    return Role.builder()
        .identifier(randomAlphabetic(10))
        .name(randomAlphabetic(10))
        .scopeIdentifier(randomAlphabetic(10))
        .allowedScopeLevels(Sets.newHashSet(TestScopeLevels.TEST_SCOPE.toString()))
        .permissions(permissions)
        .managed(managed)
        .version(17L)
        .build();
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testCreateManagedRole() {
    Role role = getRole(5, true);
    when(scopeService.areScopeLevelsValid(role.getAllowedScopeLevels())).thenReturn(true);
    testCreateRole(role);
    verify(scopeService, times(1)).areScopeLevelsValid(any());
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testCreateCustomRole() {
    Role role = getRole(5, false);
    when(scopeService.buildScopeFromScopeIdentifier(role.getScopeIdentifier()))
        .thenReturn(Scope.builder().level(TestScopeLevels.TEST_SCOPE).build());
    testCreateRole(role);
    verify(scopeService, times(2)).buildScopeFromScopeIdentifier(any());
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testCreateInvalidScope() {
    Role role = getRole(5, true);
    when(scopeService.areScopeLevelsValid(role.getAllowedScopeLevels())).thenReturn(false);
    try {
      roleService.create(role);
      fail();
    } catch (InvalidArgumentsException exception) {
      verify(scopeService, times(1)).areScopeLevelsValid(any());
    }

    role = Role.builder()
               .identifier(role.getIdentifier())
               .name(role.getName())
               .scopeIdentifier(role.getScopeIdentifier())
               .allowedScopeLevels(role.getAllowedScopeLevels())
               .permissions(role.getPermissions())
               .managed(false)
               .build();
    when(scopeService.buildScopeFromScopeIdentifier(role.getScopeIdentifier()))
        .thenReturn(Scope.builder().level(TestScopeLevels.EXTRA_SCOPE).build());
    try {
      roleService.create(role);
      fail();
    } catch (InvalidArgumentsException exception) {
      verify(scopeService, times(1)).buildScopeFromScopeIdentifier(any());
    }
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testCreateInvalidPermissions() {
    Role role = getRole(5, true);
    when(scopeService.areScopeLevelsValid(role.getAllowedScopeLevels())).thenReturn(true);
    PermissionFilter validatePermissionFilter = PermissionFilter.builder()
                                                    .identifierFilter(role.getPermissions())
                                                    .statusFilter(ALLOWED_PERMISSION_STATUS)
                                                    .build();
    List<Permission> validatePermissionList = new ArrayList<>();
    when(permissionService.list(validatePermissionFilter)).thenReturn(validatePermissionList);
    try {
      roleService.create(role);
    } catch (InvalidArgumentsException exception) {
      verify(scopeService, times(1)).areScopeLevelsValid(any());
      verify(permissionService, times(1)).list(any());
    }
  }

  private void testCreateRole(Role role) {
    PermissionFilter validatePermissionFilter = getValidatePermissionFilter(role);
    List<Permission> validatePermissionList =
        role.getPermissions()
            .stream()
            .map(permission
                -> Permission.builder().allowedScopeLevels(role.getAllowedScopeLevels()).identifier(permission).build())
            .collect(Collectors.toList());
    when(permissionService.list(validatePermissionFilter)).thenReturn(validatePermissionList);

    PermissionFilter compulsoryPermissionFilter = getCompulsoryPermissionFilter(role);
    String compulsoryPermissionAllRoleScopes = randomAlphabetic(10);
    String compulsoryPermissionMissingRoleScopes = randomAlphabetic(10);
    List<Permission> compulsoryPermissionList = new ArrayList<>();
    compulsoryPermissionList.add(Permission.builder()
                                     .identifier(compulsoryPermissionAllRoleScopes)
                                     .allowedScopeLevels(role.getAllowedScopeLevels())
                                     .build());
    compulsoryPermissionList.add(Permission.builder()
                                     .identifier(compulsoryPermissionMissingRoleScopes)
                                     .allowedScopeLevels(new HashSet<>())
                                     .build());
    when(permissionService.list(compulsoryPermissionFilter)).thenReturn(compulsoryPermissionList);
    Role roleClone = (Role) HObjectMapper.clone(role);
    roleClone.getPermissions().add(compulsoryPermissionAllRoleScopes);
    when(roleDao.create(roleClone)).thenReturn(roleClone);
    when(outboxTransactionTemplate.execute(any())).thenReturn(role);
    Role savedRole = roleService.create(role);
    assertEquals(roleClone, savedRole);
    verify(permissionService, times(1)).list(validatePermissionFilter);
    verify(permissionService, times(1)).list(compulsoryPermissionFilter);
  }

  private PermissionFilter getCompulsoryPermissionFilter(Role role) {
    return PermissionFilter.builder()
        .allowedScopeLevelsFilter(role.getAllowedScopeLevels())
        .statusFilter(ALLOWED_PERMISSION_STATUS)
        .includedInAllRolesFilter(PermissionFilter.IncludedInAllRolesFilter.PERMISSIONS_INCLUDED_IN_ALL_ROLES)
        .build();
  }

  private PermissionFilter getValidatePermissionFilter(Role role) {
    return PermissionFilter.builder()
        .identifierFilter(role.getPermissions())
        .statusFilter(ALLOWED_PERMISSION_STATUS)
        .build();
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testUpdate() {
    Role currentRole = getRole(5, false);
    Role currentRoleClone = (Role) HObjectMapper.clone(currentRole);
    Set<String> newPermissions = new HashSet<>();
    for (int i = 0; i < 5; i++) {
      newPermissions.add(randomAlphabetic(10));
    }
    Role roleUpdate = Role.builder()
                          .identifier(currentRoleClone.getIdentifier())
                          .name(currentRoleClone.getName())
                          .scopeIdentifier(currentRoleClone.getScopeIdentifier())
                          .allowedScopeLevels(currentRoleClone.getAllowedScopeLevels())
                          .permissions(newPermissions)
                          .managed(false)
                          .build();
    Role updatedRole = (Role) HObjectMapper.clone(roleUpdate);
    updatedRole.setVersion(currentRole.getVersion() + 1);
    when(scopeService.buildScopeFromScopeIdentifier(roleUpdate.getScopeIdentifier()))
        .thenReturn(Scope.builder().level(TestScopeLevels.EXTRA_SCOPE).build());
    when(roleDao.get(roleUpdate.getIdentifier(), roleUpdate.getScopeIdentifier(), ManagedFilter.ONLY_CUSTOM))
        .thenReturn(Optional.of(currentRole));
    PermissionFilter validatePermissionFilter = getValidatePermissionFilter(roleUpdate);
    List<Permission> validatePermissionList = roleUpdate.getPermissions()
                                                  .stream()
                                                  .map(permission
                                                      -> Permission.builder()
                                                             .allowedScopeLevels(roleUpdate.getAllowedScopeLevels())
                                                             .identifier(permission)
                                                             .build())
                                                  .collect(Collectors.toList());
    when(permissionService.list(validatePermissionFilter)).thenReturn(validatePermissionList);

    PermissionFilter compulsoryPermissionFilter = getCompulsoryPermissionFilter(roleUpdate);
    when(permissionService.list(compulsoryPermissionFilter)).thenReturn(new ArrayList<>());
    when(outboxTransactionTemplate.execute(any())).thenReturn(updatedRole);

    Role roleUpdateResult = roleService.update(roleUpdate);

    assertEquals(updatedRole, roleUpdateResult);
    assertEquals(currentRole, currentRole);
    verify(roleDao, times(1)).get(any(), any(), any());
    verify(permissionService, times(2)).list(any());
    verify(outboxTransactionTemplate, times(1)).execute(any());
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testUpdateNotFound() {
    Role roleUpdate = getRole(5, false);
    when(roleDao.get(roleUpdate.getIdentifier(), roleUpdate.getScopeIdentifier(), ManagedFilter.ONLY_CUSTOM))
        .thenReturn(Optional.empty());
    roleService.update(roleUpdate);
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testGet() {
    String identifier = randomAlphabetic(10);
    String scopeIdentifier = randomAlphabetic(10);
    List<ManagedFilter> managedFiltersList =
        Lists.newArrayList(ManagedFilter.NO_FILTER, ManagedFilter.ONLY_CUSTOM, ManagedFilter.ONLY_MANAGED);
    int invocations = 0;
    for (ManagedFilter managedFilter : managedFiltersList) {
      invocations += 1;
      when(roleDao.get(identifier, scopeIdentifier, managedFilter))
          .thenReturn(Optional.of(Role.builder().scopeIdentifier(scopeIdentifier).identifier(identifier).build()));
      Optional<Role> role = roleService.get(identifier, scopeIdentifier, managedFilter);
      assertTrue(role.isPresent());
      assertEquals(identifier, role.get().getIdentifier());
      assertEquals(scopeIdentifier, role.get().getScopeIdentifier());
      verify(roleDao, times(1)).get(identifier, scopeIdentifier, managedFilter);
      verify(roleDao, times(invocations)).get(any(), any(), any());
    }
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testList() {
    PageRequest pageRequest = PageRequest.builder().pageIndex(0).build();
    RoleFilter roleFilter = RoleFilter.builder().scopeIdentifier(randomAlphabetic(10)).build();
    when(roleDao.list(pageRequest, roleFilter, true)).thenReturn(PageResponse.getEmptyPageResponse(pageRequest));
    PageResponse<Role> pageResponse = roleService.list(pageRequest, roleFilter, true);
    assertTrue(pageResponse.isEmpty());
    verify(roleDao, times(1)).list(pageRequest, roleFilter, true);
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testDelete() {
    String identifier = randomAlphabetic(10);
    String scopeIdentifier = randomAlphabetic(10);
    Role role = Role.builder().scopeIdentifier(scopeIdentifier).identifier(identifier).build();
    when(roleDao.get(identifier, scopeIdentifier, ManagedFilter.ONLY_CUSTOM)).thenReturn(Optional.of(role));
    when(outboxTransactionTemplate.execute(any())).thenReturn(role);
    Role deletedRole = roleService.delete(identifier, scopeIdentifier);
    assertEquals(role, deletedRole);
    verify(roleDao, times(1)).get(any(), any(), any());
    verify(outboxTransactionTemplate, times(1)).execute(any());
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testDeleteNotFound() {
    String identifier = randomAlphabetic(10);
    String scopeIdentifier = randomAlphabetic(10);
    when(roleDao.get(identifier, scopeIdentifier, ManagedFilter.ONLY_CUSTOM)).thenReturn(Optional.empty());
    roleService.delete(identifier, scopeIdentifier);
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testDeleteManaged() {
    String identifier = randomAlphabetic(10);
    Role role = Role.builder().identifier(identifier).build();
    when(roleDao.get(identifier, null, ManagedFilter.ONLY_MANAGED)).thenReturn(Optional.of(role));
    when(transactionTemplate.execute(any())).thenReturn(role);
    Role deletedRole = roleService.deleteManaged(identifier);
    assertEquals(role, deletedRole);
    verify(roleDao, times(1)).get(any(), any(), any());
    verify(transactionTemplate, times(1)).execute(any());
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testDeleteManagedNotFound() {
    String identifier = randomAlphabetic(10);
    when(roleDao.get(identifier, null, ManagedFilter.ONLY_MANAGED)).thenReturn(Optional.empty());
    roleService.deleteManaged(identifier);
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testDeleteMulti() {
    RoleFilter roleFilter =
        RoleFilter.builder().scopeIdentifier(randomAlphabetic(10)).managedFilter(ManagedFilter.ONLY_CUSTOM).build();
    when(roleDao.deleteMulti(roleFilter)).thenReturn(17L);
    long deletedCount = roleService.deleteMulti(roleFilter);
    assertEquals(17L, deletedCount);
    verify(roleDao, times(1)).deleteMulti(roleFilter);
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testDeleteInvalidManagedFilter() {
    Set<ManagedFilter> managedFilters = new HashSet<>(Arrays.asList(ManagedFilter.values()));
    managedFilters.remove(ManagedFilter.ONLY_CUSTOM);
    managedFilters.forEach(managedFilter -> {
      RoleFilter roleFilter =
          RoleFilter.builder().scopeIdentifier(randomAlphabetic(10)).managedFilter(managedFilter).build();
      try {
        roleService.deleteMulti(roleFilter);
        fail();
      } catch (InvalidRequestException exception) {
        // all good
      }
    });
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testAddPermissionToRoles() {
    String permissionIdentifier = randomAlphabetic(10);
    RoleFilter roleFilter = RoleFilter.builder().scopeIdentifier(randomAlphabetic(10)).build();
    when(roleDao.addPermissionToRoles(permissionIdentifier, roleFilter)).thenReturn(true);
    boolean result = roleService.addPermissionToRoles(permissionIdentifier, roleFilter);
    assertTrue(result);
    verify(roleDao, times(1)).addPermissionToRoles(any(), any());

    when(roleDao.addPermissionToRoles(permissionIdentifier, roleFilter)).thenReturn(false);
    result = roleService.addPermissionToRoles(permissionIdentifier, roleFilter);
    assertFalse(result);
    verify(roleDao, times(2)).addPermissionToRoles(any(), any());
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testRemovePermissionFromRoles() {
    String permissionIdentifier = randomAlphabetic(10);
    RoleFilter roleFilter = RoleFilter.builder().scopeIdentifier(randomAlphabetic(10)).build();
    when(roleDao.removePermissionFromRoles(permissionIdentifier, roleFilter)).thenReturn(true);
    boolean result = roleService.removePermissionFromRoles(permissionIdentifier, roleFilter);
    assertTrue(result);
    verify(roleDao, times(1)).removePermissionFromRoles(any(), any());

    when(roleDao.removePermissionFromRoles(permissionIdentifier, roleFilter)).thenReturn(false);
    result = roleService.removePermissionFromRoles(permissionIdentifier, roleFilter);
    assertFalse(result);
    verify(roleDao, times(2)).removePermissionFromRoles(any(), any());
  }

  @Test
  @Owner(developers = {ADITYA, JIMIT_GANDHI})
  @Category(UnitTests.class)
  public void testListWithPrincipalCountUser() {
    PageRequest pageRequest = PageRequest.builder().pageIndex(0).pageSize(50).build();
    Role role = Role.builder()
                    .identifier(randomAlphabetic(10))
                    .scopeIdentifier(randomAlphabetic(10))
                    .name(randomAlphabetic(10))
                    .build();
    RoleAssignment roleAssignmentToUser = RoleAssignment.builder()
                                              .identifier(randomAlphabetic(10))
                                              .scopeIdentifier(role.getScopeIdentifier())
                                              .roleIdentifier(role.getIdentifier())
                                              .principalType(PrincipalType.USER)
                                              .build();
    RoleFilter roleFilter = RoleFilter.builder()
                                .identifierFilter(Sets.newHashSet(roleAssignmentToUser.getRoleIdentifier()))
                                .scopeIdentifier(role.getScopeIdentifier())
                                .build();

    PageResponse<Role> rolePageResponse = PageResponse.<Role>builder()
                                              .content(Collections.singletonList(role))
                                              .totalPages(1)
                                              .totalItems(1)
                                              .pageItemCount(1)
                                              .pageSize(50)
                                              .pageIndex(0)
                                              .empty(false)
                                              .build();
    when(roleService.list(any(), any(RoleFilter.class), eq(true))).thenReturn(rolePageResponse);

    PageResponse<RoleAssignment> roleAssignmentPageResponse =
        PageResponse.<RoleAssignment>builder()
            .content(Collections.singletonList(roleAssignmentToUser))
            .totalPages(1)
            .totalItems(1)
            .pageItemCount(1)
            .pageSize(50)
            .pageIndex(0)
            .empty(false)
            .build();

    PageRequest roleAssignmentsPageRequest = PageRequest.builder().pageSize(50000).build();
    RoleAssignmentFilter roleAssignmentFilter = RoleAssignmentFilter.builder()
                                                    .scopeFilter(roleFilter.getScopeIdentifier())
                                                    .roleFilter(roleFilter.getIdentifierFilter())
                                                    .build();
    when(roleAssignmentService.list(roleAssignmentsPageRequest, roleAssignmentFilter, true))
        .thenReturn(roleAssignmentPageResponse);

    PageResponse<RoleWithPrincipalCount> pageResponse =
        roleService.listWithPrincipalCount(pageRequest, roleFilter, true);

    assertEquals(1, pageResponse.getContent().size());
    for (int i = 0; i < pageResponse.getContent().size(); i++) {
      assertEquals(1, (int) pageResponse.getContent().get(i).getRoleAssignedToUserCount());
    }
    verify(roleAssignmentService, times(1)).list(roleAssignmentsPageRequest, roleAssignmentFilter, true);
  }

  @Test
  @Owner(developers = {ADITYA, JIMIT_GANDHI})
  @Category(UnitTests.class)
  public void testListWithPrincipalCountUserGroup() {
    PageRequest pageRequest = PageRequest.builder().pageIndex(0).pageSize(50).build();
    Role role = Role.builder()
                    .identifier(randomAlphabetic(10))
                    .scopeIdentifier(randomAlphabetic(10))
                    .name(randomAlphabetic(10))
                    .build();
    RoleAssignment roleAssignmentToUserGroup = RoleAssignment.builder()
                                                   .identifier(randomAlphabetic(10))
                                                   .scopeIdentifier(role.getScopeIdentifier())
                                                   .roleIdentifier(role.getIdentifier())
                                                   .principalType(PrincipalType.USER_GROUP)
                                                   .build();
    RoleFilter roleFilter = RoleFilter.builder()
                                .identifierFilter(Sets.newHashSet(roleAssignmentToUserGroup.getRoleIdentifier()))
                                .scopeIdentifier(role.getScopeIdentifier())
                                .build();

    PageResponse<Role> rolePageResponse = PageResponse.<Role>builder()
                                              .content(Collections.singletonList(role))
                                              .totalPages(1)
                                              .totalItems(1)
                                              .pageItemCount(1)
                                              .pageSize(50)
                                              .pageIndex(0)
                                              .empty(false)
                                              .build();
    when(roleService.list(any(), any(RoleFilter.class), eq(true))).thenReturn(rolePageResponse);

    PageResponse<RoleAssignment> roleAssignmentPageResponse =
        PageResponse.<RoleAssignment>builder()
            .content(Collections.singletonList(roleAssignmentToUserGroup))
            .totalPages(1)
            .totalItems(1)
            .pageItemCount(1)
            .pageSize(50000)
            .pageIndex(0)
            .empty(false)
            .build();
    when(roleAssignmentService.list(any(), any(RoleAssignmentFilter.class), eq(true)))
        .thenReturn(roleAssignmentPageResponse);
    PageRequest roleAssignmentsPageRequest = PageRequest.builder().pageSize(50000).build();
    RoleAssignmentFilter roleAssignmentFilter = RoleAssignmentFilter.builder()
                                                    .scopeFilter(roleFilter.getScopeIdentifier())
                                                    .roleFilter(roleFilter.getIdentifierFilter())
                                                    .build();
    when(roleAssignmentService.list(roleAssignmentsPageRequest, roleAssignmentFilter, true))
        .thenReturn(roleAssignmentPageResponse);

    PageResponse<RoleWithPrincipalCount> pageResponse =
        roleService.listWithPrincipalCount(pageRequest, roleFilter, true);

    assertEquals(1, pageResponse.getContent().size());
    for (int i = 0; i < pageResponse.getContent().size(); i++) {
      assertEquals(1, (int) pageResponse.getContent().get(i).getRoleAssignedToUserGroupCount());
    }
    verify(roleAssignmentService, times(1)).list(roleAssignmentsPageRequest, roleAssignmentFilter, true);
  }
  @Test
  @Owner(developers = {ADITYA, JIMIT_GANDHI})
  @Category(UnitTests.class)
  public void testListWithPrincipalCountServiceAccount() {
    PageRequest pageRequest = PageRequest.builder().pageIndex(0).pageSize(50).build();
    Role role = Role.builder()
                    .identifier(randomAlphabetic(10))
                    .scopeIdentifier(randomAlphabetic(10))
                    .name(randomAlphabetic(10))
                    .build();
    RoleAssignment roleAssignmentToServiceAccount = RoleAssignment.builder()
                                                        .identifier(randomAlphabetic(10))
                                                        .scopeIdentifier(role.getScopeIdentifier())
                                                        .roleIdentifier(role.getIdentifier())
                                                        .principalType(PrincipalType.SERVICE_ACCOUNT)
                                                        .build();
    RoleFilter roleFilter = RoleFilter.builder()
                                .identifierFilter(Sets.newHashSet(roleAssignmentToServiceAccount.getRoleIdentifier()))
                                .scopeIdentifier(role.getScopeIdentifier())
                                .build();

    PageResponse<Role> rolePageResponse = PageResponse.<Role>builder()
                                              .content(Collections.singletonList(role))
                                              .totalPages(1)
                                              .totalItems(1)
                                              .pageItemCount(1)
                                              .pageSize(50)
                                              .pageIndex(0)
                                              .empty(false)
                                              .build();
    when(roleService.list(any(), any(RoleFilter.class), eq(true))).thenReturn(rolePageResponse);

    PageResponse<RoleAssignment> roleAssignmentPageResponse =
        PageResponse.<RoleAssignment>builder()
            .content(Collections.singletonList(roleAssignmentToServiceAccount))
            .totalPages(1)
            .totalItems(1)
            .pageItemCount(1)
            .pageSize(50000)
            .pageIndex(0)
            .empty(false)
            .build();
    PageRequest roleAssignmentsPageRequest = PageRequest.builder().pageSize(50000).build();
    RoleAssignmentFilter roleAssignmentFilter = RoleAssignmentFilter.builder()
                                                    .scopeFilter(roleFilter.getScopeIdentifier())
                                                    .roleFilter(roleFilter.getIdentifierFilter())
                                                    .build();
    when(roleAssignmentService.list(roleAssignmentsPageRequest, roleAssignmentFilter, true))
        .thenReturn(roleAssignmentPageResponse);

    PageResponse<RoleWithPrincipalCount> pageResponse =
        roleService.listWithPrincipalCount(pageRequest, roleFilter, true);

    assertEquals(1, pageResponse.getContent().size());
    for (int i = 0; i < pageResponse.getContent().size(); i++) {
      assertEquals(1, (int) pageResponse.getContent().get(i).getRoleAssignedToServiceAccountCount());
    }
    verify(roleAssignmentService, times(1)).list(roleAssignmentsPageRequest, roleAssignmentFilter, true);
  }
}
