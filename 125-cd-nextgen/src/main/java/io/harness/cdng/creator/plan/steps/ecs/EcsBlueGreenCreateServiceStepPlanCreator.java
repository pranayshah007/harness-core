package io.harness.cdng.creator.plan.steps.ecs;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.creator.plan.steps.CDPMSStepPlanCreatorV2;
import io.harness.cdng.ecs.EcsBlueGreenCreateServiceStepNode;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;

import com.google.common.collect.Sets;
import java.util.Set;

@OwnedBy(HarnessTeam.CDP)
public class EcsBlueGreenCreateServiceStepPlanCreator
    extends CDPMSStepPlanCreatorV2<EcsBlueGreenCreateServiceStepNode> {
  @Override
  public Set<String> getSupportedStepTypes() {
    return Sets.newHashSet(StepSpecTypeConstants.ECS_BLUE_GREEN_CREATE_SERVICE);
  }

  @Override
  public Class<EcsBlueGreenCreateServiceStepNode> getFieldClass() {
    return EcsBlueGreenCreateServiceStepNode.class;
  }

  @Override
  public PlanCreationResponse createPlanForField(
      PlanCreationContext ctx, EcsBlueGreenCreateServiceStepNode stepElement) {
    return super.createPlanForField(ctx, stepElement);
  }
}
