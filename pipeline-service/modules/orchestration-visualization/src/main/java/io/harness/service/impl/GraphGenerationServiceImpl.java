/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.service.impl;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EphemeralOrchestrationGraph;
import io.harness.beans.GraphVertex;
import io.harness.beans.OrchestrationEventLog;
import io.harness.beans.OrchestrationGraph;
import io.harness.beans.converter.EphemeralOrchestrationGraphConverter;
import io.harness.beans.internal.OrchestrationAdjacencyListInternal;
import io.harness.cache.SpringCacheEntity;
import io.harness.cache.SpringMongoStore;
import io.harness.data.structure.EmptyPredicate;
import io.harness.dto.OrchestrationGraphDTO;
import io.harness.dto.converter.OrchestrationGraphDTOConverter;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.engine.executions.plan.PlanExecutionService;
import io.harness.engine.utils.OrchestrationUtils;
import io.harness.event.GraphStatusUpdateHelper;
import io.harness.event.OrchestrationLogPublisher;
import io.harness.event.PlanExecutionStatusUpdateEventHandler;
import io.harness.event.StepDetailsUpdateEventHandler;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.NestedExceptionUtils;
import io.harness.execution.NodeExecution;
import io.harness.execution.PlanExecution;
import io.harness.generator.OrchestrationAdjacencyListGenerator;
import io.harness.lock.AcquiredLock;
import io.harness.lock.PersistentLocker;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.events.OrchestrationEventType;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.execution.utils.StatusUtils;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.pms.plan.execution.service.PmsExecutionSummaryService;
import io.harness.repositories.orchestrationEventLog.OrchestrationEventLogRepository;
import io.harness.service.GraphGenerationService;
import io.harness.skip.service.VertexSkipperService;
import io.harness.utils.PmsFeatureFlagService;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.util.CloseableIterator;

@OwnedBy(HarnessTeam.PIPELINE)
@Singleton
@Slf4j
public class GraphGenerationServiceImpl implements GraphGenerationService {
  public static final int THRESHOLD_LOG = 1000;

  private static final String GRAPH_LOCK = "GRAPH_LOCK_";

  @Inject private PlanExecutionService planExecutionService;
  @Inject private NodeExecutionService nodeExecutionService;
  @Inject private SpringMongoStore mongoStore;
  @Inject private OrchestrationAdjacencyListGenerator orchestrationAdjacencyListGenerator;
  @Inject private VertexSkipperService vertexSkipperService;
  @Inject private OrchestrationEventLogRepository orchestrationEventLogRepository;
  @Inject private GraphStatusUpdateHelper graphStatusUpdateHelper;
  @Inject private PlanExecutionStatusUpdateEventHandler planExecutionStatusUpdateEventHandler;
  @Inject private StepDetailsUpdateEventHandler stepDetailsUpdateEventHandler;
  @Inject private PmsExecutionSummaryService pmsExecutionSummaryService;
  @Inject private PersistentLocker persistentLocker;
  @Inject private OrchestrationLogPublisher orchestrationLogPublisher;
  @Inject private PmsFeatureFlagService pmsFeatureFlagService;

  @Override
  public boolean updateGraph(String planExecutionId) {
    String lockName = GRAPH_LOCK + planExecutionId;
    try (AcquiredLock<?> lock = persistentLocker.tryToAcquireLock(lockName, Duration.ofSeconds(10))) {
      if (lock == null) {
        log.debug(String.format(
            "[PMS_GRAPH_LOCK_TEST] Not able to take lock on graph generation for lockName - %s, returning early.",
            lockName));
        return false;
      }

      return updateGraphUnderLock(planExecutionId);
    } catch (Exception exception) {
      log.error(String.format(
                    "[GRAPH_ERROR] Exception Occurred while updating graph for planExecutionId: %s", planExecutionId),
          exception);
      return false;
    }
  }

  @Override
  public boolean updateGraphWithWaitLock(String planExecutionId) {
    String lockName = GRAPH_LOCK + planExecutionId;
    try (AcquiredLock<?> lock =
             persistentLocker.waitToAcquireLockOptional(lockName, Duration.ofSeconds(10), Duration.ofSeconds(30))) {
      if (lock == null) {
        log.debug(String.format(
            "[PMS_GRAPH_LOCK_TEST] Not able to take lock on graph generation for lockName - %s, returning early.",
            lockName));
        return false;
      }

      return updateGraphUnderLock(planExecutionId);
    } catch (Exception exception) {
      log.error(String.format(
                    "[GRAPH_ERROR] Exception Occurred while updating graph for planExecutionId: %s", planExecutionId),
          exception);
      return false;
    }
  }

  // This must always be called after acquiring the lock
  @VisibleForTesting
  boolean updateGraphUnderLock(String planExecutionId) {
    OrchestrationGraph orchestrationGraph = getCachedOrchestrationGraph(planExecutionId);
    if (orchestrationGraph == null) {
      log.warn("[PMS_GRAPH] Graph not yet generated. Passing on to next iteration");
      return true;
    }
    return updateGraphUnderLock(orchestrationGraph);
  }

  // This must always be called after acquiring the lock
  @VisibleForTesting
  boolean updateGraphUnderLock(OrchestrationGraph orchestrationGraph) {
    if (orchestrationGraph == null) {
      return false;
    }
    boolean shouldAck = true;
    String planExecutionId = orchestrationGraph.getPlanExecutionId();
    long startTs = System.currentTimeMillis();
    long lastUpdatedAt = orchestrationGraph.getLastUpdatedAt();

    List<OrchestrationEventLog> unprocessedEventLogs =
        orchestrationEventLogRepository.findUnprocessedEvents(planExecutionId, lastUpdatedAt, THRESHOLD_LOG);
    if (unprocessedEventLogs.isEmpty()) {
      return true;
    }

    if (unprocessedEventLogs.size() == THRESHOLD_LOG) {
      log.warn("[PMS_GRAPH] Found [{}] unprocessed event logs", unprocessedEventLogs.size());
      // Re-emit if there are too many logs
      shouldAck = false;
    }
    boolean updateRequired = false;
    Update executionSummaryUpdate = new Update();
    Set<String> nodeExecutionIds = new HashSet<>();
    for (OrchestrationEventLog orchestrationEventLog : unprocessedEventLogs) {
      String nodeExecutionId = orchestrationEventLog.getNodeExecutionId();
      OrchestrationEventType orchestrationEventType = orchestrationEventLog.getOrchestrationEventType();
      // lastUpdatedAt is updated initially as this was not getting updating in case we have multiple event log of same
      // nodeExecution Id. If you check the default case in the below switch case, we continue if a particular node
      // execution is already processed without updating the lastUpdatedAt. For more details, you can refer CDS-75792
      lastUpdatedAt = orchestrationEventLog.getCreatedAt();
      switch (orchestrationEventType) {
        case PLAN_EXECUTION_STATUS_UPDATE:
          orchestrationGraph = planExecutionStatusUpdateEventHandler.handleEvent(planExecutionId, orchestrationGraph);
          break;
        case STEP_DETAILS_UPDATE:
          orchestrationGraph = stepDetailsUpdateEventHandler.handleEvent(
              planExecutionId, nodeExecutionId, orchestrationGraph, executionSummaryUpdate);
          updateRequired = true;
          break;
        case STEP_INPUTS_UPDATE:
          orchestrationGraph =
              stepDetailsUpdateEventHandler.handleStepInputEvent(planExecutionId, nodeExecutionId, orchestrationGraph);
          updateRequired = true;
          break;
        default:
          if (nodeExecutionIds.contains(nodeExecutionId)) {
            continue;
          }
          nodeExecutionIds.add(nodeExecutionId);
          NodeExecution nodeExecution = nodeExecutionService.get(nodeExecutionId);
          if (nodeExecution.getStepType().getStepCategory() == StepCategory.STRATEGY) {
            log.info("Status" + nodeExecution.getStatus());
          }

          updateRequired = pmsExecutionSummaryService.handleNodeExecutionUpdateFromGraphUpdate(
                               planExecutionId, nodeExecution, executionSummaryUpdate)
              || updateRequired;
          orchestrationGraph =
              graphStatusUpdateHelper.handleEventV2(planExecutionId, nodeExecution, orchestrationGraph);
      }
    }

    cachePartialOrchestrationGraph(orchestrationGraph.withLastUpdatedAt(lastUpdatedAt), lastUpdatedAt);
    if (updateRequired) {
      pmsExecutionSummaryService.update(planExecutionId, executionSummaryUpdate);
    }
    log.info("[PMS_GRAPH] Processing of [{}] orchestration event logs completed in [{}ms]", unprocessedEventLogs.size(),
        System.currentTimeMillis() - startTs);
    return shouldAck;
  }

  @Override
  public OrchestrationGraph getCachedOrchestrationGraph(String planExecutionId) {
    return mongoStore.get(OrchestrationGraph.ALGORITHM_ID, OrchestrationGraph.STRUCTURE_HASH, planExecutionId, null);
  }

  @Override
  public OrchestrationGraph getCachedOrchestrationGraphFromSecondary(String planExecutionId) {
    return mongoStore.getFromSecondary(
        OrchestrationGraph.ALGORITHM_ID, OrchestrationGraph.STRUCTURE_HASH, planExecutionId, null);
  }

  @Override
  public void cacheOrchestrationGraph(OrchestrationGraph orchestrationGraph) {
    mongoStore.upsert(orchestrationGraph, SpringCacheEntity.TTL);
  }

  private void cachePartialOrchestrationGraph(OrchestrationGraph orchestrationGraph, long entityUpdatedAt) {
    mongoStore.upsert(orchestrationGraph, SpringCacheEntity.TTL, entityUpdatedAt);
  }

  @Override
  public OrchestrationGraphDTO generateOrchestrationGraphV2(String planExecutionId) {
    OrchestrationGraph cachedOrchestrationGraph = getCachedOrchestrationGraphFromSecondary(planExecutionId);
    if (cachedOrchestrationGraph == null) {
      cachedOrchestrationGraph = buildOrchestrationGraph(planExecutionId);
    } else {
      sendUpdateEventIfAny(cachedOrchestrationGraph);
    }
    EphemeralOrchestrationGraph ephemeralOrchestrationGraph =
        EphemeralOrchestrationGraphConverter.convertFrom(cachedOrchestrationGraph);
    vertexSkipperService.removeSkippedVertices(ephemeralOrchestrationGraph);
    return OrchestrationGraphDTOConverter.convertFrom(ephemeralOrchestrationGraph);
  }

  @Override
  public OrchestrationGraphDTO generatePartialOrchestrationGraphFromSetupNodeIdAndExecutionId(
      String startingSetupNodeId, String planExecutionId, String startingExecutionId) {
    OrchestrationGraph orchestrationGraph = getCachedOrchestrationGraphFromSecondary(planExecutionId);
    if (orchestrationGraph == null) {
      orchestrationGraph = buildOrchestrationGraph(planExecutionId);
    } else {
      sendUpdateEventIfAny(orchestrationGraph);
    }
    String startingNodeId = obtainStartingIdFromSetupNodeIdAndExecutionId(
        orchestrationGraph.getAdjacencyList().getGraphVertexMap(), startingSetupNodeId, startingExecutionId);
    try {
      return generatePartialGraph(startingNodeId, orchestrationGraph);
    } catch (Exception ex) {
      orchestrationGraph = buildOrchestrationGraph(planExecutionId);
      return generatePartialGraph(startingNodeId, orchestrationGraph);
    }
  }

  @Override
  public void sendUpdateEventIfAny(PipelineExecutionSummaryEntity executionSummaryEntity) {
    sendUpdateEventIfAny(executionSummaryEntity.getStatus().getEngineStatus(),
        executionSummaryEntity.getPlanExecutionId(), executionSummaryEntity.getLastUpdatedAt());
  }

  @Override
  public void deleteAllGraphMetadataForGivenExecutionIds(Set<String> planExecutionIds) {
    // Delete all related orchestration logs
    orchestrationEventLogRepository.deleteAllOrchestrationLogEvents(planExecutionIds);
    // Delete related cache entities
    List<OrchestrationGraph> cacheEntities = new LinkedList<>();
    for (String planExecutionId : planExecutionIds) {
      OrchestrationGraph graph = OrchestrationGraph.builder().cacheKey(planExecutionId).cacheParams(null).build();
      cacheEntities.add(graph);
    }
    mongoStore.delete(cacheEntities);
  }

  private void sendUpdateEventIfAny(OrchestrationGraph orchestrationGraph) {
    sendUpdateEventIfAny(
        orchestrationGraph.getStatus(), orchestrationGraph.getPlanExecutionId(), orchestrationGraph.getLastUpdatedAt());
  }

  private void sendUpdateEventIfAny(Status planExecutionStatus, String planExecutionId, long lastUpdatedAt) {
    if (!StatusUtils.isFinalStatus(planExecutionStatus)
        || orchestrationEventLogRepository.checkIfAnyUnprocessedEvents(planExecutionId, lastUpdatedAt)) {
      orchestrationLogPublisher.sendLogEvent(planExecutionId);
    }
  }

  public OrchestrationGraph buildOrchestrationGraph(String planExecutionId) {
    try {
      log.warn(String.format(
          "[GRAPH_ERROR]: Trying to build orchestration graph from scratch for planExecutionId [%s]", planExecutionId));
      PlanExecution planExecution = planExecutionService.getPlanExecutionMetadata(planExecutionId);
      if (planExecution == null) {
        throw NestedExceptionUtils.hintWithExplanationException("Pipeline Execution with given plan execution id: ["
                + planExecutionId + "] not found or unable to generate a graph for it",
            "Try to open an execution which is not 6 months old. If issue persists, please contact harness support",
            new InvalidRequestException("Graph could not be generated for planExecutionId [" + planExecutionId + "]."));
      }
      List<NodeExecution> nodeExecutions = new LinkedList<>();
      try (CloseableIterator<NodeExecution> iterator =
               nodeExecutionService.fetchNodeExecutionsWithoutOldRetriesIterator(planExecutionId)) {
        while (iterator.hasNext()) {
          nodeExecutions.add(iterator.next());
        }
      }
      log.warn(String.format(
          "[GRAPH_ERROR]: Trying to build orchestration graph from scratch for planExecutionId [%s] with nodeExecutionsCount [%d]",
          planExecutionId, nodeExecutions.size()));
      if (isEmpty(nodeExecutions)) {
        return OrchestrationGraph.builder()
            .adjacencyList(OrchestrationAdjacencyListInternal.builder()
                               .adjacencyMap(new HashMap<>())
                               .graphVertexMap(new HashMap<>())
                               .build())
            .rootNodeIds(new ArrayList<>())
            .build();
      }

      String rootNodeId = obtainStartingNodeExId(nodeExecutions);

      OrchestrationGraph graph = OrchestrationGraph.builder()
                                     .cacheKey(planExecutionId)
                                     .cacheContextOrder(System.currentTimeMillis())
                                     .cacheParams(null)
                                     .planExecutionId(planExecution.getUuid())
                                     .startTs(planExecution.getStartTs())
                                     .endTs(planExecution.getEndTs())
                                     .status(planExecution.getStatus())
                                     .rootNodeIds(Lists.newArrayList(rootNodeId))
                                     .adjacencyList(orchestrationAdjacencyListGenerator.generateAdjacencyList(
                                         rootNodeId, nodeExecutions, true))
                                     .build();

      List<NodeExecution> stageNodeExecutions =
          nodeExecutions.stream().filter(OrchestrationUtils::isStageOrParallelStageNode).collect(Collectors.toList());
      cacheOrchestrationGraph(graph);
      pmsExecutionSummaryService.regenerateStageLayoutGraph(planExecutionId, stageNodeExecutions);
      return graph;
    } catch (Exception ex) {
      log.error("Exception occurred while generating graph from nodeExecutions", ex);
      throw new InvalidRequestException(
          "Could not fetch graph for the given execution. It might have been deleted or does not exist");
    }
  }

  public OrchestrationGraph buildOrchestrationGraphForNodeExecution(
      String planExecutionId, String nodeExecutionId, List<NodeExecution> nodeExecutions) {
    return OrchestrationGraph.builder()
        .cacheKey(planExecutionId)
        .cacheContextOrder(System.currentTimeMillis())
        .cacheParams(null)
        .planExecutionId(planExecutionId)
        .rootNodeIds(Lists.newArrayList(nodeExecutionId))
        .adjacencyList(orchestrationAdjacencyListGenerator.generateAdjacencyList(nodeExecutionId, nodeExecutions, true))
        .build();
  }

  private OrchestrationGraphDTO generatePartialGraph(String startId, OrchestrationGraph orchestrationGraph) {
    EphemeralOrchestrationGraph ephemeralOrchestrationGraph =
        EphemeralOrchestrationGraph.builder()
            .planExecutionId(orchestrationGraph.getPlanExecutionId())
            .rootNodeIds(Lists.newArrayList(startId))
            .startTs(orchestrationGraph.getStartTs())
            .endTs(orchestrationGraph.getEndTs())
            .status(orchestrationGraph.getStatus())
            .adjacencyList(orchestrationAdjacencyListGenerator.generatePartialAdjacencyList(
                startId, orchestrationGraph.getAdjacencyList()))
            .build();

    vertexSkipperService.removeSkippedVertices(ephemeralOrchestrationGraph);

    return OrchestrationGraphDTOConverter.convertFrom(ephemeralOrchestrationGraph);
  }

  private String obtainStartingIdFromSetupNodeIdAndExecutionId(
      Map<String, GraphVertex> graphVertexMap, String startingSetupNodeId, String startingExecutionId) {
    List<GraphVertex> vertexList = graphVertexMap.values()
                                       .stream()
                                       .filter(vertex -> {
                                         if (startingExecutionId != null) {
                                           return vertex.getPlanNodeId().equals(startingSetupNodeId)
                                               && vertex.getUuid().equals(startingExecutionId);
                                         }
                                         return vertex.getPlanNodeId().equals(startingSetupNodeId);
                                       })
                                       .collect(Collectors.toList());
    if (vertexList.size() == 1) {
      return vertexList.get(0).getUuid();
    }
    if (vertexList.size() > 1) {
      log.error(String.format("Multiple node Ids found for a given combination of setupId: %s and ExecutionId: %s",
          startingSetupNodeId, startingExecutionId));
    }
    return null;
  }

  private String obtainStartingNodeExId(List<NodeExecution> nodeExecutions) {
    return nodeExecutions.stream()
        .filter(node -> EmptyPredicate.isEmpty(node.getParentId()) && EmptyPredicate.isEmpty(node.getPreviousId()))
        .findFirst()
        .orElseThrow(() -> new InvalidRequestException("Starting node is not found"))
        .getUuid();
  }
}
