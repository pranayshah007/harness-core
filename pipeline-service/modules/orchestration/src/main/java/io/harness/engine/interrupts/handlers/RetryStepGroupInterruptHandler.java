/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.interrupts.handlers;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.exception.InvalidRequestException;
import io.harness.execution.NodeExecution;
import io.harness.interrupts.Interrupt;
import io.harness.pms.contracts.interrupts.InterruptType;
import io.harness.pms.execution.utils.NodeProjectionUtils;
import io.harness.pms.execution.utils.StatusUtils;

import java.util.List;

public class RetryStepGroupInterruptHandler extends RetryInterruptHandler {
  @Override
  protected Interrupt validateAndSave(Interrupt interrupt) {
    if (isEmpty(interrupt.getNodeExecutionId())) {
      throw new InvalidRequestException("NodeExecutionId Cannot be empty for RETRY interrupt");
    }
    NodeExecution nodeExecution = nodeExecutionService.getWithFieldsIncluded(
        interrupt.getNodeExecutionId(), NodeProjectionUtils.fieldsForRetryInterruptHandler);
    if (!StatusUtils.retryableStatuses().contains(nodeExecution.getStatus())) {
      throw new InvalidRequestException(
          "NodeExecution is not in a retryable status. Current Status: " + nodeExecution.getStatus());
    }
    if (nodeExecution.getOldRetry()) {
      throw new InvalidRequestException("This Node is already Retried");
    }

    interrupt.setState(Interrupt.State.PROCESSING);
    List<Interrupt> activeInterrupts = interruptService.fetchActiveInterruptsForNodeExecutionByType(
        interrupt.getPlanExecutionId(), interrupt.getNodeExecutionId(), InterruptType.RETRY);
    if (activeInterrupts.size() > 0) {
      throw new InvalidRequestException("A Retry Interrupt is already in process");
    }
    return interruptService.save(interrupt);
  }
}
