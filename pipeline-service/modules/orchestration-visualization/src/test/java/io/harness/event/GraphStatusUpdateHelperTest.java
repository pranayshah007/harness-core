/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.event;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.pms.contracts.execution.Status.APPROVAL_WAITING;
import static io.harness.pms.contracts.execution.Status.FAILED;
import static io.harness.pms.contracts.execution.Status.INTERVENTION_WAITING;
import static io.harness.pms.contracts.execution.Status.RUNNING;
import static io.harness.pms.contracts.execution.Status.SUCCEEDED;
import static io.harness.rule.OwnerRule.ALEXEI;
import static io.harness.rule.OwnerRule.ARCHIT;
import static io.harness.rule.OwnerRule.BRIJESH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.OrchestrationVisualizationTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.GraphVertex;
import io.harness.beans.OrchestrationGraph;
import io.harness.beans.internal.EdgeListInternal;
import io.harness.beans.internal.OrchestrationAdjacencyListInternal;
import io.harness.cache.SpringMongoStore;
import io.harness.category.element.UnitTests;
import io.harness.data.OutcomeInstance;
import io.harness.engine.events.OrchestrationEventEmitter;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.engine.executions.plan.PlanExecutionMetadataService;
import io.harness.engine.executions.plan.PlanExecutionService;
import io.harness.engine.utils.PmsLevelUtils;
import io.harness.execution.NodeExecution;
import io.harness.execution.PlanExecution;
import io.harness.execution.PlanExecutionMetadata;
import io.harness.interrupts.InterruptEffect;
import io.harness.plan.NodeType;
import io.harness.plan.PlanNode;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.ExecutionMode;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.steps.SkipType;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.data.PmsOutcome;
import io.harness.pms.data.stepparameters.PmsStepParameters;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.serializer.recaster.RecastOrchestrationUtils;
import io.harness.rule.Owner;
import io.harness.service.GraphGenerationService;
import io.harness.utils.DummyVisualizationOutcome;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.assertj.core.util.Maps;
import org.awaitility.Awaitility;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.springframework.data.mongodb.core.MongoTemplate;

@OwnedBy(HarnessTeam.PIPELINE)
public class GraphStatusUpdateHelperTest extends OrchestrationVisualizationTestBase {
  @Inject private PlanExecutionService planExecutionService;
  @Inject private SpringMongoStore mongoStore;
  @Mock private PlanExecutionMetadataService planExecutionMetadataService;
  @Inject @InjectMocks private NodeExecutionService nodeExecutionService;
  @Inject @Spy private GraphGenerationService graphGenerationService;
  @Inject private MongoTemplate mongoTemplate;
  @Inject private GraphStatusUpdateHelper eventHandlerV2;

  @Mock private OrchestrationEventEmitter eventEmitter;

  @Before
  public void setup() {
    doNothing().when(eventEmitter).emitEvent(any());
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)

  public void shouldDoNothingIfRuntimeIdIsNull() {
    String planExecutionId = generateUuid();
    eventHandlerV2.handleEvent(planExecutionId, null, OrchestrationGraph.builder().build());

    verify(graphGenerationService, never()).getCachedOrchestrationGraph(planExecutionId);
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)

  @Ignore("Using event sourcing now, will remove/update")
  public void shouldAddRootNodeIdToTheGraphAndAddVertex() {
    when(planExecutionMetadataService.findByPlanExecutionId(any()))
        .thenReturn(Optional.of(PlanExecutionMetadata.builder().build()));

    // creating PlanExecution
    PlanExecution planExecution =
        PlanExecution.builder().uuid(generateUuid()).startTs(System.currentTimeMillis()).status(Status.RUNNING).build();
    planExecutionService.save(planExecution);

    // creating NodeExecution
    NodeExecution dummyStart =
        NodeExecution.builder()
            .uuid(generateUuid())
            .ambiance(Ambiance.newBuilder().setPlanExecutionId(planExecution.getUuid()).build())
            .mode(ExecutionMode.SYNC)
            .planNode(PlanNode.builder()
                          .uuid(generateUuid())
                          .name("name")
                          .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build())
                          .identifier("identifier1")
                          .build())
            .status(Status.QUEUED)
            .build();
    nodeExecutionService.save(dummyStart);

    // creating cached graph
    OrchestrationGraph cachedGraph = OrchestrationGraph.builder()
                                         .cacheKey(planExecution.getUuid())
                                         .cacheParams(null)
                                         .cacheContextOrder(System.currentTimeMillis())
                                         .adjacencyList(OrchestrationAdjacencyListInternal.builder()
                                                            .graphVertexMap(new HashMap<>())
                                                            .adjacencyMap(new HashMap<>())
                                                            .build())
                                         .planExecutionId(planExecution.getUuid())
                                         .rootNodeIds(new ArrayList<>())
                                         .startTs(planExecution.getStartTs())
                                         .endTs(planExecution.getEndTs())
                                         .status(planExecution.getStatus())
                                         .build();
    mongoStore.upsert(cachedGraph, Duration.ofDays(10));

    OrchestrationGraph updatedGraph =
        eventHandlerV2.handleEvent(planExecution.getUuid(), dummyStart.getUuid(), cachedGraph);

    Awaitility.await().atMost(2, TimeUnit.SECONDS).pollInterval(500, TimeUnit.MILLISECONDS).until(() -> {
      OrchestrationGraph graphInternal = graphGenerationService.getCachedOrchestrationGraph(planExecution.getUuid());
      return !graphInternal.getRootNodeIds().isEmpty();
    });

    assertThat(updatedGraph).isNotNull();
    assertThat(updatedGraph.getPlanExecutionId()).isEqualTo(planExecution.getUuid());
    assertThat(updatedGraph.getStartTs()).isEqualTo(planExecution.getStartTs());
    assertThat(updatedGraph.getEndTs()).isNull();
    assertThat(updatedGraph.getRootNodeIds()).containsExactlyInAnyOrder(dummyStart.getUuid());
    assertThat(updatedGraph.getAdjacencyList().getGraphVertexMap().size()).isEqualTo(1);
    assertThat(updatedGraph.getAdjacencyList().getAdjacencyMap().size()).isEqualTo(1);
    assertThat(updatedGraph.getStatus()).isEqualTo(planExecution.getStatus());
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)

  public void shouldUpdateExistingVertexInGraphAndAddOutcomes() {
    when(planExecutionMetadataService.findByPlanExecutionId(any()))
        .thenReturn(Optional.of(PlanExecutionMetadata.builder().build()));

    // creating PlanExecution
    PlanExecution planExecution =
        PlanExecution.builder().uuid(generateUuid()).startTs(System.currentTimeMillis()).status(Status.RUNNING).build();
    planExecutionService.save(planExecution);

    // creating NodeExecution
    StepType stepType = StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build();
    PlanNode planNode = PlanNode.builder()
                            .uuid(generateUuid())
                            .name("name")
                            .stepType(stepType)
                            .identifier("identifier1")
                            .serviceName("PIPELINE")
                            .build();
    NodeExecution dummyStart = NodeExecution.builder()
                                   .uuid(generateUuid())
                                   .ambiance(Ambiance.newBuilder()
                                                 .setPlanExecutionId(planExecution.getUuid())
                                                 .addLevels(Level.newBuilder()
                                                                .setStepType(stepType)
                                                                .setNodeType(NodeType.PLAN_NODE.name())
                                                                .setSetupId(planNode.getUuid())
                                                                .build())
                                                 .build())
                                   .mode(ExecutionMode.SYNC)
                                   .status(SUCCEEDED)
                                   .nodeId(planNode.getUuid())
                                   .name(planNode.getName())
                                   .resolvedParams(PmsStepParameters.parse(new HashMap<>()))
                                   .stepType(planNode.getStepType())
                                   .identifier(planNode.getIdentifier())
                                   .module(planNode.getServiceName())
                                   .skipGraphType(planNode.getSkipGraphType())
                                   .build();
    nodeExecutionService.save(dummyStart);

    // creating cached graph
    OrchestrationGraph cachedGraph =
        OrchestrationGraph.builder()
            .cacheKey(planExecution.getUuid())
            .cacheParams(null)
            .cacheContextOrder(System.currentTimeMillis())
            .adjacencyList(
                OrchestrationAdjacencyListInternal.builder()
                    .graphVertexMap(
                        Maps.newHashMap(dummyStart.getUuid(), convertNodeExecutionWithStatusSucceeded(dummyStart)))
                    .adjacencyMap(Maps.newHashMap(dummyStart.getUuid(),
                        EdgeListInternal.builder().edges(new ArrayList<>()).nextIds(new ArrayList<>()).build()))
                    .build())
            .planExecutionId(planExecution.getUuid())
            .rootNodeIds(Lists.newArrayList(dummyStart.getUuid()))
            .startTs(planExecution.getStartTs())
            .endTs(planExecution.getEndTs())
            .status(planExecution.getStatus())
            .build();
    mongoStore.upsert(cachedGraph, Duration.ofDays(10));

    // creating outcome
    DummyVisualizationOutcome dummyVisualizationOutcome = new DummyVisualizationOutcome("outcome");
    Map<String, Object> doc = RecastOrchestrationUtils.toMap(dummyVisualizationOutcome);
    OutcomeInstance outcome = OutcomeInstance.builder()
                                  .planExecutionId(planExecution.getUuid())
                                  .producedBy(PmsLevelUtils.buildLevelFromNode(dummyStart.getUuid(), planNode))
                                  .createdAt(System.currentTimeMillis())
                                  .outcomeValue(PmsOutcome.parse(doc))
                                  .build();
    mongoTemplate.insert(outcome);

    OrchestrationGraph updatedGraph =
        eventHandlerV2.handleEvent(planExecution.getUuid(), dummyStart.getUuid(), cachedGraph);

    assertThat(updatedGraph).isNotNull();
    assertThat(updatedGraph.getPlanExecutionId()).isEqualTo(planExecution.getUuid());
    assertThat(updatedGraph.getStartTs()).isEqualTo(planExecution.getStartTs());
    assertThat(updatedGraph.getEndTs()).isNull();
    assertThat(updatedGraph.getRootNodeIds()).containsExactlyInAnyOrder(dummyStart.getUuid());

    Map<String, GraphVertex> graphVertexMap = updatedGraph.getAdjacencyList().getGraphVertexMap();
    assertThat(graphVertexMap.size()).isEqualTo(1);
    assertThat(graphVertexMap.get(dummyStart.getUuid()).getStatus()).isEqualTo(SUCCEEDED);
    assertThat(graphVertexMap.get(dummyStart.getUuid()).getOutcomeDocuments().values())
        .containsExactlyInAnyOrder(PmsOutcome.parse(RecastOrchestrationUtils.toMap(dummyVisualizationOutcome)));
    assertThat(updatedGraph.getAdjacencyList().getAdjacencyMap().size()).isEqualTo(1);
    assertThat(updatedGraph.getStatus()).isEqualTo(planExecution.getStatus());
  }

  private GraphVertex convertNodeExecutionWithStatusSucceeded(NodeExecution nodeExecution) {
    Level level = AmbianceUtils.obtainCurrentLevel(nodeExecution.getAmbiance());
    return GraphVertex.builder()
        .uuid(nodeExecution.getUuid())
        .planNodeId(level.getSetupId())
        .name(nodeExecution.getName())
        .startTs(nodeExecution.getStartTs())
        .endTs(nodeExecution.getEndTs())
        .initialWaitDuration(nodeExecution.getInitialWaitDuration())
        .lastUpdatedAt(nodeExecution.getLastUpdatedAt())
        .stepType(level.getStepType().getType())
        .status(SUCCEEDED)
        .failureInfo(nodeExecution.getFailureInfo())
        .stepParameters(nodeExecution.getPmsStepParameters())
        .mode(nodeExecution.getMode())
        .interruptHistories(nodeExecution.getInterruptHistories())
        .retryIds(nodeExecution.getRetryIds())
        .build();
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testIsOutcomeUpdateGraphStatus() {
    boolean outcomeUpdateGraphStatus = eventHandlerV2.isOutcomeUpdateGraphStatus(Status.SUCCEEDED);
    assertThat(outcomeUpdateGraphStatus).isTrue();

    outcomeUpdateGraphStatus = eventHandlerV2.isOutcomeUpdateGraphStatus(FAILED);
    assertThat(outcomeUpdateGraphStatus).isTrue();

    outcomeUpdateGraphStatus = eventHandlerV2.isOutcomeUpdateGraphStatus(INTERVENTION_WAITING);
    assertThat(outcomeUpdateGraphStatus).isTrue();

    outcomeUpdateGraphStatus = eventHandlerV2.isOutcomeUpdateGraphStatus(APPROVAL_WAITING);
    assertThat(outcomeUpdateGraphStatus).isTrue();

    outcomeUpdateGraphStatus = eventHandlerV2.isOutcomeUpdateGraphStatus(RUNNING);
    assertThat(outcomeUpdateGraphStatus).isFalse();
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testConvertFromNodeExecution() {
    NodeExecution nodeExecution =
        NodeExecution.builder()
            .uuid(generateUuid())
            .ambiance(Ambiance.newBuilder()
                          .setPlanExecutionId("plaExecutionId")
                          .addLevels(Level.newBuilder()
                                         .setStepType(StepType.newBuilder().setType("http").build())
                                         .setNodeType(NodeType.PLAN_NODE.name())
                                         .setSetupId("planNodeUuid")
                                         .build())
                          .build())
            .mode(ExecutionMode.SYNC)
            .status(SUCCEEDED)
            .nodeId("planNodeUuid")
            .name("planNodeName")
            .resolvedParams(PmsStepParameters.parse(new HashMap<>()))
            .stepType(StepType.newBuilder().setType("Http").build())
            .lastUpdatedAt(100L)
            .failureInfo(FailureInfo.newBuilder().build())
            .interruptHistory(InterruptEffect.builder().build())
            .retryIds(Collections.emptyList())
            .identifier("planNodeId")
            .module("PMS")
            .resolvedParams(PmsStepParameters.parse(Map.of("AA", "BB")))
            .skipGraphType(SkipType.SKIP_TREE)
            .build();

    GraphVertex prevValue = GraphVertex.builder().stepParameters(PmsStepParameters.parse(Map.of("a", "b"))).build();
    GraphVertex newValue = eventHandlerV2.convertFromNodeExecution(prevValue, nodeExecution);

    assertThat(newValue.getIdentifier())
        .isEqualTo(AmbianceUtils.obtainCurrentLevel(nodeExecution.getAmbiance()).getIdentifier());
    assertThat(newValue.getUuid()).isEqualTo(nodeExecution.getUuid());
    assertThat(newValue.getStartTs()).isEqualTo(nodeExecution.getStartTs());
    assertThat(newValue.getAmbiance()).isEqualTo(nodeExecution.getAmbiance());
    assertThat(newValue.getStepType())
        .isEqualTo(AmbianceUtils.obtainCurrentLevel(nodeExecution.getAmbiance()).getStepType().getType());
    assertThat(newValue.getStatus()).isEqualTo(nodeExecution.getStatus());
    assertThat(newValue.getNodeRunInfo()).isEqualTo(nodeExecution.getNodeRunInfo());
    assertThat(newValue.getMode()).isEqualTo(nodeExecution.getMode());
    assertThat(newValue.getInterruptHistories()).isEqualTo(nodeExecution.getInterruptHistories());
    assertThat(newValue.getRetryIds()).isEqualTo(nodeExecution.getRetryIds());
    assertThat(newValue.getExecutableResponses()).isEqualTo(nodeExecution.getExecutableResponses());
    assertThat(newValue.getProgressData()).isEqualTo(nodeExecution.getPmsProgressData());
    assertThat(newValue.getFailureInfo()).isEqualTo(nodeExecution.getFailureInfo());
    assertThat(newValue.getLastUpdatedAt()).isEqualTo(nodeExecution.getLastUpdatedAt());
    assertThat(newValue.getRetryIds()).isEqualTo(nodeExecution.getRetryIds());
    assertThat(newValue.getRetryIds()).isEqualTo(nodeExecution.getRetryIds());

    // Since prevValue.getStepParameters is not null. It will remain same.
    assertThat(newValue.getStepParameters()).isEqualTo(prevValue.getStepParameters());
    assertThat(newValue.getStepParameters()).isNotEqualTo(nodeExecution.getResolvedParams());

    prevValue = GraphVertex.builder().build();
    newValue = eventHandlerV2.convertFromNodeExecution(prevValue, nodeExecution);

    // Since prevValue.getStepParameters is null. it will be set from nodeExecution.
    assertThat(newValue.getStepParameters()).isNotEqualTo(prevValue.getStepParameters());
    assertThat(newValue.getStepParameters()).isEqualTo(nodeExecution.getResolvedParams());
  }
}
