package io.harness.execution.node;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;

import lombok.Builder;
import lombok.Value;
import org.springframework.data.annotation.Id;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@Value
@Builder
public class NodeExecutionAmbianceResult {
  @Id private String uuid;
  private Ambiance ambiance;
  private Status status;

  public NodeExecutionAmbianceResult(String uuid, Ambiance ambiance, Status status) {
    this.uuid = uuid;
    this.ambiance = ambiance;
    this.status = status;
  }
}
