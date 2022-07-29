package io.harness.provision;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TerraformPlanSummary {
  private int commandExitCode;
  private int add;
  private int change;
  private int destroy;
  private boolean changesExist;
}
