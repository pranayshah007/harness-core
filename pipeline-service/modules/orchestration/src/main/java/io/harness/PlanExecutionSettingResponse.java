package io.harness;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PlanExecutionSettingResponse {
  boolean useNewFlow;
  boolean shouldQueue;
}
