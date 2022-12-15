/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.config;

import io.harness.batch.processing.ccm.BatchJobType;
import io.harness.batch.processing.events.deployment.writer.DeploymentEventWriter;
import io.harness.batch.processing.reader.DeploymentEventReader;
import io.harness.batch.processing.svcmetrics.BatchJobExecutionListener;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory;
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.item.ItemWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class DeploymentEventConfiguration {
  private static final int DEPLOYMENT_BATCH_SIZE = 10;
  @Autowired private BatchJobExecutionListener batchJobExecutionListener;

  @Bean
  public ItemWriter<List<String>> deploymentEventWriter() {
    return new DeploymentEventWriter();
  }

  @Bean
  public Step deploymentEventStep(StepBuilderFactory stepBuilderFactory, DeploymentEventReader deploymentEventReader) {
    return stepBuilderFactory.get("deploymentEventStep")
        .<List<String>, List<String>>chunk(DEPLOYMENT_BATCH_SIZE)
        .reader(deploymentEventReader)
        .writer(deploymentEventWriter())
        .build();
  }

  @Bean
  @Autowired
  @Qualifier(value = "deploymentEventJob")
  public Job deploymentEventJob(JobBuilderFactory jobBuilderFactory, Step deploymentEventStep) {
    return jobBuilderFactory.get(BatchJobType.DEPLOYMENT_EVENT.name())
        .incrementer(new RunIdIncrementer())
        .listener(batchJobExecutionListener)
        .start(deploymentEventStep)
        .build();
  }
}
