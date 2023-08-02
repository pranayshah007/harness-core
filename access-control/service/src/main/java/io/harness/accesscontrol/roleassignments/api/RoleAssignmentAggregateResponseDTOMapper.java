/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.roleassignments.api;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.accesscontrol.resourcegroups.api.ResourceGroupDTO;
import io.harness.accesscontrol.roles.api.RoleResponseDTO;
import io.harness.accesscontrol.scopes.core.Scope;
import io.harness.accesscontrol.scopes.core.ScopeMapper;
import io.harness.annotations.dev.OwnedBy;

import java.util.List;
import lombok.experimental.UtilityClass;

@OwnedBy(PL)
@UtilityClass
public class RoleAssignmentAggregateResponseDTOMapper {
  public static RoleAssignmentAggregateResponseDTO toDTO(List<RoleAssignmentDTO> roleAssignments, Scope scope,
      List<RoleResponseDTO> roles, List<ResourceGroupDTO> resourceGroups) {
    return RoleAssignmentAggregateResponseDTO.builder()
        .roleAssignments(roleAssignments)
        .scope(ScopeMapper.toDTO(scope))
        .roles(roles)
        .resourceGroups(resourceGroups)
        .build();
  }
}
