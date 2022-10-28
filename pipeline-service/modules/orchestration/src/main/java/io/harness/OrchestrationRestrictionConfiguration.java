package io.harness;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class OrchestrationRestrictionConfiguration {
  @JsonProperty("useRestrictionForFree") boolean useRestrictionForFree;
  @JsonProperty("useRestrictionForTeam") boolean useRestrictionForTeam;
  @JsonProperty("useRestrictionForEnterprise") boolean useRestrictionForEnterprise;
  @JsonProperty("planExecutionRestriction") PlanExecutionRestrictionConfig planExecutionRestriction;
  @JsonProperty("pipelineCreationRestriction") PlanExecutionRestrictionConfig pipelineCreationRestriction;
}
