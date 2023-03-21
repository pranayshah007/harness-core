/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.user.remote.dto;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.role.dto.RoleAssignmentMetadataDTO;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@ApiModel(value = "UserAggregate")
@OwnedBy(PL)
@Schema(name = "UserAggregate", description = "Returns User's metadata and Role Assignments metadata")
public class UserAggregateDTO {
  @ApiModelProperty(required = true) UserMetadataDTO user;
  @ApiModelProperty(required = true) List<RoleAssignmentMetadataDTO> roleAssignmentMetadata;
}
