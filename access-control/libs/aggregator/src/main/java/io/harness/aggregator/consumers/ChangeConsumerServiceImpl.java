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
import static io.harness.accesscontrol.scopes.core.ScopeHelper.toParentScope;
import static io.harness.aggregator.ACLUtils.buildACL;

import io.harness.accesscontrol.acl.api.Principal;
import io.harness.accesscontrol.acl.persistence.ACL;
import io.harness.accesscontrol.common.filter.ManagedFilter;
import io.harness.accesscontrol.principals.usergroups.UserGroup;
import io.harness.accesscontrol.principals.usergroups.UserGroupService;
import io.harness.accesscontrol.resources.resourcegroups.ResourceGroup;
import io.harness.accesscontrol.resources.resourcegroups.ResourceGroupService;
import io.harness.accesscontrol.resources.resourcegroups.ResourceSelector;
import io.harness.accesscontrol.roleassignments.persistence.RoleAssignmentDBO;
import io.harness.accesscontrol.roles.Role;
import io.harness.accesscontrol.roles.RoleService;
import io.harness.accesscontrol.scopes.core.Scope;
import io.harness.accesscontrol.scopes.core.ScopeService;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;

@Singleton
@OwnedBy(HarnessTeam.PL)
@AllArgsConstructor(onConstructor = @__({ @Inject }))
public class ChangeConsumerServiceImpl implements ChangeConsumerService {
  private final RoleService roleService;
  private final UserGroupService userGroupService;
  private final ResourceGroupService resourceGroupService;
  private final ScopeService scopeService;

  @Override
  public List<ACL> getAClsForRoleAssignment(RoleAssignmentDBO roleAssignment) {
    Optional<Role> role = roleService.get(
        roleAssignment.getRoleIdentifier(), roleAssignment.getScopeIdentifier(), ManagedFilter.NO_FILTER);
    Optional<ResourceGroup> resourceGroup = resourceGroupService.get(
        roleAssignment.getResourceGroupIdentifier(), roleAssignment.getScopeIdentifier(), ManagedFilter.NO_FILTER);
    if (!role.isPresent() || !resourceGroup.isPresent()) {
      return new ArrayList<>();
    }

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

    List<ACL> acls = new ArrayList<>();
    for (String permission : role.get().getPermissions()) {
      for (String principalIdentifier : principals) {
        for (ResourceSelector resourceSelector : resourceSelectors) {
          if (SERVICE_ACCOUNT.equals(roleAssignment.getPrincipalType())) {
            acls.add(buildACL(
                permission, Principal.of(SERVICE_ACCOUNT, principalIdentifier), roleAssignment, resourceSelector));
          } else {
            acls.add(buildACL(permission, Principal.of(USER, principalIdentifier), roleAssignment, resourceSelector));
          }
        }
      }
    }
    return acls;
  }
}
