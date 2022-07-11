/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.interrupts.handlers;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.ALEXEI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.OrchestrationTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.engine.interrupts.InterruptService;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.NodeExecution;
import io.harness.interrupts.Interrupt;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.interrupts.InterruptConfig;
import io.harness.pms.contracts.interrupts.InterruptType;
import io.harness.rule.Owner;
import io.harness.waiter.WaitNotifyEngine;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.PIPELINE)
public class ResumeAllInterruptHandlerTest extends OrchestrationTestBase {
  @Mock private InterruptService interruptService;
  @Mock private NodeExecutionService nodeExecutionService;
  @Mock private WaitNotifyEngine waitNotifyEngine;

  @Inject @InjectMocks private ResumeAllInterruptHandler resumeAllInterruptHandler;

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldThrowInvalidRequestExceptionForStageWhenRegisterInterrupt() {
    Interrupt interrupt = Interrupt.builder()
                              .uuid(generateUuid())
                              .type(InterruptType.RESUME_ALL)
                              .interruptConfig(InterruptConfig.newBuilder().build())
                              .planExecutionId(generateUuid())
                              .nodeExecutionId(generateUuid())
                              .build();

    List<Interrupt> activeInterrupts = ImmutableList.of(Interrupt.builder()
                                                            .uuid(generateUuid())
                                                            .type(InterruptType.PAUSE_ALL)
                                                            .interruptConfig(InterruptConfig.newBuilder().build())
                                                            .planExecutionId(interrupt.getPlanExecutionId())
                                                            .nodeExecutionId(interrupt.getNodeExecutionId())
                                                            .build(),
        Interrupt.builder()
            .uuid(generateUuid())
            .type(InterruptType.RESUME_ALL)
            .interruptConfig(InterruptConfig.newBuilder().build())
            .planExecutionId(interrupt.getPlanExecutionId())
            .nodeExecutionId(interrupt.getNodeExecutionId())
            .build());

    when(interruptService.fetchActiveInterrupts(interrupt.getPlanExecutionId())).thenReturn(activeInterrupts);

    assertThatThrownBy(() -> resumeAllInterruptHandler.registerInterrupt(interrupt))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Execution already has RESUME_ALL interrupt");
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldThrowInvalidRequestExceptionForPlanWhenRegisterInterrupt() {
    Interrupt interrupt = Interrupt.builder()
                              .uuid(generateUuid())
                              .type(InterruptType.RESUME_ALL)
                              .interruptConfig(InterruptConfig.newBuilder().build())
                              .planExecutionId(generateUuid())
                              .build();

    List<Interrupt> activeInterrupts = ImmutableList.of(Interrupt.builder()
                                                            .uuid(generateUuid())
                                                            .type(InterruptType.PAUSE_ALL)
                                                            .interruptConfig(InterruptConfig.newBuilder().build())
                                                            .planExecutionId(interrupt.getPlanExecutionId())
                                                            .build(),
        Interrupt.builder()
            .uuid(generateUuid())
            .type(InterruptType.RESUME_ALL)
            .interruptConfig(InterruptConfig.newBuilder().build())
            .planExecutionId(interrupt.getPlanExecutionId())
            .build());

    when(interruptService.fetchActiveInterrupts(interrupt.getPlanExecutionId())).thenReturn(activeInterrupts);

    assertThatThrownBy(() -> resumeAllInterruptHandler.registerInterrupt(interrupt))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Execution already has RESUME_ALL interrupt");
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldThrowInvalidRequestExceptionWhenRegisterInterrupt() {
    Interrupt interrupt = Interrupt.builder()
                              .uuid(generateUuid())
                              .type(InterruptType.RESUME_ALL)
                              .interruptConfig(InterruptConfig.newBuilder().build())
                              .planExecutionId(generateUuid())
                              .build();

    List<Interrupt> activeInterrupts = ImmutableList.of(Interrupt.builder()
                                                            .uuid(generateUuid())
                                                            .type(InterruptType.MARK_EXPIRED)
                                                            .interruptConfig(InterruptConfig.newBuilder().build())
                                                            .planExecutionId(interrupt.getPlanExecutionId())
                                                            .build());

    when(interruptService.fetchActiveInterrupts(interrupt.getPlanExecutionId())).thenReturn(activeInterrupts);

    assertThatThrownBy(() -> resumeAllInterruptHandler.registerInterrupt(interrupt))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("No PAUSE_ALL interrupt present");
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldTestRegisterInterrupt() {
    ArgumentCaptor<Interrupt> interruptArgumentCaptor = ArgumentCaptor.forClass(Interrupt.class);
    Interrupt interrupt = Interrupt.builder()
                              .uuid(generateUuid())
                              .type(InterruptType.RESUME_ALL)
                              .interruptConfig(InterruptConfig.newBuilder().build())
                              .planExecutionId(generateUuid())
                              .build();

    Interrupt activeInterrupt = Interrupt.builder()
                                    .uuid(generateUuid())
                                    .type(InterruptType.PAUSE_ALL)
                                    .interruptConfig(InterruptConfig.newBuilder().build())
                                    .planExecutionId(interrupt.getPlanExecutionId())
                                    .state(Interrupt.State.PROCESSING)
                                    .build();

    List<Interrupt> activeInterrupts = ImmutableList.of(activeInterrupt);

    when(interruptService.fetchActiveInterrupts(interrupt.getPlanExecutionId())).thenReturn(activeInterrupts);

    when(interruptService.markProcessed(any(), eq(Interrupt.State.PROCESSED_SUCCESSFULLY))).thenReturn(activeInterrupt);
    when(waitNotifyEngine.doneWith(any(), any())).thenReturn(null);

    when(interruptService.save(any())).thenReturn(interrupt);

    resumeAllInterruptHandler.registerInterrupt(interrupt);

    verify(interruptService).markProcessed(eq(activeInterrupt.getUuid()), eq(Interrupt.State.PROCESSED_SUCCESSFULLY));
    verify(waitNotifyEngine).doneWith(eq(activeInterrupt.getUuid()), any());
    verify(interruptService).save(interruptArgumentCaptor.capture());

    Interrupt finalInterrupt = interruptArgumentCaptor.getValue();
    assertThat(finalInterrupt).isNotNull();
    assertThat(finalInterrupt.getState()).isEqualTo(Interrupt.State.PROCESSING);
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldThrowWhenHandleInterrupt() {
    assertThatThrownBy(
        ()
            -> resumeAllInterruptHandler.handleInterrupt(Interrupt.builder()
                                                             .type(InterruptType.RESUME_ALL)
                                                             .interruptConfig(InterruptConfig.newBuilder().build())
                                                             .planExecutionId(generateUuid())
                                                             .build()))
        .isInstanceOf(UnsupportedOperationException.class)
        .hasMessageContaining("RESUME_ALL handling Not required for overall Plan");
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldHandleInterruptForNodeExecutionWhenTypeisNotPaused() {
    String nodeExecutionId = generateUuid();
    Interrupt interrupt = Interrupt.builder()
                              .uuid(generateUuid())
                              .type(InterruptType.RESUME_ALL)
                              .interruptConfig(InterruptConfig.newBuilder().build())
                              .planExecutionId(generateUuid())
                              .build();

    when(nodeExecutionService.get(nodeExecutionId)).thenReturn(NodeExecution.builder().status(Status.RUNNING).build());

    resumeAllInterruptHandler.handleInterruptForNodeExecution(interrupt, nodeExecutionId);

    verify(nodeExecutionService, times(0)).updateStatusWithOps(anyString(), any(), any(), any());
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldHandleInterruptForNodeExecution() {
    String nodeExecutionId = generateUuid();
    Interrupt interrupt = Interrupt.builder()
                              .uuid(generateUuid())
                              .type(InterruptType.RESUME_ALL)
                              .interruptConfig(InterruptConfig.newBuilder().build())
                              .planExecutionId(generateUuid())
                              .build();

    when(nodeExecutionService.get(nodeExecutionId)).thenReturn(NodeExecution.builder().status(Status.PAUSED).build());

    resumeAllInterruptHandler.handleInterruptForNodeExecution(interrupt, nodeExecutionId);

    verify(nodeExecutionService).updateStatusWithOps(anyString(), any(), any(), any());
  }
}
