package io.harness.cdng.gitops;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import static com.fasterxml.jackson.annotation.JsonTypeInfo.As.EXTERNAL_PROPERTY;
import static com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NAME;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.yaml.YamlNode;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.swagger.annotations.ApiModelProperty;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@OwnedBy(CDP)
@RecasterAlias("io.harness.steps.shellscript.CreatePRStepUpdateConfigScriptWrapper")
public class CreatePRStepUpdateConfigScriptWrapper {
  @JsonProperty(YamlNode.UUID_FIELD_NAME)
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
  @ApiModelProperty(hidden = true)
  String uuid;
  @NotNull String type;
  @NotNull
  @JsonTypeInfo(use = NAME, property = "type", include = EXTERNAL_PROPERTY, visible = true)
  CreatePRStepUpdateConfigScriptBaseSource spec;

  @Builder
  public CreatePRStepUpdateConfigScriptWrapper(String type, CreatePRStepUpdateConfigScriptBaseSource spec) {
    this.type = type;
    this.spec = spec;
  }
}