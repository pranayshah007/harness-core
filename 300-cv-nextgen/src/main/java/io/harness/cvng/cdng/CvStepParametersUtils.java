package io.harness.cvng.cdng;

import io.harness.advisers.rollback.OnFailRollbackParameters;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cvng.cdng.beans.CVNGAbstractStepNode;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.plancreator.steps.common.StepElementParameters.StepElementParametersBuilder;
import io.harness.pms.yaml.ParameterField;
import io.harness.utils.TimeoutUtils;
import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(HarnessTeam.CV)
public class CvStepParametersUtils {
    public StepElementParametersBuilder getStepParameters(CVNGAbstractStepNode stepNode) {
        StepElementParametersBuilder stepBuilder = StepElementParameters.builder();
        stepBuilder.name(stepNode.getName());
        stepBuilder.identifier(stepNode.getIdentifier());
        stepBuilder.delegateSelectors(stepNode.getDelegateSelectors());
        stepBuilder.description(stepNode.getDescription());
        stepBuilder.skipCondition(stepNode.getSkipCondition());
        stepBuilder.failureStrategies(stepNode.getFailureStrategies());
        stepBuilder.timeout(ParameterField.createValueField(TimeoutUtils.getTimeoutString(stepNode.getTimeout())));
        stepBuilder.when(stepNode.getWhen());
        stepBuilder.type(stepNode.getType());
        stepBuilder.uuid(stepNode.getUuid());

        return stepBuilder;
    }
    public StepElementParametersBuilder getStepParameters(
            CVNGAbstractStepNode stepNode, OnFailRollbackParameters failRollbackParameters) {
        StepElementParametersBuilder stepBuilder = getStepParameters(stepNode);
        stepBuilder.rollbackParameters(failRollbackParameters);
        return stepBuilder;
    }

}
