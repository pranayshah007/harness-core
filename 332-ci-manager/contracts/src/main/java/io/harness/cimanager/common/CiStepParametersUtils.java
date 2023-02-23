/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans.steps;

import io.harness.advisers.rollback.OnFailRollbackParameters;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.plancreator.steps.common.StepElementParameters.StepElementParametersBuilder;
import io.harness.pms.yaml.ParameterField;
import io.harness.utils.TimeoutUtils;

@OwnedBy(HarnessTeam.CI)
public class CiStepParametersUtils {
  public static StepElementParametersBuilder getStepParameters(CIAbstractStepNode stepNode) {
    StepElementParametersBuilder stepBuilder = StepElementParameters.builder();
    stepBuilder.name(stepNode.getName());
    stepBuilder.identifier(stepNode.getIdentifier());
    stepBuilder.delegateSelectors(stepNode.getDelegateSelectors());
    stepBuilder.description(stepNode.getDescription());
    stepBuilder.skipCondition(stepNode.getSkipCondition());
    stepBuilder.timeout(ParameterField.createValueField(TimeoutUtils.getTimeoutString(stepNode.getTimeout())));
    stepBuilder.when(stepNode.getWhen() != null ? stepNode.getWhen().getValue() : null);
    stepBuilder.type(stepNode.getType());
    stepBuilder.uuid(stepNode.getUuid());
    stepBuilder.enforce(stepNode.getEnforce());

    return stepBuilder;
  }

  public static StepElementParametersBuilder getStepParameters(
      CIAbstractStepNode stepNode, OnFailRollbackParameters failRollbackParameters) {
    StepElementParametersBuilder stepBuilder = getStepParameters(stepNode);
    stepBuilder.rollbackParameters(failRollbackParameters);
    return stepBuilder;
  }
}
