package io.harness.plancreator.steps.email;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.runtime;

import io.harness.annotations.dev.OwnedBy;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.steps.common.SpecParameters;
import io.harness.plancreator.steps.common.WithDelegateSelector;
import io.harness.plancreator.steps.internal.PMSStepInfo;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.yaml.ParameterField;
import io.harness.steps.Email.EmailStep;
import io.harness.steps.Email.EmailStepParameters;
import io.harness.walktree.beans.VisitableChildren;
import io.harness.walktree.visitor.Visitable;
import io.harness.yaml.YamlSchemaTypes;

import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@OwnedBy(CDC)
public class EmailStepInfo implements PMSStepInfo, Visitable, WithDelegateSelector {
  @YamlSchemaTypes(value = {runtime}) ParameterField<List<TaskSelectorYaml>> delegateSelectors;
  @Override
  public ParameterField<List<TaskSelectorYaml>> fetchDelegateSelectors() {
    return getDelegateSelectors();
  }

  @Override
  public VisitableChildren getChildrenToWalk() {
    return VisitableChildren.builder().build();
  }

  @Override
  public StepType getStepType() {
    return EmailStep.STEP_TYPE;
  }

  @Override
  public String getFacilitatorType() {
    return OrchestrationFacilitatorType.TASK;
  }

  @Override
  public SpecParameters getSpecParameters() {
    return EmailStepParameters.builder()
        .body(body)
        .cc(getCc())
        .delegateSelectors(getDelegateSelectors())
        .to(getTo())
        .build();
  }
}
