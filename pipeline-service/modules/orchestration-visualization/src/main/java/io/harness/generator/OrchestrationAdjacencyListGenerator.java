/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.generator;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.execution.ExecutionModeUtils.isChainMode;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.GraphVertex;
import io.harness.beans.converter.GraphVertexConverter;
import io.harness.beans.internal.EdgeListInternal;
import io.harness.beans.internal.OrchestrationAdjacencyListInternal;
import io.harness.beans.stepDetail.NodeExecutionsInfo;
import io.harness.data.structure.EmptyPredicate;
import io.harness.engine.pms.data.PmsOutcomeService;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.NodeExecution;
import io.harness.graph.stepDetail.service.PmsGraphStepDetailsService;
import io.harness.pms.contracts.execution.ExecutionMode;
import io.harness.pms.data.PmsOutcome;
import io.harness.pms.sdk.core.resolver.outcome.mapper.PmsOutcomeMapper;
import io.harness.steps.StepUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@Slf4j
@OwnedBy(HarnessTeam.CDC)
@Singleton
public class OrchestrationAdjacencyListGenerator {
  @Inject private PmsOutcomeService pmsOutcomeService;
  @Inject private GraphVertexConverter graphVertexConverter;
  @Inject private PmsGraphStepDetailsService pmsGraphStepDetailsService;

  public OrchestrationAdjacencyListInternal generateAdjacencyList(
      String startingNodeExId, List<NodeExecution> nodeExecutions, boolean isOutcomePresent) {
    if (isEmpty(startingNodeExId)) {
      log.warn("Starting node cannot be null");
      return null;
    }
    return generateList(startingNodeExId, nodeExecutions, isOutcomePresent);
  }

  public void addVertex(OrchestrationAdjacencyListInternal adjacencyListInternal, NodeExecution nodeExecution) {
    Map<String, GraphVertex> graphVertexMap = adjacencyListInternal.getGraphVertexMap();
    Map<String, EdgeListInternal> adjacencyList = adjacencyListInternal.getAdjacencyMap();

    String currentUuid = nodeExecution.getUuid();
    List<String> logBaseKeys = StepUtils.generateLogKeys(nodeExecution.getAmbiance(), null);

    graphVertexMap.put(currentUuid, graphVertexConverter.convertFrom(nodeExecution, logBaseKeys));

    // compute adjList
    String parentId = null;
    List<String> prevIds = new ArrayList<>();
    if (isIdPresent(nodeExecution.getPreviousId())) {
      adjacencyList.get(nodeExecution.getPreviousId()).getNextIds().add(currentUuid);
      prevIds.add(nodeExecution.getPreviousId());
    } else if (isIdPresent(nodeExecution.getParentId())) {
      parentId = nodeExecution.getParentId();
      EdgeListInternal parentEdgeList = Objects.requireNonNull(adjacencyList.get(parentId),
          String.format("[GRAPH_ERROR] ParentId: [%s] not present in the adjacency list. Please debug", parentId));
      GraphVertex parentGraphVertex = Objects.requireNonNull(graphVertexMap.get(parentId),
          String.format("[GRAPH_ERROR] ParentId: [%s] not present in the graph vertex map. Please debug", parentId));
      if (isChainNonInitialVertex(parentGraphVertex.getMode(), parentEdgeList)) {
        appendToChainEnd(adjacencyList, parentEdgeList.getEdges().get(0), currentUuid, prevIds);
      } else {
        parentEdgeList.getEdges().add(currentUuid);
      }
    }
    adjacencyList.put(currentUuid,
        EdgeListInternal.builder()
            .edges(new ArrayList<>())
            .nextIds(new ArrayList<>())
            .prevIds(prevIds)
            .parentId(parentId)
            .build());
  }

  public void removeVertex(OrchestrationAdjacencyListInternal adjacencyListInternal, NodeExecution nodeExecution) {
    Map<String, GraphVertex> graphVertexMap = adjacencyListInternal.getGraphVertexMap();
    Map<String, EdgeListInternal> adjacencyList = adjacencyListInternal.getAdjacencyMap();

    String currentUuid = nodeExecution.getUuid();

    String parentId;
    if (isIdPresent(nodeExecution.getPreviousId())) {
      EdgeListInternal previousEdgeList = adjacencyList.get(nodeExecution.getPreviousId());
      previousEdgeList.getNextIds().remove(currentUuid);
    } else if (isIdPresent(nodeExecution.getParentId())) {
      parentId = nodeExecution.getParentId();
      EdgeListInternal parentEdgeList = adjacencyList.get(parentId);
      parentEdgeList.getEdges().remove(currentUuid);
    }

    graphVertexMap.remove(currentUuid);
    adjacencyList.remove(currentUuid);
  }

  public OrchestrationAdjacencyListInternal generatePartialAdjacencyList(
      String startingId, OrchestrationAdjacencyListInternal cachedAdjacencyList) {
    OrchestrationAdjacencyListInternal adjacencyListInternal = OrchestrationAdjacencyListInternal.builder()
                                                                   .graphVertexMap(new HashMap<>())
                                                                   .adjacencyMap(new HashMap<>())
                                                                   .build();
    populateAdjacencyListWithEdgesStartingFrom(startingId, cachedAdjacencyList, adjacencyListInternal);

    return adjacencyListInternal;
  }

  boolean isIdPresent(String id) {
    return EmptyPredicate.isNotEmpty(id);
  }

  boolean isChainNonInitialVertex(ExecutionMode mode, EdgeListInternal parentEdgeList) {
    return isChainMode(mode) && !parentEdgeList.getEdges().isEmpty();
  }

  OrchestrationAdjacencyListInternal generateList(
      String startingNodeExId, List<NodeExecution> nodeExecutions, boolean isOutcomePresent) {
    final GraphGeneratorSession session = createSession(nodeExecutions);
    return session.generateListStartingFrom(startingNodeExId, isOutcomePresent);
  }

  private GraphGeneratorSession createSession(List<NodeExecution> nodeExecutions) {
    Map<String, NodeExecution> nodeExIdMap = obtainNodeExecutionMap(nodeExecutions);
    Map<String, List<String>> parentIdMap = obtainParentIdMap(nodeExecutions);
    Map<String, String> oldRetriedNodeToLatestRetriedNodeExecutionMap =
        createOldRetriedNodeToLatestRetriedNodeExecutionMap(nodeExecutions);
    return new GraphGeneratorSession(nodeExIdMap, parentIdMap, oldRetriedNodeToLatestRetriedNodeExecutionMap);
  }

  private Map<String, String> createOldRetriedNodeToLatestRetriedNodeExecutionMap(List<NodeExecution> nodeExecutions) {
    Map<String, String> nodeExIdMap = new HashMap<>();
    for (NodeExecution nodeExecution : nodeExecutions) {
      nodeExecution.getRetryIds().forEach(o -> nodeExIdMap.put(o, nodeExecution.getUuid()));
    }
    return nodeExIdMap;
  }

  private Map<String, NodeExecution> obtainNodeExecutionMap(List<NodeExecution> nodeExecutions) {
    return nodeExecutions.stream().collect(toMap(NodeExecution::getUuid, identity()));
  }

  private Map<String, List<String>> obtainParentIdMap(List<NodeExecution> nodeExecutions) {
    return nodeExecutions.stream()
        .filter(node -> EmptyPredicate.isNotEmpty(node.getParentId()) && EmptyPredicate.isEmpty(node.getPreviousId()))
        .sorted(Comparator.comparingLong(NodeExecution::getCreatedAt))
        .collect(groupingBy(NodeExecution::getParentId, mapping(NodeExecution::getUuid, toList())));
  }

  private void appendToChainEnd(
      Map<String, EdgeListInternal> adjacencyList, String firstChainId, String nextId, List<String> prevIds) {
    EdgeListInternal edgeList = adjacencyList.get(firstChainId);
    String nextEdgeId = firstChainId;
    while (!edgeList.getNextIds().isEmpty()) {
      nextEdgeId = edgeList.getNextIds().get(0);
      edgeList = adjacencyList.get(nextEdgeId);
    }

    prevIds.add(nextEdgeId);
    edgeList.getNextIds().add(nextId);
  }

  private void populateAdjacencyListWithEdgesStartingFrom(String startingId,
      OrchestrationAdjacencyListInternal listToTraverse, OrchestrationAdjacencyListInternal listToPopulate) {
    EdgeListInternal edgeListInternal = listToTraverse.getAdjacencyMap().get(startingId);
    if (edgeListInternal == null) {
      return;
    }

    for (String edgeId : edgeListInternal.getEdges()) {
      populateAdjacencyListFor(edgeId, listToTraverse, listToPopulate);
    }

    listToPopulate.getGraphVertexMap().put(startingId, listToTraverse.getGraphVertexMap().get(startingId));
    listToPopulate.getAdjacencyMap().put(startingId, listToTraverse.getAdjacencyMap().get(startingId));
  }

  private void populateAdjacencyListFor(String currentId, OrchestrationAdjacencyListInternal listToTraverse,
      OrchestrationAdjacencyListInternal listToPopulate) {
    EdgeListInternal edgeListInternal = listToTraverse.getAdjacencyMap().get(currentId);
    if (edgeListInternal == null) {
      return;
    }

    for (String edgeId : edgeListInternal.getEdges()) {
      populateAdjacencyListFor(edgeId, listToTraverse, listToPopulate);
    }

    for (String nextId : edgeListInternal.getNextIds()) {
      populateAdjacencyListFor(nextId, listToTraverse, listToPopulate);
    }

    listToPopulate.getGraphVertexMap().put(currentId, listToTraverse.getGraphVertexMap().get(currentId));
    listToPopulate.getAdjacencyMap().put(currentId, listToTraverse.getAdjacencyMap().get(currentId));
  }

  private class GraphGeneratorSession {
    private final Map<String, NodeExecution> nodeExIdMap;
    private final Map<String, List<String>> parentIdMap;
    private final Map<String, String> oldRetriedNodeToLatestRetriedNodeExecutionMap;

    GraphGeneratorSession(Map<String, NodeExecution> nodeExIdMap, Map<String, List<String>> parentIdMap,
        Map<String, String> oldRetriedNodeToLatestRetriedNodeExecutionMap) {
      this.nodeExIdMap = nodeExIdMap;
      this.parentIdMap = parentIdMap;
      this.oldRetriedNodeToLatestRetriedNodeExecutionMap = oldRetriedNodeToLatestRetriedNodeExecutionMap;
    }

    private OrchestrationAdjacencyListInternal generateListStartingFrom(
        String startingNodeId, boolean isOutcomePresent) {
      if (startingNodeId == null) {
        throw new InvalidRequestException("The starting node id cannot be null");
      }

      Map<String, String> chainMap = new HashMap<>();
      Map<String, GraphVertex> graphVertexMap = new HashMap<>();
      Map<String, EdgeListInternal> adjacencyList = new HashMap<>();

      LinkedList<String> queue = new LinkedList<>();
      queue.add(startingNodeId);

      while (!queue.isEmpty()) {
        String currentNodeId = queue.removeFirst();
        NodeExecution nodeExecution = nodeExIdMap.get(currentNodeId);

        Map<String, PmsOutcome> outcomes;
        if (isOutcomePresent) {
          outcomes = PmsOutcomeMapper.convertJsonToOrchestrationMap(pmsOutcomeService.findAllOutcomesMapByRuntimeId(
              nodeExecution.getAmbiance().getPlanExecutionId(), currentNodeId));
        } else {
          outcomes = new LinkedHashMap<>();
        }
        NodeExecutionsInfo stepDetails = pmsGraphStepDetailsService.getNodeExecutionsInfo(currentNodeId);
        List<String> logBaseKeys = StepUtils.generateLogKeys(nodeExecution.getAmbiance(), null);

        GraphVertex graphVertex = graphVertexConverter.convertFrom(nodeExecution, outcomes, stepDetails, logBaseKeys);

        if (graphVertexMap.containsKey(graphVertex.getUuid())) {
          continue;
        }

        graphVertexMap.put(graphVertex.getUuid(), graphVertex);

        List<String> edges = new ArrayList<>();
        List<String> nextIds = new ArrayList<>();
        List<String> prevIds = new ArrayList<>();

        if (parentIdMap.containsKey(currentNodeId) && !parentIdMap.get(currentNodeId).isEmpty()) {
          List<String> childNodeIds = parentIdMap.get(currentNodeId);

          if (isChainMode(graphVertex.getMode())) {
            String chainStartingId = populateChainMap(chainMap, childNodeIds);
            edges.add(chainStartingId);
            queue.add(chainStartingId);
          } else {
            edges.addAll(childNodeIds);
            queue.addAll(childNodeIds);
          }
        }

        String nextNodeId = nodeExecution.getNextId();
        if (EmptyPredicate.isNotEmpty(nextNodeId)) {
          nextNodeId = oldRetriedNodeToLatestRetriedNodeExecutionMap.getOrDefault(nextNodeId, nextNodeId);
        }
        String parentNodeId = nodeExecution.getParentId();
        if (EmptyPredicate.isNotEmpty(nextNodeId)) {
          if (chainMap.containsKey(currentNodeId)) {
            chainMap.put(nextNodeId, chainMap.get(currentNodeId));
            chainMap.remove(currentNodeId);
          }
          if (!Objects.equals(parentNodeId, nextNodeId)) {
            queue.add(nextNodeId);
            nextIds.add(nextNodeId);
          }
        } else if (chainMap.containsKey(currentNodeId)) {
          nextNodeId = chainMap.get(currentNodeId);
          if (!Objects.equals(parentNodeId, nextNodeId)) {
            queue.add(nextNodeId);
            nextIds.add(nextNodeId);
          }
        }

        String prevNodeId = nodeExecution.getPreviousId();
        if (EmptyPredicate.isNotEmpty(prevNodeId)) {
          prevIds.add(prevNodeId);
        }
        adjacencyList.put(currentNodeId,
            EdgeListInternal.builder().edges(edges).nextIds(nextIds).prevIds(prevIds).parentId(parentNodeId).build());
      }

      return OrchestrationAdjacencyListInternal.builder()
          .graphVertexMap(graphVertexMap)
          .adjacencyMap(adjacencyList)
          .build();
    }

    /**
     * Population of chainMap with chainIds except for the last id <br>
     * Ex.: pin1 -> pin2 -> pin3 <br>
     *      map = {pin1, pin2}, {pin2, pin3} <br>
     * <br>
     * @param chainMap map containing chain order
     * @param chainIds list which contains ids of the chain
     * @return starting point of a chain
     */
    private String populateChainMap(Map<String, String> chainMap, List<String> chainIds) {
      for (int i = 1; i < chainIds.size(); ++i) {
        chainMap.put(chainIds.get(i - 1), chainIds.get(i));
      }
      return chainIds.get(0);
    }
  }
}
