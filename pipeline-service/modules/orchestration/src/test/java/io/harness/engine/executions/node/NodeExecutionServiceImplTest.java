/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.executions.node;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.pms.contracts.execution.Status.ERRORED;
import static io.harness.pms.contracts.execution.Status.FAILED;
import static io.harness.pms.contracts.execution.Status.RUNNING;
import static io.harness.pms.contracts.execution.Status.SUCCEEDED;
import static io.harness.rule.OwnerRule.ALEXEI;
import static io.harness.rule.OwnerRule.ARCHIT;
import static io.harness.rule.OwnerRule.NAMAN;
import static io.harness.rule.OwnerRule.PRASHANT;
import static io.harness.rule.OwnerRule.PRASHANTSHARMA;
import static io.harness.rule.OwnerRule.SAHIL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

import io.harness.OrchestrationTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.engine.OrchestrationTestHelper;
import io.harness.engine.observers.NodeExecutionDeleteObserver;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.NodeExecution;
import io.harness.execution.NodeExecution.NodeExecutionKeys;
import io.harness.observer.Subject;
import io.harness.plan.Node;
import io.harness.plan.PlanNode;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.ExecutionMode;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.NodeProjectionUtils;
import io.harness.rule.Owner;
import io.harness.utils.AmbianceTestUtils;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.joor.Reflect;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.util.CloseableIterator;

@OwnedBy(HarnessTeam.PIPELINE)
public class NodeExecutionServiceImplTest extends OrchestrationTestBase {
  @Mock private Subject<NodeExecutionDeleteObserver> nodeDeleteObserverSubject;
  @Inject @InjectMocks @Spy private NodeExecutionServiceImpl nodeExecutionService;

  @Before
  public void beforeTest() {
    doNothing().when(nodeExecutionService).emitEvent(any(), any());
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldTestSave() {
    String nodeExecutionId = generateUuid();
    NodeExecution nodeExecution =
        NodeExecution.builder()
            .uuid(nodeExecutionId)
            .ambiance(AmbianceTestUtils.buildAmbiance())
            .nodeId(generateUuid())
            .name("name")
            .identifier("dummy")
            .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build())
            .module("CD")
            .startTs(System.currentTimeMillis())
            .status(Status.QUEUED)
            .build();
    NodeExecution savedExecution = nodeExecutionService.save(nodeExecution);
    assertThat(savedExecution.getUuid()).isEqualTo(nodeExecutionId);
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldTestErrorOutNodes() {
    String nodeExecutionId1 = generateUuid();
    String nodeExecutionId2 = generateUuid();
    NodeExecution nodeExecution1 =
        NodeExecution.builder()
            .uuid(nodeExecutionId1)
            .ambiance(AmbianceTestUtils.buildAmbiance())
            .nodeId(generateUuid())
            .name("name")
            .identifier("dummy")
            .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build())
            .module("CD")
            .startTs(System.currentTimeMillis())
            .status(Status.RUNNING)
            .build();
    NodeExecution nodeExecution2 =
        NodeExecution.builder()
            .uuid(nodeExecutionId2)
            .ambiance(AmbianceTestUtils.buildAmbiance())
            .nodeId(generateUuid())
            .name("name")
            .identifier("dummy")
            .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build())
            .module("CD")
            .startTs(System.currentTimeMillis())
            .status(Status.SUCCEEDED)
            .build();
    nodeExecutionService.save(nodeExecution1);
    nodeExecutionService.save(nodeExecution2);

    boolean res = nodeExecutionService.errorOutActiveNodes(AmbianceTestUtils.PLAN_EXECUTION_ID);
    assertThat(res).isTrue();
    NodeExecution ne1 = nodeExecutionService.get(nodeExecutionId1);
    assertThat(ne1).isNotNull();
    assertThat(ne1.getStatus()).isEqualTo(ERRORED);

    NodeExecution ne2 = nodeExecutionService.get(nodeExecutionId2);
    assertThat(ne2).isNotNull();
    assertThat(ne2.getStatus()).isEqualTo(SUCCEEDED);
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldTestExtractChildExecutions() {
    NodeExecutionService service = spy(NodeExecutionServiceImpl.class);
    Ambiance ambiance = Ambiance.newBuilder().setPlanExecutionId(generateUuid()).build();
    NodeExecution pipelineNode =
        NodeExecution.builder().uuid("pipelineNode").status(Status.RUNNING).ambiance(ambiance).version(1L).build();
    NodeExecution stageNode = NodeExecution.builder()
                                  .uuid("stageNode")
                                  .status(Status.RUNNING)
                                  .parentId(pipelineNode.getUuid())
                                  .stepType(StepType.newBuilder().setStepCategory(StepCategory.STEP).build())
                                  .build();
    NodeExecution forkNode = NodeExecution.builder()
                                 .uuid("forkNode")
                                 .status(RUNNING)
                                 .parentId(stageNode.getUuid())
                                 .stepType(StepType.newBuilder().setStepCategory(StepCategory.STEP).build())
                                 .build();
    NodeExecution child1 = NodeExecution.builder()
                               .uuid("child1")
                               .status(Status.RUNNING)
                               .parentId(forkNode.getUuid())
                               .stepType(StepType.newBuilder().setStepCategory(StepCategory.STEP).build())
                               .build();
    NodeExecution child2 = NodeExecution.builder()
                               .uuid("child2")
                               .status(Status.RUNNING)
                               .parentId(forkNode.getUuid())
                               .stepType(StepType.newBuilder().setStepCategory(StepCategory.STEP).build())
                               .build();
    NodeExecution child3 = NodeExecution.builder()
                               .uuid("child3")
                               .status(Status.RUNNING)
                               .parentId(forkNode.getUuid())
                               .stepType(StepType.newBuilder().setStepCategory(StepCategory.STEP).build())
                               .build();

    NodeExecution strategyParent = NodeExecution.builder()
                                       .uuid("strategyParent")
                                       .status(FAILED)
                                       .parentId(stageNode.getUuid())
                                       .stepType(StepType.newBuilder().setStepCategory(StepCategory.STEP).build())
                                       .build();

    NodeExecution strategyNode = NodeExecution.builder()
                                     .uuid("strategyNode")
                                     .status(FAILED)
                                     .parentId(strategyParent.getUuid())
                                     .stepType(StepType.newBuilder().setStepCategory(StepCategory.STRATEGY).build())
                                     .build();

    NodeExecution strategyChildNode = NodeExecution.builder()
                                          .uuid("childNode")
                                          .status(FAILED)
                                          .parentId(strategyNode.getUuid())
                                          .stepType(StepType.newBuilder().setStepCategory(StepCategory.STEP).build())
                                          .build();

    List<NodeExecution> nodeExecutions = Arrays.asList(
        pipelineNode, stageNode, forkNode, child1, child2, child3, strategyNode, strategyChildNode, strategyParent);
    CloseableIterator<NodeExecution> iterator =
        OrchestrationTestHelper.createCloseableIterator(nodeExecutions.iterator());
    doReturn(iterator).when(service).fetchNodeExecutionsWithoutOldRetriesAndStatusInIterator(any(), any(), any());

    List<NodeExecution> stageChildList =
        service.findAllChildrenWithStatusInAndWithoutOldRetries(ambiance.getPlanExecutionId(), stageNode.getUuid(),
            EnumSet.of(Status.RUNNING), true, Collections.emptySet(), false);
    assertThat(stageChildList).isNotEmpty();
    assertThat(stageChildList).hasSize(7);
    assertThat(stageChildList)
        .containsExactlyInAnyOrder(stageNode, forkNode, child1, child2, child3, strategyNode, strategyParent);

    // Iterator cannot be reused again, thus initialise again
    iterator = OrchestrationTestHelper.createCloseableIterator(nodeExecutions.iterator());
    doReturn(iterator).when(service).fetchNodeExecutionsWithoutOldRetriesAndStatusInIterator(any(), any(), any());
    List<NodeExecution> stageChildListWithoutParent =
        service.findAllChildrenWithStatusInAndWithoutOldRetries(ambiance.getPlanExecutionId(), stageNode.getUuid(),
            EnumSet.of(Status.RUNNING), false, Collections.emptySet(), false);
    assertThat(stageChildListWithoutParent).isNotEmpty();
    assertThat(stageChildListWithoutParent).hasSize(6);
    assertThat(stageChildListWithoutParent)
        .containsExactlyInAnyOrder(forkNode, child1, child2, child3, strategyNode, strategyParent);

    // Iterator cannot be reused again, thus initialise again
    iterator = OrchestrationTestHelper.createCloseableIterator(nodeExecutions.iterator());
    doReturn(iterator).when(service).fetchNodeExecutionsWithoutOldRetriesAndStatusInIterator(any(), any(), any());
    List<NodeExecution> forkChildList =
        service.findAllChildrenWithStatusInAndWithoutOldRetries(ambiance.getPlanExecutionId(), forkNode.getUuid(),
            EnumSet.of(Status.RUNNING), true, Collections.emptySet(), false);
    assertThat(forkChildList).isNotEmpty();
    assertThat(forkChildList).hasSize(4);
    assertThat(forkChildList).containsExactlyInAnyOrder(forkNode, child1, child2, child3);

    iterator = OrchestrationTestHelper.createCloseableIterator(nodeExecutions.iterator());
    doReturn(iterator).when(service).fetchNodeExecutionsWithoutOldRetriesAndStatusInIterator(any(), any(), any());
    List<NodeExecution> strategyChildList =
        service.findAllChildrenWithStatusInAndWithoutOldRetries(ambiance.getPlanExecutionId(), stageNode.getUuid(),
            EnumSet.of(Status.RUNNING), true, Collections.emptySet(), true);
    assertThat(strategyChildList).isNotEmpty();
    assertThat(strategyChildList).hasSize(8);
    assertThat(strategyChildList)
        .containsExactlyInAnyOrder(
            forkNode, child1, child2, child3, strategyNode, strategyChildNode, stageNode, strategyParent);

    iterator = OrchestrationTestHelper.createCloseableIterator(nodeExecutions.iterator());
    doReturn(iterator).when(service).fetchNodeExecutionsWithoutOldRetriesAndStatusInIterator(any(), any(), any());
    strategyChildList = service.findAllChildrenWithStatusInAndWithoutOldRetries(ambiance.getPlanExecutionId(),
        stageNode.getUuid(), EnumSet.of(Status.RUNNING), true, Collections.emptySet(), false);
    assertThat(strategyChildList).isNotEmpty();
    assertThat(strategyChildList).hasSize(7);
    assertThat(strategyChildList)
        .containsExactlyInAnyOrder(forkNode, child1, child2, child3, strategyNode, strategyParent, stageNode);
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldTestGetByPlanNodeUuidShouldThrowInvalidRequestException() {
    assertThatThrownBy(() -> nodeExecutionService.getByPlanNodeUuid(generateUuid(), generateUuid()))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Node Execution is null for planNodeUuid: ");
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldTestGetByPlanNodeUuid() {
    String nodeExecutionId = generateUuid();
    String planNodeUuid = generateUuid();
    String planExecutionUuid = generateUuid();
    NodeExecution nodeExecution =
        NodeExecution.builder()
            .uuid(nodeExecutionId)
            .ambiance(Ambiance.newBuilder().setPlanExecutionId(planExecutionUuid).build())
            .startTs(System.currentTimeMillis())
            .nodeId(planNodeUuid)
            .name("name")
            .identifier("dummy")
            .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build())
            .module("CD")
            .status(Status.SUCCEEDED)
            .build();
    nodeExecutionService.save(nodeExecution);

    NodeExecution found = nodeExecutionService.getByPlanNodeUuid(planNodeUuid, planExecutionUuid);
    assertThat(found).isNotNull();

    assertThat(found.getUuid()).isEqualTo(nodeExecutionId);
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldTestMarkLeavesDiscontinuing() {
    String planExecutionUuid = generateUuid();
    String parentId = generateUuid();
    NodeExecution nodeExecution =
        NodeExecution.builder()
            .uuid(generateUuid())
            .parentId(parentId)
            .ambiance(Ambiance.newBuilder().setPlanExecutionId(planExecutionUuid).build())
            .uuid(generateUuid())
            .name("name")
            .identifier(generateUuid())
            .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build())
            .module("CD")
            .startTs(System.currentTimeMillis())
            .status(Status.RUNNING)
            .build();
    NodeExecution nodeExecution1 =
        NodeExecution.builder()
            .uuid(generateUuid())
            .parentId(parentId)
            .ambiance(Ambiance.newBuilder().setPlanExecutionId(planExecutionUuid).build())
            .uuid(generateUuid())
            .name("name")
            .identifier(generateUuid())
            .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build())
            .module("CD")
            .startTs(System.currentTimeMillis())
            .status(Status.RUNNING)
            .build();
    nodeExecutionService.save(nodeExecution);
    nodeExecutionService.save(nodeExecution1);

    long updatedNumber = nodeExecutionService.markLeavesDiscontinuing(
        ImmutableList.of(nodeExecution.getUuid(), nodeExecution1.getUuid()));
    assertThat(updatedNumber).isEqualTo(2);
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldTestMarkAllLeavesDiscontinuing() {
    String planExecutionUuid = generateUuid();
    String parentId = generateUuid();
    NodeExecution nodeExecution =
        NodeExecution.builder()
            .uuid(generateUuid())
            .parentId(parentId)
            .ambiance(Ambiance.newBuilder().setPlanExecutionId(planExecutionUuid).build())
            .mode(ExecutionMode.SYNC)
            .uuid(generateUuid())
            .name("name")
            .identifier(generateUuid())
            .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build())
            .module("CD")
            .startTs(System.currentTimeMillis())
            .status(Status.RUNNING)
            .build();
    NodeExecution nodeExecution1 =
        NodeExecution.builder()
            .uuid(generateUuid())
            .parentId(parentId)
            .ambiance(Ambiance.newBuilder().setPlanExecutionId(planExecutionUuid).build())
            .mode(ExecutionMode.SYNC)
            .uuid(generateUuid())
            .name("name")
            .identifier(generateUuid())
            .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build())
            .module("CD")
            .startTs(System.currentTimeMillis())
            .status(Status.RUNNING)
            .build();
    NodeExecution nodeExecution2 =
        NodeExecution.builder()
            .uuid(generateUuid())
            .parentId(parentId)
            .ambiance(Ambiance.newBuilder().setPlanExecutionId(planExecutionUuid).build())
            .uuid(generateUuid())
            .name("name")
            .identifier(generateUuid())
            .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build())
            .module("CD")
            .startTs(System.currentTimeMillis())
            .status(Status.QUEUED)
            .build();
    nodeExecutionService.save(nodeExecution);
    nodeExecutionService.save(nodeExecution1);
    nodeExecutionService.save(nodeExecution2);

    long updatedNumber =
        nodeExecutionService.markAllLeavesAndQueuedNodesDiscontinuing(planExecutionUuid, EnumSet.of(Status.RUNNING));
    assertThat(updatedNumber).isEqualTo(3);
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void shouldTestFetchChildrenNodeExecutionsIterator() {
    String planExecutionUuid = generateUuid();
    String parentId = generateUuid();
    NodeExecution nodeExecution =
        NodeExecution.builder()
            .uuid(generateUuid())
            .parentId(parentId)
            .ambiance(Ambiance.newBuilder().setPlanExecutionId(planExecutionUuid).build())
            .mode(ExecutionMode.SYNC)
            .uuid(generateUuid())
            .name("name")
            .identifier(generateUuid())
            .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build())
            .module("CD")
            .startTs(System.currentTimeMillis())
            .status(Status.RUNNING)
            .build();
    NodeExecution nodeExecution1 =
        NodeExecution.builder()
            .uuid(generateUuid())
            .parentId(parentId)
            .ambiance(Ambiance.newBuilder().setPlanExecutionId(planExecutionUuid).build())
            .mode(ExecutionMode.SYNC)
            .uuid(generateUuid())
            .name("name")
            .identifier(generateUuid())
            .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build())
            .module("CD")
            .startTs(System.currentTimeMillis())
            .status(Status.RUNNING)
            .build();
    nodeExecutionService.save(nodeExecution);
    nodeExecutionService.save(nodeExecution1);

    List<NodeExecution> nodeExecutions = new LinkedList<>();
    try (CloseableIterator<NodeExecution> iterator = nodeExecutionService.fetchChildrenNodeExecutionsIterator(
             parentId, NodeProjectionUtils.fieldsForResponseNotifyData)) {
      while (iterator.hasNext()) {
        nodeExecutions.add(iterator.next());
      }
    }
    assertThat(nodeExecutions).isNotEmpty();

    assertThat(nodeExecutions.size()).isEqualTo(2);
    assertThat(nodeExecutions)
        .extracting(NodeExecution::getUuid)
        .containsExactlyInAnyOrder(nodeExecution.getUuid(), nodeExecution1.getUuid());
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void test() {
    // stageFqn
    String stage1Fqn = "pipeline.stages.stage1";
    String stage2Fqn = "pipeline.stages.stage2";
    String stage3Fqn = "pipeline.stages.stage3";

    String planExecutionUuid = generateUuid();
    String parentId = generateUuid();
    String nodeUuid = generateUuid();
    String nodeExecutionUuid = generateUuid();

    // Node execution of type other than stage
    NodeExecution nodeExecution1 =
        NodeExecution.builder()
            .uuid(nodeExecutionUuid)
            .parentId(parentId)
            .ambiance(Ambiance.newBuilder().setPlanExecutionId(planExecutionUuid).build())
            .planNode(PlanNode.builder()
                          .uuid(nodeUuid)
                          .name("name")
                          .identifier("stage1")
                          .stageFqn(stage1Fqn)
                          .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build())
                          .serviceName("CD")
                          .build())
            .status(Status.RUNNING)
            .nodeId(nodeUuid)
            .name("name")
            .identifier("stage1")
            .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build())
            .module("CD")
            .build();
    String nodeUuid2 = generateUuid();
    String nodeExecutionUuid2 = generateUuid();
    // Node execution of type stage
    NodeExecution nodeExecution2 =
        NodeExecution.builder()
            .uuid(nodeExecutionUuid2)
            .parentId(parentId)
            .ambiance(Ambiance.newBuilder().setPlanExecutionId(planExecutionUuid).build())
            .planNode(PlanNode.builder()
                          .uuid(nodeUuid2)
                          .name("name")
                          .identifier("stage2")
                          .stageFqn(stage2Fqn)
                          .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STAGE).build())
                          .serviceName("CD")
                          .build())
            .status(Status.RUNNING)
            .nodeId(nodeUuid2)
            .name("name")
            .identifier("stage2")
            .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STAGE).build())
            .module("CD")
            .build();

    String nodeUuid3 = generateUuid();
    String nodeExecutionUuid3 = generateUuid();
    // Node execution of type stage
    NodeExecution nodeExecution3 =
        NodeExecution.builder()
            .uuid(nodeExecutionUuid3)
            .parentId(parentId)
            .ambiance(Ambiance.newBuilder().setPlanExecutionId(planExecutionUuid).build())
            .planNode(PlanNode.builder()
                          .uuid(nodeUuid3)
                          .name("name")
                          .stageFqn(stage3Fqn)
                          .identifier("stage3")
                          .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STAGE).build())
                          .serviceName("CD")
                          .build())
            .status(Status.RUNNING)
            .nodeId(nodeUuid3)
            .name("name")
            .identifier("stage3")
            .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STAGE).build())
            .module("CD")
            .build();

    // saving nodeExecution
    nodeExecutionService.save(nodeExecution1);
    nodeExecutionService.save(nodeExecution2);
    nodeExecutionService.save(nodeExecution3);

    List<String> stageFqns = nodeExecutionService.fetchStageFqnFromStageIdentifiers(
        planExecutionUuid, Arrays.asList("stage1", "stage2", "stage3"));
    assertThat(stageFqns.size()).isEqualTo(2);
    assertThat(stageFqns).contains(stage2Fqn);
    assertThat(stageFqns).contains(stage3Fqn);
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testMapNodeExecutionIdWithPlanNodeForGivenStageFQN() {
    // stageFqn
    String stage1Fqn = "pipeline.stages.stage1";
    String stage2Fqn = "pipeline.stages.stage2";
    String stage3Fqn = "pipeline.stages.stage3";

    String planExecutionUuid = generateUuid();
    String parentId = generateUuid();
    String nodeUuid = generateUuid();
    String nodeExecutionUuid = generateUuid();

    // Node execution of type other than stage
    NodeExecution nodeExecution1 =
        NodeExecution.builder()
            .uuid(nodeExecutionUuid)
            .parentId(parentId)
            .ambiance(Ambiance.newBuilder().setPlanExecutionId(planExecutionUuid).build())
            .planNode(PlanNode.builder()
                          .uuid(nodeUuid)
                          .name("name")
                          .identifier("stage1")
                          .stageFqn(stage1Fqn)
                          .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build())
                          .serviceName("CD")
                          .build())
            .status(Status.RUNNING)
            .nodeId(nodeUuid)
            .name("name")
            .oldRetry(false)
            .identifier("stage1")
            .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build())
            .startTs(500L)
            .module("CD")
            .build();

    String nodeExecutionUuid12 = generateUuid();
    NodeExecution nodeExecution12 =
        NodeExecution.builder()
            .uuid(nodeExecutionUuid12)
            .parentId(parentId)
            .ambiance(Ambiance.newBuilder().setPlanExecutionId(planExecutionUuid).build())
            .planNode(PlanNode.builder()
                          .uuid(nodeUuid)
                          .name("name")
                          .identifier("stage1")
                          .stageFqn(stage1Fqn)
                          .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build())
                          .serviceName("CD")
                          .build())
            .status(Status.RUNNING)
            .nodeId(nodeUuid)
            .name("name")
            .identifier("stage1")
            .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build())
            .module("CD")
            .oldRetry(true)
            .startTs(200L)
            .build();
    String nodeUuid2 = generateUuid();
    String nodeExecutionUuid2 = generateUuid();
    // Node execution of type stage
    NodeExecution nodeExecution2 =
        NodeExecution.builder()
            .uuid(nodeExecutionUuid2)
            .parentId(parentId)
            .ambiance(Ambiance.newBuilder().setPlanExecutionId(planExecutionUuid).build())
            .planNode(PlanNode.builder()
                          .uuid(nodeUuid2)
                          .name("name")
                          .identifier("stage2")
                          .stageFqn(stage2Fqn)
                          .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STAGE).build())
                          .serviceName("CD")
                          .build())
            .status(Status.RUNNING)
            .nodeId(nodeUuid2)
            .name("name")
            .identifier("stage2")
            .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STAGE).build())
            .oldRetry(false)
            .startTs(400L)
            .module("CD")
            .build();

    String nodeUuid3 = generateUuid();
    String nodeExecutionUuid3 = generateUuid();
    // Node execution of type stage
    NodeExecution nodeExecution3 =
        NodeExecution.builder()
            .uuid(nodeExecutionUuid3)
            .parentId(parentId)
            .ambiance(Ambiance.newBuilder().setPlanExecutionId(planExecutionUuid).build())
            .planNode(PlanNode.builder()
                          .uuid(nodeUuid3)
                          .name("name")
                          .stageFqn(stage3Fqn)
                          .identifier("stage3")
                          .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STAGE).build())
                          .serviceName("CD")
                          .build())
            .status(Status.RUNNING)
            .nodeId(nodeUuid3)
            .name("name")
            .startTs(500L)
            .identifier("stage3")
            .oldRetry(false)
            .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STAGE).build())
            .module("CD")
            .build();

    // saving nodeExecution
    nodeExecutionService.save(nodeExecution1);
    nodeExecutionService.save(nodeExecution12);
    nodeExecutionService.save(nodeExecution2);
    nodeExecutionService.save(nodeExecution3);

    Map<String, Node> uuidNodeMap = nodeExecutionService.mapNodeExecutionIdWithPlanNodeForGivenStageFQN(
        planExecutionUuid, Arrays.asList(stage1Fqn, stage2Fqn, stage3Fqn));
    // Total 4 nodeExecutions but only 3 will be returned. Because nodeExecution1 and nodeExecution12 correspond to same
    // planNode.
    assertThat(uuidNodeMap.size()).isEqualTo(3);

    assertThat(uuidNodeMap.containsKey(nodeExecutionUuid)).isEqualTo(true);
    assertThat(uuidNodeMap.get(nodeExecutionUuid).getIdentifier()).isEqualTo("stage1");

    // nodeExecutionUuid12 will not be present because nodeExecutionUuid12 and nodeExecutionUuid12 both belongs to same
    // PlanNode. And nodeExecutionUuid is final nodeExecution later startTs.
    assertThat(uuidNodeMap.containsKey(nodeExecutionUuid12)).isFalse();

    assertThat(uuidNodeMap.containsKey(nodeExecutionUuid2)).isEqualTo(true);
    assertThat(uuidNodeMap.get(nodeExecutionUuid2).getIdentifier()).isEqualTo("stage2");

    assertThat(uuidNodeMap.containsKey(nodeExecutionUuid3)).isEqualTo(true);
    assertThat(uuidNodeMap.get(nodeExecutionUuid3).getIdentifier()).isEqualTo("stage3");
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testShouldLog() {
    Update update = new Update();
    update.addToSet(NodeExecutionKeys.executableResponses, null);
    update.set(NodeExecutionKeys.failureInfo, FailureInfo.newBuilder().build());
    assertThat(nodeExecutionService.shouldLog(update)).isTrue();
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testShouldNotLog() {
    Update update = new Update();
    update.set(NodeExecutionKeys.nodeId, "test");
    assertThat(nodeExecutionService.shouldLog(update)).isFalse();
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testDeleteAllNodeExecutionAndMetadata() {
    MongoTemplate mongoTemplateMock = Mockito.mock(MongoTemplate.class);
    Reflect.on(nodeExecutionService).set("mongoTemplate", mongoTemplateMock);
    Reflect.on(nodeExecutionService).set("nodeDeleteObserverSubject", nodeDeleteObserverSubject);

    List<NodeExecution> nodeExecutionList = new LinkedList<>();
    Set<String> batchNodeExecutionIds = new HashSet<>();
    for (int i = 0; i < 1200; i++) {
      String uuid = generateUuid();
      nodeExecutionList.add(NodeExecution.builder().uuid(uuid).build());
      batchNodeExecutionIds.add(uuid);
    }
    CloseableIterator<NodeExecution> iterator =
        OrchestrationTestHelper.createCloseableIterator(nodeExecutionList.iterator());
    doReturn(iterator)
        .when(nodeExecutionService)
        .fetchNodeExecutionsFromAnalytics("EXECUTION_1", NodeProjectionUtils.fieldsForNodeExecutionDelete);
    nodeExecutionService.deleteAllNodeExecutionAndMetadata("EXECUTION_1");
    verify(nodeDeleteObserverSubject, times(2)).fireInform(any(), any());

    verify(mongoTemplateMock, times(1))
        .remove(query(where(NodeExecutionKeys.id).in(batchNodeExecutionIds)), NodeExecution.class);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testFetchNodeExecutionsForGivenStageFQNs() {
    NodeExecutionReadHelper nodeExecutionReadHelperMock = Mockito.mock(NodeExecutionReadHelper.class);
    Reflect.on(nodeExecutionService).set("nodeExecutionReadHelper", nodeExecutionReadHelperMock);
    List<NodeExecution> nodeExecutionList = new LinkedList<>();
    for (int i = 0; i < 1200; i++) {
      String uuid = generateUuid();
      nodeExecutionList.add(NodeExecution.builder().uuid(uuid).build());
    }
    CloseableIterator<NodeExecution> iterator =
        OrchestrationTestHelper.createCloseableIterator(nodeExecutionList.iterator());
    String planExecutionId = "planId";
    List<String> stageFQNs = Collections.singletonList("s1");
    Criteria criteria = Criteria.where(NodeExecutionKeys.planExecutionId)
                            .is(planExecutionId)
                            .and(NodeExecutionKeys.stageFqn)
                            .in(stageFQNs);
    Query query = query(criteria);
    query.fields().include("node");
    doReturn(iterator).when(nodeExecutionReadHelperMock).fetchNodeExecutions(query);
    CloseableIterator<NodeExecution> fetchedIterator = nodeExecutionService.fetchNodeExecutionsForGivenStageFQNs(
        planExecutionId, stageFQNs, Collections.singletonList("node"));
    assertThat(fetchedIterator).isEqualTo(iterator);
  }
}
