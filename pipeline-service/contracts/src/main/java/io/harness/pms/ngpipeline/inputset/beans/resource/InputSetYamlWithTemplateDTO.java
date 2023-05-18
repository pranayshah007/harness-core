/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.ngpipeline.inputset.beans.resource;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.annotations.ApiModel;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;
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
@ApiModel("InputSetTemplateResponse")
@Schema(name = "InputSetTemplateResponse",
    description = "This contains the Runtime Input YAML used during a Pipeline Execution.")
public class InputSetYamlWithTemplateDTO {
  // Template-Yaml at the time of execution
  @Schema(description = "Template Yaml at the time of execution") String inputSetTemplateYaml;
  // InputSet Yaml used during execution
  @Schema(description = "Input set Yaml used during execution") String inputSetYaml;
  // Execution Inputs.
  @Schema(hidden = true, description = "Execution inputs") Map<String, String> expressionValues;
}
