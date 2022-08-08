/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.interrupts.handlers;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.pms.contracts.execution.Status.IGNORE_FAILED;
import static io.harness.rule.OwnerRule.PRASHANTSHARMA;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.OrchestrationTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.interrupts.Interrupt;
import io.harness.interrupts.Interrupt.State;
import io.harness.pms.contracts.interrupts.InterruptConfig;
import io.harness.pms.contracts.interrupts.InterruptType;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.PIPELINE)
public class IgnoreFailedInterruptHandlerTest extends OrchestrationTestBase {
  private IgnoreFailedInterruptHandler ignoreFailedInterruptHandler1 = spy(IgnoreFailedInterruptHandler.class);

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testHandleInterruptForNodeExecution() {
    String planExecutionId = generateUuid();
    String interruptUuid = generateUuid();
    String nodeExecutionId = generateUuid();
    Interrupt interrupt = Interrupt.builder()
                              .uuid(interruptUuid)
                              .type(InterruptType.ABORT_ALL)
                              .nodeExecutionId(nodeExecutionId)
                              .interruptConfig(InterruptConfig.newBuilder().build())
                              .planExecutionId(planExecutionId)
                              .state(State.REGISTERED)
                              .build();

    doReturn(interrupt)
        .when(ignoreFailedInterruptHandler1)
        .handleInterruptStatus(interrupt, nodeExecutionId, IGNORE_FAILED);
    ignoreFailedInterruptHandler1.handleInterruptForNodeExecution(interrupt, nodeExecutionId);
    verify(ignoreFailedInterruptHandler1, times(1)).handleInterruptStatus(interrupt, nodeExecutionId, IGNORE_FAILED);
  }
}
