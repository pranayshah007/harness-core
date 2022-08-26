/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.config;

import io.harness.batch.processing.ccm.ActualIdleCostBatchJobData;
import io.harness.batch.processing.ccm.BatchJobType;
import io.harness.batch.processing.reader.ActualIdleBillingDataReader;
import io.harness.batch.processing.svcmetrics.BatchJobExecutionListener;
import io.harness.batch.processing.writer.ActualIdleBillingDataWriter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory;
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class ActualIdleCostBatchConfig {
  private static final int BATCH_SIZE = 100;
  @Autowired private BatchJobExecutionListener batchJobExecutionListener;

  @Bean
  public ItemReader<ActualIdleCostBatchJobData> actualIdleCostReader() {
    return new ActualIdleBillingDataReader();
  }

  @Bean
  public ItemWriter<ActualIdleCostBatchJobData> actualIdleCostWriter() {
    return new ActualIdleBillingDataWriter();
  }

  @Bean
  @Qualifier(value = "actualIdleCostJob")
  public Job actualIdleCostJob(JobBuilderFactory jobBuilderFactory, Step actualIdleCostCalculationStep) {
    return jobBuilderFactory.get(BatchJobType.ACTUAL_IDLE_COST_BILLING.name())
        .incrementer(new RunIdIncrementer())
        .listener(batchJobExecutionListener)
        .start(actualIdleCostCalculationStep)
        .build();
  }

  @Bean
  @Qualifier(value = "actualIdleCostHourlyJob")
  public Job actualIdleCostHourlyJob(JobBuilderFactory jobBuilderFactory, Step actualIdleCostCalculationStep) {
    return jobBuilderFactory.get(BatchJobType.ACTUAL_IDLE_COST_BILLING_HOURLY.name())
        .incrementer(new RunIdIncrementer())
        .listener(batchJobExecutionListener)
        .start(actualIdleCostCalculationStep)
        .build();
  }

  @Bean
  public Step actualIdleCostCalculationStep(StepBuilderFactory stepBuilderFactory) {
    return stepBuilderFactory.get("actualIdleCostCalculationStep")
        .<ActualIdleCostBatchJobData, ActualIdleCostBatchJobData>chunk(BATCH_SIZE)
        .reader(actualIdleCostReader())
        .writer(actualIdleCostWriter())
        .build();
  }
}
