/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.auditevent.streaming;

import java.util.HashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
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
  @Autowired private JobLauncher jobLauncher;
  @Autowired @Qualifier("auditEventPublisherJob") private Job auditEventPublisherJob;
  @Scheduled(cron = "* */1 * * * *") // run every 1 min
  public void runEventCollectionBatchJob() {
    try {
      log.info("Starting Event collection batch job");
      jobLauncher.run(auditEventPublisherJob, new JobParameters(new HashMap<>()));
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
