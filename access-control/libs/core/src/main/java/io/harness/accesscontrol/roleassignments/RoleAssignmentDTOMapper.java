/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.roleassignments;

import io.harness.accesscontrol.principals.PrincipalDTO;
import io.harness.accesscontrol.roleassignments.api.RoleAssignmentDTO;

public class RoleAssignmentDTOMapper {
  public static RoleAssignmentDTO toDTO(RoleAssignment object) {
    return RoleAssignmentDTO.builder()
        .identifier(object.getIdentifier())
        .principal(PrincipalDTO.builder()
                       .scopeLevel(object.getPrincipalScopeLevel())
                       .identifier(object.getPrincipalIdentifier())
                       .type(object.getPrincipalType())
                       .build())
        .resourceGroupIdentifier(object.getResourceGroupIdentifier())
        .roleIdentifier(object.getRoleIdentifier())
        .disabled(object.isDisabled())
        .managed(object.isManaged())
        .build();
  }
}
