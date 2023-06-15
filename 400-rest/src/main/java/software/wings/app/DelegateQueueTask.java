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
import io.harness.delegate.utils.DelegateLogContextHelper;
import io.harness.delegate.utils.DelegateTaskMigrationHelper;
import io.harness.exception.ExceptionLogger;
import io.harness.exception.WingsException;
import io.harness.ff.FeatureFlagService;
import io.harness.logging.AccountLogContext;
import io.harness.logging.AutoLogContext;
import io.harness.metrics.intfc.DelegateMetricsService;
import io.harness.persistence.HIterator;
import io.harness.persistence.HPersistence;
import io.harness.service.intfc.DelegateTaskService;
import io.harness.version.VersionInfoManager;

import software.wings.core.managerConfiguration.ConfigurationController;
import software.wings.service.impl.DelegateTaskBroadcastHelper;
import software.wings.service.intfc.AssignDelegateService;
import software.wings.service.intfc.DelegateSelectionLogsService;
import software.wings.service.intfc.DelegateService;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import dev.morphia.query.Query;
import dev.morphia.query.UpdateOperations;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

/**
 * Scheduled Task to look for finished WaitInstances and send messages to NotifyEventQueue.
 */
@Slf4j
@TargetModule(HarnessModule._420_DELEGATE_SERVICE)
public class DelegateQueueTask implements Runnable {
  private static final SecureRandom random = new SecureRandom();

  @Inject private HPersistence persistence;
  @Inject private Clock clock;
  @Inject private VersionInfoManager versionInfoManager;
  @Inject private AssignDelegateService assignDelegateService;
  @Inject private DelegateService delegateService;
  @Inject private DelegateTaskBroadcastHelper broadcastHelper;
  @Inject private ConfigurationController configurationController;
  @Inject private DelegateTaskService delegateTaskService;
  @Inject private DelegateSelectionLogsService delegateSelectionLogsService;
  @Inject private DelegateMetricsService delegateMetricsService;
  @Inject private FeatureFlagService featureFlagService;
  @Inject private DelegateTaskMigrationHelper delegateTaskMigrationHelper;

  private static long BROADCAST_INTERVAL = TimeUnit.MINUTES.toMillis(1);
  private static int MAX_BROADCAST_ROUND = 3;

  @Override
  public void run() {
    if (getMaintenanceFlag()) {
      return;
    }

    try {
      // don't run on old DB once all tasks has been migrated.
      if (!delegateTaskMigrationHelper.isDelegateTaskMigrationFinished()) {
        rebroadcastUnassignedTasks(false);
      }
      if (delegateTaskMigrationHelper.isDelegateTaskMigrationEnabled()) {
        rebroadcastUnassignedTasks(true);
      }
    } catch (WingsException exception) {
      ExceptionLogger.logProcessedMessages(exception, MANAGER, log);
    } catch (Exception exception) {
      log.error("Error seen in the DelegateQueueTask call", exception);
    }
  }

  // TODO Fix this iterator so that all managers work on different set of tasks

  @VisibleForTesting
  protected void rebroadcastUnassignedTasks(boolean isMigrationEnabled) {
    // Re-broadcast queued tasks not picked up by any Delegate and not in process of validation
    long now = clock.millis();
    Query<DelegateTask> unassignedTasksQuery =
        persistence.createQuery(DelegateTask.class, excludeAuthority, isMigrationEnabled)
            .filter(DelegateTaskKeys.status, QUEUED)
            .field(DelegateTaskKeys.nextBroadcast)
            .lessThan(now)
            .field(DelegateTaskKeys.expiry)
            .greaterThan(now)
            .field(DelegateTaskKeys.broadcastRound)
            .lessThan(MAX_BROADCAST_ROUND)
            .field(DelegateTaskKeys.delegateId)
            .doesNotExist();

    try (HIterator<DelegateTask> iterator = new HIterator<>(unassignedTasksQuery.fetch())) {
      while (iterator.hasNext()) {
        DelegateTask delegateTask = iterator.next();
        Query<DelegateTask> query = persistence.createQuery(DelegateTask.class, excludeAuthority, isMigrationEnabled)
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
        long nextInterval = TimeUnit.SECONDS.toMillis(5);
        int broadcastRoundCount = delegateTask.getBroadcastRound();
        Set<String> alreadyTriedDelegates =
            Optional.ofNullable(delegateTask.getAlreadyTriedDelegates()).orElse(Sets.newHashSet());

        // if all delegates got one round of rebroadcast, then increase broadcast interval & broadcastRound
        if (alreadyTriedDelegates.containsAll(delegateTask.getEligibleToExecuteDelegateIds())) {
          alreadyTriedDelegates.clear();
          broadcastRoundCount++;
          nextInterval = (long) broadcastRoundCount * BROADCAST_INTERVAL;
        }
        alreadyTriedDelegates.addAll(broadcastToDelegates);

        UpdateOperations<DelegateTask> updateOperations =
            persistence.createUpdateOperations(DelegateTask.class, isMigrationEnabled)
                .set(DelegateTaskKeys.lastBroadcastAt, now)
                .set(DelegateTaskKeys.broadcastCount, delegateTask.getBroadcastCount() + 1)
                .set(DelegateTaskKeys.eligibleToExecuteDelegateIds, eligibleDelegatesList)
                .set(DelegateTaskKeys.nextBroadcast, now + nextInterval)
                .set(DelegateTaskKeys.alreadyTriedDelegates, alreadyTriedDelegates)
                .set(DelegateTaskKeys.broadcastRound, broadcastRoundCount);
        delegateTask =
            persistence.findAndModify(query, updateOperations, HPersistence.returnNewOptions, isMigrationEnabled);
        // update failed, means this was broadcast by some other manager
        if (delegateTask == null) {
          log.debug("Cannot find delegate task, update failed on broadcast");
          continue;
        }
        delegateTask.setBroadcastToDelegateIds(broadcastToDelegates);
        delegateSelectionLogsService.logBroadcastToDelegate(Sets.newHashSet(broadcastToDelegates), delegateTask);

        if (delegateTask.getTaskDataV2() != null) {
          rebroadcastDelegateTaskUsingTaskDataV2(delegateTask);
        } else {
          rebroadcastDelegateTaskUsingTaskData(delegateTask);
        }
      }
    }
  }

  private void rebroadcastDelegateTaskUsingTaskData(DelegateTask delegateTask) {
    try (AutoLogContext ignore1 = DelegateLogContextHelper.getLogContext(delegateTask);
         AutoLogContext ignore2 = new AccountLogContext(delegateTask.getAccountId(), OVERRIDE_ERROR)) {
      log.info("ST: Rebroadcast queued task id {} on broadcast attempt: {} on round {} to {} ", delegateTask.getUuid(),
          delegateTask.getBroadcastCount(), delegateTask.getBroadcastRound(), delegateTask.getBroadcastToDelegateIds());
      delegateMetricsService.recordDelegateTaskMetrics(delegateTask, DELEGATE_TASK_REBROADCAST);
      broadcastHelper.rebroadcastDelegateTask(delegateTask);
    }
  }

  private void rebroadcastDelegateTaskUsingTaskDataV2(DelegateTask delegateTask) {
    try (AutoLogContext ignore1 = DelegateLogContextHelper.getLogContext(delegateTask);
         AutoLogContext ignore2 = new AccountLogContext(delegateTask.getAccountId(), OVERRIDE_ERROR)) {
      log.info("ST: Rebroadcast queued task id {} on broadcast attempt: {} on round {} to {} ", delegateTask.getUuid(),
          delegateTask.getBroadcastCount(), delegateTask.getBroadcastRound(), delegateTask.getBroadcastToDelegateIds());
      delegateMetricsService.recordDelegateTaskMetrics(delegateTask, DELEGATE_TASK_REBROADCAST);
      broadcastHelper.rebroadcastDelegateTaskV2(delegateTask);
    }
  }
}
