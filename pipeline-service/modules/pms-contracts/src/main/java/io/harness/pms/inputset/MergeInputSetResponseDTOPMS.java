/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.inputset;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldDefaults;

@OwnedBy(PIPELINE)
@Value
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@ApiModel("MergeInputSetResponse")
@Schema(name = "MergeInputSetResponse", description = "View of the Response of Merging of Input Sets of a Pipeline")
public class MergeInputSetResponseDTOPMS {
  @Schema(description = "Merged YAML of all the Input Sets") String pipelineYaml;
  @Schema(description = "Pipeline YAML after merging with the Input Sets") String completePipelineYaml;

  @Schema(description = "This field is true if the merging is not possible")
  @ApiModelProperty(name = "isErrorResponse")
  boolean isErrorResponse;
  @Schema(description = "This field contains the errors encountered while merging Input Sets")
  InputSetErrorWrapperDTOPMS inputSetErrorWrapper;
}
