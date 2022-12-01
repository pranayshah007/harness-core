package io.harness.cdng.creator.variables;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.tas.TasSwapRoutesStepNode;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.pms.sdk.core.pipeline.variables.GenericStepVariableCreator;

import java.util.Collections;
import java.util.Set;

@OwnedBy(HarnessTeam.CDP)
public class TasSwapRoutesStepVariableCreator extends GenericStepVariableCreator<TasSwapRoutesStepNode> {
  @Override
  public Set<String> getSupportedStepTypes() {
    return Collections.singleton(StepSpecTypeConstants.TAS_SWAP_ROUTES);
  }

  @Override
  public Class<TasSwapRoutesStepNode> getFieldClass() {
    return TasSwapRoutesStepNode.class;
  }
}
