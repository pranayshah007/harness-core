package io.harness.pms.ngpipeline.inputset.beans.resource;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@OwnedBy(value = PIPELINE)
@Schema(name = "InputSetImportResponse", description = "Contains the details of the Saved Input Set")
public class InputSetImportResponseDTO {
  @Schema(description = "Identifier of the created Input Set") String identifier;
}
