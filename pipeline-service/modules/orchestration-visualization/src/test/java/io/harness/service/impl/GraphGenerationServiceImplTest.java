/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.service.impl;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.ALEXEI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;

import io.harness.OrchestrationVisualizationTestBase;
import io.harness.beans.GraphVertex;
import io.harness.beans.OrchestrationGraph;
import io.harness.beans.converter.GraphVertexConverter;
import io.harness.beans.internal.EdgeListInternal;
import io.harness.beans.internal.OrchestrationAdjacencyListInternal;
import io.harness.cache.SpringMongoStore;
import io.harness.category.element.UnitTests;
import io.harness.dto.OrchestrationGraphDTO;
import io.harness.engine.events.OrchestrationEventEmitter;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.engine.executions.plan.PlanExecutionMetadataService;
import io.harness.engine.executions.plan.PlanExecutionService;
import io.harness.execution.NodeExecution;
import io.harness.execution.PlanExecution;
import io.harness.execution.PlanExecutionMetadata;
import io.harness.plan.NodeType;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.ExecutionMode;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.steps.SkipType;
import io.harness.pms.contracts.steps.StepType;
import io.harness.rule.Owner;
import io.harness.service.GraphGenerationService;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;

/**
 * Test class for {@link GraphGenerationServiceImpl}
 */
public class GraphGenerationServiceImplTest extends OrchestrationVisualizationTestBase {
  @Inject @InjectMocks private PlanExecutionService planExecutionService;
  @Inject @InjectMocks private NodeExecutionService nodeExecutionService;
  @Inject @InjectMocks private SpringMongoStore mongoStore;
  @Inject private GraphVertexConverter graphVertexConverter;
  @InjectMocks @Inject private GraphGenerationService graphGenerationService;
  @Mock private OrchestrationEventEmitter eventEmitter;
  @Mock private PlanExecutionMetadataService planExecutionMetadataService;

  @Before
  public void setup() {
    Mockito.doNothing().when(eventEmitter).emitEvent(any());
    Mockito.when(planExecutionMetadataService.findByPlanExecutionId(any()))
        .thenReturn(Optional.of(PlanExecutionMetadata.builder().build()));
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldReturnOrchestrationGraphWithoutCache() {
    PlanExecution planExecution = planExecutionService.save(PlanExecution.builder().build());
    NodeExecution dummyStart =
        NodeExecution.builder()
            .uuid(generateUuid())
            .status(Status.SUCCEEDED)
            .ambiance(
                Ambiance.newBuilder()
                    .setPlanExecutionId(planExecution.getUuid())
                    .addAllLevels(Collections.singletonList(
                        Level.newBuilder().setSetupId("node1_plan").setNodeType(NodeType.PLAN_NODE.name()).build()))
                    .build())
            .mode(ExecutionMode.SYNC)
            .nodeId("node1_plan")
            .name("name")
            .stepType(StepType.newBuilder().setType("DUMMY").build())
            .identifier("identifier1")
            .module("CD")
            .skipGraphType(SkipType.NOOP)
            .identifier("identifier1")
            .build();
    nodeExecutionService.save(dummyStart);

    OrchestrationGraphDTO graphResponse = graphGenerationService.generateOrchestrationGraphV2(planExecution.getUuid());
    assertThat(graphResponse).isNotNull();
    assertThat(graphResponse.getAdjacencyList()).isNotNull();
    assertThat(graphResponse.getAdjacencyList().getGraphVertexMap()).isNotEmpty();
    assertThat(graphResponse.getAdjacencyList().getGraphVertexMap().size()).isEqualTo(1);
    assertThat(graphResponse.getAdjacencyList().getAdjacencyMap().get(graphResponse.getRootNodeIds().get(0)))
        .isNotNull();
    assertThat(
        graphResponse.getAdjacencyList().getAdjacencyMap().get(graphResponse.getRootNodeIds().get(0)).getNextIds())
        .isEmpty();
    assertThat(graphResponse.getAdjacencyList().getAdjacencyMap().get(graphResponse.getRootNodeIds().get(0)).getEdges())
        .isEmpty();
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldReturnPartialOrchestrationGraph() {
    GraphVertex dummyStart = GraphVertex.builder()
                                 .uuid(generateUuid())
                                 .ambiance(Ambiance.newBuilder()
                                               .setPlanExecutionId("")
                                               .addAllLevels(new ArrayList<>())
                                               .putAllSetupAbstractions(new HashMap<>())
                                               .build())
                                 .planNodeId("node1_plan")
                                 .name("dummyStart")
                                 .mode(ExecutionMode.SYNC)
                                 .skipType(SkipType.NOOP)
                                 .build();
    GraphVertex dummyFinish = GraphVertex.builder()
                                  .uuid(generateUuid())
                                  .ambiance(Ambiance.newBuilder()
                                                .setPlanExecutionId("")
                                                .addAllLevels(new ArrayList<>())
                                                .putAllSetupAbstractions(new HashMap<>())
                                                .build())
                                  .planNodeId("node2_plan")
                                  .name("dummyFinish")
                                  .skipType(SkipType.NOOP)
                                  .build();

    OrchestrationGraph orchestrationGraph =
        constructOrchestrationGraphForPartialTest(Lists.newArrayList(dummyStart, dummyFinish));
    graphGenerationService.cacheOrchestrationGraph(orchestrationGraph);

    OrchestrationGraphDTO graphResponse =
        graphGenerationService.generatePartialOrchestrationGraphFromSetupNodeIdAndExecutionId(
            dummyFinish.getPlanNodeId(), orchestrationGraph.getPlanExecutionId(), null);
    assertThat(graphResponse).isNotNull();
    assertThat(graphResponse.getRootNodeIds().get(0)).isEqualTo(dummyFinish.getUuid());
    assertThat(graphResponse.getAdjacencyList()).isNotNull();
    assertThat(graphResponse.getAdjacencyList().getGraphVertexMap()).isNotEmpty();
    assertThat(graphResponse.getAdjacencyList().getGraphVertexMap().size()).isEqualTo(1);
    assertThat(graphResponse.getAdjacencyList().getAdjacencyMap().get(graphResponse.getRootNodeIds().get(0)))
        .isNotNull();
    assertThat(
        graphResponse.getAdjacencyList().getAdjacencyMap().get(graphResponse.getRootNodeIds().get(0)).getNextIds())
        .isEmpty();
    assertThat(graphResponse.getAdjacencyList().getAdjacencyMap().get(graphResponse.getRootNodeIds().get(0)).getEdges())
        .isEmpty();
  }

  private OrchestrationGraph constructOrchestrationGraphForPartialTest(List<GraphVertex> graphVertices) {
    PlanExecution planExecution = planExecutionService.save(PlanExecution.builder().build());

    Map<String, GraphVertex> graphVertexMap =
        graphVertices.stream().collect(Collectors.toMap(GraphVertex::getUuid, Function.identity()));
    Map<String, EdgeListInternal> adjacencyMap = new HashMap<>();
    adjacencyMap.put(graphVertices.get(0).getUuid(),
        EdgeListInternal.builder()
            .parentId(null)
            .prevIds(new ArrayList<>())
            .nextIds(Collections.singletonList(graphVertices.get(1).getUuid()))
            .edges(new ArrayList<>())
            .build());
    adjacencyMap.put(graphVertices.get(1).getUuid(),
        EdgeListInternal.builder()
            .parentId(null)
            .prevIds(Collections.singletonList(graphVertices.get(0).getUuid()))
            .nextIds(new ArrayList<>())
            .edges(new ArrayList<>())
            .build());
    OrchestrationAdjacencyListInternal listInternal =
        OrchestrationAdjacencyListInternal.builder().graphVertexMap(graphVertexMap).adjacencyMap(adjacencyMap).build();

    return OrchestrationGraph.builder()
        .cacheKey(planExecution.getUuid())
        .cacheContextOrder(System.currentTimeMillis())
        .cacheParams(null)
        .startTs(System.currentTimeMillis())
        .endTs(System.currentTimeMillis())
        .rootNodeIds(Collections.singletonList(graphVertices.get(0).getUuid()))
        .planExecutionId(planExecution.getUuid())
        .status(Status.SUCCEEDED)
        .adjacencyList(listInternal)
        .build();
  }
}
