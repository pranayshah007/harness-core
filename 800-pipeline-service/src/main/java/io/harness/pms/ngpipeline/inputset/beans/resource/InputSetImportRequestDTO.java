package io.harness.pms.ngpipeline.inputset.beans.resource;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@OwnedBy(value = PIPELINE)
public class InputSetImportRequestDTO {
  @Schema(description = "Name of the Input Set to be imported. This will override the Name in the YAML on Git",
      required = true)
  String inputSetName;
  @Schema(description =
              "Description of the Input Set to be imported. This will override the Description in the YAML on Git")
  String inputSetDescription;
}
