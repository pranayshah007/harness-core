/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.scheduler;

import io.harness.scheduler.BackgroundExecutorService;
import io.harness.scheduler.PersistentScheduler;

import software.wings.graphql.datafetcher.instance.InstanceTimeSeriesDataHelper;
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

  @Inject private BackgroundExecutorService executorService;
  @Inject private InstanceService instanceService;
  @Inject private InstanceStatService instanceStatsService;
  @Inject private ServerlessInstanceStatService serverlessInstanceStatService;
  @Inject InstanceTimeSeriesDataHelper instanceTimeSeriesDataHelper;

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
    log.info("Starting execution of instances purge job");
    Stopwatch sw = Stopwatch.createStarted();
    purgeOldDeletedInstances();
    log.info("Execution of instances purge job completed. Time taken: {} millis", sw.elapsed(TimeUnit.MILLISECONDS));
  }

  private void purgeOldDeletedInstances() {
    log.info("Starting purge of instances");
    Stopwatch sw = Stopwatch.createStarted();
    try {
      boolean purged =
          instanceService.purgeDeletedUpTo(getRetentionStartTime(MONTHS_TO_RETAIN_INSTANCES_EXCLUDING_CURRENT_MONTH));
      if (purged) {
        log.info("Purge of instances completed successfully. Time taken: {} millis", sw.elapsed(TimeUnit.MILLISECONDS));
      } else {
        log.info("Purge of instances failed. Time taken: {} millis", sw.elapsed(TimeUnit.MILLISECONDS));
      }
    } catch (Exception e) {
      log.error("Purge of instances failed. Time spent: {} millis", sw.elapsed(TimeUnit.MILLISECONDS));
    }
  }

  public Instant getRetentionStartTime(int monthsToSubtract) {
    LocalDate firstDayOfMonthOfRetention =
        LocalDate.now(ZoneOffset.UTC).minusMonths(monthsToSubtract).with(TemporalAdjusters.firstDayOfMonth());

    return firstDayOfMonthOfRetention.atStartOfDay().toInstant(ZoneOffset.UTC);
  }
}
