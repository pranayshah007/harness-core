/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.execution.NodeExecution;
import io.harness.plan.IdentityPlanNode;
import io.harness.plan.Node;
import io.harness.plan.Plan;
import io.harness.pms.contracts.steps.StepCategory;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.PIPELINE)
@Singleton
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
public class RollbackModeExecutionHelper {
  private final NodeExecutionService nodeExecutionService;

  public Plan transformPlanForRollbackMode(Plan plan, String previousExecutionId, List<String> nodeIDsToPreserve) {
    List<NodeExecution> nodeExecutions = nodeExecutionService.fetchNodesWithStageFQNs(previousExecutionId);
    Map<String, NodeExecution> planNodeIDtoNodeExecution =
        nodeExecutions.stream().collect(Collectors.toMap(NodeExecution::getNodeId, Function.identity()));

    List<Node> updatedPlanNodes = new ArrayList<>();
    for (Node planNode : plan.getPlanNodes()) {
      if (nodeIDsToPreserve.contains(planNode.getUuid())
          || planNode.getStepType().getStepCategory() == StepCategory.STAGE
          || EmptyPredicate.isEmpty(planNode.getStageFqn())) {
        updatedPlanNodes.add(planNode);
        continue;
      }
      // The processed YAML used in rollback mode is the same as the one used in the original execution. Hence, the
      // plan node IDs used will also be the same
      NodeExecution nodeExecution = planNodeIDtoNodeExecution.get(planNode.getUuid());
      if (nodeExecution == null) {
        // this means that this given node was not executed in the previous execution
        continue;
      }
      IdentityPlanNode identityPlanNode = IdentityPlanNode.mapPlanNodeToIdentityNode(
          nodeExecution.getNode(), nodeExecution.getStepType(), nodeExecution.getUuid());
      updatedPlanNodes.add(identityPlanNode);
    }
    return Plan.builder()
        .uuid(plan.getUuid())
        .planNodes(updatedPlanNodes)
        .startingNodeId(plan.getStartingNodeId())
        .setupAbstractions(plan.getSetupAbstractions())
        .graphLayoutInfo(plan.getGraphLayoutInfo())
        .validUntil(plan.getValidUntil())
        .valid(plan.isValid())
        .errorResponse(plan.getErrorResponse())
        .build();
  }
}