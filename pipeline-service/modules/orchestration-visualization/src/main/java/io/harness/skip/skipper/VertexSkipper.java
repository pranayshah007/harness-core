/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.skip.skipper;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EphemeralOrchestrationGraph;
import io.harness.beans.GraphVertex;
import io.harness.beans.internal.EdgeListInternal;
import io.harness.beans.internal.OrchestrationAdjacencyListInternal;

import java.util.List;
import java.util.Map;

@OwnedBy(CDC)
public abstract class VertexSkipper {
  public void remapRelations(EphemeralOrchestrationGraph orchestrationGraph, GraphVertex skippedVertex) {
    OrchestrationAdjacencyListInternal adjacencyList = orchestrationGraph.getAdjacencyList();
    EdgeListInternal skippedVertexEdgeList = adjacencyList.getAdjacencyMap().get(skippedVertex.getUuid());

    // remapping relations
    if (!skippedVertexEdgeList.getPrevIds().isEmpty()) {
      for (String prevVertexId : skippedVertexEdgeList.getPrevIds()) {
        remapRelations(adjacencyList, skippedVertex.getUuid(), prevVertexId);
      }
    } else if (skippedVertexEdgeList.getParentId() != null) {
      remapRelationsForParent(adjacencyList, skippedVertex.getUuid(), skippedVertexEdgeList.getParentId());
    } else {
      // skipped vertex is a rootId
      remapRelations(adjacencyList, skippedVertex.getUuid(), null);
      orchestrationGraph.getRootNodeIds().clear();
      orchestrationGraph.getRootNodeIds().addAll(skippedVertexEdgeList.getNextIds());
    }
  }

  public void removeVertex(OrchestrationAdjacencyListInternal orchestrationAdjacencyList, String skippedVertexId) {
    orchestrationAdjacencyList.getGraphVertexMap().remove(skippedVertexId);
    orchestrationAdjacencyList.getAdjacencyMap().remove(skippedVertexId);
  }

  public abstract void skip(EphemeralOrchestrationGraph orchestrationGraph, GraphVertex skippedVertex);

  private void remapRelations(
      OrchestrationAdjacencyListInternal orchestrationAdjacencyList, String skippedVertexId, String precedingId) {
    Map<String, EdgeListInternal> adjacencyMap = orchestrationAdjacencyList.getAdjacencyMap();
    List<String> skippedVertexNextIds = getNextIdsFor(adjacencyMap, skippedVertexId);

    if (precedingId != null && adjacencyMap.containsKey(precedingId)) {
      List<String> precedingVertexNextIds = getNextIdsFor(adjacencyMap, precedingId);
      precedingVertexNextIds.remove(skippedVertexId);
      precedingVertexNextIds.addAll(skippedVertexNextIds);
    }

    skippedVertexNextIds.forEach(nextId -> {
      List<String> prevIds = getPrevIdsFor(adjacencyMap, nextId);
      prevIds.remove(skippedVertexId);
      if (precedingId != null) {
        prevIds.add(precedingId);
      }
    });
  }

  private void remapRelationsForParent(
      OrchestrationAdjacencyListInternal orchestrationAdjacencyList, String skippedVertexId, String parentId) {
    Map<String, EdgeListInternal> adjacencyList = orchestrationAdjacencyList.getAdjacencyMap();
    List<String> skippedVertexNextIds = getNextIdsFor(adjacencyList, skippedVertexId);

    if (adjacencyList.containsKey(parentId)) {
      List<String> parentEdges = adjacencyList.get(parentId).getEdges();
      parentEdges.remove(skippedVertexId);
      parentEdges.addAll(skippedVertexNextIds);
    }

    skippedVertexNextIds.forEach(nextId -> {
      EdgeListInternal edgeList = adjacencyList.get(nextId);
      edgeList.setParentId(parentId);
      edgeList.getPrevIds().remove(skippedVertexId);
    });
  }

  private List<String> getPrevIdsFor(Map<String, EdgeListInternal> adjacencyList, String vertexId) {
    return adjacencyList.get(vertexId).getPrevIds();
  }

  private List<String> getNextIdsFor(Map<String, EdgeListInternal> adjacencyList, String vertexId) {
    return adjacencyList.get(vertexId).getNextIds();
  }
}
