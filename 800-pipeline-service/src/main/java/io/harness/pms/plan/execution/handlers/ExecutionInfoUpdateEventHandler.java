/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution.handlers;

import io.harness.engine.executions.plan.PlanExecutionService;
import io.harness.engine.observers.PlanStatusUpdateObserver;
import io.harness.exception.ExceptionUtils;
import io.harness.execution.PlanExecution;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.execution.ExecutionStatus;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.execution.utils.StatusUtils;
import io.harness.pms.pipeline.ExecutionSummaryInfo;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.metadata.RecentExecutionsInfoHelper;
import io.harness.pms.pipeline.service.PMSPipelineService;
import io.harness.pms.security.PmsSecurityContextEventGuard;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
public class ExecutionInfoUpdateEventHandler implements PlanStatusUpdateObserver {
  private final PMSPipelineService pmsPipelineService;
  private final PlanExecutionService planExecutionService;
  private final RecentExecutionsInfoHelper recentExecutionsInfoHelper;

  @Inject
  public ExecutionInfoUpdateEventHandler(PMSPipelineService pmsPipelineService,
      PlanExecutionService planExecutionService, RecentExecutionsInfoHelper recentExecutionsInfoHelper) {
    this.pmsPipelineService = pmsPipelineService;
    this.planExecutionService = planExecutionService;
    this.recentExecutionsInfoHelper = recentExecutionsInfoHelper;
  }

  @Override
  public void onPlanStatusUpdate(Ambiance ambiance) {
    String planExecutionId = ambiance.getPlanExecutionId();
    PlanExecution planExecution = planExecutionService.get(planExecutionId);

    recentExecutionsInfoHelper.onExecutionUpdate(ambiance, planExecution);
    updateExecutionInfoInPipelineEntity(ambiance, planExecution);
  }

  void updateExecutionInfoInPipelineEntity(Ambiance ambiance, PlanExecution planExecution) {
    // this security context guard is needed because now pipeline get requires proper permissions to be set in the case
    // when the Pipeline is REMOTE
    try (PmsSecurityContextEventGuard ignore = new PmsSecurityContextEventGuard(ambiance)) {
      Status status = planExecution.getStatus();
      String accountId = AmbianceUtils.getAccountId(ambiance);
      String orgId = AmbianceUtils.getOrgIdentifier(ambiance);
      String projectId = AmbianceUtils.getProjectIdentifier(ambiance);
      String pipelineId = ambiance.getMetadata().getPipelineIdentifier();
      Optional<PipelineEntity> pipelineEntity = pmsPipelineService.get(accountId, orgId, projectId, pipelineId, false);
      if (!pipelineEntity.isPresent()) {
        return;
      }
      ExecutionSummaryInfo executionSummaryInfo = pipelineEntity.get().getExecutionSummaryInfo();
      if (executionSummaryInfo != null) {
        executionSummaryInfo.setLastExecutionStatus(ExecutionStatus.getExecutionStatus(status));
        if (StatusUtils.brokeStatuses().contains(status)) {
          Map<String, Integer> errors = executionSummaryInfo.getNumOfErrors();
          Date date = new Date();
          SimpleDateFormat formatter = new SimpleDateFormat("dd/MM/yyyy");
          String strDate = formatter.format(date);
          if (errors.containsKey(strDate)) {
            errors.put(strDate, errors.get(strDate) + 1);
          } else {
            errors.put(strDate, 1);
          }
        }
        pmsPipelineService.saveExecutionInfo(accountId, orgId, projectId, pipelineId, executionSummaryInfo);
      } else {
        log.error("ExecutionSummaryInfo is null for executionId - " + ambiance.getPlanExecutionId());
      }
    } catch (Exception e) {
      log.error("Error while updating Plan Status for execution with ID " + ambiance.getPlanExecutionId() + ": "
              + ExceptionUtils.getMessage(e),
          e);
    }
  }
}
