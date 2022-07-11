/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.skip.skipper.impl;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EphemeralOrchestrationGraph;
import io.harness.beans.GraphVertex;
import io.harness.beans.internal.EdgeListInternal;
import io.harness.beans.internal.OrchestrationAdjacencyListInternal;
import io.harness.skip.skipper.VertexSkipper;

import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;

@OwnedBy(CDC)
public class SkipNodeSkipper extends VertexSkipper {
  @Override
  public void skip(EphemeralOrchestrationGraph orchestrationGraph, GraphVertex skippedVertex) {
    OrchestrationAdjacencyListInternal adjacencyList = orchestrationGraph.getAdjacencyList();
    if (!adjacencyList.getGraphVertexMap().containsKey(skippedVertex.getUuid())) {
      return;
    }

    EdgeListInternal skippedEdgeList = adjacencyList.getAdjacencyMap().get(skippedVertex.getUuid());
    // check if we have children
    if (skippedEdgeList.getEdges().isEmpty()) {
      remapRelations(orchestrationGraph, skippedVertex);
    } else {
      promoteChildren(orchestrationGraph, skippedVertex);
    }

    removeVertex(orchestrationGraph.getAdjacencyList(), skippedVertex.getUuid());
  }

  private void promoteChildren(EphemeralOrchestrationGraph orchestrationGraph, GraphVertex skippedVertex) {
    Map<String, EdgeListInternal> adjacencyList = orchestrationGraph.getAdjacencyList().getAdjacencyMap();

    EdgeListInternal skippedVertexEdgeList = adjacencyList.get(skippedVertex.getUuid());
    if (!skippedVertexEdgeList.getPrevIds().isEmpty()) {
      skippedVertexEdgeList.getPrevIds().forEach(prevId -> {
        adjacencyList.get(prevId).getNextIds().addAll(skippedVertexEdgeList.getEdges());
        adjacencyList.get(prevId).getNextIds().remove(skippedVertex.getUuid());
      });
      skippedVertexEdgeList.getEdges().forEach(edge -> {
        EdgeListInternal edgeList = adjacencyList.get(edge);
        edgeList.getPrevIds().addAll(skippedVertexEdgeList.getPrevIds());
        edgeList.setParentId(null);
      });
    } else if (skippedVertexEdgeList.getParentId() != null) {
      String parentId = skippedVertexEdgeList.getParentId();
      EdgeListInternal parentEdgeList = adjacencyList.get(parentId);
      skippedVertexEdgeList.getEdges().forEach(edge -> {
        adjacencyList.get(edge).setParentId(parentId);
        parentEdgeList.getEdges().add(edge);
      });
      parentEdgeList.getEdges().remove(skippedVertex.getUuid());
    } else {
      // vertex is a rootId
      orchestrationGraph.getRootNodeIds().clear();
      orchestrationGraph.getRootNodeIds().addAll(skippedVertexEdgeList.getEdges());
      skippedVertexEdgeList.getEdges().forEach(edge -> adjacencyList.get(edge).setParentId(null));
    }

    final Session session = new Session(orchestrationGraph.getAdjacencyList());
    skippedVertexEdgeList.getNextIds().forEach(nextId -> {
      session.traverseNextIds(skippedVertexEdgeList.getEdges(), nextId, nextId);
      adjacencyList.get(nextId).getPrevIds().remove(skippedVertex.getUuid());
    });
  }

  @AllArgsConstructor
  private static final class Session {
    OrchestrationAdjacencyListInternal orchestrationAdjacencyList;

    private void traverseNextIds(List<String> skippedVertexEdgeIds, String nextIdToSet, String skippedVertexNextId) {
      if (skippedVertexEdgeIds.isEmpty()) {
        return;
      }
      skippedVertexEdgeIds.forEach(edge -> traverse(edge, nextIdToSet, skippedVertexNextId));
    }

    private void traverse(String nextId, String nextIdToSet, String skippedVertexNextId) {
      EdgeListInternal edgeList = orchestrationAdjacencyList.getAdjacencyMap().get(nextId);
      if (edgeList == null) {
        return;
      }

      if (edgeList.getNextIds().isEmpty()) {
        edgeList.getNextIds().add(nextIdToSet);
        orchestrationAdjacencyList.getAdjacencyMap().get(skippedVertexNextId).getPrevIds().add(nextId);
        return;
      }

      for (String nextToRemove : edgeList.getNextIds()) {
        traverse(nextToRemove, nextIdToSet, skippedVertexNextId);
      }
    }
  }
}
