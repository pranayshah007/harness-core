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
import io.harness.plancreator.steps.StepElementConfig;
import io.harness.plancreator.steps.StepParameterCommonUtils;
import io.harness.plancreator.steps.common.StepElementParameters.StepElementParametersBuilder;
import io.harness.pms.sdk.core.steps.io.StepParameters;

import com.fasterxml.jackson.annotation.JsonIgnore;

@OwnedBy(PIPELINE)
public interface WithStepElementParameters {
  default StepParameters getStepParametersInfo(
      StepElementConfig stepElementConfig, OnFailRollbackParameters failRollbackParameters) {
    StepElementParametersBuilder stepParametersBuilder =
        StepParameterCommonUtils.getStepParameters(stepElementConfig, failRollbackParameters);
    stepParametersBuilder.spec(getSpecParameters());
    return stepParametersBuilder.build();
  }

  @JsonIgnore
  default SpecParameters getSpecParameters() {
    return null;
  }
}
