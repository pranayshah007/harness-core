/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.ngpipeline.inputset.beans.resource;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.inputset.InputSetSchemaConstants;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.annotations.ApiModel;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Set;
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
@ApiModel("InputSetTemplateWithReplacedExpressionsResponse")
@Schema(name = "InputSetTemplateWithReplacedExpressionsResponse",
    description = "This is the Runtime Input Template for a Pipeline defined in Harness.")
public class InputSetTemplateResponseDTOPMS {
  @Schema(description = InputSetSchemaConstants.RUNTIME_INPUT_TEMPLATE_MESSAGE) String inputSetTemplateYaml;
  @Schema(description = InputSetSchemaConstants.INPUT_SET_REPLACED_EXPRESSIONS_MESSAGE)
  List<String> replacedExpressions;
  @Schema(description = InputSetSchemaConstants.INPUT_SET_MODULES_MESSAGE) Set<String> modules;
  @Schema(description = InputSetSchemaConstants.INPUT_SET_COUNT_MESSAGE) Boolean hasInputSets;
}
