/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.pms.advise.handlers;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.execution.NodeExecution.NodeExecutionKeys;

import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.data.structure.UUIDGenerator;
import io.harness.engine.OrchestrationEngine;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.engine.executions.plan.PlanService;
import io.harness.engine.pms.advise.AdviserResponseHandler;
import io.harness.engine.utils.PmsLevelUtils;
import io.harness.execution.NodeExecution;
import io.harness.plan.IdentityPlanNode;
import io.harness.plan.Node;
import io.harness.plan.NodeType;
import io.harness.plan.PlanNode;
import io.harness.pms.contracts.advisers.AdviserResponse;
import io.harness.pms.contracts.advisers.NextStepAdvise;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.plan.ExecutionMode;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.execution.utils.NodeProjectionUtils;
import io.harness.utils.ExecutionModeUtils;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import java.util.Collections;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDC)
@Slf4j
public class NextStepHandler implements AdviserResponseHandler {
  @Inject private OrchestrationEngine engine;
  @Inject private NodeExecutionService nodeExecutionService;
  @Inject private PlanService planService;

  @Override
  public void handleAdvise(NodeExecution prevNodeExecution, AdviserResponse adviserResponse) {
    NextStepAdvise advise = adviserResponse.getNextStepAdvise();
    ExecutionMode executionMode = prevNodeExecution.getAmbiance().getMetadata().getExecutionMode();
    runNextNode(prevNodeExecution, advise.getNextNodeId(), executionMode);
  }

  public void runNextNode(NodeExecution prevNodeExecution, String nextNodeId, ExecutionMode executionMode) {
    if (EmptyPredicate.isNotEmpty(nextNodeId)) {
      Node nextNode =
          Preconditions.checkNotNull(planService.fetchNode(prevNodeExecution.getAmbiance().getPlanId(), nextNodeId));
      nextNode = createIdentityNodeIfRequired(nextNode, prevNodeExecution, executionMode);
      String runtimeId = generateUuid();
      // Update NodeExecution nextId and endTs
      nodeExecutionService.updateV2(prevNodeExecution.getUuid(),
          ops -> ops.set(NodeExecutionKeys.nextId, runtimeId).set(NodeExecutionKeys.endTs, System.currentTimeMillis()));
      Ambiance cloned = AmbianceUtils.cloneForFinish(
          prevNodeExecution.getAmbiance(), PmsLevelUtils.buildLevelFromNode(runtimeId, nextNode));
      // prevNodeExecution will not contain nextId and endTs
      engine.runNextNode(cloned, nextNode, prevNodeExecution, null);
    } else {
      engine.endNodeExecution(prevNodeExecution.getAmbiance());
    }
  }

  @VisibleForTesting
  Node createIdentityNodeIfRequired(Node nextNode, NodeExecution prevNodeExecution, ExecutionMode executionMode) {
    // If nextNode already instance of IdentityPlanNode, or if in rollback mode, the plan node received is to be
    // preserved, then return the node as is.
    if (checkIfSameNodeIsRequired(nextNode, executionMode)) {
      return nextNode;
    }
    if (EmptyPredicate.isEmpty(prevNodeExecution.getParentId())) {
      log.error("ParentId is empty for nodeExecution: {} and planExecutionId: {}", prevNodeExecution.getUuid(),
          prevNodeExecution.getAmbiance().getPlanExecutionId());
      return nextNode;
    }
    NodeExecution parentNodeExecution =
        nodeExecutionService.getWithFieldsIncluded(prevNodeExecution.getParentId(), NodeProjectionUtils.withAmbiance);
    // Create IdentityNode for nextNode when the parentNodeExecution.node is of type IdentityNode
    if (parentNodeExecution.getNodeType() == NodeType.IDENTITY_PLAN_NODE) {
      NodeExecution originalNodeExecution = nodeExecutionService.getWithFieldsIncluded(
          prevNodeExecution.getOriginalNodeExecutionId(), NodeProjectionUtils.withNextId);
      Node identityNode = IdentityPlanNode.mapPlanNodeToIdentityNode(UUIDGenerator.generateUuid(), nextNode,
          nextNode.getIdentifier(), nextNode.getName(), nextNode.getStepType(), originalNodeExecution.getNextId());
      planService.saveIdentityNodesForMatrix(Collections.singletonList(identityNode), prevNodeExecution.getPlanId());
      return identityNode;
    }
    return nextNode;
  }

  boolean checkIfSameNodeIsRequired(Node nextNode, ExecutionMode executionMode) {
    return nextNode.getNodeType().equals(NodeType.IDENTITY_PLAN_NODE)
        || (ExecutionModeUtils.isRollbackMode(executionMode) && ((PlanNode) nextNode).isPreserveInRollbackMode());
  }
}
