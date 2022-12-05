package io.harness.cdng.creator.plan.steps;

import static io.harness.cdng.visitor.YamlTypes.K8S_BG_SWAP_SERVICES;
import static io.harness.cdng.visitor.YamlTypes.K8S_BLUE_GREEN_DEPLOY;
import static io.harness.executions.steps.StepSpecTypeConstants.TAS_APP_RESIZE;
import static io.harness.executions.steps.StepSpecTypeConstants.TAS_BG_APP_SETUP;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.k8s.K8sBGSwapServicesStepParameters;
import io.harness.cdng.tas.TasAppResizeStepNode;
import io.harness.cdng.tas.TasAppResizeStepParameters;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.sdk.core.steps.io.StepParameters;

import com.google.common.collect.Sets;
import java.util.Set;

@OwnedBy(HarnessTeam.CDP)
public class TasAppResizeStepPlanCreator extends CDPMSStepPlanCreatorV2<TasAppResizeStepNode> {
  @Override
  public Set<String> getSupportedStepTypes() {
    return Sets.newHashSet(TAS_APP_RESIZE);
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
    String tasSetupFqn = getExecutionStepFqn(ctx.getCurrentField(), TAS_BG_APP_SETUP);
    TasAppResizeStepParameters tasAppResizeStepParameters =
        (TasAppResizeStepParameters) ((StepElementParameters) stepParameters).getSpec();
    tasAppResizeStepParameters.setTasSetupFqn(tasSetupFqn);
    return stepParameters;
  }
}