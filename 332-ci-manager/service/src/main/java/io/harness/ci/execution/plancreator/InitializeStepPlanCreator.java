package io.harness.ci.plancreator;

import io.harness.beans.steps.CIStepInfoType;
import io.harness.beans.steps.nodes.InitializeStepNode;
import io.harness.ci.plan.creator.step.CIPMSStepPlanCreatorV2;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;

import com.google.common.collect.Sets;
import java.util.Set;

public class InitializeStepPlanCreator extends CIPMSStepPlanCreatorV2<InitializeStepNode> {
  @Override
  public Set<String> getSupportedStepTypes() {
    return Sets.newHashSet(CIStepInfoType.INITIALIZE_TASK.getDisplayName());
  }

  @Override
  public Class<InitializeStepNode> getFieldClass() {
    return InitializeStepNode.class;
  }

  @Override
  public PlanCreationResponse createPlanForField(PlanCreationContext ctx, InitializeStepNode stepElement) {
    return super.createPlanForField(ctx, stepElement);
  }
}
