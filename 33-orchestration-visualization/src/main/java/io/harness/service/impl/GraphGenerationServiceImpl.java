package io.harness.service.impl;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;

import io.harness.annotations.Redesign;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Graph;
import io.harness.beans.OrchestrationAdjacencyListInternal;
import io.harness.cache.SpringMongoStore;
import io.harness.data.structure.EmptyPredicate;
import io.harness.dto.OrchestrationGraph;
import io.harness.dto.OrchestrationGraph.OrchestrationGraphBuilder;
import io.harness.dto.converter.OrchestrationAdjacencyListConverter;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.engine.executions.plan.PlanExecutionService;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.NodeExecution;
import io.harness.execution.PlanExecution;
import io.harness.generator.GraphGenerator;
import io.harness.service.GraphGenerationService;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

@Redesign
@OwnedBy(HarnessTeam.CDC)
@Singleton
public class GraphGenerationServiceImpl implements GraphGenerationService {
  @Inject private PlanExecutionService planExecutionService;
  @Inject private NodeExecutionService nodeExecutionService;
  @Inject private SpringMongoStore mongoStore;
  @Inject private GraphGenerator graphGenerator;
  @Inject @Named("EngineExecutorService") private ExecutorService executorService;

  @Override
  public Graph generateGraph(String planExecutionId) {
    PlanExecution planExecution = planExecutionService.get(planExecutionId);
    List<NodeExecution> nodeExecutions = nodeExecutionService.fetchNodeExecutionsWithoutOldRetries(planExecutionId);
    if (EmptyPredicate.isEmpty(nodeExecutions)) {
      throw new InvalidRequestException("No nodes found for planExecutionId [" + planExecutionId + "]");
    }

    long lastUpdated = nodeExecutions.stream().map(NodeExecution::getLastUpdatedAt).max(Long::compare).orElse(0L);

    Graph cachedGraph = mongoStore.get(Graph.ALGORITHM_ID, Graph.STRUCTURE_HASH, planExecutionId, null);
    if (cachedGraph != null && cachedGraph.getCacheContextOrder() >= lastUpdated) {
      return cachedGraph;
    }

    Graph graphToBeCached = Graph.builder()
                                .cacheKey(planExecutionId)
                                .cacheContextOrder(lastUpdated)
                                .cacheParams(null)
                                .planExecutionId(planExecution.getUuid())
                                .startTs(planExecution.getStartTs())
                                .endTs(planExecution.getEndTs())
                                .status(planExecution.getStatus())
                                .graphVertex(graphGenerator.generateGraphVertexStartingFrom(
                                    obtainStartingNodeExId(nodeExecutions), nodeExecutions))
                                .build();

    executorService.submit(() -> mongoStore.upsert(graphToBeCached, Duration.ofDays(10)));
    return graphToBeCached;
  }

  @Override
  public OrchestrationAdjacencyListInternal getCachedOrchestrationAdjacencyListInternal(String planExecutionId) {
    return mongoStore.get(OrchestrationAdjacencyListInternal.ALGORITHM_ID,
        OrchestrationAdjacencyListInternal.STRUCTURE_HASH, planExecutionId, null);
  }

  @Override
  public void cacheOrchestrationAdjacencyListInternal(OrchestrationAdjacencyListInternal adjacencyListInternal) {
    executorService.submit(() -> mongoStore.upsert(adjacencyListInternal, Duration.ofDays(10)));
  }

  @Override
  public OrchestrationGraph generateOrchestrationGraph(String planExecutionId) {
    PlanExecution planExecution = planExecutionService.get(planExecutionId);
    List<NodeExecution> nodeExecutions = nodeExecutionService.fetchNodeExecutionsWithoutOldRetries(planExecutionId);
    if (isEmpty(nodeExecutions)) {
      throw new InvalidRequestException("No nodes found for planExecutionId [" + planExecutionId + "]");
    }

    String rootNodeId = obtainStartingNodeExId(nodeExecutions);

    OrchestrationGraphBuilder graphBuilder = OrchestrationGraph.builder()
                                                 .planExecutionId(planExecution.getUuid())
                                                 .startTs(planExecution.getStartTs())
                                                 .endTs(planExecution.getEndTs())
                                                 .status(planExecution.getStatus())
                                                 .rootNodeId(rootNodeId);

    OrchestrationAdjacencyListInternal cachedAdjacencyList =
        getCachedOrchestrationAdjacencyListInternal(planExecutionId);
    if (cachedAdjacencyList != null) {
      List<NodeExecution> newNodeExecutions =
          nodeExecutions.stream()
              .filter(node -> !cachedAdjacencyList.getGraphVertexMap().containsKey(node.getUuid()))
              .collect(Collectors.toList());
      if (!newNodeExecutions.isEmpty()) {
        graphGenerator.populateAdjacencyList(cachedAdjacencyList, newNodeExecutions);
      }
      cacheOrchestrationAdjacencyListInternal(cachedAdjacencyList);

      graphBuilder.adjacencyList(OrchestrationAdjacencyListConverter.convertFrom(cachedAdjacencyList));
      return graphBuilder.build();
    }

    OrchestrationAdjacencyListInternal orchestrationAdjacencyList =
        graphGenerator.generateAdjacencyList(rootNodeId, nodeExecutions, false);

    cacheOrchestrationAdjacencyListInternal(orchestrationAdjacencyList.withCacheKey(planExecutionId));

    graphBuilder.adjacencyList(OrchestrationAdjacencyListConverter.convertFrom(orchestrationAdjacencyList));
    return graphBuilder.build();
  }

  @Override
  public OrchestrationGraph generatePartialOrchestrationGraph(String startingSetupNodeId, String planExecutionId) {
    PlanExecution planExecution = planExecutionService.get(planExecutionId);
    List<NodeExecution> nodeExecutions = nodeExecutionService.fetchNodeExecutionsWithoutOldRetries(planExecutionId);
    if (isEmpty(nodeExecutions)) {
      throw new InvalidRequestException("No nodes found for planExecutionId [" + planExecutionId + "]");
    }

    String startingNodeId = obtainStartingNodeExId(nodeExecutions, startingSetupNodeId);

    OrchestrationGraphBuilder graphBuilder =
        OrchestrationGraph.builder()
            .planExecutionId(planExecution.getUuid())
            .startTs(planExecution.getStartTs())
            .endTs(planExecution.getEndTs())
            .status(planExecution.getStatus())
            .rootNodeId(startingNodeId)
            .adjacencyList(OrchestrationAdjacencyListConverter.convertFrom(
                graphGenerator.generateAdjacencyList(startingNodeId, nodeExecutions, true)));

    return graphBuilder.build();
  }

  private String obtainStartingNodeExId(List<NodeExecution> nodeExecutions, String startingSetupNodeId) {
    return nodeExecutions.stream()
        .filter(node -> node.getAmbiance().obtainCurrentLevel().getSetupId().equals(startingSetupNodeId))
        .collect(Collectors.collectingAndThen(Collectors.toList(),
            list -> {
              if (list.isEmpty()) {
                throw new InvalidRequestException("No nodes found for setupNodeId [" + startingSetupNodeId + "]");
              }
              if (list.size() > 1) {
                throw new InvalidRequestException("Repeated setupNodeIds are not supported. Check the plan for ["
                    + startingSetupNodeId + "] planNodeId");
              }

              return list.get(0);
            }))
        .getUuid();
  }

  private String obtainStartingNodeExId(List<NodeExecution> nodeExecutions) {
    return nodeExecutions.stream()
        .filter(node -> EmptyPredicate.isEmpty(node.getParentId()) && EmptyPredicate.isEmpty(node.getPreviousId()))
        .findFirst()
        .orElseThrow(() -> new InvalidRequestException("Starting node is not found"))
        .getUuid();
  }
}
