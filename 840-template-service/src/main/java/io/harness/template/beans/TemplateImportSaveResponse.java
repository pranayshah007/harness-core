package io.harness.template.beans;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

@OwnedBy(HarnessTeam.CDC)
@Value
@Builder
@Hidden
@Schema(name = "TemplateImportSaveResponse",
    description = "Contains the Template details for the given Template ID and version")
public class TemplateImportSaveResponse {
  String templateIdentifier;
  String templateVersion;
}
