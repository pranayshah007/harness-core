/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.common;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.springdata.PersistenceUtils.DEFAULT_RETRY_POLICY;

import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.engine.execution.ExecutionInputService;
import io.harness.engine.executions.plan.PlanService;
import io.harness.engine.observers.NodeExecutionDeleteObserver;
import io.harness.execution.NodeExecution;
import io.harness.graph.stepDetail.service.NodeExecutionInfoService;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.steps.approval.step.ApprovalInstanceService;
import io.harness.steps.resourcerestraint.beans.HoldingScope;
import io.harness.steps.resourcerestraint.service.ResourceRestraintInstanceService;
import io.harness.steps.wait.WaitStepService;
import io.harness.timeout.TimeoutEngine;
import io.harness.waiter.persistence.SpringPersistenceWrapper;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import net.jodah.failsafe.Failsafe;

@OwnedBy(PIPELINE)
// Its a syncObserver to delete metadata for given nodeExecutions
public class NodeExecutionMetadataDeleteObserver implements NodeExecutionDeleteObserver {
  @Inject private SpringPersistenceWrapper springPersistenceWrapper;
  @Inject private TimeoutEngine timeoutEngine;
  @Inject private ResourceRestraintInstanceService resourceRestraintInstanceService;
  @Inject private PlanService planService;
  @Inject private NodeExecutionInfoService pmsGraphStepDetailsService;
  @Inject private WaitStepService waitStepService;
  @Inject private ExecutionInputService executionInputService;
  @Inject private ApprovalInstanceService approvalInstanceService;

  @Override
  public void onNodesDelete(List<NodeExecution> nodeExecutionsToDelete) {
    if (EmptyPredicate.isEmpty(nodeExecutionsToDelete)) {
      return;
    }

    Set<String> timeoutInstanceIds = new HashSet<>();
    List<String> correlationIds = new LinkedList<>();
    Set<String> stageNodeExecutionIds = new HashSet<>();
    Set<String> nodeEntityIds = new HashSet<>();
    Set<String> nodeExecutionIds = new HashSet<>();
    Set<String> waitStepNodeExecutionIds = new HashSet<>();
    Set<String> executionInputNodeExecutionIds = new HashSet<>();
    Set<String> approvalNodeExecutionIds = new HashSet<>();
    String planId = nodeExecutionsToDelete.get(0).getPlanId();
    for (NodeExecution nodeExecution : nodeExecutionsToDelete) {
      nodeExecutionIds.add(nodeExecution.getUuid());
      if (isNotEmpty(nodeExecution.getTimeoutInstanceIds())) {
        timeoutInstanceIds.addAll(nodeExecution.getTimeoutInstanceIds());
      }
      if (isNotEmpty(nodeExecution.getAdviserTimeoutInstanceIds())) {
        timeoutInstanceIds.addAll(nodeExecution.getAdviserTimeoutInstanceIds());
      }
      if (isNotEmpty(nodeExecution.getNotifyId())) {
        correlationIds.add(nodeExecution.getNotifyId());
      }
      if (nodeExecution.getStepType() != null
          && nodeExecution.getStepType().getStepCategory().equals(StepCategory.STAGE)) {
        stageNodeExecutionIds.add(nodeExecution.getUuid());
      }
      if (EmptyPredicate.isNotEmpty(nodeExecution.getNodeId())) {
        nodeEntityIds.add(nodeExecution.getNodeId());
      }
      if (nodeExecution.getStepType() != null
          && nodeExecution.getStepType().getType().equals(StepSpecTypeConstants.WAIT_STEP)) {
        waitStepNodeExecutionIds.add(nodeExecution.getUuid());
      }
      if (nodeExecution.getExecutionInputConfigured().equals(Boolean.TRUE)) {
        executionInputNodeExecutionIds.add(nodeExecution.getUuid());
      }
      if (approvalInstanceService.isNodeExecutionOfApprovalStepType(nodeExecution)) {
        approvalNodeExecutionIds.add(nodeExecution.getUuid());
      }
    }
    // Delete the waitInstances for correlationsIds in nodeExecutionIds
    springPersistenceWrapper.deleteWaitInstancesAndMetadata(correlationIds);
    // Delete the timeoutInstanceIds
    Failsafe.with(DEFAULT_RETRY_POLICY).get(() -> {
      timeoutEngine.deleteTimeouts(new ArrayList<>(timeoutInstanceIds));
      return true;
    });
    // Delete resource restraint instances
    resourceRestraintInstanceService.deleteInstancesForGivenReleaseType(stageNodeExecutionIds, HoldingScope.STAGE);
    // Delete nodes entity
    planService.deleteNodesForGivenIds(planId, nodeEntityIds);
    // Delete NodeExecutionsInfo
    pmsGraphStepDetailsService.deleteNodeExecutionInfoForGivenIds(nodeExecutionIds);
    // Delete WaiStepInstance for given waiStep nodeExecutionIds
    waitStepService.deleteWaitStepInstancesForGivenNodeExecutionIds(waitStepNodeExecutionIds);
    // Delete ExecutionInputInstance for given nodeExecutionIds
    executionInputService.deleteExecutionInputInstanceForGivenNodeExecutionIds(executionInputNodeExecutionIds);
    // Delete the approval instances
    approvalInstanceService.deleteByNodeExecutionIds(approvalNodeExecutionIds);
  }
}
