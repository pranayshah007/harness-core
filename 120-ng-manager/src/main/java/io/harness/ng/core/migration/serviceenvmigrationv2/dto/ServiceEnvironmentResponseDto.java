package io.harness.ng.core.migration.serviceenvmigrationv2.dto;

import io.harness.annotations.dev.OwnedBy;
import lombok.Builder;
import lombok.Value;

import java.util.List;

import static io.harness.annotations.dev.HarnessTeam.CDP;

@OwnedBy(CDP)
@Value
@Builder
public class ServiceEnvironmentResponseDto {
    List<StageMigrationFailureResponse> failures;
    String migratedPipelineYaml;
}
