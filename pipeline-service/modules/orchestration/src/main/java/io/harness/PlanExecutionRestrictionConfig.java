package io.harness;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PlanExecutionRestrictionConfig {
  long free;
  long team;
  long enterprise;
}
