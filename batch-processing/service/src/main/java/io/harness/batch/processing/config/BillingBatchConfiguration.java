/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.config;

import io.harness.batch.processing.billing.writer.InstanceBillingAggregationDataTasklet;
import io.harness.batch.processing.billing.writer.InstanceBillingDataTasklet;
import io.harness.batch.processing.ccm.BatchJobType;
import io.harness.batch.processing.svcmetrics.BatchJobExecutionListener;

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

@Configuration
@Slf4j
public class BillingBatchConfiguration {
  @Autowired private StepBuilderFactory stepBuilderFactory;
  @Autowired private JobBuilderFactory jobBuilderFactory;
  @Autowired private BatchJobExecutionListener batchJobExecutionListener;

  @Bean
  public Tasklet instanceBillingDataTasklet() {
    return new InstanceBillingDataTasklet();
  }

  @Bean
  public Step instanceBillingStep() {
    return stepBuilderFactory.get("instanceBillingStep").tasklet(instanceBillingDataTasklet()).build();
  }

  @Bean
  @Qualifier(value = "instanceBillingHourlyJob")
  public Job instanceBillingHourlyJob(Step instanceBillingStep) {
    return jobBuilderFactory.get(BatchJobType.INSTANCE_BILLING_HOURLY.name())
        .incrementer(new RunIdIncrementer())
        .listener(batchJobExecutionListener)
        .start(instanceBillingStep)
        .build();
  }

  // ------------------------------------------------------------------------------------------

  @Bean
  @Qualifier(value = "instanceBillingJob")
  public Job instanceBillingJob(Step instanceBillingStep) {
    return jobBuilderFactory.get(BatchJobType.INSTANCE_BILLING.name())
        .incrementer(new RunIdIncrementer())
        .listener(batchJobExecutionListener)
        .start(instanceBillingStep)
        .build();
  }

  // ------------------------------------------------------------------------------------------

  @Bean
  public Tasklet instanceBillingAggregationDataTasklet() {
    return new InstanceBillingAggregationDataTasklet();
  }

  @Bean
  public Step instanceBillingHourlyAggregationStep() {
    return stepBuilderFactory.get("instanceBillingHourlyAggregationStep")
        .tasklet(instanceBillingAggregationDataTasklet())
        .build();
  }

  @Bean
  @Qualifier(value = "instanceBillingHourlyAggregationJob")
  public Job instanceBillingHourlyAggregationJob(Step instanceBillingHourlyAggregationStep) {
    return jobBuilderFactory.get(BatchJobType.INSTANCE_BILLING_HOURLY_AGGREGATION.name())
        .incrementer(new RunIdIncrementer())
        .listener(batchJobExecutionListener)
        .start(instanceBillingHourlyAggregationStep)
        .build();
  }

  @Bean
  public Step instanceBillingAggregationStep() {
    return stepBuilderFactory.get("instanceBillingAggregationStep")
        .tasklet(instanceBillingAggregationDataTasklet())
        .build();
  }

  @Bean
  @Qualifier(value = "instanceBillingAggregationJob")
  public Job instanceBillingAggregationJob(Step instanceBillingAggregationStep) {
    return jobBuilderFactory.get(BatchJobType.INSTANCE_BILLING_AGGREGATION.name())
        .incrementer(new RunIdIncrementer())
        .listener(batchJobExecutionListener)
        .start(instanceBillingAggregationStep)
        .build();
  }
}
