/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.annotations.ApiModel;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
@ApiModel("ClonePipelineProperties")
@OwnedBy(PIPELINE)
@Schema(name = "ClonePipelineProperties", description = "Properties of the Clone Pipeline defined in Harness")
public class ClonePipelineDTO {
  @Schema(description = "This is the source config of the Pipeline from where the pipeline will be cloned.")
  private SourceIdentifierConfig sourceConfig;
  @Schema(description = "This is the destination config of the Pipeline that will be saved after its cloned.")
  private DestinationPipelineConfig destinationConfig;
  @Schema(description = "This is the Pipeline's Clone Config that will be applied.", hidden = true)
  private CloneConfig cloneConfig;
}
