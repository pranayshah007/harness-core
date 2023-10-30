/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.plancreator.steps;

import io.harness.advisers.rollback.OnFailRollbackParameters;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.CollectionUtils;
import io.harness.plancreator.stages.stage.StageElementConfig;
import io.harness.plancreator.steps.common.StageElementParameters;
import io.harness.plancreator.steps.common.StageElementParameters.StageElementParametersBuilder;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.plancreator.steps.common.StepElementParameters.StepElementParametersBuilder;
import io.harness.pms.tags.TagUtils;
import io.harness.pms.yaml.ParameterField;
import io.harness.steps.SdkCoreStepUtils;
import io.harness.utils.TimeoutUtils;
import io.harness.yaml.utils.NGVariablesUtils;

import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.PIPELINE)
@UtilityClass
public class StepParameterCommonUtils {
  public StepElementParametersBuilder getStepParameters(StepElementConfig stepElementConfig) {
    StepElementParametersBuilder stepBuilder = StepElementParameters.builder();
    stepBuilder.name(stepElementConfig.getName());
    stepBuilder.identifier(stepElementConfig.getIdentifier());
    stepBuilder.delegateSelectors(stepElementConfig.getDelegateSelectors());
    stepBuilder.description(stepElementConfig.getDescription());
    stepBuilder.failureStrategies(
        stepElementConfig.getFailureStrategies() != null ? stepElementConfig.getFailureStrategies().getValue() : null);
    stepBuilder.skipCondition(stepElementConfig.getSkipCondition());
    stepBuilder.timeout(ParameterField.createValueField(TimeoutUtils.getTimeoutString(stepElementConfig.getTimeout())));
    stepBuilder.when(stepElementConfig.getWhen() != null ? stepElementConfig.getWhen().getValue() : null);
    stepBuilder.type(stepElementConfig.getType());
    stepBuilder.uuid(stepElementConfig.getUuid());

    return stepBuilder;
  }

  public StepElementParametersBuilder getStepParameters(
      StepElementConfig stepElementConfig, OnFailRollbackParameters failRollbackParameters) {
    return getStepParameters(stepElementConfig);
  }

  public StageElementParametersBuilder getStageParameters(StageElementConfig stageElementConfig) {
    TagUtils.removeUuidFromTags(stageElementConfig.getTags());

    StageElementParametersBuilder stageBuilder = StageElementParameters.builder();
    stageBuilder.name(stageElementConfig.getName());
    stageBuilder.identifier(stageElementConfig.getIdentifier());
    stageBuilder.description(SdkCoreStepUtils.getParameterFieldHandleValueNull(stageElementConfig.getDescription()));
    stageBuilder.failureStrategies(stageElementConfig.getFailureStrategies() != null
            ? stageElementConfig.getFailureStrategies().getValue()
            : null);
    stageBuilder.skipCondition(stageElementConfig.getSkipCondition());
    stageBuilder.when(stageElementConfig.getWhen() != null ? stageElementConfig.getWhen().getValue() : null);
    stageBuilder.type(stageElementConfig.getType());
    stageBuilder.uuid(stageElementConfig.getUuid());
    stageBuilder.variables(
        ParameterField.createValueField(NGVariablesUtils.getMapOfVariables(stageElementConfig.getVariables())));
    stageBuilder.tags(CollectionUtils.emptyIfNull(stageElementConfig.getTags()));

    return stageBuilder;
  }
}
