/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.generator;

import static io.harness.rule.OwnerRule.ALEXEI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import io.harness.OrchestrationVisualizationTestBase;
import io.harness.beans.GraphVertex;
import io.harness.beans.converter.GraphVertexConverter;
import io.harness.beans.internal.EdgeListInternal;
import io.harness.beans.internal.OrchestrationAdjacencyListInternal;
import io.harness.category.element.UnitTests;
import io.harness.engine.pms.data.PmsOutcomeService;
import io.harness.execution.NodeExecution;
import io.harness.plan.PlanNode;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.ExecutionMode;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.data.stepparameters.PmsStepParameters;
import io.harness.pms.sdk.core.resolver.outcome.mapper.PmsOutcomeMapper;
import io.harness.pms.sdk.core.steps.io.StepParameters;
import io.harness.pms.serializer.recaster.RecastOrchestrationUtils;
import io.harness.rule.Owner;
import io.harness.utils.DummyForkStepParameters;
import io.harness.utils.DummySectionStepParameters;
import io.harness.utils.DummyVisualizationOutcome;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

/**
 * Test class for {@link OrchestrationAdjacencyListGenerator}
 */
public class OrchestrationAdjacencyListDTOGeneratorTest extends OrchestrationVisualizationTestBase {
  private static final String PLAN_EXECUTION_ID = "planId";
  private static final String STARTING_EXECUTION_NODE_ID = "startID";

  private static final StepType DUMMY_STEP_TYPE =
      StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build();

  @Mock private PmsOutcomeService pmsOutcomeService;
  @Inject private GraphVertexConverter graphVertexConverter;
  @InjectMocks @Inject private OrchestrationAdjacencyListGenerator orchestrationAdjacencyListGenerator;

  @Before
  public void setUp() {
    when(pmsOutcomeService.findAllByRuntimeId(anyString(), anyString()))
        .thenReturn(Collections.singletonList(
            PmsOutcomeMapper.convertOutcomeValueToJson(new DummyVisualizationOutcome("outcome"))));
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldGenerateOrchestrationAdjacencyListInternalWithSection() {
    NodeExecution dummyStart = NodeExecution.builder()
                                   .uuid("node1")
                                   .ambiance(Ambiance.newBuilder()
                                                 .setPlanExecutionId(PLAN_EXECUTION_ID)
                                                 .addAllLevels(Collections.singletonList(
                                                     Level.newBuilder().setSetupId(STARTING_EXECUTION_NODE_ID).build()))
                                                 .build())
                                   .mode(ExecutionMode.SYNC)
                                   .planNode(PlanNode.builder()
                                                 .uuid(STARTING_EXECUTION_NODE_ID)
                                                 .name("name")
                                                 .stepType(DUMMY_STEP_TYPE)
                                                 .identifier("identifier1")
                                                 .build())
                                   .nextId("node2")
                                   .build();
    Map<String, Object> sectionStepParams =
        RecastOrchestrationUtils.toMap(DummySectionStepParameters.builder().childNodeId("child_section_2").build());
    NodeExecution section =
        NodeExecution.builder()
            .uuid("node2")
            .ambiance(Ambiance.newBuilder()
                          .setPlanExecutionId(PLAN_EXECUTION_ID)
                          .addAllLevels(Collections.singletonList(Level.newBuilder().setSetupId("section_2").build()))
                          .build())
            .mode(ExecutionMode.CHILD)
            .planNode(PlanNode.builder()
                          .uuid("section_2")
                          .name("name2")
                          .stepType(StepType.newBuilder().setType("SECTION").setStepCategory(StepCategory.STEP).build())
                          .identifier("identifier2")
                          .stepParameters(PmsStepParameters.parse(RecastOrchestrationUtils.toJson(sectionStepParams)))
                          .build())
            .resolvedStepParameters(sectionStepParams)
            .previousId(dummyStart.getUuid())
            .build();
    NodeExecution sectionChild =
        NodeExecution.builder()
            .uuid("node_child_2")
            .ambiance(
                Ambiance.newBuilder()
                    .setPlanExecutionId(PLAN_EXECUTION_ID)
                    .addAllLevels(Collections.singletonList(Level.newBuilder().setSetupId("child_section_2").build()))
                    .build())
            .mode(ExecutionMode.SYNC)
            .planNode(PlanNode.builder()
                          .uuid("child_section_2")
                          .name("name_child_2")
                          .stepType(DUMMY_STEP_TYPE)
                          .identifier("identifier_child_2")
                          .build())
            .parentId(section.getUuid())
            .build();
    List<NodeExecution> nodeExecutions = Lists.newArrayList(dummyStart, section, sectionChild);

    OrchestrationAdjacencyListInternal adjacencyList =
        orchestrationAdjacencyListGenerator.generateAdjacencyList(dummyStart.getUuid(), nodeExecutions, false);

    assertThat(adjacencyList).isNotNull();

    assertThat(adjacencyList.getGraphVertexMap().size()).isEqualTo(3);
    assertThat(adjacencyList.getGraphVertexMap().keySet())
        .containsExactlyInAnyOrder(dummyStart.getUuid(), section.getUuid(), sectionChild.getUuid());

    assertThat(adjacencyList.getAdjacencyMap().size()).isEqualTo(3);
    assertThat(adjacencyList.getAdjacencyMap().get(dummyStart.getUuid()).getNextIds())
        .containsExactlyInAnyOrder(section.getUuid());
    assertThat(adjacencyList.getAdjacencyMap().get(section.getUuid()).getEdges())
        .containsExactlyInAnyOrder(sectionChild.getUuid());
    assertThat(adjacencyList.getAdjacencyMap().get(sectionChild.getUuid()).getEdges()).isEmpty();
    assertThat(adjacencyList.getAdjacencyMap().get(sectionChild.getUuid()).getNextIds()).isEmpty();
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldGenerateOrchestrationAdjacencyListInternalWithChildChain() {
    String dummyNode1Uuid = "dummyNode1";
    String dummyNode2Uuid = "dummyNode2";

    NodeExecution sectionChainParentNode =
        NodeExecution.builder()
            .uuid("section_chain_start")
            .ambiance(Ambiance.newBuilder()
                          .setPlanExecutionId(PLAN_EXECUTION_ID)
                          .addAllLevels(Collections.singletonList(
                              Level.newBuilder().setSetupId("section_chain_plan_node").build()))
                          .build())
            .mode(ExecutionMode.CHILD_CHAIN)
            .planNode(PlanNode.builder()
                          .uuid("section_chain_plan_node")
                          .name("name_section_chain")
                          .identifier("name_section_chain")
                          .stepType(StepType.newBuilder()
                                        .setType("_DUMMY_SECTION_CHAIN")
                                        .setStepCategory(StepCategory.STEP)
                                        .build())
                          .build())
            .createdAt(System.currentTimeMillis())
            .lastUpdatedAt(System.currentTimeMillis())
            .build();

    NodeExecution sectionChain1 =
        NodeExecution.builder()
            .uuid("section_chain_child1")
            .ambiance(Ambiance.newBuilder()
                          .setPlanExecutionId(PLAN_EXECUTION_ID)
                          .addAllLevels(Collections.singletonList(
                              Level.newBuilder().setSetupId("section_chain_child1_plan_node").build()))
                          .build())
            .mode(ExecutionMode.TASK)
            .planNode(PlanNode.builder()
                          .uuid("section_chain_child1_plan_node")
                          .name("name_section_chain_child1_plan_node")
                          .identifier("name_section_chain_child1_plan_node")
                          .stepType(DUMMY_STEP_TYPE)
                          .build())
            .createdAt(System.currentTimeMillis())
            .lastUpdatedAt(System.currentTimeMillis())
            .parentId(sectionChainParentNode.getUuid())
            .nextId(dummyNode1Uuid)
            .build();

    NodeExecution sectionChain2 =
        NodeExecution.builder()
            .uuid("section_chain_child2")
            .ambiance(Ambiance.newBuilder()
                          .setPlanExecutionId(PLAN_EXECUTION_ID)
                          .addAllLevels(Collections.singletonList(
                              Level.newBuilder().setSetupId("section_chain_child2_plan_node").build()))
                          .build())
            .mode(ExecutionMode.TASK)
            .planNode(PlanNode.builder()
                          .uuid("section_chain_child2_plan_node")
                          .name("name_section_chain_child2_plan_node")
                          .identifier("name_section_chain_child2_plan_node")
                          .stepType(DUMMY_STEP_TYPE)
                          .build())
            .createdAt(System.currentTimeMillis())
            .lastUpdatedAt(System.currentTimeMillis())
            .parentId(sectionChainParentNode.getUuid())
            .nextId(dummyNode2Uuid)
            .build();

    NodeExecution dummyNode1 =
        NodeExecution.builder()
            .uuid(dummyNode1Uuid)
            .ambiance(
                Ambiance.newBuilder()
                    .setPlanExecutionId(PLAN_EXECUTION_ID)
                    .addAllLevels(Collections.singletonList(Level.newBuilder().setSetupId("dummy_plan_node_1").build()))
                    .build())
            .mode(ExecutionMode.SYNC)
            .planNode(PlanNode.builder()
                          .uuid("dummy_plan_node_1")
                          .name("name_dummy_node_1")
                          .stepType(DUMMY_STEP_TYPE)
                          .identifier("name_dummy_node_1")
                          .build())
            .createdAt(System.currentTimeMillis())
            .lastUpdatedAt(System.currentTimeMillis())
            .parentId(sectionChainParentNode.getUuid())
            .previousId(sectionChain1.getUuid())
            .build();

    NodeExecution dummyNode2 =
        NodeExecution.builder()
            .uuid(dummyNode2Uuid)
            .ambiance(
                Ambiance.newBuilder()
                    .setPlanExecutionId(PLAN_EXECUTION_ID)
                    .addAllLevels(Collections.singletonList(Level.newBuilder().setSetupId("dummy_plan_node_2").build()))
                    .build())
            .mode(ExecutionMode.SYNC)
            .planNode(PlanNode.builder()
                          .uuid("dummy_plan_node_2")
                          .name("name_dummy_node_2")
                          .stepType(DUMMY_STEP_TYPE)
                          .identifier("name_dummy_node_2")
                          .build())
            .createdAt(System.currentTimeMillis())
            .lastUpdatedAt(System.currentTimeMillis())
            .parentId(sectionChainParentNode.getUuid())
            .previousId(sectionChain2.getUuid())
            .build();

    List<NodeExecution> nodeExecutions =
        Lists.newArrayList(sectionChainParentNode, sectionChain1, sectionChain2, dummyNode1, dummyNode2);

    OrchestrationAdjacencyListInternal adjacencyList = orchestrationAdjacencyListGenerator.generateAdjacencyList(
        sectionChainParentNode.getUuid(), nodeExecutions, true);
    assertThat(adjacencyList).isNotNull();

    assertThat(adjacencyList.getGraphVertexMap().size()).isEqualTo(5);
    assertThat(adjacencyList.getGraphVertexMap().keySet())
        .containsExactlyInAnyOrder(sectionChainParentNode.getUuid(), sectionChain1.getUuid(), sectionChain2.getUuid(),
            dummyNode1.getUuid(), dummyNode2.getUuid());

    assertThat(adjacencyList.getAdjacencyMap().size()).isEqualTo(5);
    assertThat(adjacencyList.getAdjacencyMap().get(sectionChainParentNode.getUuid()).getEdges())
        .containsExactlyInAnyOrder(sectionChain1.getUuid());
    assertThat(adjacencyList.getAdjacencyMap().get(sectionChain1.getUuid()).getNextIds())
        .containsExactlyInAnyOrder(dummyNode1.getUuid());
    assertThat(adjacencyList.getAdjacencyMap().get(dummyNode1.getUuid()).getNextIds())
        .containsExactlyInAnyOrder(sectionChain2.getUuid());
    assertThat(adjacencyList.getAdjacencyMap().get(sectionChain2.getUuid()).getNextIds())
        .containsExactlyInAnyOrder(dummyNode2.getUuid());
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldGenerateOrchestrationGraphWithFork() {
    StepParameters forkStepParams =
        DummyForkStepParameters.builder().parallelNodeId("parallel_node_1").parallelNodeId("parallel_node_2").build();
    NodeExecution fork =
        NodeExecution.builder()
            .uuid("node1")
            .ambiance(Ambiance.newBuilder()
                          .setPlanExecutionId(PLAN_EXECUTION_ID)
                          .addAllLevels(Collections.singletonList(
                              Level.newBuilder().setSetupId(STARTING_EXECUTION_NODE_ID).build()))
                          .build())
            .mode(ExecutionMode.CHILDREN)
            .planNode(
                PlanNode.builder()
                    .uuid(STARTING_EXECUTION_NODE_ID)
                    .name("name1")
                    .stepType(StepType.newBuilder().setType("DUMMY_FORK").setStepCategory(StepCategory.STEP).build())
                    .identifier("identifier1")
                    .stepParameters(PmsStepParameters.parse(RecastOrchestrationUtils.toJson(forkStepParams)))
                    .build())
            .resolvedStepParameters(RecastOrchestrationUtils.toMap(forkStepParams))
            .createdAt(System.currentTimeMillis())
            .lastUpdatedAt(System.currentTimeMillis())
            .build();
    NodeExecution parallelNode1 = NodeExecution.builder()
                                      .uuid("parallel_node_1")
                                      .ambiance(Ambiance.newBuilder()
                                                    .setPlanExecutionId(PLAN_EXECUTION_ID)
                                                    .addAllLevels(Collections.singletonList(
                                                        Level.newBuilder().setSetupId("parallel_plan_node_1").build()))
                                                    .build())
                                      .mode(ExecutionMode.SYNC)
                                      .planNode(PlanNode.builder()
                                                    .uuid("parallel_plan_node_1")
                                                    .name("name_children_1")
                                                    .stepType(DUMMY_STEP_TYPE)
                                                    .identifier("name_children_1")
                                                    .build())
                                      .parentId(fork.getUuid())
                                      .createdAt(System.currentTimeMillis())
                                      .lastUpdatedAt(System.currentTimeMillis())
                                      .build();
    NodeExecution parallelNode2 = NodeExecution.builder()
                                      .uuid("parallel_node_2")
                                      .ambiance(Ambiance.newBuilder()
                                                    .setPlanExecutionId(PLAN_EXECUTION_ID)
                                                    .addAllLevels(Collections.singletonList(
                                                        Level.newBuilder().setSetupId("parallel_plan_node_2").build()))
                                                    .build())
                                      .mode(ExecutionMode.SYNC)
                                      .planNode(PlanNode.builder()
                                                    .uuid("parallel_plan_node_2")
                                                    .name("name_children_2")
                                                    .stepType(DUMMY_STEP_TYPE)
                                                    .identifier("name_children_2")
                                                    .build())
                                      .parentId(fork.getUuid())
                                      .createdAt(System.currentTimeMillis())
                                      .lastUpdatedAt(System.currentTimeMillis())
                                      .build();
    List<NodeExecution> nodeExecutions = Lists.newArrayList(fork, parallelNode1, parallelNode2);

    OrchestrationAdjacencyListInternal adjacencyList =
        orchestrationAdjacencyListGenerator.generateAdjacencyList(fork.getUuid(), nodeExecutions, false);
    assertThat(adjacencyList).isNotNull();

    assertThat(adjacencyList.getGraphVertexMap().size()).isEqualTo(3);
    assertThat(adjacencyList.getGraphVertexMap().keySet())
        .containsExactlyInAnyOrder(fork.getUuid(), parallelNode1.getUuid(), parallelNode2.getUuid());

    assertThat(adjacencyList.getAdjacencyMap().get(fork.getUuid())).isNotNull();
    assertThat(adjacencyList.getAdjacencyMap().get(fork.getUuid()).getNextIds()).isEmpty();
    assertThat(adjacencyList.getAdjacencyMap().get(fork.getUuid()).getEdges())
        .containsExactlyInAnyOrder(parallelNode1.getUuid(), parallelNode2.getUuid());
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldGeneratePartialOrchestrationGraphWithFork() {
    NodeExecution dummyNode1 =
        NodeExecution.builder()
            .uuid("dummy_node_1")
            .ambiance(
                Ambiance.newBuilder()
                    .setPlanExecutionId(PLAN_EXECUTION_ID)
                    .addAllLevels(Collections.singletonList(Level.newBuilder().setSetupId("dummy_plan_node_1").build()))
                    .build())
            .mode(ExecutionMode.SYNC)
            .planNode(PlanNode.builder()
                          .uuid("dummy_plan_node_1")
                          .name("dummy_node_1")
                          .stepType(DUMMY_STEP_TYPE)
                          .identifier("dummy_node_1")
                          .build())
            .createdAt(System.currentTimeMillis())
            .lastUpdatedAt(System.currentTimeMillis())
            .build();
    StepParameters forkStepParams =
        DummyForkStepParameters.builder().parallelNodeId("parallel_node_1").parallelNodeId("parallel_node_2").build();
    NodeExecution fork =
        NodeExecution.builder()
            .uuid("node1")
            .ambiance(Ambiance.newBuilder()
                          .setPlanExecutionId(PLAN_EXECUTION_ID)
                          .addAllLevels(Collections.singletonList(
                              Level.newBuilder().setSetupId(STARTING_EXECUTION_NODE_ID).build()))
                          .build())
            .mode(ExecutionMode.CHILDREN)
            .planNode(PlanNode.builder()
                          .uuid(STARTING_EXECUTION_NODE_ID)
                          .name("name1")
                          .stepType(StepType.newBuilder().setType("DUMMY_FORK").build())
                          .identifier("identifier1")
                          .stepParameters(PmsStepParameters.parse(RecastOrchestrationUtils.toJson(forkStepParams)))
                          .build())
            .resolvedStepParameters(RecastOrchestrationUtils.toMap(forkStepParams))
            .createdAt(System.currentTimeMillis())
            .lastUpdatedAt(System.currentTimeMillis())
            .build();
    NodeExecution parallelNode1 = NodeExecution.builder()
                                      .uuid("parallel_node_1")
                                      .ambiance(Ambiance.newBuilder()
                                                    .setPlanExecutionId(PLAN_EXECUTION_ID)
                                                    .addAllLevels(Collections.singletonList(
                                                        Level.newBuilder().setSetupId("parallel_plan_node_1").build()))
                                                    .build())
                                      .mode(ExecutionMode.SYNC)
                                      .planNode(PlanNode.builder()
                                                    .uuid("parallel_plan_node_1")
                                                    .name("name_children_1")
                                                    .stepType(DUMMY_STEP_TYPE)
                                                    .identifier("name_children_1")
                                                    .build())
                                      .parentId(fork.getUuid())
                                      .createdAt(System.currentTimeMillis())
                                      .lastUpdatedAt(System.currentTimeMillis())
                                      .build();
    NodeExecution parallelNode2 = NodeExecution.builder()
                                      .uuid("parallel_node_2")
                                      .ambiance(Ambiance.newBuilder()
                                                    .setPlanExecutionId(PLAN_EXECUTION_ID)
                                                    .addAllLevels(Collections.singletonList(
                                                        Level.newBuilder().setSetupId("parallel_plan_node_2").build()))
                                                    .build())
                                      .mode(ExecutionMode.SYNC)
                                      .planNode(PlanNode.builder()
                                                    .uuid("parallel_plan_node_2")
                                                    .name("name_children_2")
                                                    .stepType(DUMMY_STEP_TYPE)
                                                    .identifier("name_children_2")
                                                    .build())
                                      .parentId(fork.getUuid())
                                      .nextId(dummyNode1.getUuid())
                                      .createdAt(System.currentTimeMillis())
                                      .lastUpdatedAt(System.currentTimeMillis())
                                      .build();
    List<NodeExecution> nodeExecutions = Lists.newArrayList(fork, parallelNode1, parallelNode2, dummyNode1);

    OrchestrationAdjacencyListInternal adjacencyList =
        orchestrationAdjacencyListGenerator.generateAdjacencyList(parallelNode2.getUuid(), nodeExecutions, true);
    assertThat(adjacencyList).isNotNull();

    assertThat(adjacencyList.getGraphVertexMap().size()).isEqualTo(2);
    assertThat(adjacencyList.getGraphVertexMap().keySet())
        .containsExactlyInAnyOrder(parallelNode2.getUuid(), dummyNode1.getUuid());

    assertThat(adjacencyList.getAdjacencyMap().get(parallelNode2.getUuid()).getEdges()).isEmpty();
    assertThat(adjacencyList.getAdjacencyMap().get(parallelNode2.getUuid()).getNextIds())
        .containsExactlyInAnyOrder(dummyNode1.getUuid());

    assertThat(adjacencyList.getAdjacencyMap().get(dummyNode1.getUuid()).getEdges()).isEmpty();
    assertThat(adjacencyList.getAdjacencyMap().get(dummyNode1.getUuid()).getNextIds()).isEmpty();
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldGeneratePartialAdjacencyListWithFork() {
    GraphVertex fork = GraphVertex.builder()
                           .uuid("fork1")
                           .planNodeId(STARTING_EXECUTION_NODE_ID)
                           .mode(ExecutionMode.CHILDREN)
                           .lastUpdatedAt(System.currentTimeMillis())
                           .build();
    GraphVertex parallelNode1 = GraphVertex.builder()
                                    .uuid("parallel_node_1")
                                    .planNodeId("parallel_plan_node_1")
                                    .mode(ExecutionMode.SYNC)
                                    .lastUpdatedAt(System.currentTimeMillis())
                                    .build();
    GraphVertex parallelNode2 = GraphVertex.builder()
                                    .uuid("parallel_node_2")
                                    .planNodeId("parallel_plan_node_2")
                                    .mode(ExecutionMode.SYNC)
                                    .lastUpdatedAt(System.currentTimeMillis())
                                    .build();
    GraphVertex dummyNode1 = GraphVertex.builder()
                                 .uuid("dummy_node_1")
                                 .planNodeId("dummy_plan_node_1")
                                 .mode(ExecutionMode.SYNC)
                                 .lastUpdatedAt(System.currentTimeMillis())
                                 .build();
    GraphVertex dummyNode2 = GraphVertex.builder()
                                 .uuid("dummy_node_2")
                                 .planNodeId("dummy_plan_node_2")
                                 .mode(ExecutionMode.SYNC)
                                 .lastUpdatedAt(System.currentTimeMillis())
                                 .build();
    GraphVertex dummyNode3 = GraphVertex.builder()
                                 .uuid("dummy_node_3")
                                 .planNodeId("dummy_plan_node_3")
                                 .mode(ExecutionMode.SYNC)
                                 .lastUpdatedAt(System.currentTimeMillis())
                                 .build();
    List<GraphVertex> vertices =
        Lists.newArrayList(fork, parallelNode1, parallelNode2, dummyNode1, dummyNode2, dummyNode3);

    Map<String, GraphVertex> graphVertexMap =
        vertices.stream().collect(Collectors.toMap(GraphVertex::getUuid, Function.identity()));
    Map<String, EdgeListInternal> adjacencyMap = new HashMap<>();
    adjacencyMap.put(fork.getUuid(),
        EdgeListInternal.builder()
            .edges(Arrays.asList(parallelNode1.getUuid(), parallelNode2.getUuid()))
            .nextIds(new ArrayList<>())
            .prevIds(new ArrayList<>())
            .parentId(null)
            .build());
    adjacencyMap.put(parallelNode1.getUuid(),
        EdgeListInternal.builder()
            .edges(new ArrayList<>())
            .nextIds(new ArrayList<>())
            .prevIds(new ArrayList<>())
            .parentId(fork.getUuid())
            .build());
    adjacencyMap.put(parallelNode2.getUuid(),
        EdgeListInternal.builder()
            .edges(Collections.singletonList(dummyNode2.getUuid()))
            .nextIds(Collections.singletonList(dummyNode1.getUuid()))
            .prevIds(new ArrayList<>())
            .parentId(fork.getUuid())
            .build());
    adjacencyMap.put(dummyNode1.getUuid(),
        EdgeListInternal.builder()
            .edges(new ArrayList<>())
            .nextIds(new ArrayList<>())
            .prevIds(Collections.singletonList(parallelNode2.getUuid()))
            .parentId(null)
            .build());
    adjacencyMap.put(dummyNode2.getUuid(),
        EdgeListInternal.builder()
            .edges(new ArrayList<>())
            .nextIds(Collections.singletonList(dummyNode3.getUuid()))
            .prevIds(new ArrayList<>())
            .parentId(parallelNode2.getUuid())
            .build());
    adjacencyMap.put(dummyNode3.getUuid(),
        EdgeListInternal.builder()
            .edges(new ArrayList<>())
            .nextIds(new ArrayList<>())
            .prevIds(Collections.singletonList(dummyNode2.getUuid()))
            .parentId(null)
            .build());

    OrchestrationAdjacencyListInternal listInternal =
        OrchestrationAdjacencyListInternal.builder().graphVertexMap(graphVertexMap).adjacencyMap(adjacencyMap).build();

    OrchestrationAdjacencyListInternal adjacencyList =
        orchestrationAdjacencyListGenerator.generatePartialAdjacencyList(parallelNode2.getUuid(), listInternal);
    assertThat(adjacencyList).isNotNull();

    assertThat(adjacencyList.getGraphVertexMap().size()).isEqualTo(3);
    assertThat(adjacencyList.getGraphVertexMap().keySet())
        .containsExactlyInAnyOrder(parallelNode2.getUuid(), dummyNode2.getUuid(), dummyNode3.getUuid());

    assertThat(adjacencyList.getAdjacencyMap().get(parallelNode2.getUuid()).getEdges())
        .containsExactlyInAnyOrder(dummyNode2.getUuid());
    assertThat(adjacencyList.getAdjacencyMap().get(parallelNode2.getUuid()).getNextIds())
        .containsExactlyInAnyOrder(dummyNode1.getUuid());

    assertThat(adjacencyList.getAdjacencyMap().get(dummyNode2.getUuid()).getNextIds())
        .containsExactlyInAnyOrder(dummyNode3.getUuid());
    assertThat(adjacencyList.getAdjacencyMap().get(dummyNode2.getUuid()).getEdges()).isEmpty();

    assertThat(adjacencyList.getAdjacencyMap().get(dummyNode3.getUuid()).getNextIds()).isEmpty();
    assertThat(adjacencyList.getAdjacencyMap().get(dummyNode3.getUuid()).getEdges()).isEmpty();
  }
}
