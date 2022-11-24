package io.harness.cdng.gitops.beans;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.gitops.steps.FetchLinkedAppsStep;
import io.harness.cdng.pipeline.CDStepInfo;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.steps.common.SpecParameters;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.yaml.ParameterField;

import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(HarnessTeam.GITOPS)
@Data
@NoArgsConstructor
@JsonTypeName(StepSpecTypeConstants.GITOPS_FETCH_LINKED_APPS)
@TypeAlias("FetchLinkedAppsStepInfo")
@RecasterAlias("io.harness.cdng.gitops.beans.FetchLinkedAppsStepInfo")
public class FetchLinkedAppsStepInfo implements CDStepInfo {
  @Override
  public StepType getStepType() {
    return FetchLinkedAppsStep.STEP_TYPE;
  }

  @Override
  public String getFacilitatorType() {
    return OrchestrationFacilitatorType.SYNC;
  }

  @Override
  public ParameterField<List<TaskSelectorYaml>> fetchDelegateSelectors() {
    return null;
  }

  @Override
  public void setDelegateSelectors(ParameterField<List<TaskSelectorYaml>> delegateSelectors) {}

  @Override
  public SpecParameters getSpecParameters() {
    return FetchLinkedAppsStepParams.infoBuilder().build();
  }
}
