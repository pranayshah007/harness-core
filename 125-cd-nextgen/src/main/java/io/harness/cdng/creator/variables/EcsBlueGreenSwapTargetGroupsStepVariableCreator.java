package io.harness.cdng.creator.variables;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.ecs.EcsBlueGreenSwapTargetGroupsStepNode;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.pms.sdk.core.pipeline.variables.GenericStepVariableCreator;

import java.util.Collections;
import java.util.Set;

@OwnedBy(HarnessTeam.CDP)
public class EcsBlueGreenSwapTargetGroupsStepVariableCreator
    extends GenericStepVariableCreator<EcsBlueGreenSwapTargetGroupsStepNode> {
  @Override
  public Set<String> getSupportedStepTypes() {
    return Collections.singleton(StepSpecTypeConstants.ECS_BLUE_GREEN_SWAP_TARGET_GROUPS);
  }

  @Override
  public Class<EcsBlueGreenSwapTargetGroupsStepNode> getFieldClass() {
    return EcsBlueGreenSwapTargetGroupsStepNode.class;
  }
}
