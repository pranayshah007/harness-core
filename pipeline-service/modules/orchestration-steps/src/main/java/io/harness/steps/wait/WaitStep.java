/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.wait;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.UUIDGenerator;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.AsyncExecutableResponse;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.steps.executables.AsyncExecutable;
import io.harness.pms.sdk.core.steps.io.PassThroughData;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.tasks.ResponseData;

import java.util.Map;

// TODO(Shalini): Adding basic skeleton. Implement the methods later.
@OwnedBy(PIPELINE)
public class WaitStep implements AsyncExecutable<StepElementParameters> {
  public static final StepType STEP_TYPE = StepSpecTypeConstants.HARNESS_APPROVAL_STEP_TYPE;

  @Override
  public AsyncExecutableResponse executeAsync(Ambiance ambiance, StepElementParameters stepParameters,
      StepInputPackage inputPackage, PassThroughData passThroughData) {
    String correlationId = UUIDGenerator.generateUuid();
    // Store correlationId and NodeExecutionId together in DB collection to retrieve correlationId from nodeExecutionId.
    // get the duration from stepParameters.
    return AsyncExecutableResponse.newBuilder().addCallbackIds(correlationId).setTimeout(3600).build();
  }

  @Override
  public StepResponse handleAsyncResponse(
      Ambiance ambiance, StepElementParameters stepParameters, Map<String, ResponseData> responseDataMap) {
    // Get status of wait from responseDataMap and create stepResponse based on that.
    return null;
  }

  @Override
  public void handleAbort(
      Ambiance ambiance, StepElementParameters stepParameters, AsyncExecutableResponse executableResponse) {
    // implement and log.
  }

  @Override
  public Class<StepElementParameters> getStepParametersClass() {
    return StepElementParameters.class;
  }
}
