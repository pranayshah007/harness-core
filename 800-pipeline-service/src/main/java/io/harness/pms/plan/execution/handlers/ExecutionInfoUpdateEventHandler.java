/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution.handlers;

import io.harness.engine.executions.plan.PlanExecutionService;
import io.harness.engine.observers.PlanStatusUpdateObserver;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.execution.ExecutionStatus;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.execution.utils.StatusUtils;
import io.harness.pms.pipeline.ExecutionSummaryInfo;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.service.PMSPipelineService;
import io.harness.pms.security.PmsSecurityContextEventGuard;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.Optional;

@Singleton
public class ExecutionInfoUpdateEventHandler implements PlanStatusUpdateObserver {
  private final PMSPipelineService pmsPipelineService;
  private final PlanExecutionService planExecutionService;

  @Inject
  public ExecutionInfoUpdateEventHandler(
      PMSPipelineService pmsPipelineService, PlanExecutionService planExecutionService) {
    this.pmsPipelineService = pmsPipelineService;
    this.planExecutionService = planExecutionService;
  }

  @Override
  public void onPlanStatusUpdate(Ambiance ambiance) {
    // this security context guard is needed because now pipeline get requires proper permissions to be set in the case
    // when the Pipeline is REMOTE
    try (PmsSecurityContextEventGuard ignore = new PmsSecurityContextEventGuard(ambiance)) {
      String accountId = AmbianceUtils.getAccountId(ambiance);
      String projectId = AmbianceUtils.getProjectIdentifier(ambiance);
      String orgId = AmbianceUtils.getOrgIdentifier(ambiance);
      String pipelineId = ambiance.getMetadata().getPipelineIdentifier();
      Optional<PipelineEntity> pipelineEntity = pmsPipelineService.get(accountId, orgId, projectId, pipelineId, false);
      if (!pipelineEntity.isPresent()) {
        return;
      }
      Status status = planExecutionService.get(ambiance.getPlanExecutionId()).getStatus();
      ExecutionSummaryInfo executionSummaryInfo = pipelineEntity.get().getExecutionSummaryInfo();
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
    } catch (Exception e) {
      // ignore
    }
  }
}
