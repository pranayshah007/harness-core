/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.plan.execution.service;

import static io.harness.springdata.PersistenceUtils.DEFAULT_RETRY_POLICY;

import io.harness.OrchestrationStepTypes;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ExecutionErrorInfo;
import io.harness.concurrency.ConcurrentChildInstance;
import io.harness.data.structure.EmptyPredicate;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.engine.utils.OrchestrationUtils;
import io.harness.execution.NodeExecution;
import io.harness.graph.stepDetail.service.PmsGraphStepDetailsService;
import io.harness.plan.NodeType;
import io.harness.plancreator.strategy.StrategyType;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.execution.ExecutionStatus;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.plan.execution.ExecutionSummaryUpdateUtils;
import io.harness.pms.plan.execution.LayoutNodeGraphConstants;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys;
import io.harness.pms.plan.execution.beans.dto.GraphLayoutNodeDTO;
import io.harness.pms.plan.execution.beans.dto.GraphLayoutNodeDTO.GraphLayoutNodeDTOKeys;
import io.harness.repositories.executions.PmsExecutionSummaryRepository;

import com.google.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.util.CloseableIterator;

@OwnedBy(HarnessTeam.PIPELINE)
@Slf4j
public class PmsExecutionSummaryServiceImpl implements PmsExecutionSummaryService {
  @Inject NodeExecutionService nodeExecutionService;
  @Inject private PmsExecutionSummaryRepository pmsExecutionSummaryRepository;
  @Inject private PmsGraphStepDetailsService pmsGraphStepDetailsService;

  /**
   * Performs the following:
   * 1. Fetches all stage and strategy nodeExecutions from db with identity nodes
   * 2. Iterate over each node Execution
   *    * If node is of type stage then update status and endTs
   *    * if node is of type strategy then add all its children in layoutNodeMap and update the execution summary
   *
   * @param planExecutionId - the execution id for which the identity stages needs to be updated
   * @param update - the reference to update operation
   * @return true if there is an update otherwise returns false
   */
  public boolean updateIdentityStageOrStrategyNodes(String planExecutionId, Update update) {
    boolean updateApplied = false;

    List<NodeExecution> stageOrStrategyNodeExecutions =
        nodeExecutionService.fetchStageExecutionsWithEndTsAndStatusProjection(planExecutionId);

    Optional<PipelineExecutionSummaryEntity> entity =
        pmsExecutionSummaryRepository.findByPlanExecutionId(planExecutionId);
    if (entity.isEmpty()) {
      log.error(String.format("PlanExecutionSummary not found for plan execution id: %s", planExecutionId));
      return false;
    }
    Map<String, GraphLayoutNodeDTO> graphLayoutNode = entity.get().getLayoutNodeMap();

    // This is done inorder to reduce the load while updating stageInfo. Here we will update only the status.
    for (NodeExecution nodeExecution : stageOrStrategyNodeExecutions) {
      if (nodeExecution.getStepType().getStepCategory() != StepCategory.STRATEGY) {
        // This means it is stage nodes
        updateApplied = handleStageIdentityNodes(nodeExecution, update);
      } else

          if (!AmbianceUtils.isCurrentStrategyLevelAtStage(nodeExecution.getAmbiance())) {
        // There is a chance that strategy might belong to step, this check is used to validate if strategy level is
        // actually at stage
        continue;
      }
      if (graphLayoutNode.get(nodeExecution.getNodeId()) == null) {
        log.error("layout node is null for key [{}] in GraphLayoutNodeMap for planExecutionId: [{}]",
            nodeExecution.getNodeId(), planExecutionId);
        continue;
      }

      // Filter child executions for this strategy
      List<NodeExecution> childrenNodeExecution = stageOrStrategyNodeExecutions.stream()
                                                      .filter(o -> o.getParentId().equals(nodeExecution.getUuid()))
                                                      .collect(Collectors.toList());

      // Update Max concurrency in graph (consumed by UI) if the type of strategy is not parallelism
      // For parallelism, the maxConcurrency cannot be defined via yaml, so we are ignoring its addition in graph.
      if (!graphLayoutNode.get(nodeExecution.getNodeId()).getNodeType().equals(StrategyType.PARALLELISM.name())) {
        ConcurrentChildInstance concurrentChildInstance =
            pmsGraphStepDetailsService.fetchConcurrentChildInstance(nodeExecution.getUuid());
        if (concurrentChildInstance != null && !nodeExecution.getExecutableResponses().isEmpty()) {
          update.set(PlanExecutionSummaryKeys.layoutNodeMap + "." + nodeExecution.getNodeId()
                  + ".moduleInfo.maxConcurrency.value",
              nodeExecution.getExecutableResponses().get(0).getChildren().getMaxConcurrency());
          updateApplied = true;
        }
      }

      // We need to update the status and StepParameters for strategy node
      updateStatusAndStepParametersInStrategyNode(nodeExecution, update);

      String stageSetupId = getStageSetupId(childrenNodeExecution, graphLayoutNode, nodeExecution);
      if (stageSetupId == null) {
        continue;
      }
      // This adds the childNodes as children for the strategy node in top level graph.
      addAndUpdateChildNodesForStrategy(
          planExecutionId, update, graphLayoutNode, nodeExecution, childrenNodeExecution, stageSetupId);
    }

    return updateApplied;
  }

  /**
   *
   * This adds the child stage of strategy to the graph if it is not present. It clones the dummy node that we add
   * during plan creation This also adds the generated nodes as children to the strategy nodes.
   * @param planExecutionId
   * @param update
   * @param graphLayoutNode
   * @param nodeExecution
   * @param childrenNodeExecution
   * @param stageSetupId
   */
  private void addAndUpdateChildNodesForStrategy(String planExecutionId, Update update,
      Map<String, GraphLayoutNodeDTO> graphLayoutNode, NodeExecution nodeExecution,
      List<NodeExecution> childrenNodeExecution, String stageSetupId) {
    if (childrenNodeExecution.isEmpty()) {
      return;
    }
    for (NodeExecution stageNodeExecution : childrenNodeExecution) {
      // If the child already exists in graph then ignore.
      if (!alreadyAddedAsChild(graphLayoutNode, nodeExecution.getNodeId(), stageNodeExecution.getUuid())) {
        cloneGraphLayoutNodeDTO(
            graphLayoutNode.get(stageSetupId), stageNodeExecution.getUuid(), update, stageNodeExecution.getNodeId());
      }
    }

    List<String> childrenExecutionIds =
        childrenNodeExecution.stream().map(NodeExecution::getUuid).collect(Collectors.toList());
    addChildrenToStrategyNode(update, planExecutionId, nodeExecution, stageSetupId, childrenExecutionIds);
  }

  @Override
  public void regenerateStageLayoutGraph(String planExecutionId, List<NodeExecution> nodeExecutions) {
    Update update = new Update();
    for (NodeExecution nodeExecution : nodeExecutions) {
      ExecutionSummaryUpdateUtils.addStageUpdateCriteria(update, nodeExecution);
    }
    Criteria criteria = Criteria.where(PlanExecutionSummaryKeys.planExecutionId).is(planExecutionId);
    Query query = new Query(criteria);
    pmsExecutionSummaryRepository.update(query, update);
  }

  @Override
  public PipelineExecutionSummaryEntity save(PipelineExecutionSummaryEntity pipelineExecutionSummaryEntity) {
    return pmsExecutionSummaryRepository.save(pipelineExecutionSummaryEntity);
  }

  public boolean updateStrategyPlanNode(String planExecutionId, NodeExecution strategyNodeExecution, Update update) {
    // If nodeExecution is of type identity, or it is not stage strategy node then ignore.
    if (strategyNodeExecution.getNodeType() == NodeType.IDENTITY_PLAN_NODE
        || strategyNodeExecution.getStepType().getStepCategory() != StepCategory.STRATEGY
        || !AmbianceUtils.isCurrentStrategyLevelAtStage(strategyNodeExecution.getAmbiance())) {
      return false;
    }

    ConcurrentChildInstance concurrentChildInstance =
        pmsGraphStepDetailsService.fetchConcurrentChildInstance(strategyNodeExecution.getUuid());
    if (concurrentChildInstance != null && !strategyNodeExecution.getExecutableResponses().isEmpty()) {
      if (strategyNodeExecution.getNode().getStepParameters().containsKey("strategyType")
          && !strategyNodeExecution.getNode()
                  .getStepParameters()
                  .get("strategyType")
                  .equals(StrategyType.PARALLELISM.name())) {
        update.set(PlanExecutionSummaryKeys.layoutNodeMap + "." + strategyNodeExecution.getNodeId()
                + ".moduleInfo.maxConcurrency.value",
            strategyNodeExecution.getExecutableResponses().get(0).getChildren().getMaxConcurrency());
      }

      // Extract the node id for the given child
      String childSetupId =
          strategyNodeExecution.getExecutableResponses().get(0).getChildren().getChildren(0).getChildNodeId();
      Ambiance ambiance = strategyNodeExecution.getAmbiance();
      Optional<PipelineExecutionSummaryEntity> entity =
          pmsExecutionSummaryRepository.findByPlanExecutionId(planExecutionId);
      if (entity.isEmpty()) {
        return false;
      }
      addChildStagesForStrategy(update, entity.get(), concurrentChildInstance.getChildrenNodeExecutionIds(),
          childSetupId, strategyNodeExecution);
    }
    updateStatusAndStepParametersInStrategyNode(strategyNodeExecution, update);
    return true;
  }

  @Override
  public void update(String planExecutionId, Update update) {
    Criteria criteria = Criteria.where(PlanExecutionSummaryKeys.planExecutionId).is(planExecutionId);
    Query query = new Query(criteria);
    pmsExecutionSummaryRepository.update(query, update);
  }

  @Override
  public boolean handleNodeExecutionUpdateFromGraphUpdate(
      String planExecutionId, NodeExecution nodeExecution, Update update) {
    // Update strategy node data if it is not an identity node and strategy node
    boolean updateRequired = updateStrategyPlanNode(planExecutionId, nodeExecution, update);

    // Update identity nodes if only they are in final status.
    if ((OrchestrationUtils.isStageOrParallelStageNode(nodeExecution)
            || nodeExecution.getStepType().getStepCategory() == StepCategory.STRATEGY)
        && nodeExecution.getNodeType() == NodeType.IDENTITY_PLAN_NODE) {
      updateRequired = updateIdentityStageOrStrategyNodes(planExecutionId, update) || updateRequired;
    }
    if (nodeExecution.getStepType().getType().equals(OrchestrationStepTypes.PIPELINE_ROLLBACK_STAGE)) {
      String previousStagePlanNodeId = nodeExecutionService.get(nodeExecution.getPreviousId()).getNodeId();
      ExecutionSummaryUpdateUtils.updateNextIdOfStageBeforePipelineRollback(
          update, nodeExecution.getNodeId(), previousStagePlanNodeId);
    }
    return ExecutionSummaryUpdateUtils.addStageUpdateCriteria(update, nodeExecution) || updateRequired;
  }

  @Override
  public PipelineExecutionSummaryEntity getPipelineExecutionSummaryWithProjections(
      String planExecutionId, Set<String> fields) {
    Criteria criteria = Criteria.where(PlanExecutionSummaryKeys.planExecutionId).is(planExecutionId);
    return pmsExecutionSummaryRepository.getPipelineExecutionSummaryWithProjections(criteria, fields);
  }

  @Override
  public CloseableIterator<PipelineExecutionSummaryEntity> fetchPlanExecutionIdsFromAnalytics(
      String accountId, String orgIdentifier, String projectIdentifier, String pipelineIdentifier) {
    // Uses - accountId_organizationId_projectId_pipelineId idx
    Criteria criteria = Criteria.where(PlanExecutionSummaryKeys.accountId)
                            .is(accountId)
                            .and(PlanExecutionSummaryKeys.orgIdentifier)
                            .is(orgIdentifier)
                            .and(PlanExecutionSummaryKeys.projectIdentifier)
                            .is(projectIdentifier)
                            .and(PlanExecutionSummaryKeys.pipelineIdentifier)
                            .is(pipelineIdentifier);
    Query query = new Query(criteria);
    query.fields().include(PlanExecutionSummaryKeys.planExecutionId);
    return pmsExecutionSummaryRepository.fetchExecutionSummaryEntityFromAnalytics(query);
  }

  @Override
  public void deleteAllSummaryForGivenPlanExecutionIds(Set<String> planExecutionIds) {
    if (EmptyPredicate.isEmpty(planExecutionIds)) {
      return;
    }
    Failsafe.with(DEFAULT_RETRY_POLICY).get(() -> {
      // Uses - id index
      pmsExecutionSummaryRepository.deleteAllByPlanExecutionIdIn(planExecutionIds);
      return true;
    });
  }

  /**
   * Adds child stages of strategy node to the top level graph
   * @param update
   * @param pipelineExecutionSummaryEntity
   * @param childrenExecutionIds
   * @param childSetupId
   * @param strategyNodeExecution
   * @return
   */
  private boolean addChildStagesForStrategy(Update update,
      PipelineExecutionSummaryEntity pipelineExecutionSummaryEntity, List<String> childrenExecutionIds,
      String childSetupId, NodeExecution strategyNodeExecution) {
    Map<String, GraphLayoutNodeDTO> graphLayoutNodeDTOMap = pipelineExecutionSummaryEntity.getLayoutNodeMap();
    GraphLayoutNodeDTO graphLayoutNodeDTO = graphLayoutNodeDTOMap.get(childSetupId);
    for (String childId : childrenExecutionIds) {
      // This ensures that we do not add the graph layoutNode again if already added.
      // Since module field is only added while cloning, this means that the cloning was successful
      if (graphLayoutNodeDTOMap.containsKey(childId) && graphLayoutNodeDTOMap.get(childId).getModule() != null) {
        continue;
      }
      cloneGraphLayoutNodeDTO(graphLayoutNodeDTO, childId, update, graphLayoutNodeDTO.getNodeUuid());
    }
    addChildrenToStrategyNode(update, pipelineExecutionSummaryEntity.getPlanExecutionId(), strategyNodeExecution,
        childSetupId, childrenExecutionIds);
    return true;
  }

  /**
   * During strategy, we spawn multiple stages,
   * and we already have a dummy node during plan creation,
   * this method is used to copy fields from dummy node to original node.
   */
  private void cloneGraphLayoutNodeDTO(
      GraphLayoutNodeDTO graphLayoutNodeDTO, String uuid, Update update, String setupId) {
    String baseKey = PlanExecutionSummaryKeys.layoutNodeMap + "." + uuid + ".";
    update.set(baseKey + GraphLayoutNodeDTOKeys.nodeType, graphLayoutNodeDTO.getNodeType());
    update.set(baseKey + GraphLayoutNodeDTOKeys.nodeGroup, graphLayoutNodeDTO.getNodeGroup());
    update.set(baseKey + GraphLayoutNodeDTOKeys.edgeLayoutList, graphLayoutNodeDTO.getEdgeLayoutList());
    update.set(baseKey + GraphLayoutNodeDTOKeys.skipInfo, graphLayoutNodeDTO.getSkipInfo());
    update.set(baseKey + GraphLayoutNodeDTOKeys.nodeUuid, setupId);
    update.set(
        baseKey + GraphLayoutNodeDTOKeys.executionInputConfigured, graphLayoutNodeDTO.getExecutionInputConfigured());
  }

  /**
   * This is used to get stage setup id of the child nodes for strategy which we want to add in graph.
   */
  private String getStageSetupId(List<NodeExecution> childrenNodeExecution,
      Map<String, GraphLayoutNodeDTO> graphLayoutNode, NodeExecution nodeExecution) {
    String stageSetupId = null;
    // Get the stageSetupId from ChildNodeExecutions of strategy. Will be null if all the stages were successful in
    // previous execution.
    for (NodeExecution childNodeExecution : childrenNodeExecution) {
      if (childNodeExecution.getNodeType() == NodeType.PLAN_NODE && stageSetupId == null) {
        stageSetupId = childNodeExecution.getNodeId();
      }
    }

    // If null, then that means all our childNodeExecution are of type plan node.
    if (stageSetupId == null) {
      if (EmptyPredicate.isNotEmpty(
              graphLayoutNode.get(nodeExecution.getNodeId()).getEdgeLayoutList().getCurrentNodeChildren())) {
        stageSetupId =
            graphLayoutNode.get(nodeExecution.getNodeId()).getEdgeLayoutList().getCurrentNodeChildren().get(0);
      }
    }

    return stageSetupId;
  }

  /**
   * This method updates the status and endTs for identity nodes. All other fields are updated by our
   * ExecutionSummaryUpdateUtils#addStageUpdateCriteria but status and endTs should be populated once identity nodes are
   * in final status, hence this call is done.
   * @param nodeExecution
   * @param update
   * @return
   */
  private boolean handleStageIdentityNodes(NodeExecution nodeExecution, Update update) {
    String graphNodeId = AmbianceUtils.obtainCurrentSetupId(nodeExecution.getAmbiance());
    if (AmbianceUtils.getStrategyLevelFromAmbiance(nodeExecution.getAmbiance()).isPresent()) {
      graphNodeId = nodeExecution.getUuid();
    }
    update.set(String.format(LayoutNodeGraphConstants.STATUS, graphNodeId),
        ExecutionStatus.getExecutionStatus(nodeExecution.getStatus()));
    update.set(String.format(LayoutNodeGraphConstants.END_TS, graphNodeId), nodeExecution.getEndTs());
    return true;
  }

  /**
   * Checks if the given childNodeExecutionId is already added as child for strategy node.
   * @param graphLayoutNode
   * @param strategyNodeId
   * @param childNodeExecutionId
   * @return
   */
  private boolean alreadyAddedAsChild(
      Map<String, GraphLayoutNodeDTO> graphLayoutNode, String strategyNodeId, String childNodeExecutionId) {
    return graphLayoutNode.get(strategyNodeId)
        .getEdgeLayoutList()
        .getCurrentNodeChildren()
        .contains(childNodeExecutionId);
  }

  /**
   * This removes the dummy node id created during plan creation from strategy children.
   * @param planExecutionId
   * @param nodeExecution
   * @param stageSetupId
   */
  private void pullStageStepIdFromStrategyChildren(
      String planExecutionId, NodeExecution nodeExecution, String stageSetupId) {
    // This is done because we cannot addToSet and pull in same update. We need to fire two operations.
    Update spotUpdate = new Update();
    spotUpdate.pull(PlanExecutionSummaryKeys.layoutNodeMap + "." + nodeExecution.getNodeId()
            + ".edgeLayoutList.currentNodeChildren",
        stageSetupId);
    update(planExecutionId, spotUpdate);
  }

  /**
   * Adds children in graphLayoutNodeDTO for strategy node and removes the dummy node
   * @param update
   * @param planExecutionId
   * @param strategyNodeExecution
   * @param childSetupId
   * @param childrenExecutionIds
   */
  private void addChildrenToStrategyNode(Update update, String planExecutionId, NodeExecution strategyNodeExecution,
      String childSetupId, List<String> childrenExecutionIds) {
    // This removes the dummy node
    pullStageStepIdFromStrategyChildren(planExecutionId, strategyNodeExecution, childSetupId);
    // This adds all the new nodes
    update
        .addToSet(PlanExecutionSummaryKeys.layoutNodeMap + "." + strategyNodeExecution.getNodeId()
            + ".edgeLayoutList.currentNodeChildren")
        .each(childrenExecutionIds);
  }

  /**
   * This updates the status and the step parameters in the layoutNodeMap for strategy node.
   * StrategyNodeExecution should only be passed as parameter.
   * @param strategyNodeExecution
   * @param update
   */

  private void updateStatusAndStepParametersInStrategyNode(NodeExecution strategyNodeExecution, Update update) {
    ExecutionStatus status = ExecutionStatus.getExecutionStatus(strategyNodeExecution.getStatus());
    update.set(String.format(LayoutNodeGraphConstants.STATUS, strategyNodeExecution.getNodeId()), status);
    update.set(
        PlanExecutionSummaryKeys.layoutNodeMap + "." + strategyNodeExecution.getNodeId() + ".moduleInfo.stepParameters",
        strategyNodeExecution.getResolvedStepParameters());
    update.set(PlanExecutionSummaryKeys.layoutNodeMap + "." + strategyNodeExecution.getNodeId() + ".startTs",
        strategyNodeExecution.getStartTs());
    if (strategyNodeExecution.getFailureInfo() != null) {
      update.set(PlanExecutionSummaryKeys.layoutNodeMap + "." + strategyNodeExecution.getNodeId() + ".failureInfo",
          ExecutionErrorInfo.builder().message(strategyNodeExecution.getFailureInfo().getErrorMessage()).build());
    }
    update.set(PlanExecutionSummaryKeys.layoutNodeMap + "." + strategyNodeExecution.getNodeId() + ".endTs",
        strategyNodeExecution.getEndTs());
  }
}
