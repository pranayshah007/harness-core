/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.iacm.execution;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ci.states.IntegrationStageStepPMS;
import io.harness.plancreator.steps.common.StageElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.ChildExecutableResponse;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.tasks.ResponseData;

import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.IACM)
public class IACMIntegrationStageStepPMS extends IntegrationStageStepPMS {
  @Override
  public ChildExecutableResponse obtainChild(
      Ambiance ambiance, StageElementParameters stepParameters, StepInputPackage inputPackage) {
    return super.obtainChild(ambiance, stepParameters, inputPackage);
  }

  @Override
  public StepResponse handleChildResponse(
      Ambiance ambiance, StageElementParameters stepParameters, Map<String, ResponseData> responseDataMap) {
    return super.handleChildResponse(ambiance, stepParameters, responseDataMap);
  }
}
