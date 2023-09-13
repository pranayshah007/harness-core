/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.interrupts.handlers;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.interrupts.Interrupt.State.PROCESSED_SUCCESSFULLY;
import static io.harness.interrupts.Interrupt.State.PROCESSED_UNSUCCESSFULLY;
import static io.harness.rule.OwnerRule.BRIJESH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.OrchestrationTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.engine.interrupts.InterruptProcessingFailedException;
import io.harness.engine.interrupts.InterruptService;
import io.harness.engine.interrupts.helpers.ExpiryHelper;
import io.harness.execution.NodeExecution;
import io.harness.interrupts.Interrupt;
import io.harness.interrupts.Interrupt.State;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.interrupts.InterruptConfig;
import io.harness.pms.contracts.interrupts.InterruptType;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.util.EnumSet;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.PIPELINE)
public class MarkExpiredInterruptHandlerTest extends OrchestrationTestBase {
  @Mock private NodeExecutionService nodeExecutionService;
  @Mock private ExpiryHelper expiryHelper;
  @Mock private InterruptService interruptService;
  @Inject @InjectMocks private MarkExpiredInterruptHandler interruptHandler;

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testHandleAndMarkInterruptForNodeExecution() {
    String planExecutionId = generateUuid();
    String nodeExecutionId = generateUuid();
    Interrupt interrupt = Interrupt.builder()
                              .uuid(generateUuid())
                              .type(InterruptType.MARK_EXPIRED)
                              .interruptConfig(InterruptConfig.newBuilder().build())
                              .planExecutionId(planExecutionId)
                              .state(State.REGISTERED)
                              .build();
    assertThatThrownBy(() -> interruptHandler.handleAndMarkInterruptForNodeExecution(interrupt, nodeExecutionId, false))
        .isInstanceOf(InterruptProcessingFailedException.class);
    // No interaction with the interruptService.markProcessed because `markInterruptAsProcessed` was passed as false.
    verify(interruptService, times(0)).markProcessed(interrupt.getUuid(), PROCESSED_UNSUCCESSFULLY);

    assertThatThrownBy(() -> interruptHandler.handleAndMarkInterruptForNodeExecution(interrupt, nodeExecutionId, true))
        .isInstanceOf(InterruptProcessingFailedException.class);
    // 1 interaction with the interruptService.markProcessed because `markInterruptAsProcessed` was passed as true.
    verify(interruptService, times(1)).markProcessed(interrupt.getUuid(), PROCESSED_UNSUCCESSFULLY);

    Interrupt returnedInterrupt;
    NodeExecution nodeExecution = NodeExecution.builder().build();
    doReturn(nodeExecution)
        .when(nodeExecutionService)
        .updateStatusWithOps(nodeExecutionId, Status.DISCONTINUING, null, EnumSet.noneOf(Status.class));
    doReturn(Interrupt.builder()
                 .uuid(generateUuid())
                 .type(InterruptType.MARK_EXPIRED)
                 .interruptConfig(InterruptConfig.newBuilder().build())
                 .planExecutionId(planExecutionId)
                 .state(PROCESSED_SUCCESSFULLY)
                 .build())
        .when(interruptService)
        .markProcessed(interrupt.getUuid(), PROCESSED_SUCCESSFULLY);

    returnedInterrupt = interruptHandler.handleAndMarkInterruptForNodeExecution(interrupt, nodeExecutionId, false);
    verify(expiryHelper, times(1)).expireMarkedInstance(nodeExecution, interrupt);
    // No interaction with the interruptService.markProcessed because `markInterruptAsProcessed` was passed as false.
    verify(interruptService, times(0)).markProcessed(interrupt.getUuid(), PROCESSED_SUCCESSFULLY);
    assertThat(returnedInterrupt.getState()).isEqualTo(interrupt.getState());

    returnedInterrupt = interruptHandler.handleAndMarkInterruptForNodeExecution(interrupt, nodeExecutionId, true);
    verify(expiryHelper, times(2)).expireMarkedInstance(nodeExecution, interrupt);
    // 1 interaction with the interruptService.markProcessed because `markInterruptAsProcessed` was passed as true.
    verify(interruptService, times(1)).markProcessed(interrupt.getUuid(), PROCESSED_SUCCESSFULLY);
    assertThat(returnedInterrupt.getState()).isEqualTo(PROCESSED_SUCCESSFULLY);
  }
}
