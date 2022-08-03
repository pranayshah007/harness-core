/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.steps.section.chain;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.OwnedBy;
import io.harness.plancreator.NGCommonUtilPlanCreationConstants;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.ChildChainExecutableResponse;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.StatusUtils;
import io.harness.pms.sdk.core.steps.executables.ChildChainExecutable;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.StepResponse.StepResponseBuilder;
import io.harness.pms.sdk.core.steps.io.StepResponseNotifyData;
import io.harness.serializer.KryoSerializer;
import io.harness.tasks.ResponseData;

import com.google.inject.Inject;
import com.google.protobuf.ByteString;
import java.util.Map;

@OwnedBy(CDC)
public class SectionChainStep implements ChildChainExecutable<SectionChainStepParameters> {
  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(NGCommonUtilPlanCreationConstants.SECTION_CHAIN)
                                               .setStepCategory(StepCategory.STEP)
                                               .build();

  @Inject KryoSerializer kryoSerializer;

  @Override
  public Class<SectionChainStepParameters> getStepParametersClass() {
    return SectionChainStepParameters.class;
  }

  @Override
  public ChildChainExecutableResponse executeFirstChild(
      Ambiance ambiance, SectionChainStepParameters sectionChainStepParameters, StepInputPackage inputPackage) {
    if (isEmpty(sectionChainStepParameters.getChildNodeIds())) {
      return ChildChainExecutableResponse.newBuilder().setSuspend(true).build();
    }

    return ChildChainExecutableResponse.newBuilder()
        .setNextChildId(sectionChainStepParameters.getChildNodeIds().get(0))
        .setPassThroughData(
            ByteString.copyFrom(kryoSerializer.asBytes(SectionChainPassThroughData.builder().childIndex(0).build())))
        .setLastLink(sectionChainStepParameters.getChildNodeIds().size() == 1)
        .build();
  }

  @Override
  public ChildChainExecutableResponse executeNextChild(Ambiance ambiance,
      SectionChainStepParameters sectionChainStepParameters, StepInputPackage inputPackage, ByteString passThroughData,
      Map<String, ResponseData> responseDataMap) {
    SectionChainPassThroughData chainPassThroughData =
        (SectionChainPassThroughData) kryoSerializer.asObject(passThroughData.toByteArray());
    int nextChildIndex = chainPassThroughData.getChildIndex() + 1;
    String previousChildId = responseDataMap.keySet().iterator().next();
    boolean lastLink = nextChildIndex + 1 == sectionChainStepParameters.getChildNodeIds().size();
    chainPassThroughData.setChildIndex(nextChildIndex);
    return ChildChainExecutableResponse.newBuilder()
        .setPassThroughData(ByteString.copyFrom(
            kryoSerializer.asBytes(SectionChainPassThroughData.builder().childIndex(nextChildIndex).build())))
        .setNextChildId(sectionChainStepParameters.getChildNodeIds().get(nextChildIndex))
        .setLastLink(lastLink)
        .setPreviousChildId(previousChildId)
        .build();
  }

  @Override
  public StepResponse finalizeExecution(Ambiance ambiance, SectionChainStepParameters sectionChainStepParameters,
      ByteString passThroughData, Map<String, ResponseData> responseDataMap) {
    StepResponseBuilder responseBuilder = StepResponse.builder().status(Status.SUCCEEDED);
    for (ResponseData responseData : responseDataMap.values()) {
      Status executionStatus = ((StepResponseNotifyData) responseData).getStatus();
      if (executionStatus != Status.SUCCEEDED) {
        responseBuilder.status(executionStatus);
      }
      if (StatusUtils.brokeStatuses().contains(executionStatus)) {
        responseBuilder.failureInfo(((StepResponseNotifyData) responseData).getFailureInfo());
      }
    }
    return responseBuilder.build();
  }
}
