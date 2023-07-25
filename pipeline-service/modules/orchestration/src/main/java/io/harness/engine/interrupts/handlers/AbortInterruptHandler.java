/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.interrupts.handlers;
import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.CollectionUtils.isPresent;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.interrupts.Interrupt.State.DISCARDED;
import static io.harness.interrupts.Interrupt.State.PROCESSED_SUCCESSFULLY;
import static io.harness.interrupts.Interrupt.State.PROCESSED_UNSUCCESSFULLY;
import static io.harness.interrupts.Interrupt.State.PROCESSING;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.engine.interrupts.InterruptHandler;
import io.harness.engine.interrupts.InterruptProcessingFailedException;
import io.harness.engine.interrupts.InterruptService;
import io.harness.engine.interrupts.helpers.AbortHelper;
import io.harness.execution.NodeExecution;
import io.harness.interrupts.Interrupt;
import io.harness.logging.AutoLogContext;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.interrupts.InterruptType;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import java.util.EnumSet;
import java.util.List;
import javax.validation.Valid;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_COMMON_STEPS})
@Slf4j
@OwnedBy(PIPELINE)
public class AbortInterruptHandler implements InterruptHandler {
  @Inject private InterruptService interruptService;
  @Inject private NodeExecutionService nodeExecutionService;
  @Inject private AbortHelper abortHelper;

  @Override
  public Interrupt registerInterrupt(Interrupt interrupt) {
    Interrupt savedInterrupt = validateAndSave(interrupt);
    return handleInterruptForNodeExecution(savedInterrupt, savedInterrupt.getNodeExecutionId());
  }

  @Override
  public Interrupt handleInterrupt(Interrupt interrupt) {
    throw new UnsupportedOperationException("ABORT handling Not required for PLAN");
  }

  @Override
  public Interrupt handleInterruptForNodeExecution(@NotNull Interrupt interrupt, @NotNull String nodeExecutionId) {
    try (AutoLogContext ignore = interrupt.autoLogContext()) {
      NodeExecution nodeExecution = nodeExecutionService.updateStatusWithOps(
          nodeExecutionId, Status.DISCONTINUING, null, EnumSet.noneOf(Status.class));

      if (nodeExecution == null) {
        log.error("Failed to abort node with nodeExecutionId: {}", nodeExecutionId);
        throw new InterruptProcessingFailedException(
            InterruptType.ABORT, "Failed to abort node with nodeExecutionId" + nodeExecutionId);
      }
      abortHelper.discontinueMarkedInstance(nodeExecution, interrupt);

    } catch (Exception ex) {
      interruptService.markProcessed(interrupt.getUuid(), PROCESSED_UNSUCCESSFULLY);
      throw ex;
    }
    return interruptService.markProcessed(interrupt.getUuid(), PROCESSED_SUCCESSFULLY);
  }

  @VisibleForTesting
  Interrupt validateAndSave(Interrupt interrupt) {
    if (isEmpty(interrupt.getNodeExecutionId())) {
      log.error("Failed to abort node with nodeExecutionId: null. NodeExecutionId cannot be null");
      throw new InterruptProcessingFailedException(
          InterruptType.ABORT, "Failed to abort node with nodeExecutionId: null. NodeExecutionId cannot be null");
    }

    List<Interrupt> interrupts = interruptService.fetchActiveInterruptsForNodeExecution(
        interrupt.getPlanExecutionId(), interrupt.getNodeExecutionId());
    return processInterrupt(interrupt, interrupts);
  }

  private Interrupt processInterrupt(@Valid @NonNull Interrupt interrupt, List<Interrupt> interrupts) {
    if (isPresent(interrupts, presentInterrupt -> presentInterrupt.getType() == InterruptType.ABORT)) {
      throw new InterruptProcessingFailedException(InterruptType.ABORT,
          "Execution already contains ABORT interrupt for nodeExecution: " + interrupt.getNodeExecutionId());
    }
    if (isEmpty(interrupts)) {
      return interruptService.save(interrupt);
    }

    interrupts.forEach(savedInterrupt
        -> interruptService.markProcessed(
            savedInterrupt.getUuid(), savedInterrupt.getState() == PROCESSING ? PROCESSED_SUCCESSFULLY : DISCARDED));
    return interruptService.save(interrupt);
  }
}
