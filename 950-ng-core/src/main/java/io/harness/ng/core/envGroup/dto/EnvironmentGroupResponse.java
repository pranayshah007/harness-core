/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.envGroup.dto;

import io.harness.NGCommonEntityConstants;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@Data
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Schema(name = "EnvironmentGroup", description = "This is the view of Environment Group Entity defined in Harness")
public class EnvironmentGroupResponse {
  @Schema(description = "Environment Group Entity") EnvironmentGroupResponseDTO envGroup;
  @Schema(description = NGCommonEntityConstants.CREATED_AT_MESSAGE) Long createdAt;
  @Schema(description = NGCommonEntityConstants.UPDATED_AT_MESSAGE) Long lastModifiedAt;

  @Builder
  public EnvironmentGroupResponse(EnvironmentGroupResponseDTO environment, Long createdAt, Long lastModifiedAt) {
    this.envGroup = environment;
    this.createdAt = createdAt;
    this.lastModifiedAt = lastModifiedAt;
  }
}
