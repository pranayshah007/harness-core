/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.steps.common.pipeline;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.steps.StepUtils.createStepResponseFromChildResponse;

import io.harness.OrchestrationStepTypes;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.ChildExecutableResponse;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.steps.executables.ChildExecutable;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.tasks.ResponseData;

import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(PIPELINE)
public class PipelineSetupStep implements ChildExecutable<PipelineSetupStepParameters> {
  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(OrchestrationStepTypes.PIPELINE_SECTION)
                                               .setStepCategory(StepCategory.PIPELINE)
                                               .build();

  @Override
  public ChildExecutableResponse obtainChild(
      Ambiance ambiance, PipelineSetupStepParameters stepParameters, StepInputPackage inputPackage) {
    log.info("Starting execution for Pipeline Step [{}]", stepParameters);

    final String stagesNodeId = stepParameters.getChildNodeID();
    return ChildExecutableResponse.newBuilder().setChildNodeId(stagesNodeId).build();
  }

  @Override
  public StepResponse handleChildResponse(
      Ambiance ambiance, PipelineSetupStepParameters stepParameters, Map<String, ResponseData> responseDataMap) {
    log.info("Executed Pipeline Step =[{}]", stepParameters);
    return createStepResponseFromChildResponse(responseDataMap);
  }

  @Override
  public Class<PipelineSetupStepParameters> getStepParametersClass() {
    return PipelineSetupStepParameters.class;
  }
}
