package io.harness.cdng.creator.plan.steps;

import static io.harness.executions.steps.StepSpecTypeConstants.TAS_ROLLBACK;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.tas.TasRollbackStepNode;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.sdk.core.steps.io.StepParameters;

import com.google.common.collect.Sets;
import java.util.Set;

@OwnedBy(HarnessTeam.CDP)
public class TasRollbackStepPlanCreator extends CDPMSStepPlanCreatorV2<TasRollbackStepNode> {
  @Override
  public Set<String> getSupportedStepTypes() {
    return Sets.newHashSet(TAS_ROLLBACK);
  }

  @Override
  public Class<TasRollbackStepNode> getFieldClass() {
    return TasRollbackStepNode.class;
  }

  @Override
  public PlanCreationResponse createPlanForField(PlanCreationContext ctx, TasRollbackStepNode stepElement) {
    return super.createPlanForField(ctx, stepElement);
  }

  @Override
  protected StepParameters getStepParameters(PlanCreationContext ctx, TasRollbackStepNode stepElement) {
    return super.getStepParameters(ctx, stepElement);
  }
}