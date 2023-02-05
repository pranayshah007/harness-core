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
import io.harness.engine.observers.NodeExecutionDeleteObserver;
import io.harness.execution.NodeExecution;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.steps.approval.step.ApprovalInstanceService;
import io.harness.steps.resourcerestraint.beans.HoldingScope;
import io.harness.steps.resourcerestraint.service.ResourceRestraintInstanceService;
import io.harness.timeout.TimeoutEngine;
import io.harness.waiter.persistence.SpringPersistenceWrapper;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.jodah.failsafe.Failsafe;

@OwnedBy(PIPELINE)
// Its a syncObserver to delete metadata for given nodeExecutions
public class NodeExecutionMetadataDeleteObserver implements NodeExecutionDeleteObserver {
  @Inject private SpringPersistenceWrapper springPersistenceWrapper;
  @Inject private TimeoutEngine timeoutEngine;
  @Inject private ResourceRestraintInstanceService resourceRestraintInstanceService;
  @Inject private ApprovalInstanceService approvalInstanceService;
  @Override
  public void onNodesDelete(List<NodeExecution> nodeExecutionsToDelete) {
    if (EmptyPredicate.isEmpty(nodeExecutionsToDelete)) {
      return;
    }

    Set<String> timeoutInstanceIds = new HashSet<>();
    Set<String> correlationIds = new HashSet<>();
    Set<String> stageNodeExecutionIds = new HashSet<>();
    Set<String> approvalNodeExecutionIds = new HashSet<>();
    for (NodeExecution nodeExecution : nodeExecutionsToDelete) {
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
    resourceRestraintInstanceService.deleteInstancesForGivenReleaseType(stageNodeExecutionIds, HoldingScope.STAGE);
    // Delete the approval instances
    approvalInstanceService.deleteByNodeExecutionIds(approvalNodeExecutionIds);
  }
}
