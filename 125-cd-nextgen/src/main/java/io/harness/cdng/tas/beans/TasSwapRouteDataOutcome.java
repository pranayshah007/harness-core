package io.harness.cdng.tas.beans;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.sdk.core.data.ExecutionSweepingOutput;
import io.harness.pms.sdk.core.data.Outcome;

import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Builder;
import lombok.Value;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(HarnessTeam.CDP)
@Value
@Builder
@TypeAlias("TasSwapRouteDataOutcome")
@JsonTypeName("TasSwapRouteDataOutcome")
@RecasterAlias("io.harness.cdng.tas.beans.TasSwapRouteDataOutcome")
public class TasSwapRouteDataOutcome implements Outcome, ExecutionSweepingOutput {
  boolean swapRouteOccurred;
  boolean downsizeOldApplication;
}
