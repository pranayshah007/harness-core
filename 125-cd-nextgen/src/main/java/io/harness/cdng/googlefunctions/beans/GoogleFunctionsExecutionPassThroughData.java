package io.harness.cdng.googlefunctions.beans;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.delegate.beans.logstreaming.UnitProgressData;

import lombok.Builder;
import lombok.Value;
import org.springframework.data.annotation.TypeAlias;

@Value
@Builder
@OwnedBy(CDP)
@TypeAlias("googleFunctionsExecutionPassThroughData")
@RecasterAlias("io.harness.cdng.googlefunctions.beans.GoogleFunctionsExecutionPassThroughData")
public class GoogleFunctionsExecutionPassThroughData {
  InfrastructureOutcome infrastructure;
  UnitProgressData lastActiveUnitProgressData;
}
