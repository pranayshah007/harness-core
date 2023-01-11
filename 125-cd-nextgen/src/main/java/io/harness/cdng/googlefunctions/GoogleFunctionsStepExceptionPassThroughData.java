package io.harness.cdng.googlefunctions;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.pms.sdk.core.steps.io.PassThroughData;
import lombok.Builder;
import lombok.Value;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(HarnessTeam.CDP)
@Value
@Builder
@TypeAlias("googleFunctionsStepExceptionPassThroughData")
@RecasterAlias("io.harness.cdng.googlefunctions.GoogleFunctionsStepExceptionPassThroughData")
public class GoogleFunctionsStepExceptionPassThroughData implements PassThroughData {
    String errorMsg;
    UnitProgressData unitProgressData;
}
