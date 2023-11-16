/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.interrupts.handlers;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.eraro.ErrorCode.ABORT_ALL_ALREADY_NG;
import static io.harness.exception.WingsException.USER;
import static io.harness.interrupts.Interrupt.State.DISCARDED;
import static io.harness.interrupts.Interrupt.State.PROCESSED_SUCCESSFULLY;
import static io.harness.interrupts.Interrupt.State.PROCESSED_UNSUCCESSFULLY;
import static io.harness.interrupts.Interrupt.State.PROCESSING;

import io.harness.OrchestrationPublisherName;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.data.structure.EmptyPredicate;
import io.harness.engine.executions.plan.PlanExecutionService;
import io.harness.engine.interrupts.InterruptHandler;
import io.harness.engine.interrupts.InterruptService;
import io.harness.engine.interrupts.helpers.AbortHelper;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.NodeExecution;
import io.harness.interrupts.Interrupt;
import io.harness.logging.AutoLogContext;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.interrupts.InterruptType;
import io.harness.pms.execution.utils.StatusUtils;
import io.harness.waiter.WaitNotifyEngine;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.List;
import javax.validation.Valid;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_COMMON_STEPS})
@Slf4j
@OwnedBy(PIPELINE)
public class AbortAllInterruptHandler extends InterruptPropagatorHandler implements InterruptHandler {
  @Inject private InterruptService interruptService;
  @Inject private AbortHelper abortHelper;
  @Inject @Named(OrchestrationPublisherName.PUBLISHER_NAME) String publisherName;
  @Inject private WaitNotifyEngine waitNotifyEngine;
  @Inject private PlanExecutionService planExecutionService;

  @Override
  public Interrupt registerInterrupt(Interrupt interrupt) {
    Status status = planExecutionService.getStatus(interrupt.getPlanExecutionId());
    Interrupt savedInterrupt = validateAndSave(interrupt, status);
    if (status == Status.QUEUED) {
      planExecutionService.updateStatus(interrupt.getPlanExecutionId(), Status.ABORTED);
      return savedInterrupt;
    }
    return isNotEmpty(savedInterrupt.getNodeExecutionId())
        ? handleInterruptForNodeExecution(interrupt, interrupt.getNodeExecutionId())
        : handleInterrupt(savedInterrupt);
  }

  private Interrupt validateAndSave(Interrupt interrupt, Status status) {
    return isNotEmpty(interrupt.getNodeExecutionId()) ? validateAndSaveWithNodeExecution(interrupt)
                                                      : validateAndSaveWithoutNodeExecution(interrupt, status);
  }

  private Interrupt validateAndSaveWithoutNodeExecution(@Valid @NonNull Interrupt interrupt, Status status) {
    List<Interrupt> interrupts = interruptService.fetchActiveInterrupts(interrupt.getPlanExecutionId());
    if (StatusUtils.isFinalStatus(status)) {
      throw new InvalidRequestException(String.format("Execution is already finished with status: [%s]", status));
    }
    return processInterrupt(interrupt, interrupts);
  }

  private Interrupt validateAndSaveWithNodeExecution(@Valid @NonNull Interrupt interrupt) {
    List<Interrupt> interrupts = interruptService.fetchActiveInterruptsForNodeExecution(
        interrupt.getPlanExecutionId(), interrupt.getNodeExecutionId());
    return processInterrupt(interrupt, interrupts);
  }

  private Interrupt processInterrupt(@Valid @NonNull Interrupt interrupt, List<Interrupt> interrupts) {
    if (checkIfInterruptAlreadyPresent(interrupt, interrupts)) {
      throw new InvalidRequestException("Execution already has ABORT_ALL interrupt", ABORT_ALL_ALREADY_NG, USER);
    }
    if (isEmpty(interrupts)) {
      return interruptService.save(interrupt);
    }

    interrupts.forEach(savedInterrupt
        -> interruptService.markProcessed(
            savedInterrupt.getUuid(), savedInterrupt.getState() == PROCESSING ? PROCESSED_SUCCESSFULLY : DISCARDED));
    return interruptService.save(interrupt);
  }

  private boolean checkIfInterruptAlreadyPresent(Interrupt interrupt, List<Interrupt> interrupts) {
    for (Interrupt presentInterrupt : interrupts) {
      if (presentInterrupt.getType() == InterruptType.ABORT_ALL) {
        if (EmptyPredicate.isEmpty(presentInterrupt.getNodeExecutionId())) {
          // Checking if any plan level AbortAll interrupt present.
          return true;
        } else if (presentInterrupt.getNodeExecutionId().equals(interrupt.getNodeExecutionId())) {
          // Checking if AbortAll interrupt present on the same nodeExecutionId;
          return true;
        }
      }
    }
    return false;
  }

  /**
   * This method is applicable for parent node i.e stage/stepGroup etc.
   * For complete pipeline refer Handle interrupt
   */
  @Override
  public Interrupt handleInterruptForNodeExecution(Interrupt interrupt, String nodeExecutionId) {
    try (AutoLogContext ignore = interrupt.autoLogContext()) {
      log.info("Stating to handle interrupt for Node Execution");
      return handleChildNodes(interrupt, nodeExecutionId);
    }
  }

  @Override
  public Interrupt handleInterrupt(@NonNull @Valid Interrupt interrupt) {
    try (AutoLogContext ignore = interrupt.autoLogContext()) {
      log.info("Stating to handle interrupt for Plan Execution");
      return handleAllNodes(interrupt);
    }
  }

  protected Interrupt processDiscontinuedInstances(
      Interrupt updatedInterrupt, List<NodeExecution> discontinuingNodeExecutions) {
    List<String> notifyIds = new ArrayList<>();
    try (AutoLogContext ignore = updatedInterrupt.autoLogContext()) {
      for (NodeExecution discontinuingNodeExecution : discontinuingNodeExecutions) {
        log.info("Trying to abort discontinuing instance {}", discontinuingNodeExecution.getUuid());
        handleMarkedInstance(discontinuingNodeExecution, updatedInterrupt);
        notifyIds.add(discontinuingNodeExecution.getUuid() + "|" + updatedInterrupt.getUuid());
      }

    } catch (Exception ex) {
      log.info("Exception occurred while aborting instance marking interrupt as PROCESSED_UNSUCCESSFULLY");
      interruptService.markProcessed(updatedInterrupt.getUuid(), PROCESSED_UNSUCCESSFULLY);
      throw ex;
    }

    waitNotifyEngine.waitForAllOnInList(
        publisherName, AllInterruptCallback.builder().interrupt(updatedInterrupt).build(), notifyIds);
    return updatedInterrupt;
  }

  @Override
  protected void handleMarkedInstance(NodeExecution nodeExecution, Interrupt interrupt) {
    abortHelper.discontinueMarkedInstance(nodeExecution, interrupt);
  }
}
