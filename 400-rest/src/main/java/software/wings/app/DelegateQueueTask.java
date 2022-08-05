/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.app;

import static io.harness.beans.DelegateTask.Status.QUEUED;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.exception.WingsException.ExecutionContext.MANAGER;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;
import static io.harness.maintenance.MaintenanceController.getMaintenanceFlag;
import static io.harness.metrics.impl.DelegateMetricsServiceImpl.DELEGATE_TASK_REBROADCAST;
import static io.harness.persistence.HQuery.excludeAuthority;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.DelegateTask;
import io.harness.beans.DelegateTask.DelegateTaskKeys;
import io.harness.delegate.beans.DelegateTaskExpiryReason;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.delegate.task.TaskLogContext;
import io.harness.exception.DelegateNotAvailableException;
import io.harness.exception.WingsException;
import io.harness.logging.AccountLogContext;
import io.harness.logging.AutoLogContext;
import io.harness.logging.ExceptionLogger;
import io.harness.metrics.intfc.DelegateMetricsService;
import io.harness.network.FibonacciBackOff;
import io.harness.persistence.HIterator;
import io.harness.persistence.HPersistence;
import io.harness.service.intfc.DelegateTaskService;

import software.wings.beans.TaskType;
import software.wings.service.impl.DelegateTaskBroadcastHelper;
import software.wings.service.intfc.DelegateSelectionLogsService;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.UpdateOperations;

/**
 * Scheduled Task to look for finished WaitInstances and send messages to NotifyEventQueue.
 */
@Slf4j
@TargetModule(HarnessModule._420_DELEGATE_SERVICE)
public class DelegateQueueTask implements Runnable {
  private static final SecureRandom random = new SecureRandom();

  @Inject private HPersistence persistence;
  @Inject private Clock clock;
  @Inject private DelegateTaskBroadcastHelper broadcastHelper;
  @Inject private DelegateSelectionLogsService delegateSelectionLogsService;
  @Inject private DelegateMetricsService delegateMetricsService;
  @Inject private DelegateTaskService delegateTaskService;

  private static int MAX_BROADCAST_ROUND = 5;

  @Override
  public void run() {
    if (getMaintenanceFlag()) {
      return;
    }

    try {
      rebroadcastUnassignedTasks();
    } catch (WingsException exception) {
      ExceptionLogger.logProcessedMessages(exception, MANAGER, log);
    } catch (Exception exception) {
      log.error("Error seen in the DelegateQueueTask call", exception);
    }
  }

  @VisibleForTesting
  protected void rebroadcastUnassignedTasks() {
    // Re-broadcast queued tasks not picked up by any Delegate and not in process of validation
    long now = clock.millis();
    Query<DelegateTask> unassignedTasksQuery = persistence.createQuery(DelegateTask.class, excludeAuthority)
                                                   .filter(DelegateTaskKeys.status, QUEUED)
                                                   .field(DelegateTaskKeys.nextBroadcast)
                                                   .lessThan(now)
                                                   .field(DelegateTaskKeys.expiry)
                                                   .greaterThan(now)
                                                   .field(DelegateTaskKeys.broadcastRound)
                                                   .lessThanOrEq(MAX_BROADCAST_ROUND)
                                                   .field(DelegateTaskKeys.delegateId)
                                                   .doesNotExist();

    try (HIterator<DelegateTask> iterator = new HIterator<>(unassignedTasksQuery.fetch())) {
      while (iterator.hasNext()) {
        DelegateTask delegateTask = iterator.next();
        if (delegateTask.getBroadcastRound() >= MAX_BROADCAST_ROUND) {
          markTaskFailed(delegateTask);
          continue;
        }
        Query<DelegateTask> query = persistence.createQuery(DelegateTask.class, excludeAuthority)
                                        .filter(DelegateTaskKeys.uuid, delegateTask.getUuid())
                                        .filter(DelegateTaskKeys.broadcastCount, delegateTask.getBroadcastCount());

        LinkedList<String> eligibleDelegatesList = delegateTask.getEligibleToExecuteDelegateIds();

        if (isEmpty(eligibleDelegatesList)) {
          log.info("No eligible delegates for task {}", delegateTask.getUuid());
          continue;
        }

        // add connected eligible delegates to broadcast list. Also rotate the eligibleDelegatesList list
        List<String> broadcastToDelegates = Lists.newArrayList();
        int broadcastLimit = Math.min(eligibleDelegatesList.size(), 10);

        Iterator<String> delegateIdIterator = eligibleDelegatesList.iterator();

        while (delegateIdIterator.hasNext() && broadcastLimit > broadcastToDelegates.size()) {
          String delegateId = eligibleDelegatesList.removeFirst();
          broadcastToDelegates.add(delegateId);
          eligibleDelegatesList.addLast(delegateId);
        }
        int broadcastRoundCount = delegateTask.getBroadcastRound();
        Set<String> alreadyTriedDelegates =
            Optional.ofNullable(delegateTask.getAlreadyTriedDelegates()).orElse(Sets.newHashSet());

        long nextInterval = nextRebroadcastInterval(alreadyTriedDelegates, delegateTask);
        // if all delegates got one round of rebroadcast, then increase broadcast interval & broadcastRound
        if (alreadyTriedDelegates.containsAll(delegateTask.getEligibleToExecuteDelegateIds())) {
          alreadyTriedDelegates.clear();
          broadcastRoundCount++;
        }

        alreadyTriedDelegates.addAll(broadcastToDelegates);

        UpdateOperations<DelegateTask> updateOperations =
            persistence.createUpdateOperations(DelegateTask.class)
                .set(DelegateTaskKeys.lastBroadcastAt, now)
                .set(DelegateTaskKeys.broadcastCount, delegateTask.getBroadcastCount() + 1)
                .set(DelegateTaskKeys.eligibleToExecuteDelegateIds, eligibleDelegatesList)
                .set(DelegateTaskKeys.nextBroadcast, now + nextInterval)
                .set(DelegateTaskKeys.alreadyTriedDelegates, alreadyTriedDelegates)
                .set(DelegateTaskKeys.broadcastRound, broadcastRoundCount);
        delegateTask = persistence.findAndModify(query, updateOperations, HPersistence.returnNewOptions);
        // update failed, means this was broadcast by some other manager
        if (delegateTask == null) {
          log.info("Cannot find delegate task, update failed on broadcast");
          continue;
        }
        delegateTask.setBroadcastToDelegateIds(broadcastToDelegates);
        delegateSelectionLogsService.logBroadcastToDelegate(Sets.newHashSet(broadcastToDelegates), delegateTask);

        try (AutoLogContext ignore1 = new TaskLogContext(delegateTask.getUuid(), delegateTask.getData().getTaskType(),
                 TaskType.valueOf(delegateTask.getData().getTaskType()).getTaskGroup().name(), OVERRIDE_ERROR);
             AutoLogContext ignore2 = new AccountLogContext(delegateTask.getAccountId(), OVERRIDE_ERROR)) {
          log.info("ST: Rebroadcast queued task id {} on broadcast attempt: {} on round {} to {} ",
              delegateTask.getUuid(), delegateTask.getBroadcastCount(), delegateTask.getBroadcastRound(),
              delegateTask.getBroadcastToDelegateIds());
          delegateMetricsService.recordDelegateTaskMetrics(delegateTask, DELEGATE_TASK_REBROADCAST);
          broadcastHelper.rebroadcastDelegateTask(delegateTask);
        }
      }
    }
  }

  /**
   * returns the time after which the task will be eligible to be rebroadcasted
   * broadcast count is starts from zero and we don't want to wait for long when the rebroadcast limit is reached
   * hence the condition delegateTask.getBroadcastRound() + 1 < MAX_BROADCAST_ROUND
   */
  private long nextRebroadcastInterval(Set<String> alreadyTriedDelegates, DelegateTask delegateTask) {
    if (alreadyTriedDelegates.containsAll(delegateTask.getEligibleToExecuteDelegateIds())
        && delegateTask.getBroadcastRound() + 1 < MAX_BROADCAST_ROUND) {
      return TimeUnit.MINUTES.toMillis(FibonacciBackOff.getFibonacciElement(delegateTask.getBroadcastRound()));
    }
    return TimeUnit.SECONDS.toMillis(5);
  }

  private void markTaskFailed(DelegateTask delegateTask) {
    delegateTaskService.processDelegateResponse(delegateTask.getAccountId(), null, delegateTask.getUuid(),
        DelegateTaskResponse.builder()
            .responseCode(DelegateTaskResponse.ResponseCode.FAILED)
            .accountId(delegateTask.getAccountId())
            .response(ErrorNotifyResponseData.builder()
                          .errorMessage(DelegateTaskExpiryReason.REBROADCAST_LIMIT_REACHED.getMessage())
                          .exception(new DelegateNotAvailableException(
                              DelegateTaskExpiryReason.REBROADCAST_LIMIT_REACHED.getMessage()))
                          .build())
            .build());
  }
}
