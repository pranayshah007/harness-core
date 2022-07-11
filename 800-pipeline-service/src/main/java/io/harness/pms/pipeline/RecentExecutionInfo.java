package io.harness.pms.pipeline;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.plan.ExecutionTriggerInfo;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
@OwnedBy(PIPELINE)
public class RecentExecutionInfo {
  ExecutionTriggerInfo executionTriggerInfo;
  String planExecutionId;
  Status status;
  Long startTs;
  Long endTs;
}
