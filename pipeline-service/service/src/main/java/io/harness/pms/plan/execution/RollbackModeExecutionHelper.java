/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.data.structure.EmptyPredicate;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.engine.executions.plan.PlanService;
import io.harness.execution.NodeExecution;
import io.harness.execution.NodeExecution.NodeExecutionKeys;
import io.harness.execution.PlanExecutionMetadata;
import io.harness.execution.StagesExecutionMetadata;
import io.harness.plan.IdentityPlanNode;
import io.harness.plan.Node;
import io.harness.plan.Plan;
import io.harness.plan.PlanNode;
import io.harness.pms.contracts.advisers.AdviserObtainment;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.plan.ExecutionMetadata.Builder;
import io.harness.pms.contracts.plan.ExecutionMode;
import io.harness.pms.contracts.plan.ExecutionTriggerInfo;
import io.harness.pms.contracts.plan.PipelineStageInfo;
import io.harness.pms.contracts.plan.PostExecutionRollbackInfo;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.execution.utils.NodeProjectionUtils;
import io.harness.pms.helpers.PrincipalInfoHelper;
import io.harness.pms.pipeline.service.PipelineMetadataService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.util.CloseableIterator;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_SERVICE_ENVIRONMENT})
@OwnedBy(HarnessTeam.PIPELINE)
@Singleton
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
public class RollbackModeExecutionHelper {
  private NodeExecutionService nodeExecutionService;
  private PlanService planService;
  private PipelineMetadataService pipelineMetadataService;
  private PrincipalInfoHelper principalInfoHelper;
  private RollbackModeYamlTransformer rollbackModeYamlTransformer;

  public ExecutionMetadata transformExecutionMetadata(ExecutionMetadata executionMetadata, String planExecutionID,
      ExecutionTriggerInfo triggerInfo, String accountId, String orgIdentifier, String projectIdentifier,
      ExecutionMode executionMode, PipelineStageInfo parentStageInfo, List<String> stageNodeExecutionIds) {
    String originalPlanExecutionId = executionMetadata.getExecutionUuid();
    Builder newMetadata = executionMetadata.toBuilder()
                              .setExecutionUuid(planExecutionID)
                              .setTriggerInfo(triggerInfo)
                              .setRunSequence(pipelineMetadataService.incrementExecutionCounter(accountId,
                                  orgIdentifier, projectIdentifier, executionMetadata.getPipelineIdentifier()))
                              .setPrincipalInfo(principalInfoHelper.getPrincipalInfoFromSecurityContext())
                              .setExecutionMode(executionMode)
                              .setOriginalPlanExecutionIdForRollbackMode(originalPlanExecutionId);
    if (parentStageInfo != null) {
      newMetadata = newMetadata.setPipelineStageInfo(parentStageInfo);
    }
    if (EmptyPredicate.isNotEmpty(stageNodeExecutionIds)) {
      List<NodeExecution> rollbackStageNodeExecutions = nodeExecutionService.getAllWithFieldIncluded(
          new HashSet<>(stageNodeExecutionIds), NodeProjectionUtils.fieldsForNodeAndAmbiance);
      newMetadata.addAllPostExecutionRollbackInfo(rollbackStageNodeExecutions.stream()
                                                      .map(ne -> createPostExecutionRollbackInfo(ne.getAmbiance()))
                                                      .collect(Collectors.toList()));
    }
    return newMetadata.build();
  }

  private PostExecutionRollbackInfo createPostExecutionRollbackInfo(Ambiance ambiance) {
    PostExecutionRollbackInfo.Builder builder = PostExecutionRollbackInfo.newBuilder();
    String stageId;
    // This stageId will also be the startingNodeId in the execution graph. So if its under the
    // strategy(Multi-deployment) then it must be set to strategy setupId so that graph is shown correctly.
    if (AmbianceUtils.getStrategyLevelFromAmbiance(ambiance).isPresent()) {
      // If the nodeExecutions is under the strategy, then set the stageId to strategy setupId.
      stageId = ambiance.getLevels(ambiance.getLevelsCount() - 2).getSetupId();
      builder.setRollbackStageStrategyMetadata(AmbianceUtils.obtainCurrentLevel(ambiance).getStrategyMetadata());
    } else {
      // If not under strategy then stage setupId will be the stageId.
      stageId = AmbianceUtils.obtainCurrentSetupId(ambiance);
    }
    builder.setPostExecutionRollbackStageId(stageId);
    String stageExecutionId = AmbianceUtils.obtainCurrentRuntimeId(ambiance);
    builder.setOriginalStageExecutionId(stageExecutionId);
    return builder.build();
  }

  public PlanExecutionMetadata transformPlanExecutionMetadata(PlanExecutionMetadata planExecutionMetadata,
      String planExecutionID, ExecutionMode executionMode, List<String> stageNodeExecutionIds, String updatedNotes) {
    String originalPlanExecutionId = planExecutionMetadata.getPlanExecutionId();
    PlanExecutionMetadata metadata =
        planExecutionMetadata.withPlanExecutionId(planExecutionID)
            .withProcessedYaml(rollbackModeYamlTransformer.transformProcessedYaml(
                planExecutionMetadata.getProcessedYaml(), executionMode, originalPlanExecutionId))
            .withNotes(updatedNotes) // these are updated notes given for a pipelineRollback.
            .withUuid(null); // this uuid is the mongo uuid. It is being set as null so that when this Plan Execution
                             // Metadata is saved later on in the execution, a new object is stored rather than
                             // replacing the Metadata for the original execution

    if (EmptyPredicate.isEmpty(stageNodeExecutionIds)) {
      return metadata;
    }
    List<String> rollbackStageFQNs =
        nodeExecutionService
            .getAllWithFieldIncluded(new HashSet<>(stageNodeExecutionIds), Set.of(NodeExecutionKeys.planNode))
            .stream()
            .map(NodeExecution::getStageFqn)
            .collect(Collectors.toList());
    metadata.setStagesExecutionMetadata(StagesExecutionMetadata.builder()
                                            .fullPipelineYaml(planExecutionMetadata.getYaml())
                                            .stageIdentifiers(rollbackStageFQNs)
                                            .build());
    return metadata;
  }

  /**
   * Step1: Initialise a map from planNodeIDs to Plan Nodes
   * Step2: fetch all node executions of previous execution that are the descendants of any stage
   * Step3: create identity plan nodes for all node executions that are the descendants of any stage, and add them to
   * the map
   * Step4: Go through `createdPlan`. If any Plan node has AdvisorObtainments for POST_EXECUTION_ROLLBACK Mode, add them
   * to the corresponding Identity Plan Node in the initialised map
   * Step5: From `createdPlan`, pick out all nodes that are not a descendants of some stage, and add them to the
   * initialised map.
   * Step6: For all IDs in `nodeIDsToPreserve`, remove the Identity Plan Nodes in the map, and put the
   * Plan nodes from `createdPlan`
   */
  public Plan transformPlanForRollbackMode(Plan createdPlan, String previousExecutionId, List<String> nodeIDsToPreserve,
      ExecutionMode executionMode, List<String> rollbackStageIds) {
    // steps 1, 2, and 3
    Map<String, Node> planNodeIDToUpdatedPlanNodes =
        buildIdentityNodes(previousExecutionId, createdPlan.getPlanNodes());

    // step 4
    addAdvisorsToIdentityNodes(createdPlan, planNodeIDToUpdatedPlanNodes, executionMode, rollbackStageIds);

    // steps 5 and 6
    addPreservedPlanNodes(createdPlan, nodeIDsToPreserve, planNodeIDToUpdatedPlanNodes);

    return Plan.builder()
        .uuid(createdPlan.getUuid())
        .planNodes(planNodeIDToUpdatedPlanNodes.values())
        .startingNodeId(createdPlan.getStartingNodeId())
        .setupAbstractions(createdPlan.getSetupAbstractions())
        .graphLayoutInfo(createdPlan.getGraphLayoutInfo())
        .validUntil(createdPlan.getValidUntil())
        .valid(createdPlan.isValid())
        .errorResponse(createdPlan.getErrorResponse())
        .build();
  }

  Map<String, Node> buildIdentityNodes(String previousExecutionId, List<Node> createdPlanNodes) {
    Map<String, Node> planNodeIDToUpdatedNodes = new HashMap<>();

    CloseableIterator<NodeExecution> nodeExecutions =
        getNodeExecutionsWithOnlyRequiredFields(previousExecutionId, createdPlanNodes);

    while (nodeExecutions.hasNext()) {
      NodeExecution nodeExecution = nodeExecutions.next();
      String planNodeIdFromNodeExec = nodeExecution.getNodeId();
      if (nodeExecution.getStepType().getStepCategory() == StepCategory.STAGE) {
        continue;
      }
      if (planNodeIDToUpdatedNodes.containsKey(nodeExecution.getNodeId())) {
        // this means that the current plan node ID was already added, hence this plan node has multiple node executions
        // mapped to it. Hence, the identity node created for the plan node needs to be updated to contain the IDs of
        // all the node executions mapped to it
        IdentityPlanNode previouslyAddedNode = (IdentityPlanNode) planNodeIDToUpdatedNodes.get(planNodeIdFromNodeExec);
        previouslyAddedNode.convertToListOfOGNodeExecIds(nodeExecution.getUuid());
        planNodeIDToUpdatedNodes.put(planNodeIdFromNodeExec, previouslyAddedNode);
      } else {
        Node node = planService.fetchNode(nodeExecution.getNodeId());
        IdentityPlanNode identityPlanNode = IdentityPlanNode.mapPlanNodeToIdentityNode(
            node, nodeExecution.getStepType(), nodeExecution.getUuid(), true);
        planNodeIDToUpdatedNodes.put(planNodeIdFromNodeExec, identityPlanNode);
      }
    }
    return planNodeIDToUpdatedNodes;
  }

  CloseableIterator<NodeExecution> getNodeExecutionsWithOnlyRequiredFields(
      String previousExecutionId, List<Node> createdPlanNodes) {
    List<String> stageFQNs = createdPlanNodes.stream()
                                 .filter(n -> n.getStepCategory() == StepCategory.STAGE)
                                 .map(Node::getStageFqn)
                                 .collect(Collectors.toList());
    return nodeExecutionService.fetchNodeExecutionsForGivenStageFQNs(
        previousExecutionId, stageFQNs, NodeProjectionUtils.fieldsForIdentityNodeCreation);
  }

  void addAdvisorsToIdentityNodes(Plan createdPlan, Map<String, Node> planNodeIDToUpdatedPlanNodes,
      ExecutionMode executionMode, List<String> stageFQNsToRollback) {
    for (Node planNode : createdPlan.getPlanNodes()) {
      if (EmptyPredicate.isEmpty(planNode.getAdvisorObtainmentsForExecutionMode())) {
        continue;
      }
      if (executionMode == ExecutionMode.POST_EXECUTION_ROLLBACK) {
        if (EmptyPredicate.isEmpty(stageFQNsToRollback) || !stageFQNsToRollback.contains(planNode.getStageFqn())) {
          continue;
        }
      }
      List<AdviserObtainment> adviserObtainments = planNode.getAdvisorObtainmentsForExecutionMode().get(executionMode);
      if (EmptyPredicate.isNotEmpty(adviserObtainments)) {
        IdentityPlanNode updatedNode = (IdentityPlanNode) planNodeIDToUpdatedPlanNodes.get(planNode.getUuid());
        if (updatedNode == null) {
          // this means that the stage had failed before the node could start in the previous execution
          continue;
        }
        planNodeIDToUpdatedPlanNodes.put(
            planNode.getUuid(), updatedNode.withAdviserObtainments(adviserObtainments).withUseAdviserObtainments(true));
      }
    }
  }

  void addPreservedPlanNodes(
      Plan createdPlan, List<String> nodeIDsToPreserve, Map<String, Node> planNodeIDToUpdatedPlanNodes) {
    for (Node node : createdPlan.getPlanNodes()) {
      if (nodeIDsToPreserve.contains(node.getUuid()) || isStageOrAncestorOfSomeStage(node)) {
        PlanNode planNode = ((PlanNode) node).withPreserveInRollbackMode(true);
        planNodeIDToUpdatedPlanNodes.put(node.getUuid(), planNode);
      }
    }
  }

  boolean isStageOrAncestorOfSomeStage(Node planNode) {
    StepCategory stepCategory = planNode.getStepCategory();
    if (Arrays.asList(StepCategory.PIPELINE, StepCategory.STAGES, StepCategory.STAGE).contains(stepCategory)) {
      return true;
    }
    // todo: once fork and strategy are divided in sub categories of step and stage, add that check as well
    // parallel nodes and strategy nodes need to be plan nodes so that we don't take the advisor response from the
    // previous execution. Previous execution's advisor response would be setting next step as something we dont want in
    // rollback mode. We want the new advisors set in the Plan Node to be used
    return Arrays.asList(StepCategory.FORK, StepCategory.STRATEGY).contains(stepCategory);
  }
}
