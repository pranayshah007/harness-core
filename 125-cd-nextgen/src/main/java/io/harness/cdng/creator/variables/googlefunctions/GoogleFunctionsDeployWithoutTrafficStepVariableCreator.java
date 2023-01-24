package io.harness.cdng.creator.variables.googlefunctions;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.googlefunctions.deployWithoutTraffic.GoogleFunctionsDeployWithoutTrafficStepNode;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.pms.sdk.core.pipeline.variables.GenericStepVariableCreator;

import java.util.Collections;
import java.util.Set;

@OwnedBy(HarnessTeam.CDP)
public class GoogleFunctionsDeployWithoutTrafficStepVariableCreator
    extends GenericStepVariableCreator<GoogleFunctionsDeployWithoutTrafficStepNode> {
  @Override
  public Set<String> getSupportedStepTypes() {
    return Collections.singleton(StepSpecTypeConstants.GOOGLE_CLOUD_FUNCTIONS_DEPLOY_WITHOUT_TRAFFIC);
  }

  @Override
  public Class<GoogleFunctionsDeployWithoutTrafficStepNode> getFieldClass() {
    return GoogleFunctionsDeployWithoutTrafficStepNode.class;
  }
}
