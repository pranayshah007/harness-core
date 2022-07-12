/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.interrupts.helpers;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.pms.contracts.execution.Status.ABORTED;
import static io.harness.pms.contracts.execution.Status.DISCONTINUING;
import static io.harness.rule.OwnerRule.PRASHANT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.OrchestrationTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.engine.OrchestrationEngine;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.engine.interrupts.AbortInterruptCallback;
import io.harness.engine.interrupts.handlers.publisher.InterruptEventPublisher;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.NodeExecution;
import io.harness.execution.NodeExecution.NodeExecutionBuilder;
import io.harness.interrupts.Interrupt;
import io.harness.interrupts.Interrupt.State;
import io.harness.plan.PlanNode;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.ExecutionMode;
import io.harness.pms.contracts.interrupts.InterruptConfig;
import io.harness.pms.contracts.interrupts.InterruptType;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.rule.Owner;
import io.harness.waiter.OldNotifyCallback;
import io.harness.waiter.WaitNotifyEngine;

import com.google.inject.Inject;
import java.time.Duration;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.data.mongodb.core.MongoTemplate;

@OwnedBy(HarnessTeam.PIPELINE)
public class AbortHelperTest extends OrchestrationTestBase {
  @Mock private OrchestrationEngine engine;
  @Mock private InterruptHelper interruptHelper;
  @Mock private NodeExecutionService nodeExecutionService;
  @Mock private WaitNotifyEngine waitNotifyEngine;
  @Mock private InterruptEventPublisher interruptEventPublisher;
  @Inject private MongoTemplate mongoTemplate;
  @Inject @InjectMocks private AbortHelper abortHelper;

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldTestDiscontinueMarkedInstances() {
    String notifyId = generateUuid();
    String nodeExecutionId = generateUuid();
    String planExecutionId = generateUuid();
    String interruptUuid = generateUuid();
    Interrupt interrupt = Interrupt.builder()
                              .uuid(interruptUuid)
                              .type(InterruptType.ABORT_ALL)
                              .interruptConfig(InterruptConfig.newBuilder().build())
                              .planExecutionId(planExecutionId)
                              .state(State.PROCESSING)
                              .build();
    mongoTemplate.save(interrupt);

    NodeExecution nodeExecution =
        NodeExecution.builder()
            .uuid(nodeExecutionId)
            .ambiance(Ambiance.newBuilder().setPlanExecutionId(generateUuid()).build())
            .status(DISCONTINUING)
            .mode(ExecutionMode.ASYNC)
            .planNode(PlanNode.builder()
                          .uuid(generateUuid())
                          .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build())
                          .build())
            .startTs(System.currentTimeMillis())
            .build();

    when(interruptEventPublisher.publishEvent(nodeExecutionId, interrupt, InterruptType.ABORT)).thenReturn(notifyId);
    abortHelper.discontinueMarkedInstance(nodeExecution, interrupt);

    ArgumentCaptor<String> pName = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<OldNotifyCallback> callbackCaptor = ArgumentCaptor.forClass(OldNotifyCallback.class);
    ArgumentCaptor<List> correlationIdCaptor = ArgumentCaptor.forClass(List.class);

    verify(waitNotifyEngine, times(1))
        .waitForAllOnInList(
            pName.capture(), callbackCaptor.capture(), correlationIdCaptor.capture(), eq(Duration.ofMinutes(1)));

    assertThat(callbackCaptor.getValue()).isInstanceOf(AbortInterruptCallback.class);
    List<String> corrIds = correlationIdCaptor.getValue();
    assertThat(corrIds).hasSize(1);
    assertThat(corrIds.get(0)).isEqualTo(notifyId);
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldTestDiscontinueMarkedInstancesForSync() {
    String nodeExecutionId = generateUuid();
    String planExecutionId = generateUuid();
    String interruptUuid = generateUuid();
    Interrupt interrupt = Interrupt.builder()
                              .uuid(interruptUuid)
                              .type(InterruptType.ABORT_ALL)
                              .interruptConfig(InterruptConfig.newBuilder().build())
                              .planExecutionId(planExecutionId)
                              .state(State.PROCESSING)
                              .build();
    mongoTemplate.save(interrupt);

    Ambiance ambiance = Ambiance.newBuilder()
                            .setPlanExecutionId(generateUuid())
                            .addLevels(Level.newBuilder().setRuntimeId(nodeExecutionId).build())
                            .build();
    NodeExecutionBuilder nodeExecution =
        NodeExecution.builder()
            .uuid(nodeExecutionId)
            .ambiance(ambiance)
            .mode(ExecutionMode.SYNC)
            .planNode(PlanNode.builder()
                          .uuid(generateUuid())
                          .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build())
                          .build())
            .startTs(System.currentTimeMillis());

    when(nodeExecutionService.updateStatusWithOps(eq(nodeExecutionId), eq(ABORTED), any(), any()))
        .thenReturn(nodeExecution.status(ABORTED).endTs(System.currentTimeMillis()).build());
    abortHelper.discontinueMarkedInstance(nodeExecution.status(DISCONTINUING).build(), interrupt);

    ArgumentCaptor<Ambiance> ambianceCaptor = ArgumentCaptor.forClass(Ambiance.class);

    verify(interruptEventPublisher, times(0)).publishEvent(any(), any(), any());
    verify(waitNotifyEngine, times(0)).waitForAllOn(any(), any(), any());

    verify(engine, times(1)).endNodeExecution(ambianceCaptor.capture());
    assertThat(ambianceCaptor.getValue()).isNotNull();
    assertThat(ambianceCaptor.getValue()).isEqualTo(ambiance);
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldTestDiscontinueMarkedInstancesForParentNodes() {
    String nodeExecutionId = generateUuid();
    String planExecutionId = generateUuid();
    String interruptUuid = generateUuid();
    Interrupt interrupt = Interrupt.builder()
                              .uuid(interruptUuid)
                              .type(InterruptType.ABORT_ALL)
                              .interruptConfig(InterruptConfig.newBuilder().build())
                              .planExecutionId(planExecutionId)
                              .state(State.PROCESSING)
                              .build();
    mongoTemplate.save(interrupt);

    Ambiance ambiance = Ambiance.newBuilder()
                            .setPlanExecutionId(generateUuid())
                            .addLevels(Level.newBuilder().setRuntimeId(nodeExecutionId).build())
                            .build();
    NodeExecutionBuilder nodeExecution =
        NodeExecution.builder()
            .uuid(nodeExecutionId)
            .ambiance(ambiance)
            .mode(ExecutionMode.CHILD)
            .planNode(PlanNode.builder()
                          .uuid(generateUuid())
                          .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build())
                          .build())
            .startTs(System.currentTimeMillis());

    when(nodeExecutionService.updateStatusWithOps(eq(nodeExecutionId), eq(ABORTED), any(), any()))
        .thenReturn(nodeExecution.status(ABORTED).endTs(System.currentTimeMillis()).build());

    abortHelper.discontinueMarkedInstance(nodeExecution.status(DISCONTINUING).build(), interrupt);

    ArgumentCaptor<Ambiance> ambianceCaptor = ArgumentCaptor.forClass(Ambiance.class);

    verify(interruptEventPublisher, times(0)).publishEvent(any(), any(), any());
    verify(waitNotifyEngine, times(0)).waitForAllOn(any(), any(), any());

    verify(engine, times(1)).endNodeExecution(ambianceCaptor.capture());
    assertThat(ambianceCaptor.getValue()).isNotNull();
    assertThat(ambianceCaptor.getValue()).isEqualTo(ambiance);
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldTestAbortException() {
    when(interruptHelper.discontinueTaskIfRequired(any())).thenThrow(new RuntimeException("TEST_EXCEPTION"));
    Interrupt interrupt = Interrupt.builder()
                              .uuid(generateUuid())
                              .type(InterruptType.ABORT_ALL)
                              .interruptConfig(InterruptConfig.newBuilder().build())
                              .planExecutionId(generateUuid())
                              .state(State.PROCESSING)
                              .build();
    NodeExecution nodeExecution =
        NodeExecution.builder()
            .uuid(generateUuid())
            .ambiance(Ambiance.newBuilder().setPlanExecutionId(generateUuid()).build())
            .status(DISCONTINUING)
            .mode(ExecutionMode.ASYNC)
            .planNode(PlanNode.builder()
                          .uuid(generateUuid())
                          .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build())
                          .build())
            .startTs(System.currentTimeMillis())
            .build();
    assertThatThrownBy(() -> abortHelper.discontinueMarkedInstance(nodeExecution, interrupt))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Error in discontinuing, TEST_EXCEPTION");
  }
}
