package io.harness.ng.core.migration.serviceenvmigrationv2.dto;

import io.harness.annotations.dev.OwnedBy;
import lombok.Builder;
import lombok.Value;

import static io.harness.annotations.dev.HarnessTeam.CDP;

@OwnedBy(CDP)
@Value
@Builder
public class StageMigrationFailureResponse {
    String orgIdentifier;
    String projectIdentifier;
    String pipelineIdentifier;
    String stageIdentifier;
    String failureReason;
}
