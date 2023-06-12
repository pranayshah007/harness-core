/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.aggregator.consumers;

import static io.harness.accesscontrol.principals.PrincipalType.SERVICE_ACCOUNT;
import static io.harness.accesscontrol.principals.PrincipalType.USER;
import static io.harness.accesscontrol.principals.PrincipalType.USER_GROUP;
import static io.harness.accesscontrol.resources.resourcegroups.ResourceSelector.validateResourceType;
import static io.harness.accesscontrol.scopes.core.ScopeHelper.toParentScope;
import static io.harness.aggregator.ACLUtils.buildACL;
import static io.harness.aggregator.ACLUtils.buildResourceSelector;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.accesscontrol.acl.api.Principal;
import io.harness.accesscontrol.acl.persistence.ACL;
import io.harness.accesscontrol.acl.persistence.repositories.ACLRepository;
import io.harness.accesscontrol.common.filter.ManagedFilter;
import io.harness.accesscontrol.permissions.persistence.repositories.InMemoryPermissionRepository;
import io.harness.accesscontrol.principals.usergroups.UserGroup;
import io.harness.accesscontrol.principals.usergroups.UserGroupService;
import io.harness.accesscontrol.resources.resourcegroups.ResourceGroup;
import io.harness.accesscontrol.resources.resourcegroups.ResourceGroupService;
import io.harness.accesscontrol.resources.resourcegroups.ResourceSelector;
import io.harness.accesscontrol.resources.resourcegroups.ScopeSelector;
import io.harness.accesscontrol.roleassignments.persistence.RoleAssignmentDBO;
import io.harness.accesscontrol.roles.Role;
import io.harness.accesscontrol.roles.RoleService;
import io.harness.accesscontrol.scopes.core.Scope;
import io.harness.accesscontrol.scopes.core.ScopeLevel;
import io.harness.accesscontrol.scopes.core.ScopeService;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

@Slf4j
@Singleton
@OwnedBy(HarnessTeam.PL)
public class ACLGeneratorServiceImpl implements ACLGeneratorService {
  private final RoleService roleService;
  private final UserGroupService userGroupService;
  private final ResourceGroupService resourceGroupService;
  private final ScopeService scopeService;
  private final Map<Pair<ScopeLevel, Boolean>, Set<String>> implicitPermissionsByScope;
  private final ACLRepository aclRepository;
  private final boolean disableRedundantACLs;
  private final InMemoryPermissionRepository inMemoryPermissionRepository;

  @Inject
  public ACLGeneratorServiceImpl(RoleService roleService, UserGroupService userGroupService,
      ResourceGroupService resourceGroupService, ScopeService scopeService,
      Map<Pair<ScopeLevel, Boolean>, Set<String>> implicitPermissionsByScope,
      @Named(ACL.PRIMARY_COLLECTION) ACLRepository aclRepository,
      @Named("disableRedundantACLs") boolean disableRedundantACLs,
      InMemoryPermissionRepository inMemoryPermissionRepository) {
    this.roleService = roleService;
    this.userGroupService = userGroupService;
    this.resourceGroupService = resourceGroupService;
    this.scopeService = scopeService;
    this.implicitPermissionsByScope = implicitPermissionsByScope;
    this.aclRepository = aclRepository;
    this.disableRedundantACLs = disableRedundantACLs;
    this.inMemoryPermissionRepository = inMemoryPermissionRepository;
  }

  @Override
  public long createACLsForRoleAssignment(RoleAssignmentDBO roleAssignmentDBO) {
    Set<String> principals = getPrincipalsFromRoleAssignment(roleAssignmentDBO);
    return createACLsForRoleAssignment(roleAssignmentDBO, principals);
  }

  @Override
  public long createACLsForRoleAssignment(RoleAssignmentDBO roleAssignmentDBO, Set<String> principals) {
    Set<String> permissions = getPermissionsFromRole(roleAssignmentDBO);
    Set<ResourceSelector> resourceSelectors = getResourceSelectorsFromRoleAssignment(roleAssignmentDBO);
    return createACLs(roleAssignmentDBO, principals, permissions, resourceSelectors);
  }

  @Override
  public long createImplicitACLsForRoleAssignment(
      RoleAssignmentDBO roleAssignment, Set<String> addedUsers, Set<String> addedPermissions) {
    List<ACL> acls = getImplicitACLsForRoleAssignment(roleAssignment);
    if (isNotEmpty(addedPermissions)) {
      acls = acls.stream()
                 .filter(acl -> addedPermissions.contains(acl.getPermissionIdentifier()))
                 .collect(Collectors.toList());
    }
    if (isNotEmpty(addedUsers)) {
      acls = acls.stream()
                 .filter(acl
                     -> addedUsers.contains(acl.getPrincipalIdentifier()) && USER.name().equals(acl.getPrincipalType()))
                 .collect(Collectors.toList());
    }
    return aclRepository.insertAllIgnoringDuplicates(acls);
  }

  @Override
  public long createACLs(RoleAssignmentDBO roleAssignmentDBO, Set<String> principals, Set<String> permissions,
      Set<ResourceSelector> resourceSelectors) {
    long numberOfACLsCreated = 0;
    long maxACLsAllowed = 2000000;
    List<ACL> acls = new ArrayList<>();
    long aclCount = isEmpty(principals) || isEmpty(permissions) || isEmpty(resourceSelectors)
        ? 0
        : principals.size() * permissions.size() * resourceSelectors.size();
    if (aclCount > maxACLsAllowed) {
      log.error(String.format(
          "Skipping ACLs creation for roleAssignment id: %s defined at scope %s as it is attempting to create %d ACLs greater than maxAllowed %d",
          roleAssignmentDBO.getId(), roleAssignmentDBO.getScopeIdentifier(), aclCount, maxACLsAllowed));
      return 0L;
    }

    boolean isResourceTypeApplicableToPermission = true;
    for (String principalIdentifier : principals) {
      for (String permission : permissions) {
        for (ResourceSelector resourceSelector : resourceSelectors) {
          if (disableRedundantACLs) {
            isResourceTypeApplicableToPermission = validateResourceType(
                inMemoryPermissionRepository.getResourceTypeBy(permission), resourceSelector.getSelector());
          }
          if (SERVICE_ACCOUNT.equals(roleAssignmentDBO.getPrincipalType())) {
            acls.add(buildACL(permission, Principal.of(SERVICE_ACCOUNT, principalIdentifier), roleAssignmentDBO,
                resourceSelector, false, isEnabled(roleAssignmentDBO, isResourceTypeApplicableToPermission)));
          } else {
            acls.add(buildACL(permission, Principal.of(USER, principalIdentifier), roleAssignmentDBO, resourceSelector,
                false, isEnabled(roleAssignmentDBO, isResourceTypeApplicableToPermission)));
          }
          if (acls.size() >= 50000) {
            numberOfACLsCreated += aclRepository.insertAllIgnoringDuplicates(acls);
            acls.clear();
          }
        }
      }
    }
    if (acls.size() > 0) {
      numberOfACLsCreated += aclRepository.insertAllIgnoringDuplicates(acls);
      acls.clear();
    }
    return numberOfACLsCreated;
  }

  private boolean isEnabled(RoleAssignmentDBO roleAssignmentDBO, boolean isResourceTypeApplicableToPermission) {
    return !roleAssignmentDBO.isDisabled() && isResourceTypeApplicableToPermission;
  }

  private List<ACL> getImplicitACLsForRoleAssignment(RoleAssignmentDBO roleAssignment) {
    Optional<ResourceGroup> resourceGroup = resourceGroupService.get(
        roleAssignment.getResourceGroupIdentifier(), roleAssignment.getScopeIdentifier(), ManagedFilter.NO_FILTER);
    if (!resourceGroup.isPresent() || resourceGroup.get().getScopeSelectors() == null) {
      return new ArrayList<>();
    }
    Set<String> principals = getPrincipalsFromRoleAssignment(roleAssignment);
    Set<String> permissionsFromRole = getPermissionsFromRole(roleAssignment);
    List<ACL> acls = new ArrayList<>();
    for (ScopeSelector scopeSelector : resourceGroup.get().getScopeSelectors()) {
      Scope currentScope = scopeSelector.getScopeIdentifier() == null
          ? scopeService.buildScopeFromScopeIdentifier(roleAssignment.getScopeIdentifier())
          : scopeService.buildScopeFromScopeIdentifier(scopeSelector.getScopeIdentifier());
      boolean givePermissionOnChildScopes = scopeSelector.isIncludingChildScopes();
      boolean isResourceTypeApplicableToPermission = true;
      while (currentScope != null) {
        ResourceSelector resourceSelector =
            ResourceSelector.builder().selector(buildResourceSelector(currentScope)).build();
        Set<String> permissions = getPermissions(currentScope, givePermissionOnChildScopes, permissionsFromRole);
        for (String principalIdentifier : principals) {
          for (String permission : permissions) {
            if (disableRedundantACLs) {
              isResourceTypeApplicableToPermission = validateResourceType(
                  inMemoryPermissionRepository.getResourceTypeBy(permission), resourceSelector.getSelector());
            }
            if (SERVICE_ACCOUNT.equals(roleAssignment.getPrincipalType())) {
              acls.add(buildACL(permission, Principal.of(SERVICE_ACCOUNT, principalIdentifier), roleAssignment,
                  resourceSelector, true, isEnabled(roleAssignment, isResourceTypeApplicableToPermission)));
            } else {
              acls.add(buildACL(permission, Principal.of(USER, principalIdentifier), roleAssignment, resourceSelector,
                  true, isEnabled(roleAssignment, isResourceTypeApplicableToPermission)));
            }
          }
        }
        givePermissionOnChildScopes = false;
        currentScope = currentScope.getParentScope();
      }
    }
    return acls;
  }

  private Set<String> getPermissionsFromRole(RoleAssignmentDBO roleAssignment) {
    Optional<Role> role = roleService.get(
        roleAssignment.getRoleIdentifier(), roleAssignment.getScopeIdentifier(), ManagedFilter.NO_FILTER);
    if (!role.isPresent()) {
      return new HashSet<>();
    }
    return role.get().getPermissions() == null ? new HashSet<>() : role.get().getPermissions();
  }

  private Set<String> getPrincipalsFromRoleAssignment(RoleAssignmentDBO roleAssignment) {
    Set<String> principals = new HashSet<>();
    if (USER_GROUP.equals(roleAssignment.getPrincipalType())) {
      Scope scope = toParentScope(scopeService.buildScopeFromScopeIdentifier(roleAssignment.getScopeIdentifier()),
          roleAssignment.getPrincipalScopeLevel());
      String principalScopeIdentifier = scope == null ? roleAssignment.getScopeIdentifier() : scope.toString();
      Optional<UserGroup> userGroup =
          userGroupService.get(roleAssignment.getPrincipalIdentifier(), principalScopeIdentifier);
      userGroup.ifPresent(group -> principals.addAll(group.getUsers()));
    } else {
      principals.add(roleAssignment.getPrincipalIdentifier());
    }
    return principals;
  }

  private Set<ResourceSelector> getResourceSelectorsFromRoleAssignment(RoleAssignmentDBO roleAssignment) {
    Optional<ResourceGroup> resourceGroup = resourceGroupService.get(
        roleAssignment.getResourceGroupIdentifier(), roleAssignment.getScopeIdentifier(), ManagedFilter.NO_FILTER);
    if (!resourceGroup.isPresent()) {
      return new HashSet<>();
    }
    Set<ResourceSelector> resourceSelectors = new HashSet<>();
    if (resourceGroup.get().getResourceSelectors() != null) {
      resourceSelectors.addAll(resourceGroup.get()
                                   .getResourceSelectors()
                                   .stream()
                                   .map(selector -> ResourceSelector.builder().selector(selector).build())
                                   .collect(Collectors.toList()));
    }
    if (resourceGroup.get().getResourceSelectorsV2() != null) {
      resourceSelectors.addAll(resourceGroup.get().getResourceSelectorsV2());
    }
    return resourceSelectors;
  }

  private Set<String> getPermissions(Scope scope, boolean givePermissionOnChildScopes, Set<String> permissionsFilter) {
    Set<String> permissions = implicitPermissionsByScope.get(Pair.of(scope.getLevel(), givePermissionOnChildScopes));
    return permissions.stream().filter(permissionsFilter::contains).collect(Collectors.toSet());
  }
}
