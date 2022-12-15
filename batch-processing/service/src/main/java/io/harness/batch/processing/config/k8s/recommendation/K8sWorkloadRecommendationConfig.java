/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.config.k8s.recommendation;

import io.harness.batch.processing.ccm.BatchJobType;
import io.harness.batch.processing.dao.intfc.InstanceDataDao;
import io.harness.batch.processing.dao.intfc.PublishedMessageDao;
import io.harness.batch.processing.reader.CloseableIteratorItemReader;
import io.harness.batch.processing.reader.PublishedMessageBatchedReader;
import io.harness.batch.processing.service.intfc.WorkloadRepository;
import io.harness.batch.processing.svcmetrics.BatchJobExecutionListener;
import io.harness.batch.processing.tasklet.support.K8sLabelServiceInfoFetcher;
import io.harness.batch.processing.tasklet.util.ClusterHelper;
import io.harness.batch.processing.writer.constants.EventTypeConstants;
import io.harness.ccm.commons.dao.recommendation.RecommendationCrudService;
import io.harness.ccm.commons.entities.events.PublishedMessage;
import io.harness.ccm.commons.entities.k8s.recommendation.K8sWorkloadRecommendation;
import io.harness.ccm.commons.entities.k8s.recommendation.K8sWorkloadRecommendation.K8sWorkloadRecommendationKeys;
import io.harness.persistence.HIterator;
import io.harness.persistence.HPersistence;

import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.SkipListener;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory;
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.support.PassThroughItemProcessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;

@Configuration
@Slf4j
public class K8sWorkloadRecommendationConfig {
  private static final int BATCH_SIZE = 1000;

  @Autowired private BatchJobExecutionListener batchJobExecutionListener;
  private final PublishedMessageDao publishedMessageDao;
  private final StepBuilderFactory stepBuilderFactory;

  public K8sWorkloadRecommendationConfig(
      PublishedMessageDao publishedMessageDao, StepBuilderFactory stepBuilderFactory) {
    this.publishedMessageDao = publishedMessageDao;
    this.stepBuilderFactory = stepBuilderFactory;
  }

  @Bean
  @StepScope
  public ItemReader<PublishedMessage> workloadSpecReader(@Value("#{jobParameters[accountId]}") String accountId,
      @Value("#{jobParameters[startDate]}") Long startDate, @Value("#{jobParameters[endDate]}") Long endDate) {
    return new PublishedMessageBatchedReader(
        accountId, EventTypeConstants.K8S_WORKLOAD_SPEC, startDate, endDate, null, publishedMessageDao);
  }

  @Bean
  public Step workloadSpecStep(ItemReader<? extends PublishedMessage> workloadSpecReader,
      WorkloadSpecWriter workloadSpecWriter, SkipListener<PublishedMessage, PublishedMessage> skipListener) {
    return stepBuilderFactory.get("workloadSpecStep")
        .<PublishedMessage, PublishedMessage>chunk(BATCH_SIZE)
        .faultTolerant()
        .retry(Exception.class)
        .retryLimit(1)
        .skip(Exception.class)
        .skipLimit(50)
        .listener(skipListener)
        .reader(workloadSpecReader)
        .processor(new PassThroughItemProcessor<>())
        .writer(workloadSpecWriter)
        .build();
  }

  @Bean
  @StepScope
  public ItemReader<PublishedMessage> containerStateReader(@Value("#{jobParameters[accountId]}") String accountId,
      @Value("#{jobParameters[startDate]}") Long startDate, @Value("#{jobParameters[endDate]}") Long endDate) {
    return new PublishedMessageBatchedReader(
        accountId, EventTypeConstants.K8S_CONTAINER_STATE, startDate, endDate, null, publishedMessageDao);
  }

  @Bean
  @StepScope
  public ContainerStateWriter containerStateWriter(InstanceDataDao instanceDataDao,
      WorkloadRecommendationDao workloadRecommendationDao, @Value("#{jobParameters[startDate]}") Long startDateMillis) {
    Instant jobStartDate = Instant.ofEpochMilli(startDateMillis);
    return new ContainerStateWriter(instanceDataDao, workloadRecommendationDao, jobStartDate);
  }

  @Bean
  public Step containerStateStep(ItemReader<? extends PublishedMessage> containerStateReader,
      ContainerStateWriter containerStateWriter, SkipListener<PublishedMessage, PublishedMessage> skipListener) {
    return stepBuilderFactory.get("containerStateStep")
        .<PublishedMessage, PublishedMessage>chunk(BATCH_SIZE)
        .faultTolerant()
        .retry(Exception.class)
        .retryLimit(1)
        .skip(Exception.class)
        .skipLimit(50)
        .listener(skipListener)
        .reader(containerStateReader)
        .processor(new PassThroughItemProcessor<>())
        .writer(containerStateWriter)
        .build();
  }

  @Bean
  @StepScope
  public ItemReader<K8sWorkloadRecommendation> dirtyRecommendationReader(
      @Value("#{jobParameters[accountId]}") String accountId, HPersistence hPersistence, MongoTemplate mongoTemplate) {
    long slow = Duration.ofMinutes(1).toMillis();
    long dangerouslySlow = Duration.ofMinutes(3).toMillis();
    Iterator<K8sWorkloadRecommendation> hIterator =
        new HIterator<>(hPersistence.createQuery(K8sWorkloadRecommendation.class)
                            .field(K8sWorkloadRecommendationKeys.accountId)
                            .equal(accountId)
                            .field(K8sWorkloadRecommendationKeys.dirty)
                            .equal(Boolean.TRUE)
                            .fetch(),
            slow, dangerouslySlow);
    return new CloseableIteratorItemReader<>(hIterator);
  }

  @Bean
  @StepScope
  public ComputedRecommendationWriter computedRecommendationWriter(WorkloadRecommendationDao workloadRecommendationDao,
      WorkloadCostService workloadCostService, WorkloadRepository workloadRepository,
      K8sLabelServiceInfoFetcher k8sLabelServiceInfoFetcher, RecommendationCrudService recommendationCrudService,
      ClusterHelper clusterHelper, @Value("#{jobParameters[startDate]}") Long startDateMillis) {
    Instant jobStartDate = Instant.ofEpochMilli(startDateMillis);
    return new ComputedRecommendationWriter(workloadRecommendationDao, workloadCostService, workloadRepository,
        k8sLabelServiceInfoFetcher, recommendationCrudService, clusterHelper, jobStartDate);
  }

  @Bean
  public Step computeRecommendationStep(ItemReader<K8sWorkloadRecommendation> dirtyRecommendationReader,
      ItemProcessor<K8sWorkloadRecommendation, K8sWorkloadRecommendation> passThroughItemProcessor,
      ComputedRecommendationWriter computedRecommendationWriter) {
    return stepBuilderFactory.get("computeRecommendationStep")
        .<K8sWorkloadRecommendation, K8sWorkloadRecommendation>chunk(BATCH_SIZE)
        .faultTolerant()
        .retry(Exception.class)
        .retryLimit(1)
        .reader(dirtyRecommendationReader)
        .processor(new PassThroughItemProcessor<>())
        .writer(computedRecommendationWriter)
        .build();
  }

  @Bean
  public Job k8sRecommendationJob(JobBuilderFactory jobBuilderFactory, Step containerStateStep, Step workloadSpecStep,
      Step computeRecommendationStep) {
    return jobBuilderFactory.get(BatchJobType.K8S_WORKLOAD_RECOMMENDATION.name())
        .incrementer(new RunIdIncrementer())
        .listener(batchJobExecutionListener)
        // process WorkloadSpec messages and update current requests & limits.
        .start(workloadSpecStep)
        // process ContainerState messages and update histograms.
        .next(containerStateStep)
        // recompute recommendations if updated in last 2 steps.
        .next(computeRecommendationStep)
        .build();
  }
}
