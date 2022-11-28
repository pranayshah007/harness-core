package io.harness.plancreator.steps.pluginstep;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.sdk.core.pipeline.variables.GenericStepVariableCreator;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.steps.plugin.ContainerStepNode;

import java.util.Collections;
import java.util.Set;

@OwnedBy(HarnessTeam.PIPELINE)
public class ContainerStepVariableCreator extends GenericStepVariableCreator<ContainerStepNode> {
  @Override
  public Set<String> getSupportedStepTypes() {
    return Collections.singleton(StepSpecTypeConstants.CONTAINER_STEP);
  }
}
