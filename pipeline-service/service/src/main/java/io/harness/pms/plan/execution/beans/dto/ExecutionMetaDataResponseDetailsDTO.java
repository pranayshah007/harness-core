/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution.beans.dto;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.contracts.triggers.TriggerPayload;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.annotations.ApiModel;
import io.swagger.v3.oas.annotations.media.Schema;
import javax.validation.constraints.NotNull;
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
@ApiModel("ExecutionMetaDataResponse")
@Schema(name = "ExecutionMetaDataResponse", description = "This contains Execution metadata details.")
public class ExecutionMetaDataResponseDetailsDTO {
  @NotNull @Schema(description = "The plan ExecutionID") String planExecutionId;
  @Schema(description = "Execution YAML - Prepared after resolving runtime inputs and templates.") String executionYaml;
  @Schema(description = "Input YAML ") String inputYaml;
  @Schema(description = "Trigger Payload - Payload used for fetching trigger build data .")
  TriggerPayload triggerPayload;
  @Schema(description = "Resolved YAML ") String resolvedYaml;
}
