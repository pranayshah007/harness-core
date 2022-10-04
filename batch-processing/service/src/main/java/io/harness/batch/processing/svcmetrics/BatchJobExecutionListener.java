/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.svcmetrics;

import io.harness.batch.processing.ccm.CCMJobConstants;
import io.harness.metrics.service.api.MetricService;

import java.util.Date;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.JobParameters;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class BatchJobExecutionListener implements JobExecutionListener {
  private final MetricService metricService;

  @Autowired
  public BatchJobExecutionListener(MetricService metricService) {
    this.metricService = metricService;
  }

  @Override
  public void beforeJob(JobExecution jobExecution) {}

  @Override
  public void afterJob(JobExecution jobExecution) {
    JobParameters params = jobExecution.getJobParameters();
    String accountId = params.getString(CCMJobConstants.ACCOUNT_ID);
    String jobType = jobExecution.getJobInstance().getJobName();

    Date startTime = jobExecution.getStartTime();
    Date endTime = jobExecution.getEndTime();
    long durationInMillis = endTime.getTime() - startTime.getTime();
    long durationInSeconds = durationInMillis / 1000;

    log.info("Job execution completed in {} sec: accountId={} jobType={}", durationInSeconds, accountId, jobType);
    try (BatchJobContext x = new BatchJobContext(accountId, jobType)) {
      metricService.recordMetric(BatchProcessingMetricName.JOB_EXECUTION_TIME_IN_SEC, durationInSeconds);
    }
  }
}
