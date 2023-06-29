/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.event;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.pms.contracts.execution.Status.APPROVAL_WAITING;
import static io.harness.pms.contracts.execution.Status.INTERVENTION_WAITING;
import static io.harness.pms.contracts.execution.Status.WAIT_STEP_RUNNING;

import io.harness.DelegateInfoHelper;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.GraphVertex;
import io.harness.beans.GraphVertex.GraphVertexBuilder;
import io.harness.beans.OrchestrationGraph;
import io.harness.data.structure.CollectionUtils;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.engine.pms.data.PmsOutcomeService;
import io.harness.execution.NodeExecution;
import io.harness.generator.OrchestrationAdjacencyListGenerator;
import io.harness.graph.stepDetail.service.PmsGraphStepDetailsService;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.execution.utils.StatusUtils;
import io.harness.pms.sdk.core.resolver.outcome.mapper.PmsOutcomeMapper;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.PIPELINE)
@Singleton
@Slf4j
public class GraphStatusUpdateHelper {
  @Inject private NodeExecutionService nodeExecutionService;
  @Inject private PmsOutcomeService pmsOutcomeService;
  @Inject private OrchestrationAdjacencyListGenerator orchestrationAdjacencyListGenerator;
  @Inject private DelegateInfoHelper delegateInfoHelper;

  @Inject private PmsGraphStepDetailsService pmsGraphStepDetailsService;

  public OrchestrationGraph handleEvent(
      String planExecutionId, String nodeExecutionId, OrchestrationGraph orchestrationGraph) {
    if (isEmpty(nodeExecutionId)) {
      return orchestrationGraph;
    }
    NodeExecution nodeExecution = nodeExecutionService.get(nodeExecutionId);
    return handleEventV2(planExecutionId, nodeExecution, orchestrationGraph);
  }

  public OrchestrationGraph handleEventV2(
      String planExecutionId, NodeExecution nodeExecution, OrchestrationGraph orchestrationGraph) {
    if (nodeExecution == null) {
      return orchestrationGraph;
    }
    String nodeExecutionId = nodeExecution.getUuid();
    try {
      if (orchestrationGraph.getRootNodeIds().isEmpty()) {
        log.info("[PMS_GRAPH]  Setting rootNodeId: [{}] for plan [{}]", nodeExecutionId, planExecutionId);
        orchestrationGraph.getRootNodeIds().add(nodeExecutionId);
      }

      Map<String, GraphVertex> graphVertexMap = orchestrationGraph.getAdjacencyList().getGraphVertexMap();
      if (graphVertexMap.containsKey(nodeExecutionId)) {
        if (nodeExecution.getOldRetry()) {
          log.info("[PMS_GRAPH]  Removing graph vertex with id [{}] and status [{}]. PlanExecutionId: [{}]",
              nodeExecutionId, nodeExecution.getStatus(), planExecutionId);
          orchestrationAdjacencyListGenerator.removeVertex(orchestrationGraph.getAdjacencyList(), nodeExecution);
        } else {
          updateGraphVertex(graphVertexMap, nodeExecution, planExecutionId);
        }
      } else if (!nodeExecution.getOldRetry()) {
        orchestrationAdjacencyListGenerator.addVertex(orchestrationGraph.getAdjacencyList(), nodeExecution);
      }
    } catch (Exception e) {
      log.error(
          String.format("[GRAPH_ERROR] event failed for [%s] for plan [%s]", nodeExecutionId, planExecutionId), e);
      throw e;
    }
    return orchestrationGraph;
  }

  private void updateGraphVertex(
      Map<String, GraphVertex> graphVertexMap, NodeExecution nodeExecution, String planExecutionId) {
    String nodeExecutionId = nodeExecution.getUuid();
    graphVertexMap.computeIfPresent(nodeExecutionId, (key, prevValue) -> {
      GraphVertex newValue = convertFromNodeExecution(prevValue, nodeExecution);
      if (isOutcomeUpdateGraphStatus(newValue.getStatus())) {
        newValue.setOutcomeDocuments(PmsOutcomeMapper.convertJsonToOrchestrationMap(
            pmsOutcomeService.findAllOutcomesMapByRuntimeId(planExecutionId, nodeExecutionId)));
        newValue.setGraphDelegateSelectionLogParams(
            delegateInfoHelper.getDelegateInformationForGivenTask(nodeExecution.getExecutableResponses(),
                nodeExecution.getMode(), AmbianceUtils.getAccountId(nodeExecution.getAmbiance())));
      }
      return newValue;
    });
  }

  // Todo: Update only properties that will be changed. No need to construct full
  public GraphVertex convertFromNodeExecution(GraphVertex prevValue, NodeExecution nodeExecution) {
    Level level = Objects.requireNonNull(AmbianceUtils.obtainCurrentLevel(nodeExecution.getAmbiance()));
    GraphVertexBuilder prevValueBuilder =
        prevValue.toBuilder()
            .uuid(nodeExecution.getUuid())
            .ambiance(nodeExecution.getAmbiance())
            .planNodeId(level.getSetupId())
            .identifier(level.getIdentifier())
            .name(nodeExecution.getName())
            .startTs(nodeExecution.getStartTs())
            .endTs(nodeExecution.getEndTs())
            .initialWaitDuration(nodeExecution.getInitialWaitDuration())
            .lastUpdatedAt(nodeExecution.getLastUpdatedAt())
            .stepType(level.getStepType().getType())
            .status(nodeExecution.getStatus())
            .failureInfo(nodeExecution.getFailureInfo())
            .nodeRunInfo(nodeExecution.getNodeRunInfo())
            .mode(nodeExecution.getMode())
            .executableResponses(CollectionUtils.emptyIfNull(nodeExecution.getExecutableResponses()))
            .interruptHistories(nodeExecution.getInterruptHistories())
            .retryIds(nodeExecution.getRetryIds())
            .skipType(nodeExecution.getSkipGraphType())
            .unitProgresses(nodeExecution.getUnitProgresses())
            .progressData(nodeExecution.getPmsProgressData());
    if (prevValue.getStepParameters() == null) {
      prevValueBuilder.stepParameters(nodeExecution.getResolvedParams());
    }
    return prevValueBuilder.build();
  }

  @VisibleForTesting
  boolean isOutcomeUpdateGraphStatus(Status status) {
    return StatusUtils.isFinalStatus(status) || status.equals(INTERVENTION_WAITING) || status.equals(APPROVAL_WAITING)
        || status.equals(WAIT_STEP_RUNNING);
  }
}
