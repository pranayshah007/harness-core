package io.harness.cdng.creator.variables;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.ecs.EcsBlueGreenRollbackStepNode;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.pms.sdk.core.pipeline.variables.GenericStepVariableCreator;

import java.util.Collections;
import java.util.Set;

@OwnedBy(HarnessTeam.CDP)
public class EcsBlueGreenRollbackStepVariableCreator extends GenericStepVariableCreator<EcsBlueGreenRollbackStepNode> {
   @Override
   public Set<String> getSupportedStepTypes() {
        return Collections.singleton(StepSpecTypeConstants.ECS_BLUE_GREEN_ROLLBACK);
   }

  @Override
  public Class<EcsBlueGreenRollbackStepNode> getFieldClass() {
        return EcsBlueGreenRollbackStepNode.class;
  }
}
