/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.pipeline;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.execution.ExecutionStatus;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.annotations.ApiModel;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@ApiModel("executionSummaryInfo")
@Schema(name = "ExecutionSummaryInfo", description = "This is the view of the Execution Summary")
@OwnedBy(PIPELINE)
public class ExecutionSummaryInfoDTO {
  List<Integer> numOfErrors; // total number of errors in the last 7 days
  List<Integer> deployments; // no of deployments for each of the last 7 days, most recent first
  Long lastExecutionTs;
  ExecutionStatus lastExecutionStatus;
  String lastExecutionId;
}
