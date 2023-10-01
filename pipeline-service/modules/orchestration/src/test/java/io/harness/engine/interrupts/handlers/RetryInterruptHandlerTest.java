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
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.engine.executions.plan.PlanExecutionService;
import io.harness.engine.interrupts.InterruptService;
import io.harness.engine.interrupts.helpers.RetryHelper;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.NodeExecution;
import io.harness.interrupts.Interrupt;
import io.harness.interrupts.Interrupt.InterruptBuilder;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.ExecutionMode;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.interrupts.InterruptConfig;
import io.harness.pms.contracts.interrupts.InterruptType;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.NodeProjectionUtils;
import io.harness.rule.Owner;

import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.PIPELINE)
public class RetryInterruptHandlerTest extends CategoryTest {
  @Mock private NodeExecutionService nodeExecutionService;
  @Mock private InterruptService interruptService;
  @Mock private PlanExecutionService planExecutionService;
  @Mock private RetryHelper retryHelper;

  @InjectMocks RetryInterruptHandler retryInterruptHandler;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testRegisterInterrupt() {
    String nodeExecutionId = "nodeExecutionId";
    String planExecutionId = "planExecutionId";
    String interruptUuid = "interruptUuid";
    InterruptBuilder interruptBuilder = Interrupt.builder()
                                            .planExecutionId(planExecutionId)
                                            .uuid(interruptUuid)
                                            .interruptConfig(InterruptConfig.newBuilder().build())
                                            .type(InterruptType.MARK_EXPIRED);

    assertThatThrownBy(() -> retryInterruptHandler.registerInterrupt(interruptBuilder.build()))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("NodeExecutionId Cannot be empty for RETRY interrupt");

    doReturn(NodeExecution.builder()
                 .status(Status.SUCCEEDED)
                 .ambiance(Ambiance.newBuilder()
                               .addLevels(Level.newBuilder().setStepType(StepType.newBuilder().setType("step")).build())
                               .build())
                 .build())
        .when(nodeExecutionService)
        .getWithFieldsIncluded(nodeExecutionId, NodeProjectionUtils.fieldsForRetryInterruptHandler);

    interruptBuilder.nodeExecutionId(nodeExecutionId);
    assertThatThrownBy(() -> retryInterruptHandler.registerInterrupt(interruptBuilder.build()))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("NodeExecution is not in a retryable status. Current Status: " + Status.SUCCEEDED);

    doReturn(NodeExecution.builder()
                 .oldRetry(true)
                 .status(FAILED)
                 .ambiance(Ambiance.newBuilder()
                               .addLevels(Level.newBuilder().setStepType(StepType.newBuilder().setType("step")).build())
                               .build())
                 .build())
        .when(nodeExecutionService)
        .getWithFieldsIncluded(nodeExecutionId, NodeProjectionUtils.fieldsForRetryInterruptHandler);

    assertThatThrownBy(() -> retryInterruptHandler.registerInterrupt(interruptBuilder.build()))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("This Node is already Retried");

    doReturn(NodeExecution.builder()
                 .mode(ExecutionMode.CHILDREN)
                 .status(FAILED)
                 .ambiance(Ambiance.newBuilder()
                               .addLevels(Level.newBuilder().setStepType(StepType.newBuilder().setType("step")).build())
                               .build())
                 .build())
        .when(nodeExecutionService)
        .getWithFieldsIncluded(nodeExecutionId, NodeProjectionUtils.fieldsForRetryInterruptHandler);

    assertThatThrownBy(() -> retryInterruptHandler.registerInterrupt(interruptBuilder.build()))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Node Retry is supported only for Leaf Nodes");

    doReturn(NodeExecution.builder()
                 .mode(ExecutionMode.TASK)
                 .status(FAILED)
                 .ambiance(Ambiance.newBuilder()
                               .addLevels(Level.newBuilder().setStepType(StepType.newBuilder().setType("step")).build())
                               .build())
                 .build())
        .when(nodeExecutionService)
        .getWithFieldsIncluded(nodeExecutionId, NodeProjectionUtils.fieldsForRetryInterruptHandler);

    doReturn(Collections.singletonList(interruptBuilder.build()))
        .when(interruptService)
        .fetchActiveInterruptsForNodeExecutionByType(planExecutionId, nodeExecutionId, InterruptType.RETRY);
    assertThatThrownBy(() -> retryInterruptHandler.registerInterrupt(interruptBuilder.build()))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("A Retry Interrupt is already in process");

    doReturn(Collections.emptyList())
        .when(interruptService)
        .fetchActiveInterruptsForNodeExecutionByType(planExecutionId, nodeExecutionId, InterruptType.RETRY);

    doReturn(interruptBuilder.state(Interrupt.State.PROCESSING).build()).when(interruptService).save(any());
    Interrupt returnedInterrupt = retryInterruptHandler.registerInterrupt(interruptBuilder.build());
    ArgumentCaptor<Interrupt> interruptArgumentCaptor = ArgumentCaptor.forClass(Interrupt.class);
    verify(interruptService, times(1)).save(interruptArgumentCaptor.capture());
    Interrupt savedInterrupt = interruptArgumentCaptor.getValue();
    assertEquals(savedInterrupt.getState(), Interrupt.State.PROCESSING);

    verify(retryHelper, times(1))
        .retryNodeExecution(nodeExecutionId, interruptUuid, interruptBuilder.build().getInterruptConfig());
    verify(planExecutionService, times(1)).updateStatus(planExecutionId, RUNNING);

    assertEquals(returnedInterrupt.getInterruptConfig(), interruptBuilder.build().getInterruptConfig());
    assertEquals(returnedInterrupt.getState(), Interrupt.State.PROCESSING);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testHandleInterrupt() {
    assertThatThrownBy(() -> retryInterruptHandler.handleInterrupt(null))
        .isInstanceOf(UnsupportedOperationException.class)
        .hasMessage("Please use handleInterrupt for handling retries");
  }
}
