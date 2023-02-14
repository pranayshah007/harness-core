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
import io.harness.execution.ExecutionModeUtils;
import io.harness.execution.NodeExecution;
import io.harness.plan.IdentityPlanNode;
import io.harness.plan.Node;
import io.harness.plan.NodeType;
import io.harness.pms.contracts.advisers.AdviserResponse;
import io.harness.pms.contracts.advisers.NextStepAdvise;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import java.util.Collections;
import java.util.Set;

@OwnedBy(CDC)
public class NextStepHandler implements AdviserResponseHandler {
  @Inject private OrchestrationEngine engine;
  @Inject private NodeExecutionService nodeExecutionService;
  @Inject private PlanService planService;

  @Override
  public void handleAdvise(NodeExecution prevNodeExecution, AdviserResponse adviserResponse) {
    NextStepAdvise advise = adviserResponse.getNextStepAdvise();
    if (EmptyPredicate.isNotEmpty(advise.getNextNodeId())) {
      Node nextNode = Preconditions.checkNotNull(
          planService.fetchNode(prevNodeExecution.getAmbiance().getPlanId(), advise.getNextNodeId()));
      nextNode = createIdentityNodeIfRequired(nextNode, prevNodeExecution);
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

  private Node createIdentityNodeIfRequired(Node nextNode, NodeExecution prevNodeExecution) {
    // If nextNode already instance of IdentityPlanNode, then return the nextNode.
    if (nextNode instanceof IdentityPlanNode) {
      return nextNode;
    }
    // Create IdentityNode for nextNode when the preNodeExecution.node is of type IdentityNode and preNodeExecution is
    // leaf node. Basically prevNodeExecution(leaf) ran using IdentityNode so nextNode should also be executed with
    // IdentityNode.
    if (prevNodeExecution.getNode().getNodeType() == NodeType.IDENTITY_PLAN_NODE
        && ExecutionModeUtils.isLeafMode(prevNodeExecution.getMode())) {
      NodeExecution originalNodeExecution = nodeExecutionService.getWithFieldsIncluded(
          prevNodeExecution.getOriginalNodeExecutionId(), Set.of(NodeExecutionKeys.nextId));
      Node identityNode = IdentityPlanNode.mapPlanNodeToIdentityNode(UUIDGenerator.generateUuid(), nextNode,
          nextNode.getIdentifier(), nextNode.getName(), nextNode.getStepType(), originalNodeExecution.getNextId());
      planService.saveIdentityNodesForMatrix(Collections.singletonList(identityNode), prevNodeExecution.getPlanId());
      return identityNode;
    }
    return nextNode;
  }
}
