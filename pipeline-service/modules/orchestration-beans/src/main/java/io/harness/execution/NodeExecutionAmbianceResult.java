package io.harness.execution;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;

import lombok.Builder;
import lombok.Value;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@Value
@Builder
public class NodeExecutionAmbianceResult {
  private Ambiance ambiance;
  private Status status;
  public NodeExecutionAmbianceResult(Ambiance ambiance, Status status) {
    this.ambiance = ambiance;
    this.status = status;
  }
}
