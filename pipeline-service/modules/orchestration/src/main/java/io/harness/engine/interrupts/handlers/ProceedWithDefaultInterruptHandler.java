/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.interrupts.handlers;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.engine.OrchestrationEngine;
import io.harness.engine.execution.ExecutionInputService;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.engine.interrupts.InterruptHandler;
import io.harness.engine.interrupts.InterruptProcessingFailedException;
import io.harness.engine.interrupts.InterruptService;
import io.harness.interrupts.Interrupt;
import io.harness.logging.AutoLogContext;
import io.harness.pms.contracts.interrupts.InterruptType;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import javax.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ProceedWithDefaultInterruptHandler implements InterruptHandler {
  @Inject private InterruptService interruptService;
  @Inject private ExecutionInputService executionInputService;
  @Inject private NodeExecutionService nodeExecutionService;
  @Inject private OrchestrationEngine orchestrationEngine;

  @Override
  public Interrupt registerInterrupt(Interrupt interrupt) {
    Interrupt savedInterrupt = validateAndSave(interrupt);
    return handleInterruptForNodeExecution(savedInterrupt, savedInterrupt.getNodeExecutionId());
  }

  @Override
  public Interrupt handleInterrupt(Interrupt interrupt) {
    throw new UnsupportedOperationException("ProceedWithDefault handling not required for PLAN");
  }

  @Override
  public Interrupt handleInterruptForNodeExecution(@NotNull Interrupt interrupt, @NotNull String nodeExecutionId) {
    try (AutoLogContext ignore = interrupt.autoLogContext()) {
      executionInputService.continueWithDefault(nodeExecutionId);
      return interrupt;
    }
  }

  @VisibleForTesting
  Interrupt validateAndSave(Interrupt interrupt) {
    if (isEmpty(interrupt.getNodeExecutionId())) {
      log.error("Failed to proceed with default value for nodeExecutionId: null. NodeExecutionId cannot be null");
      throw new InterruptProcessingFailedException(InterruptType.ABORT,
          "Failed to proceed with default value for nodeExecutionId: null. NodeExecutionId cannot be null");
    }
    return interruptService.save(interrupt);
  }
}
