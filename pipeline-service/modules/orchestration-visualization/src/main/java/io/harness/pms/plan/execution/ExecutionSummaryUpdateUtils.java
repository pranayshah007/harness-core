/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.plan.execution;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ExecutionErrorInfo;
import io.harness.dto.converter.FailureInfoDTOConverter;
import io.harness.engine.utils.OrchestrationUtils;
import io.harness.execution.NodeExecution;
import io.harness.plan.NodeType;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.execution.ExecutionStatus;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys;
import io.harness.steps.StepSpecTypeConstants;

import java.util.Objects;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import org.springframework.data.mongodb.core.query.Update;

/**
 * A utility to generate updates for the layout graph used in the list api for stage layout
 */
@OwnedBy(HarnessTeam.PIPELINE)
@UtilityClass
public class ExecutionSummaryUpdateUtils {
  public static boolean addStageUpdateCriteria(Update update, NodeExecution nodeExecution) {
    boolean updateApplied = false;
    Level level = Objects.requireNonNull(AmbianceUtils.obtainCurrentLevel(nodeExecution.getAmbiance()));
    ExecutionStatus status = ExecutionStatus.getExecutionStatus(nodeExecution.getStatus());
    if (Objects.equals(level.getStepType().getType(), StepSpecTypeConstants.BARRIER)) {
      // Todo: Check here if the nodeExecution is under strategy then use executionId instead.
      Optional<Level> stage = AmbianceUtils.getStageLevelFromAmbiance(nodeExecution.getAmbiance());
      // Updating the barrier information in the stage node.
      if (stage.isPresent()) {
        Level stageNode = stage.get();
        update.set(PlanExecutionSummaryKeys.layoutNodeMap + "." + stageNode.getSetupId() + ".barrierFound", true);
        updateApplied = true;
      }
    }
    // Making update in graph only if the strategy is at stage level.
    if (nodeExecution.getStepType().getStepCategory() == StepCategory.STRATEGY
        && AmbianceUtils.isCurrentStrategyLevelAtStage(nodeExecution.getAmbiance())) {
      update.set(PlanExecutionSummaryKeys.layoutNodeMap + "." + nodeExecution.getNodeId() + ".status", status);
      update.set(
          PlanExecutionSummaryKeys.layoutNodeMap + "." + nodeExecution.getNodeId() + ".moduleInfo.stepParameters",
          nodeExecution.getResolvedStepParameters());
      updateApplied = true;
    }
    if (!OrchestrationUtils.isStageNode(nodeExecution)) {
      return updateApplied;
    }
    // If the nodes is of type Identity, there is no need to update the status. We want to update the status only when
    // there is a PlanNode
    String stageUuid = nodeExecution.getNodeId();
    if (AmbianceUtils.getStrategyLevelFromAmbiance(nodeExecution.getAmbiance()).isPresent()) {
      // If nodeExecution is under strategy then we use nodeExecution.getUuid rather than the planNodeId
      stageUuid = nodeExecution.getUuid();
      update.set(
          PlanExecutionSummaryKeys.layoutNodeMap + "." + stageUuid + ".nodeIdentifier", nodeExecution.getIdentifier());
      update.set(PlanExecutionSummaryKeys.layoutNodeMap + "." + stageUuid + ".name", nodeExecution.getName());
      update.set(PlanExecutionSummaryKeys.layoutNodeMap + "." + stageUuid + ".strategyMetadata",
          AmbianceUtils.obtainCurrentLevel(nodeExecution.getAmbiance()).getStrategyMetadata());
    }
    if (!level.getNodeType().equals(NodeType.IDENTITY_PLAN_NODE.toString())) {
      update.set(PlanExecutionSummaryKeys.layoutNodeMap + "." + stageUuid + ".status", status);
    }
    update.set(PlanExecutionSummaryKeys.layoutNodeMap + "." + stageUuid + ".startTs", nodeExecution.getStartTs());
    update.set(PlanExecutionSummaryKeys.layoutNodeMap + "." + stageUuid + ".nodeExecutionId", nodeExecution.getUuid());
    update.set(
        PlanExecutionSummaryKeys.layoutNodeMap + "." + stageUuid + ".nodeRunInfo", nodeExecution.getNodeRunInfo());
    if (nodeExecution.getEndTs() != null) {
      update.set(PlanExecutionSummaryKeys.layoutNodeMap + "." + stageUuid + ".endTs", nodeExecution.getEndTs());
    }
    if (nodeExecution.getFailureInfo() != null) {
      update.set(PlanExecutionSummaryKeys.layoutNodeMap + "." + stageUuid + ".failureInfo",
          ExecutionErrorInfo.builder().message(nodeExecution.getFailureInfo().getErrorMessage()).build());
      update.set(PlanExecutionSummaryKeys.layoutNodeMap + "." + stageUuid + ".failureInfoDTO",
          FailureInfoDTOConverter.toFailureInfoDTO(nodeExecution.getFailureInfo()));
    }
    if (nodeExecution.getSkipInfo() != null) {
      update.set(PlanExecutionSummaryKeys.layoutNodeMap + "." + stageUuid + ".skipInfo", nodeExecution.getSkipInfo());
    }
    update.set(PlanExecutionSummaryKeys.layoutNodeMap + "." + stageUuid + ".executionInputConfigured",
        nodeExecution.getExecutionInputConfigured());
    return true;
  }

  public boolean addPipelineUpdateCriteria(Update update, NodeExecution nodeExecution) {
    if (OrchestrationUtils.isPipelineNode(nodeExecution)) {
      ExecutionStatus status = ExecutionStatus.getExecutionStatus(nodeExecution.getStatus());
      update.set(PlanExecutionSummaryKeys.internalStatus, nodeExecution.getStatus());
      update.set(PlanExecutionSummaryKeys.status, status);
      if (nodeExecution.getEndTs() != null) {
        update.set(PlanExecutionSummaryKeys.endTs, nodeExecution.getEndTs());
      }
      if (status == ExecutionStatus.FAILED) {
        update.set(PlanExecutionSummaryKeys.executionErrorInfo,
            ExecutionErrorInfo.builder().message(nodeExecution.getFailureInfo().getErrorMessage()).build());
        update.set(PlanExecutionSummaryKeys.failureInfo,
            FailureInfoDTOConverter.toFailureInfoDTO(nodeExecution.getFailureInfo()));
      }
      return true;
    }

    return false;
  }
}
