/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.steps.common.pipeline;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.steps.SdkCoreStepUtils.createStepResponseFromChildResponse;

import io.harness.OrchestrationStepTypes;
import io.harness.annotations.dev.OwnedBy;
import io.harness.engine.OrchestrationService;
import io.harness.engine.interrupts.InterruptPackage;
import io.harness.engine.interrupts.InterruptService;
import io.harness.interrupts.Interrupt;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.ChildExecutableResponse;
import io.harness.pms.contracts.interrupts.InterruptConfig;
import io.harness.pms.contracts.interrupts.InterruptType;
import io.harness.pms.contracts.plan.PipelineStageInfo;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.steps.executables.ChildExecutable;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.tasks.ResponseData;

import com.google.inject.Inject;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(PIPELINE)
public class PipelineSetupStep implements ChildExecutable<PipelineSetupStepParameters> {
  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(OrchestrationStepTypes.PIPELINE_SECTION)
                                               .setStepCategory(StepCategory.PIPELINE)
                                               .build();

  @Inject private InterruptService interruptService;

  @Inject private OrchestrationService orchestrationService;

  @Override
  public ChildExecutableResponse obtainChild(
      Ambiance ambiance, PipelineSetupStepParameters stepParameters, StepInputPackage inputPackage) {
    log.info("Starting execution for Pipeline Step [{}]", stepParameters);

    PipelineStageInfo pipelineStageInfo = ambiance.getMetadata().getPipelineStageInfo();

    // This is to handle edge case in Pipeline Chaining. Parent Pipeline is aborted but Child Pipeline was not started
    // by then. This will abort the child pipeline if there is any abort registered in parent pipeline
    if (pipelineStageInfo.getHasParentPipeline()) {
      List<Interrupt> interrupts = interruptService.fetchAbortAllPlanLevelInterrupt(pipelineStageInfo.getExecutionId());
      if (isNotEmpty(interrupts)) {
        try {
          handleAbort(ambiance, interrupts.get(0).getInterruptConfig());
        } catch (Exception e) {
          log.warn("Error during interrupt registration - Interrupt might already have been registered ", e);
        }
      }
    }
    final String stagesNodeId = stepParameters.getChildNodeID();
    return ChildExecutableResponse.newBuilder().setChildNodeId(stagesNodeId).build();
  }

  private void handleAbort(Ambiance ambiance, InterruptConfig interruptConfig) {
    InterruptPackage interruptPackage = InterruptPackage.builder()
                                            .interruptType(InterruptType.ABORT_ALL)
                                            .planExecutionId(ambiance.getPlanExecutionId())
                                            .nodeExecutionId(null)
                                            .interruptConfig(interruptConfig)
                                            .metadata(Collections.emptyMap())
                                            .build();
    orchestrationService.registerInterrupt(interruptPackage);
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
