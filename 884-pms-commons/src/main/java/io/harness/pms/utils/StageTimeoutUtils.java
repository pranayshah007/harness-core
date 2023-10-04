/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.utils;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.plancreator.stages.stage.AbstractStageNode;
import io.harness.pms.timeout.AbsoluteSdkTimeoutTrackerParameters;
import io.harness.pms.timeout.SdkTimeoutObtainment;
import io.harness.pms.yaml.ParameterField;
import io.harness.timeout.trackers.absolute.AbsoluteTimeoutTrackerFactory;
import io.harness.utils.TimeoutUtils;
import io.harness.yaml.core.timeout.Timeout;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.PIPELINE)
@UtilityClass
@Slf4j
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
public class StageTimeoutUtils {
  public SdkTimeoutObtainment getStageTimeoutObtainment(ParameterField<Timeout> timeout) {
    if (null != timeout && !ParameterField.isBlank(timeout)) {
      return SdkTimeoutObtainment.builder()
          .dimension(AbsoluteTimeoutTrackerFactory.DIMENSION)
          .parameters(AbsoluteSdkTimeoutTrackerParameters.builder()
                          .timeout(TimeoutUtils.getTimeoutParameterFieldStringForStage(timeout))
                          .build())
          .build();
    }
    return null;
  }

  public SdkTimeoutObtainment getStageTimeoutObtainment(AbstractStageNode stageNode) {
    return getStageTimeoutObtainment(stageNode.getTimeout());
  }
}
