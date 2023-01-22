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

import javax.annotation.Nonnull;
import java.util.List;

@OwnedBy(HarnessTeam.CDP)
@Value
@Builder
@TypeAlias("googleFunctionStepOutcome")
@JsonTypeName("googleFunctionStepOutcome")
@RecasterAlias("io.harness.cdng.googlefunctions.GoogleFunctionStepOutcome")
public class GoogleFunctionStepOutcome implements Outcome, ExecutionSweepingOutput {
    String functionName;
    String runtime;
    String state;
    String environment;
    @Nonnull GoogleCloudRunService cloudRunService;
    @Nonnull List<GoogleCloudRunRevision> activeCloudRunRevisions;

    @Value
    @Builder
    public static class GoogleCloudRunService {
        String serviceName;
        String memory;
        String revision;
    }

    @Value
    @Builder
    public static class GoogleCloudRunRevision {
        String revision;
        Integer trafficPercent;
    }
}
