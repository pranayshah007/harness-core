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
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.steps.executables.AsyncExecutable;
import io.harness.pms.sdk.core.steps.io.PassThroughData;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.tasks.ResponseData;
import io.harness.wait.WaitStepInstance;

import com.google.inject.Inject;
import java.util.Map;

@OwnedBy(PIPELINE)
public class WaitStep implements AsyncExecutable<StepElementParameters> {
  public static final StepType STEP_TYPE = StepSpecTypeConstants.WAIT_STEP_TYPE;
  @Inject WaitStepService waitStepService;

  @Override
  public AsyncExecutableResponse executeAsync(Ambiance ambiance, StepElementParameters stepParameters,
      StepInputPackage inputPackage, PassThroughData passThroughData) {
    String correlationId = UUIDGenerator.generateUuid();
    WaitStepParameters waitStepParameters = (WaitStepParameters) stepParameters.getSpec();
    int duration = (int) waitStepParameters.duration.getValue().getTimeoutInMillis();
    waitStepService.save(WaitStepInstance.builder()
                             .waitStepInstanceId(correlationId)
                             .duration(duration)
                             .createdAt(System.currentTimeMillis())
                             .nodeExecutionId(AmbianceUtils.obtainCurrentRuntimeId(ambiance))
                             .build());
    return AsyncExecutableResponse.newBuilder().addCallbackIds(correlationId).setTimeout(duration).build();
  }

  @Override
  public StepResponse handleAsyncResponse(
      Ambiance ambiance, StepElementParameters stepParameters, Map<String, ResponseData> responseDataMap) {
    String nodeExecutionId = AmbianceUtils.obtainCurrentRuntimeId(ambiance);
    String correlationId = waitStepService.findByNodeExecutionId(nodeExecutionId).get().getWaitStepInstanceId();
    if (responseDataMap.get(correlationId) instanceof WaitStepResponseData
        && ((WaitStepResponseData) responseDataMap.get(correlationId)).action == WaitStepAction.MARK_AS_FAIL) {
      return StepResponse.builder().status(Status.FAILED).build();
    } else {
      return StepResponse.builder().status(Status.SUCCEEDED).build();
    }
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