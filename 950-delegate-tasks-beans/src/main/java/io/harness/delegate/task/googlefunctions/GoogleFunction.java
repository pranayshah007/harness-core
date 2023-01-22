package io.harness.delegate.task.googlefunctions;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import lombok.Builder;
import lombok.Value;

import javax.annotation.Nonnull;
import java.util.List;

@Value
@Builder
@OwnedBy(HarnessTeam.CDP)
public class GoogleFunction {
    String functionName;
    String runtime;
    String state;
    String environment;
    @Nonnull GoogleCloudRunService cloudRunService;
    @Nonnull  List<GoogleCloudRunRevision> activeCloudRunRevisions;

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
