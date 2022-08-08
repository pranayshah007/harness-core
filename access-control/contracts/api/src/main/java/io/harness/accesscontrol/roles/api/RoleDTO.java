/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.roles.api;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;
import java.util.Set;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;

@OwnedBy(HarnessTeam.PL)
@Getter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@AllArgsConstructor
@EqualsAndHashCode
@FieldNameConstants(innerTypeName = "RoleDTOKeys")
@ApiModel(value = RoleDTO.MODEL_NAME)
@Schema(name = RoleDTO.MODEL_NAME)
public class RoleDTO {
  public static final String MODEL_NAME = "Role";

  @Schema(description = "Unique identifier of the role")
  @NotNull
  @ApiModelProperty(required = true)
  final String identifier;
  @Schema(description = "Name of the role") @NotNull @ApiModelProperty(required = true) final String name;
  @Schema(
      description = "List of the permission identifiers (Subset of the list returned by GET /authz/api/permissions)")
  final Set<String> permissions;
  @Schema(description = "The scope levels at which this role can be used",
      allowableValues = {"account", "organization", "project"})
  @Setter
  Set<String> allowedScopeLevels;
  @Schema(description = "Description of the role") final String description;
  @Schema(description = "Tags") final Map<String, String> tags;
}
