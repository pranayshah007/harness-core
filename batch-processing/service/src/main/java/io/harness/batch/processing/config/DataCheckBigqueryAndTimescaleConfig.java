/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.config;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.batch.processing.ccm.BatchJobType;
import io.harness.batch.processing.svcmetrics.BatchJobExecutionListener;
import io.harness.batch.processing.tasklet.DataCheckBigqueryAndTimescaleTasklet;

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

@OwnedBy(HarnessTeam.CE)
@Slf4j
@Configuration
public class DataCheckBigqueryAndTimescaleConfig {
  @Autowired private BatchJobExecutionListener batchJobExecutionListener;

  @Bean
  public Tasklet dataCheckBigqueryAndTimescaleTasklet() {
    return new DataCheckBigqueryAndTimescaleTasklet();
  }

  @Bean
  public Step dataCheckBigqueryAndTimescaleStep(StepBuilderFactory stepBuilderFactory) {
    return stepBuilderFactory.get("dataCheckBigqueryAndTimescaleStep")
        .tasklet(dataCheckBigqueryAndTimescaleTasklet())
        .build();
  }

  @Bean
  @Qualifier(value = "dataCheckBigqueryAndTimescaleJob")
  public Job dataCheckBigqueryAndTimescaleJob(
      JobBuilderFactory jobBuilderFactory, Step dataCheckBigqueryAndTimescaleStep) {
    return jobBuilderFactory.get(BatchJobType.DATA_CHECK_BIGQUERY_TIMESCALE.name())
        .incrementer(new RunIdIncrementer())
        .listener(batchJobExecutionListener)
        .start(dataCheckBigqueryAndTimescaleStep)
        .build();
  }
}
