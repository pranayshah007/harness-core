/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.interrupts.handlers;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.interrupts.Interrupt.State.DISCARDED;
import static io.harness.interrupts.Interrupt.State.PROCESSED_SUCCESSFULLY;
import static io.harness.interrupts.Interrupt.State.PROCESSED_UNSUCCESSFULLY;
import static io.harness.interrupts.Interrupt.State.PROCESSING;

import io.harness.OrchestrationPublisherName;
import io.harness.annotations.dev.OwnedBy;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.engine.executions.plan.PlanExecutionService;
import io.harness.engine.interrupts.InterruptHandler;
import io.harness.engine.interrupts.InterruptService;
import io.harness.engine.interrupts.helpers.UserMarkedFailAllHelper;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.ExecutionModeUtils;
import io.harness.execution.NodeExecution;
import io.harness.interrupts.Interrupt;
import io.harness.logging.AutoLogContext;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.execution.utils.NodeProjectionUtils;
import io.harness.pms.execution.utils.StatusUtils;
import io.harness.waiter.WaitNotifyEngine;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;
import javax.validation.Valid;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.util.CloseableIterator;

@Slf4j
@OwnedBy(PIPELINE)
public class UserMarkedFailAllInterruptHandler extends InterruptPropagatorHandler implements InterruptHandler {
  @Inject private InterruptService interruptService;
  @Inject private UserMarkedFailAllHelper userMarkedFailAllHelper;
  @Inject @Named(OrchestrationPublisherName.PUBLISHER_NAME) String publisherName;
  @Inject private WaitNotifyEngine waitNotifyEngine;
  @Inject private PlanExecutionService planExecutionService;
  @Inject private NodeExecutionService nodeExecutionService;

  @Override
  public Interrupt registerInterrupt(Interrupt interrupt) {
    Interrupt savedInterrupt = validateAndSave(interrupt);
    return isNotEmpty(savedInterrupt.getNodeExecutionId())
        ? handleInterruptForNodeExecution(interrupt, interrupt.getNodeExecutionId())
        : handleInterrupt(savedInterrupt);
  }

  private Interrupt validateAndSave(Interrupt interrupt) {
    return isNotEmpty(interrupt.getNodeExecutionId()) ? validateAndSaveWithNodeExecution(interrupt)
                                                      : validateAndSaveWithoutNodeExecution(interrupt);
  }

  private Interrupt validateAndSaveWithoutNodeExecution(@Valid @NonNull Interrupt interrupt) {
    List<Interrupt> interrupts = interruptService.fetchActiveInterrupts(interrupt.getPlanExecutionId());
    // Use projections
    Status status = planExecutionService.getStatus(interrupt.getPlanExecutionId());
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
    if (isEmpty(interrupts)) {
      return interruptService.save(interrupt);
    }

    interrupts.forEach(savedInterrupt
        -> interruptService.markProcessed(
            savedInterrupt.getUuid(), savedInterrupt.getState() == PROCESSING ? PROCESSED_SUCCESSFULLY : DISCARDED));
    return interruptService.save(interrupt);
  }

  /**
   * This method is applicable for parent node i.e stage/stepGroup etc.
   * For complete pipeline refer Handle interrupt
   */
  @Override
  public Interrupt handleInterruptForNodeExecution(Interrupt interrupt, String nodeExecutionId) {
    try (AutoLogContext ignore = interrupt.autoLogContext()) {
      log.info("Starting to handle interrupt for Node Execution");
      return handleChildNodes(interrupt, nodeExecutionId);
    }
  }

  @Override
  public Interrupt handleInterrupt(@NonNull @Valid Interrupt interrupt) {
    try (AutoLogContext ignore = interrupt.autoLogContext()) {
      log.info("Starting to handle interrupt for Plan Execution");
      return handleAllNodes(interrupt);
    }
  }

  public Interrupt handleChildNodes(Interrupt interrupt, String nodeExecutionId) {
    Interrupt updatedInterrupt = interruptService.markProcessing(interrupt.getUuid());
    // Find all the nodeExecutions for this plan
    List<NodeExecution> allExecutions = new LinkedList<>();
    try (
        CloseableIterator<NodeExecution> iterator =
            nodeExecutionService.fetchNodeExecutionsWithoutOldRetriesAndStatusInIterator(interrupt.getPlanExecutionId(),
                StatusUtils.userMarkedFailureStatuses(), NodeProjectionUtils.fieldsForInterruptPropagatorHandler)) {
      while (iterator.hasNext()) {
        allExecutions.add(iterator.next());
      }
    }

    List<NodeExecution> finalList = new ArrayList<>();
    // Extract all the running leaf nodes and queued nodeswith the parent id as nodeExecutionId passed in as param
    nodeExecutionService.extractChildExecutions(nodeExecutionId, true, finalList, allExecutions, true);

    List<String> targetIds = finalList.stream()
                                 .filter(ne
                                     -> ExecutionModeUtils.isLeafMode(ne.getMode())
                                         || StatusUtils.abortingStatuses().contains(ne.getStatus()))
                                 .map(NodeExecution::getUuid)
                                 .collect(Collectors.toList());

    long updatedCount = nodeExecutionService.markLeavesDiscontinuing(targetIds);
    return handleDiscontinuingNodes(updatedInterrupt, updatedCount);
  }

  public Interrupt handleAllNodes(Interrupt interrupt) {
    Interrupt updatedInterrupt = interruptService.markProcessing(interrupt.getUuid());
    // Marking all finalizable leaf nodes as DISCONTINUING
    long updatedCount = nodeExecutionService.markAllLeavesAndQueuedNodesDiscontinuing(
        interrupt.getPlanExecutionId(), StatusUtils.userMarkedFailureStatuses());
    return handleDiscontinuingNodes(updatedInterrupt, updatedCount);
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
        publisherName, UserMarkedFailAllInterruptCallback.builder().interrupt(updatedInterrupt).build(), notifyIds);
    return updatedInterrupt;
  }

  @Override
  protected void handleMarkedInstance(NodeExecution nodeExecution, Interrupt interrupt) {
    userMarkedFailAllHelper.discontinueMarkedInstance(nodeExecution, interrupt);
  }
}
