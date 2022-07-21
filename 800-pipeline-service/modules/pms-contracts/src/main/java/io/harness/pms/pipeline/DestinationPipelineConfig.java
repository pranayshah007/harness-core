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
import java.util.Map;
import lombok.Builder;
import lombok.Data;
import lombok.NonNull;

@Data
@Builder
@OwnedBy(PIPELINE)
@Schema(name = "DestinationPipelineConfig", description = "Properties of the destination Pipeline")
public class DestinationPipelineConfig {
  @Schema(description = "This is the orgIdentifier of the destination Pipeline") @NonNull private String orgIdentifier;
  @Schema(description = "This is the projectIdentifier of the destination Pipeline")
  @NonNull
  private String projectIdentifier;
  @Schema(description = "This is the Identifier of the destination Pipeline")
  @NonNull
  private String pipelineIdentifier;
  @Schema(description = "This is the name of the destination Pipeline") private String pipelineName;
  @Schema(description = "This is the description details of the destination Pipeline") private String description;
  @Schema(description = "This contains the map of tags for the destination Pipeline") private Map<String, String> tags;
}
