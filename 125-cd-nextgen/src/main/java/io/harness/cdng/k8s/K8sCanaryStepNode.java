package io.harness.cdng.k8s;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import static com.fasterxml.jackson.annotation.JsonTypeInfo.As.EXTERNAL_PROPERTY;
import static com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NAME;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.pipeline.CdAbstractStepNode;
import io.harness.executions.steps.StepSpecTypeConstants;
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
@JsonTypeName(StepSpecTypeConstants.K8S_CANARY_DEPLOY)
@TypeAlias("K8sCanaryStepNode")
@OwnedBy(PIPELINE)
@RecasterAlias("io.harness.cdng.k8s.K8sCanaryStepNode")
public class K8sCanaryStepNode extends CdAbstractStepNode {
  @JsonProperty("type") @NotNull StepType type = StepType.K8sCanaryDeploy;
  @JsonProperty("spec")
  @JsonTypeInfo(use = NAME, property = "type", include = EXTERNAL_PROPERTY, visible = true)
  K8sCanaryStepInfo k8sCanaryStepInfo;
  @Override
  public String getType() {
    return StepSpecTypeConstants.K8S_ROLLING_DEPLOY;
  }

  @Override
  public StepSpecType getStepSpecType() {
    return k8sCanaryStepInfo;
  }

  enum StepType {
    K8sCanaryDeploy(StepSpecTypeConstants.K8S_CANARY_DEPLOY);
    @Getter String name;
    StepType(String name) {
      this.name = name;
    }
  }
}
