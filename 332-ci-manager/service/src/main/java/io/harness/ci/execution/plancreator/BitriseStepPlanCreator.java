package io.harness.ci.plancreator;

import io.harness.beans.steps.CIStepInfoType;
import io.harness.beans.steps.nodes.BitriseStepNode;
import io.harness.ci.plan.creator.step.CIPMSStepPlanCreatorV2;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;

import com.google.common.collect.Sets;
import java.util.Set;

public class BitriseStepPlanCreator extends CIPMSStepPlanCreatorV2<BitriseStepNode> {
  @Override
  public Set<String> getSupportedStepTypes() {
    return Sets.newHashSet(CIStepInfoType.BITRISE.getDisplayName());
  }

  @Override
  public Class<BitriseStepNode> getFieldClass() {
    return BitriseStepNode.class;
  }

  @Override
  public PlanCreationResponse createPlanForField(PlanCreationContext ctx, BitriseStepNode stepElement) {
    return super.createPlanForField(ctx, stepElement);
  }
}
