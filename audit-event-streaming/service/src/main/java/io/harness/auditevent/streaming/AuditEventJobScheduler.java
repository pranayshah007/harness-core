/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.auditevent.streaming;

import static io.harness.auditevent.streaming.AuditEventStreamingConstants.ACCOUNT_IDENTIFIER_PARAMETER_KEY;
import static io.harness.auditevent.streaming.AuditEventStreamingConstants.AUDIT_EVENT_PUBLISHER_JOB;
import static io.harness.auditevent.streaming.AuditEventStreamingConstants.JOB_START_TIME_PARAMETER_KEY;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameter;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersInvalidException;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.repository.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.repository.JobRestartException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Slf4j
@Configuration
@EnableScheduling
public class AuditEventJobScheduler {
  private final JobLauncher jobLauncher;
  @Qualifier(AUDIT_EVENT_PUBLISHER_JOB) private final Job auditEventPublisherJob;

  @Autowired
  public AuditEventJobScheduler(JobLauncher jobLauncher, Job auditEventPublisherJob) {
    this.jobLauncher = jobLauncher;
    this.auditEventPublisherJob = auditEventPublisherJob;
  }

  @Scheduled(cron = "0 */10 * * * *") // run every 10 min
  public void runEventCollectionBatchJob() {
    // TODO: Replace with real logic of fetching account identifier based in stateful set logic
    List<String> accountIdentifiers = new ArrayList<>();
    accountIdentifiers.forEach(this::startJob);
  }

  private void startJob(String accountIdentifier) {
    try {
      log.info("Starting Event collection batch job");
      JobParameters jobParameters = new JobParameters(
          Map.ofEntries(Map.entry(ACCOUNT_IDENTIFIER_PARAMETER_KEY, new JobParameter(accountIdentifier)),
              Map.entry(JOB_START_TIME_PARAMETER_KEY, new JobParameter(System.currentTimeMillis()))));
      jobLauncher.run(auditEventPublisherJob, jobParameters);
    } catch (JobExecutionAlreadyRunningException e) {
      throw new RuntimeException(e);
    } catch (JobRestartException e) {
      throw new RuntimeException(e);
    } catch (JobInstanceAlreadyCompleteException e) {
      throw new RuntimeException(e);
    } catch (JobParametersInvalidException e) {
      throw new RuntimeException(e);
    }
  }
}
