/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.scheduler;

import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;

import io.harness.beans.FeatureName;
import io.harness.dataretention.LongerDataRetentionService;
import io.harness.event.timeseries.processor.instanceeventprocessor.instancereconservice.IInstanceReconService;
import io.harness.ff.FeatureFlagService;
import io.harness.lock.AcquiredLock;
import io.harness.lock.PersistentLocker;
import io.harness.logging.AccountLogContext;
import io.harness.logging.AutoLogContext;
import io.harness.scheduler.PersistentScheduler;

import software.wings.beans.Account;
import software.wings.beans.LicenseInfo;
import software.wings.beans.account.AccountStatus;
import software.wings.beans.datatretention.LongerDataRetentionState;
import software.wings.beans.instance.dashboard.InstanceStatsUtils;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.instance.licensing.InstanceUsageLimitExcessHandler;
import software.wings.service.intfc.instance.stats.InstanceStatService;
import software.wings.service.intfc.instance.stats.collector.StatsCollector;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Stopwatch;
import com.google.inject.Inject;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Date;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.SimpleTrigger;
import org.quartz.TriggerBuilder;

@DisallowConcurrentExecution
@Slf4j
public class InstanceStatsCollectorJob implements Job {
  private static final SecureRandom random = new SecureRandom();
  public static final String GROUP = "INSTANCE_STATS_COLLECT_CRON_GROUP";
  public static final long TWO_MONTH_IN_MILLIS = 5184000000L;
  public static final String ACCOUNT_ID_KEY = "accountId";

  // 10 minutes
  private static final int SYNC_INTERVAL = 10;
  private static final int DATA_MIGRATION_INTERVAL_IN_HOURS = 24;

  private static final int ACQUIRE_LOCK_TIME_MINUTES = 90;
  private static final int ACQUIRE_LOCK_WAIT_TIMEOUT_MINUTES = 10;

  // instance data migration cron
  private static final long DATA_MIGRATION_CRON_LOCK_EXPIRY_IN_SECONDS = 660; // 60 * 11
  private static final String DATA_MIGRATION_CRON_LOCK_PREFIX = "INSTANCE_DATA_MIGRATION_CRON:";

  @Inject private ExecutorService executorService;
  @Inject private PersistentLocker persistentLocker;
  @Inject private StatsCollector statsCollector;
  @Inject private InstanceUsageLimitExcessHandler instanceLimitHandler;
  @Inject private InstanceStatService instanceStatService;
  @Inject private AccountService accountService;
  @Inject private IInstanceReconService instanceReconService;
  @Inject private FeatureFlagService featureFlagService;
  @Inject LongerDataRetentionService longerDataRetentionService;

  private static TriggerBuilder<SimpleTrigger> instanceStatsTriggerBuilder(String accountId) {
    return TriggerBuilder.newTrigger()
        .withIdentity(accountId, GROUP)
        .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                          .withIntervalInMinutes(SYNC_INTERVAL)
                          .repeatForever()
                          .withMisfireHandlingInstructionNowWithExistingCount());
  }

  public TriggerBuilder<SimpleTrigger> getInstanceStatsTriggerBuilder(String accountId) {
    return instanceStatsTriggerBuilder(accountId);
  }

  public static void addWithDelay(PersistentScheduler jobScheduler, String accountId) {
    // Add some randomness in the trigger start time to avoid overloading quartz by firing jobs at the same time.
    long startTime = System.currentTimeMillis() + random.nextInt((int) TimeUnit.MINUTES.toMillis(SYNC_INTERVAL));
    addInternal(jobScheduler, accountId, new Date(startTime));
  }

  public static void add(PersistentScheduler jobScheduler, String accountId) {
    addInternal(jobScheduler, accountId, null);
  }

  private static void addInternal(PersistentScheduler jobScheduler, String accountId, Date triggerStartTime) {
    JobDetail job = JobBuilder.newJob(InstanceStatsCollectorJob.class)
                        .withIdentity(accountId, GROUP)
                        .usingJobData(ACCOUNT_ID_KEY, accountId)
                        .build();

    TriggerBuilder triggerBuilder = instanceStatsTriggerBuilder(accountId);
    if (triggerStartTime != null) {
      triggerBuilder.startAt(triggerStartTime);
    }

    jobScheduler.ensureJob__UnderConstruction(job, triggerBuilder.build());
  }

  public static void delete(PersistentScheduler jobScheduler, String accountId) {
    jobScheduler.deleteJob(accountId, GROUP);
  }

  @Override
  public void execute(JobExecutionContext jobExecutionContext) {
    String accountId = (String) jobExecutionContext.getJobDetail().getJobDataMap().get(ACCOUNT_ID_KEY);
    if (accountId == null) {
      log.debug("Skipping instance stats collector job since the account id is null");
      return;
    }

    try (AutoLogContext ignore = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
      Account account = accountService.get(accountId);
      if (account == null || account.getLicenseInfo() == null || account.getLicenseInfo().getAccountStatus() == null
          || shouldSkipStatsCollection(account.getLicenseInfo(), account.getUuid())) {
        log.info("Skipping instance stats since the account is not active / not found, accountId: {}", accountId);
      } else {
        log.info("Running instance stats collector job");
        executorService.submit(() -> {
          Objects.requireNonNull(accountId, "Account Id must be passed in job context");
          createStats(accountId);
          double ninety_five_percentile_usage = InstanceStatsUtils.actualUsage(accountId, instanceStatService);
          instanceLimitHandler.handle(accountId, ninety_five_percentile_usage);
        });
      }
    }

    try (AcquiredLock lock =
             persistentLocker.tryToAcquireLock(Account.class, DATA_MIGRATION_CRON_LOCK_PREFIX + accountId,
                 Duration.ofSeconds(DATA_MIGRATION_CRON_LOCK_EXPIRY_IN_SECONDS))) {
      if (lock == null) {
        log.error("Unable to fetch lock for running instance data migration for account : {}", accountId);
        return;
      }
      if (featureFlagService.isEnabled(FeatureName.CUSTOM_DASHBOARD_ENABLE_CRON_INSTANCE_DATA_MIGRATION, accountId)
          && !longerDataRetentionService.isLongerDataRetentionCompleted(
              LongerDataRetentionState.INSTANCE_LONGER_RETENTION, accountId)) {
        log.info("Triggering instance data migration cron for account : {}", accountId);
        try {
          instanceReconService.doDataMigration(accountId, DATA_MIGRATION_INTERVAL_IN_HOURS);
        } catch (Exception exception) {
          log.error("Failed to do instance data migration for account id : {}", accountId, exception);
        }
      }
    }
  }

  private boolean shouldSkipStatsCollection(LicenseInfo licenseInfo, String accountId) {
    if (featureFlagService.isEnabled(FeatureName.DISABLE_INSTANCE_STATS_JOB_CG, accountId)) {
      return true;
    } else if (AccountStatus.ACTIVE.equals(licenseInfo.getAccountStatus())) {
      return false;
    } else if (AccountStatus.DELETED.equals(licenseInfo.getAccountStatus())
        || AccountStatus.INACTIVE.equals(licenseInfo.getAccountStatus())
        || AccountStatus.MARKED_FOR_DELETION.equals(licenseInfo.getAccountStatus())) {
      return true;
    } else if (AccountStatus.EXPIRED.equals(licenseInfo.getAccountStatus())
        && System.currentTimeMillis() > (licenseInfo.getExpiryTime() + TWO_MONTH_IN_MILLIS)) {
      return true;
    }
    return false;
  }

  @VisibleForTesting
  void createStats(@Nonnull final String accountId) {
    Objects.requireNonNull(accountId, "Account Id must be present");

    /**
     * Redis lock is automatically released based on lease-time even if the current job is still in progress.
     * To prevent redis lock to be released before job is completed lock time was increased to
     * {@value ACQUIRE_LOCK_TIME_MINUTES}. In most cases lock should be released in short period of time, only in case
     * if a huge account has to catch up multiple data points we do require a long-running lock to prevent other jobs
     * from the same account to acquire lock and run in parallel with unfinished jobs. Setting wait timeout
     * {@value ACQUIRE_LOCK_WAIT_TIMEOUT_MINUTES} to not queue multiple jobs. It is expected if the app crashes then
     * it will still be able to continue after lock timeout and should be able to catch up with missing entries
     * One jobs shouldn't wait more than {@value ACQUIRE_LOCK_WAIT_TIMEOUT_MINUTES} otherwise we will end up in
     * queueing multiple blocked threads for the same account.
     **/
    try (AcquiredLock lock = persistentLocker.waitToAcquireLock(Account.class, accountId,
             Duration.ofMinutes(ACQUIRE_LOCK_TIME_MINUTES), Duration.ofMinutes(ACQUIRE_LOCK_WAIT_TIMEOUT_MINUTES))) {
      if (lock == null) {
        log.warn("Unable to acquire lock for account {}", accountId);
        return;
      }

      Stopwatch sw = Stopwatch.createStarted();
      boolean ranAtLeastOnce = statsCollector.createStats(accountId);
      if (ranAtLeastOnce) {
        log.info("Successfully saved instance history stats. Account Id: {}. Time taken: {} millis", accountId,
            sw.elapsed(TimeUnit.MILLISECONDS));
      } else {
        log.info("No instance history stats were saved. Account Id: {}. Time taken: {} millis", accountId,
            sw.elapsed(TimeUnit.MILLISECONDS));
      }
      sw = Stopwatch.createStarted();
      ranAtLeastOnce = statsCollector.createServerlessStats(accountId);
      if (ranAtLeastOnce) {
        log.info("Successfully saved Serverless instance history stats. Account Id: {}. Time taken: {} millis",
            accountId, sw.elapsed(TimeUnit.MILLISECONDS));
      } else {
        log.info("No Serverless instance history stats were saved. Account Id: {}. Time taken: {} millis", accountId,
            sw.elapsed(TimeUnit.MILLISECONDS));
      }
    }
  }
}
