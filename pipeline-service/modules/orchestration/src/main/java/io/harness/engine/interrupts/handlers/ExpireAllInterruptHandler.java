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
import static io.harness.eraro.ErrorCode.EXPIRE_ALL_ALREADY;
import static io.harness.exception.WingsException.USER;

import io.harness.annotations.dev.OwnedBy;
import io.harness.engine.executions.plan.PlanExecutionService;
import io.harness.engine.interrupts.InterruptHandler;
import io.harness.engine.interrupts.InterruptService;
import io.harness.engine.interrupts.helpers.ExpiryHelper;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.NodeExecution;
import io.harness.execution.PlanExecution;
import io.harness.interrupts.Interrupt;
import io.harness.pms.contracts.interrupts.InterruptType;
import io.harness.pms.execution.utils.StatusUtils;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.List;
import java.util.concurrent.ExecutorService;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PIPELINE)
@Slf4j
public class ExpireAllInterruptHandler extends InterruptPropagatorHandler implements InterruptHandler {
  @Inject private InterruptService interruptService;
  @Inject private PlanExecutionService planExecutionService;
  @Inject private ExpiryHelper expiryHelper;
  @Inject @Named("EngineExecutorService") private ExecutorService executorService;

  @Override
  public Interrupt registerInterrupt(Interrupt interrupt) {
    List<Interrupt> interrupts = interruptService.fetchActivePlanLevelInterrupts(interrupt.getPlanExecutionId());

    // Check if ABORT_ALL present on plan level
    if (isPresent(interrupts, presentInterrupt -> presentInterrupt.getType() == InterruptType.ABORT_ALL)) {
      throw new InvalidRequestException("Execution already has ABORT_ALL interrupt", ABORT_ALL_ALREADY, USER);
    }

    // Check if EXPIRE_ALL already
    if (isPresent(interrupts,
            presentInterrupt
            -> presentInterrupt.getType() == InterruptType.EXPIRE_ALL
                && presentInterrupt.getNodeExecutionId() == null)) {
      throw new InvalidRequestException("Execution already has EXPIRE_ALL interrupt", EXPIRE_ALL_ALREADY, USER);
    }

    // Check if EXPIRE_ALL already for same node
    if (isPresent(interrupts,
            presentInterrupt
            -> presentInterrupt.getType() == InterruptType.EXPIRE_ALL && presentInterrupt.getNodeExecutionId() != null
                && interrupt.getNodeExecutionId() != null
                && presentInterrupt.getNodeExecutionId().equals(interrupt.getNodeExecutionId()))) {
      throw new InvalidRequestException(
          "Execution already has EXPIRE_ALL interrupt for node", EXPIRE_ALL_ALREADY, USER);
    }

    // Check if plan is running
    PlanExecution planExecution = planExecutionService.get(interrupt.getPlanExecutionId());
    if (StatusUtils.isFinalStatus(planExecution.getStatus())) {
      throw new InvalidRequestException("Plan Execution is already finished");
    }

    Interrupt savedInterrupt = interruptService.save(interrupt);
    executorService.submit(() -> {
      if (interrupt.getNodeExecutionId() != null) {
        handleInterruptForNodeExecution(savedInterrupt, interrupt.getNodeExecutionId());
      }
      handleInterrupt(savedInterrupt);
    });
    return savedInterrupt;
  }

  @Override
  public Interrupt handleInterrupt(Interrupt interrupt) {
    return handleAllNodes(interrupt);
  }

  @Override
  protected void handleMarkedInstance(NodeExecution nodeExecution, Interrupt interrupt) {
    expiryHelper.expireMarkedInstance(nodeExecution, interrupt);
  }

  @Override
  public Interrupt handleInterruptForNodeExecution(Interrupt interrupt, String nodeExecutionId) {
    return handleChildNodes(interrupt, nodeExecutionId);
  }
}
