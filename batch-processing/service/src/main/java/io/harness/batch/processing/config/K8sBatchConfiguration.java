/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.config;

import io.harness.batch.processing.ccm.BatchJobType;
import io.harness.batch.processing.svcmetrics.BatchJobExecutionListener;
import io.harness.batch.processing.tasklet.K8SSyncEventTasklet;
import io.harness.batch.processing.tasklet.K8sNodeEventTasklet;
import io.harness.batch.processing.tasklet.K8sNodeInfoTasklet;
import io.harness.batch.processing.tasklet.K8sPVEventTasklet;
import io.harness.batch.processing.tasklet.K8sPVInfoTasklet;
import io.harness.batch.processing.tasklet.K8sPodEventTasklet;
import io.harness.batch.processing.tasklet.K8sPodInfoTasklet;

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
public class K8sBatchConfiguration {
  @Autowired private StepBuilderFactory stepBuilderFactory;
  @Autowired private BatchJobExecutionListener batchJobExecutionListener;

  @Bean
  public Tasklet k8sNodeInfoTasklet() {
    return new K8sNodeInfoTasklet();
  }

  @Bean
  public Tasklet k8sNodeEventTasklet() {
    return new K8sNodeEventTasklet();
  }

  @Bean
  public Tasklet k8sPodInfoTasklet() {
    return new K8sPodInfoTasklet();
  }

  @Bean
  public Tasklet k8sPodEventTasklet() {
    return new K8sPodEventTasklet();
  }

  @Bean
  public Tasklet k8SSyncEventTasklet() {
    return new K8SSyncEventTasklet();
  }

  @Bean
  public Tasklet k8sPVInfoTasklet() {
    return new K8sPVInfoTasklet();
  }

  @Bean
  public Tasklet k8sPVEventTasklet() {
    return new K8sPVEventTasklet();
  }

  @Bean
  public Step k8sSyncEventStep(StepBuilderFactory stepBuilderFactory) {
    return stepBuilderFactory.get("k8sSyncEventStep").tasklet(k8SSyncEventTasklet()).build();
  }

  @Bean
  public Step k8sNodeInfoStep(StepBuilderFactory stepBuilderFactory) {
    return stepBuilderFactory.get("k8sNodeInfoStep").tasklet(k8sNodeInfoTasklet()).build();
  }

  @Bean
  public Step k8sNodeEventStep(StepBuilderFactory stepBuilderFactory) {
    return stepBuilderFactory.get("k8sNodeEventStep").tasklet(k8sNodeEventTasklet()).build();
  }

  @Bean
  public Step k8sPodInfoStep(StepBuilderFactory stepBuilderFactory) {
    return stepBuilderFactory.get("k8sPodInfoStep").tasklet(k8sPodInfoTasklet()).build();
  }

  @Bean
  public Step k8sPodEventStep(StepBuilderFactory stepBuilderFactory) {
    return stepBuilderFactory.get("k8sPodEventStep").tasklet(k8sPodEventTasklet()).build();
  }

  @Bean
  public Step k8sPVInfoStep(StepBuilderFactory stepBuilderFactory) {
    return stepBuilderFactory.get("k8sPVInfoStep").tasklet(k8sPVInfoTasklet()).build();
  }

  @Bean
  public Step k8sPVEventStep(StepBuilderFactory stepBuilderFactory) {
    return stepBuilderFactory.get("k8sPVEventStep").tasklet(k8sPVEventTasklet()).build();
  }

  @Bean
  @Autowired
  @Qualifier(value = "k8sJob")
  public Job k8sJob(JobBuilderFactory jobBuilderFactory, Step k8sNodeInfoStep, Step k8sNodeEventStep,
      Step k8sPodInfoStep, Step k8sPodEventStep, Step k8sPVInfoStep, Step k8sPVEventStep, Step k8sSyncEventStep) {
    return jobBuilderFactory.get(BatchJobType.K8S_EVENT.name())
        .incrementer(new RunIdIncrementer())
        .listener(batchJobExecutionListener)
        .start(k8sNodeInfoStep)
        .next(k8sNodeEventStep)
        .next(k8sPodInfoStep)
        .next(k8sPodEventStep)
        .next(k8sPVInfoStep)
        .next(k8sPVEventStep)
        .next(k8sSyncEventStep)
        .build();
  }
}
