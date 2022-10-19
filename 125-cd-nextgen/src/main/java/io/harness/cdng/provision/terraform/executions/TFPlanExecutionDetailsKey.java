package io.harness.cdng.provision.terraform.executions;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;

import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;

@OwnedBy(CDP)
@Value
@Builder
public class TFPlanExecutionDetailsKey {
  @NotNull Scope scope;
  @NotNull String pipelineExecutionId;
  @NotNull String stageExecutionId;
  @Nullable String provisionerId;
}
