package io.harness.cdng.infra.steps;

import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.pms.sdk.core.data.ExecutionSweepingOutput;

import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class InfrastructureTaskExecutableStepSweepingOutput implements ExecutionSweepingOutput {
  @NotNull InfrastructureOutcome infrastructureOutcome;
  boolean skipInstances;
}
