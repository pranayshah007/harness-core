/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.workers.background.critical.iterator;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.ExecutionInterruptType;
import io.harness.beans.ExecutionStatus;
import io.harness.beans.FeatureName;
import io.harness.data.structure.CollectionUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.ff.FeatureFlagService;
import io.harness.mongo.iterator.MongoPersistenceIterator;

import software.wings.beans.WorkflowExecution;
import software.wings.dl.WingsPersistence;
import software.wings.service.intfc.WorkflowExecutionService;
import software.wings.sm.ExecutionInterrupt;
import software.wings.sm.StateExecutionInstance;
import software.wings.sm.StateExecutionInstance.StateExecutionInstanceKeys;
import software.wings.sm.StateType;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import dev.morphia.query.FindOptions;
import dev.morphia.query.Sort;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@OwnedBy(CDC)
@Singleton
@Slf4j
@TargetModule(HarnessModule._815_CG_TRIGGERS)
public class WorkflowExecutionZombieHandler implements MongoPersistenceIterator.Handler<WorkflowExecution> {
  private static final Set<String> ZOMBIE_STATE_TYPES = Sets.newHashSet(StateType.REPEAT.name(), StateType.FORK.name(),
      StateType.PHASE_STEP.name(), StateType.PHASE.name(), StateType.SUB_WORKFLOW.name());
  private final Set<ExecutionStatus> zombieStatus;
  /** Minutes past the creation time */
  private static final long CREATED_THRESHOLD = 45;
  private static final long ENDED_THRESHOLD = 45;

  @Inject private WingsPersistence wingsPersistence;
  @Inject private WorkflowExecutionService workflowExecutionService;
  @Inject private FeatureFlagService featureFlagService;

  public WorkflowExecutionZombieHandler() {
    this.zombieStatus = new HashSet<>(ExecutionStatus.flowingStatuses());
    this.zombieStatus.remove(ExecutionStatus.PAUSED);
  }

  @Override
  public void handle(WorkflowExecution wfExecution) {
    log.debug("Evaluating if workflow execution {} is a zombie execution [workflowId={}]", wfExecution.getUuid(),
        wfExecution.getWorkflowId());

    // SORT STATE EXECUTION INSTANCES BASED ON createdAt FIELD IN DESCENDING ORDER. WE NEED
    // THE MOST RECENTLY EXECUTION INSTANCE TO EVALUATE IF IT'S A ZOMBIE EXECUTION.
    List<StateExecutionInstance> stateExecutionInstances =
        CollectionUtils.emptyIfNull(wingsPersistence.createQuery(StateExecutionInstance.class)
                                        .filter(StateExecutionInstanceKeys.appId, wfExecution.getAppId())
                                        .filter(StateExecutionInstanceKeys.workflowId, wfExecution.getWorkflowId())
                                        .filter(StateExecutionInstanceKeys.executionUuid, wfExecution.getUuid())
                                        .order(Sort.descending(StateExecutionInstanceKeys.createdAt))
                                        .asList(new FindOptions().limit(1)));

    Optional<StateExecutionInstance> opt = getElement(stateExecutionInstances);
    opt.ifPresent(seInstance -> {
      if (isZombie(seInstance) || isSuccessZombie(seInstance)) {
        log.info(
            "Trigger force abort of workflow execution {} due remains in a zombie state [currentStateType={},createdAt={},endTs={}]",
            seInstance.getExecutionUuid(), seInstance.getStateType(), seInstance.getCreatedAt(), seInstance.getEndTs());

        ExecutionInterrupt executionInterrupt = new ExecutionInterrupt();
        executionInterrupt.setAppId(seInstance.getAppId());
        executionInterrupt.setExecutionUuid(seInstance.getExecutionUuid());
        executionInterrupt.setExecutionInterruptType(ExecutionInterruptType.ABORT_ALL);

        try {
          workflowExecutionService.triggerExecutionInterrupt(executionInterrupt);

        } catch (InvalidRequestException e) {
          log.warn(String.format("Unable to honor force abort [workflowId=%s,executionUuid=%s]",
                       seInstance.getWorkflowId(), seInstance.getExecutionUuid()),
              e);
        }
      }
    });
  }

  /**
   * Check if state execution instance was created {@value CREATED_THRESHOLD} minutes ago
   */
  private boolean hitCreatedThreshold(StateExecutionInstance seInstance) {
    return seInstance.getCreatedAt() <= (System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(CREATED_THRESHOLD));
  }

  /**
   * Check if state execution instance was ended {@value ENDED_THRESHOLD} minutes ago
   */
  private boolean hitEndedThreshold(StateExecutionInstance seInstance) {
    return seInstance.getEndTs() != null
        && seInstance.getEndTs() <= (System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(ENDED_THRESHOLD));
  }

  private Optional<StateExecutionInstance> getElement(List<StateExecutionInstance> elements) {
    return elements.isEmpty() ? Optional.empty() : Optional.ofNullable(elements.get(0));
  }

  // WE MUST ABORT THE EXECUTION WHEN ALL CONDITIONS MATCH:
  // -- THE ELEMENT HAS A ZOMBIE STATE TYPE
  // -- HIT THE CREATED THRESHOLD
  // -- IS AT FLOWING STATUSES, EXCEPT PAUSE STATUS
  private boolean isZombie(StateExecutionInstance seInstance) {
    return isZombieState(seInstance) && hitCreatedThreshold(seInstance)
        && zombieStatus.contains(seInstance.getStatus());
  }

  // A SUCCESS ZOMBIE IS DETECTED WHEN
  // -- THE StateExecutionInstance STATUS IS SUCCESS
  // -- HIT THE ENDED THRESHOLD
  // -- PARENT IS NOT A FORK
  //
  // THE MOST RECENT StateExecutionInstance SHOULD NOT HAVE SUCCESS STATUS AND THE WORKFLOW
  // EXECUTION STILL IN A RUNNING STATE.
  private boolean isSuccessZombie(StateExecutionInstance seInstance) {
    if (featureFlagService.isNotEnabled(FeatureName.WORKFLOW_EXECUTION_ZOMBIE_MONITOR, seInstance.getAccountId())) {
      return false;
    }
    return hitEndedThreshold(seInstance) && ExecutionStatus.SUCCESS.equals(seInstance.getStatus())
        && isNotParentFork(seInstance);
  }

  private boolean isNotParentFork(StateExecutionInstance seInstance) {
    if (StringUtils.isEmpty(seInstance.getParentInstanceId())) {
      return false;
    }
    long count = wingsPersistence.createQuery(StateExecutionInstance.class)
                     .filter(StateExecutionInstanceKeys.uuid, seInstance.getParentInstanceId())
                     .filter(StateExecutionInstanceKeys.stateType, StateType.FORK.name())
                     .count();
    return count == 0;
  }

  @VisibleForTesting
  boolean isZombieState(StateExecutionInstance seInstance) {
    return ZOMBIE_STATE_TYPES.contains(seInstance.getStateType());
  }
}
