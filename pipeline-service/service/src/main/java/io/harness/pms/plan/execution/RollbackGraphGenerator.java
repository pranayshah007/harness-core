/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ExecutionGraph;
import io.harness.beans.ExecutionNode;
import io.harness.dto.OrchestrationGraphDTO;
import io.harness.gitsync.sdk.EntityGitDetails;
import io.harness.pms.pipeline.mappers.ExecutionGraphMapper;
import io.harness.pms.pipeline.mappers.PipelineExecutionSummaryDtoMapper;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.pms.plan.execution.beans.dto.ChildExecutionDetailDTO;
import io.harness.pms.plan.execution.beans.dto.GraphLayoutNodeDTO;
import io.harness.pms.plan.execution.service.PMSExecutionService;
import io.harness.steps.StepSpecTypeConstants;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PIPELINE)
@Singleton
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
public class RollbackGraphGenerator {
  PMSExecutionService executionService;

  ChildExecutionDetailDTO checkAndBuildRollbackGraph(String accountId, String orgId, String projectId,
      PipelineExecutionSummaryEntity executionSummaryEntity, EntityGitDetails entityGitDetails, String childStageId,
      String stageNodeExecutionId, String stageNodeId) {
    // if rollback mode execution has started, then executionSummaryEntity will have its planExecutionId, and the
    // rollback graph will be always there
    boolean generateRollbackGraph = executionSummaryEntity.getRollbackModeExecutionId() != null;
    if (!generateRollbackGraph) {
      return null;
    }
    boolean isPipelineRollbackStageSelected = isPipelineRollbackStageSelected(executionSummaryEntity, stageNodeId);

    String childExecutionId = executionSummaryEntity.getRollbackModeExecutionId();
    PipelineExecutionSummaryEntity executionSummaryEntityForChild =
        executionService.getPipelineExecutionSummaryEntity(accountId, orgId, projectId, childExecutionId);
    changeIdsInLayoutNodeMap(executionSummaryEntityForChild);

    ExecutionGraph executionGraphForChild = null;
    if (isPipelineRollbackStageSelected && childStageId != null) {
      String actualChildStageId = getOriginalUuid(childStageId, childExecutionId);
      OrchestrationGraphDTO orchestrationGraph = executionService.getOrchestrationGraph(
          actualChildStageId, executionSummaryEntityForChild.getPlanExecutionId(), stageNodeExecutionId);
      executionGraphForChild =
          ExecutionGraphMapper.toExecutionGraph(orchestrationGraph, executionSummaryEntityForChild);
      changeSetupIdInChildExecutionGraph(executionGraphForChild, childExecutionId, actualChildStageId);
    }
    return ChildExecutionDetailDTO.builder()
        .pipelineExecutionSummary(
            PipelineExecutionSummaryDtoMapper.toDto(executionSummaryEntityForChild, entityGitDetails))
        .executionGraph(executionGraphForChild)
        .build();
  }

  boolean isPipelineRollbackStageSelected(PipelineExecutionSummaryEntity executionSummaryEntity, String stageNodeId) {
    return executionSummaryEntity.getLayoutNodeMap().containsKey(stageNodeId)
        && executionSummaryEntity.getLayoutNodeMap()
               .get(stageNodeId)
               .getNodeType()
               .equals(StepSpecTypeConstants.PIPELINE_ROLLBACK_STAGE);
  }

  void changeIdsInLayoutNodeMap(PipelineExecutionSummaryEntity childSummary) {
    Map<String, GraphLayoutNodeDTO> originalLayoutNodeMap = childSummary.getLayoutNodeMap();
    String prefix = childSummary.getPlanExecutionId();
    Map<String, GraphLayoutNodeDTO> newLayoutNodeMap = new HashMap<>();
    for (Map.Entry<String, GraphLayoutNodeDTO> entry : originalLayoutNodeMap.entrySet()) {
      String uuid = entry.getKey();
      String newUuid = prefix + uuid;
      GraphLayoutNodeDTO layoutNode = entry.getValue();
      layoutNode.setNodeUuid(newUuid);
      List<String> nextIds = layoutNode.getEdgeLayoutList().getNextIds();
      List<String> newNextIds = nextIds.stream().map(nextId -> prefix + nextId).collect(Collectors.toList());
      nextIds.clear();
      nextIds.addAll(newNextIds);
      newLayoutNodeMap.put(newUuid, layoutNode);
      if (uuid.equals(childSummary.getStartingNodeId())) {
        childSummary.setStartingNodeId(newUuid);
      }
    }
    originalLayoutNodeMap.clear();
    originalLayoutNodeMap.putAll(newLayoutNodeMap);
  }

  String getOriginalUuid(String childStageId, String rbModePlanExecutionId) {
    return childStageId.replace(rbModePlanExecutionId, "");
  }

  void changeSetupIdInChildExecutionGraph(
      ExecutionGraph executionGraphForChild, String childExecutionId, String actualChildStageId) {
    Map<String, ExecutionNode> nodeMap = executionGraphForChild.getNodeMap();
    for (Map.Entry<String, ExecutionNode> entry : nodeMap.entrySet()) {
      ExecutionNode executionNode = entry.getValue();
      if (executionNode.getSetupId().equals(actualChildStageId)) {
        executionNode.setSetupId(childExecutionId + actualChildStageId);
      }
    }
  }
}
