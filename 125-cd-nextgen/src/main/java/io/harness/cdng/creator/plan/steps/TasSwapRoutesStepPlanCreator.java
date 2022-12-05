package io.harness.cdng.creator.plan.steps;

import static io.harness.executions.steps.StepSpecTypeConstants.TAS_BG_APP_SETUP;
import static io.harness.executions.steps.StepSpecTypeConstants.TAS_SWAP_ROUTES;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.tas.TasAppResizeStepParameters;
import io.harness.cdng.tas.TasSwapRoutesStepNode;
import io.harness.cdng.tas.TasSwapRoutesStepParameters;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.sdk.core.steps.io.StepParameters;

import com.google.common.collect.Sets;
import java.util.Set;

@OwnedBy(HarnessTeam.CDP)
public class TasSwapRoutesStepPlanCreator extends CDPMSStepPlanCreatorV2<TasSwapRoutesStepNode> {
  @Override
  public Set<String> getSupportedStepTypes() {
    return Sets.newHashSet(TAS_SWAP_ROUTES);
  }

  @Override
  public Class<TasSwapRoutesStepNode> getFieldClass() {
    return TasSwapRoutesStepNode.class;
  }

  @Override
  public PlanCreationResponse createPlanForField(PlanCreationContext ctx, TasSwapRoutesStepNode stepElement) {
    return super.createPlanForField(ctx, stepElement);
  }

  @Override
  protected StepParameters getStepParameters(PlanCreationContext ctx, TasSwapRoutesStepNode stepElement) {
    final StepParameters stepParameters = super.getStepParameters(ctx, stepElement);
    String tasSetupFqn = getExecutionStepFqn(ctx.getCurrentField(), TAS_BG_APP_SETUP);
    TasSwapRoutesStepParameters tasSwapRoutesStepParameters =
        (TasSwapRoutesStepParameters) ((StepElementParameters) stepParameters).getSpec();
    tasSwapRoutesStepParameters.setTasSetupFqn(tasSetupFqn);
    return stepParameters;
  }
}