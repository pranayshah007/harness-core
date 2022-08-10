/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.steps.fork;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.steps.SdkCoreStepUtils.createStepResponseFromChildResponse;

import io.harness.annotations.dev.OwnedBy;
import io.harness.plancreator.NGCommonUtilPlanCreationConstants;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.ChildrenExecutableResponse;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.steps.executable.ChildrenExecutableWithRollbackAndRbac;
import io.harness.tasks.ResponseData;

import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(PIPELINE)
public class NGForkStep extends ChildrenExecutableWithRollbackAndRbac<ForkStepParameters> {
  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(NGCommonUtilPlanCreationConstants.NG_FORK)
                                               .setStepCategory(StepCategory.FORK)
                                               .build();

  @Override
  public void validateResources(Ambiance ambiance, ForkStepParameters stepParameters) {
    // do Nothing
  }

  @Override
  public ChildrenExecutableResponse obtainChildrenAfterRbac(
      Ambiance ambiance, ForkStepParameters stepParameters, StepInputPackage inputPackage) {
    log.info("Starting execution for Parallel Step [{}]", stepParameters);
    ChildrenExecutableResponse.Builder responseBuilder = ChildrenExecutableResponse.newBuilder();
    for (String nodeId : stepParameters.getParallelNodeIds()) {
      responseBuilder.addChildren(ChildrenExecutableResponse.Child.newBuilder().setChildNodeId(nodeId).build());
    }
    return responseBuilder.build();
  }

  @Override
  public StepResponse handleChildrenResponseInternal(
      Ambiance ambiance, ForkStepParameters stepParameters, Map<String, ResponseData> responseDataMap) {
    log.info("Completed  execution for Parallel Step [{}]", stepParameters);
    return createStepResponseFromChildResponse(responseDataMap);
  }

  @Override
  public Class<ForkStepParameters> getStepParametersClass() {
    return ForkStepParameters.class;
  }
}
