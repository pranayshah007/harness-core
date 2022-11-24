package io.harness.ci.config;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ExecutionLimits {
  ExecutionLimitSpec free;
  ExecutionLimitSpec team;
  ExecutionLimitSpec enterprise;

  @Value
  @Builder
  public static class ExecutionLimitSpec {
    long defaultTotalExecutionCount;
    long defaultMacExecutionCount;
  }
}
