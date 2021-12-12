package io.harness.cvng.cdng.beans;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.harness.advisers.rollback.OnFailRollbackParameters;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cvng.cdng.CvStepParametersUtils;
import io.harness.plancreator.steps.common.SpecParameters;
import io.harness.plancreator.steps.common.StepElementParameters.StepElementParametersBuilder;
import io.harness.pms.sdk.core.steps.io.StepParameters;
import io.harness.yaml.core.StepSpecType;
import io.swagger.annotations.ApiModel;

@ApiModel(subTypes = {CVNGStepInfo.class})
@OwnedBy(HarnessTeam.CV)
public interface CVStepInfoBase extends StepParameters, StepSpecType {
    default StepParameters getStepParameters(
            CVNGAbstractStepNode stepElementConfig, OnFailRollbackParameters failRollbackParameters) {
        StepElementParametersBuilder stepParametersBuilder =
                CvStepParametersUtils.getStepParameters(stepElementConfig, failRollbackParameters);
        stepParametersBuilder.spec(getSpecParameters());
        return stepParametersBuilder.build();
    }

    @JsonIgnore
    default SpecParameters getSpecParameters() {
        return null;
    }
}
