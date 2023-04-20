/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.interrupts;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.interrupts.Interrupt.State.PROCESSED_UNSUCCESSFULLY;
import static io.harness.rule.OwnerRule.PRASHANT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.OrchestrationTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.engine.OrchestrationTestHelper;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.engine.executions.plan.PlanExecutionService;
import io.harness.engine.interrupts.helpers.AbortHelper;
import io.harness.execution.NodeExecution;
import io.harness.execution.PlanExecution;
import io.harness.interrupts.Interrupt;
import io.harness.pms.contracts.execution.ExecutionMode;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.interrupts.InterruptType;
import io.harness.pms.contracts.steps.SkipType;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.NodeProjectionUtils;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.data.mapping.model.MappingInstantiationException;
import org.springframework.data.util.CloseableIterator;

@OwnedBy(HarnessTeam.PIPELINE)
public class InterruptMonitorTest extends OrchestrationTestBase {
  @Mock private PlanExecutionService planExecutionService;
  @Mock private NodeExecutionService nodeExecutionService;
  @Mock private InterruptService interruptService;
  @Mock private AbortHelper abortHelper;

  @Inject @InjectMocks private InterruptMonitor interruptMonitor;

  /**
   * Setup for the test
   * pipeline (running)
   *    stages (running)
   *      stage (running)
   *        execution (running)
   *          step (failed)
   *
   * And now the abort interrupt is registered
   */
  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldTestExecutionStuck() {
    String planExecutionId = generateUuid();
    PlanExecution planExecution = PlanExecution.builder().uuid(planExecutionId).status(Status.RUNNING).build();

    Interrupt interrupt = Interrupt.builder()
                              .uuid(generateUuid())
                              .planExecutionId(planExecutionId)
                              .type(InterruptType.ABORT_ALL)
                              .state(Interrupt.State.PROCESSING)
                              .build();
    NodeExecution pipeline =
        NodeExecution.builder()
            .uuid(generateUuid() + "_pipeline")
            .status(Status.RUNNING)
            .mode(ExecutionMode.CHILD)
            .skipGraphType(SkipType.NOOP)
            .stageFqn(generateUuid())
            .group("STEP")
            .module("CD")
            .name(generateUuid())
            .identifier(generateUuid())
            .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build())
            .nodeId(generateUuid())
            .build();
    NodeExecution stages =
        NodeExecution.builder()
            .uuid(generateUuid() + "_stages")
            .status(Status.RUNNING)
            .mode(ExecutionMode.CHILD)
            .parentId(pipeline.getUuid())
            .skipGraphType(SkipType.NOOP)
            .stageFqn(generateUuid())
            .group("STEP")
            .module("CD")
            .name(generateUuid())
            .identifier(generateUuid())
            .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build())
            .nodeId(generateUuid())
            .build();
    NodeExecution stage =
        NodeExecution.builder()
            .uuid(generateUuid() + "_stage")
            .status(Status.RUNNING)
            .mode(ExecutionMode.CHILD)
            .parentId(stages.getUuid())
            .skipGraphType(SkipType.NOOP)
            .stageFqn(generateUuid())
            .group("STEP")
            .module("CD")
            .name(generateUuid())
            .identifier(generateUuid())
            .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build())
            .nodeId(generateUuid())
            .build();
    NodeExecution execution =
        NodeExecution.builder()
            .uuid(generateUuid() + "_execution")
            .status(Status.RUNNING)
            .mode(ExecutionMode.CHILD)
            .parentId(stage.getUuid())
            .skipGraphType(SkipType.NOOP)
            .stageFqn(generateUuid())
            .group("STEP")
            .module("CD")
            .name(generateUuid())
            .identifier(generateUuid())
            .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build())
            .nodeId(generateUuid())
            .build();
    NodeExecution step =
        NodeExecution.builder()
            .uuid(generateUuid() + "_step")
            .status(Status.FAILED)
            .mode(ExecutionMode.SYNC)
            .parentId(execution.getUuid())
            .skipGraphType(SkipType.NOOP)
            .stageFqn(generateUuid())
            .group("STEP")
            .module("CD")
            .name(generateUuid())
            .identifier(generateUuid())
            .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build())
            .nodeId(generateUuid())
            .build();

    when(planExecutionService.getPlanExecutionMetadata(planExecutionId)).thenReturn(planExecution);

    List<NodeExecution> nodeExecutionList = Arrays.asList(pipeline, stages, stage, execution, step);
    CloseableIterator<NodeExecution> iterator =
        OrchestrationTestHelper.createCloseableIterator(nodeExecutionList.iterator());
    when(nodeExecutionService.fetchNodeExecutionsWithoutOldRetriesIterator(
             planExecutionId, NodeProjectionUtils.fieldsForDiscontinuingNodes))
        .thenReturn(iterator);

    when(nodeExecutionService.updateStatusWithOps(
             eq(execution.getUuid()), eq(Status.DISCONTINUING), any(), eq(EnumSet.noneOf(Status.class))))
        .thenReturn(NodeExecution.builder()
                        .uuid(execution.getUuid())
                        .status(Status.DISCONTINUING)
                        .mode(ExecutionMode.CHILD)
                        .parentId(stage.getUuid())
                        .build());
    interruptMonitor.handle(interrupt);
    ArgumentCaptor<NodeExecution> discontinuingNodeCaptor = ArgumentCaptor.forClass(NodeExecution.class);
    ArgumentCaptor<Interrupt> interruptCaptor = ArgumentCaptor.forClass(Interrupt.class);
    verify(abortHelper).discontinueMarkedInstance(discontinuingNodeCaptor.capture(), interruptCaptor.capture());

    NodeExecution discontinuingNode = discontinuingNodeCaptor.getValue();
    assertThat(discontinuingNode.getUuid()).isEqualTo(execution.getUuid());
  }

  /**
   * Setup for the test
   * pipeline (running)
   *  stages (running)
   *    stage (running)
   *      execution (running)
   *        fork (running)
   *          sg1 (running)
   *            step(failed)
   *          sg2 (running)
   *            step(failed)
   *
   * And now the abort interrupt is registered
   */

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldTestParallelStepGroupStuck() {
    String planExecutionId = generateUuid();
    PlanExecution planExecution = PlanExecution.builder().uuid(planExecutionId).status(Status.RUNNING).build();
    Interrupt interrupt = Interrupt.builder()
                              .uuid(generateUuid())
                              .planExecutionId(planExecutionId)
                              .type(InterruptType.ABORT_ALL)
                              .state(Interrupt.State.PROCESSING)
                              .build();
    NodeExecution pipeline = NodeExecution.builder()
                                 .uuid(generateUuid() + "_pipeline")
                                 .status(Status.RUNNING)
                                 .mode(ExecutionMode.CHILD)
                                 .build();
    NodeExecution stages =
        NodeExecution.builder()
            .uuid(generateUuid() + "_stages")
            .status(Status.RUNNING)
            .mode(ExecutionMode.CHILD)
            .parentId(pipeline.getUuid())
            .skipGraphType(SkipType.NOOP)
            .stageFqn(generateUuid())
            .group("STEP")
            .module("CD")
            .name(generateUuid())
            .identifier(generateUuid())
            .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build())
            .nodeId(generateUuid())
            .build();
    NodeExecution stage =
        NodeExecution.builder()
            .uuid(generateUuid() + "_stage")
            .status(Status.RUNNING)
            .mode(ExecutionMode.CHILD)
            .parentId(stages.getUuid())
            .skipGraphType(SkipType.NOOP)
            .stageFqn(generateUuid())
            .group("STEP")
            .module("CD")
            .name(generateUuid())
            .identifier(generateUuid())
            .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build())
            .nodeId(generateUuid())
            .build();
    NodeExecution execution =
        NodeExecution.builder()
            .uuid(generateUuid() + "_execution")
            .status(Status.RUNNING)
            .mode(ExecutionMode.CHILD)
            .parentId(stage.getUuid())
            .skipGraphType(SkipType.NOOP)
            .stageFqn(generateUuid())
            .group("STEP")
            .module("CD")
            .name(generateUuid())
            .identifier(generateUuid())
            .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build())
            .nodeId(generateUuid())
            .build();

    NodeExecution fork =
        NodeExecution.builder()
            .uuid(generateUuid() + "_fork")
            .status(Status.RUNNING)
            .mode(ExecutionMode.CHILDREN)
            .parentId(execution.getUuid())
            .skipGraphType(SkipType.NOOP)
            .stageFqn(generateUuid())
            .group("STEP")
            .module("CD")
            .name(generateUuid())
            .identifier(generateUuid())
            .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build())
            .nodeId(generateUuid())
            .build();

    NodeExecution sg1 = NodeExecution.builder()
                            .uuid(generateUuid() + "_sg1")
                            .status(Status.RUNNING)
                            .mode(ExecutionMode.CHILD)
                            .parentId(fork.getUuid())
                            .skipGraphType(SkipType.NOOP)
                            .stageFqn(generateUuid())
                            .group("STEP")
                            .module("CD")
                            .name(generateUuid())
                            .identifier(generateUuid())
                            .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build())
                            .nodeId(generateUuid())
                            .build();

    NodeExecution stepSg1 =
        NodeExecution.builder()
            .uuid(generateUuid() + "_stepSg1")
            .status(Status.FAILED)
            .mode(ExecutionMode.SYNC)
            .parentId(sg1.getUuid())
            .skipGraphType(SkipType.NOOP)
            .stageFqn(generateUuid())
            .group("STEP")
            .module("CD")
            .name(generateUuid())
            .identifier(generateUuid())
            .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build())
            .nodeId(generateUuid())
            .build();

    NodeExecution sg2 = NodeExecution.builder()
                            .uuid(generateUuid() + "_sg2")
                            .status(Status.RUNNING)
                            .mode(ExecutionMode.CHILD)
                            .parentId(fork.getUuid())
                            .skipGraphType(SkipType.NOOP)
                            .stageFqn(generateUuid())
                            .group("STEP")
                            .module("CD")
                            .name(generateUuid())
                            .identifier(generateUuid())
                            .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build())
                            .nodeId(generateUuid())
                            .build();

    NodeExecution stepSg2 =
        NodeExecution.builder()
            .uuid(generateUuid() + "_stepSg2")
            .status(Status.FAILED)
            .mode(ExecutionMode.SYNC)
            .parentId(sg2.getUuid())
            .skipGraphType(SkipType.NOOP)
            .stageFqn(generateUuid())
            .group("STEP")
            .module("CD")
            .name(generateUuid())
            .identifier(generateUuid())
            .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build())
            .nodeId(generateUuid())
            .build();

    when(planExecutionService.getPlanExecutionMetadata(eq(planExecutionId))).thenReturn(planExecution);
    List<NodeExecution> nodeExecutionList =
        Arrays.asList(pipeline, stages, stage, execution, fork, sg1, sg2, stepSg1, stepSg2);
    CloseableIterator<NodeExecution> iterator =
        OrchestrationTestHelper.createCloseableIterator(nodeExecutionList.iterator());
    when(nodeExecutionService.fetchNodeExecutionsWithoutOldRetriesIterator(
             planExecutionId, NodeProjectionUtils.fieldsForDiscontinuingNodes))
        .thenReturn(iterator);

    when(nodeExecutionService.updateStatusWithOps(
             eq(sg1.getUuid()), eq(Status.DISCONTINUING), any(), eq(EnumSet.noneOf(Status.class))))
        .thenReturn(NodeExecution.builder()
                        .uuid(sg1.getUuid())
                        .status(Status.DISCONTINUING)
                        .mode(ExecutionMode.CHILD)
                        .parentId(stage.getUuid())
                        .build());

    when(nodeExecutionService.updateStatusWithOps(
             eq(sg2.getUuid()), eq(Status.DISCONTINUING), any(), eq(EnumSet.noneOf(Status.class))))
        .thenReturn(NodeExecution.builder()
                        .uuid(sg2.getUuid())
                        .status(Status.DISCONTINUING)
                        .mode(ExecutionMode.CHILD)
                        .parentId(stage.getUuid())
                        .build());
    interruptMonitor.handle(interrupt);
    ArgumentCaptor<NodeExecution> discontinuingNodeCaptor = ArgumentCaptor.forClass(NodeExecution.class);
    ArgumentCaptor<Interrupt> interruptCaptor = ArgumentCaptor.forClass(Interrupt.class);
    verify(abortHelper, times(2))
        .discontinueMarkedInstance(discontinuingNodeCaptor.capture(), interruptCaptor.capture());

    List<NodeExecution> discontinuingNodes = discontinuingNodeCaptor.getAllValues();
    assertThat(discontinuingNodes.stream().map(NodeExecution::getUuid).collect(Collectors.toSet()))
        .containsExactlyInAnyOrder(sg1.getUuid(), sg2.getUuid());
  }

  /**
   * Setup for the test
   * pipeline (running)
   *  stages (running)
   *    stage (running)
   *      execution (running)
   *        fork (discontinuing)
   *          sg1 (failed)
   *            step(failed)
   *          sg2 (failed)
   *            step(failed)
   *
   * And now the abort interrupt is registered
   */

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldTestSecondLevelParentStuck() {
    String planExecutionId = generateUuid();
    PlanExecution planExecution = PlanExecution.builder().uuid(planExecutionId).status(Status.RUNNING).build();
    Interrupt interrupt = Interrupt.builder()
                              .uuid(generateUuid())
                              .planExecutionId(planExecutionId)
                              .type(InterruptType.ABORT_ALL)
                              .state(Interrupt.State.PROCESSING)
                              .build();
    NodeExecution pipeline =
        NodeExecution.builder()
            .uuid(generateUuid() + "_pipeline")
            .status(Status.RUNNING)
            .mode(ExecutionMode.CHILD)
            .skipGraphType(SkipType.NOOP)
            .stageFqn(generateUuid())
            .group("STEP")
            .module("CD")
            .name(generateUuid())
            .identifier(generateUuid())
            .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build())
            .nodeId(generateUuid())
            .build();
    NodeExecution stages =
        NodeExecution.builder()
            .uuid(generateUuid() + "_stages")
            .status(Status.RUNNING)
            .mode(ExecutionMode.CHILD)
            .parentId(pipeline.getUuid())
            .skipGraphType(SkipType.NOOP)
            .stageFqn(generateUuid())
            .group("STEP")
            .module("CD")
            .name(generateUuid())
            .identifier(generateUuid())
            .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build())
            .nodeId(generateUuid())
            .build();
    NodeExecution stage =
        NodeExecution.builder()
            .uuid(generateUuid() + "_stage")
            .status(Status.RUNNING)
            .mode(ExecutionMode.CHILD)
            .parentId(stages.getUuid())
            .skipGraphType(SkipType.NOOP)
            .stageFqn(generateUuid())
            .group("STEP")
            .module("CD")
            .name(generateUuid())
            .identifier(generateUuid())
            .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build())
            .nodeId(generateUuid())
            .build();
    NodeExecution execution =
        NodeExecution.builder()
            .uuid(generateUuid() + "_execution")
            .status(Status.RUNNING)
            .mode(ExecutionMode.CHILD)
            .parentId(stage.getUuid())
            .skipGraphType(SkipType.NOOP)
            .stageFqn(generateUuid())
            .group("STEP")
            .module("CD")
            .name(generateUuid())
            .identifier(generateUuid())
            .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build())
            .nodeId(generateUuid())
            .build();

    NodeExecution fork =
        NodeExecution.builder()
            .uuid(generateUuid() + "_fork")
            .status(Status.DISCONTINUING)
            .mode(ExecutionMode.CHILDREN)
            .parentId(execution.getUuid())
            .skipGraphType(SkipType.NOOP)
            .stageFqn(generateUuid())
            .group("STEP")
            .module("CD")
            .name(generateUuid())
            .identifier(generateUuid())
            .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build())
            .nodeId(generateUuid())
            .build();

    NodeExecution sg1 = NodeExecution.builder()
                            .uuid(generateUuid() + "_sg1")
                            .status(Status.FAILED)
                            .mode(ExecutionMode.CHILD)
                            .parentId(fork.getUuid())
                            .skipGraphType(SkipType.NOOP)
                            .stageFqn(generateUuid())
                            .group("STEP")
                            .module("CD")
                            .name(generateUuid())
                            .identifier(generateUuid())
                            .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build())
                            .nodeId(generateUuid())
                            .build();

    NodeExecution stepSg1 =
        NodeExecution.builder()
            .uuid(generateUuid() + "_stepSg1")
            .status(Status.FAILED)
            .mode(ExecutionMode.SYNC)
            .parentId(sg1.getUuid())
            .skipGraphType(SkipType.NOOP)
            .stageFqn(generateUuid())
            .group("STEP")
            .module("CD")
            .name(generateUuid())
            .identifier(generateUuid())
            .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build())
            .nodeId(generateUuid())
            .build();

    NodeExecution sg2 = NodeExecution.builder()
                            .uuid(generateUuid() + "_sg2")
                            .status(Status.FAILED)
                            .mode(ExecutionMode.CHILD)
                            .parentId(fork.getUuid())
                            .skipGraphType(SkipType.NOOP)
                            .stageFqn(generateUuid())
                            .group("STEP")
                            .module("CD")
                            .name(generateUuid())
                            .identifier(generateUuid())
                            .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build())
                            .nodeId(generateUuid())
                            .build();

    NodeExecution stepSg2 =
        NodeExecution.builder()
            .uuid(generateUuid() + "_stepSg2")
            .status(Status.FAILED)
            .mode(ExecutionMode.SYNC)
            .parentId(sg2.getUuid())
            .skipGraphType(SkipType.NOOP)
            .stageFqn(generateUuid())
            .group("STEP")
            .module("CD")
            .name(generateUuid())
            .identifier(generateUuid())
            .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build())
            .nodeId(generateUuid())
            .build();

    when(planExecutionService.getPlanExecutionMetadata(eq(planExecutionId))).thenReturn(planExecution);

    List<NodeExecution> nodeExecutionList =
        Arrays.asList(pipeline, stages, stage, execution, fork, sg1, sg2, stepSg1, stepSg2);
    CloseableIterator<NodeExecution> iterator =
        OrchestrationTestHelper.createCloseableIterator(nodeExecutionList.iterator());
    when(nodeExecutionService.fetchNodeExecutionsWithoutOldRetriesIterator(
             planExecutionId, NodeProjectionUtils.fieldsForDiscontinuingNodes))
        .thenReturn(iterator);

    interruptMonitor.handle(interrupt);

    ArgumentCaptor<NodeExecution> discontinuingNodeCaptor = ArgumentCaptor.forClass(NodeExecution.class);
    ArgumentCaptor<Interrupt> interruptCaptor = ArgumentCaptor.forClass(Interrupt.class);
    verify(abortHelper).discontinueMarkedInstance(discontinuingNodeCaptor.capture(), interruptCaptor.capture());

    List<NodeExecution> discontinuingNodes = discontinuingNodeCaptor.getAllValues();
    assertThat(discontinuingNodes).hasSize(1);
    assertThat(discontinuingNodes.get(0).getUuid()).isEqualTo(fork.getUuid());
  }

  /**
   * Setup for the test
   * pipeline (running)
   *  stages (running)
   *    stage (running)
   *      execution (running)
   *        fork (running)
   *          sg1 (SUCCEEDED)
   *            step(SUCCEEDED)
   *          sg2 (running)
   *            step(failed)
   *
   * And now the abort interrupt is registered
   */

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldTestParallelStepGroupOneStuck() {
    String planExecutionId = generateUuid();
    PlanExecution planExecution = PlanExecution.builder().uuid(planExecutionId).status(Status.RUNNING).build();
    Interrupt interrupt = Interrupt.builder()
                              .uuid(generateUuid())
                              .planExecutionId(planExecutionId)
                              .type(InterruptType.ABORT_ALL)
                              .state(Interrupt.State.PROCESSING)
                              .build();
    NodeExecution pipeline =
        NodeExecution.builder()
            .uuid(generateUuid() + "_pipeline")
            .status(Status.RUNNING)
            .mode(ExecutionMode.CHILD)
            .skipGraphType(SkipType.NOOP)
            .stageFqn(generateUuid())
            .group("STEP")
            .module("CD")

            .name(generateUuid())
            .identifier(generateUuid())
            .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build())
            .nodeId(generateUuid())
            .build();
    NodeExecution stages =
        NodeExecution.builder()
            .uuid(generateUuid() + "_stages")
            .status(Status.RUNNING)
            .mode(ExecutionMode.CHILD)
            .parentId(pipeline.getUuid())
            .skipGraphType(SkipType.NOOP)
            .stageFqn(generateUuid())
            .group("STEP")
            .module("CD")
            .name(generateUuid())
            .identifier(generateUuid())
            .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build())
            .nodeId(generateUuid())
            .build();
    NodeExecution stage =
        NodeExecution.builder()
            .uuid(generateUuid() + "_stage")
            .status(Status.RUNNING)
            .mode(ExecutionMode.CHILD)
            .parentId(stages.getUuid())
            .skipGraphType(SkipType.NOOP)
            .stageFqn(generateUuid())
            .group("STEP")
            .module("CD")
            .name(generateUuid())
            .identifier(generateUuid())
            .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build())
            .nodeId(generateUuid())
            .build();
    NodeExecution execution =
        NodeExecution.builder()
            .uuid(generateUuid() + "_execution")
            .status(Status.RUNNING)
            .mode(ExecutionMode.CHILD)
            .parentId(stage.getUuid())
            .skipGraphType(SkipType.NOOP)
            .stageFqn(generateUuid())
            .group("STEP")
            .module("CD")
            .name(generateUuid())
            .identifier(generateUuid())
            .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build())
            .nodeId(generateUuid())
            .build();

    NodeExecution fork =
        NodeExecution.builder()
            .uuid(generateUuid() + "_fork")
            .status(Status.RUNNING)
            .mode(ExecutionMode.CHILDREN)
            .parentId(execution.getUuid())
            .skipGraphType(SkipType.NOOP)
            .stageFqn(generateUuid())
            .group("STEP")
            .module("CD")
            .name(generateUuid())
            .identifier(generateUuid())
            .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build())
            .nodeId(generateUuid())
            .build();

    NodeExecution sg1 = NodeExecution.builder()
                            .uuid(generateUuid() + "_sg1")
                            .status(Status.SUCCEEDED)
                            .mode(ExecutionMode.CHILD)
                            .parentId(fork.getUuid())
                            .skipGraphType(SkipType.NOOP)
                            .stageFqn(generateUuid())
                            .group("STEP")
                            .module("CD")
                            .name(generateUuid())
                            .identifier(generateUuid())
                            .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build())
                            .nodeId(generateUuid())
                            .build();

    NodeExecution stepSg1 =
        NodeExecution.builder()
            .uuid(generateUuid() + "_stepSg1")
            .status(Status.SUCCEEDED)
            .mode(ExecutionMode.SYNC)
            .parentId(sg1.getUuid())
            .skipGraphType(SkipType.NOOP)
            .stageFqn(generateUuid())
            .group("STEP")
            .module("CD")
            .name(generateUuid())
            .identifier(generateUuid())
            .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build())
            .nodeId(generateUuid())
            .build();

    NodeExecution sg2 = NodeExecution.builder()
                            .uuid(generateUuid() + "_sg2")
                            .status(Status.RUNNING)
                            .mode(ExecutionMode.CHILD)
                            .parentId(fork.getUuid())
                            .skipGraphType(SkipType.NOOP)
                            .stageFqn(generateUuid())
                            .group("STEP")
                            .module("CD")
                            .name(generateUuid())
                            .identifier(generateUuid())
                            .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build())
                            .nodeId(generateUuid())
                            .build();

    NodeExecution stepSg2 =
        NodeExecution.builder()
            .uuid(generateUuid() + "_stepSg2")
            .status(Status.FAILED)
            .mode(ExecutionMode.SYNC)
            .parentId(sg2.getUuid())
            .skipGraphType(SkipType.NOOP)
            .stageFqn(generateUuid())
            .group("STEP")
            .module("CD")
            .name(generateUuid())
            .identifier(generateUuid())
            .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build())
            .nodeId(generateUuid())
            .build();

    when(planExecutionService.getPlanExecutionMetadata(eq(planExecutionId))).thenReturn(planExecution);

    List<NodeExecution> nodeExecutionList =
        Arrays.asList(pipeline, stages, stage, execution, fork, sg1, sg2, stepSg1, stepSg2);
    CloseableIterator<NodeExecution> iterator =
        OrchestrationTestHelper.createCloseableIterator(nodeExecutionList.iterator());
    when(nodeExecutionService.fetchNodeExecutionsWithoutOldRetriesIterator(
             planExecutionId, NodeProjectionUtils.fieldsForDiscontinuingNodes))
        .thenReturn(iterator);

    when(nodeExecutionService.updateStatusWithOps(
             eq(sg2.getUuid()), eq(Status.DISCONTINUING), any(), eq(EnumSet.noneOf(Status.class))))
        .thenReturn(NodeExecution.builder()
                        .uuid(sg2.getUuid())
                        .status(Status.DISCONTINUING)
                        .mode(ExecutionMode.CHILD)
                        .parentId(stage.getUuid())
                        .build());

    interruptMonitor.handle(interrupt);
    ArgumentCaptor<NodeExecution> discontinuingNodeCaptor = ArgumentCaptor.forClass(NodeExecution.class);
    ArgumentCaptor<Interrupt> interruptCaptor = ArgumentCaptor.forClass(Interrupt.class);
    verify(abortHelper, times(1))
        .discontinueMarkedInstance(discontinuingNodeCaptor.capture(), interruptCaptor.capture());

    List<NodeExecution> discontinuingNodes = discontinuingNodeCaptor.getAllValues();
    assertThat(discontinuingNodes.get(0).getUuid()).isEqualTo(sg2.getUuid());
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldTestLeafDiscontinuing() {
    String planExecutionId = generateUuid();
    PlanExecution planExecution = PlanExecution.builder().uuid(planExecutionId).status(Status.RUNNING).build();
    Interrupt interrupt = Interrupt.builder()
                              .uuid(generateUuid())
                              .planExecutionId(planExecutionId)
                              .type(InterruptType.ABORT_ALL)
                              .state(Interrupt.State.PROCESSING)
                              .build();
    NodeExecution pipeline =
        NodeExecution.builder()
            .uuid(generateUuid() + "_pipeline")
            .status(Status.RUNNING)
            .mode(ExecutionMode.CHILD)
            .skipGraphType(SkipType.NOOP)
            .stageFqn(generateUuid())
            .group("STEP")
            .module("CD")
            .name(generateUuid())
            .identifier(generateUuid())
            .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build())
            .nodeId(generateUuid())
            .build();
    NodeExecution stages =
        NodeExecution.builder()
            .uuid(generateUuid() + "_stages")
            .status(Status.RUNNING)
            .mode(ExecutionMode.CHILD)
            .parentId(pipeline.getUuid())
            .skipGraphType(SkipType.NOOP)
            .stageFqn(generateUuid())
            .group("STEP")
            .module("CD")
            .name(generateUuid())
            .identifier(generateUuid())
            .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build())
            .nodeId(generateUuid())
            .build();
    NodeExecution stage =
        NodeExecution.builder()
            .uuid(generateUuid() + "_stage")
            .status(Status.RUNNING)
            .mode(ExecutionMode.CHILD)
            .parentId(stages.getUuid())
            .skipGraphType(SkipType.NOOP)
            .stageFqn(generateUuid())
            .group("STEP")
            .module("CD")
            .name(generateUuid())
            .identifier(generateUuid())
            .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build())
            .nodeId(generateUuid())
            .build();
    NodeExecution execution =
        NodeExecution.builder()
            .uuid(generateUuid() + "_execution")
            .status(Status.RUNNING)
            .mode(ExecutionMode.CHILD)
            .parentId(stage.getUuid())
            .skipGraphType(SkipType.NOOP)
            .stageFqn(generateUuid())
            .group("STEP")
            .module("CD")
            .name(generateUuid())
            .identifier(generateUuid())
            .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build())
            .nodeId(generateUuid())
            .build();

    NodeExecution fork =
        NodeExecution.builder()
            .uuid(generateUuid() + "_fork")
            .status(Status.RUNNING)
            .mode(ExecutionMode.CHILDREN)
            .parentId(execution.getUuid())
            .skipGraphType(SkipType.NOOP)
            .stageFqn(generateUuid())
            .group("STEP")
            .module("CD")
            .name(generateUuid())
            .identifier(generateUuid())
            .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build())
            .nodeId(generateUuid())
            .build();

    NodeExecution sg1 = NodeExecution.builder()
                            .uuid(generateUuid() + "_sg1")
                            .status(Status.SUCCEEDED)
                            .mode(ExecutionMode.CHILD)
                            .parentId(fork.getUuid())
                            .skipGraphType(SkipType.NOOP)
                            .stageFqn(generateUuid())
                            .group("STEP")
                            .module("CD")
                            .name(generateUuid())
                            .identifier(generateUuid())
                            .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build())
                            .nodeId(generateUuid())
                            .build();

    NodeExecution stepSg1 =
        NodeExecution.builder()
            .uuid(generateUuid() + "_stepSg1")
            .status(Status.SUCCEEDED)
            .mode(ExecutionMode.SYNC)
            .parentId(sg1.getUuid())
            .skipGraphType(SkipType.NOOP)
            .stageFqn(generateUuid())
            .group("STEP")
            .module("CD")
            .name(generateUuid())
            .identifier(generateUuid())
            .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build())
            .nodeId(generateUuid())
            .build();

    NodeExecution sg2 = NodeExecution.builder()
                            .uuid(generateUuid() + "_sg2")
                            .status(Status.RUNNING)
                            .mode(ExecutionMode.CHILD)
                            .parentId(fork.getUuid())
                            .skipGraphType(SkipType.NOOP)
                            .stageFqn(generateUuid())
                            .group("STEP")
                            .module("CD")
                            .name(generateUuid())
                            .identifier(generateUuid())
                            .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build())
                            .nodeId(generateUuid())
                            .build();

    NodeExecution stepSg2 =
        NodeExecution.builder()
            .uuid(generateUuid() + "_stepSg2")
            .status(Status.DISCONTINUING)
            .mode(ExecutionMode.SYNC)
            .parentId(sg2.getUuid())
            .skipGraphType(SkipType.NOOP)
            .stageFqn(generateUuid())
            .group("STEP")
            .module("CD")
            .name(generateUuid())
            .identifier(generateUuid())
            .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build())
            .nodeId(generateUuid())
            .build();

    when(planExecutionService.getPlanExecutionMetadata(eq(planExecutionId))).thenReturn(planExecution);

    List<NodeExecution> nodeExecutionList =
        Arrays.asList(pipeline, stages, stage, execution, fork, sg1, sg2, stepSg1, stepSg2);
    CloseableIterator<NodeExecution> iterator =
        OrchestrationTestHelper.createCloseableIterator(nodeExecutionList.iterator());
    when(nodeExecutionService.fetchNodeExecutionsWithoutOldRetriesIterator(
             planExecutionId, NodeProjectionUtils.fieldsForDiscontinuingNodes))
        .thenReturn(iterator);

    when(nodeExecutionService.updateStatusWithOps(
             eq(sg2.getUuid()), eq(Status.DISCONTINUING), any(), eq(EnumSet.noneOf(Status.class))))
        .thenReturn(NodeExecution.builder()
                        .uuid(sg2.getUuid())
                        .status(Status.DISCONTINUING)
                        .mode(ExecutionMode.CHILD)
                        .parentId(stage.getUuid())
                        .build());

    interruptMonitor.handle(interrupt);
    ArgumentCaptor<NodeExecution> discontinuingNodeCaptor = ArgumentCaptor.forClass(NodeExecution.class);
    verify(abortHelper, times(1))
        .abortDiscontinuingNode(
            discontinuingNodeCaptor.capture(), eq(interrupt.getUuid()), eq(interrupt.getInterruptConfig()));
    NodeExecution dne = discontinuingNodeCaptor.getValue();
    assertThat(dne.getUuid()).isEqualTo(stepSg2.getUuid());
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldTestLeafDiscontinuingException() {
    String planExecutionId = generateUuid();
    PlanExecution planExecution = PlanExecution.builder().uuid(planExecutionId).status(Status.RUNNING).build();
    Interrupt interrupt = Interrupt.builder()
                              .uuid(generateUuid())
                              .planExecutionId(planExecutionId)
                              .type(InterruptType.ABORT_ALL)
                              .state(Interrupt.State.PROCESSING)
                              .build();
    NodeExecution pipeline =
        NodeExecution.builder()
            .uuid(generateUuid() + "_pipeline")
            .status(Status.RUNNING)
            .mode(ExecutionMode.CHILD)
            .skipGraphType(SkipType.NOOP)
            .stageFqn(generateUuid())
            .group("STEP")
            .module("CD")
            .name(generateUuid())
            .identifier(generateUuid())
            .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build())
            .nodeId(generateUuid())
            .build();
    NodeExecution stages =
        NodeExecution.builder()
            .uuid(generateUuid() + "_stages")
            .status(Status.RUNNING)
            .mode(ExecutionMode.CHILD)
            .parentId(pipeline.getUuid())
            .skipGraphType(SkipType.NOOP)
            .stageFqn(generateUuid())
            .group("STEP")
            .module("CD")
            .name(generateUuid())
            .identifier(generateUuid())
            .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build())
            .nodeId(generateUuid())
            .build();
    NodeExecution stage =
        NodeExecution.builder()
            .uuid(generateUuid() + "_stage")
            .status(Status.RUNNING)
            .mode(ExecutionMode.CHILD)
            .parentId(stages.getUuid())
            .skipGraphType(SkipType.NOOP)
            .stageFqn(generateUuid())
            .group("STEP")
            .module("CD")
            .name(generateUuid())
            .identifier(generateUuid())
            .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build())
            .nodeId(generateUuid())
            .build();
    NodeExecution execution =
        NodeExecution.builder()
            .uuid(generateUuid() + "_execution")
            .status(Status.RUNNING)
            .mode(ExecutionMode.CHILD)
            .parentId(stage.getUuid())
            .skipGraphType(SkipType.NOOP)
            .stageFqn(generateUuid())
            .group("STEP")
            .module("CD")
            .name(generateUuid())
            .identifier(generateUuid())
            .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build())
            .nodeId(generateUuid())
            .build();

    NodeExecution fork =
        NodeExecution.builder()
            .uuid(generateUuid() + "_fork")
            .status(Status.RUNNING)
            .mode(ExecutionMode.CHILDREN)
            .parentId(execution.getUuid())
            .skipGraphType(SkipType.NOOP)
            .stageFqn(generateUuid())
            .group("STEP")
            .module("CD")
            .name(generateUuid())
            .identifier(generateUuid())
            .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build())
            .nodeId(generateUuid())
            .build();

    NodeExecution sg1 = NodeExecution.builder()
                            .uuid(generateUuid() + "_sg1")
                            .status(Status.SUCCEEDED)
                            .mode(ExecutionMode.CHILD)
                            .parentId(fork.getUuid())
                            .skipGraphType(SkipType.NOOP)
                            .stageFqn(generateUuid())
                            .group("STEP")
                            .module("CD")
                            .name(generateUuid())
                            .identifier(generateUuid())
                            .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build())
                            .nodeId(generateUuid())
                            .build();

    NodeExecution stepSg1 =
        NodeExecution.builder()
            .uuid(generateUuid() + "_stepSg1")
            .status(Status.SUCCEEDED)
            .mode(ExecutionMode.SYNC)
            .parentId(sg1.getUuid())
            .skipGraphType(SkipType.NOOP)
            .stageFqn(generateUuid())
            .group("STEP")
            .module("CD")
            .name(generateUuid())
            .identifier(generateUuid())
            .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build())
            .nodeId(generateUuid())
            .build();

    NodeExecution sg2 = NodeExecution.builder()
                            .uuid(generateUuid() + "_sg2")
                            .status(Status.RUNNING)
                            .mode(ExecutionMode.CHILD)
                            .parentId(fork.getUuid())
                            .skipGraphType(SkipType.NOOP)
                            .stageFqn(generateUuid())
                            .group("STEP")
                            .module("CD")
                            .name(generateUuid())
                            .identifier(generateUuid())
                            .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build())
                            .nodeId(generateUuid())
                            .build();

    NodeExecution stepSg2 =
        NodeExecution.builder()
            .uuid(generateUuid() + "_stepSg2")
            .status(Status.DISCONTINUING)
            .mode(ExecutionMode.SYNC)
            .parentId(sg2.getUuid())
            .skipGraphType(SkipType.NOOP)
            .stageFqn(generateUuid())
            .group("STEP")
            .module("CD")
            .name(generateUuid())
            .identifier(generateUuid())
            .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build())
            .nodeId(generateUuid())
            .build();

    when(planExecutionService.getPlanExecutionMetadata(eq(planExecutionId))).thenReturn(planExecution);

    List<NodeExecution> nodeExecutionList =
        Arrays.asList(pipeline, stages, stage, execution, fork, sg1, sg2, stepSg1, stepSg2);
    CloseableIterator<NodeExecution> iterator =
        OrchestrationTestHelper.createCloseableIterator(nodeExecutionList.iterator());
    when(nodeExecutionService.fetchNodeExecutionsWithoutOldRetriesIterator(
             planExecutionId, NodeProjectionUtils.fieldsForDiscontinuingNodes))
        .thenReturn(iterator);

    when(nodeExecutionService.updateStatusWithOps(
             eq(sg2.getUuid()), eq(Status.DISCONTINUING), any(), eq(EnumSet.noneOf(Status.class))))
        .thenReturn(NodeExecution.builder()
                        .uuid(sg2.getUuid())
                        .status(Status.DISCONTINUING)
                        .mode(ExecutionMode.CHILD)
                        .parentId(stage.getUuid())
                        .build());
    doThrow(MappingInstantiationException.class)
        .when(abortHelper)
        .abortDiscontinuingNode(stepSg2, interrupt.getUuid(), interrupt.getInterruptConfig());
    interruptMonitor.handle(interrupt);
    verify(interruptService, times(1))
        .markProcessedForceful(eq(interrupt.getUuid()), eq(PROCESSED_UNSUCCESSFULLY), eq(true));
    Mockito.verifyNoMoreInteractions(interruptService);
  }
}
