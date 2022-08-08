/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.interrupts.statusupdate;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.pms.contracts.execution.Status.INPUT_WAITING;
import static io.harness.pms.contracts.execution.Status.PAUSED;
import static io.harness.pms.contracts.execution.Status.RUNNING;

import io.harness.annotations.dev.OwnedBy;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.engine.executions.plan.PlanExecutionService;
import io.harness.engine.observers.NodeStatusUpdateHandler;
import io.harness.engine.observers.NodeUpdateInfo;
import io.harness.execution.NodeExecution;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.execution.utils.StatusUtils;

import com.google.inject.Inject;
import java.util.EnumSet;

@OwnedBy(PIPELINE)
public class ResumeStepStatusUpdate implements NodeStatusUpdateHandler {
  @Inject private NodeExecutionService nodeExecutionService;
  @Inject private PlanExecutionService planExecutionService;

  @Override
  public void handleNodeStatusUpdate(NodeUpdateInfo nodeStatusUpdateInfo) {
    boolean resumePlan = resumeParents(nodeStatusUpdateInfo.getNodeExecution());
    if (resumePlan) {
      Status planStatus = planExecutionService.calculateStatusExcluding(
          nodeStatusUpdateInfo.getPlanExecutionId(), nodeStatusUpdateInfo.getNodeExecutionId());
      if (!StatusUtils.isFinalStatus(planStatus)) {
        planExecutionService.updateStatus(nodeStatusUpdateInfo.getPlanExecutionId(), planStatus);
      }
    }
  }

  private boolean resumeParents(NodeExecution nodeExecution) {
    if (nodeExecution.getParentId() == null) {
      return true;
    }
    NodeExecution parentNodeExecution = nodeExecutionService.updateStatusWithOps(
        nodeExecution.getParentId(), RUNNING, null, EnumSet.of(INPUT_WAITING, PAUSED));
    return parentNodeExecution != null && resumeParents(parentNodeExecution);
  }
}
