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
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.OrchestrationTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.engine.executions.plan.PlanExecutionService;
import io.harness.engine.interrupts.InterruptService;
import io.harness.engine.pms.resume.EngineResumeAllCallback;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.NodeExecution;
import io.harness.execution.PlanExecution;
import io.harness.interrupts.Interrupt;
import io.harness.plan.PlanNode;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.interrupts.InterruptConfig;
import io.harness.pms.contracts.interrupts.InterruptType;
import io.harness.rule.Owner;
import io.harness.waiter.WaitNotifyEngine;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import java.util.EnumSet;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.PIPELINE)
public class PauseAllInterruptHandlerTest extends OrchestrationTestBase {
  @Mock private InterruptService interruptService;
  @Mock private NodeExecutionService nodeExecutionService;
  @Mock private PlanExecutionService planExecutionService;
  @Mock private WaitNotifyEngine waitNotifyEngine;

  @Inject @InjectMocks private PauseAllInterruptHandler pauseAllInterruptHandler;

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldThrowInvalidRequestExceptionForStageWhenRegisterInterrupt() {
    Interrupt interrupt = Interrupt.builder()
                              .uuid(generateUuid())
                              .type(InterruptType.PAUSE_ALL)
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
                                                            .build());

    when(interruptService.fetchActiveInterrupts(interrupt.getPlanExecutionId())).thenReturn(activeInterrupts);

    assertThatThrownBy(() -> pauseAllInterruptHandler.registerInterrupt(interrupt))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Execution already has PAUSE_ALL interrupt for node");
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldThrowInvalidRequestExceptionForPlanWhenRegisterInterrupt() {
    Interrupt interrupt = Interrupt.builder()
                              .uuid(generateUuid())
                              .type(InterruptType.PAUSE_ALL)
                              .interruptConfig(InterruptConfig.newBuilder().build())
                              .planExecutionId(generateUuid())
                              .build();

    List<Interrupt> activeInterrupts = ImmutableList.of(Interrupt.builder()
                                                            .uuid(generateUuid())
                                                            .type(InterruptType.PAUSE_ALL)
                                                            .interruptConfig(InterruptConfig.newBuilder().build())
                                                            .planExecutionId(interrupt.getPlanExecutionId())
                                                            .build());

    when(interruptService.fetchActiveInterrupts(interrupt.getPlanExecutionId())).thenReturn(activeInterrupts);

    assertThatThrownBy(() -> pauseAllInterruptHandler.registerInterrupt(interrupt))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Execution already has PAUSE_ALL interrupt");
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldTestRegisterInterrupt() {
    ArgumentCaptor<Interrupt> interruptArgumentCaptor = ArgumentCaptor.forClass(Interrupt.class);
    Interrupt interrupt = Interrupt.builder()
                              .uuid(generateUuid())
                              .type(InterruptType.PAUSE_ALL)
                              .interruptConfig(InterruptConfig.newBuilder().build())
                              .planExecutionId(generateUuid())
                              .build();

    List<Interrupt> activeInterrupts = ImmutableList.of(Interrupt.builder()
                                                            .uuid(generateUuid())
                                                            .type(InterruptType.RESUME_ALL)
                                                            .interruptConfig(InterruptConfig.newBuilder().build())
                                                            .planExecutionId(interrupt.getPlanExecutionId())
                                                            .state(Interrupt.State.PROCESSING)
                                                            .build());

    PlanExecution planExecution = PlanExecution.builder().uuid(interrupt.getPlanExecutionId()).build();

    when(interruptService.fetchActiveInterrupts(interrupt.getPlanExecutionId())).thenReturn(activeInterrupts);

    when(planExecutionService.get(interrupt.getPlanExecutionId())).thenReturn(planExecution);
    when(planExecutionService.updateStatus(interrupt.getPlanExecutionId(), Status.PAUSING)).thenReturn(null);

    when(interruptService.markProcessed(any(), eq(Interrupt.State.PROCESSING))).thenReturn(activeInterrupts.get(0));

    when(interruptService.save(any())).thenReturn(interrupt);

    pauseAllInterruptHandler.registerInterrupt(interrupt);

    verify(interruptService).markProcessed(any(), eq(Interrupt.State.PROCESSED_SUCCESSFULLY));
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
            -> pauseAllInterruptHandler.handleInterrupt(Interrupt.builder()
                                                            .type(InterruptType.PAUSE_ALL)
                                                            .interruptConfig(InterruptConfig.newBuilder().build())
                                                            .planExecutionId(generateUuid())
                                                            .build()))
        .isInstanceOf(UnsupportedOperationException.class)
        .hasMessageContaining("PAUSE_ALL handling Not required for overall Plan");
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldTestHandleInterruptForNodeExecution() {
    Ambiance ambiance = Ambiance.newBuilder().build();
    String nodeExecutionId = generateUuid();
    String interruptUuid = generateUuid();
    when(nodeExecutionService.updateStatusWithOps(
             eq(nodeExecutionId), eq(Status.PAUSED), any(), eq(EnumSet.noneOf(Status.class))))
        .thenReturn(NodeExecution.builder()
                        .uuid(generateUuid())
                        .status(Status.PAUSED)
                        .parentId(generateUuid())
                        .planNode(PlanNode.builder().identifier(generateUuid()).build())
                        .ambiance(ambiance)
                        .version(1L)
                        .build());
    when(waitNotifyEngine.waitForAllOn(any(), any(), eq(interruptUuid))).thenReturn(null);

    pauseAllInterruptHandler.handleInterruptForNodeExecution(Interrupt.builder()
                                                                 .uuid(interruptUuid)
                                                                 .type(InterruptType.PAUSE_ALL)
                                                                 .interruptConfig(InterruptConfig.newBuilder().build())
                                                                 .planExecutionId(generateUuid())
                                                                 .build(),
        nodeExecutionId);

    ArgumentCaptor<EngineResumeAllCallback> captor = ArgumentCaptor.forClass(EngineResumeAllCallback.class);
    verify(nodeExecutionService)
        .updateStatusWithOps(eq(nodeExecutionId), eq(Status.PAUSED), any(), eq(EnumSet.noneOf(Status.class)));
    verify(waitNotifyEngine).waitForAllOn(any(), captor.capture(), eq(interruptUuid));
    assertThat(captor.getValue().getAmbiance()).isEqualTo(ambiance);
  }
}
