package io.harness.accesscontrol.roleassignments.validation;

import static io.harness.accesscontrol.resources.resourcegroups.HarnessResourceGroupConstants.DEFAULT_ACCOUNT_LEVEL_RESOURCE_GROUP_IDENTIFIER;
import static io.harness.accesscontrol.resources.resourcegroups.HarnessResourceGroupConstants.DEFAULT_ORGANIZATION_LEVEL_RESOURCE_GROUP_IDENTIFIER;
import static io.harness.accesscontrol.resources.resourcegroups.HarnessResourceGroupConstants.DEFAULT_PROJECT_LEVEL_RESOURCE_GROUP_IDENTIFIER;
import static io.harness.accesscontrol.resources.resourcegroups.HarnessResourceGroupConstants.DEFAULT_RESOURCE_GROUP_IDENTIFIER;
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
import io.harness.accesscontrol.scopes.core.Scope;
import io.harness.accesscontrol.scopes.core.ScopeService;
import io.harness.accesscontrol.scopes.harness.HarnessScopeLevel;
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
  private static final String PROJECT_ADMIN = "_project_admin";
  private static final String ORG_ADMIN = "_organization_admin";
  private static final String ACCOUNT_ADMIN = "_account_admin";
  private static final List<String> MANAGED_RESOURCE_GROUP_IDENTIFIERS =
      ImmutableList.of(DEFAULT_RESOURCE_GROUP_IDENTIFIER, DEFAULT_ACCOUNT_LEVEL_RESOURCE_GROUP_IDENTIFIER,
          DEFAULT_ORGANIZATION_LEVEL_RESOURCE_GROUP_IDENTIFIER, DEFAULT_PROJECT_LEVEL_RESOURCE_GROUP_IDENTIFIER);

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
    if (HarnessScopeLevel.ACCOUNT.equals(scope.getLevel())
        && ACCOUNT_ADMIN.equals(roleAssignment.getRoleIdentifier())) {
      roleAssignmentFilter = builder.scopeFilter(roleAssignment.getScopeIdentifier())
                                 .roleFilter(Sets.newHashSet(ACCOUNT_ADMIN))
                                 .resourceGroupFilter(Sets.newHashSet(DEFAULT_ACCOUNT_LEVEL_RESOURCE_GROUP_IDENTIFIER))
                                 .build();
    } else if (HarnessScopeLevel.ORGANIZATION.equals(scope.getLevel())
        && ORG_ADMIN.equals(roleAssignment.getRoleIdentifier())) {
      roleAssignmentFilter =
          builder.scopeFilter(roleAssignment.getScopeIdentifier())
              .roleFilter(Sets.newHashSet(ORG_ADMIN))
              .resourceGroupFilter(Sets.newHashSet(DEFAULT_ORGANIZATION_LEVEL_RESOURCE_GROUP_IDENTIFIER))
              .build();
    } else if (HarnessScopeLevel.PROJECT.equals(scope.getLevel())
        && PROJECT_ADMIN.equals(roleAssignment.getRoleIdentifier())) {
      roleAssignmentFilter = builder.scopeFilter(roleAssignment.getScopeIdentifier())
                                 .roleFilter(Sets.newHashSet(PROJECT_ADMIN))
                                 .resourceGroupFilter(Sets.newHashSet(DEFAULT_PROJECT_LEVEL_RESOURCE_GROUP_IDENTIFIER))
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
              "Please add another Admin assigned to All Resources Resource Group before deleting this role assignment.")
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
    Optional<UserGroup> userGroup =
        userGroupService.get(roleAssignment.getPrincipalIdentifier(), roleAssignment.getScopeIdentifier());
    return userGroup.isPresent() && isNotEmpty(userGroup.get().getUsers());
  }
}
