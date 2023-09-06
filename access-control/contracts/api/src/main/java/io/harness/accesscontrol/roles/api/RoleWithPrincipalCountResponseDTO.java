/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.roles.api;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.accesscontrol.scopes.ScopeDTO;
import io.harness.annotations.dev.OwnedBy;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;

@OwnedBy(PL)
@Value
@Builder
@ApiModel(value = "RoleWithPrincipalCountResponse")
@Schema(name = "RoleWithPrincipalCountResponse")
public class RoleWithPrincipalCountResponseDTO {
  @NotNull @ApiModelProperty(required = true) RoleDTO role;
  @ApiModelProperty(required = true) ScopeDTO scope;
  boolean harnessManaged;
  Long createdAt;
  Long lastModifiedAt;
  Integer roleAssignedToUserCount;
  Integer roleAssignedToUserGroupCount;
  Integer roleAssignedToServiceAccountCount;
}
