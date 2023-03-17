/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.beans;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldNameConstants;

@Value
@Builder
@FieldNameConstants(innerTypeName = "AbortedByKeys")
@Schema(name = "AbortedBy", description = "This contains info of the user who aborted the pipeline")
@OwnedBy(HarnessTeam.PIPELINE)
public class AbortedBy {
  @Schema(description = "Email id of the user who aborted the pipeline") String email;
  @Schema(description = "User name of the user who aborted the pipeline") String userName;
}
