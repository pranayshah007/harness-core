package io.harness.ng.core.migration.serviceenvmigrationv2.dto;

import io.harness.annotations.dev.OwnedBy;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

import javax.validation.constraints.NotNull;

import static io.harness.annotations.dev.HarnessTeam.CDP;

@OwnedBy(CDP)
@Value
@Builder
@Schema(name = "stageResponse", description = "stage details defined in Harness with service-env v2.")
public class StageResponseDto {
    @NotNull @Schema(description = "YAML for the stage") String yaml;
}
