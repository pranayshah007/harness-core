/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.event;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.OrchestrationGraph;
import io.harness.graph.stepDetail.service.PmsGraphStepDetailsService;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.data.stepdetails.PmsStepDetails;
import io.harness.pms.data.stepparameters.PmsStepParameters;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.pms.serializer.recaster.RecastOrchestrationUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.query.Update;

@Singleton
@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public class StepDetailsUpdateEventHandler {
  @Inject PmsGraphStepDetailsService pmsGraphStepDetailsService;

  public OrchestrationGraph handleEvent(String planExecutionId, String nodeExecutionId,
      OrchestrationGraph orchestrationGraph, Update summaryEntityUpdate) {
    try {
      if (orchestrationGraph.getAdjacencyList().getGraphVertexMap().get(nodeExecutionId) != null) {
        Map<String, PmsStepDetails> stepDetails =
            pmsGraphStepDetailsService.getStepDetails(planExecutionId, nodeExecutionId);
        orchestrationGraph.getAdjacencyList().getGraphVertexMap().get(nodeExecutionId).setStepDetails(stepDetails);
        Level currentLevel = AmbianceUtils.obtainCurrentLevel(
            orchestrationGraph.getAdjacencyList().getGraphVertexMap().get(nodeExecutionId).getAmbiance());
        if (Objects.equals(currentLevel.getStepType().getStepCategory(), StepCategory.STAGE)
            || Objects.equals(currentLevel.getStepType().getStepCategory(), StepCategory.STRATEGY)) {
          String stageUuid = currentLevel.getSetupId();
          summaryEntityUpdate.set(
              PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys.layoutNodeMap + "." + stageUuid + ".stepDetails",
              stepDetails);
        }
      }
    } catch (Exception e) {
      log.error(String.format(
                    "[GRAPH_ERROR] Graph update for Step Details update event failed for node [%s]", nodeExecutionId),
          e);
      throw e;
    }
    return orchestrationGraph;
  }

  public OrchestrationGraph handleStepInputEvent(
      String planExecutionId, String nodeExecutionId, OrchestrationGraph orchestrationGraph) {
    try {
      if (orchestrationGraph.getAdjacencyList().getGraphVertexMap().containsKey(nodeExecutionId)) {
        PmsStepParameters stepDetails = PmsStepParameters.parse(RecastOrchestrationUtils.pruneRecasterAdditions(
            pmsGraphStepDetailsService.getStepInputs(planExecutionId, nodeExecutionId)));
        orchestrationGraph.getAdjacencyList().getGraphVertexMap().get(nodeExecutionId).setStepParameters(stepDetails);
      } else {
        log.error("[GRAPH_ERROR]: Given nodeExecution Id was not found before running Step inputs update event");
      }
    } catch (Exception e) {
      log.error(
          String.format("[GRAPH_ERROR] Graph update for Step Input event failed for node [%s]", nodeExecutionId), e);
      throw e;
    }
    return orchestrationGraph;
  }
}
