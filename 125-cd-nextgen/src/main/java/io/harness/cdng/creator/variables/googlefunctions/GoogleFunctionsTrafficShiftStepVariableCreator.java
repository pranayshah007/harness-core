package io.harness.cdng.creator.variables.googlefunctions;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.googlefunctions.rollback.GoogleFunctionsRollbackStepNode;
import io.harness.cdng.googlefunctions.trafficShift.GoogleFunctionsTrafficShiftStepNode;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.pms.sdk.core.pipeline.variables.GenericStepVariableCreator;

import java.util.Collections;
import java.util.Set;

@OwnedBy(HarnessTeam.CDP)
public class GoogleFunctionsTrafficShiftStepVariableCreator extends
        GenericStepVariableCreator<GoogleFunctionsTrafficShiftStepNode> {
    @Override
    public Set<String> getSupportedStepTypes() {
        return Collections.singleton(StepSpecTypeConstants.GOOGLE_CLOUD_FUNCTIONS_TRAFFIC_SHIFT);
    }

    @Override
    public Class<GoogleFunctionsTrafficShiftStepNode> getFieldClass() {
        return GoogleFunctionsTrafficShiftStepNode.class;
    }
}
