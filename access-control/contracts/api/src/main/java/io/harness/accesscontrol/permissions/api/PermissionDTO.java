/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.permissions.api;

import io.harness.accesscontrol.permissions.PermissionStatus;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Set;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldNameConstants;

@OwnedBy(HarnessTeam.PL)
@Value
@Builder
@FieldNameConstants(innerTypeName = "PermissionDTOKeys")
@ApiModel(value = PermissionDTO.MODEL_NAME)
@Schema(name = PermissionDTO.MODEL_NAME)
public class PermissionDTO {
  public static final String MODEL_NAME = "Permission";

  @NotNull @ApiModelProperty(required = true) String identifier;
  @NotNull @ApiModelProperty(required = true) String name;
  @ApiModelProperty(required = true) PermissionStatus status;
  @ApiModelProperty(required = true) boolean includeInAllRoles;
  @ApiModelProperty(required = true) Set<String> allowedScopeLevels;
  @NotNull @ApiModelProperty(required = true) String resourceType;
  @NotNull @ApiModelProperty(required = true) String action;
}
