package io.harness.cdng.chaos;

import io.harness.cdng.creator.plan.steps.CDPMSStepPlanCreatorV2;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;

import com.google.common.collect.Sets;
import java.util.Set;

public class ChaosStepPlanCreator extends CDPMSStepPlanCreatorV2<ChaosStepNode> {
  @Override
  public Set<String> getSupportedStepTypes() {
    return Sets.newHashSet(StepSpecTypeConstants.CHAOS_STEP);
  }

  @Override
  public Class<ChaosStepNode> getFieldClass() {
    return ChaosStepNode.class;
  }

  @Override
  public PlanCreationResponse createPlanForField(PlanCreationContext ctx, ChaosStepNode field) {
    return super.createPlanForField(ctx, field);
  }
}
