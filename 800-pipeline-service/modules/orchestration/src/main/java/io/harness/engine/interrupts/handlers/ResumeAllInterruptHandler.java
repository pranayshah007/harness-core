/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.interrupts.handlers;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.CollectionUtils.isPresent;
import static io.harness.eraro.ErrorCode.ABORT_ALL_ALREADY;
import static io.harness.eraro.ErrorCode.RESUME_ALL_ALREADY;
import static io.harness.exception.WingsException.USER;
import static io.harness.interrupts.Interrupt.State.DISCARDED;
import static io.harness.interrupts.Interrupt.State.PROCESSED_SUCCESSFULLY;
import static io.harness.interrupts.Interrupt.State.PROCESSING;

import io.harness.annotations.dev.OwnedBy;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.engine.interrupts.InterruptHandler;
import io.harness.engine.interrupts.InterruptService;
import io.harness.engine.interrupts.InterruptUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.NodeExecution;
import io.harness.execution.NodeExecution.NodeExecutionKeys;
import io.harness.interrupts.Interrupt;
import io.harness.interrupts.InterruptEffect;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.interrupts.InterruptType;
import io.harness.pms.sdk.core.steps.io.StatusNotifyResponseData;
import io.harness.waiter.WaitNotifyEngine;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PIPELINE)
@Slf4j
public class ResumeAllInterruptHandler implements InterruptHandler {
  @Inject private InterruptService interruptService;
  @Inject private NodeExecutionService nodeExecutionService;
  @Inject private WaitNotifyEngine waitNotifyEngine;

  private final Predicate<Interrupt> resumeAllPredicate = interrupt -> interrupt.getType() == InterruptType.RESUME_ALL;

  @Override
  public Interrupt registerInterrupt(Interrupt interrupt) {
    return validateAndSave(interrupt);
  }

  private Interrupt validateAndSave(Interrupt interrupt) {
    List<Interrupt> interrupts = interruptService.fetchActiveInterrupts(interrupt.getPlanExecutionId());

    // Check if ABORT_ALL present on plan level
    if (isPresent(interrupts, presentInterrupt -> presentInterrupt.getType() == InterruptType.ABORT_ALL)) {
      throw new InvalidRequestException("Execution already has ABORT_ALL interrupt", ABORT_ALL_ALREADY, USER);
    }

    // Check if RESUME_ALL present on plan level
    if (isPresent(interrupts,
            presentInterrupt
            -> presentInterrupt.getType() == InterruptType.RESUME_ALL
                && presentInterrupt.getNodeExecutionId() == null)) {
      throw new InvalidRequestException("Execution already has RESUME_ALL interrupt", RESUME_ALL_ALREADY, USER);
    }

    // Check if RESUME_ALL already for same node
    if (isPresent(interrupts,
            presentInterrupt
            -> presentInterrupt.getType() == InterruptType.RESUME_ALL && presentInterrupt.getNodeExecutionId() != null
                && interrupt.getNodeExecutionId() != null
                && presentInterrupt.getNodeExecutionId().equals(interrupt.getNodeExecutionId()))) {
      throw new InvalidRequestException(
          "Execution already has RESUME_ALL interrupt for node", RESUME_ALL_ALREADY, USER);
    }

    Optional<Interrupt> pauseAllOptional = InterruptUtils.obtainOptionalInterruptFromActiveInterruptsWithPredicates(
        interrupts, interrupt.getPlanExecutionId(), interrupt.getNodeExecutionId(),
        ImmutableList.of(presentInterrupt -> presentInterrupt.getType() == InterruptType.PAUSE_ALL));
    if (!pauseAllOptional.isPresent()) {
      throw new InvalidRequestException("No PAUSE_ALL interrupt present", USER);
    }

    Interrupt pauseAllInterrupt = pauseAllOptional.get();
    interruptService.markProcessed(
        pauseAllInterrupt.getUuid(), pauseAllInterrupt.getState() == PROCESSING ? PROCESSED_SUCCESSFULLY : DISCARDED);
    waitNotifyEngine.doneWith(
        pauseAllInterrupt.getUuid(), StatusNotifyResponseData.builder().status(Status.SUCCEEDED).build());
    interrupt.setState(PROCESSING);
    return interruptService.save(interrupt);
  }

  @Override
  public Interrupt handleInterrupt(Interrupt interrupt) {
    throw new UnsupportedOperationException("RESUME_ALL handling Not required for overall Plan");
  }

  @Override
  public Interrupt handleInterruptForNodeExecution(Interrupt interrupt, String nodeExecutionId) {
    NodeExecution nodeExecution = nodeExecutionService.get(nodeExecutionId);
    if (nodeExecution.getStatus() != Status.PAUSED) {
      return interrupt;
    }
    // Update status
    nodeExecutionService.updateStatusWithOps(nodeExecutionId, Status.QUEUED,
        ops
        -> ops.addToSet(NodeExecutionKeys.interruptHistories,
            InterruptEffect.builder()
                .interruptId(interrupt.getUuid())
                .tookEffectAt(System.currentTimeMillis())
                .interruptType(interrupt.getType())
                .interruptConfig(interrupt.getInterruptConfig())
                .build()),
        EnumSet.noneOf(Status.class));

    return interrupt;
  }
}
