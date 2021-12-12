package io.harness.ng.core.invites.mapper;

import static io.harness.NGConstants.DEFAULT_ACCOUNT_LEVEL_RESOURCE_GROUP_IDENTIFIER;
import static io.harness.NGConstants.DEFAULT_ORGANIZATION_LEVEL_RESOURCE_GROUP_IDENTIFIER;
import static io.harness.NGConstants.DEFAULT_PROJECT_LEVEL_RESOURCE_GROUP_IDENTIFIER;
import static io.harness.NGConstants.DEFAULT_RESOURCE_GROUP_IDENTIFIER;
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
import io.harness.exception.InvalidRequestException;
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
          sanitizeRoleBinding(roleBinding, scope.getOrgIdentifier(), scope.getProjectIdentifier());
          return RoleAssignmentDTO.builder()
              .roleIdentifier(roleBinding.getRoleIdentifier())
              .resourceGroupIdentifier(roleBinding.getResourceGroupIdentifier())
              .principal(PrincipalDTO.builder().type(PrincipalType.USER).identifier(userId).build())
              .disabled(false)
              .build();
        })
        .collect(Collectors.toList());
  }

  public static void sanitizeRoleBindings(
      List<RoleBinding> roleBindings, String orgIdentifier, String projectIdentifier) {
    roleBindings.forEach(roleBinding -> sanitizeRoleBinding(roleBinding, orgIdentifier, projectIdentifier));
  }

  public static void sanitizeRoleBinding(RoleBinding roleBinding, String orgIdentifier, String projectIdentifier) {
    if (isBlank(roleBinding.getResourceGroupIdentifier())) {
      roleBinding.setResourceGroupIdentifier(
          RoleBindingMapper.getDefaultResourceGroupIdentifier(orgIdentifier, projectIdentifier));
      roleBinding.setResourceGroupName(RoleBindingMapper.getDefaultResourceGroupName(orgIdentifier, projectIdentifier));
    }
  }

  public static void validateRoleBindings(
      List<RoleBinding> roleBindings, String orgIdentifier, String projectIdentifier) {
    if (isEmpty(roleBindings)) {
      return;
    }
    roleBindings.forEach(roleBinding -> {
      if (DEFAULT_RESOURCE_GROUP_IDENTIFIER.equals(roleBinding.getResourceGroupIdentifier())) {
        throw new InvalidRequestException(String.format("_all_resources is deprecated, please use %s",
            RoleBindingMapper.getDefaultResourceGroupIdentifier(orgIdentifier, projectIdentifier)));
      }
    });
  }

  public static String getDefaultResourceGroupIdentifier(String orgIdentifier, String projectIdentifier) {
    if (isNotEmpty(projectIdentifier)) {
      return DEFAULT_PROJECT_LEVEL_RESOURCE_GROUP_IDENTIFIER;
    } else if (isNotEmpty(orgIdentifier)) {
      return DEFAULT_ORGANIZATION_LEVEL_RESOURCE_GROUP_IDENTIFIER;
    } else {
      return DEFAULT_ACCOUNT_LEVEL_RESOURCE_GROUP_IDENTIFIER;
    }
  }

  public static String getDefaultResourceGroupIdentifier(Scope scope) {
    return getDefaultResourceGroupIdentifier(scope.getOrgIdentifier(), scope.getProjectIdentifier());
  }

  public String getDefaultResourceGroupName(String orgIdentifier, String projectIdentifier) {
    if (isNotEmpty(projectIdentifier)) {
      return "All Project Level Resources";
    } else if (isNotEmpty(orgIdentifier)) {
      return "All Organization Level Resources";
    } else {
      return "All Account Level Resources";
    }
  }
}
