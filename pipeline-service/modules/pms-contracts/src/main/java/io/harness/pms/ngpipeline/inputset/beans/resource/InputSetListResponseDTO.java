/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.ngpipeline.inputset.beans.resource;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.inputset.InputSetSchemaConstants;
import io.harness.pms.ngpipeline.inputset.beans.entity.InputSetEntityType;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.annotations.ApiModel;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

@OwnedBy(PIPELINE)
@Value
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@ApiModel("InputSetListResponse")
@Schema(name = "InputSetListResponse", description = "This is the response of InputSet list call.")
public class InputSetListResponseDTO {
  @Schema(description = InputSetSchemaConstants.INPUT_SET_ID_MESSAGE) String identifier;
  @Schema(description = InputSetSchemaConstants.INPUT_SET_NAME_MESSAGE) String name;
  @Schema(description = InputSetSchemaConstants.PIPELINE_ID_FOR_INPUT_SET_PARAM_MESSAGE) String pipelineIdentifier;
  @Schema(description = InputSetSchemaConstants.INPUT_SET_ID_WITH_PIPELINE_ID) String inputSetIdWithPipelineId;
  @Schema(description = InputSetSchemaConstants.INPUT_SET_DESCRIPTION_MESSAGE) String description;
  @Schema(description = InputSetSchemaConstants.INPUT_SET_TYPE_MESSAGE) InputSetEntityType inputSetType;
}
