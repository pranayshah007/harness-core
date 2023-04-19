/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;
import lombok.Data;

@OwnedBy(PIPELINE)
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(
    name = "PipelineValidationResponse", description = "This contains information about a Pipeline Validation Event.")
public class PipelineAiGenerationResponseDTO {
  @Schema(description = "Status of the Pipeline Validation Event") private final String yaml;
  @Schema(description = "Services that need to be created") private final List<String> missingServices;
  @Schema(description = "Environments that need to be created") private final List<String> missingEnvironments;
  @Schema(description = "Infra that needs to be created") private final Map<String, List<String>> missingInfra;
}
