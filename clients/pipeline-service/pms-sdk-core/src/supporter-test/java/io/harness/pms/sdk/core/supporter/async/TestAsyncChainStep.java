/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.core.supporter.async;

import static io.harness.data.structure.UUIDGenerator.generateUuid;

import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.AsyncChainExecutableResponse;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.steps.executables.AsyncChainExecutable;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.supplier.ThrowingSupplier;
import io.harness.tasks.ResponseData;

import lombok.Getter;

public class TestAsyncChainStep implements AsyncChainExecutable<TestStepParameters> {
  public static int timeout = 100;

  public static final StepType ASYNC_CHAIN_STEP_TYPE =
      StepType.newBuilder().setType("TEST_STATE_PLAN_ASYNC_CHAIN").setStepCategory(StepCategory.STEP).build();

  @Getter private String message;

  public TestAsyncChainStep(String message) {
    this.message = message;
  }

  @Override
  public Class<TestStepParameters> getStepParametersClass() {
    return TestStepParameters.class;
  }

  @Override
  public void handleAbort(
      Ambiance ambiance, TestStepParameters stepParameters, AsyncChainExecutableResponse executableResponse) {
    // Do Nothing
  }

  @Override
  public AsyncChainExecutableResponse startChainLink(
      Ambiance ambiance, TestStepParameters stepParameters, StepInputPackage inputPackage) {
    String resumeId = generateUuid();
    return AsyncChainExecutableResponse.newBuilder()
        .setCallbackId(resumeId)
        .setTimeout(timeout)
        .setChainEnd(false)
        .build();
  }

  @Override
  public AsyncChainExecutableResponse executeNextLink(Ambiance ambiance, TestStepParameters stepParameters,
      StepInputPackage inputPackage, ThrowingSupplier<ResponseData> responseSupplier) throws Exception {
    String resumeId = generateUuid();
    return AsyncChainExecutableResponse.newBuilder()
        .setCallbackId(resumeId)
        .setTimeout(timeout)
        .setChainEnd(true)
        .build();
  }

  @Override
  public StepResponse finalizeExecution(Ambiance ambiance, TestStepParameters stepParameters,
      ThrowingSupplier<ResponseData> responseDataSupplier) throws Exception {
    return StepResponse.builder().status(Status.SUCCEEDED).build();
  }
}
