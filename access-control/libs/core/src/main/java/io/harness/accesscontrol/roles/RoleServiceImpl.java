/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.roles;

import static io.harness.accesscontrol.common.filter.ManagedFilter.ONLY_CUSTOM;
import static io.harness.accesscontrol.common.filter.ManagedFilter.ONLY_MANAGED;
import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.accesscontrol.common.filter.ManagedFilter;
import io.harness.accesscontrol.permissions.Permission;
import io.harness.accesscontrol.permissions.PermissionFilter;
import io.harness.accesscontrol.permissions.PermissionFilter.IncludedInAllRolesFilter;
import io.harness.accesscontrol.permissions.PermissionService;
import io.harness.accesscontrol.permissions.PermissionStatus;
import io.harness.accesscontrol.principals.PrincipalType;
import io.harness.accesscontrol.roleassignments.RoleAssignment;
import io.harness.accesscontrol.roleassignments.RoleAssignmentFilter;
import io.harness.accesscontrol.roleassignments.RoleAssignmentService;
import io.harness.accesscontrol.roles.filter.RoleFilter;
import io.harness.accesscontrol.roles.persistence.RoleDao;
import io.harness.accesscontrol.scopes.core.ScopeService;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnexpectedException;
import io.harness.ng.beans.PageRequest;
import io.harness.ng.beans.PageResponse;
import io.harness.springdata.PersistenceUtils;

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.validation.executable.ValidateOnExecution;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.springframework.transaction.support.TransactionTemplate;

@OwnedBy(PL)
@Slf4j
@Singleton
@ValidateOnExecution
public class RoleServiceImpl implements RoleService {
  private final RoleDao roleDao;
  private final PermissionService permissionService;
  private final ScopeService scopeService;
  private final RoleAssignmentService roleAssignmentService;
  private final TransactionTemplate transactionTemplate;
  private static final RetryPolicy<Object> removeRoleTransactionPolicy = PersistenceUtils.getRetryPolicy(
      "[Retrying]: Failed to remove role assignments for the role and remove the role; attempt: {}",
      "[Failed]: Failed to remove role assignments for the role and remove the role; attempt: {}");

  private static final RetryPolicy<Object> removeRoleScopeLevelsTransactionPolicy = PersistenceUtils.getRetryPolicy(
      "[Retrying]: Failed to remove role assignments for the erased scope levels of the role and update the role; attempt: {}",
      "[Failed]: Failed to remove role assignments for the erased scope levels of the role and update the role; attempt: {}");

  private static final Set<PermissionStatus> ALLOWED_PERMISSION_STATUS =
      Sets.newHashSet(PermissionStatus.EXPERIMENTAL, PermissionStatus.ACTIVE, PermissionStatus.DEPRECATED);

  @Inject
  public RoleServiceImpl(RoleDao roleDao, PermissionService permissionService, ScopeService scopeService,
      RoleAssignmentService roleAssignmentService, TransactionTemplate transactionTemplate) {
    this.roleDao = roleDao;
    this.permissionService = permissionService;
    this.scopeService = scopeService;
    this.roleAssignmentService = roleAssignmentService;
    this.transactionTemplate = transactionTemplate;
  }

  @Override
  public Role create(Role role) {
    validateScopes(role);
    validatePermissions(role);
    addCompulsoryPermissions(role);
    return roleDao.create(role);
  }

  @Override
  public PageResponse<Role> list(PageRequest pageRequest, RoleFilter roleFilter, boolean hideInternal) {
    return roleDao.list(pageRequest, roleFilter, hideInternal);
  }

  @Override
  public PageResponse<RoleWithPrincipalCount> listWithPrincipalCount(
      PageRequest pageRequest, RoleFilter roleFilter, boolean hideInternal) {
    PageResponse<Role> rolePages = list(pageRequest, roleFilter, hideInternal);
    RoleAssignmentFilter roleAssignmentFilter = RoleAssignmentFilter.builder()
                                                    .scopeFilter(roleFilter.getScopeIdentifier())
                                                    .roleFilter(roleFilter.getIdentifierFilter())
                                                    .build();
    PageResponse<RoleAssignment> roleAssignmentServicePageResponse =
        roleAssignmentService.list(pageRequest, roleAssignmentFilter, true);
    Map<String, Map<PrincipalType, Integer>> countMap = new HashMap<>();

    for (RoleAssignment roleAssignment : roleAssignmentServicePageResponse.getContent()) {
      PrincipalType principalType = roleAssignment.getPrincipalType();
      if (principalType == PrincipalType.USER || principalType == PrincipalType.SERVICE_ACCOUNT
          || principalType == PrincipalType.USER_GROUP) {
        String roleIdentifier = roleAssignment.getRoleIdentifier();
        countMap.putIfAbsent(roleIdentifier, new HashMap<>());
        countMap.get(roleIdentifier)
            .put(principalType, countMap.get(roleIdentifier).getOrDefault(principalType, 0) + 1);
      }
    }

    List<RoleWithPrincipalCount> rolesWithPrincipalCount = new ArrayList<>();

    for (Role role : rolePages.getContent()) {
      String roleIdentifier = role.getIdentifier();
      Map<PrincipalType, Integer> roleIdentifierMap = countMap.get(roleIdentifier);

      RoleWithPrincipalCount roleWithCount =
          RoleWithPrincipalCount.builder()
              .role(role)
              .roleAssignedToUserCount(
                  roleIdentifierMap != null ? roleIdentifierMap.getOrDefault(PrincipalType.USER, 0) : 0)
              .roleAssignedToUserGroupCount(
                  roleIdentifierMap != null ? roleIdentifierMap.getOrDefault(PrincipalType.USER_GROUP, 0) : 0)
              .roleAssignedToServiceAccountCount(
                  roleIdentifierMap != null ? roleIdentifierMap.getOrDefault(PrincipalType.SERVICE_ACCOUNT, 0) : 0)
              .build();

      rolesWithPrincipalCount.add(roleWithCount);
    }

    return PageResponse.<RoleWithPrincipalCount>builder()
        .totalPages(rolePages.getTotalPages())
        .totalItems(rolePages.getTotalItems())
        .pageItemCount(rolePages.getPageItemCount())
        .pageSize(rolePages.getPageSize())
        .content(rolesWithPrincipalCount)
        .pageIndex(rolePages.getPageIndex())
        .empty(rolePages.isEmpty())
        .pageToken(rolePages.getPageToken())
        .build();
  }

  @Override
  public Optional<Role> get(String identifier, String scopeIdentifier, ManagedFilter managedFilter) {
    return roleDao.get(identifier, scopeIdentifier, managedFilter);
  }

  @Override
  public RoleUpdateResult update(Role roleUpdate) {
    ManagedFilter managedFilter = roleUpdate.isManaged() ? ONLY_MANAGED : ONLY_CUSTOM;
    Optional<Role> currentRoleOptional =
        get(roleUpdate.getIdentifier(), roleUpdate.getScopeIdentifier(), managedFilter);
    if (!currentRoleOptional.isPresent()) {
      throw new InvalidRequestException(
          String.format("Could not find the role in the scope %s", roleUpdate.getScopeIdentifier()));
    }
    Role currentRole = currentRoleOptional.get();
    if (areScopeLevelsUpdated(currentRole, roleUpdate) && !roleUpdate.isManaged()) {
      throw new InvalidRequestException("Cannot change the the scopes at which this role can be used.");
    }
    validatePermissions(roleUpdate);
    addCompulsoryPermissions(roleUpdate);
    roleUpdate.setVersion(currentRole.getVersion());
    roleUpdate.setCreatedAt(currentRole.getCreatedAt());
    roleUpdate.setLastModifiedAt(currentRole.getLastModifiedAt());
    Role updatedRole =
        Failsafe.with(removeRoleScopeLevelsTransactionPolicy).get(() -> transactionTemplate.execute(status -> {
          if (areScopeLevelsUpdated(currentRole, roleUpdate) && roleUpdate.isManaged()) {
            Set<String> removedScopeLevels =
                Sets.difference(currentRole.getAllowedScopeLevels(), roleUpdate.getAllowedScopeLevels());
            roleAssignmentService.deleteMulti(RoleAssignmentFilter.builder()
                                                  .roleFilter(Collections.singleton(roleUpdate.getIdentifier()))
                                                  .scopeFilter("/")
                                                  .includeChildScopes(true)
                                                  .scopeLevelFilter(removedScopeLevels)
                                                  .build());
          }
          return roleDao.update(roleUpdate);
        }));
    return RoleUpdateResult.builder().originalRole(currentRole).updatedRole(updatedRole).build();
  }

  private boolean areScopeLevelsUpdated(Role currentRole, Role roleUpdate) {
    return !currentRole.getAllowedScopeLevels().equals(roleUpdate.getAllowedScopeLevels());
  }

  @Override
  public boolean removePermissionFromRoles(String permissionIdentifier, RoleFilter roleFilter) {
    return roleDao.removePermissionFromRoles(permissionIdentifier, roleFilter);
  }

  @Override
  public boolean addPermissionToRoles(String permissionIdentifier, RoleFilter roleFilter) {
    return roleDao.addPermissionToRoles(permissionIdentifier, roleFilter);
  }

  @Override
  public Role delete(String identifier, String scopeIdentifier) {
    Optional<Role> roleOpt = get(identifier, scopeIdentifier, ONLY_CUSTOM);
    if (!roleOpt.isPresent()) {
      throw new InvalidRequestException(
          String.format("Could not find the role %s in the scope %s", identifier, scopeIdentifier));
    }
    return deleteCustomRole(identifier, scopeIdentifier);
  }

  @Override
  public Role deleteManaged(String identifier) {
    Optional<Role> roleOpt = get(identifier, null, ONLY_MANAGED);
    if (!roleOpt.isPresent()) {
      throw new InvalidRequestException(String.format("Could not find the role %s", identifier));
    }
    return deleteManagedRole(identifier);
  }

  @Override
  public long deleteMulti(RoleFilter roleFilter) {
    if (!roleFilter.getManagedFilter().equals(ONLY_CUSTOM)) {
      throw new InvalidRequestException("Can only delete custom roles");
    }
    return roleDao.deleteMulti(roleFilter);
  }

  private Role deleteManagedRole(String roleIdentifier) {
    return Failsafe.with(removeRoleTransactionPolicy).get(() -> transactionTemplate.execute(status -> {
      roleAssignmentService.deleteMulti(RoleAssignmentFilter.builder()
                                            .scopeFilter("/")
                                            .includeChildScopes(true)
                                            .roleFilter(Sets.newHashSet(roleIdentifier))
                                            .build());
      return roleDao.delete(roleIdentifier, null, true)
          .orElseThrow(
              () -> new UnexpectedException(String.format("Failed to delete the managed role %s", roleIdentifier)));
    }));
  }

  private Role deleteCustomRole(String identifier, String scopeIdentifier) {
    return Failsafe.with(removeRoleTransactionPolicy).get(() -> transactionTemplate.execute(status -> {
      Optional<Role> roleOptional = roleDao.delete(identifier, scopeIdentifier, false);
      if (roleOptional.isPresent()) {
        roleAssignmentService.deleteMulti(RoleAssignmentFilter.builder()
                                              .scopeFilter(scopeIdentifier)
                                              .roleFilter(Collections.singleton(identifier))
                                              .build());
      }
      return roleOptional.orElseThrow(
          ()
              -> new UnexpectedException(
                  String.format("Failed to delete the role %s in the scope %s", identifier, scopeIdentifier)));
    }));
  }

  private void validatePermissions(Role role) {
    PermissionFilter permissionFilter = PermissionFilter.builder()
                                            .identifierFilter(role.getPermissions())
                                            .statusFilter(ALLOWED_PERMISSION_STATUS)
                                            .build();
    List<Permission> permissionList = permissionService.list(permissionFilter);
    permissionList = permissionList == null ? new ArrayList<>() : permissionList;
    Set<String> validPermissions = permissionList.stream()
                                       .filter(permission -> {
                                         Set<String> scopesMissingInPermission = Sets.difference(
                                             role.getAllowedScopeLevels(), permission.getAllowedScopeLevels());
                                         return scopesMissingInPermission.isEmpty();
                                       })
                                       .map(Permission::getIdentifier)
                                       .collect(Collectors.toSet());
    Set<String> invalidPermissions = Sets.difference(role.getPermissions(), validPermissions);
    if (!invalidPermissions.isEmpty()) {
      log.error("Invalid permissions while creating role {} in scope {} : [ {} ]", role.getIdentifier(),
          role.getScopeIdentifier(), String.join(",", invalidPermissions));
      throw new InvalidArgumentsException(
          "Some of the specified permissions in the role are invalid or cannot be given at this scope. Please check the permissions again");
    }
  }

  private void validateScopes(Role role) {
    if (role.isManaged() && !scopeService.areScopeLevelsValid(role.getAllowedScopeLevels())) {
      throw new InvalidArgumentsException(
          String.format("The provided scopes are not registered in the service. Please select scopes out of [ %s ]",
              String.join(",", scopeService.getAllScopeLevels())));
    }
    if (!role.isManaged()) {
      String scopeLevel = scopeService.buildScopeFromScopeIdentifier(role.getScopeIdentifier()).getLevel().toString();
      if (role.getAllowedScopeLevels().size() > 1 || !role.getAllowedScopeLevels().contains(scopeLevel)) {
        throw new InvalidArgumentsException(String.format(
            "This custom role can be only used at '%s' level. Please set the allowedScopeLevels to contain only the %s level.",
            scopeLevel, scopeLevel));
      }
    }
  }

  private void addCompulsoryPermissions(Role role) {
    PermissionFilter permissionFilter =
        PermissionFilter.builder()
            .allowedScopeLevelsFilter(role.getAllowedScopeLevels())
            .statusFilter(ALLOWED_PERMISSION_STATUS)
            .includedInAllRolesFilter(IncludedInAllRolesFilter.PERMISSIONS_INCLUDED_IN_ALL_ROLES)
            .build();
    List<Permission> permissionList = permissionService.list(permissionFilter);
    permissionList = permissionList == null ? new ArrayList<>() : permissionList;
    Set<String> compulsoryPermissions = permissionList.stream()
                                            .filter(permission -> {
                                              Set<String> scopesMissingInPermission = Sets.difference(
                                                  role.getAllowedScopeLevels(), permission.getAllowedScopeLevels());
                                              return scopesMissingInPermission.isEmpty();
                                            })
                                            .map(Permission::getIdentifier)
                                            .collect(Collectors.toSet());
    role.getPermissions().addAll(compulsoryPermissions);
  }
}
