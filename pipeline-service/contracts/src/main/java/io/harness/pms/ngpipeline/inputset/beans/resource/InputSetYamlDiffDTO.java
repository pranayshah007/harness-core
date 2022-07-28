package io.harness.pms.ngpipeline.inputset.beans.resource;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.annotations.ApiModel;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldDefaults;

@OwnedBy(PIPELINE)
@Value
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@ApiModel("InputSetYamlDiff")
@Schema(name = "InputSetYamlDiff", description = "This contains the YAML diff required to fix an Input Set")
public class InputSetYamlDiffDTO {
  String oldYAML;
  String newYAML;
  @Schema(description = "Tells whether the Input Set provides any values after removing invalid fields")
  boolean isInputSetEmpty;
  @Schema(description = "Tells whether any Input Set can provide any new values") boolean noUpdatePossible;
}
