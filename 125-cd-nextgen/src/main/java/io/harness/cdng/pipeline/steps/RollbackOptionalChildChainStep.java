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
import io.harness.cdng.pipeline.beans.RollbackOptionalChildChainStepParameters;
import io.harness.cdng.pipeline.plancreators.PlanCreatorHelper;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.ChildChainExecutableResponse;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.executables.ChildChainExecutable;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.StepResponseNotifyData;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.section.chain.SectionChainPassThroughData;
import io.harness.tasks.ResponseData;

import com.google.inject.Inject;
import com.google.protobuf.ByteString;
import java.util.Map;

@OwnedBy(PIPELINE)
public class RollbackOptionalChildChainStep implements ChildChainExecutable<RollbackOptionalChildChainStepParameters> {
  public static final StepType STEP_TYPE =
      StepType.newBuilder().setType("ROLLBACK_OPTIONAL_CHILD_CHAIN").setStepCategory(StepCategory.STEP).build();

  @Inject private PlanCreatorHelper planCreatorHelper;
  @Inject private KryoSerializer kryoSerializer;
  @Inject ExecutionSweepingOutputService executionSweepingOutputService;

  @Override
  public Class<RollbackOptionalChildChainStepParameters> getStepParametersClass() {
    return RollbackOptionalChildChainStepParameters.class;
  }

  @Override
  public ChildChainExecutableResponse executeFirstChild(
      Ambiance ambiance, RollbackOptionalChildChainStepParameters stepParameters, StepInputPackage inputPackage) {
    int index = 0;
    for (int i = index; i < stepParameters.getChildNodes().size(); i++) {
      RollbackNode childNode = stepParameters.getChildNodes().get(i);

      if (planCreatorHelper.shouldNodeRun(childNode, ambiance)) {
        return ChildChainExecutableResponse.newBuilder()
            .setNextChildId(childNode.getNodeId())
            .setPassThroughData(obtainPassThroughData(SectionChainPassThroughData.builder().childIndex(i).build()))
            .setLastLink(stepParameters.getChildNodes().size() == i + 1)
            .build();
      }
    }
    return ChildChainExecutableResponse.newBuilder().setSuspend(true).build();
  }

  @Override
  public ChildChainExecutableResponse executeNextChild(Ambiance ambiance,
      RollbackOptionalChildChainStepParameters stepParameters, StepInputPackage inputPackage,
      ByteString passThroughData, Map<String, ResponseData> responseDataMap) {
    SectionChainPassThroughData sectionChainPassThroughData =
        (SectionChainPassThroughData) kryoSerializer.asObject(passThroughData.toByteArray());
    int index = sectionChainPassThroughData.getChildIndex() + 1;

    for (int i = index; i < stepParameters.getChildNodes().size(); i++) {
      RollbackNode childNode = stepParameters.getChildNodes().get(i);

      if (planCreatorHelper.shouldNodeRun(childNode, ambiance)) {
        return ChildChainExecutableResponse.newBuilder()
            .setNextChildId(childNode.getNodeId())
            .setPassThroughData(obtainPassThroughData(SectionChainPassThroughData.builder().childIndex(i).build()))
            .setLastLink(stepParameters.getChildNodes().size() == i + 1)
            .setPreviousChildId(responseDataMap.keySet().iterator().next())
            .build();
      }
    }
    return ChildChainExecutableResponse.newBuilder().setSuspend(true).build();
  }

  @Override
  public StepResponse finalizeExecution(Ambiance ambiance, RollbackOptionalChildChainStepParameters stepParameters,
      ByteString passThroughData, Map<String, ResponseData> responseDataMap) {
    StepResponseNotifyData notifyData = (StepResponseNotifyData) responseDataMap.values().iterator().next();
    // If status is suspended, then we should mark the execution as success
    if (notifyData.getStatus() == Status.SUSPENDED) {
      return StepResponse.builder().status(Status.SUCCEEDED).failureInfo(notifyData.getFailureInfo()).build();
    }
    return StepResponse.builder().status(notifyData.getStatus()).failureInfo(notifyData.getFailureInfo()).build();
  }

  private ByteString obtainPassThroughData(SectionChainPassThroughData passThroughData) {
    return ByteString.copyFrom(kryoSerializer.asBytes(passThroughData));
  }
}
