/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.core.supporter.async;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.TaskExecutableResponse;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.steps.executables.TaskExecutable;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.StepResponseNotifyData;
import io.harness.supplier.ThrowingSupplier;

@OwnedBy(HarnessTeam.PIPELINE)
public class TestTaskStep implements TaskExecutable<TestStepParameters, StepResponseNotifyData> {
  public static final StepType TASK_STEP_TYPE =
      StepType.newBuilder().setType("TEST_STATE_PLAN_CHILD").setStepCategory(StepCategory.STEP).build();

  @Override
  public Class getStepParametersClass() {
    return TestStepParameters.class;
  }

  @Override
  public void handleAbort(
      Ambiance ambiance, TestStepParameters stepParameters, TaskExecutableResponse executableResponse) {
    // doNothing
  }

  @Override
  public TaskRequest obtainTask(Ambiance ambiance, TestStepParameters stepParameters, StepInputPackage inputPackage) {
    return TaskRequest.newBuilder().build();
  }

  @Override
  public StepResponse handleTaskResult(
      Ambiance ambiance, TestStepParameters stepParameters, ThrowingSupplier responseDataSupplier) throws Exception {
    return StepResponse.builder().status(Status.SUCCEEDED).build();
  }
}
