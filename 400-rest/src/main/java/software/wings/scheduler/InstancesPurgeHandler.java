/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.scheduler;

import static io.harness.mongo.iterator.MongoPersistenceIterator.SchedulingType.REGULAR;

import static software.wings.beans.Account.AccountKeys;

import static java.time.Duration.ofDays;
import static java.time.Duration.ofHours;
import static java.time.Duration.ofMinutes;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.ff.FeatureFlagService;
import io.harness.iterator.PersistenceIteratorFactory;
import io.harness.mongo.iterator.MongoPersistenceIterator;
import io.harness.mongo.iterator.filter.MorphiaFilterExpander;
import io.harness.mongo.iterator.provider.MorphiaPersistenceProvider;
import io.harness.workers.background.AccountLevelEntityProcessController;

import software.wings.beans.Account;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.instance.InstanceService;
import software.wings.service.intfc.instance.stats.InstanceStatService;
import software.wings.service.intfc.instance.stats.ServerlessInstanceStatService;

import com.google.common.base.Stopwatch;
import com.google.inject.Inject;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;

@DisallowConcurrentExecution
@Slf4j
@OwnedBy(HarnessTeam.CDP)
public class InstancesPurgeHandler implements MongoPersistenceIterator.Handler<Account> {
  private static final int MONTHS_TO_RETAIN_INSTANCES_EXCLUDING_CURRENT_MONTH = 2;
  private static final int MONTHS_TO_RETAIN_INSTANCE_STATS_EXCLUDING_CURRENT_MONTH = 6;

  @Inject private AccountService accountService;
  @Inject private InstanceService instanceService;
  @Inject private InstanceStatService instanceStatsService;
  @Inject private ServerlessInstanceStatService serverlessInstanceStatService;
  @Inject private FeatureFlagService featureFlagService;
  @Inject private PersistenceIteratorFactory persistenceIteratorFactory;
  @Inject private MorphiaPersistenceProvider<Account> persistenceProvider;

  public void registerIterators() {
    persistenceIteratorFactory.createPumpIteratorWithDedicatedThreadPool(
        PersistenceIteratorFactory.PumpExecutorOptions.builder()
            .name("InstancesPurging")
            .poolSize(5)
            .interval(Duration.ofMinutes(1))
            .build(),
        Account.class,
        MongoPersistenceIterator.<Account, MorphiaFilterExpander<Account>>builder()
            .clazz(Account.class)
            .fieldName(AccountKeys.instancesPurgeTaskIteration)
            .targetInterval(ofDays(7))
            .acceptableNoAlertDelay(ofMinutes(Integer.MAX_VALUE))
            .acceptableExecutionTime(ofHours(15))
            .handler(this)
            .entityProcessController(new AccountLevelEntityProcessController(accountService))
            .schedulingType(REGULAR)
            .persistenceProvider(persistenceProvider)
            .redistribute(true));
  }

  @Override
  public void handle(Account account) {
    log.info("Starting execution of instances and instance stats purge job");
    Stopwatch sw = Stopwatch.createStarted();

    if (featureFlagService.isEnabled(FeatureName.USE_INSTANCES_PURGE_ITERATOR_FW, account.getUuid())) {
      // TODO: purging stats can be removed in Jan 2023 as we started adding 6 months TTL index in July
      purgeOldStats(account);
      purgeOldServerlessInstanceStats(account);
      purgeOldDeletedInstances(account);
    } else {
      log.info(String.format(
          "Feature flag %s is NOT enabled for account %s which means instances purging task will be handled by legacy quartz job",
          FeatureName.USE_INSTANCES_PURGE_ITERATOR_FW, account.getUuid()));
    }

    log.info("Execution of instances and instance stats purge job completed. Time taken: {} millis",
        sw.elapsed(TimeUnit.MILLISECONDS));
  }

  private void purgeOldStats(Account account) {
    log.info("Starting purge of instance stats for account {}", account.getUuid());
    Stopwatch sw = Stopwatch.createStarted();

    boolean purged = instanceStatsService.purgeUpTo(
        getRetentionStartTime(MONTHS_TO_RETAIN_INSTANCE_STATS_EXCLUDING_CURRENT_MONTH), account);
    if (purged) {
      log.info("Purge of instance stats completed successfully for account {}. Time taken: {} millis",
          account.getUuid(), sw.elapsed(TimeUnit.MILLISECONDS));
    } else {
      log.info("Purge of instance stats failed for account {}. Time taken: {} millis", account.getUuid(),
          sw.elapsed(TimeUnit.MILLISECONDS));
    }
  }

  private void purgeOldServerlessInstanceStats(Account account) {
    log.info("Starting purge of serverless instance stats for account {}", account.getUuid());
    Stopwatch sw = Stopwatch.createStarted();

    boolean purged = serverlessInstanceStatService.purgeUpTo(
        getRetentionStartTime(MONTHS_TO_RETAIN_INSTANCE_STATS_EXCLUDING_CURRENT_MONTH), account);
    if (purged) {
      log.info("Purge of serverless instance stats completed successfully for account {}. Time taken: {} millis",
          account.getUuid(), sw.elapsed(TimeUnit.MILLISECONDS));
    } else {
      log.info("Purge of serverless instance stats failed for account {}. Time taken: {} millis", account.getUuid(),
          sw.elapsed(TimeUnit.MILLISECONDS));
    }
  }

  private void purgeOldDeletedInstances(Account account) {
    log.info("Starting purge of instances for account {}", account.getUuid());
    Stopwatch sw = Stopwatch.createStarted();

    boolean purged = instanceService.purgeDeletedUpTo(
        getRetentionStartTime(MONTHS_TO_RETAIN_INSTANCES_EXCLUDING_CURRENT_MONTH), account);
    if (purged) {
      log.info("Purge of instances completed successfully for account {}. Time taken: {} millis", account.getUuid(),
          sw.elapsed(TimeUnit.MILLISECONDS));
    } else {
      log.info("Purge of instances failed for account {}. Time taken: {} millis", account.getUuid(),
          sw.elapsed(TimeUnit.MILLISECONDS));
    }
  }

  public Instant getRetentionStartTime(int monthsToSubtract) {
    LocalDate firstDayOfMonthOfRetention =
        LocalDate.now(ZoneOffset.UTC).minusMonths(monthsToSubtract).with(TemporalAdjusters.firstDayOfMonth());

    return firstDayOfMonthOfRetention.atStartOfDay().toInstant(ZoneOffset.UTC);
  }
}
