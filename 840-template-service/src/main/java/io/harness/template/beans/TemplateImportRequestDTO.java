package io.harness.template.beans;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.annotations.ApiModel;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldDefaults;

@OwnedBy(CDC)
@Value
@Builder
@Hidden
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@ApiModel("TemplateImportRequest")
@Schema(name = "TemplateImportRequest",
    description = "Contains basic information required to be linked with imported Template YAML")
public class TemplateImportRequestDTO {
  @Schema(description = "Expected Identifier of the Template to be imported") String templateIdentifier;
  @Schema(description = "Expected Version of the Template to be imported") String templateVersion;
  @Schema(description = "Expected Description of the Template to be imported") String templateDescription;
}
