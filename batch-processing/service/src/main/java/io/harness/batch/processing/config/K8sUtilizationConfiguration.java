/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.config;

import io.harness.batch.processing.ccm.BatchJobType;
import io.harness.batch.processing.dao.intfc.PublishedMessageDao;
import io.harness.batch.processing.reader.K8sGranularUtilizationMetricsReader;
import io.harness.batch.processing.reader.PublishedMessageBatchedReader;
import io.harness.batch.processing.svcmetrics.BatchJobExecutionListener;
import io.harness.batch.processing.writer.K8sPVUtilizationAggregationTasklet;
import io.harness.batch.processing.writer.K8sUtilizationMetricsWriter;
import io.harness.batch.processing.writer.NodeUtilizationMetricsWriter;
import io.harness.batch.processing.writer.PVUtilizationMetricsWriter;
import io.harness.batch.processing.writer.PodUtilizationMetricsWriter;
import io.harness.batch.processing.writer.constants.EventTypeConstants;
import io.harness.ccm.commons.entities.events.PublishedMessage;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory;
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class K8sUtilizationConfiguration {
  private static final int BATCH_SIZE = 50;
  private static final int GRANULAR_BATCH_SIZE = 2000;

  @Autowired private StepBuilderFactory stepBuilderFactory;
  @Autowired private PublishedMessageDao publishedMessageDao;
  @Autowired private BatchJobExecutionListener batchJobExecutionListener;
  /*
   * ****************** PodUtilization ******************
   */

  //  READER
  @Bean
  @StepScope
  public ItemReader<PublishedMessage> k8sPodUtilizationEventMessageReader(
      @Value("#{jobParameters[accountId]}") String accountId, @Value("#{jobParameters[startDate]}") Long startDate,
      @Value("#{jobParameters[endDate]}") Long endDate) {
    return new PublishedMessageBatchedReader(
        accountId, EventTypeConstants.POD_UTILIZATION, startDate, endDate, null, publishedMessageDao);
  }

  //  WRITER
  @Bean
  public ItemWriter<PublishedMessage> podUtilizationMetricsWriter() {
    return new PodUtilizationMetricsWriter();
  }

  // STEP
  @Bean
  public Step k8sPodUtilizationEventStep() {
    return stepBuilderFactory.get("k8sPodUtilizationEventStep")
        .<PublishedMessage, PublishedMessage>chunk(GRANULAR_BATCH_SIZE)
        .reader(k8sPodUtilizationEventMessageReader(null, null, null))
        .writer(podUtilizationMetricsWriter())
        .build();
  }

  /*
   ****************** NodeUtilization ******************
   */

  //  READER
  @Bean
  @StepScope
  public ItemReader<PublishedMessage> k8sNodeUtilizationEventMessageReader(
      @Value("#{jobParameters[accountId]}") String accountId, @Value("#{jobParameters[startDate]}") Long startDate,
      @Value("#{jobParameters[endDate]}") Long endDate) {
    return new PublishedMessageBatchedReader(
        accountId, EventTypeConstants.NODE_UTILIZATION, startDate, endDate, GRANULAR_BATCH_SIZE, publishedMessageDao);
  }

  //  WRITER
  @Bean
  public ItemWriter<PublishedMessage> nodeUtilizationMetricsWriter() {
    return new NodeUtilizationMetricsWriter();
  }

  // STEP
  @Bean
  public Step k8sNodeUtilizationEventStep() {
    return stepBuilderFactory.get("k8sNodeUtilizationEventStep")
        .<PublishedMessage, PublishedMessage>chunk(GRANULAR_BATCH_SIZE)
        .reader(k8sNodeUtilizationEventMessageReader(null, null, null))
        .writer(nodeUtilizationMetricsWriter())
        .build();
  }

  /*
   ****************** PVUtilization ******************
   */

  //  READER
  @Bean
  @StepScope
  public ItemReader<PublishedMessage> k8sPVUtilizationEventMessageReader(
      @Value("#{jobParameters[accountId]}") String accountId, @Value("#{jobParameters[startDate]}") Long startDate,
      @Value("#{jobParameters[endDate]}") Long endDate) {
    return new PublishedMessageBatchedReader(
        accountId, EventTypeConstants.PV_UTILIZATION, startDate, endDate, GRANULAR_BATCH_SIZE, publishedMessageDao);
  }

  //  WRITER
  @Bean
  public ItemWriter<PublishedMessage> pvUtilizationMetricsWriter() {
    return new PVUtilizationMetricsWriter();
  }

  // STEP
  @Bean
  public Step k8sPVUtilizationGranularStep() {
    return stepBuilderFactory.get("k8sPVUtilizationGranularStep")
        .<PublishedMessage, PublishedMessage>chunk(GRANULAR_BATCH_SIZE)
        .reader(k8sPVUtilizationEventMessageReader(null, null, null))
        .writer(pvUtilizationMetricsWriter())
        .build();
  }

  /*
   * ****************** K8sUtilizationAggregation ******************
   */

  //  READER
  @Bean
  public ItemReader<List<String>> k8sUtilizationAggregationReader() {
    return new K8sGranularUtilizationMetricsReader();
  }

  //  WRITER
  @Bean
  public ItemWriter<List<String>> k8sUtilizationAggregationWriter() {
    return new K8sUtilizationMetricsWriter();
  }

  // STEP
  @Bean
  public Step k8sUtilizationAggregationStep() {
    return stepBuilderFactory.get("k8sUtilizationAggregationStep")
        .<List<String>, List<String>>chunk(BATCH_SIZE)
        .reader(k8sUtilizationAggregationReader())
        .writer(k8sUtilizationAggregationWriter())
        .build();
  }

  /*
   * ****************** k8sPVUtilizationAggregation ******************
   */

  // Tasklet
  @Bean
  public Tasklet k8sPVUtilizationAggregationTasklet() {
    return new K8sPVUtilizationAggregationTasklet();
  }

  // STEP
  @Bean
  public Step k8sPVUtilizationAggregationStep() {
    return stepBuilderFactory.get("k8sPVUtilizationAggregationStep")
        .tasklet(k8sPVUtilizationAggregationTasklet())
        .build();
  }

  /*
   * ****************** Job ******************
   */
  @Bean
  @Autowired
  @Qualifier(value = "k8sUtilizationJob")
  public Job k8sUtilizationJob(JobBuilderFactory jobBuilderFactory, Step k8sPodUtilizationEventStep,
      Step k8sNodeUtilizationEventStep, Step k8sUtilizationAggregationStep, Step k8sPVUtilizationGranularStep,
      Step k8sPVUtilizationAggregationStep) {
    return jobBuilderFactory.get(BatchJobType.K8S_UTILIZATION.name())
        .incrementer(new RunIdIncrementer())
        .listener(batchJobExecutionListener)
        .start(k8sPodUtilizationEventStep)
        .next(k8sNodeUtilizationEventStep)
        .next(k8sUtilizationAggregationStep)
        .next(k8sPVUtilizationGranularStep)
        .next(k8sPVUtilizationAggregationStep)
        .build();
  }
}
