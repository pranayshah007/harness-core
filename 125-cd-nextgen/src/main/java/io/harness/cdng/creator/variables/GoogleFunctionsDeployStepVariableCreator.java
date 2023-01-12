package io.harness.cdng.creator.variables;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.googlefunctions.GoogleFunctionsDeployStepNode;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.pms.sdk.core.pipeline.variables.GenericStepVariableCreator;

import java.util.Collections;
import java.util.Set;

@OwnedBy(HarnessTeam.CDP)
public class GoogleFunctionsDeployStepVariableCreator extends GenericStepVariableCreator<GoogleFunctionsDeployStepNode> {
    @Override
    public Set<String> getSupportedStepTypes() {
        return Collections.singleton(StepSpecTypeConstants.GOOGLE_CLOUD_FUNCTIONS_DEPLOY);
    }

    @Override
    public Class<GoogleFunctionsDeployStepNode> getFieldClass() {
        return GoogleFunctionsDeployStepNode.class;
    }
}
