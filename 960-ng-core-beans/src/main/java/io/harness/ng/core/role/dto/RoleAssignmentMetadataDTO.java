/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.role.dto;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;

import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

@OwnedBy(PL)
@Value
@Builder
@Schema(name = "RoleAssignmentMetadata",
    description = "This has information of Role like name, id, resource group name, etc.")
public class RoleAssignmentMetadataDTO {
  @ApiModelProperty(required = true) String identifier;
  @ApiModelProperty(required = true) String roleIdentifier;
  @ApiModelProperty(required = true) String roleName;
  @ApiModelProperty(required = true) String resourceGroupIdentifier;
  @ApiModelProperty(required = true) String resourceGroupName;
  @ApiModelProperty(required = true) boolean managedRole;
  @ApiModelProperty(required = true) boolean managedRoleAssignment;
}
