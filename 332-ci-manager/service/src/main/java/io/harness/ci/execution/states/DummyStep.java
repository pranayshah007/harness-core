/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.states;

// import com.google.inject.Inject;
// import io.harness.logstreaming.LogStreamingStepClientFactory;
// import io.harness.logstreaming.NGLogCallback;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.AsyncExecutableResponse;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.steps.executables.AsyncExecutable;
import io.harness.pms.sdk.core.steps.executables.SyncExecutable;
import io.harness.pms.sdk.core.steps.io.PassThroughData;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.steps.StepUtils;
import io.harness.tasks.ResponseData;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class DummyStep implements AsyncExecutable<StepElementParameters> {
  public static StepType STEP_TYPE = StepType.newBuilder().setType("dummy").setStepCategory(StepCategory.STEP).build();

  @Override
  public void handleAbort(
      Ambiance ambiance, StepElementParameters stepParameters, AsyncExecutableResponse executableResponse) {}

  @Override
  public AsyncExecutableResponse executeAsync(Ambiance ambiance, StepElementParameters stepParameters,
      StepInputPackage inputPackage, PassThroughData passThroughData) {
    return AsyncExecutableResponse.newBuilder().build();
  }

  @Override
  public StepResponse handleAsyncResponse(
      Ambiance ambiance, StepElementParameters stepParameters, Map<String, ResponseData> responseDataMap) {
    return StepResponse.builder().status(Status.SUCCEEDED).build();
  }

  //    @Inject private LogStreamingStepClientFactory logStreamingStepClientFactory;

  @Override
  public Class<StepElementParameters> getStepParametersClass() {
    return StepElementParameters.class;
  }

  //    @Override
  //    public StepResponse executeSync(Ambiance ambiance, StepElementParameters stepParameters, StepInputPackage
  //    inputPackage, PassThroughData passThroughData) {
  ////        NGLogCallback ngLogCallback = new NGLogCallback(logStreamingStepClientFactory, ambiance, null, false);
  ////        ngLogCallback.saveExecutionLog("yayyyyyyy, I am CD step!!");
  //
  //        return StepResponse.builder().status(Status.SUCCEEDED).build();
  //    }

  //    @Override
  //    public List<String> getLogKeys(Ambiance ambiance) {
  //        return StepUtils.generateLogKeys(ambiance, new ArrayList<>());
  //    }
}
