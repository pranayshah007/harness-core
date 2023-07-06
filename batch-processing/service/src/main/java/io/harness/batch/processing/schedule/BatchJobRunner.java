/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.schedule;

import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.batch.processing.ccm.BatchJobType;
import io.harness.batch.processing.ccm.CCMJobConstants;
import io.harness.batch.processing.config.BatchMainConfig;
import io.harness.batch.processing.service.impl.BatchJobTimeLogContext;
import io.harness.batch.processing.service.intfc.BatchJobIntervalService;
import io.harness.batch.processing.service.intfc.BatchJobScheduledDataService;
import io.harness.batch.processing.service.intfc.CustomBillingMetaDataService;
import io.harness.ccm.commons.entities.batch.BatchJobInterval;
import io.harness.ccm.commons.entities.batch.BatchJobScheduledData;
import io.harness.logging.AutoLogContext;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.common.collect.ImmutableSet;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.JobParametersInvalidException;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.repository.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.repository.JobRestartException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@OwnedBy(HarnessTeam.CE)
public class BatchJobRunner {
  @Autowired private JobLauncher jobLauncher;
  @Autowired private BatchJobIntervalService batchJobIntervalService;
  @Autowired private BatchJobScheduledDataService batchJobScheduledDataService;
  @Autowired private CustomBillingMetaDataService customBillingMetaDataService;
  @Autowired private BatchMainConfig batchMainConfig;

  private Cache<CacheKey, Boolean> logErrorCache = Caffeine.newBuilder().expireAfterWrite(24, TimeUnit.HOURS).build();
  public static final int TOTAL_INSTANCE_BILLING_HOURLY_JOBS_PER_MINUTE = 5;
  @Value
  private static class CacheKey {
    private String accountId;
    private BatchJobType batchJobType;
    private Instant startInstant;
  }

  /**
   * Runs the batch job from previous end time and save the job logs
   * @param job - Job
   * @throws Exception
   */
  public void runJob(String accountId, Job job, boolean runningMode)
      throws JobParametersInvalidException, JobExecutionAlreadyRunningException, JobRestartException,
             JobInstanceAlreadyCompleteException {
    BatchJobType batchJobType = BatchJobType.fromJob(job);
    // Disable some jobs based on the flag. This can be removed later on
    if (batchJobType == BatchJobType.SYNC_BILLING_REPORT_AZURE
        && batchMainConfig.getAzureStorageSyncConfig().isSyncJobDisabled()) {
      log.warn("Azure sync job is disabled in this environment");
      return;
    }
    long duration = batchJobType.getInterval();
    ChronoUnit chronoUnit = batchJobType.getIntervalUnit();
    BatchJobInterval batchJobInterval = batchJobIntervalService.fetchBatchJobInterval(accountId, batchJobType);
    if (null != batchJobInterval) {
      chronoUnit = batchJobInterval.getIntervalUnit();
      duration = batchJobInterval.getInterval();
    }
    List<BatchJobType> dependentBatchJobs = batchJobType.getDependentBatchJobs();
    Instant startAt = batchJobScheduledDataService.fetchLastBatchJobScheduledTime(accountId, batchJobType);
    if (null == startAt) {
      log.info("Event not received for account: {}, Job: {} ", accountId, batchJobType.name());
      return;
    }
    Instant endAt = Instant.now().minus(1, ChronoUnit.HOURS);
    if (batchJobType == BatchJobType.ANOMALY_DETECTION_CLOUD) {
      endAt = Instant.now().minus(7, ChronoUnit.HOURS);
    }
    if (batchJobType == BatchJobType.RERUN_JOB) {
      endAt = Instant.now().minus(15, ChronoUnit.HOURS);
    }
    if (batchJobType == BatchJobType.DELEGATE_HEALTH_CHECK || batchJobType == BatchJobType.RECOMMENDATION_JIRA_STATUS
        || batchJobType == BatchJobType.MSP_MARKUP_AMOUNT) {
      endAt = Instant.now();
    }
    BatchJobScheduleTimeProvider batchJobScheduleTimeProvider =
        new BatchJobScheduleTimeProvider(startAt, endAt, duration, chronoUnit);
    Instant startInstant = startAt;
    Instant jobsStartTime = Instant.now();

    int clusterDataHourlyCounter = 0;
    Instant clusterDataHourlyLastRunTime = Instant.now();

    while (batchJobScheduleTimeProvider.hasNext()) {
      Instant endInstant = batchJobScheduleTimeProvider.next();
      if (batchJobType == BatchJobType.INSTANCE_BILLING_HOURLY) {
        Instant currentTime = Instant.now();
        if (clusterDataHourlyCounter < TOTAL_INSTANCE_BILLING_HOURLY_JOBS_PER_MINUTE
            && currentTime.isAfter(clusterDataHourlyLastRunTime.plus(1, ChronoUnit.MINUTES))) {
          clusterDataHourlyLastRunTime = currentTime;
          clusterDataHourlyCounter = 0;
          log.info("Job: currentTime: {}, updating counter to ZERO and clusterDataHourlyLastRunTime: {}", currentTime,
              clusterDataHourlyLastRunTime);
        }
        if (clusterDataHourlyCounter >= TOTAL_INSTANCE_BILLING_HOURLY_JOBS_PER_MINUTE) {
          log.info("Job: INSTANCE_BILLING_HOURLY has already run {} times in a minute. batchJobType: {}",
              TOTAL_INSTANCE_BILLING_HOURLY_JOBS_PER_MINUTE, batchJobType.name());
          return;
        }
        clusterDataHourlyCounter++;
      }
      if (null != endInstant && checkDependentJobFinished(accountId, endInstant, dependentBatchJobs)
          && checkOutOfClusterDependentJobs(accountId, startInstant, endInstant, batchJobType)
          && checkClusterToBigQueryJobCompleted(accountId, batchJobType)) {
        if (runningMode) {
          JobParameters params =
              new JobParametersBuilder()
                  .addString(CCMJobConstants.JOB_ID, String.valueOf(System.currentTimeMillis()))
                  .addString(CCMJobConstants.ACCOUNT_ID, accountId)
                  .addString(CCMJobConstants.JOB_START_DATE, String.valueOf(startInstant.toEpochMilli()))
                  .addString(CCMJobConstants.JOB_END_DATE, String.valueOf(endInstant.toEpochMilli()))
                  .addString(CCMJobConstants.BATCH_JOB_TYPE, batchJobType.name())
                  .toJobParameters();
          try (AutoLogContext ignore =
                   new BatchJobTimeLogContext(String.valueOf(startInstant.toEpochMilli()), OVERRIDE_ERROR)) {
            Instant jobStartTime = Instant.now();
            JobExecution jobExecution = jobLauncher.run(job, params);
            BatchStatus status = jobExecution.getStatus();
            log.info("Job status {}", status);
            long instanceId =
                jobExecution.getJobInstance() != null ? jobExecution.getJobInstance().getInstanceId() : 0l;
            Instant jobStopTime = Instant.now();

            if (status == BatchStatus.COMPLETED) {
              BatchJobScheduledData batchJobScheduledData = new BatchJobScheduledData(accountId, batchJobType.name(),
                  Duration.between(jobStartTime, jobStopTime).toMillis(), startInstant, endInstant, instanceId);
              batchJobScheduledDataService.create(batchJobScheduledData);
              startInstant = endInstant;
            } else {
              logJobErrors(accountId, batchJobType, status, startInstant, endInstant);
              break;
            }
          }
        } else {
          logDelayedJobs(accountId, batchJobType, startInstant, endInstant);
          break;
        }
      } else {
        break;
      }

      long totalTimeTaken = Duration.between(jobsStartTime, Instant.now()).toMinutes();
      logJobRunTimeStats(accountId, batchJobType, totalTimeTaken);
      if (totalTimeTaken >= 30) {
        log.warn("Job was taking more time so terminated next runs {}", totalTimeTaken);
        break;
      }
    }
  }

  private void logJobRunTimeStats(String accountId, BatchJobType batchJobType, long totalTimeTaken) {
    if (batchJobType.getIntervalUnit() == ChronoUnit.HOURS && totalTimeTaken > 60) {
      log.error("Hourly Time taken for job is more {} {} {}", accountId, batchJobType, totalTimeTaken);
    } else if (batchJobType.getIntervalUnit() == ChronoUnit.DAYS && totalTimeTaken > 150) {
      log.error("Daily Time taken for job is more {} {} {}", accountId, batchJobType, totalTimeTaken);
    }
  }

  private void logJobErrors(
      String accountId, BatchJobType batchJobType, BatchStatus status, Instant startInstant, Instant endInstant) {
    CacheKey cacheKey = new CacheKey(accountId, batchJobType, startInstant);
    if (ImmutableSet
            .of(BatchJobType.SYNC_BILLING_REPORT_S3, BatchJobType.SYNC_BILLING_REPORT_AZURE,
                BatchJobType.SYNC_BILLING_REPORT_GCP)
            .contains(batchJobType)) {
      log.error("Exception running job for account {} type {} status {} time range {} - {}", accountId, batchJobType,
          status, startInstant, endInstant);
    } else {
      if (logErrorCache.getIfPresent(cacheKey) == null) {
        log.error("Error while running batch job for account {} type {} status {} time range {} - {}", accountId,
            batchJobType, status, startInstant, endInstant);
        logErrorCache.put(cacheKey, Boolean.TRUE);
      } else {
        log.error("Error in running batch job retry for account {} type {} status {} time range {} - {}", accountId,
            batchJobType, status, startInstant, endInstant);
      }
    }
  }

  private void logDelayedJobs(String accountId, BatchJobType batchJobType, Instant startInstant, Instant endInstant) {
    Instant currentTime = Instant.now();
    long diffMillis = currentTime.toEpochMilli() - endInstant.toEpochMilli();
    if (diffMillis > 43200000) {
      CacheKey cacheKey = new CacheKey(accountId, batchJobType, startInstant);
      if (logErrorCache.getIfPresent(cacheKey) == null) {
        log.error("Batch job is delayed for account {} {} {}", accountId, batchJobType, diffMillis);
      } else {
        log.error("Batch job delayed for the account {} {} {}", accountId, batchJobType, diffMillis);
      }
    }
  }

  boolean checkDependentJobFinished(String accountId, Instant endAt, List<BatchJobType> dependentBatchJobs) {
    for (BatchJobType dependentBatchJob : dependentBatchJobs) {
      Instant lastDependentJobEndAt =
          batchJobScheduledDataService.fetchLastDependentBatchJobScheduledTime(accountId, dependentBatchJob);
      if (lastDependentJobEndAt == null || lastDependentJobEndAt.isBefore(endAt)) {
        return false;
      }
    }
    return true;
  }

  boolean checkClusterToBigQueryJobCompleted(String accountId, BatchJobType batchJobType) {
    if (batchJobType != BatchJobType.DATA_CHECK_BIGQUERY_TIMESCALE) {
      return true;
    }
    Instant instant = batchJobScheduledDataService.fetchLastDependentBatchJobCreatedTime(
        accountId, BatchJobType.CLUSTER_DATA_TO_BIG_QUERY);
    if (instant != null) {
      long timeDifference = Duration.between(instant, Instant.now()).toMinutes();
      if (timeDifference >= 30) {
        log.info("It has been greater than 30 mins since CLUSTER_DATA_TO_BIG_QUERY ran");
        return true;
      }
    }
    log.warn("CLUSTER_DATA_TO_BIG_QUERY hasn't ran yet");
    return false;
  }

  boolean checkOutOfClusterDependentJobs(String accountId, Instant startAt, Instant endAt, BatchJobType batchJobType) {
    if (ImmutableSet.of(BatchJobType.INSTANCE_BILLING, BatchJobType.INSTANCE_BILLING_HOURLY).contains(batchJobType)) {
      if (batchJobType == BatchJobType.INSTANCE_BILLING_HOURLY) {
        // adding 6 hrs buffer because sometime last hr data is not present for few instances and we consider cost as 0
        endAt = endAt.plus(6, ChronoUnit.HOURS);
      }
      return customBillingMetaDataService.checkPipelineJobFinished(accountId, startAt, endAt);
    }
    return true;
  }
}
