package io.harness.cdng.creator.variables.googlefunctions;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.googlefunctions.rollback.GoogleFunctionsRollbackStepNode;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.pms.sdk.core.pipeline.variables.GenericStepVariableCreator;

import java.util.Collections;
import java.util.Set;

@OwnedBy(HarnessTeam.CDP)
public class GoogleFunctionsRollbackStepVariableCreator extends
        GenericStepVariableCreator<GoogleFunctionsRollbackStepNode> {
    @Override
    public Set<String> getSupportedStepTypes() {
        return Collections.singleton(StepSpecTypeConstants.GOOGLE_CLOUD_FUNCTIONS_ROLLBACK);
    }

    @Override
    public Class<GoogleFunctionsRollbackStepNode> getFieldClass() {
        return GoogleFunctionsRollbackStepNode.class;
    }
}