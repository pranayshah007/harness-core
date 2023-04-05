/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.yaml.gitSync;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.WingsException.ExecutionContext.MANAGER;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_NESTS;
import static io.harness.maintenance.MaintenanceController.getMaintenanceFlag;

import static software.wings.beans.yaml.YamlConstants.GIT_YAML_LOG_PREFIX;

import static dev.morphia.aggregation.Accumulator.accumulator;
import static dev.morphia.aggregation.Group.first;
import static dev.morphia.aggregation.Group.grouping;
import static java.lang.String.format;
import static java.util.Collections.singletonList;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.stream.Collectors.toList;

import io.harness.exception.ExceptionLogger;
import io.harness.exception.WingsException;
import io.harness.logging.AccountLogContext;
import io.harness.logging.AutoLogContext;
import io.harness.logging.ProcessTimeLogContext;

import software.wings.core.managerConfiguration.ConfigurationController;
import software.wings.dl.WingsPersistence;
import software.wings.service.impl.yaml.YamlProcessingLogContext;
import software.wings.service.intfc.yaml.YamlChangeSetService;
import software.wings.service.intfc.yaml.YamlGitService;
import software.wings.yaml.gitSync.YamlChangeSet.Status;
import software.wings.yaml.gitSync.YamlChangeSet.YamlChangeSetKeys;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Stopwatch;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.mongodb.AggregationOptions;
import dev.morphia.aggregation.AggregationPipeline;
import dev.morphia.aggregation.Group;
import dev.morphia.query.Query;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import lombok.EqualsAndHashCode;
import lombok.EqualsAndHashCode.Include;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

/**
 * @author bsollish on 09/26/17
 */
@Slf4j
public class GitChangeSetRunnable implements Runnable {
  public static final List<Status> RUNNING_STATUS_LIST = singletonList(Status.RUNNING);
  //  marking this 1 for now to suit migration. Should be increases to a higher number once migration succeeds
  public static final int MAX_RUNNING_CHANGESETS_FOR_ACCOUNT = 5;
  private static final AtomicLong lastTimestampForStuckJobCheck = new AtomicLong(0);
  private static final AtomicLong lastTimestampForStatusLogPrint = new AtomicLong(0);

  @Inject private YamlGitService yamlGitSyncService;
  @Inject private YamlChangeSetService yamlChangeSetService;
  @Inject private WingsPersistence wingsPersistence;
  @Inject private ConfigurationController configurationController;
  @Inject private GitChangeSetRunnableHelper gitChangeSetRunnableHelper;

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

      final List<YamlChangeSet> yamlChangeSets = getYamlChangeSetsToProcess();

      if (!yamlChangeSets.isEmpty()) {
        log.info("change sets to process =[{}]", yamlChangeSets.stream().map(YamlChangeSet::getUuid).collect(toList()));
        yamlChangeSets.forEach(this::processChangeSet);

        try (ProcessTimeLogContext ignore4 =
                 new ProcessTimeLogContext(stopwatch.elapsed(MILLISECONDS), OVERRIDE_ERROR)) {
          if (!yamlChangeSets.isEmpty()) {
            log.info(GIT_YAML_LOG_PREFIX + "Successfully handled {} change sets", yamlChangeSets.size());
          }
        }
      }
    } catch (WingsException exception) {
      ExceptionLogger.logProcessedMessages(exception, MANAGER, log);
    } catch (Exception exception) {
      log.error(GIT_YAML_LOG_PREFIX + "Unexpected error", exception);
    }
  }

  private List<YamlChangeSet> getYamlChangeSetsToProcess() {
    return getYamlChangeSetsPerQueueKey();
  }

  private AutoLogContext createLogContextForChangeSet(YamlChangeSet yamlChangeSet) {
    return YamlProcessingLogContext.builder()
        .changeSetQueueKey(yamlChangeSet.getQueueKey())
        .changeSetId(yamlChangeSet.getUuid())
        .build(OVERRIDE_NESTS);
  }

  private void processChangeSet(YamlChangeSet yamlChangeSet) {
    final String accountId = yamlChangeSet.getAccountId();
    try (AccountLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_NESTS);
         AutoLogContext ignore2 = createLogContextForChangeSet(yamlChangeSet)) {
      log.info(GIT_YAML_LOG_PREFIX + "Processing changeSetId: [{}]", yamlChangeSet.getUuid());

      if (yamlChangeSet.isGitToHarness()) {
        yamlGitSyncService.handleGitChangeSet(yamlChangeSet, accountId);
      } else {
        yamlGitSyncService.handleHarnessChangeSet(yamlChangeSet, accountId);
      }
    } catch (Exception ex) {
      log.error(format("Unexpected error while processing commit for accountId: [%s], changeSetId =[%s] ", accountId,
                    yamlChangeSet.getUuid()),
          ex);
      yamlChangeSetService.updateStatusForGivenYamlChangeSets(
          accountId, Status.FAILED, RUNNING_STATUS_LIST, singletonList(yamlChangeSet.getUuid()));
    }
  }

  private boolean shouldPrintStatusLogs() {
    return lastTimestampForStatusLogPrint.get() == 0
        || (System.currentTimeMillis() - lastTimestampForStatusLogPrint.get() > TimeUnit.MINUTES.toMillis(5));
  }

  @NotNull
  private List<YamlChangeSet> getYamlChangeSetsPerQueueKey() {
    final Set<ChangeSetGroupingKey> queuedChangeSetKeys = getQueuedChangesetKeys();
    final Set<ChangeSetGroupingKey> runningChangeSetKeys = getRunningChangesetKeys();
    final Set<String> maxedOutAccountIds = getMaxedOutAccountIds(runningChangeSetKeys);
    final Set<ChangeSetGroupingKey> eligibleChangeSetKeysForPicking =
        getEligibleQueueKeysForPicking(queuedChangeSetKeys, runningChangeSetKeys, maxedOutAccountIds);

    if (shouldPrintStatusLogs()) {
      lastTimestampForStatusLogPrint.set(System.currentTimeMillis());
      log.info(GIT_YAML_LOG_PREFIX
              + "queuedChangeSetKeys:{}, runningChangeSetKeys:{}, maxedOutAccountIds: {} ,eligibleChangeSetKeysForPicking:{}",
          queuedChangeSetKeys, runningChangeSetKeys, maxedOutAccountIds, eligibleChangeSetKeysForPicking);
      if (isNotEmpty(maxedOutAccountIds)) {
        log.info(GIT_YAML_LOG_PREFIX
                + " Skipping processing of GitChangeSet for Accounts :[{}], as concurrently running tasks have maxed out",
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

  private YamlChangeSet getQueuedChangeSetForWaitingQueueKey(String accountId, String queueKey) {
    try (
        AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_NESTS);
        AutoLogContext ignore2 = YamlProcessingLogContext.builder().changeSetQueueKey(queueKey).build(OVERRIDE_NESTS)) {
      return yamlChangeSetService.getQueuedChangeSetForWaitingQueueKey(
          accountId, queueKey, getMaxRunningChangesetsForAccount());
    } catch (Exception ex) {
      log.error(
          format("error while finding changeset to process for accountId=[%s], queueKey=[%s]", accountId, queueKey),
          ex);
    }
    return null;
  }

  private boolean shouldRun() {
    return !getMaintenanceFlag() && configurationController.isPrimary();
  }

  private void handleStuckChangeSets() {
    if (shouldPerformStuckJobCheck()) {
      lastTimestampForStuckJobCheck.set(System.currentTimeMillis());
      gitChangeSetRunnableHelper.handleOldQueuedChangeSets(wingsPersistence);
      handleStuckRunningChangesets();
    }
  }

  private void handleStuckRunningChangesets() {
    List<String> runningAccountIdList = gitChangeSetRunnableHelper.getRunningAccountIdList(wingsPersistence);
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

    // Get yamlChangeSet that is in running mode for more than 90 mins.
    List<YamlChangeSet> stuckChangeSets =
        gitChangeSetRunnableHelper.getStuckYamlChangeSets(wingsPersistence, runningAccountIdList);

    if (isNotEmpty(stuckChangeSets)) {
      // Map Acc vs such yamlChangeSets (with multigit support, there can be more than 1 for an account)
      Map<String, List<YamlChangeSet>> accountIdToStuckChangeSets =
          stuckChangeSets.stream().collect(Collectors.groupingBy(YamlChangeSet::getAccountId));

      // Mark these yamlChagneSets as Queued.
      accountIdToStuckChangeSets.forEach(this::retryOrSkipStuckChangeSets);
    }
  }

  private void retryOrSkipStuckChangeSets(String accountId, List<YamlChangeSet> changeSets) {
    final List<String> yamlChangeSetIds = uuidsOfChangeSets(changeSets);
    yamlChangeSetService.updateStatusAndIncrementRetryCountForYamlChangeSets(
        accountId, Status.QUEUED, RUNNING_STATUS_LIST, yamlChangeSetIds);
    log.info("Retrying stuck change sets: [{}]", yamlChangeSetIds);

    yamlChangeSetService.markQueuedYamlChangeSetsWithMaxRetriesAsSkipped(accountId);
  }

  @NotNull
  private List<String> uuidsOfChangeSets(List<YamlChangeSet> changeSets) {
    return changeSets.stream().map(YamlChangeSet::getUuid).collect(toList());
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
    final Query<YamlChangeSet> query =
        wingsPersistence.createAuthorizedQuery(YamlChangeSet.class).filter(YamlChangeSetKeys.status, Status.QUEUED);
    return getChangesetGroupingKeys(query);
  }

  private Set<ChangeSetGroupingKey> getRunningChangesetKeys() {
    final Query<YamlChangeSet> query =
        wingsPersistence.createAuthorizedQuery(YamlChangeSet.class).filter(YamlChangeSetKeys.status, Status.RUNNING);
    return getChangesetGroupingKeys(query);
  }

  @NotNull
  private Set<ChangeSetGroupingKey> getChangesetGroupingKeys(Query<YamlChangeSet> query) {
    final Set<ChangeSetGroupingKey> keys = new HashSet<>();
    AggregationPipeline aggregationPipeline =
        wingsPersistence.getDatastore(YamlChangeSet.class)
            .createAggregation(YamlChangeSet.class)
            .match(query)
            .group(Group.id(grouping(YamlChangeSetKeys.accountId), grouping(YamlChangeSetKeys.queueKey)),
                grouping(YamlChangeSetKeys.accountId, first(YamlChangeSetKeys.accountId)),
                grouping(YamlChangeSetKeys.queueKey, first(YamlChangeSetKeys.queueKey)),
                grouping("count", accumulator("$sum", 1)));
    int limit = wingsPersistence.getMaxDocumentLimit(YamlChangeSet.class);
    if (limit > 0) {
      aggregationPipeline.limit(limit);
    }
    aggregationPipeline
        .aggregate(ChangeSetGroupingKey.class,
            AggregationOptions.builder()
                .maxTime(wingsPersistence.getMaxTimeMs(YamlChangeSet.class), TimeUnit.MILLISECONDS)
                .build())
        .forEachRemaining(keys::add);
    return keys;
  }

  @VisibleForTesting
  int getMaxRunningChangesetsForAccount() {
    return MAX_RUNNING_CHANGESETS_FOR_ACCOUNT;
  }

  @Value
  @EqualsAndHashCode(onlyExplicitlyIncluded = true)
  public static class ChangeSetGroupingKey {
    @Include String accountId;
    @Include String queueKey;
    int count;

    @Override
    public String toString() {
      return "{"
          + "accountId='" + accountId + '\'' + ", queueKey='" + queueKey + '\'' + ", count=" + count + '}';
    }
  }
}
