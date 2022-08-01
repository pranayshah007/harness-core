/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.core.supporter.children;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.ChildChainExecutableResponse;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.steps.executables.ChildChainExecutable;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.tasks.ResponseData;

import com.google.protobuf.ByteString;
import java.util.Map;

@OwnedBy(HarnessTeam.PIPELINE)
public class TestChildChainStep implements ChildChainExecutable<TestChildrenStepParameters> {
  public static final StepType STEP_TYPE =
      StepType.newBuilder().setType("TEST_CHILDREN").setStepCategory(StepCategory.STEP).build();

  @Override
  public Class<TestChildrenStepParameters> getStepParametersClass() {
    return TestChildrenStepParameters.class;
  }

  @Override
  public ChildChainExecutableResponse executeFirstChild(
      Ambiance ambiance, TestChildrenStepParameters stepParameters, StepInputPackage inputPackage) {
    return ChildChainExecutableResponse.newBuilder().setNextChildId("test").build();
  }

  @Override
  public ChildChainExecutableResponse executeNextChild(Ambiance ambiance, TestChildrenStepParameters stepParameters,
      StepInputPackage inputPackage, ByteString passThroughData, Map<String, ResponseData> responseDataMap) {
    return ChildChainExecutableResponse.newBuilder().setSuspend(true).build();
  }

  @Override
  public StepResponse finalizeExecution(Ambiance ambiance, TestChildrenStepParameters stepParameters,
      ByteString passThroughData, Map<String, ResponseData> responseDataMap) {
    return StepResponse.builder().status(Status.SUCCEEDED).build();
  }
}
