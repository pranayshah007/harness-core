/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.inputset;

import io.harness.exception.ngexception.ErrorMetadataConstants;
import io.harness.exception.ngexception.ErrorMetadataDTO;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModel;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@ApiModel("InputSetErrorWrapper")
@Schema(name = "InputSetErrorWrapper", description = InputSetSchemaConstants.INPUT_SET_ERROR_WRAPPER_MESSAGE)
@JsonTypeName(ErrorMetadataConstants.INPUT_SET_ERROR)
public class InputSetErrorWrapperDTOPMS implements ErrorMetadataDTO {
  @Schema(description = InputSetSchemaConstants.INPUT_SET_ERROR_PIPELINE_YAML_MESSAGE) String errorPipelineYaml;
  @Schema(description = InputSetSchemaConstants.INPUT_SET_UUID_TO_ERROR_YAML_MESSAGE)
  Map<String, InputSetErrorResponseDTOPMS> uuidToErrorResponseMap;
  @Schema(description = "List of Input Sets that are invalid") List<String> invalidInputSetReferences;

  @Override
  public String getType() {
    return ErrorMetadataConstants.INPUT_SET_ERROR;
  }
}
