/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.observers;

import io.harness.beans.ExecutionErrorInfo;
import io.harness.dto.converter.FailureInfoDTOConverter;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.engine.observers.NodeStatusUpdateObserver;
import io.harness.engine.observers.NodeUpdateInfo;
import io.harness.engine.utils.OrchestrationUtils;
import io.harness.execution.NodeExecution;
import io.harness.pms.execution.ExecutionStatus;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys;
import io.harness.pms.plan.execution.service.PmsExecutionSummaryService;

import com.google.inject.Inject;
import javax.validation.constraints.NotNull;
import org.springframework.data.mongodb.core.query.Update;

public class PipelineExecutionSummaryFailureInfoUpdateHandler implements NodeStatusUpdateObserver {
  @Inject PmsExecutionSummaryService pmsExecutionSummaryService;

  @Override
  public void onNodeStatusUpdate(@NotNull NodeUpdateInfo nodeUpdateInfo) {
    String planExecutionId = nodeUpdateInfo.getPlanExecutionId();
    NodeExecution nodeExecution = nodeUpdateInfo.getNodeExecution();
    Update update = new Update();
    if (OrchestrationUtils.isPipelineNode(nodeExecution)) {
      ExecutionStatus status = ExecutionStatus.getExecutionStatus(nodeExecution.getStatus());
      if (status == ExecutionStatus.FAILED) {
        update.set(PlanExecutionSummaryKeys.executionErrorInfo,
            ExecutionErrorInfo.builder().message(nodeExecution.getFailureInfo().getErrorMessage()).build());
        update.set(PlanExecutionSummaryKeys.failureInfo,
            FailureInfoDTOConverter.toFailureInfoDTO(nodeExecution.getFailureInfo()));
        pmsExecutionSummaryService.update(planExecutionId, update);
      }
    }
  }
}
