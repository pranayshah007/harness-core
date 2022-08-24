/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.config;

import io.harness.batch.processing.ccm.BatchJobType;
import io.harness.batch.processing.cloudevents.aws.ecs.service.tasklet.AwsECSClusterDataSyncTasklet;
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

@Slf4j
@Configuration
public class AwsECSClusterDataSyncConfiguration {
  @Autowired private BatchJobExecutionListener batchJobExecutionListener;

  @Bean
  public Tasklet awsECSClusterDataSyncTasklet() {
    return new AwsECSClusterDataSyncTasklet();
  }

  @Bean
  @Autowired
  @Qualifier(value = "awsECSClusterDataSyncJob")
  public Job awsECSClusterDataSyncJob(JobBuilderFactory jobBuilderFactory, Step awsECSClusterDataSyncStep) {
    return jobBuilderFactory.get(BatchJobType.AWS_ECS_CLUSTER_DATA_SYNC.name())
        .incrementer(new RunIdIncrementer())
        .listener(batchJobExecutionListener)
        .start(awsECSClusterDataSyncStep)
        .build();
  }

  @Bean
  public Step awsECSClusterDataSyncStep(StepBuilderFactory stepBuilderFactory) {
    return stepBuilderFactory.get("awsECSClusterDataSyncStep").tasklet(awsECSClusterDataSyncTasklet()).build();
  }
}
