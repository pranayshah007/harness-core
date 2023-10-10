/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.utilities;

import static io.harness.cdng.environment.constants.CustomStageEnvironmentStepConstants.ENVIRONMENT_STEP_COMMAND_UNIT;
import static io.harness.cdng.service.steps.constants.ServiceStepConstants.SERVICE_STEP_COMMAND_UNIT;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.cdng.environment.constants.CustomStageEnvironmentStepConstants;
import io.harness.cdng.service.steps.constants.ServiceConfigStepConstants;
import io.harness.cdng.service.steps.constants.ServiceSectionStepConstants;
import io.harness.cdng.service.steps.constants.ServiceStepV3Constants;
import io.harness.logstreaming.LogStreamingStepClientFactory;
import io.harness.logstreaming.NGLogCallback;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.execution.utils.AmbianceUtils;

import com.google.inject.Inject;
import java.util.List;
import lombok.AllArgsConstructor;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_SERVICE_ENVIRONMENT})
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@OwnedBy(HarnessTeam.CDC)
public class NGLogCallbackUtility {
  @Inject private LogStreamingStepClientFactory logStreamingStepClientFactory;

  public NGLogCallback getLogCallback(Ambiance ambiance, boolean shouldOpenStream) {
    Ambiance newAmbiance = null;
    String commandUnit = null;
    List<Level> levels = ambiance.getLevelsList();
    for (int i = levels.size() - 1; i >= 0; i--) {
      Level level = levels.get(i);
      if (CustomStageEnvironmentStepConstants.STEP_TYPE.equals(level.getStepType())) {
        commandUnit = ENVIRONMENT_STEP_COMMAND_UNIT;
        newAmbiance = AmbianceUtils.clone(ambiance, i + 1);
        break;
      } else if (ServiceConfigStepConstants.STEP_TYPE.equals(level.getStepType())
          || ServiceSectionStepConstants.STEP_TYPE.equals(level.getStepType())
          || ServiceStepV3Constants.STEP_TYPE.equals(level.getStepType())) {
        commandUnit = SERVICE_STEP_COMMAND_UNIT;
        newAmbiance = AmbianceUtils.clone(ambiance, i + 1);
      }
    }

    if (newAmbiance == null) {
      throw new UnsupportedOperationException(
          "Not inside deployment stage service step or custom stage environment step or one of their children");
    }

    return new NGLogCallback(logStreamingStepClientFactory, ambiance, commandUnit, shouldOpenStream);
  }
}