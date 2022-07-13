/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.interrupts;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.interrupts.Interrupt.State.DISCARDED;
import static io.harness.interrupts.Interrupt.State.PROCESSING;
import static io.harness.interrupts.Interrupt.State.REGISTERED;
import static io.harness.rule.OwnerRule.ALEXEI;
import static io.harness.rule.OwnerRule.PRASHANT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.OrchestrationTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.engine.ExecutionCheck;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.engine.executions.plan.PlanExecutionService;
import io.harness.engine.interrupts.handlers.AbortInterruptHandler;
import io.harness.engine.interrupts.handlers.MarkExpiredInterruptHandler;
import io.harness.engine.interrupts.handlers.PauseAllInterruptHandler;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.NodeExecution;
import io.harness.interrupts.Interrupt;
import io.harness.plan.PlanNode;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.ExecutionMode;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.interrupts.InterruptType;
import io.harness.pms.execution.utils.NodeProjectionUtils;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.PIPELINE)
public class InterruptServiceImplTest extends OrchestrationTestBase {
  @Mock private NodeExecutionService nodeExecutionService;
  @Mock private AbortInterruptHandler abortInterruptHandler;
  @Mock private MarkExpiredInterruptHandler markExpiredInterruptHandler;
  @Mock private PauseAllInterruptHandler pauseAllInterruptHandler;
  @Mock private PlanExecutionService planExecutionService;
  @Inject @InjectMocks private InterruptService interruptService;

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldTestSave() {
    String planExecutionId = generateUuid();
    Interrupt interrupt = Interrupt.builder().planExecutionId(planExecutionId).type(InterruptType.ABORT_ALL).build();

    Interrupt savedInterrupt = interruptService.save(interrupt);
    assertThat(savedInterrupt).isNotNull();
    assertThat(savedInterrupt.getUuid()).isNotNull();
    assertThat(savedInterrupt.getPlanExecutionId()).isEqualTo(planExecutionId);
    assertThat(savedInterrupt.getType()).isEqualTo(InterruptType.ABORT_ALL);
    assertThat(savedInterrupt.getState()).isEqualTo(REGISTERED);
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void fetchActivePlanLevelInterrupts() {
    String planExecutionId = generateUuid();
    saveInterruptList(planExecutionId, false);

    List<Interrupt> planLevelInterrupts = interruptService.fetchActivePlanLevelInterrupts(planExecutionId);
    assertThat(planLevelInterrupts).isNotEmpty();
    assertThat(planLevelInterrupts).hasSize(2);
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldTestMarkProcessed() {
    String planExecutionId = generateUuid();
    Interrupt abortAllInterrupt =
        Interrupt.builder().planExecutionId(planExecutionId).type(InterruptType.ABORT_ALL).build();
    Interrupt savedInterrupt = interruptService.save(abortAllInterrupt);
    Interrupt processed = interruptService.markProcessed(savedInterrupt.getUuid(), DISCARDED);
    assertThat(processed).isNotNull();
    assertThat(processed.getState()).isEqualTo(DISCARDED);
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void markProcessing() {
    String planExecutionId = generateUuid();
    Interrupt abortAllInterrupt =
        Interrupt.builder().planExecutionId(planExecutionId).type(InterruptType.ABORT_ALL).build();
    Interrupt savedInterrupt = interruptService.save(abortAllInterrupt);
    Interrupt processing = interruptService.markProcessing(savedInterrupt.getUuid());
    assertThat(processing).isNotNull();
    assertThat(processing.getState()).isEqualTo(PROCESSING);
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void fetchAllInterrupts() {
    String planExecutionId = generateUuid();
    saveInterruptList(planExecutionId, false);

    List<Interrupt> interrupts = interruptService.fetchAllInterrupts(planExecutionId);
    assertThat(interrupts).isNotEmpty();
    assertThat(interrupts).hasSize(3);
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void fetchActiveInterrupts() {
    String planExecutionId = generateUuid();
    saveInterruptList(planExecutionId, true);
    List<Interrupt> interrupts = interruptService.fetchActiveInterrupts(planExecutionId);
    assertThat(interrupts).isNotEmpty();
    assertThat(interrupts).hasSize(2);
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void testPreInvocationNoInterrupts() {
    String planExecutionId = generateUuid();
    ExecutionCheck executionCheck = interruptService.checkInterruptsPreInvocation(planExecutionId, generateUuid());
    assertThat(executionCheck).isNotNull();
    assertThat(executionCheck.isProceed()).isTrue();
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void testAbortAllPreInvocationParent() {
    String planExecutionId = generateUuid();
    Interrupt abortAllInterrupt =
        Interrupt.builder().uuid(generateUuid()).planExecutionId(planExecutionId).type(InterruptType.ABORT_ALL).build();
    interruptService.save(abortAllInterrupt);
    NodeExecution nodeExecution = NodeExecution.builder()
                                      .uuid(generateUuid())
                                      .status(Status.QUEUED)
                                      .planNode(PlanNode.builder().identifier(generateUuid()).build())
                                      .ambiance(Ambiance.newBuilder().build())
                                      .mode(ExecutionMode.CHILD)
                                      .version(1L)
                                      .build();
    when(nodeExecutionService.getWithFieldsIncluded(nodeExecution.getUuid(), NodeProjectionUtils.withStatusAndMode))
        .thenReturn(nodeExecution);
    ExecutionCheck executionCheck =
        interruptService.checkInterruptsPreInvocation(planExecutionId, nodeExecution.getUuid());
    assertThat(executionCheck).isNotNull();
    assertThat(executionCheck.isProceed()).isTrue();
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void testAbortAllPreInvocationNotParent() {
    String planExecutionId = generateUuid();
    Interrupt abortAllInterrupt =
        Interrupt.builder().uuid(generateUuid()).planExecutionId(planExecutionId).type(InterruptType.ABORT_ALL).build();
    interruptService.save(abortAllInterrupt);
    NodeExecution nodeExecution = NodeExecution.builder()
                                      .uuid(generateUuid())
                                      .status(Status.QUEUED)
                                      .planNode(PlanNode.builder().identifier(generateUuid()).build())
                                      .ambiance(Ambiance.newBuilder().build())
                                      .mode(ExecutionMode.TASK)
                                      .version(1L)
                                      .build();
    when(nodeExecutionService.getWithFieldsIncluded(nodeExecution.getUuid(), NodeProjectionUtils.withStatusAndMode))
        .thenReturn(nodeExecution);
    when(abortInterruptHandler.handleInterruptForNodeExecution(any(), eq(nodeExecution.getUuid())))
        .thenReturn(abortAllInterrupt);
    ExecutionCheck executionCheck =
        interruptService.checkInterruptsPreInvocation(planExecutionId, nodeExecution.getUuid());
    assertThat(executionCheck).isNotNull();
    assertThat(executionCheck.isProceed()).isFalse();
    verify(abortInterruptHandler).handleInterruptForNodeExecution(any(), eq(nodeExecution.getUuid()));
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void testExpireAllPreInvocationNotParent() {
    String planExecutionId = generateUuid();
    Interrupt expireAllInterrupt = Interrupt.builder()
                                       .uuid(generateUuid())
                                       .planExecutionId(planExecutionId)
                                       .type(InterruptType.EXPIRE_ALL)
                                       .build();
    interruptService.save(expireAllInterrupt);
    NodeExecution nodeExecution = NodeExecution.builder()
                                      .uuid(generateUuid())
                                      .status(Status.QUEUED)
                                      .planNode(PlanNode.builder().identifier(generateUuid()).build())
                                      .ambiance(Ambiance.newBuilder().build())
                                      .mode(ExecutionMode.TASK)
                                      .version(1L)
                                      .build();
    when(nodeExecutionService.getWithFieldsIncluded(nodeExecution.getUuid(), NodeProjectionUtils.withStatusAndMode))
        .thenReturn(nodeExecution);
    when(markExpiredInterruptHandler.handleInterruptForNodeExecution(any(), eq(nodeExecution.getUuid())))
        .thenReturn(expireAllInterrupt);
    ExecutionCheck executionCheck =
        interruptService.checkInterruptsPreInvocation(planExecutionId, nodeExecution.getUuid());
    assertThat(executionCheck).isNotNull();
    assertThat(executionCheck.isProceed()).isFalse();
    verify(markExpiredInterruptHandler).handleInterruptForNodeExecution(any(), eq(nodeExecution.getUuid()));
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void testPauseAllPreInvocationParent() {
    String planExecutionId = generateUuid();
    Interrupt interrupt =
        Interrupt.builder().uuid(generateUuid()).planExecutionId(planExecutionId).type(InterruptType.PAUSE_ALL).build();
    interruptService.save(interrupt);
    NodeExecution nodeExecution = NodeExecution.builder()
                                      .uuid(generateUuid())
                                      .status(Status.QUEUED)
                                      .planNode(PlanNode.builder().identifier(generateUuid()).build())
                                      .ambiance(Ambiance.newBuilder().build())
                                      .mode(ExecutionMode.CHILD)
                                      .version(1L)
                                      .build();

    when(nodeExecutionService.getWithFieldsIncluded(nodeExecution.getUuid(), NodeProjectionUtils.withStatusAndMode))
        .thenReturn(nodeExecution);

    ExecutionCheck executionCheck =
        interruptService.checkInterruptsPreInvocation(planExecutionId, nodeExecution.getUuid());
    assertThat(executionCheck).isNotNull();
    assertThat(executionCheck.isProceed()).isTrue();
    assertThat(executionCheck.getReason()).isEqualTo("[InterruptCheck] No Interrupts Found");

    verify(nodeExecutionService).getWithFieldsIncluded(nodeExecution.getUuid(), NodeProjectionUtils.withStatusAndMode);
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void testPauseAllPreInvocationNotParentWhereNodeExecutionIdIsNull() {
    String planExecutionId = generateUuid();
    Interrupt interrupt =
        Interrupt.builder().uuid(generateUuid()).planExecutionId(planExecutionId).type(InterruptType.PAUSE_ALL).build();
    interruptService.save(interrupt);

    NodeExecution nodeExecution = NodeExecution.builder()
                                      .uuid(generateUuid())
                                      .status(Status.QUEUED)
                                      .planNode(PlanNode.builder().identifier(generateUuid()).build())
                                      .ambiance(Ambiance.newBuilder().build())
                                      .mode(ExecutionMode.TASK)
                                      .version(1L)
                                      .build();

    when(nodeExecutionService.getWithFieldsIncluded(nodeExecution.getUuid(), NodeProjectionUtils.withStatusAndMode))
        .thenReturn(nodeExecution);
    when(pauseAllInterruptHandler.handleInterruptForNodeExecution(interrupt, nodeExecution.getUuid()))
        .thenReturn(interrupt);

    ExecutionCheck executionCheck =
        interruptService.checkInterruptsPreInvocation(planExecutionId, nodeExecution.getUuid());
    assertThat(executionCheck).isNotNull();
    assertThat(executionCheck.isProceed()).isFalse();
    assertThat(executionCheck.getReason()).isEqualTo("[InterruptCheck] PAUSE_ALL interrupt found");

    verify(nodeExecutionService).getWithFieldsIncluded(nodeExecution.getUuid(), NodeProjectionUtils.withStatusAndMode);
    verify(pauseAllInterruptHandler).handleInterruptForNodeExecution(any(), eq(nodeExecution.getUuid()));
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void testPauseAllPreInvocationNotParentForStageInterruptWhenNodeIsFinal() {
    String planExecutionId = generateUuid();
    String stageNodeExecutionId = generateUuid();
    Interrupt interruptInstance = Interrupt.builder()
                                      .uuid(generateUuid())
                                      .planExecutionId(planExecutionId)
                                      .nodeExecutionId(stageNodeExecutionId)
                                      .type(InterruptType.PAUSE_ALL)
                                      .build();
    interruptService.save(interruptInstance);
    NodeExecution nodeExecution = NodeExecution.builder()
                                      .uuid(stageNodeExecutionId)
                                      .status(Status.SUCCEEDED)
                                      .planNode(PlanNode.builder().identifier(generateUuid()).build())
                                      .ambiance(Ambiance.newBuilder().build())
                                      .mode(ExecutionMode.TASK)
                                      .version(1L)
                                      .build();

    when(nodeExecutionService.getWithFieldsIncluded(nodeExecution.getUuid(), NodeProjectionUtils.withAmbianceAndStatus))
        .thenReturn(nodeExecution);
    when(nodeExecutionService.getWithFieldsIncluded(nodeExecution.getUuid(), NodeProjectionUtils.withStatusAndMode))
        .thenReturn(nodeExecution);

    when(planExecutionService.calculateStatusExcluding(any(), any())).thenReturn(Status.PAUSING);

    ExecutionCheck executionCheck =
        interruptService.checkInterruptsPreInvocation(planExecutionId, stageNodeExecutionId);
    assertThat(executionCheck).isNotNull();
    assertThat(executionCheck.isProceed()).isTrue();
    assertThat(executionCheck.getReason()).isEqualTo("[InterruptCheck] No Interrupts Found");

    verify(nodeExecutionService)
        .getWithFieldsIncluded(nodeExecution.getUuid(), NodeProjectionUtils.withAmbianceAndStatus);
    verify(nodeExecutionService).getWithFieldsIncluded(nodeExecution.getUuid(), NodeProjectionUtils.withStatusAndMode);

    verify(planExecutionService).calculateStatusExcluding(any(), any());
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldTestGet() {
    String interruptId = generateUuid();
    Interrupt expectedInterrupt =
        Interrupt.builder().uuid(interruptId).planExecutionId(generateUuid()).type(InterruptType.EXPIRE_ALL).build();
    interruptService.save(expectedInterrupt);

    Interrupt interrupt = interruptService.get(interruptId);

    assertThat(interrupt).isNotNull();
    assertThat(interrupt.getUuid()).isNotNull();
    assertThat(interrupt.getPlanExecutionId()).isEqualTo(expectedInterrupt.getPlanExecutionId());
    assertThat(interrupt.getType()).isEqualTo(expectedInterrupt.getType());
    assertThat(interrupt.getState()).isEqualTo(REGISTERED);
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldThrowInvalidRequestExceptionWhenGet() {
    String interruptId = generateUuid();
    assertThatThrownBy(() -> interruptService.get(interruptId))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Interrupt Not found for id: " + interruptId);
  }

  private void saveInterruptList(String planExecutionId, boolean retryDiscarded) {
    Interrupt abortAllInterrupt =
        Interrupt.builder().planExecutionId(planExecutionId).type(InterruptType.ABORT_ALL).build();
    Interrupt pauseAllInterrupt =
        Interrupt.builder().planExecutionId(planExecutionId).type(InterruptType.PAUSE_ALL).build();
    Interrupt retryInterrupt = Interrupt.builder()
                                   .planExecutionId(planExecutionId)
                                   .type(InterruptType.RETRY)
                                   .nodeExecutionId(generateUuid())
                                   .state(retryDiscarded ? DISCARDED : REGISTERED)
                                   .build();
    interruptService.save(abortAllInterrupt);
    interruptService.save(pauseAllInterrupt);
    interruptService.save(retryInterrupt);
  }
}
