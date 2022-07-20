package io.harness.accesscontrol.roleassignments.api;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.accesscontrol.resourcegroups.api.ResourceGroupDTO;
import io.harness.accesscontrol.roles.api.RoleResponseDTO;
import io.harness.accesscontrol.scopes.ScopeNameDTO;
import io.harness.annotations.dev.OwnedBy;

import io.swagger.annotations.ApiModel;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

@OwnedBy(PL)
@Value
@Builder
@ApiModel(value = "RoleAssignmentAggregateWithScope")
@Schema(name = "RoleAssignmentAggregateWithScope")
public class RoleAssignmentAggregateWithScope {
  RoleAssignmentResponseDTO roleAssignmentDTO;
  RoleResponseDTO role;
  ResourceGroupDTO resourceGroup;
  ScopeNameDTO scope;
}
