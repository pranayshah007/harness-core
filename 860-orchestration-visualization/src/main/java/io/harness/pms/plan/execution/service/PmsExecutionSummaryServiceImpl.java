/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.plan.execution.service;

import static io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.engine.utils.OrchestrationUtils;
import io.harness.execution.NodeExecution;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.ExecutionStatus;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.plan.execution.ExecutionSummaryUpdateUtils;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.pms.plan.execution.beans.dto.GraphLayoutNodeDTO;
import io.harness.repositories.executions.PmsExecutionSummaryRespository;

import com.google.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@OwnedBy(HarnessTeam.PIPELINE)
public class PmsExecutionSummaryServiceImpl implements PmsExecutionSummaryService {
  @Inject NodeExecutionService nodeExecutionService;
  @Inject private PmsExecutionSummaryRespository pmsExecutionSummaryRepository;

  /**
   * Updates all the fields in the stage graph from nodeExecutions.
   * @param planExecutionId
   */
  public void updateStageOfIdentityType(String planExecutionId, Update update) {
    List<NodeExecution> nodeExecutions =
        nodeExecutionService.fetchStageExecutionsWithEndTsAndStatusProjection(planExecutionId);

    // This is done inorder to reduce the load while updating stageInfo. Here we will update only the status.
    for (NodeExecution nodeExecution : nodeExecutions) {
      String nodeSetupId = AmbianceUtils.obtainCurrentSetupId(nodeExecution.getAmbiance());
      update.set(PlanExecutionSummaryKeys.layoutNodeMap + "." + nodeSetupId + ".status",
          ExecutionStatus.getExecutionStatus(nodeExecution.getStatus()));
      update.set(PlanExecutionSummaryKeys.layoutNodeMap + "." + nodeSetupId + ".endTs", nodeExecution.getEndTs());
    }
  }

  @Override
  public void regenerateStageLayoutGraph(String planExecutionId, List<NodeExecution> nodeExecutions) {
    Update update = new Update();
    for (NodeExecution nodeExecution : nodeExecutions) {
      addStageNodeInGraphIfUnderStrategy(planExecutionId, nodeExecution, update);
      ExecutionSummaryUpdateUtils.addStageUpdateCriteria(update, nodeExecution);
    }
    Criteria criteria = Criteria.where(PlanExecutionSummaryKeys.planExecutionId).is(planExecutionId);
    Query query = new Query(criteria);
    pmsExecutionSummaryRepository.update(query, update);
  }

  @Override
  public void updateEndTs(String planExecutionId, NodeExecution nodeExecution) {
    Update update = new Update();
    boolean updated = false;

    // Update endTs at pipeline level
    if (OrchestrationUtils.isPipelineNode(nodeExecution)) {
      if (nodeExecution.getEndTs() != null) {
        updated = true;
        update.set(PlanExecutionSummaryKeys.endTs, nodeExecution.getEndTs());
      }
    }

    // Update endTs at stage level
    if (OrchestrationUtils.isStageNode(nodeExecution)) {
      String stageUuid = AmbianceUtils.obtainCurrentSetupId(nodeExecution.getAmbiance());
      if (nodeExecution.getEndTs() != null) {
        updated = true;
        update.set(PlanExecutionSummaryKeys.layoutNodeMap + "." + stageUuid + ".endTs", nodeExecution.getEndTs());
      }
    }
    if (updated) {
      Criteria criteria = Criteria.where(PlanExecutionSummaryKeys.planExecutionId).is(planExecutionId);
      Query query = new Query(criteria);
      pmsExecutionSummaryRepository.update(query, update);
    }
  }

  @Override
  public void addStageNodeInGraphIfUnderStrategy(
      String planExecutionId, NodeExecution nodeExecution, Update summaryUpdate) {
    if (OrchestrationUtils.isStageNode(nodeExecution)
        && AmbianceUtils.getStrategyLevelFromAmbiance(nodeExecution.getAmbiance()).isPresent()) {
      String stageSetupId = nodeExecution.getNodeId();
      Update update = new Update();
      Ambiance ambiance = nodeExecution.getAmbiance();
      Optional<PipelineExecutionSummaryEntity> entity =
          getPipelineExecutionSummary(AmbianceUtils.getAccountId(ambiance), AmbianceUtils.getOrgIdentifier(ambiance),
              AmbianceUtils.getProjectIdentifier(ambiance), planExecutionId);
      if (!entity.isPresent()) {
        return;
      }
      PipelineExecutionSummaryEntity pipelineExecutionSummaryEntity = entity.get();
      Map<String, GraphLayoutNodeDTO> graphLayoutNodeDTOMap = pipelineExecutionSummaryEntity.getLayoutNodeMap();
      if (graphLayoutNodeDTOMap.containsKey(nodeExecution.getUuid())) {
        return;
      }
      GraphLayoutNodeDTO graphLayoutNodeDTO = graphLayoutNodeDTOMap.get(stageSetupId);
      modifyGraphLayoutNode(graphLayoutNodeDTO, nodeExecution);
      update.set(PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys.layoutNodeMap + "." + nodeExecution.getUuid(),
          graphLayoutNodeDTO);
      String strategyNodeId = AmbianceUtils.getStrategyLevelFromAmbiance(ambiance).get().getSetupId();
      update.addToSet(PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys.layoutNodeMap + "." + strategyNodeId
              + ".edgeLayoutList.currentNodeChildren",
          nodeExecution.getUuid());
      summaryUpdate.pull(PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys.layoutNodeMap + "." + strategyNodeId
              + ".edgeLayoutList.currentNodeChildren",
          stageSetupId);
      summaryUpdate.set(
          PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys.layoutNodeMap + "." + stageSetupId + ".hidden", true);
      update(planExecutionId, update);
    }
  }
  @Override
  public Optional<PipelineExecutionSummaryEntity> getPipelineExecutionSummary(
      String accountId, String orgId, String projectId, String planExecutionId) {
    return pmsExecutionSummaryRepository.findByAccountIdAndOrgIdentifierAndProjectIdentifierAndPlanExecutionId(
        accountId, orgId, projectId, planExecutionId);
  }

  @Override
  public void update(String planExecutionId, Update update) {
    Criteria criteria = Criteria.where(PlanExecutionSummaryKeys.planExecutionId).is(planExecutionId);
    Query query = new Query(criteria);
    pmsExecutionSummaryRepository.update(query, update);
  }

  /**
   * Modifies the identifier and name of the dummy node we are copying.
   * @param graphLayoutNodeDTO
   * @param nodeExecution
   */
  private void modifyGraphLayoutNode(GraphLayoutNodeDTO graphLayoutNodeDTO, NodeExecution nodeExecution) {
    graphLayoutNodeDTO.setNodeIdentifier(nodeExecution.getIdentifier());
    graphLayoutNodeDTO.setName(nodeExecution.getName());
    graphLayoutNodeDTO.setNodeExecutionId(nodeExecution.getUuid());
  }
}
