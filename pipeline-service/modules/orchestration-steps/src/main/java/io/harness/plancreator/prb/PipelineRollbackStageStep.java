/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.plancreator.prb;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.OrchestrationStepTypes;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.PlanExecution;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.AsyncExecutableResponse;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.plan.execution.PipelineExecutor;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.EmptyStepParameters;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.steps.executable.AsyncExecutableWithRbac;
import io.harness.tasks.ResponseData;

import com.google.inject.Inject;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(PIPELINE)
public class PipelineRollbackStageStep implements AsyncExecutableWithRbac<EmptyStepParameters> {
  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(OrchestrationStepTypes.PIPELINE_ROLLBACK_STAGE)
                                               .setStepCategory(StepCategory.STAGE)
                                               .build();

  @Inject private PipelineExecutor pipelineExecutor;
  @Inject private ExecutionSweepingOutputService sweepingOutputService;

  @Override
  public Class<EmptyStepParameters> getStepParametersClass() {
    return EmptyStepParameters.class;
  }

  @Override
  public AsyncExecutableResponse executeAsyncAfterRbac(
      Ambiance ambiance, EmptyStepParameters stepParameters, StepInputPackage inputPackage) {
    String accountId = AmbianceUtils.getAccountId(ambiance);
    String orgId = AmbianceUtils.getOrgIdentifier(ambiance);
    String projectId = AmbianceUtils.getProjectIdentifier(ambiance);
    String planExecutionId = ambiance.getPlanExecutionId();
    log.info("Starting Pipeline Rollback");
    PlanExecution planExecution = pipelineExecutor.startPipelineRollback(accountId, orgId, projectId, planExecutionId);
    if (planExecution == null) {
      throw new InvalidRequestException("Failed to start Pipeline Rollback");
    }
    // saving output for handleAsyncResponse
    sweepingOutputService.consume(ambiance, PipelineRollbackStageSweepingOutput.OUTPUT_NAME,
        PipelineRollbackStageSweepingOutput.builder().rollbackModeExecutionId(planExecution.getUuid()).build(),
        StepCategory.STAGE.name());

    return AsyncExecutableResponse.newBuilder().addCallbackIds(planExecution.getUuid()).build();
  }

  @Override
  public StepResponse handleAsyncResponse(
      Ambiance ambiance, EmptyStepParameters stepParameters, Map<String, ResponseData> responseDataMap) {
    // todo: implement
    return StepResponse.builder().status(Status.SUCCEEDED).build();
  }

  @Override
  public void handleAbort(
      Ambiance ambiance, EmptyStepParameters stepParameters, AsyncExecutableResponse executableResponse) {
    // todo: implement
  }

  @Override
  public void validateResources(Ambiance ambiance, EmptyStepParameters stepParameters) {
    // do nothing
  }
}
