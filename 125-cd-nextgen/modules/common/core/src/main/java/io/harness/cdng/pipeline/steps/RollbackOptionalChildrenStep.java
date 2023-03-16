/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.pipeline.steps;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.pipeline.beans.RollbackNode;
import io.harness.cdng.pipeline.beans.RollbackOptionalChildrenParameters;
import io.harness.cdng.pipeline.plancreators.PlanCreatorHelper;
import io.harness.executions.steps.ExecutionNodeType;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.ChildrenExecutableResponse;
import io.harness.pms.contracts.execution.ChildrenExecutableResponse.Child;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.steps.executables.ChildrenExecutable;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.StepResponse.StepResponseBuilder;
import io.harness.pms.sdk.core.steps.io.StepResponseNotifyData;
import io.harness.tasks.ResponseData;

import com.google.inject.Inject;
import java.util.Map;

@OwnedBy(PIPELINE)
public class RollbackOptionalChildrenStep implements ChildrenExecutable<RollbackOptionalChildrenParameters> {
  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(ExecutionNodeType.ROLLBACK_SECTION.getName())
                                               .setStepCategory(StepCategory.STEP)
                                               .build();

  @Inject private PlanCreatorHelper planCreatorHelper;

  @Override
  public Class<RollbackOptionalChildrenParameters> getStepParametersClass() {
    return RollbackOptionalChildrenParameters.class;
  }

  @Override
  public ChildrenExecutableResponse obtainChildren(
      Ambiance ambiance, RollbackOptionalChildrenParameters stepParameters, StepInputPackage inputPackage) {
    ChildrenExecutableResponse.Builder responseBuilder = ChildrenExecutableResponse.newBuilder();
    for (RollbackNode node : stepParameters.getParallelNodes()) {
      if (planCreatorHelper.shouldNodeRun(node, ambiance)) {
        responseBuilder.addChildren(Child.newBuilder().setChildNodeId(node.getNodeId()).build());
      }
    }
    return responseBuilder.build();
  }

  @Override
  public StepResponse handleChildrenResponse(
      Ambiance ambiance, RollbackOptionalChildrenParameters stepParameters, Map<String, ResponseData> responseDataMap) {
    StepResponseBuilder responseBuilder = StepResponse.builder().status(Status.SUCCEEDED);
    for (ResponseData responseData : responseDataMap.values()) {
      Status executionStatus = ((StepResponseNotifyData) responseData).getStatus();
      if (executionStatus != Status.SUCCEEDED) {
        responseBuilder.status(executionStatus);
      }
    }
    return responseBuilder.build();
  }
}
