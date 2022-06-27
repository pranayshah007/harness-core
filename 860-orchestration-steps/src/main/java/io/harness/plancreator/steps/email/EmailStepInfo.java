package io.harness.plancreator.steps.email;

import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.runtime;

import io.harness.advisers.rollback.OnFailRollbackParameters;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.steps.common.WithDelegateSelector;
import io.harness.plancreator.steps.http.PmsAbstractStepNode;
import io.harness.plancreator.steps.internal.PMSStepInfo;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.steps.io.StepParameters;
import io.harness.pms.yaml.ParameterField;
import io.harness.steps.Email.EmailBaseStepInfo;
import io.harness.walktree.beans.VisitableChildren;
import io.harness.yaml.YamlSchemaTypes;

import java.util.List;

public class EmailStepInfo extends EmailBaseStepInfo implements PMSStepInfo, Visitable, WithDelegateSelector {
  @YamlSchemaTypes(value = {runtime}) ParameterField<List<TaskSelectorYaml>> delegateSelectors;
  @Override
  public ParameterField<List<TaskSelectorYaml>> fetchDelegateSelectors() {
    return getDelegateSelectors();
  }

  @Override
  public void setDelegateSelectors(ParameterField<List<TaskSelectorYaml>> delegateSelectors) {
    this.delegateSelectors = delegateSelectors;
  }

  @Override
  public VisitableChildren getChildrenToWalk() {
    return VisitableChildren.builder().build();
  }

  @Override
  public StepType getStepType() {
    return null;
  }

  @Override
  public String getFacilitatorType() {
    return OrchestrationFacilitatorType.TASK;
  }

  @Override
  public StepParameters getStepParameters(
      PmsAbstractStepNode stepElementConfig, OnFailRollbackParameters failRollbackParameters, PlanCreationContext ctx) {
    return PMSStepInfo.super.getStepParameters(stepElementConfig, failRollbackParameters, ctx);
  }
}
