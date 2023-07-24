/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.interrupts.handlers;
import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.interrupts.Interrupt.State.PROCESSED_SUCCESSFULLY;
import static io.harness.interrupts.Interrupt.State.PROCESSED_UNSUCCESSFULLY;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.engine.interrupts.InterruptHandler;
import io.harness.engine.interrupts.InterruptProcessingFailedException;
import io.harness.engine.interrupts.InterruptService;
import io.harness.engine.interrupts.helpers.ExpiryHelper;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.NodeExecution;
import io.harness.interrupts.Interrupt;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.interrupts.InterruptType;
import io.harness.pms.execution.utils.NodeProjectionUtils;
import io.harness.pms.execution.utils.StatusUtils;

import com.google.inject.Inject;
import java.util.EnumSet;
import javax.validation.Valid;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_COMMON_STEPS})
@OwnedBy(CDC)
@Slf4j
public class MarkExpiredInterruptHandler implements InterruptHandler {
  @Inject private InterruptService interruptService;
  @Inject private NodeExecutionService nodeExecutionService;
  @Inject private ExpiryHelper expiryHelper;

  @Override
  public Interrupt registerInterrupt(Interrupt interrupt) {
    Interrupt savedInterrupt = validateAndSave(interrupt);
    return handleInterruptForNodeExecution(savedInterrupt, interrupt.getNodeExecutionId());
  }

  private Interrupt validateAndSave(@Valid @NonNull Interrupt interrupt) {
    if (isEmpty(interrupt.getNodeExecutionId())) {
      throw new InvalidRequestException("NodeExecutionId Cannot be empty for MARK_EXPIRED interrupt");
    }

    NodeExecution nodeExecution =
        nodeExecutionService.getWithFieldsIncluded(interrupt.getNodeExecutionId(), NodeProjectionUtils.withStatus);
    if (!StatusUtils.finalizableStatuses().contains(nodeExecution.getStatus())) {
      throw new InvalidRequestException(
          "NodeExecution is not in a finalizable status. Current Status: " + nodeExecution.getStatus());
    }

    interrupt.setState(Interrupt.State.PROCESSING);
    return interruptService.save(interrupt);
  }

  @Override
  public Interrupt handleInterruptForNodeExecution(Interrupt interrupt, String nodeExecutionId) {
    try {
      NodeExecution nodeExecution = nodeExecutionService.updateStatusWithOps(
          nodeExecutionId, Status.DISCONTINUING, null, EnumSet.noneOf(Status.class));
      if (nodeExecution == null) {
        log.error("Failed to expire node with nodeExecutionId: {}", nodeExecutionId);
        throw new InterruptProcessingFailedException(
            InterruptType.MARK_EXPIRED, "Failed to expire node with nodeExecutionId" + nodeExecutionId);
      }
      expiryHelper.expireMarkedInstance(nodeExecution, interrupt);
    } catch (Exception ex) {
      interruptService.markProcessed(interrupt.getUuid(), PROCESSED_UNSUCCESSFULLY);
      throw ex;
    }
    return interruptService.markProcessed(interrupt.getUuid(), PROCESSED_SUCCESSFULLY);
  }

  @Override
  public Interrupt handleInterrupt(@NonNull @Valid Interrupt interrupt) {
    throw new UnsupportedOperationException("MARK_EXPIRED handling Not required for node individually");
  }
}
