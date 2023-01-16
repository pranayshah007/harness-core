/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.pipeline;

import io.harness.advisers.rollback.OnFailRollbackParameters;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.pipeline.steps.CdStepParametersUtils;
import io.harness.plancreator.steps.common.StepElementParameters.StepElementParametersBuilder;
import io.harness.plancreator.steps.common.WithDelegateSelector;
import io.harness.plancreator.steps.common.WithStepElementParameters;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.steps.io.StepParameters;
import io.harness.steps.StepUtils;
import io.harness.yaml.core.StepSpecType;

@OwnedBy(HarnessTeam.CDC)
public interface CDAbstractStepInfo extends StepSpecType, WithStepElementParameters, WithDelegateSelector {
  default StepParameters getStepParameters(
      CdAbstractStepNode stepElementConfig, OnFailRollbackParameters failRollbackParameters, PlanCreationContext ctx) {
    StepElementParametersBuilder stepParametersBuilder =
        CdStepParametersUtils.getStepParameters(stepElementConfig, failRollbackParameters);
    StepUtils.appendDelegateSelectorsToSpecParameters(stepElementConfig.getStepSpecType(), ctx);
    stepParametersBuilder.spec(getSpecParameters());
    return stepParametersBuilder.build();
  }
}
