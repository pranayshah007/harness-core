package io.harness.cdng.creator.variables;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.tas.TasBasicAppSetupStepNode;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.pms.sdk.core.pipeline.variables.GenericStepVariableCreator;

import java.util.Collections;
import java.util.Set;

@OwnedBy(HarnessTeam.CDP)
public class TasBasicAppSetupStepVariableCreator extends GenericStepVariableCreator<TasBasicAppSetupStepNode> {
  @Override
  public Set<String> getSupportedStepTypes() {
    return Collections.singleton(StepSpecTypeConstants.TAS_BASIC_APP_SETUP);
  }

  @Override
  public Class<TasBasicAppSetupStepNode> getFieldClass() {
    return TasBasicAppSetupStepNode.class;
  }
}
