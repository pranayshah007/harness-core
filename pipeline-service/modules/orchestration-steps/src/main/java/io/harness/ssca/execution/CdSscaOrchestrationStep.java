/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.execution;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.AsyncExecutableResponse;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.steps.executable.AsyncExecutableWithRbac;
import io.harness.tasks.ResponseData;

import java.util.Map;

@OwnedBy(HarnessTeam.SSCA)
public class CdSscaOrchestrationStep implements AsyncExecutableWithRbac<StepElementParameters> {
  @Override
  public void validateResources(Ambiance ambiance, StepElementParameters stepParameters) {}

  @Override
  public AsyncExecutableResponse executeAsyncAfterRbac(
      Ambiance ambiance, StepElementParameters stepParameters, StepInputPackage inputPackage) {
    return null;
  }

  @Override
  public Class<StepElementParameters> getStepParametersClass() {
    return StepElementParameters.class;
  }

  @Override
  public void handleAbort(
      Ambiance ambiance, StepElementParameters stepParameters, AsyncExecutableResponse executableResponse) {}

  @Override
  public StepResponse handleAsyncResponse(
      Ambiance ambiance, StepElementParameters stepParameters, Map<String, ResponseData> responseDataMap) {
    return null;
  }
}
