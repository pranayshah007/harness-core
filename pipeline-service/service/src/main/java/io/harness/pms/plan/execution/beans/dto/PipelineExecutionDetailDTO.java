/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution.beans.dto;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ExecutionGraph;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.annotations.ApiModel;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldDefaults;

@OwnedBy(HarnessTeam.PIPELINE)
@Value
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@ApiModel("PipelineExecutionDetail")
@Schema(name = "PipelineExecutionDetail", description = "This contains the Pipeline Execution details.")
public class PipelineExecutionDetailDTO {
  PipelineExecutionSummaryDTO pipelineExecutionSummary;
  ExecutionGraph executionGraph;
}
