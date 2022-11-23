package io.harness.ng.core.migration.serviceenvmigrationv2.dto;

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.OwnedBy;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

import javax.validation.constraints.NotNull;

import static io.harness.annotations.dev.HarnessTeam.CDP;

@OwnedBy(CDP)
@Value
@Builder
@Schema(name = "stageRequest", description = "stage details defined in Harness with service-env v1.")
public class StageRequestDto {
    @NotNull String orgIdentifier;
    @NotNull String projectIdentifier;
    @NotNull @Schema(description = "YAML for the stage") String yaml;
    @NotNull @Schema(description = "infra identifier") String infraIdentifier;
}
