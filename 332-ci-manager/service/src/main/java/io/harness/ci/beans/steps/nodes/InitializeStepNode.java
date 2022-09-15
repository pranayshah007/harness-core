package io.harness.beans.steps.nodes;

import static io.harness.annotations.dev.HarnessTeam.CI;

import static com.fasterxml.jackson.annotation.JsonTypeInfo.As.EXTERNAL_PROPERTY;
import static com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NAME;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.steps.CIAbstractStepNode;
import io.harness.beans.steps.CIStepInfoType;
import io.harness.beans.steps.stepinfo.InitializeStepInfo;
import io.harness.pms.yaml.ParameterField;
import io.harness.yaml.core.StepSpecType;
import io.harness.yaml.core.failurestrategy.FailureStrategyConfig;
import io.harness.yaml.core.timeout.Timeout;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.TypeAlias;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonTypeName("liteEngineTask")
@TypeAlias("InitializeStepNode")
@OwnedBy(CI)
@RecasterAlias("io.harness.beans.steps.nodes.InitializeStepNode")
public class InitializeStepNode extends CIAbstractStepNode {
  @JsonProperty("type") @NotNull StepType type = StepType.liteEngineTask;
  @NotNull
  @JsonProperty("spec")
  @JsonTypeInfo(use = NAME, property = "type", include = EXTERNAL_PROPERTY, visible = true)
  InitializeStepInfo initializeStepInfo;

  @Override
  public String getType() {
    return CIStepInfoType.INITIALIZE_TASK.getDisplayName();
  }

  @Override
  public StepSpecType getStepSpecType() {
    return initializeStepInfo;
  }

  public enum StepType {
    liteEngineTask(CIStepInfoType.INITIALIZE_TASK.getDisplayName());
    @Getter String name;

    StepType(String name) {
      this.name = name;
    }
  }

  @Builder
  public InitializeStepNode(String uuid, String identifier, String name, List<FailureStrategyConfig> failureStrategies,
      InitializeStepInfo initializeStepInfo, StepType type, ParameterField<Timeout> timeout) {
    this.setFailureStrategies(failureStrategies);
    this.initializeStepInfo = initializeStepInfo;
    this.type = type;
    this.setFailureStrategies(failureStrategies);
    this.setTimeout(timeout);
    this.setUuid(uuid);
    this.setIdentifier(identifier);
    this.setName(name);
    this.setDescription(getDescription());
  }
}
