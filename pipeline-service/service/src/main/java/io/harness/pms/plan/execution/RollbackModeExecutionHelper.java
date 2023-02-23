/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.execution.NodeExecution;
import io.harness.plan.IdentityPlanNode;
import io.harness.plan.Node;
import io.harness.plan.Plan;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
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
    List<Node> updatedPlanNodes = nodeExecutions.stream()
                                      .map(nodeExecution -> {
                                        if (nodeExecution.getStepType().getStepCategory() == StepCategory.STAGE) {
                                          return nodeExecution.getNode();
                                        } else {
                                          return IdentityPlanNode.mapPlanNodeToIdentityNode(nodeExecution.getNode(),
                                              nodeExecution.getStepType(), nodeExecution.getUuid());
                                        }
                                      })
                                      .collect(Collectors.toList());
    plan.getPlanNodes().forEach(planNode -> {
      if (nodeIDsToPreserve.contains(planNode.getUuid())) {
        updatedPlanNodes.add(planNode);
      }
    });
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