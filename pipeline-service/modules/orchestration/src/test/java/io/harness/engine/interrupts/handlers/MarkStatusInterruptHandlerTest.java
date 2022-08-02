/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.interrupts.handlers;

import static io.harness.pms.contracts.execution.Status.FAILED;
import static io.harness.pms.contracts.execution.Status.RUNNING;
import static io.harness.rule.OwnerRule.BRIJESH;

import static junit.framework.TestCase.assertEquals;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.engine.OrchestrationEngine;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.engine.executions.plan.PlanExecutionService;
import io.harness.engine.interrupts.InterruptService;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.NodeExecution;
import io.harness.interrupts.Interrupt;
import io.harness.interrupts.Interrupt.InterruptBuilder;
import io.harness.interrupts.Interrupt.State;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.interrupts.InterruptConfig;
import io.harness.pms.contracts.interrupts.InterruptType;
import io.harness.pms.execution.utils.NodeProjectionUtils;
import io.harness.pms.execution.utils.StatusUtils;
import io.harness.rule.Owner;

import java.util.Arrays;
import java.util.EnumSet;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.PIPELINE)
public class MarkStatusInterruptHandlerTest extends CategoryTest {
  @Mock private NodeExecutionService nodeExecutionService;
  @Mock private InterruptService interruptService;
  @Mock private OrchestrationEngine orchestrationEngine;
  @Mock private PlanExecutionService planExecutionService;
  String nodeExecutionId = "nodeExecutionId";
  String planExecutionId = "planExecutionId";

  @InjectMocks MarkStatusInterruptHandlerImpl markStatusInterruptHandler;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testRegisterInterrupt() {
    InterruptBuilder interruptBuilder = Interrupt.builder()
                                            .planExecutionId(planExecutionId)
                                            .interruptConfig(InterruptConfig.newBuilder().build())
                                            .type(InterruptType.MARK_EXPIRED);
    assertThatThrownBy(() -> markStatusInterruptHandler.registerInterrupt(interruptBuilder.build()))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("NodeExecutionId Cannot be empty for MARK_SUCCESS interrupt");

    doReturn(NodeExecution.builder().status(Status.RUNNING).build())
        .when(nodeExecutionService)
        .getWithFieldsIncluded(nodeExecutionId, NodeProjectionUtils.withStatus);

    interruptBuilder.nodeExecutionId(nodeExecutionId);
    assertThatThrownBy(() -> markStatusInterruptHandler.registerInterrupt(interruptBuilder.build()))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("NodeExecution is not in a finalizable or broken status. Current Status: " + Status.RUNNING);

    doReturn(NodeExecution.builder().status(FAILED).build())
        .when(nodeExecutionService)
        .getWithFieldsIncluded(nodeExecutionId, NodeProjectionUtils.withStatus);

    ArgumentCaptor<Interrupt> interruptArgumentCaptor = ArgumentCaptor.forClass(Interrupt.class);
    markStatusInterruptHandler.registerInterrupt(interruptBuilder.build());

    verify(interruptService, times(1)).save(interruptArgumentCaptor.capture());

    Interrupt savedInterrupt = interruptArgumentCaptor.getValue();
    assertEquals(savedInterrupt.getState(), Interrupt.State.PROCESSING);
    assertEquals(savedInterrupt.getInterruptConfig(), interruptBuilder.build().getInterruptConfig());
    assertEquals(savedInterrupt.getNodeExecutionId(), nodeExecutionId);
    assertEquals(savedInterrupt.getPlanExecutionId(), planExecutionId);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testHandleInterruptStatus() {
    Ambiance ambiance = Ambiance.newBuilder().build();
    Status status = FAILED;
    Status nonFinalStatus = RUNNING;
    Status fromStatus = RUNNING;
    String interruptUuid = "interruptUuid";
    InterruptBuilder interruptBuilder = Interrupt.builder()
                                            .uuid(interruptUuid)
                                            .planExecutionId(planExecutionId)
                                            .interruptConfig(InterruptConfig.newBuilder().build())
                                            .type(InterruptType.MARK_EXPIRED);

    doReturn(NodeExecution.builder().status(fromStatus).uuid(nodeExecutionId).ambiance(ambiance).build())
        .when(nodeExecutionService)
        .update(eq(nodeExecutionId), any());
    // Returning Final status so planExecutionService should not be called.
    doReturn(Arrays.asList(NodeExecution.builder().uuid("newNodeExecutionId").status(FAILED).build()))
        .when(nodeExecutionService)
        .fetchWithoutRetriesAndStatusIn(planExecutionId, StatusUtils.activeStatuses());
    doReturn(interruptBuilder.state(State.PROCESSED_SUCCESSFULLY).build())
        .when(interruptService)
        .markProcessed(interruptUuid, State.PROCESSED_SUCCESSFULLY);

    Interrupt returnedInterrupt =
        markStatusInterruptHandler.handleInterruptStatus(interruptBuilder.build(), nodeExecutionId, status);

    assertEquals(returnedInterrupt.getState(), State.PROCESSED_SUCCESSFULLY);
    assertEquals(returnedInterrupt.getInterruptConfig(), interruptBuilder.build().getInterruptConfig());
    // 0 interaction because status was final in returned nodeExecutions.
    verify(planExecutionService, times(0)).updateStatus(any(), any());
    verify(orchestrationEngine, times(1))
        .concludeNodeExecution(ambiance, status, fromStatus, EnumSet.noneOf(Status.class));
    verify(interruptService, times(1)).markProcessed(interruptUuid, State.PROCESSED_SUCCESSFULLY);

    // Returning NonFinal status so planExecutionService should be called.
    doReturn(Arrays.asList(NodeExecution.builder().uuid("newNodeExecutionId").status(nonFinalStatus).build()))
        .when(nodeExecutionService)
        .fetchWithoutRetriesAndStatusIn(planExecutionId, StatusUtils.activeStatuses());

    returnedInterrupt =
        markStatusInterruptHandler.handleInterruptStatus(interruptBuilder.build(), nodeExecutionId, status);
    assertEquals(returnedInterrupt.getState(), State.PROCESSED_SUCCESSFULLY);
    assertEquals(returnedInterrupt.getInterruptConfig(), interruptBuilder.build().getInterruptConfig());

    verify(planExecutionService, times(1)).updateStatus(planExecutionId, nonFinalStatus);
    verify(orchestrationEngine, times(2))
        .concludeNodeExecution(ambiance, status, fromStatus, EnumSet.noneOf(Status.class));
    verify(interruptService, times(2)).markProcessed(interruptUuid, State.PROCESSED_SUCCESSFULLY);

    Exception thrownException = new InvalidRequestException("Exception message");
    doThrow(thrownException).when(orchestrationEngine).concludeNodeExecution(any(), any(), any(), any());

    assertThatThrownBy(
        () -> markStatusInterruptHandler.handleInterruptStatus(interruptBuilder.build(), nodeExecutionId, status))
        .isInstanceOf(thrownException.getClass())
        .hasMessage(thrownException.getMessage());
    verify(interruptService, times(1)).markProcessed(interruptUuid, State.PROCESSED_UNSUCCESSFULLY);
  }

  private static class MarkStatusInterruptHandlerImpl extends MarkStatusInterruptHandler {
    @Override
    public Interrupt handleInterruptForNodeExecution(Interrupt interrupt, String nodeExecutionId) {
      return null;
    }
  }
}
