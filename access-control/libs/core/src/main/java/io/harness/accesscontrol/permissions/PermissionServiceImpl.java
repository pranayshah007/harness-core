/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.permissions;

import static io.harness.accesscontrol.common.filter.ManagedFilter.NO_FILTER;
import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.accesscontrol.permissions.persistence.PermissionDao;
import io.harness.accesscontrol.resources.resourcetypes.ResourceType;
import io.harness.accesscontrol.resources.resourcetypes.ResourceTypeService;
import io.harness.accesscontrol.roles.RoleService;
import io.harness.accesscontrol.roles.filter.RoleFilter;
import io.harness.accesscontrol.scopes.core.ScopeService;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnexpectedException;
import io.harness.springdata.PersistenceUtils;

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.validation.executable.ValidateOnExecution;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.springframework.transaction.support.TransactionTemplate;

@OwnedBy(PL)
@Slf4j
@Singleton
@ValidateOnExecution
public class PermissionServiceImpl implements PermissionService {
  private final PermissionDao permissionDao;
  private final ResourceTypeService resourceTypeService;
  private final ScopeService scopeService;
  private final RoleService roleService;
  private final TransactionTemplate transactionTemplate;

  private static final RetryPolicy<Object> removePermissionTransactionRetryPolicy = PersistenceUtils.getRetryPolicy(
      "[Retrying]: Failed to remove permission from roles and remove the permission; attempt: {}",
      "[Failed]: Failed to remove permission from roles and remove the permission; attempt: {}");

  private static final RetryPolicy<Object> updatePermissionTransactionRetryPolicy =
      PersistenceUtils.getRetryPolicy("[Retrying]: Failed to update permission with roles; attempt: {}",
          "[Failed]: Failed to update permission with roles; attempt: {}");

  @Inject
  public PermissionServiceImpl(PermissionDao permissionDao, ResourceTypeService resourceTypeService,
      ScopeService scopeService, RoleService roleService, TransactionTemplate transactionTemplate) {
    this.permissionDao = permissionDao;
    this.resourceTypeService = resourceTypeService;
    this.scopeService = scopeService;
    this.roleService = roleService;
    this.transactionTemplate = transactionTemplate;
  }

  @Override
  public Permission create(Permission permission) {
    if (!scopeService.areScopeLevelsValid(permission.getAllowedScopeLevels())) {
      throw new InvalidRequestException(
          String.format("The scopes provided in the permission %s are invalid. Please select scopes from [ %s ]",
              permission.getIdentifier(), String.join(",", scopeService.getAllScopeLevels())));
    }
    if (!getResourceTypeFromPermission(permission).isPresent()) {
      throw new InvalidRequestException(String.format(
          "The resource type provided in the permission %s are invalid. Please select resource types from [ %s ]",
          permission.getIdentifier(),
          String.join(",",
              resourceTypeService.list().stream().map(ResourceType::getPermissionKey).collect(Collectors.toSet()))));
    }
    return permissionDao.create(permission);
  }

  @Override
  public Optional<Permission> get(String identifier) {
    return permissionDao.get(identifier);
  }

  @Override
  public List<Permission> list(@Valid @NotNull PermissionFilter permissionFilter) {
    return permissionDao.list(permissionFilter);
  }

  @Override
  public Permission update(Permission permissionUpdate) {
    Optional<Permission> currentPermissionOptional = get(permissionUpdate.getIdentifier());
    if (!currentPermissionOptional.isPresent()) {
      throw new InvalidRequestException(
          String.format("Could not find the permission %s", permissionUpdate.getIdentifier()));
    }
    Permission currentPermission = currentPermissionOptional.get();
    return Failsafe.with(updatePermissionTransactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
      Set<String> removedScopeLevels =
          Sets.difference(currentPermission.getAllowedScopeLevels(), permissionUpdate.getAllowedScopeLevels());
      if (!removedScopeLevels.isEmpty()) {
        removePermissionsFromRolesOutOfScope(removedScopeLevels, permissionUpdate);
      }

      if (shouldIncludePermissionInAllRoles(permissionUpdate)) {
        addPermissionToAllRoles(permissionUpdate);
      } else if (PermissionStatus.INACTIVE.equals(permissionUpdate.getStatus())) {
        removePermissionFromAllRoles(permissionUpdate.getIdentifier());
      }
      permissionUpdate.setVersion(currentPermission.getVersion());
      return permissionDao.update(permissionUpdate);
    }));
  }

  @Override
  public Permission delete(String identifier) {
    return Failsafe.with(removePermissionTransactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
      removePermissionFromAllRoles(identifier);
      return permissionDao.delete(identifier);
    }));
  }

  public Optional<ResourceType> getResourceTypeFromPermission(@Valid @NotNull Permission permission) {
    return resourceTypeService.getByPermissionKey(permission.getPermissionMetadata(1));
  }

  private boolean shouldIncludePermissionInAllRoles(Permission permissionUpdate) {
    return permissionUpdate.isIncludeInAllRoles()
        && (PermissionStatus.EXPERIMENTAL.equals(permissionUpdate.getStatus())
            || PermissionStatus.ACTIVE.equals(permissionUpdate.getStatus()));
  }

  private void removePermissionsFromRolesOutOfScope(Set<String> removedScopeLevels, Permission permission) {
    if (!removedScopeLevels.isEmpty()) {
      RoleFilter roleFilter =
          RoleFilter.builder().scopeLevelsFilter(removedScopeLevels).managedFilter(NO_FILTER).build();
      boolean updateSuccessful = roleService.removePermissionFromRoles(permission.getIdentifier(), roleFilter);
      if (!updateSuccessful) {
        throw new UnexpectedException(String.format(
            "The removal of permissions from role has failed for permission, %s", permission.getIdentifier()));
      }
    }
  }

  private void removePermissionFromAllRoles(String permissionIdentifier) {
    RoleFilter roleFilter =
        RoleFilter.builder().scopeLevelsFilter(scopeService.getAllScopeLevels()).managedFilter(NO_FILTER).build();
    boolean updateSuccessful = roleService.removePermissionFromRoles(permissionIdentifier, roleFilter);
    if (!updateSuccessful) {
      throw new UnexpectedException(
          String.format("The removal of permissions from role has failed for permission, %s", permissionIdentifier));
    }
  }

  private void addPermissionToAllRoles(Permission permission) {
    RoleFilter roleFilter =
        RoleFilter.builder().scopeLevelsFilter(permission.getAllowedScopeLevels()).managedFilter(NO_FILTER).build();
    boolean updateSuccessful = roleService.addPermissionToRoles(permission.getIdentifier(), roleFilter);
    if (!updateSuccessful) {
      throw new UnexpectedException(String.format(
          "The addition of permission in roles has failed for permission, %s", permission.getIdentifier()));
    }
  }
}
