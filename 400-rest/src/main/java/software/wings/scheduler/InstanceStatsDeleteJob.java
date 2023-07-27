/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.scheduler;

import static software.wings.beans.AccountStatus.EXPIRED;

import io.harness.beans.FeatureName;
import io.harness.dataretention.LongerDataRetentionService;
import io.harness.event.timeseries.processor.EventProcessor;
import io.harness.exception.InstanceDeletionException;
import io.harness.ff.FeatureFlagService;
import io.harness.lock.AcquiredLock;
import io.harness.lock.PersistentLocker;
import io.harness.scheduler.PersistentScheduler;
import io.harness.timescaledb.TimeScaleDBService;

import software.wings.beans.Account;
import software.wings.beans.LicenseInfo;
import software.wings.beans.datatretention.LongerDataRetentionState;
import software.wings.service.intfc.AccountService;

import com.google.inject.Inject;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.Calendar;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.quartz.CronScheduleBuilder;
import org.quartz.CronTrigger;
import org.quartz.DateBuilder;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.TriggerBuilder;

@DisallowConcurrentExecution
@Slf4j
public class InstanceStatsDeleteJob implements Job {
  private static final SecureRandom random = new SecureRandom();
  public static final String GROUP = "INSTANCE_STATS_DELETE_CRON_GROUP";
  public static final long TWO_MONTH_IN_MILLIS = 5184000000L;
  public static final long ONE_DAY_IN_MILLIS = 86400000L;
  public static final String ACCOUNT_ID_KEY = "accountId";

  static final int MAX_RETRY = 3;

  public static final String DELETE_INSTANCE_DATA_POINTS =
      "DELETE FROM INSTANCE_STATS WHERE ACCOUNTID = ? AND REPORTEDAT>= ? AND REPORTEDAT< ?";
  public static final String GET_FIRST_REPORTEDAT_INSTANCE_STAT_DATE =
      "SELECT * FROM INSTANCE_STATS WHERE ACCOUNTID = ? ORDER BY REPORTEDAT ASC LIMIT 1";
  long intervalEndTimestamp;
  Timestamp oldestInstanceStats;
  // instance data migration cron
  private static final long DATA_DELETION_CRON_LOCK_EXPIRY_IN_SECONDS = 1800; // 60 * 30
  private static final String DATA_DELETION_CRON_LOCK_PREFIX = "INSTANCE_DATA_DELETION_CRON:";
  @Inject private TimeScaleDBService timeScaleDBService;

  @Inject LongerDataRetentionService longerDataRetentionService;
  @Inject private PersistentLocker persistentLocker;

  @Inject private AccountService accountService;
  @Inject private FeatureFlagService featureFlagService;

  @Override
  public void execute(JobExecutionContext jobExecutionContext) {
    String accountId = (String) jobExecutionContext.getJobDetail().getJobDataMap().get(ACCOUNT_ID_KEY);
    if (accountId == null) {
      log.debug("Skipping instance stats deletion job since the account id is null");
      return;
    }
    intervalEndTimestamp = System.currentTimeMillis();
    Account account = accountService.get(accountId);

    if (!shouldDeleteInstanceStatsData(account.getLicenseInfo())) {
      log.info("Skipping instance stats deletion since the account is not expired, accountId: {}", accountId);
      return;
    }

    intervalEndTimestamp = account.getLicenseInfo().getExpiryTime() + TWO_MONTH_IN_MILLIS + ONE_DAY_IN_MILLIS;
    try (
        AcquiredLock lock = persistentLocker.tryToAcquireLock(Account.class, DATA_DELETION_CRON_LOCK_PREFIX + accountId,
            Duration.ofSeconds(DATA_DELETION_CRON_LOCK_EXPIRY_IN_SECONDS))) {
      if (lock == null) {
        log.error("Unable to fetch lock for running instance data deletion for account : {}", accountId);
        return;
      }
      if (featureFlagService.isEnabled(FeatureName.CUSTOM_DASHBOARD_ENABLE_CRON_INSTANCE_DATA_MIGRATION, accountId)
          && !longerDataRetentionService.isLongerDataRetentionCompleted(
              LongerDataRetentionState.INSTANCE_LONGER_RETENTION, accountId)) {
        log.info("Triggering instance data deletion cron job for account : {}", accountId);
        try {
          processBatchQueries(accountId);
        } catch (Exception ex) {
          log.error("Failed to do instance data deletion for account id : {}", accountId, ex);
        }
      }
    }
  }

  private boolean shouldDeleteInstanceStatsData(LicenseInfo licenseInfo) {
    if (licenseInfo == null) {
      return false;
    }
    return EXPIRED.equals(licenseInfo.getAccountStatus())
        && System.currentTimeMillis() > (licenseInfo.getExpiryTime() + TWO_MONTH_IN_MILLIS);
  }

  private void processBatchQueries(String accountId) throws SQLException {
    int retryCount = 0;

    try (Connection dbConnection = timeScaleDBService.getDBConnection();
         PreparedStatement fetchOldestInstanceStatsRecordStatement =
             dbConnection.prepareStatement(InstanceStatsDeleteJob.GET_FIRST_REPORTEDAT_INSTANCE_STAT_DATE)) {
      fetchOldestInstanceStatsRecordStatement.setString(1, accountId);
      ResultSet resultSet = fetchOldestInstanceStatsRecordStatement.executeQuery();
      if (resultSet.next()) {
        oldestInstanceStats = resultSet.getTimestamp(EventProcessor.REPORTEDAT);
      }
    }

    if (oldestInstanceStats == null) {
      log.info("No instance stats available for account : {}", accountId);
      return;
    }

    Timestamp currentTime = new Timestamp(intervalEndTimestamp);
    Timestamp currentBatchTime = oldestInstanceStats;
    Timestamp nextBatchTime = generateNextBatchTime(currentBatchTime);

    if (currentBatchTime.after(currentTime)) {
      currentTime = new Timestamp(System.currentTimeMillis());
    }

    while (currentBatchTime.before(currentTime)) {
      try (Connection dbConnection = timeScaleDBService.getDBConnection()) {
        PreparedStatement deleteStatement = dbConnection.prepareStatement(DELETE_INSTANCE_DATA_POINTS);
        deleteStatement.setString(1, accountId);
        deleteStatement.setTimestamp(2, currentBatchTime);
        deleteStatement.setTimestamp(3, nextBatchTime);
        deleteStatement.execute();
        currentBatchTime = nextBatchTime;
        nextBatchTime = generateNextBatchTime(currentBatchTime);
        retryCount = 0;
      } catch (SQLException exception) {
        retryCount++;
        if (retryCount >= MAX_RETRY) {
          String errorLog = "MAX RETRY FAILURE : Failed to delete instance data points within interval";
          throw new InstanceDeletionException(errorLog, exception);
        }
        log.error(
            String.format("Failed to delete instanceStats data for expired accountId : [%s], retry : [%d], error: [%s]",
                accountId, retryCount, exception.toString()));
      }
    }
  }

  private Timestamp generateNextBatchTime(Timestamp currentBatchTime) {
    Calendar calendar = Calendar.getInstance();
    calendar.setTimeInMillis(currentBatchTime.getTime());
    calendar.add(Calendar.HOUR, 1);
    return new Timestamp(calendar.getTimeInMillis());
  }

  private static TriggerBuilder<CronTrigger> instanceStatsTriggerBuilder(String accountId) {
    return TriggerBuilder.newTrigger()
        .withIdentity(accountId, GROUP)
        .withSchedule(
            CronScheduleBuilder.atHourAndMinuteOnGivenDaysOfWeek(12, 0, DateBuilder.SATURDAY, DateBuilder.SUNDAY));
  }

  public static void addWithDelay(PersistentScheduler jobScheduler, String accountId) {
    // Add some randomness in the trigger start time to avoid overloading quartz by firing jobs at the same time.
    long startTime = System.currentTimeMillis() + random.nextInt((int) TimeUnit.MINUTES.toMillis(30));
    addInternal(jobScheduler, accountId, new Date(startTime));
  }

  public static void add(PersistentScheduler jobScheduler, String accountId) {
    addInternal(jobScheduler, accountId, null);
  }

  private static void addInternal(PersistentScheduler jobScheduler, String accountId, Date triggerStartTime) {
    JobDetail job = JobBuilder.newJob(InstanceStatsDeleteJob.class)
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
}