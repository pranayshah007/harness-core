/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution.beans.dto;

import io.harness.pms.plan.execution.PlanExecutionResourceConstants;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.annotations.ApiModel;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@ApiModel("ExecutionInputStatus")
@Schema(name = "ExecutionInputStatus",
    description = "Contains the Input Instance ID and the status If the Execution Input is valid")
public class ExecutionInputStatusDTO {
  @Schema(description = PlanExecutionResourceConstants.NODE_EXECUTION_ID_PARAM_MESSAGE) String nodeExecutionId;
  @Schema(description = "Status if Execution Input is valid or not") ExecutionInputStatus status;
}
