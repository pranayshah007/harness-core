package io.harness.accesscontrol.roleassignments.api;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.accesscontrol.principals.PrincipalDTOV2;
import io.harness.accesscontrol.resourcegroups.api.ResourceGroupDTO;
import io.harness.accesscontrol.roles.api.RoleResponseDTO;
import io.harness.accesscontrol.scopes.ScopeResponseDTO;
import io.harness.annotations.dev.OwnedBy;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

@OwnedBy(PL)
@Value
@Builder
@ApiModel(value = "RoleAssignmentAggregate")
@Schema(name = "RoleAssignmentAggregate")
public class RoleAssignmentAggregate {
  String identifier;
  @ApiModelProperty(required = true) PrincipalDTOV2 principal;
  boolean disabled;
  RoleResponseDTO role;
  ResourceGroupDTO resourceGroup;
  ScopeResponseDTO scope;
  Long createdAt;
  Long lastModifiedAt;
  boolean harnessManaged;
}
