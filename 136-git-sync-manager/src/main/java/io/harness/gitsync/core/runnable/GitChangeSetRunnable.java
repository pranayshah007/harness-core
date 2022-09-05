/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.core.runnable;

import static io.harness.annotations.dev.HarnessTeam.DX;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.WingsException.ExecutionContext.MANAGER;
import static io.harness.gitsync.common.beans.YamlChangeSet.MAX_RETRY_COUNT_EXCEEDED_CODE;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;
import static io.harness.maintenance.MaintenanceController.getMaintenanceFlag;

import static java.lang.String.format;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.stream.Collectors.toList;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.UnsupportedOperationException;
import io.harness.exception.WingsException;
import io.harness.gitsync.common.YamlProcessingLogContext;
import io.harness.gitsync.common.beans.YamlChangeSet.YamlChangeSetKeys;
import io.harness.gitsync.common.beans.YamlChangeSetStatus;
import io.harness.gitsync.core.dtos.YamlChangeSetDTO;
import io.harness.gitsync.core.service.YamlChangeSetLifeCycleManagerService;
import io.harness.gitsync.core.service.YamlChangeSetService;
import io.harness.lock.PersistentLocker;
import io.harness.logging.AccountLogContext;
import io.harness.logging.AutoLogContext;
import io.harness.logging.ExceptionLogger;
import io.harness.mongo.ProcessTimeLogContext;
import io.harness.queue.QueueController;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.mongodb.core.query.Criteria;

@Slf4j
@Singleton
@OwnedBy(DX)
public class GitChangeSetRunnable implements Runnable {
  public static final int MAX_RUNNING_CHANGESETS_FOR_ACCOUNT = 5;
  public static final int MAX_RETRY_FOR_CHANGESET = 10;
  public static final int MAX_RETRIED_QUEUED_JOB_CHECK_INTERVAL = 5 /*min*/;

  public static final List<YamlChangeSetStatus> terminalStatusList =
      ImmutableList.of(YamlChangeSetStatus.FAILED, YamlChangeSetStatus.COMPLETED, YamlChangeSetStatus.SKIPPED);
  public static final List<YamlChangeSetStatus> runningStatusList = ImmutableList.of(YamlChangeSetStatus.RUNNING);

  private static final AtomicLong lastTimestampForStuckJobCheck = new AtomicLong(0);
  private static final AtomicLong lastTimestampForStatusLogPrint = new AtomicLong(0);
  private static final AtomicLong lastTimestampForQueuedJobCheck = new AtomicLong(0);

  @Inject private YamlChangeSetService yamlChangeSetService;
  @Inject private GitChangeSetRunnableHelper gitChangeSetRunnableHelper;
  @Inject private GitChangeSetRunnableQueueHelper gitChangeSetRunnableQueueHelper;
  @Inject private PersistentLocker persistentLocker;
  @Inject private YamlChangeSetLifeCycleManagerService yamlChangeSetLifeCycleHandlerService;
  @Inject private QueueController queueController;

  @Override
  public void run() {
    final Stopwatch stopwatch = Stopwatch.createStarted();
    try {
      if (!shouldRun()) {
        if (shouldPrintStatusLogs()) {
          lastTimestampForStatusLogPrint.set(System.currentTimeMillis());
          log.info("Not continuing with GitChangeSetRunnable job");
          TimeUnit.SECONDS.sleep(1);
        }
        return;
      }

      handleStuckChangeSets();
      handleChangeSetWithMaxRetry();

      final List<YamlChangeSetDTO> yamlChangeSets = getYamlChangeSetsToProcess();

      if (!yamlChangeSets.isEmpty()) {
        yamlChangeSets.forEach(this::processChangeSet);
      }

      try (ProcessTimeLogContext ignore4 = new ProcessTimeLogContext(stopwatch.elapsed(MILLISECONDS), OVERRIDE_ERROR)) {
        if (!yamlChangeSets.isEmpty()) {
          log.info("Successfully handled {} change sets", yamlChangeSets.size());
        }
      }

    } catch (WingsException exception) {
      ExceptionLogger.logProcessedMessages(exception, MANAGER, log);
    } catch (Exception exception) {
      log.error("Unexpected error", exception);
    }
  }

  private List<YamlChangeSetDTO> getYamlChangeSetsToProcess() {
    return getYamlChangeSetsPerQueueKey();
  }

  private AutoLogContext createLogContextForChangeSet(YamlChangeSetDTO yamlChangeSet) {
    return YamlProcessingLogContext.builder().changeSetId(yamlChangeSet.getChangesetId()).build(OVERRIDE_ERROR);
  }

  @VisibleForTesting
  void processChangeSet(YamlChangeSetDTO yamlChangeSet) {
    final String accountId = yamlChangeSet.getAccountId();
    try (AccountLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR);
         AutoLogContext ignore2 = createLogContextForChangeSet(yamlChangeSet)) {
      log.info("Processing changeSetId: [{}]", yamlChangeSet.getChangesetId());
      if (changeSetReachedMaxRetries(yamlChangeSet)) {
        yamlChangeSetService.markSkippedWithMessageCode(
            yamlChangeSet.getAccountId(), yamlChangeSet.getChangesetId(), MAX_RETRY_COUNT_EXCEEDED_CODE);
      } else {
        yamlChangeSetLifeCycleHandlerService.handleChangeSet(yamlChangeSet);
      }
    } catch (UnsupportedOperationException ex) {
      log.error("Couldn't process change set : {}", yamlChangeSet, ex);
    }
  }

  private boolean changeSetReachedMaxRetries(YamlChangeSetDTO yamlChangeSet) {
    return yamlChangeSet.getRetryCount() >= MAX_RETRY_FOR_CHANGESET;
  }

  private List<YamlChangeSetDTO> getYamlChangeSetsPerQueueKey() {
    final Set<ChangeSetGroupingKey> queuedChangeSetKeys = getQueuedChangesetKeys();
    final Set<ChangeSetGroupingKey> runningChangeSetKeys = getRunningChangesetKeys();
    final Set<String> maxedOutAccountIds = getMaxedOutAccountIds(runningChangeSetKeys);
    final Set<ChangeSetGroupingKey> eligibleChangeSetKeysForPicking =
        getEligibleQueueKeysForPicking(queuedChangeSetKeys, runningChangeSetKeys, maxedOutAccountIds);

    if (shouldPrintStatusLogs()) {
      lastTimestampForStatusLogPrint.set(System.currentTimeMillis());
      log.info(
          "queuedChangeSetKeys:{}, runningChangeSetKeys:{}, maxedOutAccountIds: {} ,eligibleChangeSetKeysForPicking:{}",
          queuedChangeSetKeys, runningChangeSetKeys, maxedOutAccountIds, eligibleChangeSetKeysForPicking);

      if (isNotEmpty(maxedOutAccountIds)) {
        log.info(
            " Skipping processing of GitChangeSet for Accounts :[{}], as concurrently running tasks have maxed out",
            maxedOutAccountIds);
      }
    }
    return eligibleChangeSetKeysForPicking.stream()
        .map(changeSetGroupingKey
            -> getQueuedChangeSetForWaitingQueueKey(
                changeSetGroupingKey.getAccountId(), changeSetGroupingKey.getQueueKey()))
        .filter(Objects::nonNull)
        .collect(toList());
  }

  private boolean shouldPrintStatusLogs() {
    return lastTimestampForStatusLogPrint.get() == 0
        || (System.currentTimeMillis() - lastTimestampForStatusLogPrint.get() > TimeUnit.MINUTES.toMillis(5));
  }

  private YamlChangeSetDTO getQueuedChangeSetForWaitingQueueKey(String accountId, String queueKey) {
    try (
        AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR);
        AutoLogContext ignore2 = YamlProcessingLogContext.builder().changeSetQueueKey(queueKey).build(OVERRIDE_ERROR)) {
      final YamlChangeSetDTO yamlChangeSet =
          gitChangeSetRunnableQueueHelper.getQueuedChangeSetForWaitingQueueKey(accountId, queueKey,
              getMaxRunningChangesetsForAccount(), yamlChangeSetService, runningStatusList, persistentLocker);
      if (yamlChangeSet == null) {
        log.info("no changeset found to process");
      }
      return yamlChangeSet;
    } catch (Exception ex) {
      log.error(
          format("error while finding changeset to process for accountId=[%s], queueKey=[%s]", accountId, queueKey),
          ex);
    }
    return null;
  }

  @VisibleForTesting
  boolean shouldRun() {
    return !getMaintenanceFlag() && queueController.isPrimary();
  }

  private void handleStuckChangeSets() {
    if (shouldPerformStuckJobCheck()) {
      lastTimestampForStuckJobCheck.set(System.currentTimeMillis());
      gitChangeSetRunnableHelper.handleOldQueuedChangeSets(yamlChangeSetService);
      handleStuckRunningChangesets();
      log.info("Successfully handled stuck change sets");
    }
  }

  private void handleStuckRunningChangesets() {
    List<String> runningAccountIdList =
        gitChangeSetRunnableHelper.getRunningAccountIdList(yamlChangeSetService, runningStatusList);
    retryAnyStuckYamlChangeSet(runningAccountIdList);
  }

  /**
   * This job runs every few seconds. We dont need to check for stuck job every time.
   * We will check it every 30 mins.
   */
  boolean shouldPerformStuckJobCheck() {
    return lastTimestampForStuckJobCheck.get() == 0
        || (System.currentTimeMillis() - lastTimestampForStuckJobCheck.get() > TimeUnit.MINUTES.toMillis(30));
  }

  /**
   * If any YamlChangeSet is stuck in Running mode for more than 90 minutes
   * (somehow delegate response was lost or something, mark that changeset as Queued again)
   * So let it be processed again as we don't know if that was applied.
   * If it was already applied, delegate won't do anything.
   */
  void retryAnyStuckYamlChangeSet(List<String> runningAccountIdList) {
    if (isEmpty(runningAccountIdList)) {
      return;
    }

    List<YamlChangeSetDTO> stuckChangeSets = gitChangeSetRunnableHelper.getStuckYamlChangeSets(
        yamlChangeSetService, runningAccountIdList, runningStatusList);

    if (isNotEmpty(stuckChangeSets)) {
      // Map Acc vs such yamlChangeSets (with multigit support, there can be more than 1 for an account)
      Map<String, List<YamlChangeSetDTO>> accountIdToStuckChangeSets =
          stuckChangeSets.stream().collect(Collectors.groupingBy(YamlChangeSetDTO::getAccountId));

      // Mark these yamlChangeSets as Queued.
      accountIdToStuckChangeSets.forEach(this::retryOrSkipStuckChangeSets);
    }
  }

  private void retryOrSkipStuckChangeSets(String accountId, List<YamlChangeSetDTO> changeSets) {
    final List<String> yamlChangeSetIds = idsOfChangeSets(changeSets);
    yamlChangeSetService.updateStatusAndIncrementRetryCountForYamlChangeSets(
        accountId, YamlChangeSetStatus.QUEUED, runningStatusList, yamlChangeSetIds);
    log.info("Retrying stuck changesets: [{}]", yamlChangeSetIds);
    yamlChangeSetService.markQueuedYamlChangeSetsWithMaxRetriesAsSkipped(accountId, MAX_RETRY_FOR_CHANGESET);
  }

  @NotNull
  private List<String> idsOfChangeSets(List<YamlChangeSetDTO> changeSets) {
    return changeSets.stream().map(YamlChangeSetDTO::getChangesetId).collect(toList());
  }

  private Set<String> getMaxedOutAccountIds(Set<ChangeSetGroupingKey> runningQueueKeys) {
    return runningQueueKeys.stream()
        .collect(Collectors.groupingBy(
            ChangeSetGroupingKey::getAccountId, Collectors.summingInt(ChangeSetGroupingKey::getCount)))
        .entrySet()
        .stream()
        .filter(accountIdTotalCountEntry -> accountIdTotalCountEntry.getValue() >= getMaxRunningChangesetsForAccount())
        .map(Map.Entry::getKey)
        .collect(Collectors.toSet());
  }

  private Set<ChangeSetGroupingKey> getEligibleQueueKeysForPicking(Set<ChangeSetGroupingKey> queuedChangesetKeys,
      Set<ChangeSetGroupingKey> runningQueueKeys, Set<String> maxedOutAccountIds) {
    return Sets.difference(queuedChangesetKeys, runningQueueKeys)
        .stream()
        .filter(changeSetGroupingKey -> !maxedOutAccountIds.contains(changeSetGroupingKey.getAccountId()))
        .collect(Collectors.toSet());
  }

  private Set<ChangeSetGroupingKey> getQueuedChangesetKeys() {
    Criteria criteria = Criteria.where(YamlChangeSetKeys.status).is(YamlChangeSetStatus.QUEUED);
    return yamlChangeSetService.getChangesetGroupingKeys(criteria);
  }

  private Set<ChangeSetGroupingKey> getRunningChangesetKeys() {
    Criteria criteria = Criteria.where(YamlChangeSetKeys.status).in(runningStatusList);
    return yamlChangeSetService.getChangesetGroupingKeys(criteria);
  }

  @VisibleForTesting
  int getMaxRunningChangesetsForAccount() {
    return MAX_RUNNING_CHANGESETS_FOR_ACCOUNT;
  }

  private void handleChangeSetWithMaxRetry() {
    if (shouldPerformMaxRetriedJobCheck()) {
      lastTimestampForQueuedJobCheck.set(System.currentTimeMillis());
      skipChangeSetsWithMaxRetries();
      log.info("Successfully handled max retried stuck queued change sets");
    }
  }

  private void skipChangeSetsWithMaxRetries() {
    yamlChangeSetService.markQueuedYamlChangeSetsWithMaxRetriesAsSkipped(MAX_RETRY_FOR_CHANGESET);
  }

  boolean shouldPerformMaxRetriedJobCheck() {
    return lastTimestampForQueuedJobCheck.get() == 0
        || (System.currentTimeMillis() - lastTimestampForQueuedJobCheck.get()
            > TimeUnit.MINUTES.toMillis(MAX_RETRIED_QUEUED_JOB_CHECK_INTERVAL));
  }
}
