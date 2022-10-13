package io.harness.ng.core.deploymentstage;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldDefaults;

@Value
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonInclude(NON_NULL)
@OwnedBy(HarnessTeam.CDC)
@Schema(name = "ServiceRequest", description = "Service Request details defined in Harness.")
public class CdDeployStageMetadataRequestDTO {
  @Schema(description = "Stage Identifier") @NotNull String stageIdentifier;
  @Schema(description = "Pipeline yaml string to be parsed") @NotNull String pipelineYaml;
}
