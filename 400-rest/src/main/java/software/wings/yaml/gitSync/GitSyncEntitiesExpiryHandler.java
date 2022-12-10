/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.yaml.gitSync;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.mongo.iterator.MongoPersistenceIterator.SchedulingType.REGULAR;
import static io.harness.persistence.CreatedAtAccess.CREATED_AT_KEY;

import static software.wings.beans.Account.AccountKeys;
import static software.wings.yaml.gitSync.YamlChangeSet.Status.COMPLETED;
import static software.wings.yaml.gitSync.YamlChangeSet.Status.FAILED;
import static software.wings.yaml.gitSync.YamlChangeSet.Status.SKIPPED;

import static java.time.Duration.ofMinutes;

import io.harness.exception.WingsException;
import io.harness.iterator.IteratorExecutionHandler;
import io.harness.iterator.IteratorPumpModeHandler;
import io.harness.iterator.PersistenceIteratorFactory;
import io.harness.mongo.iterator.MongoPersistenceIterator;
import io.harness.mongo.iterator.MongoPersistenceIterator.Handler;
import io.harness.mongo.iterator.filter.MorphiaFilterExpander;
import io.harness.mongo.iterator.provider.MorphiaPersistenceProvider;
import io.harness.workers.background.AccountLevelEntityProcessController;

import software.wings.beans.Account;
import software.wings.beans.GitCommit;
import software.wings.beans.GitCommit.GitCommitKeys;
import software.wings.dl.WingsPersistence;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.yaml.YamlChangeSetService;
import software.wings.service.intfc.yaml.sync.GitSyncErrorService;
import software.wings.service.intfc.yaml.sync.GitSyncService;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.mongodb.morphia.query.FindOptions;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.Sort;

@Slf4j
public class GitSyncEntitiesExpiryHandler extends IteratorPumpModeHandler implements Handler<Account> {
  @Inject private PersistenceIteratorFactory persistenceIteratorFactory;
  @Inject private AccountService accountService;
  @Inject private GitSyncService gitSyncService;
  @Inject private AppService appService;
  @Inject private WingsPersistence wingsPersistence;
  @Inject private GitSyncErrorService gitSyncErrorService;
  @Inject private MorphiaPersistenceProvider<Account> persistenceProvider;
  @Inject private YamlChangeSetService yamlChangeSetService;

  private static long ONE_MONTH_IN_MILLIS = 2592000000L;
  private static long TWELVE_MONTH_IN_MILLIS = 31104000000L;

  // Delete max 20k records per job run
  private static final int MAX_DELETE_PER_JOB_RUN = 20000;
  // Delete any YamlChangeSets older than 30 days
  private static final int RETENTION_PERIOD_IN_DAYS = 30;
  // Delete 2k record in a batch
  private static final String BATCH_SIZE = "500";

  @Override
  protected void createAndStartIterator(
      PersistenceIteratorFactory.PumpExecutorOptions executorOptions, Duration targetInterval) {
    iterator = (MongoPersistenceIterator<Account, MorphiaFilterExpander<Account>>)
                   persistenceIteratorFactory.createPumpIteratorWithDedicatedThreadPool(executorOptions,
                       GitSyncEntitiesExpiryHandler.class,
                       MongoPersistenceIterator.<Account, MorphiaFilterExpander<Account>>builder()
                           .clazz(Account.class)
                           .fieldName(AccountKeys.gitSyncExpiryCheckIteration)
                           .targetInterval(targetInterval)
                           .acceptableNoAlertDelay(ofMinutes(120))
                           .acceptableExecutionTime(ofMinutes(5))
                           .handler(this)
                           .entityProcessController(new AccountLevelEntityProcessController(accountService))
                           .schedulingType(REGULAR)
                           .persistenceProvider(persistenceProvider)
                           .redistribute(true));
  }

  @Override
  public void registerIterator(IteratorExecutionHandler iteratorExecutionHandler) {
    iteratorName = "GitSyncEntityExpiryCheck";

    // Register the iterator with the iterator config handler.
    iteratorExecutionHandler.registerIteratorHandler(iteratorName, this);
  }

  @Override
  public void handle(Account account) {
    long oneMonthExpiryMillis = System.currentTimeMillis() - ONE_MONTH_IN_MILLIS;
    long twelveMonthExpiryMillis = System.currentTimeMillis() - TWELVE_MONTH_IN_MILLIS;
    log.info("Running git sync expiry handler {} .", account.getUuid());
    handleGitCommitInGitFileActivitySummary(account, twelveMonthExpiryMillis);
    handleGitFileActivity(account, twelveMonthExpiryMillis);
    handleGitError(account, oneMonthExpiryMillis);
    handleGitCommits(account, twelveMonthExpiryMillis);

    handleYamlChangeSet(account);
  }

  private void handleYamlChangeSet(Account account) {
    try {
      yamlChangeSetService.deleteChangeSets(account.getUuid(), new YamlChangeSet.Status[] {FAILED, SKIPPED, COMPLETED},
          MAX_DELETE_PER_JOB_RUN, BATCH_SIZE, RETENTION_PERIOD_IN_DAYS);

    } catch (WingsException e) {
      log.error("YamlChangeSet deletion cron job failed with error: " + e.getParams().get("message"));
    }
  }

  @VisibleForTesting
  void handleGitCommitInGitFileActivitySummary(Account account, long expiryMillis) {
    log.info("Running git file activity summary expiry handler for account {}.", account.getUuid());
    boolean deleted = gitSyncService.deleteGitCommitsBeforeTime(expiryMillis, account.getUuid());
    if (deleted) {
      log.info("Deleted expired Git File Activity Summary for account {}", account.getUuid());
    }
  }

  @VisibleForTesting
  void handleGitFileActivity(Account account, long expiryMillis) {
    log.info("Running git file activity  expiry handler for account {}  .", account.getUuid());

    boolean deleted = gitSyncService.deleteGitActivityBeforeTime(expiryMillis, account.getUuid());
    if (deleted) {
      log.info("Deleted {} expired Git File Activity for account {}", deleted, account.getUuid());
    }
  }

  @VisibleForTesting
  void handleGitError(Account account, long expiryMillis) {
    boolean deleted = gitSyncErrorService.deleteGitSyncErrorsBeforeTime(expiryMillis, account.getUuid());
    log.info("Deleted {} expired Git Sync Error for account {}.", deleted, account.getUuid());
  }

  @VisibleForTesting
  void handleGitCommits(Account account, long expiryMillis) {
    List<String> gitCommitIds;
    do {
      Query<GitCommit> gitCommitsQuery = wingsPersistence.createQuery(GitCommit.class)
                                             .filter(GitCommit.ACCOUNT_ID_KEY2, account.getUuid())
                                             .order(Sort.descending(GitCommit.CREATED_AT_KEY));
      gitCommitsQuery.criteria(CREATED_AT_KEY).lessThan(expiryMillis);
      gitCommitsQuery.project(GitCommitKeys.status, true);
      gitCommitsQuery.project(GitCommitKeys.repositoryName, true);
      gitCommitsQuery.project(GitCommitKeys.branchName, true);
      gitCommitsQuery.project(GitCommitKeys.createdAt, true);
      gitCommitsQuery.project(GitCommitKeys.accountId, true);
      gitCommitsQuery.project(GitCommitKeys.gitConnectorId, true);
      List<GitCommit> gitCommits = gitCommitsQuery.asList(new FindOptions().limit(1000));
      // Removing topmost (as it can be head).
      List<GitCommit> gitCommitsToBeDeleted = new ArrayList<>();
      Set<Pair<String, Pair<String, String>>> connectorRepoBranchSet = new HashSet<>();

      for (GitCommit gitCommit : gitCommits) {
        Pair<String, Pair<String, String>> pair =
            Pair.of(gitCommit.getGitConnectorId(), Pair.of(gitCommit.getRepositoryName(), gitCommit.getBranchName()));
        if (connectorRepoBranchSet.contains(pair)) {
          gitCommitsToBeDeleted.add(gitCommit);
        } else {
          // Adding first successful commit to set and hence will delete all the commits post this.
          if (GitCommit.GIT_COMMIT_PROCESSED_STATUS.contains(gitCommit.getStatus())) {
            connectorRepoBranchSet.add(pair);
          }
        }
      }
      if (isEmpty(gitCommitsToBeDeleted)) {
        return;
      }
      gitCommitIds = gitCommitsToBeDeleted.stream().map(GitCommit::getUuid).collect(Collectors.toList());
      boolean deleted = wingsPersistence.delete(wingsPersistence.createQuery(GitCommit.class)
                                                    .filter(GitCommit.ACCOUNT_ID_KEY2, account.getUuid())
                                                    .field(GitCommit.ID_KEY2)
                                                    .in(gitCommitIds));
      if (deleted) {
        log.info("Deleted expired Git Commit for account {}. Expired Ids are: {} ", account.getUuid(), gitCommitIds);
      }
    } while (isNotEmpty(gitCommitIds));
  }
}
