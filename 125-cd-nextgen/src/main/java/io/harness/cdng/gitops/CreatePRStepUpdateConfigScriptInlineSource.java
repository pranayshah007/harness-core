package io.harness.cdng.gitops;

import static io.harness.annotations.dev.HarnessTeam.GITOPS;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SwaggerConstants;
import io.harness.pms.yaml.ParameterField;

import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModelProperty;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonTypeName("Inline")
@OwnedBy(GITOPS)
@RecasterAlias("io.harness.steps.shellscript.CreatePRStepUpdateConfigScriptInlineSource")
public class CreatePRStepUpdateConfigScriptInlineSource implements CreatePRStepUpdateConfigScriptBaseSource {
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) ParameterField<String> updateConfigScript;

  @Override
  public String getType() {
    return "Inline";
  }
}
