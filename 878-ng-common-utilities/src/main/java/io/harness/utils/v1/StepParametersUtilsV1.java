/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.utils.v1;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.advisers.rollback.OnFailRollbackParameters;
import io.harness.annotations.dev.OwnedBy;
import io.harness.plancreator.policy.PolicyConfig;
import io.harness.plancreator.steps.common.SpecParameters;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.plancreator.steps.common.v1.StepElementParametersV1;
import io.harness.pms.sdk.core.steps.io.StepParameters;
import io.harness.pms.sdk.core.steps.io.v1.StepBaseParameters;
import io.harness.pms.yaml.ParameterField;

import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(PIPELINE)
public class StepParametersUtilsV1 {
  public ParameterField<String> getStepTimeout(StepBaseParameters stepParameters) {
    if (stepParameters instanceof StepElementParameters) {
      StepElementParameters stepElementParameters = (StepElementParameters) stepParameters;
      return stepElementParameters.getTimeout();
    }
    StepElementParametersV1 stepElementParametersV1 = (StepElementParametersV1) stepParameters;
    return stepElementParametersV1.getTimeout();
  }

  public SpecParameters getSpecParameters(StepBaseParameters stepParameters) {
    if (stepParameters instanceof StepElementParameters) {
      StepElementParameters stepElementParameters = (StepElementParameters) stepParameters;
      return stepElementParameters.getSpec();
    }
    StepElementParametersV1 stepElementParametersV1 = (StepElementParametersV1) stepParameters;
    return stepElementParametersV1.getSpec();
  }

  public String getName(StepParameters stepParameters) {
    if (stepParameters instanceof StepElementParameters) {
      StepElementParameters stepElementParameters = (StepElementParameters) stepParameters;
      return stepElementParameters.getName();
    }
    StepElementParametersV1 stepElementParametersV1 = (StepElementParametersV1) stepParameters;
    return stepElementParametersV1.getName();
  }

  public PolicyConfig getPolicyConfig(StepBaseParameters stepParameters) {
    if (stepParameters instanceof StepElementParameters) {
      StepElementParameters stepElementParameters = (StepElementParameters) stepParameters;
      return stepElementParameters.getEnforce();
    }
    StepElementParametersV1 stepElementParametersV1 = (StepElementParametersV1) stepParameters;
    return stepElementParametersV1.getEnforce();
  }
}
