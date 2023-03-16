/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.config;

import io.harness.batch.processing.ccm.BatchJobType;
import io.harness.batch.processing.svcmetrics.BatchJobExecutionListener;
import io.harness.batch.processing.tasklet.S3ToClickHouseSyncTasklet;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory;
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class S3ToClickHouseSyncJobConfig {
  @Autowired private JobBuilderFactory jobBuilderFactory;
  @Autowired private StepBuilderFactory stepBuilderFactory;
  @Autowired private BatchJobExecutionListener batchJobExecutionListener;

  @Bean
  @Autowired
  @Qualifier(value = "s3ToClickHouseSyncJob")
  public Job s3ToClickHouseSyncJob(JobBuilderFactory jobBuilderFactory, Step s3ToClickHouseSyncStep) {
    return jobBuilderFactory.get(BatchJobType.SYNC_BILLING_REPORT_S3_TO_CLICKHOUSE.name())
        .incrementer(new RunIdIncrementer())
        .listener(batchJobExecutionListener)
        .start(s3ToClickHouseSyncStep)
        .build();
  }

  @Bean
  public Step s3ToClickHouseSyncStep(StepBuilderFactory stepBuilderFactory) {
    return stepBuilderFactory.get("s3ToClickHouseSyncStep").tasklet(s3ToClickHouseSyncTasklet()).build();
  }

  @Bean
  public Tasklet s3ToClickHouseSyncTasklet() {
    return new S3ToClickHouseSyncTasklet();
  }
}
