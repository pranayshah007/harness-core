/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.principals;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldNameConstants;

@Value
@Builder
@FieldNameConstants(innerTypeName = "PrincipalDTOV2Keys")
@ApiModel(value = "PrincipalV2")
@Schema(name = "PrincipalV2")
@OwnedBy(PL)
public class PrincipalDTOV2 {
  public static final String MODEL_NAME = "PrincipalV2";

  String scopeLevel;
  @ApiModelProperty(required = true) String identifier;
  String name;
  @ApiModelProperty(required = true) PrincipalType type;
}
