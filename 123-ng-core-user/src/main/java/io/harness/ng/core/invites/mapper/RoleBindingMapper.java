package io.harness.ng.core.invites.mapper;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.accesscontrol.principals.PrincipalDTO;
import io.harness.accesscontrol.principals.PrincipalType;
import io.harness.accesscontrol.roleassignments.api.RoleAssignmentDTO;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.ng.core.invites.dto.RoleBinding;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;

@OwnedBy(PL)
@UtilityClass
public class RoleBindingMapper {
  public static io.harness.audit.beans.custom.user.RoleBinding toAuditRoleBinding(RoleBinding roleBinding) {
    return io.harness.audit.beans.custom.user.RoleBinding.builder()
        .roleIdentifier(roleBinding.getRoleIdentifier())
        .resourceGroupIdentifier(roleBinding.getResourceGroupIdentifier())
        .build();
  }

  public static List<io.harness.audit.beans.custom.user.RoleBinding> toAuditRoleBindings(
      List<RoleBinding> roleBindings) {
    if (roleBindings == null) {
      return null;
    }
    return roleBindings.stream().map(RoleBindingMapper::toAuditRoleBinding).collect(toList());
  }

  public static List<RoleAssignmentDTO> createRoleAssignmentDTOs(
      List<RoleBinding> roleBindings, String userId, Scope scope) {
    if (isEmpty(roleBindings)) {
      return new ArrayList<>();
    }
    return roleBindings.stream()
        .map(roleBinding -> {
          if (isBlank(roleBinding.getResourceGroupIdentifier())) {
            roleBinding.setResourceGroupIdentifier(
                getDefaultResourceGroupIdentifier(scope.getOrgIdentifier(), scope.getProjectIdentifier()));
            roleBinding.setResourceGroupName(
                getDefaultResourceGroupName(scope.getOrgIdentifier(), scope.getProjectIdentifier()));
          }
          return RoleAssignmentDTO.builder()
              .roleIdentifier(roleBinding.getRoleIdentifier())
              .resourceGroupIdentifier(roleBinding.getResourceGroupIdentifier())
              .principal(PrincipalDTO.builder().type(PrincipalType.USER).identifier(userId).build())
              .disabled(false)
              .build();
        })
        .collect(Collectors.toList());
  }

  public static String getDefaultResourceGroupIdentifier(String orgIdentifier, String projectIdentifier) {
    if (isNotEmpty(projectIdentifier)) {
      return "_all_project_resources";
    } else if (isNotEmpty(orgIdentifier)) {
      return "_all_organization_resources";
    } else {
      return "_all_account_resources";
    }
  }

  public String getDefaultResourceGroupName(String orgIdentifier, String projectIdentifier) {
    if (isNotEmpty(projectIdentifier)) {
      return "All Project Resources";
    } else if (isNotEmpty(orgIdentifier)) {
      return "All Organization Resources";
    } else {
      return "All Account Resources";
    }
  }
}
