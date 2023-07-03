/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.cdng.beans;

import io.harness.advisers.rollback.OnFailRollbackParameters;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cvng.cdng.CvStepParametersUtils;
import io.harness.plancreator.steps.common.SpecParameters;
import io.harness.plancreator.steps.common.StepElementParameters.StepElementParametersBuilder;
import io.harness.plancreator.steps.common.WithStepElementParameters;
import io.harness.pms.sdk.core.steps.io.StepParameters;
import io.harness.yaml.core.StepSpecType;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.annotations.ApiModel;

@ApiModel(subTypes = {CVNGStepInfo.class, CVNGDeploymentStepInfo.class})
@OwnedBy(HarnessTeam.CV)
public interface CVStepInfoBase extends StepParameters, WithStepElementParameters, StepSpecType {
  default StepParameters getStepParameters(
      CVNGAbstractStepNode stepElementConfig, OnFailRollbackParameters failRollbackParameters) {
    StepElementParametersBuilder stepParametersBuilder =
        CvStepParametersUtils.getStepParameters(stepElementConfig, failRollbackParameters);
    stepParametersBuilder.spec(getSpecParameters());
    return stepParametersBuilder.build();
  }

  @JsonIgnore SpecParameters getSpecParameters();
}
