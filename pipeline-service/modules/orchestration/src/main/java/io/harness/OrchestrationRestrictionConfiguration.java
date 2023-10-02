/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */
package io.harness;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

@OwnedBy(HarnessTeam.PIPELINE)
@Value
@Builder
public class OrchestrationRestrictionConfiguration {
  @JsonProperty("maxNestedLevelsCount") int maxNestedLevelsCount;
  @JsonProperty("useRestrictionForFree") boolean useRestrictionForFree;
  @JsonProperty("useRestrictionForTeam") boolean useRestrictionForTeam;
  @JsonProperty("useRestrictionForEnterprise") boolean useRestrictionForEnterprise;
  @JsonProperty("planExecutionRestriction") PlanExecutionRestrictionConfig planExecutionRestriction;
  @JsonProperty("pipelineCreationRestriction") PlanExecutionRestrictionConfig pipelineCreationRestriction;
  @JsonProperty("maxConcurrencyRestriction") PlanExecutionRestrictionConfig maxConcurrencyRestriction;
  @JsonProperty("totalParallelismStopRestriction") PlanExecutionRestrictionConfig totalParallelismStopRestriction;
  @JsonProperty("maxPipelineTimeoutInHours") MaxPipelineTimeoutInHoursConfig maxPipelineTimeoutInHoursConfig;
  @JsonProperty("maxStageTimeoutInHours") MaxStageTimeoutInHoursConfig maxStageTimeoutInHoursConfig;
  @JsonProperty("maxStepTimeoutInHours") MaxStepTimeoutInHours maxStepTimeoutInHours;
}
