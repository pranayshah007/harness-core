/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.schedule;

import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;

import io.harness.batch.processing.ccm.BatchJobType;
import io.harness.batch.processing.ccm.CCMJobConstants;
import io.harness.batch.processing.service.impl.BatchJobBucketLogContext;
import io.harness.batch.processing.service.intfc.BatchJobIntervalService;
import io.harness.batch.processing.service.intfc.BatchJobScheduledDataService;
import io.harness.batch.processing.service.intfc.CustomBillingMetaDataService;
import io.harness.ccm.commons.entities.batch.LatestClusterInfo;
import io.harness.ccm.health.LastReceivedPublishedMessageDao;
import io.harness.logging.AccountLogContext;
import io.harness.logging.AutoLogContext;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
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
public class RecentlyAddedAccountJobRunner {
  @Autowired private JobLauncher jobLauncher;
  @Autowired private List<Job> jobs;
  @Autowired private BatchJobIntervalService batchJobIntervalService;
  @Autowired private BatchJobScheduledDataService batchJobScheduledDataService;
  @Autowired private CustomBillingMetaDataService customBillingMetaDataService;
  @Autowired protected LastReceivedPublishedMessageDao lastReceivedPublishedMessageDao;

  public void runJobForRecentlyAddedAccounts() {
    List<LatestClusterInfo> latestClusterInfos = lastReceivedPublishedMessageDao.fetchLatestClusterInfo();
    for (LatestClusterInfo latestClusterInfo : latestClusterInfos) {
      try (AutoLogContext ignore = new AccountLogContext(latestClusterInfo.getAccountId(), OVERRIDE_ERROR);
           AutoLogContext ignore3 = new BatchJobBucketLogContext("RECENTLY_ADDED", OVERRIDE_ERROR)) {
        log.info("Running recently added account job for {}", latestClusterInfo);
        runJob(latestClusterInfo);
        log.info("Deleting now latest cluster info {}", latestClusterInfo);
        lastReceivedPublishedMessageDao.deleteLatestClusterInfo(latestClusterInfo);
      }
    }
  }

  private boolean runJob(String accountId, BatchJobType batchJobType, Job job, Instant jobStartTime, Instant jobEndTime)
      throws JobParametersInvalidException, JobExecutionAlreadyRunningException, JobRestartException,
             JobInstanceAlreadyCompleteException {
    JobParameters params = new JobParametersBuilder()
                               .addString(CCMJobConstants.JOB_ID, String.valueOf(System.currentTimeMillis()))
                               .addString(CCMJobConstants.ACCOUNT_ID, accountId)
                               .addString(CCMJobConstants.JOB_START_DATE, String.valueOf(jobStartTime.toEpochMilli()))
                               .addString(CCMJobConstants.JOB_END_DATE, String.valueOf(jobEndTime.toEpochMilli()))
                               .addString(CCMJobConstants.BATCH_JOB_TYPE, batchJobType.name())
                               .toJobParameters();

    BatchStatus status = jobLauncher.run(job, params).getStatus();
    if (status == BatchStatus.COMPLETED) {
      return true;
    }
    return false;
  }

  public void runJob(LatestClusterInfo latestClusterInfo) {
    try {
      Instant eventJobStartTime = Instant.ofEpochMilli(latestClusterInfo.getCreatedAt()).minus(10, ChronoUnit.MINUTES);
      Instant eventJobEndTime = Instant.ofEpochMilli(latestClusterInfo.getCreatedAt()).plus(10, ChronoUnit.MINUTES);

      Job eventJob = jobs.stream().filter(job -> BatchJobType.fromJob(job) == BatchJobType.K8S_EVENT).findFirst().get();
      boolean eventJobRun = runJob(
          latestClusterInfo.getAccountId(), BatchJobType.K8S_EVENT, eventJob, eventJobStartTime, eventJobEndTime);
      if (eventJobRun) {
        Instant hourlyBillingJobEndTime =
            Instant.ofEpochMilli(latestClusterInfo.getCreatedAt()).truncatedTo(ChronoUnit.HOURS);
        Instant hourlyBillingStartTime = hourlyBillingJobEndTime.minus(8, ChronoUnit.DAYS);
        runBillingJobs(
            latestClusterInfo, BatchJobType.INSTANCE_BILLING_HOURLY, hourlyBillingJobEndTime, hourlyBillingStartTime);
        runBillingJobs(latestClusterInfo, BatchJobType.ACTUAL_IDLE_COST_BILLING_HOURLY, hourlyBillingJobEndTime,
            hourlyBillingStartTime);
        runBillingJobs(latestClusterInfo, BatchJobType.INSTANCE_BILLING_HOURLY_AGGREGATION, hourlyBillingJobEndTime,
            hourlyBillingStartTime);
        runBillingJobs(latestClusterInfo, BatchJobType.CLUSTER_DATA_HOURLY_TO_BIG_QUERY, hourlyBillingJobEndTime,
            hourlyBillingStartTime);

        Instant dailyBillingJobEndTime =
            Instant.ofEpochMilli(latestClusterInfo.getCreatedAt()).truncatedTo(ChronoUnit.DAYS);
        Instant dailyBillingStartTime = hourlyBillingJobEndTime.minus(30, ChronoUnit.DAYS);
        runBillingJobs(latestClusterInfo, BatchJobType.INSTANCE_BILLING, dailyBillingJobEndTime, dailyBillingStartTime);
        runBillingJobs(
            latestClusterInfo, BatchJobType.ACTUAL_IDLE_COST_BILLING, dailyBillingJobEndTime, dailyBillingStartTime);
        runBillingJobs(latestClusterInfo, BatchJobType.INSTANCE_BILLING_AGGREGATION, dailyBillingJobEndTime,
            dailyBillingStartTime);
        runBillingJobs(
            latestClusterInfo, BatchJobType.CLUSTER_DATA_TO_BIG_QUERY, dailyBillingJobEndTime, dailyBillingStartTime);
      }
    } catch (Exception ex) {
      log.error("Exception while running job for recently added account", ex);
    }
  }

  private void runBillingJobs(LatestClusterInfo latestClusterInfo, BatchJobType batchJobType, Instant jobEndTime,
      Instant jobStartTime) throws JobParametersInvalidException, JobExecutionAlreadyRunningException,
                                   JobRestartException, JobInstanceAlreadyCompleteException {
    long duration = batchJobType.getInterval();
    ChronoUnit chronoUnit = batchJobType.getIntervalUnit();
    BatchJobScheduleTimeProviderDesc batchJobScheduleTimeProviderDesc =
        new BatchJobScheduleTimeProviderDesc(jobEndTime, jobStartTime, duration, chronoUnit);
    Job billingHourlyJob = jobs.stream().filter(job -> BatchJobType.fromJob(job) == batchJobType).findFirst().get();

    while (batchJobScheduleTimeProviderDesc.hasNext()) {
      Instant startInstant = batchJobScheduleTimeProviderDesc.next();
      if (null != startInstant) {
        runJob(latestClusterInfo.getAccountId(), batchJobType, billingHourlyJob, startInstant, jobEndTime);
        jobEndTime = startInstant;
      }
    }
  }
}
