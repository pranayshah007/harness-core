/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.governance.GovernanceMetadata;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@OwnedBy(PIPELINE)
@Data
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(
    name = "PipelineValidationResponse", description = "This contains information about a Pipeline Validation Event.")
public class PipelineValidationResponseDTO {
  @Schema(description = "Status of the Pipeline Validation Event") String status;
  @Schema(description = "Start time of the Evaluation") long startTs;
  @Schema(description = "End time of the Evaluation") long endTs;

  @Schema(description = "Result of Policy Evaluations on the Pipeline") GovernanceMetadata policyEval;
}
