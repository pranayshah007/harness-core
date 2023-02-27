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
import io.harness.execution.PlanExecutionMetadata;
import io.harness.plan.IdentityPlanNode;
import io.harness.plan.Node;
import io.harness.plan.Plan;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.plan.ExecutionTriggerInfo;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.helpers.PrincipalInfoHelper;
import io.harness.pms.pipeline.service.PipelineMetadataService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.PIPELINE)
@Singleton
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
public class RollbackModeExecutionHelper {
  NodeExecutionService nodeExecutionService;
  PipelineMetadataService pipelineMetadataService;
  PrincipalInfoHelper principalInfoHelper;

  public ExecutionMetadata transformExecutionMetadata(ExecutionMetadata executionMetadata, String planExecutionID,
      ExecutionTriggerInfo triggerInfo, String accountId, String orgIdentifier, String projectIdentifier) {
    return executionMetadata.toBuilder()
        .setExecutionUuid(planExecutionID)
        .setTriggerInfo(triggerInfo)
        .setRunSequence(pipelineMetadataService.incrementExecutionCounter(
            accountId, orgIdentifier, projectIdentifier, executionMetadata.getPipelineIdentifier()))
        .setPrincipalInfo(principalInfoHelper.getPrincipalInfoFromSecurityContext())
        .build();
  }

  public PlanExecutionMetadata transformPlanExecutionMetadata(
      PlanExecutionMetadata planExecutionMetadata, String planExecutionID) {
    return planExecutionMetadata.withPlanExecutionId(planExecutionID)
        .withProcessedYaml(transformProcessedYaml(planExecutionMetadata.getProcessedYaml()))
        .withUuid(null); // this uuid is the mongo uuid
  }

  private String transformProcessedYaml(String processedYaml) {
    return processedYaml;
  }

  public Plan transformPlanForRollbackMode(Plan plan, String previousExecutionId, List<String> nodeIDsToPreserve) {
    Map<String, Node> planNodeIDToUpdatedPlanNodes = new HashMap<>();

    List<NodeExecution> nodeExecutions = nodeExecutionService.fetchNodesWithStageFQNs(previousExecutionId);
    // create Identity Node for every Node Execution under Stage nodes
    for (NodeExecution nodeExecution : nodeExecutions) {
      Node planNode = nodeExecution.getNode();
      if (planNode.getStepType().getStepCategory() == StepCategory.STAGE
          || EmptyPredicate.isEmpty(planNode.getStageFqn())
          || !planNode.getStageFqn().matches("pipeline\\.stages\\..+")) {
        continue;
      }
      IdentityPlanNode identityPlanNode = IdentityPlanNode.mapPlanNodeToIdentityNode(
          nodeExecution.getNode(), nodeExecution.getStepType(), nodeExecution.getUuid());
      planNodeIDToUpdatedPlanNodes.put(planNode.getUuid(), identityPlanNode);
    }

    for (Node planNode : plan.getPlanNodes()) {
      if (nodeIDsToPreserve.contains(planNode.getUuid())
          || planNode.getStepType().getStepCategory() == StepCategory.STAGE
          || EmptyPredicate.isEmpty(planNode.getStageFqn())
          || !planNode.getStageFqn().matches("pipeline\\.stages\\..+")) {
        planNodeIDToUpdatedPlanNodes.put(planNode.getUuid(), planNode);
      }
    }
    return Plan.builder()
        .uuid(plan.getUuid())
        .planNodes(planNodeIDToUpdatedPlanNodes.values())
        .startingNodeId(plan.getStartingNodeId())
        .setupAbstractions(plan.getSetupAbstractions())
        .graphLayoutInfo(plan.getGraphLayoutInfo())
        .validUntil(plan.getValidUntil())
        .valid(plan.isValid())
        .errorResponse(plan.getErrorResponse())
        .build();
  }
}