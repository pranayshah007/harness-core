/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;
import lombok.NonNull;

@Data
@Builder
@OwnedBy(PIPELINE)
@Schema(name = "SourceIdentifierConfig", description = "Properties of the Source Pipeline")
public class SourceIdentifierConfig {
  @Schema(description = "This is the orgIdentifier of the source Pipeline") @NonNull private String orgIdentifier;
  @Schema(description = "This is the projectIdentifier of the source Pipeline")
  @NonNull
  private String projectIdentifier;
  @Schema(description = "This is the Identifier of the source Pipeline") @NonNull private String pipelineIdentifier;
}
