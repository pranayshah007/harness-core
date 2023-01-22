package io.harness.cdng.googlefunctions.beans;

import com.fasterxml.jackson.annotation.JsonTypeName;
import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.sdk.core.data.ExecutionSweepingOutput;
import io.harness.pms.sdk.core.data.Outcome;
import lombok.Builder;
import lombok.Value;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(HarnessTeam.CDP)
@Value
@Builder
@TypeAlias("googleFunctionPrepareRollbackOutcome")
@JsonTypeName("googleFunctionPrepareRollbackOutcome")
@RecasterAlias("io.harness.cdng.googlefunctions.GoogleFunctionPrepareRollbackOutcome")
public class GoogleFunctionPrepareRollbackOutcome implements Outcome, ExecutionSweepingOutput {
    boolean isFirstDeployment;
    String cloudRunServiceAsString;
    String cloudFunctionAsString;
}
