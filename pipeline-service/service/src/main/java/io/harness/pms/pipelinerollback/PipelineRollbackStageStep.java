/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.pipelinerollback;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.OrchestrationStepTypes;
import io.harness.annotations.dev.OwnedBy;
import io.harness.engine.interrupts.InterruptService;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.PlanExecution;
import io.harness.interrupts.Interrupt;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.AsyncExecutableResponse;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.plan.PipelineStageInfo;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.plan.execution.PipelineExecutor;
import io.harness.pms.plan.execution.PlanExecutionInterruptType;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys;
import io.harness.pms.plan.execution.service.PMSExecutionService;
import io.harness.pms.plan.execution.service.PmsExecutionSummaryService;
import io.harness.pms.sdk.core.steps.io.EmptyStepParameters;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.security.PmsSecurityContextGuardUtils;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.SourcePrincipalContextBuilder;
import io.harness.security.dto.Principal;
import io.harness.steps.executable.AsyncExecutableWithRbac;
import io.harness.tasks.ResponseData;

import com.google.inject.Inject;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.query.Update;

@Slf4j
@OwnedBy(PIPELINE)
public class PipelineRollbackStageStep implements AsyncExecutableWithRbac<EmptyStepParameters> {
  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(OrchestrationStepTypes.PIPELINE_ROLLBACK_STAGE)
                                               .setStepCategory(StepCategory.STAGE)
                                               .build();

  @Inject private PipelineExecutor pipelineExecutor;
  @Inject private PmsExecutionSummaryService executionSummaryService;
  @Inject private InterruptService interruptService;
  @Inject private PMSExecutionService executionService;

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
    String currentPlanExecutionId = ambiance.getPlanExecutionId();
    log.info("Starting Pipeline Rollback");
    PipelineStageInfo parentStageInfo = buildParentStageInfo(ambiance);
    PlanExecution rollbackPlanExecution =
        pipelineExecutor.startPipelineRollback(accountId, orgId, projectId, currentPlanExecutionId, parentStageInfo);
    if (rollbackPlanExecution == null) {
      throw new InvalidRequestException("Failed to start Pipeline Rollback");
    }
    Update update = new Update();
    update.set(PlanExecutionSummaryKeys.rollbackModeExecutionId, rollbackPlanExecution.getUuid());
    executionSummaryService.update(currentPlanExecutionId, update);
    return AsyncExecutableResponse.newBuilder().addCallbackIds(rollbackPlanExecution.getUuid()).build();
  }

  public PipelineStageInfo buildParentStageInfo(Ambiance ambiance) {
    String currentSetupId = AmbianceUtils.obtainCurrentSetupId(ambiance);
    return PipelineStageInfo.newBuilder().setStageNodeId(currentSetupId).setHasParentPipeline(false).build();
  }

  @Override
  public StepResponse handleAsyncResponse(
      Ambiance ambiance, EmptyStepParameters stepParameters, Map<String, ResponseData> responseDataMap) {
    return StepResponse.builder().status(Status.SUCCEEDED).build();
  }

  @Override
  public void handleAbort(
      Ambiance ambiance, EmptyStepParameters stepParameters, AsyncExecutableResponse executableResponse) {
    Principal principal = PmsSecurityContextGuardUtils.getPrincipalFromAmbiance(ambiance);
    SourcePrincipalContextBuilder.setSourcePrincipal(principal);
    SecurityContextBuilder.setContext(principal);
    // Setting interrupt config of parent pipeline while registering interrupt for child pipeline
    List<Interrupt> interrupts = interruptService.fetchAbortAllPlanLevelInterrupt(ambiance.getPlanExecutionId());
    if (isNotEmpty(interrupts)) {
      Interrupt interrupt = interrupts.get(0);
      if (executableResponse != null && isNotEmpty(executableResponse.getCallbackIdsList())) {
        executionService.registerInterrupt(PlanExecutionInterruptType.ABORTALL, executableResponse.getCallbackIds(0),
            null, interrupt.getInterruptConfig());
      }
    }
  }

  @Override
  public void validateResources(Ambiance ambiance, EmptyStepParameters stepParameters) {
    // do nothing
  }
}
