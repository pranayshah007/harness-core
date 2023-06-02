/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.interrupts;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.interrupts.Interrupt.State;
import static io.harness.interrupts.Interrupt.State.PROCESSED_SUCCESSFULLY;
import static io.harness.interrupts.Interrupt.State.PROCESSING;
import static io.harness.interrupts.Interrupt.State.REGISTERED;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

import io.harness.annotations.dev.OwnedBy;
import io.harness.engine.ExecutionCheck;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.engine.executions.plan.PlanExecutionService;
import io.harness.engine.interrupts.handlers.AbortInterruptHandler;
import io.harness.engine.interrupts.handlers.MarkExpiredInterruptHandler;
import io.harness.engine.interrupts.handlers.PauseAllInterruptHandler;
import io.harness.engine.interrupts.handlers.ResumeAllInterruptHandler;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.ExecutionModeUtils;
import io.harness.execution.NodeExecution;
import io.harness.interrupts.Interrupt;
import io.harness.interrupts.Interrupt.InterruptKeys;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.interrupts.InterruptType;
import io.harness.pms.execution.utils.NodeProjectionUtils;
import io.harness.pms.execution.utils.StatusUtils;
import io.harness.repositories.InterruptRepository;
import io.harness.springdata.PersistenceUtils;

import com.google.inject.Inject;
import com.mongodb.client.result.UpdateResult;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@Slf4j
@OwnedBy(PIPELINE)
public class InterruptServiceImpl implements InterruptService {
  @Inject private InterruptRepository interruptRepository;
  @Inject private MongoTemplate mongoTemplate;
  @Inject private PauseAllInterruptHandler pauseAllInterruptHandler;
  @Inject private ResumeAllInterruptHandler resumeAllInterruptHandler;
  @Inject private MarkExpiredInterruptHandler markExpiredInterruptHandler;
  @Inject private AbortInterruptHandler abortInterruptHandler;
  @Inject private NodeExecutionService nodeExecutionService;
  @Inject private PlanExecutionService planExecutionService;

  @Override
  public Interrupt get(String interruptId) {
    return interruptRepository.findById(interruptId)
        .orElseThrow(() -> new InvalidRequestException("Interrupt Not found for id: " + interruptId));
  }

  @Override
  public Interrupt save(Interrupt interrupt) {
    return interruptRepository.save(interrupt);
  }

  @Override
  public ExecutionCheck checkInterruptsPreInvocation(String planExecutionId, String nodeExecutionId) {
    List<Interrupt> interrupts = fetchActivePlanLevelInterrupts(planExecutionId);
    if (isEmpty(interrupts)) {
      return ExecutionCheck.builder().proceed(true).reason("[InterruptCheck] No Interrupts Found").build();
    }
    if (interrupts.stream().filter(interrupt -> interrupt.getNodeExecutionId() == null).count() > 1) {
      throw new InvalidRequestException("More than 2 active Plan Level Interrupts Present: "
          + interrupts.stream().map(interrupt -> interrupt.getType().toString()).collect(Collectors.joining("|")));
    }

    Optional<Interrupt> optionalInterrupt =
        InterruptUtils.obtainOptionalInterruptFromActiveInterrupts(interrupts, planExecutionId, nodeExecutionId);

    Interrupt interrupt = optionalInterrupt.orElseThrow(() -> new InvalidRequestException("Interrupt was not found"));
    log.info("Interrupt found pre node invocation calculating execution check");
    return calculateExecutionCheck(nodeExecutionId, interrupt);
  }

  private ExecutionCheck calculateExecutionCheck(String nodeExecutionId, Interrupt interrupt) {
    switch (interrupt.getType()) {
      case PAUSE_ALL:
        if (pauseRequired(interrupt, nodeExecutionId)) {
          pauseAllInterruptHandler.handleInterruptForNodeExecution(interrupt, nodeExecutionId);
          return ExecutionCheck.builder().proceed(false).reason("[InterruptCheck] PAUSE_ALL interrupt found").build();
        }
        return ExecutionCheck.builder().proceed(true).reason("[InterruptCheck] No Interrupts Found").build();
      case RESUME_ALL:
        resumeAllInterruptHandler.handleInterruptForNodeExecution(interrupt, nodeExecutionId);
        return ExecutionCheck.builder().proceed(true).reason("[InterruptCheck] RESUME_ALL interrupt found").build();
      case ABORT_ALL:
      case EXPIRE_ALL:
        NodeExecution nodeExecution =
            nodeExecutionService.getWithFieldsIncluded(nodeExecutionId, NodeProjectionUtils.withStatusAndMode);
        if (!ExecutionModeUtils.isParentMode(nodeExecution.getMode())
            && StatusUtils.resumableStatuses().contains(nodeExecution.getStatus())) {
          if (interrupt.getType() == InterruptType.ABORT_ALL) {
            abortInterruptHandler.handleInterruptForNodeExecution(interrupt, nodeExecutionId);
          } else {
            markExpiredInterruptHandler.handleInterruptForNodeExecution(interrupt, nodeExecutionId);
          }
          return ExecutionCheck.builder()
              .proceed(false)
              .reason("[InterruptCheck] " + interrupt.getType() + " interrupt found")
              .build();
        }
        return ExecutionCheck.builder()
            .proceed(true)
            .reason("[InterruptCheck] " + interrupt.getType() + " interrupt found but Node is nnot leaf")
            .build();
      default:
        throw new InvalidRequestException("No Handler Present for interrupt type: " + interrupt.getType());
    }
  }

  private boolean pauseRequired(Interrupt interrupt, String nodeExecutionId) {
    NodeExecution nodeExecution =
        nodeExecutionService.getWithFieldsIncluded(nodeExecutionId, NodeProjectionUtils.withStatusAndMode);

    // Only Pausing leaf steps, It makes sense to let the execution flow to a leaf step and pause there
    // There is no pint pausing on parent (wrapper) steps (like stages/stage). More aesthetic for the execution graph
    // too
    if (ExecutionModeUtils.isParentMode(nodeExecution.getMode())) {
      return false;
    }

    // This is for PAUSE_ALL interrupt. If PAUSE ALL you have to pause for any node
    if (interrupt.getNodeExecutionId() == null) {
      return true;
    }

    // This case is for stage level PAUSE

    // Lets first check if stage is already in final state
    // (ex. interrupt was fired for the last node in the stage)
    NodeExecution interruptNodeExecution = nodeExecutionService.getWithFieldsIncluded(
        interrupt.getNodeExecutionId(), NodeProjectionUtils.withAmbianceAndStatus);
    if (StatusUtils.isFinalStatus(interruptNodeExecution.getStatus())) {
      updatePlanStatus(interruptNodeExecution.getAmbiance().getPlanExecutionId(), nodeExecutionId);
      updateInterruptState(interrupt.getUuid(), PROCESSED_SUCCESSFULLY, false);
      return false;
    }

    // Find All children for the stage (nodeExecutionId in interrupt) and check if the starting node is one of these. If
    // yes Pause the execution
    List<NodeExecution> targetExecutions = nodeExecutionService.findAllChildrenOnlyIds(
        interrupt.getPlanExecutionId(), interrupt.getNodeExecutionId(), true);
    return targetExecutions.stream().anyMatch(ne -> ne.getUuid().equals(nodeExecutionId));
  }

  @Override
  public List<Interrupt> fetchActivePlanLevelInterrupts(String planExecutionId) {
    return interruptRepository.findByPlanExecutionIdAndStateInAndTypeInOrderByCreatedAtDesc(planExecutionId,
        EnumSet.of(REGISTERED, PROCESSING),
        EnumSet.of(
            InterruptType.PAUSE_ALL, InterruptType.RESUME_ALL, InterruptType.ABORT_ALL, InterruptType.EXPIRE_ALL));
  }

  @Override
  public Interrupt markProcessedForceful(String interruptId, State interruptState, boolean forceful) {
    return updateInterruptState(interruptId, interruptState, forceful);
  }

  @Override
  public Interrupt markProcessed(String interruptId, State finalState) {
    return markProcessedForceful(interruptId, finalState, false);
  }

  @Override
  public Interrupt markProcessing(String interruptId) {
    return updateInterruptState(interruptId, PROCESSING, false);
  }

  private Interrupt updateInterruptState(String interruptId, State interruptState, boolean forceful) {
    Update updateOps = new Update()
                           .set(InterruptKeys.state, interruptState)
                           .set(InterruptKeys.fromMonitor, forceful)
                           .set(InterruptKeys.lastUpdatedAt, System.currentTimeMillis());
    Query query = query(where(InterruptKeys.uuid).is(interruptId));
    Interrupt seizedInterrupt = mongoTemplate.findAndModify(
        query, updateOps, new FindAndModifyOptions().upsert(false).returnNew(true), Interrupt.class);
    if (seizedInterrupt == null) {
      throw new InvalidRequestException("Cannot seize the interrupt {} with id :" + interruptId);
    }
    return seizedInterrupt;
  }

  @Override
  public List<Interrupt> fetchAllInterrupts(String planExecutionId) {
    return interruptRepository.findByPlanExecutionIdOrderByCreatedAtDesc(planExecutionId);
  }

  @Override
  public List<Interrupt> fetchActiveInterrupts(String planExecutionId) {
    return interruptRepository.findByPlanExecutionIdAndStateInOrderByCreatedAtDesc(
        planExecutionId, EnumSet.of(REGISTERED, PROCESSING));
  }

  @Override
  public long closeActiveInterrupts(String planExecutionId) {
    Query query = query(where(InterruptKeys.planExecutionId).is(planExecutionId))
                      .addCriteria(where(InterruptKeys.state).in(EnumSet.of(REGISTERED, PROCESSING)));
    Update update = new Update().set(InterruptKeys.state, PROCESSED_SUCCESSFULLY);
    UpdateResult updateResult = mongoTemplate.updateMulti(query, update, Interrupt.class);
    if (!updateResult.wasAcknowledged()) {
      log.error("Failed to close Active interrupt for planExecutionId: {}", planExecutionId);
      return -1;
    }
    log.info("Closed {} active interrupts for planExecutionId {}", updateResult.getModifiedCount(), planExecutionId);
    return updateResult.getModifiedCount();
  }

  @Override
  public List<Interrupt> fetchActiveInterruptsForNodeExecution(String planExecutionId, String nodeExecutionId) {
    return interruptRepository.findByPlanExecutionIdAndNodeExecutionIdAndStateInOrderByCreatedAtDesc(
        planExecutionId, nodeExecutionId, EnumSet.of(REGISTERED, PROCESSING));
  }

  @Override
  public List<Interrupt> fetchActiveInterruptsForNodeExecutionByType(
      String planExecutionId, String nodeExecutionId, InterruptType interruptType) {
    return interruptRepository.findByPlanExecutionIdAndNodeExecutionIdAndTypeAndStateInOrderByCreatedAtDesc(
        planExecutionId, nodeExecutionId, interruptType, EnumSet.of(REGISTERED, PROCESSING));
  }

  @Override
  public void deleteAllInterrupts(Set<String> planExecutionIds) {
    Criteria criteria = where(InterruptKeys.planExecutionId).in(planExecutionIds);
    Query query = new Query(criteria);
    RetryPolicy<Object> retryPolicy =
        PersistenceUtils.getRetryPolicy("[Retrying]: Failed deleting Interrupt entity; attempt: {}",
            "[Failed]: Failed deleting Interrupt entity; attempt: {}");
    Failsafe.with(retryPolicy).get(() -> mongoTemplate.remove(query, Interrupt.class));
  }

  private void updatePlanStatus(String planExecutionId, String excludingNodeExecutionId) {
    Status planStatus = planExecutionService.calculateStatusExcluding(planExecutionId, excludingNodeExecutionId);
    if (!StatusUtils.isFinalStatus(planStatus)) {
      planExecutionService.updateStatus(planExecutionId, planStatus);
    }
  }

  public List<Interrupt> fetchAbortAllPlanLevelInterrupt(String planExecutionId) {
    return interruptRepository.findByPlanExecutionIdAndTypeIn(
        planExecutionId, EnumSet.of(InterruptType.ABORT_ALL, InterruptType.ABORT));
  }
}
