/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.aggregator;

import static io.harness.accesscontrol.principals.PrincipalType.USER_GROUP;

import io.harness.accesscontrol.acl.api.Principal;
import io.harness.accesscontrol.acl.persistence.ACL;
import io.harness.accesscontrol.acl.persistence.SourceMetadata;
import io.harness.accesscontrol.resources.resourcegroups.ResourceSelector;
import io.harness.accesscontrol.roleassignments.persistence.RoleAssignmentDBO;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.PL)
@UtilityClass
public class ACLUtils {
  public static ACL buildACL(
      String permission, Principal principal, RoleAssignmentDBO roleAssignment, ResourceSelector resourceSelector) {
    String scopeIdentifier, selector;
    if (resourceSelector.getSelector().contains("$")) {
      scopeIdentifier = resourceSelector.getSelector().split("\\$")[0];
      selector = resourceSelector.getSelector().split("\\$")[1];
    } else {
      scopeIdentifier = roleAssignment.getScopeIdentifier();
      selector = resourceSelector.getSelector();
    }

    return ACL.builder()
        .roleAssignmentId(roleAssignment.getId())
        .scopeIdentifier(roleAssignment.getScopeIdentifier())
        .permissionIdentifier(permission)
        .sourceMetadata(SourceMetadata.builder()
                            .roleIdentifier(roleAssignment.getRoleIdentifier())
                            .roleAssignmentIdentifier(roleAssignment.getIdentifier())
                            .resourceGroupIdentifier(roleAssignment.getResourceGroupIdentifier())
                            .userGroupIdentifier(USER_GROUP.equals(roleAssignment.getPrincipalType())
                                    ? roleAssignment.getPrincipalIdentifier()
                                    : null)
                            .userGroupScopeLevel(USER_GROUP.equals(roleAssignment.getPrincipalType())
                                    ? roleAssignment.getPrincipalScopeLevel()
                                    : roleAssignment.getScopeLevel())
                            .build())
        .resourceSelector(resourceSelector.getSelector())
        .conditional(resourceSelector.isConditional())
        .condition(resourceSelector.getCondition())
        .principalType(principal.getPrincipalType().name())
        .principalIdentifier(principal.getPrincipalIdentifier())
        .aclQueryString(ACL.getAclQueryString(scopeIdentifier, selector, principal.getPrincipalType().name(),
            principal.getPrincipalIdentifier(), permission))
        .enabled(!roleAssignment.isDisabled())
        .build();
  }
}