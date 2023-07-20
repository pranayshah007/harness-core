/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.scheduler;

import static io.harness.event.timeseries.processor.EventProcessor.MAX_RETRY_COUNT;

import static software.wings.beans.AccountStatus.EXPIRED;

import io.harness.beans.FeatureName;
import io.harness.dataretention.LongerDataRetentionService;
import io.harness.ff.FeatureFlagService;
import io.harness.lock.AcquiredLock;
import io.harness.lock.PersistentLocker;
import io.harness.scheduler.PersistentScheduler;
import io.harness.timescaledb.TimeScaleDBService;

import software.wings.beans.Account;
import software.wings.beans.LicenseInfo;
import software.wings.beans.datatretention.LongerDataRetentionState;
import software.wings.service.intfc.AccountService;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import java.security.SecureRandom;
import java.sql.*;
import java.time.Duration;
import java.util.Date;
import java.util.concurrent.TimeUnit;
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
public class InstanceStatsDeleteJob implements Job {
  private static final SecureRandom random = new SecureRandom();
  public static final String GROUP = "INSTANCE_STATS_DELETE_CRON_GROUP";
  public static final long TWO_MONTH_IN_MILLIS = 5184000000L;
  public static final String ACCOUNT_ID_KEY = "accountId";

  // 10 minutes
  private static final int SYNC_INTERVAL = 10;

  public static final String DELETE_INSTANCE_DATA_POINTS =
      "DELETE FROM INSTANCE_STATS WHERE ACCOUNTID = ? AND REPORTEDAT>= ? AND REPORTEDAT< ?";
  public static final String GET_FIRST_REPORTEDAT_INSTANCE_STAT_DATE =
      "SELECT COUNT(*) FROM INSTANCE_STATS WHERE ACCOUNTID = ? REPORTEDAT LIMIT 1";
  public static final String GET_FIRST_REPORTEDAT_INSTANCE_STAT_DATE =
      "DELETE FROM INSTANCE_STATS WHERE ACCOUNTID = ? ORDER BY REPORTEDAT LIMIT 1";
  public static final String GET_FIRST_REPORTEDAT_INSTANCE_STAT_DATE =
      "DELETE FROM INSTANCE_STATS WHERE ACCOUNTID = ? ORDER BY REPORTEDAT LIMIT 1";

  // instance data migration cron
  private static final long DATA_DELETION_CRON_LOCK_EXPIRY_IN_SECONDS = 660; // 60 * 11
  private static final String DATA_DELETION_CRON_LOCK_PREFIX = "INSTANCE_DATA_DELETION_CRON:";
  @Inject private TimeScaleDBService timeScaleDBService;

  @Inject LongerDataRetentionService longerDataRetentionService;
  @Inject private PersistentLocker persistentLocker;

  @Inject private AccountService accountService;
  @Inject private FeatureFlagService featureFlagService;

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

  @Override
  public void execute(JobExecutionContext jobExecutionContext) {
    String accountId = (String) jobExecutionContext.getJobDetail().getJobDataMap().get(ACCOUNT_ID_KEY);
    if (accountId == null) {
      log.debug("Skipping instance stats deletion job since the account id is null");
      return;
    }

    Account account = accountService.get(accountId);

    if (!shouldDeleteInstanceStatsData(account.getLicenseInfo())) {
      log.info(
          "Skipping instance stats deletion since the account is not active / not found, accountId: {}", accountId);
      return;
    }

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
          boolean successful = deleteInstanceStatsDataPointsFromTsDb(accountId);
          if (!successful) {
            log.error("Unable to carry out instanceStats Delete step");
          }
        } catch (Exception exception) {
          log.error("Failed to do instance data migration for account id : {}", accountId, exception);
        }
      }
    }
  }

  private boolean shouldDeleteInstanceStatsData(LicenseInfo licenseInfo) {
    if (EXPIRED.equals(licenseInfo.getAccountStatus())
        && System.currentTimeMillis() > (licenseInfo.getExpiryTime() + TWO_MONTH_IN_MILLIS)) {
      return true;
    }
    return false;
  }

  @VisibleForTesting
  boolean deleteInstanceStatsDataPointsFromTsDb(String accountId) throws Exception {
    boolean successfulUpsert = false;
    int retryCount = 0, QUERY_BATCH_SIZE = 10;

    while (!successfulUpsert && retryCount < MAX_RETRY_COUNT) {
      try {
        boolean dataLeftToDelete = processBatchQueries(accountId, QUERY_BATCH_SIZE);
        successfulUpsert = true;
        if (!dataLeftToDelete) {
          return true;
        }
      } catch (SQLException e) {
        if (retryCount >= MAX_RETRY_COUNT) {
          String errorLog =
              String.format("MAX RETRY FAILURE : Failed to save instance data , error : [%s]", e.toString());
          throw new Exception(errorLog);
        } else {
          log.error(
              "Failed to save instance data : [{}] , retryCount : [{}] , error : [{}]", retryCount, e.toString(), e);
        }
        retryCount++;
      }
    }
    return false;
  }

  private boolean processBatchQueries(String accountId, Integer batchSize) throws SQLException {
    try (Connection dbConnection = timeScaleDBService.getDBConnection()) {
      PreparedStatement deleteStatement = dbConnection.prepareStatement(DELETE_INSTANCE_DATA_POINTS);
      deleteStatement.setString(1, accountId);
      deleteStatement.setInt(2, batchSize);
      ResultSet resultSet = deleteStatement.executeQuery();
      return true;
    } catch (SQLException e) {
      log.error("Failed to delete instanceStats data for expired accountId : %s", accountId);
    }
    return false;
  }
}
