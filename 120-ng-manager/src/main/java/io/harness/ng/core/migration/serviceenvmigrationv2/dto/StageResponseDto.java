package io.harness.ng.core.migration.serviceenvmigrationv2.dto;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;

import io.swagger.v3.oas.annotations.media.Schema;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;

@OwnedBy(CDP)
@Value
@Builder
@Schema(name = "stageResponse", description = "stage details defined in Harness with service-env v2.")
public class StageResponseDto {
  @NotNull @Schema(description = "YAML for the stage") String yaml;
}
