package io.harness.cdng.chaos;

import io.harness.cdng.pipeline.CDStepInfo;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.yaml.ParameterField;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.beans.ConstructorProperties;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.Builder;

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
}
