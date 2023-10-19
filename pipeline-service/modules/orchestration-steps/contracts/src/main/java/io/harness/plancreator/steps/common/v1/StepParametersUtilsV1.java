/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.plancreator.steps.common.v1;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.plancreator.stages.v1.PmsAbstractStageNodeV1;
import io.harness.plancreator.steps.common.v1.StageElementParametersV1.StageElementParametersV1Builder;
import io.harness.plancreator.steps.common.v1.StepElementParametersV1.StepElementParametersV1Builder;
import io.harness.plancreator.steps.internal.v1.PmsAbstractStepNodeV1;
import io.harness.pms.tags.TagUtils;
import io.harness.pms.yaml.ParameterField;
import io.harness.steps.SdkCoreStepUtils;
import io.harness.utils.TimeoutUtils;

import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(PIPELINE)
public class StepParametersUtilsV1 {
  public StepElementParametersV1Builder getStepParameters(PmsAbstractStepNodeV1 stepElementConfig) {
    StepElementParametersV1Builder stepBuilder = StepElementParametersV1.builder();
    stepBuilder.name(stepElementConfig.getName());
    stepBuilder.id(stepElementConfig.getId());
    stepBuilder.delegateSelectors(stepElementConfig.getDelegate());
    stepBuilder.desc(stepElementConfig.getDesc());
    stepBuilder.failure(stepElementConfig.getFailure() != null ? stepElementConfig.getFailure().getValue() : null);
    stepBuilder.timeout(ParameterField.createValueField(TimeoutUtils.getTimeoutString(stepElementConfig.getTimeout())));
    stepBuilder.when(
        stepElementConfig.getWhen() != null ? (String) stepElementConfig.getWhen().fetchFinalValue() : null);
    stepBuilder.uuid(stepElementConfig.getUuid());
    stepBuilder.enforce(stepElementConfig.getEnforce());

    return stepBuilder;
  }

  public StageElementParametersV1Builder getStageParameters(PmsAbstractStageNodeV1 stageNode) {
    TagUtils.removeUuidFromTags(stageNode.getLabels());
    StageElementParametersV1Builder stageBuilder = StageElementParametersV1.builder();
    stageBuilder.name(stageNode.getName());
    stageBuilder.id(stageNode.getId());
    stageBuilder.desc(SdkCoreStepUtils.getParameterFieldHandleValueNull(stageNode.getDesc()));
    stageBuilder.failure(stageNode.getFailure() != null ? stageNode.getFailure().getValue() : null);
    stageBuilder.when(stageNode.getWhen() != null ? (String) stageNode.getWhen().fetchFinalValue() : null);
    stageBuilder.uuid(stageNode.getUuid());
    stageBuilder.variables(stageNode.getVariables());
    stageBuilder.delegate(stageNode.getDelegate());
    stageBuilder.labels(stageNode.getLabels());

    return stageBuilder;
  }
}
