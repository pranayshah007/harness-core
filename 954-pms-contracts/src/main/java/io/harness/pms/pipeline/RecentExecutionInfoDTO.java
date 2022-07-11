package io.harness.pms.pipeline;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.execution.ExecutionStatus;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "RecentExecutionInfo", description = "This contains information about a recent Execution of a Pipeline",
    hidden = true)
@OwnedBy(PIPELINE)
public class RecentExecutionInfoDTO {
  ExecutorInfoDTO executorInfo;
  String planExecutionId;
  ExecutionStatus status;
  Long startTs;
  Long endTs;
}
