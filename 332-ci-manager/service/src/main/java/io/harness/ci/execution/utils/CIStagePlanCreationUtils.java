package io.harness.ci.utils;

import io.harness.beans.stages.IntegrationStageNode;
import io.harness.data.structure.CollectionUtils;
import io.harness.plancreator.steps.common.StageElementParameters;
import io.harness.plancreator.steps.common.StageElementParameters.StageElementParametersBuilder;
import io.harness.pms.tags.TagUtils;
import io.harness.pms.yaml.ParameterField;
import io.harness.steps.SdkCoreStepUtils;
import io.harness.yaml.utils.NGVariablesUtils;

public class CIStagePlanCreationUtils {
  public static StageElementParametersBuilder getStageParameters(IntegrationStageNode stageNode) {
    TagUtils.removeUuidFromTags(stageNode.getTags());

    StageElementParametersBuilder stageBuilder = StageElementParameters.builder();
    stageBuilder.name(stageNode.getName());
    stageBuilder.identifier(stageNode.getIdentifier());
    stageBuilder.description(SdkCoreStepUtils.getParameterFieldHandleValueNull(stageNode.getDescription()));
    stageBuilder.failureStrategies(stageNode.getFailureStrategies());
    stageBuilder.skipCondition(stageNode.getSkipCondition());
    stageBuilder.when(stageNode.getWhen());
    stageBuilder.type(stageNode.getType());
    stageBuilder.uuid(stageNode.getUuid());
    stageBuilder.variables(
        ParameterField.createValueField(NGVariablesUtils.getMapOfVariables(stageNode.getVariables())));
    stageBuilder.tags(CollectionUtils.emptyIfNull(stageNode.getTags()));

    return stageBuilder;
  }
}
