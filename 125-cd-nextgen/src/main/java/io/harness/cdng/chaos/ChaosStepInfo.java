package io.harness.cdng.chaos;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.pipeline.CDStepInfo;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.steps.common.SpecParameters;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.yaml.ParameterField;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import java.beans.ConstructorProperties;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.TypeAlias;

@Data
@NoArgsConstructor
@EqualsAndHashCode
@JsonTypeName(StepSpecTypeConstants.CHAOS_STEP)
@TypeAlias("chaosStepInfo")
@OwnedBy(PIPELINE)
@RecasterAlias("io.harness.cdng.chaos.ChaosStepInfo")
public class ChaosStepInfo implements CDStepInfo {
  @JsonProperty("experimentRef") @NotNull String experimentRef;

  @Builder
  @ConstructorProperties({"experimentRef"})
  public ChaosStepInfo(String experimentRef) {
    this.experimentRef = experimentRef;
  }

  @Override
  public StepType getStepType() {
    return ChaosStep.STEP_TYPE;
  }

  @Override
  public String getFacilitatorType() {
    return OrchestrationFacilitatorType.ASYNC;
  }

  @Override
  public ParameterField<List<TaskSelectorYaml>> fetchDelegateSelectors() {
    return null;
  }

  @Override
  public void setDelegateSelectors(ParameterField<List<TaskSelectorYaml>> delegateSelectors) {}

  @Override
  public SpecParameters getSpecParameters() {
    return ChaosStepParameters.builder().experimentRef(experimentRef).build();
  }
}
