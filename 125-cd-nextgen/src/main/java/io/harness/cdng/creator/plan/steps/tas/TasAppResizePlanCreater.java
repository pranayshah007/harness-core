package io.harness.cdng.creator.plan.steps.tas;

import io.harness.cdng.creator.plan.steps.CDPMSStepPlanCreatorV2;
import io.harness.cdng.pcf.TasAppResizeStepNode;
import io.harness.cdng.tas.TasAppResizeStepNode;
import io.harness.cdng.tas.TasAppResizeStepParameters;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.sdk.core.steps.io.StepParameters;

import com.google.common.collect.Sets;
import java.util.Set;

public class TasAppResizePlanCreater extends CDPMSStepPlanCreatorV2<TasAppResizeStepNode> {
  @Override
  public Set<String> getSupportedStepTypes() {
    return Sets.newHashSet(StepSpecTypeConstants.ECS_ROLLING_ROLLBACK);
  }

  @Override
  public Class<TasAppResizeStepNode> getFieldClass() {
    return TasAppResizeStepNode.class;
  }

  @Override
  public PlanCreationResponse createPlanForField(PlanCreationContext ctx, TasAppResizeStepNode stepElement) {
    return super.createPlanForField(ctx, stepElement);
  }

  @Override
  protected StepParameters getStepParameters(PlanCreationContext ctx, TasAppResizeStepNode stepElement) {
    final StepParameters stepParameters = super.getStepParameters(ctx, stepElement);

    // todo: ask about step name
    String tasSetupfqn = getExecutionStepFqn(ctx.getCurrentField(), null);
    TasAppResizeStepParameters tasAppResizeStepParameters =
        (TasAppResizeStepParameters) ((StepElementParameters) stepParameters).getSpec();
    tasAppResizeStepParameters.setTasSetupFqn(tasSetupfqn);
    tasAppResizeStepParameters.setDelegateSelectors(stepElement.getTasAppResizeStepInfo().getDelegateSelectors());
    return stepParameters;
  }
}
