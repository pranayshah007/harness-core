/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.roleassignments.validation;

import static io.harness.accesscontrol.resources.resourcegroups.HarnessResourceGroupConstants.ALL_RESOURCES_INCLUDING_CHILD_SCOPES_RESOURCE_GROUP_IDENTIFIER;
import static io.harness.accesscontrol.resources.resourcegroups.HarnessResourceGroupConstants.DEFAULT_ACCOUNT_LEVEL_RESOURCE_GROUP_IDENTIFIER;
import static io.harness.accesscontrol.resources.resourcegroups.HarnessResourceGroupConstants.DEFAULT_ORGANIZATION_LEVEL_RESOURCE_GROUP_IDENTIFIER;
import static io.harness.accesscontrol.resources.resourcegroups.HarnessResourceGroupConstants.DEFAULT_PROJECT_LEVEL_RESOURCE_GROUP_IDENTIFIER;
import static io.harness.accesscontrol.roles.HarnessRoleConstants.ACCOUNT_ADMIN_ROLE;
import static io.harness.accesscontrol.roles.HarnessRoleConstants.ORGANIZATION_ADMIN_ROLE;
import static io.harness.accesscontrol.roles.HarnessRoleConstants.PROJECT_ADMIN_ROLE;
import static io.harness.accesscontrol.scopes.core.ScopeHelper.toParentScope;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.accesscontrol.common.validation.ValidationResult;
import io.harness.accesscontrol.commons.validation.HarnessActionValidator;
import io.harness.accesscontrol.principals.PrincipalType;
import io.harness.accesscontrol.principals.usergroups.UserGroup;
import io.harness.accesscontrol.principals.usergroups.UserGroupService;
import io.harness.accesscontrol.roleassignments.RoleAssignment;
import io.harness.accesscontrol.roleassignments.RoleAssignmentFilter;
import io.harness.accesscontrol.roleassignments.RoleAssignmentFilter.RoleAssignmentFilterBuilder;
import io.harness.accesscontrol.roleassignments.RoleAssignmentService;
import io.harness.accesscontrol.scopes.ScopeDTO;
import io.harness.accesscontrol.scopes.core.Scope;
import io.harness.accesscontrol.scopes.core.ScopeService;
import io.harness.accesscontrol.scopes.harness.ScopeMapper;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.beans.PageRequest;
import io.harness.ng.beans.PageResponse;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Optional;

@OwnedBy(HarnessTeam.PL)
@Singleton
public class RoleAssignmentActionValidator implements HarnessActionValidator<RoleAssignment> {
  private final RoleAssignmentService roleAssignmentService;
  private final UserGroupService userGroupService;
  private final ScopeService scopeService;
  private static final List<String> MANAGED_RESOURCE_GROUP_IDENTIFIERS = ImmutableList.of(
      ALL_RESOURCES_INCLUDING_CHILD_SCOPES_RESOURCE_GROUP_IDENTIFIER, DEFAULT_ACCOUNT_LEVEL_RESOURCE_GROUP_IDENTIFIER,
      DEFAULT_ORGANIZATION_LEVEL_RESOURCE_GROUP_IDENTIFIER, DEFAULT_PROJECT_LEVEL_RESOURCE_GROUP_IDENTIFIER);
  private static final List<String> MANAGED_ROLES =
      ImmutableList.of(ACCOUNT_ADMIN_ROLE, ORGANIZATION_ADMIN_ROLE, PROJECT_ADMIN_ROLE);

  @Inject
  public RoleAssignmentActionValidator(
      RoleAssignmentService roleAssignmentService, UserGroupService userGroupService, ScopeService scopeService) {
    this.roleAssignmentService = roleAssignmentService;
    this.userGroupService = userGroupService;
    this.scopeService = scopeService;
  }

  @Override
  public ValidationResult canDelete(RoleAssignment roleAssignment) {
    if (roleAssignment.isManaged()) {
      return ValidationResult.builder().valid(false).errorMessage("Cannot delete a managed role assignment").build();
    }
    Scope scope = scopeService.buildScopeFromScopeIdentifier(roleAssignment.getScopeIdentifier());
    if (MANAGED_RESOURCE_GROUP_IDENTIFIERS.stream().noneMatch(
            resourceIdentifier -> resourceIdentifier.equals(roleAssignment.getResourceGroupIdentifier()))) {
      return ValidationResult.builder().valid(true).build();
    }
    RoleAssignmentFilterBuilder builder = RoleAssignmentFilter.builder();
    RoleAssignmentFilter roleAssignmentFilter;
    if (MANAGED_ROLES.contains(roleAssignment.getRoleIdentifier())) {
      ScopeDTO scopeDTO = ScopeMapper.toDTO(scope);
      roleAssignmentFilter =
          builder
              .scopeFilter(
                  ScopeMapper.fromDTO(ScopeDTO.builder().accountIdentifier(scopeDTO.getAccountIdentifier()).build())
                      .toString())
              .roleFilter(Sets.newHashSet(ACCOUNT_ADMIN_ROLE))
              .resourceGroupFilter(Sets.newHashSet(ALL_RESOURCES_INCLUDING_CHILD_SCOPES_RESOURCE_GROUP_IDENTIFIER))
              .build();
    } else {
      return ValidationResult.builder().valid(true).build();
    }
    Optional<RoleAssignment> alternateAdminRoleAssignment =
        fetchAlternateAdminRoleAssignment(roleAssignmentFilter, roleAssignment);
    if (!alternateAdminRoleAssignment.isPresent()) {
      return ValidationResult.builder()
          .valid(false)
          .errorMessage(
              "There is no account admin present in this account. Please add an Account Admin assigned to [All Resources Including Child Scopes] Resource Group at the account level before deleting this role assignment.")
          .build();
    }
    return ValidationResult.builder().valid(true).build();
  }

  @Override
  public ValidationResult canCreate(RoleAssignment object) {
    return ValidationResult.builder().valid(true).build();
  }

  @Override
  public ValidationResult canUpdate(RoleAssignment object) {
    return ValidationResult.builder().valid(true).build();
  }

  private Optional<RoleAssignment> fetchAlternateAdminRoleAssignment(
      RoleAssignmentFilter roleAssignmentFilter, RoleAssignment roleAssignment) {
    int pageIndex = 0;
    long totalPages;
    do {
      PageResponse<RoleAssignment> response = roleAssignmentService.list(
          PageRequest.builder().pageSize(50).pageIndex(pageIndex).build(), roleAssignmentFilter);
      pageIndex++;
      totalPages = response.getTotalPages();
      List<RoleAssignment> roleAssignmentList = response.getContent();
      Optional<RoleAssignment> altRoleAssignment =
          roleAssignmentList.stream()
              .filter(r -> !r.getIdentifier().equals(roleAssignment.getIdentifier()) && isEffective(r))
              .findAny();
      if (altRoleAssignment.isPresent()) {
        return altRoleAssignment;
      }
    } while (pageIndex < totalPages);
    return Optional.empty();
  }

  private boolean isEffective(RoleAssignment roleAssignment) {
    if (!PrincipalType.USER_GROUP.equals(roleAssignment.getPrincipalType())) {
      return true;
    }
    Scope scope = toParentScope(scopeService.buildScopeFromScopeIdentifier(roleAssignment.getScopeIdentifier()),
        roleAssignment.getPrincipalScopeLevel());
    String principalScopeIdentifier = scope == null ? roleAssignment.getScopeIdentifier() : scope.toString();
    Optional<UserGroup> userGroup =
        userGroupService.get(roleAssignment.getPrincipalIdentifier(), principalScopeIdentifier);
    return userGroup.isPresent() && isNotEmpty(userGroup.get().getUsers());
  }
}
