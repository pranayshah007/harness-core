/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.service.impl;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.ALEXEI;
import static io.harness.rule.OwnerRule.ARCHIT;
import static io.harness.rule.OwnerRule.SHALINI;

import static junit.framework.TestCase.assertTrue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.OrchestrationVisualizationTestBase;
import io.harness.beans.GraphVertex;
import io.harness.beans.OrchestrationEventLog;
import io.harness.beans.OrchestrationGraph;
import io.harness.beans.converter.GraphVertexConverter;
import io.harness.beans.internal.EdgeListInternal;
import io.harness.beans.internal.OrchestrationAdjacencyListInternal;
import io.harness.cache.SpringCacheEntity;
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
import io.harness.pms.contracts.execution.events.OrchestrationEventType;
import io.harness.pms.contracts.steps.SkipType;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.repositories.orchestrationEventLog.OrchestrationEventLogRepository;
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
import java.util.Set;
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
  @Mock private OrchestrationEventLogRepository orchestrationEventLogRepository;
  @Inject private GraphVertexConverter graphVertexConverter;
  @InjectMocks @Inject private GraphGenerationService graphGenerationService;
  @Mock private OrchestrationEventEmitter eventEmitter;
  @Mock private PlanExecutionMetadataService planExecutionMetadataService;
  @Inject @InjectMocks GraphGenerationServiceImpl graphGenerationServiceImpl;

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

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testUpdateGraph() {
    assertTrue(graphGenerationService.updateGraph(generateUuid()));
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testUpdateGraphWithWaitLock() {
    assertTrue(graphGenerationService.updateGraphWithWaitLock(generateUuid()));
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testUpdateGraphUnderLock() {
    assertTrue(graphGenerationServiceImpl.updateGraphUnderLock(generateUuid()));
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testDeleteAllGraphMetadataForGivenExecutionIds() {
    String planExecutionId1 = "EXECUTION_1";
    OrchestrationGraph graph1 = OrchestrationGraph.builder().cacheKey(planExecutionId1).cacheParams(null).build();
    OrchestrationGraph graph2 = OrchestrationGraph.builder().cacheKey(planExecutionId1).cacheParams(null).build();
    String planExecutionId2 = "EXECUTION_2";
    OrchestrationGraph graph3 = OrchestrationGraph.builder().cacheKey(planExecutionId2).cacheParams(null).build();
    String planExecutionId3 = "EXECUTION_3";
    OrchestrationGraph graph4 = OrchestrationGraph.builder().cacheKey(planExecutionId3).cacheParams(null).build();
    mongoStore.upsert(graph1, SpringCacheEntity.TTL);
    mongoStore.upsert(graph2, SpringCacheEntity.TTL);
    mongoStore.upsert(graph3, SpringCacheEntity.TTL);
    mongoStore.upsert(graph4, SpringCacheEntity.TTL);

    OrchestrationGraph graphForExecution1 =
        mongoStore.get(OrchestrationGraph.ALGORITHM_ID, OrchestrationGraph.STRUCTURE_HASH, planExecutionId1, null);
    OrchestrationGraph graphForExecution2 =
        mongoStore.get(OrchestrationGraph.ALGORITHM_ID, OrchestrationGraph.STRUCTURE_HASH, planExecutionId2, null);
    OrchestrationGraph graphForExecution3 =
        mongoStore.get(OrchestrationGraph.ALGORITHM_ID, OrchestrationGraph.STRUCTURE_HASH, planExecutionId3, null);

    assertThat(graphForExecution1).isNotNull();
    assertThat(graphForExecution2).isNotNull();
    assertThat(graphForExecution3).isNotNull();

    graphGenerationServiceImpl.deleteAllGraphMetadataForGivenExecutionIds(Set.of(planExecutionId1, planExecutionId2));

    graphForExecution1 =
        mongoStore.get(OrchestrationGraph.ALGORITHM_ID, OrchestrationGraph.STRUCTURE_HASH, planExecutionId1, null);
    graphForExecution2 =
        mongoStore.get(OrchestrationGraph.ALGORITHM_ID, OrchestrationGraph.STRUCTURE_HASH, planExecutionId2, null);
    graphForExecution3 =
        mongoStore.get(OrchestrationGraph.ALGORITHM_ID, OrchestrationGraph.STRUCTURE_HASH, planExecutionId3, null);
    verify(orchestrationEventLogRepository, times(1)).deleteAllOrchestrationLogEvents(any());

    assertThat(graphForExecution1).isNull();
    assertThat(graphForExecution2).isNull();
    assertThat(graphForExecution3).isNotNull();
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testUpdateGraphUnderLockWithOrchestrationGraph() {
    String planExecutionId = generateUuid();
    String nodeExecutionId = generateUuid();
    List<OrchestrationEventLog> logs = new ArrayList<>();
    logs.add(OrchestrationEventLog.builder()
                 .nodeExecutionId(nodeExecutionId)
                 .orchestrationEventType(OrchestrationEventType.NODE_EXECUTION_START)
                 .createdAt(1550L)
                 .build());
    doReturn(logs).when(orchestrationEventLogRepository).findUnprocessedEvents(planExecutionId, 1222L, 1000);
    nodeExecutionService.save(
        NodeExecution.builder()
            .uuid(nodeExecutionId)
            .stepType(StepType.newBuilder().setStepCategory(StepCategory.STEP).build())
            .status(Status.SUCCEEDED)
            .ambiance(Ambiance.newBuilder()
                          .addLevels(Level.newBuilder().setNodeType(NodeType.PLAN_NODE.toString()).build())
                          .build())
            .module("cd")
            .resolvedStepParameters(new HashMap<>())
            .build());
    assertTrue(
        graphGenerationServiceImpl.updateGraphUnderLock(OrchestrationGraph.builder()
                                                            .planExecutionId(planExecutionId)
                                                            .rootNodeIds(new ArrayList<>())
                                                            .lastUpdatedAt(1222L)
                                                            .adjacencyList(OrchestrationAdjacencyListInternal.builder()
                                                                               .adjacencyMap(new HashMap<>())
                                                                               .graphVertexMap(new HashMap<>())
                                                                               .build())
                                                            .build()));
  }
}
