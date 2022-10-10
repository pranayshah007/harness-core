/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution.api;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.dto.OrchestrationGraphDTO;
import io.harness.execution.StagesExecutionMetadata;
import io.harness.gitsync.beans.StoreType;
import io.harness.gitsync.sdk.EntityGitDetails;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.plan.TriggerType;
import io.harness.pms.execution.ExecutionStatus;
import io.harness.pms.ngpipeline.inputset.beans.resource.InputSetTemplateResponseDTOPMS;
import io.harness.pms.pipeline.api.PipelinesApiUtils;
import io.harness.pms.plan.execution.PlanExecutionResponseDto;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.pms.plan.execution.beans.dto.GraphLayoutNodeDTO;
import io.harness.pms.plan.execution.beans.dto.InterruptDTO;
import io.harness.pms.stages.BasicStageInfo;
import io.harness.pms.stages.StageExecutionSelectorHelper;
import io.harness.spec.server.pipeline.model.ExecutionsDetailsSummary;
import io.harness.spec.server.pipeline.model.ExecutionsDetailsSummary.TriggerTypeEnum;
import io.harness.spec.server.pipeline.model.Graph;
import io.harness.spec.server.pipeline.model.InterruptResponseBody;
import io.harness.spec.server.pipeline.model.InterruptResponseBody.InterruptTypeEnum;
import io.harness.spec.server.pipeline.model.PipelineExecuteResponseBody;
import io.harness.spec.server.pipeline.model.RuntimeYAMLTemplate;
import io.harness.spec.server.pipeline.model.StageInfo;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@OwnedBy(HarnessTeam.PIPELINE)
public class ExecutionsApiUtils {
  public static PipelineExecuteResponseBody getExecuteResponseBody(PlanExecutionResponseDto oldExecutionResponseDto) {
    if (oldExecutionResponseDto == null) {
      return null;
    }
    PipelineExecuteResponseBody responseBody = new PipelineExecuteResponseBody();
    responseBody.setSlug(oldExecutionResponseDto.getPlanExecution().getPlanId());
    responseBody.setStarted(oldExecutionResponseDto.getPlanExecution().getStartTs());
    responseBody.setGitDetails(PipelinesApiUtils.getGitDetails(oldExecutionResponseDto.getGitDetails()));
    responseBody.setStatus(
        ExecutionsApiUtils.getExecuteResponseBodyStatus(oldExecutionResponseDto.getPlanExecution().getStatus()));
    return responseBody;
  }

  public static PipelineExecuteResponseBody.StatusEnum getExecuteResponseBodyStatus(Status statusOld) {
    if (statusOld == null) {
      return null;
    }
    return PipelineExecuteResponseBody.StatusEnum.fromValue(statusOld.name());
  }

  public static ExecutionsDetailsSummary getExecutionDetailsSummary(
      PipelineExecutionSummaryEntity executionSummaryEntity, EntityGitDetails entityGitDetails) {
    ExecutionsDetailsSummary summary = new ExecutionsDetailsSummary();
    summary.setGitDetails(PipelinesApiUtils.getGitDetails(entityGitDetails));
    summary.setSlug(executionSummaryEntity.getPlanExecutionId());
    summary.setPipeline(executionSummaryEntity.getPipelineIdentifier());
    summary.setStatus(getExecutionDetailsStatus(executionSummaryEntity.getStatus()));
    summary.setStarted(executionSummaryEntity.getStartTs());
    summary.setEnded(executionSummaryEntity.getEndTs());
    summary.setTriggerType(getTrigger(executionSummaryEntity.getExecutionTriggerInfo().getTriggerType()));
    summary.setRunNumber(executionSummaryEntity.getRunSequence());
    summary.setFailureMessage(executionSummaryEntity.getFailureInfo().getMessage());
    summary.setStageInfo(getStageInfo(executionSummaryEntity));
    // code for Module Info population
    summary.setConnectorRef(executionSummaryEntity.getConnectorRef());
    summary.setStoreType(getStoreTypeEnum(executionSummaryEntity.getStoreType()));
    return summary;
  }

  public static ExecutionsDetailsSummary.StatusEnum getExecutionDetailsStatus(ExecutionStatus statusOld) {
    if (statusOld == null) {
      return null;
    }
    return ExecutionsDetailsSummary.StatusEnum.fromValue(statusOld.name());
  }

  public static TriggerTypeEnum getTrigger(TriggerType triggerType) {
    if (triggerType == null) {
      return null;
    }
    return TriggerTypeEnum.fromValue(triggerType.name());
  }

  public static StageInfo getStageInfo(PipelineExecutionSummaryEntity executionSummaryEntity) {
    Map<String, GraphLayoutNodeDTO> layoutNodeDTOMap = executionSummaryEntity.getLayoutNodeMap();
    String startingNodeId = executionSummaryEntity.getStartingNodeId();
    StagesExecutionMetadata stagesExecutionMetadata = executionSummaryEntity.getStagesExecutionMetadata();
    List<String> stageIdentifiers =
        stagesExecutionMetadata == null ? null : stagesExecutionMetadata.getStageIdentifiers();
    Map<String, String> stagesExecutedNames = null;
    if (EmptyPredicate.isNotEmpty(stageIdentifiers)) {
      stagesExecutedNames = getStageNames(stageIdentifiers, stagesExecutionMetadata.getFullPipelineYaml());
    }
    StageInfo stageInfo = new StageInfo();
    stageInfo.setSuccessful((long) getStagesCount(layoutNodeDTOMap, startingNodeId, ExecutionStatus.SUCCESS));
    stageInfo.setFailed((long) getStagesCount(layoutNodeDTOMap, startingNodeId, ExecutionStatus.FAILED));
    stageInfo.setTotal((long) getStagesCount(layoutNodeDTOMap, startingNodeId));
    stageInfo.setExecuted(stagesExecutedNames);
    return stageInfo;
  }

  private static Map<String, String> getStageNames(List<String> stageIdentifiers, String pipelineYaml) {
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

  public static int getStagesCount(
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

  public static int getStagesCount(Map<String, GraphLayoutNodeDTO> layoutNodeDTOMap, String startingNodeId) {
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

  public static ExecutionsDetailsSummary.StoreTypeEnum getStoreTypeEnum(StoreType storeType) {
    if (storeType == null) {
      return null;
    }
    return ExecutionsDetailsSummary.StoreTypeEnum.fromValue(storeType.name());
  }

  public static Graph getGraph(OrchestrationGraphDTO orchestrationGraph, boolean fullGraph) {
    return null;
  }

  public static InterruptResponseBody getInterruptResponse(InterruptDTO interruptDTO) {
    InterruptResponseBody interruptResponseBody = new InterruptResponseBody();
    interruptResponseBody.setExecution(interruptDTO.getPlanExecutionId());
    interruptResponseBody.setInterrupt(interruptDTO.getId());
    interruptResponseBody.setInterruptType(InterruptTypeEnum.fromValue(interruptDTO.getType().getDisplayName()));
    return interruptResponseBody;
  }

  public static RuntimeYAMLTemplate getRuntimeYAMLTemplate(InputSetTemplateResponseDTOPMS templateDTO) {
    RuntimeYAMLTemplate yamlTemplate = new RuntimeYAMLTemplate();
    yamlTemplate.setRuntimeYaml(templateDTO.getInputSetTemplateYaml());
    yamlTemplate.setReplaceExpression(templateDTO.getReplacedExpressions());
    return yamlTemplate;
  }
}
