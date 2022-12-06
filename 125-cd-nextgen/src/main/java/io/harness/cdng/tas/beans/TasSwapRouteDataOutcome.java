package io.harness.cdng.tas.beans;

import com.fasterxml.jackson.annotation.JsonTypeName;
import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.pcf.CfAppSetupTimeDetails;
import io.harness.delegate.beans.pcf.CfServiceData;
import io.harness.delegate.beans.pcf.ResizeStrategy;
import io.harness.pcf.model.CfCliVersion;
import io.harness.pms.sdk.core.data.ExecutionSweepingOutput;
import io.harness.pms.sdk.core.data.Outcome;
import lombok.Builder;
import lombok.Value;
import org.springframework.data.annotation.TypeAlias;

import java.util.List;

@OwnedBy(HarnessTeam.CDP)
@Value
@Builder
@TypeAlias("TasSwapRouteDataOutcome")
@JsonTypeName("TasSwapRouteDataOutcome")
@RecasterAlias("io.harness.cdng.tas.beans.TasSwapRouteDataOutcome")
public class TasSwapRouteDataOutcome implements Outcome, ExecutionSweepingOutput {

  boolean swapRouteOccurred;
}
