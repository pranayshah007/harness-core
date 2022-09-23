package io.harness.ng.core.customDeployment;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.annotations.ApiModel;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;

@OwnedBy(CDP)
@Data
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@ApiModel("CustomDeploymentYaml")
public class CustomDeploymentYamlDTO {
  @NotNull String yaml;
}