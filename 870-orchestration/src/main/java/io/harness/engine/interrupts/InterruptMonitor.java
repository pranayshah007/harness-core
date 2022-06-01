/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.interrupts;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.interrupts.Interrupt.State.PROCESSED_SUCCESSFULLY;
import static io.harness.interrupts.Interrupt.State.PROCESSED_UNSUCCESSFULLY;
import static io.harness.interrupts.Interrupt.State.PROCESSING;
import static io.harness.mongo.iterator.MongoPersistenceIterator.SchedulingType.REGULAR;
import static io.harness.pms.contracts.interrupts.InterruptType.ABORT_ALL;
import static io.harness.pms.contracts.interrupts.InterruptType.EXPIRE_ALL;

import static java.time.Duration.ofMinutes;
import static java.time.Duration.ofSeconds;
import static org.springframework.data.mongodb.core.query.Criteria.where;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.engine.executions.plan.PlanExecutionService;
import io.harness.engine.interrupts.helpers.AbortHelper;
import io.harness.engine.interrupts.helpers.ExpiryHelper;
import io.harness.execution.ExecutionModeUtils;
import io.harness.execution.NodeExecution;
import io.harness.execution.PlanExecution;
import io.harness.interrupts.Interrupt;
import io.harness.interrupts.Interrupt.InterruptKeys;
import io.harness.iterator.PersistenceIteratorFactory;
import io.harness.iterator.PersistenceIteratorFactory.PumpExecutorOptions;
import io.harness.logging.AutoLogContext;
import io.harness.mongo.iterator.IteratorConfig;
import io.harness.mongo.iterator.MongoPersistenceIterator;
import io.harness.mongo.iterator.MongoPersistenceIterator.Handler;
import io.harness.mongo.iterator.filter.SpringFilterExpander;
import io.harness.mongo.iterator.provider.SpringPersistenceProvider;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.execution.utils.StatusUtils;

import com.google.inject.Inject;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;
import org.springframework.data.mapping.model.MappingInstantiationException;
import org.springframework.data.mongodb.core.MongoTemplate;

/**
 * This monitor runs and try to clean up any stuck executions there are many edge cases which are not handled right now
 *
 * 1. What if there are no leaves at all (leverage level count)
 *
 */
@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public class InterruptMonitor implements Handler<Interrupt> {
  private static final int TARGET_INTERVAL_MINUTES = 3;

  @Inject private PersistenceIteratorFactory persistenceIteratorFactory;
  @Inject private NodeExecutionService nodeExecutionService;
  @Inject private PlanExecutionService planExecutionService;
  @Inject private InterruptService interruptService;
  @Inject private MongoTemplate mongoTemplate;
  @Inject private AbortHelper abortHelper;
  @Inject private ExpiryHelper expiryHelper;

  public void registerIterators(IteratorConfig iteratorConfig) {
    // todo - RDM -  should we follow this lead and use %d on the naming when not provided?
    PumpExecutorOptions executorOptions = PumpExecutorOptions.builder()
                                              .name("InterruptMonitor-%d")
                                              .poolSize(iteratorConfig.getThreadPoolCount())
                                              .interval(ofSeconds(iteratorConfig.getTargetIntervalInSeconds()))
                                              .build();

    persistenceIteratorFactory.createPumpIteratorWithDedicatedThreadPool(executorOptions, InterruptMonitor.class,
        MongoPersistenceIterator.<Interrupt, SpringFilterExpander>builder()
            .clazz(Interrupt.class)
            .fieldName(InterruptKeys.nextIteration)
            .filterExpander(q
                -> q.addCriteria(where(InterruptKeys.state).is(PROCESSING))
                       .addCriteria(where(InterruptKeys.type).in(ABORT_ALL, EXPIRE_ALL))
                       .addCriteria(where(InterruptKeys.createdAt)
                                        .lt(System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(10))))
            .targetInterval(ofMinutes(TARGET_INTERVAL_MINUTES))
            .acceptableNoAlertDelay(ofMinutes(10))
            .acceptableExecutionTime(ofSeconds(30))
            .handler(this)
            .schedulingType(REGULAR)
            .persistenceProvider(new SpringPersistenceProvider<>(mongoTemplate))
            .redistribute(true));
  }

  @Override
  public void handle(Interrupt interrupt) {
    try (AutoLogContext ignore = interrupt.autoLogContext()) {
      log.info("This is most probably a stuck interrupt. Checking for stuck conditions");

      // If plan Execution has reached in final status closing the interrupt
      // This is probably just for some legacy handling should happen no more as all the interrupts
      // get closed on plan completion now
      // The null check is for really old plans which are cleared by mongo
      PlanExecution planExecution = null;
      try {
        planExecution = planExecutionService.get(interrupt.getPlanExecutionId());
      } catch (Exception ex) {
        // Just ignoring this exception this happens again for old executions where the plan execution have been removed
        // from database
      }
      if (planExecution == null || StatusUtils.isFinalStatus(planExecution.getStatus())) {
        log.info("Interrupt active but plan finished, Closing the interrupt");
        interruptService.markProcessedForceful(interrupt.getUuid(), PROCESSED_SUCCESSFULLY, true);
        return;
      }

      List<NodeExecution> nodeExecutions =
          nodeExecutionService.findAllNodeExecutionsTrimmed(interrupt.getPlanExecutionId());
      Set<NodeExecution> leaves = findAllLeaves(nodeExecutions);

      // There are no leaves in the execution then something weird happened
      // Happen in dev environments when you abruptly stop the services
      // Will rarely happen in prod env, but can happen
      // TODO: Revisit this by introducing the level count in node execution
      if (isEmpty(leaves)) {
        log.error("No Leaves found something really wrong happened here. Lets check this execution {}",
            interrupt.getPlanExecutionId());
        interruptService.markProcessedForceful(interrupt.getUuid(), PROCESSED_UNSUCCESSFULLY, true);
        return;
      }

      List<NodeExecution> runningNodes =
          leaves.stream()
              .filter(ne -> StatusUtils.abortAndExpireStatuses().contains(ne.getStatus()))
              .collect(Collectors.toList());
      if (isNotEmpty(runningNodes)) {
        log.info("Running Nodes found {}", runningNodes);
        if (runningNodes.stream().allMatch(
                ne -> ne.getStatus() == Status.DISCONTINUING || ne.getStatus() == Status.QUEUED)) {
          log.info("All running nodes are discontinuing, Aborting these");
          for (NodeExecution ne : runningNodes) {
            try {
              abortHelper.abortDiscontinuingNode(ne, interrupt.getUuid(), interrupt.getInterruptConfig());
            } catch (MappingInstantiationException ex) {
              log.info("Node Execution Instantiation Exception Occurred. Cannot Recover from it ignore");
              interruptService.markProcessedForceful(interrupt.getUuid(), PROCESSED_UNSUCCESSFULLY, true);
              break;
            }
          }
        } else {
          log.info("Not all running nodes are discontinuing, Ignoring");
        }
        return;
      }

      log.info("Interrupt processing stuck. Taking forceful action");
      forceTerminate(
          interrupt, nodeExecutions, leaves.stream().map(NodeExecution::getUuid).collect(Collectors.toSet()));
    }
  }

  private void forceTerminate(Interrupt interrupt, List<NodeExecution> nodeExecutions, Set<String> children) {
    Set<NodeExecution> parents = findParentsForChildren(nodeExecutions, children);
    if (EmptyPredicate.isEmpty(parents)) {
      // If running parents are empty that means we have reached to the top and we did not find any running returning
      // This means all the nodes are in correct stuses except the plan Execution
      Status status = planExecutionService.calculateStatus(interrupt.getPlanExecutionId());
      PlanExecution planExecution =
          planExecutionService.updateStatusForceful(interrupt.getPlanExecutionId(), status, null, true);
      if (planExecution == null) {
        log.error("Failed to update PlanExecution {} with Status {}", interrupt.getPlanExecutionId(), status);
      } else {
        log.info("Only plan Status was not correct. Updated plan status to {}", planExecution.getStatus());
      }
      return;
    }

    Set<NodeExecution> runningParents = parents.stream()
                                            .filter(ne -> !StatusUtils.finalStatuses().contains(ne.getStatus()))
                                            .collect(Collectors.toSet());

    if (isEmpty(runningParents)) {
      forceTerminate(
          interrupt, nodeExecutions, parents.stream().map(NodeExecution::getUuid).collect(Collectors.toSet()));
      return;
    }

    // Terminate parents here
    for (NodeExecution parent : runningParents) {
      NodeExecution discontinuingParent = markDiscontinuingIfRequired(interrupt, parent);
      if (discontinuingParent == null) {
        // TODO: Think more cases can this happen if yes what we can do to improve
        log.error("Unable to unblock stuck execution InterruptId :{} NodeExecutionId: {}", interrupt.getUuid(),
            parent.getUuid());
        return;
      }
      terminateParent(interrupt, discontinuingParent);
    }
  }

  private void terminateParent(Interrupt interrupt, NodeExecution discontinuingParent) {
    switch (interrupt.getType()) {
      case ABORT_ALL:
        abortHelper.discontinueMarkedInstance(discontinuingParent, interrupt);
        return;
      case EXPIRE_ALL:
        expiryHelper.expireMarkedInstance(discontinuingParent, interrupt);
        return;
      default:
        // This cannot happen just returning
    }
  }

  @Nullable
  private NodeExecution markDiscontinuingIfRequired(Interrupt interrupt, NodeExecution parent) {
    NodeExecution discontinuingParent = parent;
    if (parent.getStatus() != Status.DISCONTINUING) {
      discontinuingParent = nodeExecutionService.updateStatusWithOps(
          parent.getUuid(), Status.DISCONTINUING, null, EnumSet.noneOf(Status.class));
    }
    return discontinuingParent;
  }

  private Set<NodeExecution> findAllLeaves(List<NodeExecution> nodeExecutions) {
    return nodeExecutions.stream()
        .filter(ne -> ExecutionModeUtils.leafModes().contains(ne.getMode()))
        .collect(Collectors.toSet());
  }

  private Set<NodeExecution> findParentsForChildren(List<NodeExecution> nodeExecutions, Set<String> childrenIds) {
    Set<String> parentIds = nodeExecutions.stream()
                                .filter(ne -> childrenIds.contains(ne.getUuid()))
                                .map(NodeExecution::getParentId)
                                .collect(Collectors.toSet());
    return nodeExecutions.stream().filter(ne -> parentIds.contains(ne.getUuid())).collect(Collectors.toSet());
  }
}
