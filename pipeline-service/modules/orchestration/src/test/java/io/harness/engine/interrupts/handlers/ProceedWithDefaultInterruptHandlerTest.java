/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.interrupts.handlers;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.BRIJESH;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.OrchestrationTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.engine.execution.ExecutionInputService;
import io.harness.engine.interrupts.InterruptProcessingFailedException;
import io.harness.engine.interrupts.InterruptService;
import io.harness.interrupts.Interrupt;
import io.harness.pms.contracts.interrupts.InterruptConfig;
import io.harness.pms.contracts.interrupts.InterruptType;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.PIPELINE)
public class ProceedWithDefaultInterruptHandlerTest extends OrchestrationTestBase {
  @Inject @InjectMocks private ProceedWithDefaultInterruptHandler proceedWithDefaultInterruptHandler;
  @Mock InterruptService interruptService;
  @Mock ExecutionInputService executionInputService;

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void shouldTestRegisterInterrupt() {
    String nodeExecutionId = generateUuid();
    String planExecutionId = generateUuid();

    String interruptUuid = generateUuid();
    assertThatThrownBy(()
                           -> proceedWithDefaultInterruptHandler.registerInterrupt(
                               Interrupt.builder()
                                   .planExecutionId(planExecutionId)
                                   .uuid(interruptUuid)
                                   .interruptConfig(InterruptConfig.newBuilder().build())
                                   .type(InterruptType.PROCEED_WITH_DEFAULT)
                                   .build()))
        .isInstanceOf(InterruptProcessingFailedException.class);

    verify(interruptService, times(0)).save(any());

    Interrupt interrupt = Interrupt.builder()
                              .planExecutionId(planExecutionId)
                              .uuid(interruptUuid)
                              .interruptConfig(InterruptConfig.newBuilder().build())
                              .type(InterruptType.PROCEED_WITH_DEFAULT)
                              .nodeExecutionId(nodeExecutionId)
                              .build();
    doReturn(interrupt).when(interruptService).save(interrupt);
    proceedWithDefaultInterruptHandler.registerInterrupt(interrupt);
    verify(interruptService, times(1)).save(any());

    verify(executionInputService, times(1)).continueWithDefault(nodeExecutionId);
  }
}
