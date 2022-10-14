/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.scheduler;

import io.harness.beans.FeatureName;
import io.harness.ff.FeatureFlagService;
import io.harness.persistence.HIterator;
import io.harness.persistence.HPersistence;
import io.harness.scheduler.BackgroundExecutorService;
import io.harness.scheduler.PersistentScheduler;

import software.wings.beans.Account;
import software.wings.service.intfc.instance.InstanceService;
import software.wings.service.intfc.instance.stats.InstanceStatService;
import software.wings.service.intfc.instance.stats.ServerlessInstanceStatService;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Stopwatch;
import com.google.inject.Inject;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.quartz.CronScheduleBuilder;
import org.quartz.DateBuilder;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;

@DisallowConcurrentExecution
@Slf4j
public class InstancesPurgeJob implements Job {
  private static final String INSTANCES_PURGE_CRON_NAME = "INSTANCES_PURGE_CRON_NAME";
  private static final String INSTANCES_PURGE_CRON_GROUP = "INSTANCES_PURGE_CRON_GROUP";

  private static final int MONTHS_TO_RETAIN_INSTANCES_EXCLUDING_CURRENT_MONTH = 2;
  private static final int MONTHS_TO_RETAIN_INSTANCE_STATS_EXCLUDING_CURRENT_MONTH = 6;

  @Inject private BackgroundExecutorService executorService;
  @Inject private InstanceService instanceService;
  @Inject private InstanceStatService instanceStatsService;
  @Inject private ServerlessInstanceStatService serverlessInstanceStatService;
  @Inject private HPersistence persistence;
  @Inject private FeatureFlagService featureFlagService;

  public static void add(PersistentScheduler jobScheduler) {
    JobDetail job = JobBuilder.newJob(InstancesPurgeJob.class)
                        .withIdentity(INSTANCES_PURGE_CRON_NAME, INSTANCES_PURGE_CRON_GROUP)
                        .build();

    Trigger trigger = TriggerBuilder.newTrigger()
                          .withIdentity(INSTANCES_PURGE_CRON_NAME, INSTANCES_PURGE_CRON_GROUP)
                          .withSchedule(CronScheduleBuilder.atHourAndMinuteOnGivenDaysOfWeek(
                              12, 0, DateBuilder.SATURDAY, DateBuilder.SUNDAY))
                          .build();
    jobScheduler.ensureJob__UnderConstruction(job, trigger);
  }

  @Override
  public void execute(JobExecutionContext jobExecutionContext) {
    log.info("Triggering instances and instance stats purge job asynchronously");
    executorService.submit(this::purge);
  }

  @VisibleForTesting
  void purge() {
    log.info("Starting execution of instances and instance stats purge job");
    Stopwatch sw = Stopwatch.createStarted();

    try (HIterator<Account> accounts =
             new HIterator<>(persistence.createQuery(Account.class).project(Account.ID_KEY2, true).fetch())) {
      while (accounts.hasNext()) {
        final Account account = accounts.next();
        if (featureFlagService.isNotEnabled(FeatureName.USE_INSTANCES_PURGE_ITERATOR_FW, account.getUuid())) {
          // TODO: purging stats can be removed in Jan 2023 as we started adding 6 months TTL index in July
          purgeOldStats(account);
          purgeOldServerlessInstanceStats(account);
          purgeOldDeletedInstances(account);
        } else {
          log.info(String.format(
              "Feature flag %s is enabled for account %s which means instances purging task will be handled by iterator framework",
              FeatureName.USE_INSTANCES_PURGE_ITERATOR_FW, account.getUuid()));
        }
      }
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
