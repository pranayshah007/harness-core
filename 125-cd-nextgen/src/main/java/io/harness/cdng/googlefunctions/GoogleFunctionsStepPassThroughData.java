package io.harness.cdng.googlefunctions;


import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.manifest.yaml.ManifestOutcome;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.pms.sdk.core.steps.io.PassThroughData;
import lombok.Builder;
import lombok.Value;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(HarnessTeam.CDP)
@Value
@Builder
@TypeAlias("googleFunctionsStepPassThroughData")
@RecasterAlias("io.harness.cdng.googlefunctions.GoogleFunctionsStepPassThroughData")
public class GoogleFunctionsStepPassThroughData implements PassThroughData {
    ManifestOutcome manifestOutcome;
    InfrastructureOutcome infrastructureOutcome;
    String manifestContent;
    UnitProgressData lastActiveUnitProgressData;
}
