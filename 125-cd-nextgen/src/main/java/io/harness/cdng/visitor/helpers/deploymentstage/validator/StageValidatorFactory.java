/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.visitor.helpers.deploymentstage.validator;

import static java.util.Objects.isNull;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.creator.plan.stage.DeploymentStageConfig;

import com.google.inject.Inject;

@OwnedBy(HarnessTeam.CDP)
public class StageValidatorFactory {
  @Inject TasStageValidatorHelper tasStageValidatorHelper;
  @Inject CustomDeploymentStageValidatorHelper customDeploymentStageValidatorHelper;
  @Inject NoOpStageValidatorHelper noOpStageValidatorHelper;

  public StageValidatorHelper getStageValidationHelper(Object object) {
    if (object instanceof DeploymentStageConfig) {
      DeploymentStageConfig stageConfig = (DeploymentStageConfig) object;
      if (!isNull(stageConfig.getDeploymentType())) {
        switch (stageConfig.getDeploymentType()) {
          case CUSTOM_DEPLOYMENT:
            return customDeploymentStageValidatorHelper;
          case TAS:
            return tasStageValidatorHelper;
          default:
            return noOpStageValidatorHelper;
        }
      }
    }
    return noOpStageValidatorHelper;
  }
}
