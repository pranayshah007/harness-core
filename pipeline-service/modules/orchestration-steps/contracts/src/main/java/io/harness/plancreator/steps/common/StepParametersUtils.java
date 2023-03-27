/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.plancreator.steps.common;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.advisers.rollback.OnFailRollbackParameters;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.CollectionUtils;
import io.harness.plancreator.stages.PmsAbstractStageNode;
import io.harness.plancreator.steps.common.StageElementParameters.StageElementParametersBuilder;
import io.harness.plancreator.steps.common.StepElementParameters.StepElementParametersBuilder;
import io.harness.plancreator.steps.internal.PmsAbstractStepNode;
import io.harness.pms.tags.TagUtils;
import io.harness.pms.yaml.ParameterField;
import io.harness.steps.SdkCoreStepUtils;
import io.harness.utils.TimeoutUtils;
import io.harness.yaml.utils.NGVariablesUtils;

import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(PIPELINE)
public class StepParametersUtils {
  public StageElementParametersBuilder getStageParameters(PmsAbstractStageNode stageNode) {
    TagUtils.removeUuidFromTags(stageNode.getTags());

    StageElementParametersBuilder stageBuilder = StageElementParameters.builder();
    stageBuilder.name(stageNode.getName());
    stageBuilder.identifier(stageNode.getIdentifier());
    stageBuilder.description(SdkCoreStepUtils.getParameterFieldHandleValueNull(stageNode.getDescription()));
    stageBuilder.failureStrategies(
        stageNode.getFailureStrategies() != null ? stageNode.getFailureStrategies().getValue() : null);
    stageBuilder.skipCondition(stageNode.getSkipCondition());
    stageBuilder.when(stageNode.getWhen() != null ? stageNode.getWhen().getValue() : null);
    stageBuilder.type(stageNode.getType());
    stageBuilder.uuid(stageNode.getUuid());
    stageBuilder.variables(
        ParameterField.createValueField(NGVariablesUtils.getMapOfVariables(stageNode.getVariables())));
    stageBuilder.tags(CollectionUtils.emptyIfNull(stageNode.getTags()));
    stageBuilder.delegateSelectors(stageNode.getDelegateSelectors());

    return stageBuilder;
  }

  public StepElementParametersBuilder getStepParameters(PmsAbstractStepNode stepElementConfig) {
    StepElementParametersBuilder stepBuilder = StepElementParameters.builder();
    stepBuilder.name(stepElementConfig.getName());
    stepBuilder.identifier(stepElementConfig.getIdentifier());
    stepBuilder.delegateSelectors(stepElementConfig.getDelegateSelectors());
    stepBuilder.description(stepElementConfig.getDescription());
    stepBuilder.skipCondition(stepElementConfig.getSkipCondition());
    stepBuilder.failureStrategies(
        stepElementConfig.getFailureStrategies() != null ? stepElementConfig.getFailureStrategies().getValue() : null);
    stepBuilder.timeout(ParameterField.createValueField(TimeoutUtils.getTimeoutString(stepElementConfig.getTimeout())));
    stepBuilder.when(stepElementConfig.getWhen() != null ? stepElementConfig.getWhen().getValue() : null);
    stepBuilder.type(stepElementConfig.getType());
    stepBuilder.uuid(stepElementConfig.getUuid());
    stepBuilder.enforce(stepElementConfig.getEnforce());

    return stepBuilder;
  }
  public StepElementParametersBuilder getStepParameters(
      PmsAbstractStepNode stepElementConfig, OnFailRollbackParameters failRollbackParameters) {
    StepElementParametersBuilder stepBuilder = getStepParameters(stepElementConfig);
    stepBuilder.rollbackParameters(failRollbackParameters);
    return stepBuilder;
  }
}
