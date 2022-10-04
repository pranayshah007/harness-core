/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.schedule;

import io.harness.batch.processing.ccm.BatchJobType;
import io.harness.batch.processing.metrics.CeProductMetricsTasklet;
import io.harness.batch.processing.metrics.ProductMetricsService;
import io.harness.batch.processing.svcmetrics.BatchJobExecutionListener;

import software.wings.service.intfc.instance.CloudToHarnessMappingService;

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
public class SegmentJobConfiguration {
  @Autowired private CloudToHarnessMappingService cloudToHarnessMappingService;
  @Autowired private ProductMetricsService productMetricsService;
  @Autowired private BatchJobExecutionListener batchJobExecutionListener;

  @Bean
  public Tasklet ceProductMetricsTasklet() {
    return new CeProductMetricsTasklet();
  }

  @Bean
  @Autowired
  @Qualifier(value = "ceProductMetricsJob")
  public Job ceProductMetricsJob(JobBuilderFactory jobBuilderFactory, Step ceProductMetricsStep) {
    return jobBuilderFactory.get(BatchJobType.CE_SEGMENT_CALL.name())
        .incrementer(new RunIdIncrementer())
        .listener(batchJobExecutionListener)
        .start(ceProductMetricsStep)
        .build();
  }

  @Bean
  public Step ceProductMetricsStep(StepBuilderFactory stepBuilderFactory) {
    return stepBuilderFactory.get("ceProductMetricsStep").tasklet(ceProductMetricsTasklet()).build();
  }
}
