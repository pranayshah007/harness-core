/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.mappers;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.execution.StagesExecutionMetadata;
import io.harness.gitsync.sdk.EntityGitDetails;
import io.harness.gitsync.sdk.EntityGitDetailsMapper;
import io.harness.pms.execution.ExecutionStatus;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.pms.plan.execution.beans.dto.GraphLayoutNodeDTO;
import io.harness.pms.plan.execution.beans.dto.PipelineExecutionSummaryDTO;
import io.harness.pms.stages.BasicStageInfo;
import io.harness.pms.stages.StageExecutionSelectorHelper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PIPELINE)
@UtilityClass
@Slf4j
public class PipelineExecutionSummaryDtoMapper {
  public PipelineExecutionSummaryDTO toDto(
      PipelineExecutionSummaryEntity pipelineExecutionSummaryEntity, EntityGitDetails entityGitDetails) {
    entityGitDetails = updateEntityGitDetails(entityGitDetails);
    Map<String, GraphLayoutNodeDTO> layoutNodeDTOMap = pipelineExecutionSummaryEntity.getLayoutNodeMap();
    String startingNodeId = pipelineExecutionSummaryEntity.getStartingNodeId();
    StagesExecutionMetadata stagesExecutionMetadata = pipelineExecutionSummaryEntity.getStagesExecutionMetadata();
    boolean isStagesExecution = stagesExecutionMetadata != null && stagesExecutionMetadata.isStagesExecution();
    List<String> stageIdentifiers =
        stagesExecutionMetadata == null ? null : stagesExecutionMetadata.getStageIdentifiers();
    Map<String, String> stagesExecutedNames = null;
    if (EmptyPredicate.isNotEmpty(stageIdentifiers)) {
      stagesExecutedNames = getStageNames(stageIdentifiers, stagesExecutionMetadata.getFullPipelineYaml());
    }
    return PipelineExecutionSummaryDTO.builder()
        .name(pipelineExecutionSummaryEntity.getName())
        .orgIdentifier(pipelineExecutionSummaryEntity.getOrgIdentifier())
        .projectIdentifier(pipelineExecutionSummaryEntity.getProjectIdentifier())
        .createdAt(pipelineExecutionSummaryEntity.getCreatedAt())
        .layoutNodeMap(layoutNodeDTOMap)
        .moduleInfo(ModuleInfoMapper.getModuleInfo(pipelineExecutionSummaryEntity.getModuleInfo()))
        .startingNodeId(startingNodeId)
        .planExecutionId(pipelineExecutionSummaryEntity.getPlanExecutionId())
        .pipelineIdentifier(pipelineExecutionSummaryEntity.getPipelineIdentifier())
        .startTs(pipelineExecutionSummaryEntity.getStartTs())
        .endTs(pipelineExecutionSummaryEntity.getEndTs())
        .status(pipelineExecutionSummaryEntity.getStatus())
        .executionInputConfigured(pipelineExecutionSummaryEntity.getExecutionInputConfigured())
        .executionTriggerInfo(pipelineExecutionSummaryEntity.getExecutionTriggerInfo())
        .executionErrorInfo(pipelineExecutionSummaryEntity.getExecutionErrorInfo())
        .successfulStagesCount(getStagesCount(layoutNodeDTOMap, startingNodeId, ExecutionStatus.SUCCESS))
        .failedStagesCount(getStagesCount(layoutNodeDTOMap, startingNodeId, ExecutionStatus.FAILED))
        .runningStagesCount(getStagesCount(layoutNodeDTOMap, startingNodeId, ExecutionStatus.RUNNING))
        .totalStagesCount(getStagesCount(layoutNodeDTOMap, startingNodeId))
        .runSequence(pipelineExecutionSummaryEntity.getRunSequence())
        .tags(pipelineExecutionSummaryEntity.getTags())
        .modules(EmptyPredicate.isEmpty(pipelineExecutionSummaryEntity.getModules())
                ? new ArrayList<>()
                : pipelineExecutionSummaryEntity.getModules())
        .gitDetails(entityGitDetails)
        .canRetry(pipelineExecutionSummaryEntity.isLatestExecution())
        .showRetryHistory(!pipelineExecutionSummaryEntity.isLatestExecution()
            || !pipelineExecutionSummaryEntity.getPlanExecutionId().equals(
                pipelineExecutionSummaryEntity.getRetryExecutionMetadata().getRootExecutionId()))
        .governanceMetadata(pipelineExecutionSummaryEntity.getGovernanceMetadata())
        .isStagesExecution(isStagesExecution)
        .stagesExecuted(stageIdentifiers)
        .stagesExecutedNames(stagesExecutedNames)
        .parentStageInfo(pipelineExecutionSummaryEntity.getParentStageInfo())
        .allowStageExecutions(pipelineExecutionSummaryEntity.isStagesExecutionAllowed())
        .storeType(pipelineExecutionSummaryEntity.getStoreType())
        .connectorRef(EmptyPredicate.isEmpty(pipelineExecutionSummaryEntity.getConnectorRef())
                ? null
                : pipelineExecutionSummaryEntity.getConnectorRef())
        .abortedBy(pipelineExecutionSummaryEntity.getAbortedBy())
        .build();
  }

  private Map<String, String> getStageNames(List<String> stageIdentifiers, String pipelineYaml) {
    Map<String, String> identifierToNames = new LinkedHashMap<>();
    List<BasicStageInfo> stageInfoList = StageExecutionSelectorHelper.getStageInfoList(pipelineYaml);
    stageInfoList.forEach(stageInfo -> {
      String identifier = stageInfo.getIdentifier();
      if (stageIdentifiers.contains(identifier)) {
        identifierToNames.put(identifier, stageInfo.getName());
      }
    });
    return identifierToNames;
  }

  public int getStagesCount(
      Map<String, GraphLayoutNodeDTO> layoutNodeDTOMap, String startingNodeId, ExecutionStatus executionStatus) {
    if (startingNodeId == null) {
      return 0;
    }
    int count = 0;
    GraphLayoutNodeDTO nodeDTO = layoutNodeDTOMap.get(startingNodeId);
    if (!nodeDTO.getNodeType().equals("parallel") && nodeDTO.getStatus().equals(executionStatus)) {
      count++;
    } else if (nodeDTO.getNodeType().equals("parallel")) {
      for (String child : nodeDTO.getEdgeLayoutList().getCurrentNodeChildren()) {
        if (layoutNodeDTOMap.get(child).getStatus().equals(executionStatus)) {
          count++;
        }
      }
    }
    if (nodeDTO.getEdgeLayoutList().getNextIds().isEmpty()) {
      return count;
    }
    return count + getStagesCount(layoutNodeDTOMap, nodeDTO.getEdgeLayoutList().getNextIds().get(0), executionStatus);
  }
  public int getStagesCount(Map<String, GraphLayoutNodeDTO> layoutNodeDTOMap, String startingNodeId) {
    if (startingNodeId == null) {
      return 0;
    }
    int count = 0;
    GraphLayoutNodeDTO nodeDTO = layoutNodeDTOMap.get(startingNodeId);
    if (!nodeDTO.getNodeType().equals("parallel")) {
      count++;
    } else if (nodeDTO.getNodeType().equals("parallel")) {
      count += nodeDTO.getEdgeLayoutList().getCurrentNodeChildren().size();
    }
    if (nodeDTO.getEdgeLayoutList().getNextIds().isEmpty()) {
      return count;
    }
    return count + getStagesCount(layoutNodeDTOMap, nodeDTO.getEdgeLayoutList().getNextIds().get(0));
  }

  private EntityGitDetails updateEntityGitDetails(EntityGitDetails entityGitDetails) {
    if (entityGitDetails == null) {
      return null;
    }
    String rootFolder = entityGitDetails.getRootFolder();
    String filePath = entityGitDetails.getFilePath();
    String repoIdentifier = entityGitDetails.getRepoIdentifier();
    String repoName = entityGitDetails.getRepoName();
    String branch = entityGitDetails.getBranch();
    String objectId = entityGitDetails.getObjectId();
    String commitId = entityGitDetails.getCommitId();
    return EntityGitDetails.builder()
        .rootFolder(EntityGitDetailsMapper.nullIfDefault(rootFolder))
        .filePath(EntityGitDetailsMapper.nullIfDefault(filePath))
        .repoIdentifier(EntityGitDetailsMapper.nullIfDefault(repoIdentifier))
        .repoName(EntityGitDetailsMapper.nullIfDefault(repoName))
        .branch(EntityGitDetailsMapper.nullIfDefault(branch))
        .objectId(EntityGitDetailsMapper.nullIfDefault(objectId))
        .commitId(EntityGitDetailsMapper.nullIfDefault(commitId))
        .build();
  }
}
