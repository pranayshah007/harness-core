/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.skip.service.impl;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.pms.contracts.steps.SkipType.NOOP;
import static io.harness.pms.contracts.steps.SkipType.SKIP_NODE;
import static io.harness.pms.contracts.steps.SkipType.SKIP_TREE;
import static io.harness.rule.OwnerRule.ALEXEI;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.OrchestrationVisualizationTestBase;
import io.harness.beans.EphemeralOrchestrationGraph;
import io.harness.beans.GraphVertex;
import io.harness.beans.internal.EdgeListInternal;
import io.harness.beans.internal.OrchestrationAdjacencyListInternal;
import io.harness.category.element.UnitTests;
import io.harness.pms.contracts.execution.Status;
import io.harness.rule.Owner;
import io.harness.skip.service.VertexSkipperService;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class VertexSkipperServiceImplTest extends OrchestrationVisualizationTestBase {
  private static final String PLAN_EXECUTION_ID = generateUuid();

  @Inject VertexSkipperService vertexSkipperService;

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldSkipVertices() {
    GraphVertex previous = GraphVertex.builder()
                               .uuid(generateUuid())
                               .name("previous")
                               .skipType(NOOP)
                               .lastUpdatedAt(System.currentTimeMillis())
                               .build();
    GraphVertex current = GraphVertex.builder()
                              .uuid(generateUuid())
                              .name("current")
                              .skipType(SKIP_TREE)
                              .lastUpdatedAt(System.currentTimeMillis())
                              .build();
    GraphVertex next = GraphVertex.builder()
                           .uuid(generateUuid())
                           .name("next")
                           .skipType(SKIP_NODE)
                           .lastUpdatedAt(System.currentTimeMillis())
                           .build();
    GraphVertex currentChild1 = GraphVertex.builder()
                                    .uuid(generateUuid())
                                    .name("currentChild1")
                                    .skipType(NOOP)
                                    .lastUpdatedAt(System.currentTimeMillis())
                                    .build();
    GraphVertex currentChild2 = GraphVertex.builder()
                                    .uuid(generateUuid())
                                    .name("currentChild2")
                                    .skipType(NOOP)
                                    .lastUpdatedAt(System.currentTimeMillis())
                                    .build();
    List<GraphVertex> graphVertices = Lists.newArrayList(previous, current, next, currentChild1, currentChild2);

    Map<String, GraphVertex> graphVertexMap =
        graphVertices.stream().collect(Collectors.toMap(GraphVertex::getUuid, Function.identity()));
    Map<String, EdgeListInternal> adjacencyList = new HashMap<>();
    adjacencyList.put(previous.getUuid(),
        EdgeListInternal.builder()
            .parentId(null)
            .prevIds(new ArrayList<>())
            .nextIds(Lists.newArrayList(current.getUuid()))
            .edges(new ArrayList<>())
            .build());
    adjacencyList.put(current.getUuid(),
        EdgeListInternal.builder()
            .parentId(null)
            .prevIds(Lists.newArrayList(previous.getUuid()))
            .nextIds(Lists.newArrayList(next.getUuid()))
            .edges(Lists.newArrayList(currentChild1.getUuid(), currentChild2.getUuid()))
            .build());
    adjacencyList.put(next.getUuid(),
        EdgeListInternal.builder()
            .parentId(null)
            .prevIds(Lists.newArrayList(current.getUuid()))
            .nextIds(new ArrayList<>())
            .edges(new ArrayList<>())
            .build());
    adjacencyList.put(currentChild1.getUuid(),
        EdgeListInternal.builder()
            .parentId(current.getUuid())
            .prevIds(new ArrayList<>())
            .nextIds(new ArrayList<>())
            .edges(new ArrayList<>())
            .build());
    adjacencyList.put(currentChild2.getUuid(),
        EdgeListInternal.builder()
            .parentId(current.getUuid())
            .prevIds(new ArrayList<>())
            .nextIds(new ArrayList<>())
            .edges(new ArrayList<>())
            .build());

    EphemeralOrchestrationGraph orchestrationGraph = EphemeralOrchestrationGraph.builder()
                                                         .startTs(System.currentTimeMillis())
                                                         .endTs(System.currentTimeMillis())
                                                         .status(Status.SUCCEEDED)
                                                         .rootNodeIds(Collections.singletonList("someId"))
                                                         .planExecutionId(PLAN_EXECUTION_ID)
                                                         .adjacencyList(OrchestrationAdjacencyListInternal.builder()
                                                                            .graphVertexMap(graphVertexMap)
                                                                            .adjacencyMap(adjacencyList)
                                                                            .build())
                                                         .build();

    vertexSkipperService.removeSkippedVertices(orchestrationGraph);

    OrchestrationAdjacencyListInternal updatedOrchestrationAdjacencyList = orchestrationGraph.getAdjacencyList();
    assertThat(updatedOrchestrationAdjacencyList).isNotNull();

    Map<String, GraphVertex> updatedGraphVertexMap = updatedOrchestrationAdjacencyList.getGraphVertexMap();
    assertThat(updatedGraphVertexMap).isNotEmpty();
    assertThat(updatedGraphVertexMap.values()).containsExactlyInAnyOrder(previous);

    Map<String, EdgeListInternal> updatedAdjacencyList = updatedOrchestrationAdjacencyList.getAdjacencyMap();
    assertThat(updatedAdjacencyList).isNotEmpty();
    assertThat(updatedAdjacencyList.keySet()).containsExactlyInAnyOrder(previous.getUuid());

    assertThat(updatedAdjacencyList.get(previous.getUuid()).getNextIds()).isEmpty();
    assertThat(updatedAdjacencyList.get(previous.getUuid()).getEdges()).isEmpty();
    assertThat(updatedAdjacencyList.get(previous.getUuid()).getParentId()).isNull();
    assertThat(updatedAdjacencyList.get(previous.getUuid()).getPrevIds()).isEmpty();
  }
}
