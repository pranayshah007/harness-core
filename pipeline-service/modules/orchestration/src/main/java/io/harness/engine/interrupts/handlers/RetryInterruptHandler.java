/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.interrupts.handlers;
import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.pms.contracts.execution.Status.RUNNING;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.engine.executions.plan.PlanExecutionService;
import io.harness.engine.interrupts.InterruptHandler;
import io.harness.engine.interrupts.InterruptService;
import io.harness.engine.interrupts.helpers.RetryHelper;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.ExecutionModeUtils;
import io.harness.execution.NodeExecution;
import io.harness.interrupts.Interrupt;
import io.harness.interrupts.Interrupt.State;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.interrupts.InterruptType;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.execution.utils.NodeProjectionUtils;
import io.harness.pms.execution.utils.StatusUtils;

import com.google.inject.Inject;
import java.util.List;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_PIPELINE, HarnessModuleComponent.CDS_TEMPLATE_LIBRARY})
@OwnedBy(CDC)
public class RetryInterruptHandler implements InterruptHandler {
  @Inject private RetryHelper retryHelper;
  @Inject private NodeExecutionService nodeExecutionService;
  @Inject private PlanExecutionService planExecutionService;
  @Inject private InterruptService interruptService;

  @Override
  public Interrupt registerInterrupt(Interrupt interrupt) {
    Interrupt savedInterrupt = validateAndSave(interrupt);
    return handleInterruptForNodeExecution(savedInterrupt, savedInterrupt.getNodeExecutionId());
  }

  private Interrupt validateAndSave(Interrupt interrupt) {
    if (isEmpty(interrupt.getNodeExecutionId())) {
      throw new InvalidRequestException("NodeExecutionId Cannot be empty for RETRY interrupt");
    }
    NodeExecution nodeExecution = nodeExecutionService.getWithFieldsIncluded(
        interrupt.getNodeExecutionId(), NodeProjectionUtils.fieldsForRetryInterruptHandler);
    Ambiance ambiance = nodeExecution.getAmbiance();
    boolean isStepGroupRetry =
        (StepCategory.STEP_GROUP.name()).equals(AmbianceUtils.obtainCurrentLevel(ambiance).getStepType().getType());
    if (!StatusUtils.retryableStatuses().contains(nodeExecution.getStatus())) {
      throw new InvalidRequestException(
          "NodeExecution is not in a retryable status. Current Status: " + nodeExecution.getStatus());
    }
    if (nodeExecution.getOldRetry()) {
      throw new InvalidRequestException("This Node is already Retried");
    }

    // If it is stepGroup then retries are supported.
    if (!isStepGroupRetry && ExecutionModeUtils.isParentMode(nodeExecution.getMode())) {
      throw new InvalidRequestException("Node Retry is supported only for Leaf Nodes");
    }
    interrupt.setState(State.PROCESSING);
    List<Interrupt> activeInterrupts = interruptService.fetchActiveInterruptsForNodeExecutionByType(
        interrupt.getPlanExecutionId(), interrupt.getNodeExecutionId(), InterruptType.RETRY);
    if (activeInterrupts.size() > 0) {
      throw new InvalidRequestException("A Retry Interrupt is already in process");
    }
    return interruptService.save(interrupt);
  }

  @Override
  public Interrupt handleInterrupt(Interrupt interrupt) {
    throw new UnsupportedOperationException("Please use handleInterrupt for handling retries");
  }

  @Override
  public Interrupt handleInterruptForNodeExecution(Interrupt interrupt, String nodeExecutionId) {
    retryHelper.retryNodeExecution(interrupt.getNodeExecutionId(), interrupt.getUuid(), interrupt.getInterruptConfig());
    planExecutionService.updateStatus(interrupt.getPlanExecutionId(), RUNNING);
    return interrupt;
  }
}
