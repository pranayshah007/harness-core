/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.interrupts.handlers;

import static io.harness.rule.OwnerRule.BRIJESH;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.interrupts.Interrupt;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.interrupts.InterruptConfig;
import io.harness.pms.contracts.interrupts.InterruptType;
import io.harness.rule.Owner;

import java.util.EnumSet;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.PIPELINE)
public class MarkSuccessInterruptHandlerTest extends CategoryTest {
  MarkSuccessInterruptHandler markSuccessInterruptHandler = spy(MarkSuccessInterruptHandler.class);

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testHandleInterruptForNodeExecution() {
    String nodeExecutionId = "nodeExecutionId";
    String planExecutionId = "planExecutionId";
    Interrupt interrupt = Interrupt.builder()
                              .planExecutionId(planExecutionId)
                              .interruptConfig(InterruptConfig.newBuilder().build())
                              .type(InterruptType.MARK_EXPIRED)
                              .build();
    doReturn(null).when(markSuccessInterruptHandler).handleInterruptStatus(any(), any(), any(), any());
    markSuccessInterruptHandler.handleInterruptForNodeExecution(interrupt, nodeExecutionId);

    verify(markSuccessInterruptHandler, times(1))
        .handleInterruptStatus(interrupt, nodeExecutionId, Status.SUCCEEDED,
            EnumSet.of(
                Status.FAILED, Status.EXPIRED, Status.ERRORED, Status.INTERVENTION_WAITING, Status.APPROVAL_REJECTED));
  }
}
