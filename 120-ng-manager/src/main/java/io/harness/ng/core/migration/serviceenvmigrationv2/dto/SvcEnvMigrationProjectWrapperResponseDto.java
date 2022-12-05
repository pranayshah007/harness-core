package io.harness.ng.core.migration.serviceenvmigrationv2.dto;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;

import java.util.List;
import lombok.Builder;
import lombok.Value;

@OwnedBy(CDP)
@Value
@Builder
public class SvcEnvMigrationProjectWrapperResponseDto {
  List<StageMigrationFailureResponse> failures;
  List<String> migratedPipelines;
}
