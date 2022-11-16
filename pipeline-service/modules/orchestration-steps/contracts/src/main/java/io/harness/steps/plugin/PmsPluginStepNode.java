package io.harness.steps.plugin;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import static com.fasterxml.jackson.annotation.JsonTypeInfo.As.EXTERNAL_PROPERTY;
import static com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NAME;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.plancreator.steps.internal.PmsAbstractStepNode;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.yaml.core.StepSpecType;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import javax.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.TypeAlias;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonTypeName(StepSpecTypeConstants.SHELL_SCRIPT)
@TypeAlias("PluginStepNode")
@OwnedBy(PIPELINE)
@RecasterAlias("io.harness.steps.plugin.PluginStepNode")
public class PmsPluginStepNode extends PmsAbstractStepNode {
  @JsonProperty("type") @NotNull StepType type = StepType.Plugin;
  @NotNull
  @JsonProperty("spec")
  @JsonTypeInfo(use = NAME, property = "type", include = EXTERNAL_PROPERTY, visible = true)
  PmsPluginStepInfo pmsPluginStepInfo;
  @Override
  public String getType() {
    return StepSpecTypeConstants.SHELL_SCRIPT;
  }

  @Override
  public StepSpecType getStepSpecType() {
    return pmsPluginStepInfo;
  }

  enum StepType {
    Plugin(StepSpecTypeConstants.PLUGIN_STEP);
    @Getter String name;
    StepType(String name) {
      this.name = name;
    }
  }
}
