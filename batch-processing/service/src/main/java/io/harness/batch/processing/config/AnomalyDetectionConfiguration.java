/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.config;

import io.harness.batch.processing.anomalydetection.AnomalyDetectionConstants;
import io.harness.batch.processing.anomalydetection.AnomalyDetectionTimeSeries;
import io.harness.batch.processing.anomalydetection.RemoveDuplicateAnomaliesTasklet;
import io.harness.batch.processing.anomalydetection.SlackNotificationsTasklet;
import io.harness.batch.processing.anomalydetection.processor.AnomalyDetectionProcessor;
import io.harness.batch.processing.anomalydetection.reader.cloud.AnomalyDetectionAwsAccountReader;
import io.harness.batch.processing.anomalydetection.reader.cloud.AnomalyDetectionAwsServiceReader;
import io.harness.batch.processing.anomalydetection.reader.cloud.AnomalyDetectionAwsUsageTypeReader;
import io.harness.batch.processing.anomalydetection.reader.cloud.AnomalyDetectionAzureMeterCategoryReader;
import io.harness.batch.processing.anomalydetection.reader.cloud.AnomalyDetectionAzureResourceGroupReader;
import io.harness.batch.processing.anomalydetection.reader.cloud.AnomalyDetectionAzureSubscriptionReader;
import io.harness.batch.processing.anomalydetection.reader.cloud.AnomalyDetectionGcpProductReader;
import io.harness.batch.processing.anomalydetection.reader.cloud.AnomalyDetectionGcpProjectReader;
import io.harness.batch.processing.anomalydetection.reader.cloud.AnomalyDetectionGcpSkuReader;
import io.harness.batch.processing.anomalydetection.reader.k8s.AnomalyDetectionClusterTimescaleReader;
import io.harness.batch.processing.anomalydetection.reader.k8s.AnomalyDetectionNamespaceTimescaleReader;
import io.harness.batch.processing.anomalydetection.writer.AnomalyDetectionTimeScaleWriter;
import io.harness.batch.processing.ccm.BatchJobType;
import io.harness.batch.processing.svcmetrics.BatchJobExecutionListener;
import io.harness.ccm.anomaly.entities.Anomaly;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory;
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class AnomalyDetectionConfiguration {
  @Autowired private BatchJobExecutionListener batchJobExecutionListener;

  @Bean
  @Qualifier(value = "anomalyDetectionInClusterDailyJob")
  public Job anomalyDetectionInClusterDailyJob(JobBuilderFactory jobBuilderFactory, Step statisticalModelClusterStep,
      Step statisticalModelNamespaceStep, Step removeDuplicatesStep) {
    return jobBuilderFactory.get(BatchJobType.ANOMALY_DETECTION_K8S.name())
        .incrementer(new RunIdIncrementer())
        .listener(batchJobExecutionListener)
        .start(statisticalModelClusterStep)
        .next(statisticalModelNamespaceStep)
        .next(removeDuplicatesStep)
        .build();
  }

  @Bean
  @Qualifier(value = "anomalyDetectionOutOfClusterDailyJob")
  public Job anomalyDetectionOutOfClusterDailyJob(JobBuilderFactory jobBuilderFactory,
      Step statisticalModelGcpProjectStep, Step statisticalModelGcpSkuStep, Step statisticalModelGcpProductStep,
      Step statisticalModelAwsAccountStep, Step statisticalModelAwsServiceStep,
      Step statisticalModelAzureSubscriptionStep, Step statisticalModelAzureResourceGroupStep,
      Step statisticalModelAzureMeterCategoryStep, Step removeDuplicatesStep, Step statisticalModelAwsUsageTypeStep,
      Step slackNotificationStep) {
    return jobBuilderFactory.get(BatchJobType.ANOMALY_DETECTION_CLOUD.name())
        .incrementer(new RunIdIncrementer())
        .listener(batchJobExecutionListener)
        .start(statisticalModelGcpProjectStep)
        .next(statisticalModelGcpProductStep)
        .next(statisticalModelGcpSkuStep)
        .next(statisticalModelAwsAccountStep)
        .next(statisticalModelAwsServiceStep)
        .next(statisticalModelAwsUsageTypeStep)
        .next(statisticalModelAzureSubscriptionStep)
        .next(statisticalModelAzureResourceGroupStep)
        .next(statisticalModelAzureMeterCategoryStep)
        .next(removeDuplicatesStep)
        .next(slackNotificationStep)
        .build();
  }

  @Bean
  protected Step statisticalModelClusterStep(StepBuilderFactory stepBuilderFactory) {
    return stepBuilderFactory.get("statisticalModelClusterDailyAnomalyDetectionStep")
        .<AnomalyDetectionTimeSeries, Anomaly>chunk(AnomalyDetectionConstants.BATCH_SIZE)
        .reader(clusterItemReader())
        .processor(modelProcessor())
        .writer(timescaleWriter())
        .build();
  }

  @Bean
  protected Step statisticalModelNamespaceStep(StepBuilderFactory stepBuilderFactory) {
    return stepBuilderFactory.get("statisticalModelNamespaceDailyAnomalyDetectionStep")
        .<AnomalyDetectionTimeSeries, Anomaly>chunk(AnomalyDetectionConstants.BATCH_SIZE)
        .reader(namespaceItemReader())
        .processor(modelProcessor())
        .writer(timescaleWriter())
        .build();
  }

  @Bean
  protected Step statisticalModelGcpProjectStep(StepBuilderFactory stepBuilderFactory) {
    return stepBuilderFactory.get("statisticalModelGcpProjectDailyAnomalyDetectionStep")
        .<AnomalyDetectionTimeSeries, Anomaly>chunk(AnomalyDetectionConstants.BATCH_SIZE)
        .reader(gcpProjectItemReader())
        .processor(modelProcessor())
        .writer(timescaleWriter())
        .build();
  }

  @Bean
  protected Step statisticalModelGcpProductStep(StepBuilderFactory stepBuilderFactory) {
    return stepBuilderFactory.get("statisticalModelGcpProductDailyAnomalyDetectionStep")
        .<AnomalyDetectionTimeSeries, Anomaly>chunk(AnomalyDetectionConstants.BATCH_SIZE)
        .reader(gcpProductItemReader())
        .processor(modelProcessor())
        .writer(timescaleWriter())
        .build();
  }

  @Bean
  protected Step statisticalModelGcpSkuStep(StepBuilderFactory stepBuilderFactory) {
    return stepBuilderFactory.get("statisticalModelGcpSkuDailyAnomalyDetectionStep")
        .<AnomalyDetectionTimeSeries, Anomaly>chunk(AnomalyDetectionConstants.BATCH_SIZE)
        .reader(gcpSkuItemReader())
        .processor(modelProcessor())
        .writer(timescaleWriter())
        .build();
  }

  @Bean
  protected Step statisticalModelAwsAccountStep(StepBuilderFactory stepBuilderFactory) {
    return stepBuilderFactory.get("statisticalModelAwsAccountDailyAnomalyDetectionStep")
        .<AnomalyDetectionTimeSeries, Anomaly>chunk(AnomalyDetectionConstants.BATCH_SIZE)
        .reader(awsAccountItemReader())
        .processor(modelProcessor())
        .writer(timescaleWriter())
        .build();
  }

  @Bean
  protected Step statisticalModelAzureSubscriptionStep(StepBuilderFactory stepBuilderFactory) {
    return stepBuilderFactory.get("statisticalModelAzureSubscriptionDailyAnomalyDetectionStep")
        .<AnomalyDetectionTimeSeries, Anomaly>chunk(AnomalyDetectionConstants.BATCH_SIZE)
        .reader(azureSubscriptionItemReader())
        .processor(modelProcessor())
        .writer(timescaleWriter())
        .build();
  }

  @Bean
  protected Step statisticalModelAzureResourceGroupStep(StepBuilderFactory stepBuilderFactory) {
    return stepBuilderFactory.get("statisticalModelAzureResourceGroupDailyAnomalyDetectionStep")
        .<AnomalyDetectionTimeSeries, Anomaly>chunk(AnomalyDetectionConstants.BATCH_SIZE)
        .reader(azureResourceGroupItemReader())
        .processor(modelProcessor())
        .writer(timescaleWriter())
        .build();
  }

  @Bean
  protected Step statisticalModelAzureMeterCategoryStep(StepBuilderFactory stepBuilderFactory) {
    return stepBuilderFactory.get("statisticalModelAzureMeterCategoryDailyAnomalyDetectionStep")
        .<AnomalyDetectionTimeSeries, Anomaly>chunk(AnomalyDetectionConstants.BATCH_SIZE)
        .reader(azureMeterCategoryItemReader())
        .processor(modelProcessor())
        .writer(timescaleWriter())
        .build();
  }

  @Bean
  protected Step statisticalModelAwsServiceStep(StepBuilderFactory stepBuilderFactory) {
    return stepBuilderFactory.get("statisticalModelAwsServiceDailyAnomalyDetectionStep")
        .<AnomalyDetectionTimeSeries, Anomaly>chunk(AnomalyDetectionConstants.BATCH_SIZE)
        .reader(awsServiceItemReader())
        .processor(modelProcessor())
        .writer(timescaleWriter())
        .build();
  }

  @Bean
  protected Step statisticalModelAwsUsageTypeStep(StepBuilderFactory stepBuilderFactory) {
    return stepBuilderFactory.get("statisticalModelAwsUsageTypeDailyAnomalyDetectionStep")
        .<AnomalyDetectionTimeSeries, Anomaly>chunk(AnomalyDetectionConstants.BATCH_SIZE)
        .reader(awsUsageTypeItemReader())
        .processor(modelProcessor())
        .writer(timescaleWriter())
        .build();
  }

  @Bean
  protected Step removeDuplicatesStep(StepBuilderFactory stepBuilderFactory) {
    return stepBuilderFactory.get("removeDuplicateAnomaliesStep").tasklet(removeDuplicateAnomaliesTasklet()).build();
  }

  @Bean
  protected Step slackNotificationStep(StepBuilderFactory stepBuilderFactory) {
    return stepBuilderFactory.get("slackNotificationStep").tasklet(slackNotificationsTasklet()).build();
  }

  @Bean
  public Tasklet removeDuplicateAnomaliesTasklet() {
    return new RemoveDuplicateAnomaliesTasklet();
  }

  @Bean
  public Tasklet slackNotificationsTasklet() {
    return new SlackNotificationsTasklet();
  }

  // ---------------- Item Reader ----------------------
  @Bean
  public ItemReader<AnomalyDetectionTimeSeries> clusterItemReader() {
    return new AnomalyDetectionClusterTimescaleReader();
  }

  @Bean
  public ItemReader<AnomalyDetectionTimeSeries> namespaceItemReader() {
    return new AnomalyDetectionNamespaceTimescaleReader();
  }

  @Bean
  public ItemReader<AnomalyDetectionTimeSeries> gcpProjectItemReader() {
    return new AnomalyDetectionGcpProjectReader();
  }

  @Bean
  public ItemReader<AnomalyDetectionTimeSeries> gcpProductItemReader() {
    return new AnomalyDetectionGcpProductReader();
  }

  @Bean
  public ItemReader<AnomalyDetectionTimeSeries> gcpSkuItemReader() {
    return new AnomalyDetectionGcpSkuReader();
  }

  @Bean
  public ItemReader<AnomalyDetectionTimeSeries> awsAccountItemReader() {
    return new AnomalyDetectionAwsAccountReader();
  }

  @Bean
  public ItemReader<AnomalyDetectionTimeSeries> azureSubscriptionItemReader() {
    return new AnomalyDetectionAzureSubscriptionReader();
  }

  @Bean
  public ItemReader<AnomalyDetectionTimeSeries> azureResourceGroupItemReader() {
    return new AnomalyDetectionAzureResourceGroupReader();
  }

  @Bean
  public ItemReader<AnomalyDetectionTimeSeries> azureMeterCategoryItemReader() {
    return new AnomalyDetectionAzureMeterCategoryReader();
  }

  @Bean
  public ItemReader<AnomalyDetectionTimeSeries> awsServiceItemReader() {
    return new AnomalyDetectionAwsServiceReader();
  }

  @Bean
  public ItemReader<AnomalyDetectionTimeSeries> awsUsageTypeItemReader() {
    return new AnomalyDetectionAwsUsageTypeReader();
  }

  // ---------------- Item Processor ----------------------
  @Bean
  public ItemProcessor<AnomalyDetectionTimeSeries, Anomaly> modelProcessor() {
    return new AnomalyDetectionProcessor();
  }

  // ---------------- Item Writer ----------------------
  @Bean
  public ItemWriter<Anomaly> timescaleWriter() {
    return new AnomalyDetectionTimeScaleWriter();
  }
}
