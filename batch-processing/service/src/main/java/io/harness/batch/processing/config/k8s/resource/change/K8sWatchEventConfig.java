/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.config.k8s.resource.change;

import static java.util.Objects.requireNonNull;

import io.harness.batch.processing.ccm.BatchJobType;
import io.harness.batch.processing.ccm.ClusterType;
import io.harness.batch.processing.ccm.CostEventSource;
import io.harness.batch.processing.ccm.CostEventType;
import io.harness.batch.processing.ccm.EnrichedEvent;
import io.harness.batch.processing.dao.intfc.PublishedMessageDao;
import io.harness.batch.processing.events.timeseries.data.CostEventData;
import io.harness.batch.processing.events.timeseries.data.CostEventData.CostEventDataBuilder;
import io.harness.batch.processing.events.timeseries.service.intfc.CostEventService;
import io.harness.batch.processing.k8s.EstimatedCostDiff;
import io.harness.batch.processing.k8s.WatchEventCostEstimator;
import io.harness.batch.processing.reader.PublishedMessageBatchedReader;
import io.harness.batch.processing.service.intfc.WorkloadRepository;
import io.harness.batch.processing.support.Deduper;
import io.harness.batch.processing.svcmetrics.BatchJobExecutionListener;
import io.harness.batch.processing.tasklet.support.K8sLabelServiceInfoFetcher;
import io.harness.batch.processing.writer.constants.EventTypeConstants;
import io.harness.ccm.cluster.dao.K8sYamlDao;
import io.harness.ccm.commons.beans.HarnessServiceInfo;
import io.harness.ccm.commons.entities.events.PublishedMessage;
import io.harness.ccm.commons.entities.k8s.K8sWorkload;
import io.harness.govern.Switch;
import io.harness.perpetualtask.k8s.watch.K8sWatchEvent;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.common.collect.ImmutableList;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
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
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.support.builder.CompositeItemProcessorBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class K8sWatchEventConfig {
  private static final int BATCH_SIZE = 100;
  @Autowired private BatchJobExecutionListener batchJobExecutionListener;
  private final PublishedMessageDao publishedMessageDao;
  private final StepBuilderFactory stepBuilderFactory;

  private Cache<WorkloadId, Long> workloadLastChangeTimestamps = Caffeine.newBuilder().build();
  private Cache<WorkloadId, Deduper<WorkloadEventId>> workloadRecentHistoryCache = Caffeine.newBuilder().build();

  public K8sWatchEventConfig(PublishedMessageDao publishedMessageDao, StepBuilderFactory stepBuilderFactory) {
    this.publishedMessageDao = publishedMessageDao;
    this.stepBuilderFactory = stepBuilderFactory;
  }

  @Bean
  @StepScope
  public ItemReader<PublishedMessage> reader(@Value("#{jobParameters[accountId]}") String accountId,
      @Value("#{jobParameters[startDate]}") Long startDate, @Value("#{jobParameters[endDate]}") Long endDate) {
    return new PublishedMessageBatchedReader(
        accountId, EventTypeConstants.K8S_WATCH_EVENT, startDate, endDate, null, publishedMessageDao);
  }

  @Bean
  @StepScope
  public ItemProcessor<PublishedMessage, PublishedMessage> normalizer(
      CostEventService costEventService, @Value("#{jobParameters[startDate]}") long startDate) {
    String[] lastProcessedAccountId = {""};
    return k8sWatchEventMsg -> {
      String accountId = k8sWatchEventMsg.getAccountId();
      if (!lastProcessedAccountId[0].equals(accountId)) {
        workloadLastChangeTimestamps.invalidateAll();
        workloadRecentHistoryCache.invalidateAll();
        lastProcessedAccountId[0] = accountId;
      }
      K8sWatchEvent k8sWatchEvent = (K8sWatchEvent) k8sWatchEventMsg.getMessage();
      Instant minChangeInstant = Instant.ofEpochMilli(startDate).minus(1, ChronoUnit.DAYS);
      Deduper<WorkloadEventId> deduper = requireNonNull(workloadRecentHistoryCache.get(
          WorkloadId.of(k8sWatchEvent.getClusterId(), k8sWatchEvent.getResourceRef().getUid()),
          wl
          -> new Deduper<>(costEventService
                               .getEventsForWorkload(accountId, wl.getClusterId(), wl.getUid(),
                                   CostEventType.K8S_RESOURCE_CHANGE.name(), minChangeInstant.toEpochMilli())
                               .stream()
                               .map(ced
                                   -> Deduper.Timestamped.of(ced.getStartTimestamp(),
                                       WorkloadEventId.of(ced.getOldYamlRef(), ced.getNewYamlRef())))
                               .collect(Collectors.toList()))));
      if (deduper.checkEvent(
              Deduper.Timestamped.of(k8sWatchEventMsg.getOccurredAt(), WorkloadEventId.of(accountId, k8sWatchEvent)))) {
        return k8sWatchEventMsg;
      }
      return null;
    };
  }

  @Bean
  public ItemProcessor<PublishedMessage, EnrichedEvent<K8sWatchEvent>> enricher(
      WorkloadRepository workloadRepository, K8sLabelServiceInfoFetcher k8sLabelServiceInfoFetcher) {
    return k8sWatchEventMsg -> {
      String accountId = k8sWatchEventMsg.getAccountId();
      K8sWatchEvent k8sWatchEvent = (K8sWatchEvent) k8sWatchEventMsg.getMessage();
      String clusterId = k8sWatchEvent.getClusterId();
      String uid = k8sWatchEvent.getResourceRef().getUid();
      Map<String, String> labels = workloadRepository.getWorkload(accountId, clusterId, uid)
                                       .map(K8sWorkload::getLabels)
                                       .map(K8sWorkload::decodeDotsInKey)
                                       .orElse(Collections.emptyMap());
      Optional<HarnessServiceInfo> serviceInfo =
          k8sLabelServiceInfoFetcher.fetchHarnessServiceInfoFromCache(accountId, labels);
      return new EnrichedEvent<>(accountId, k8sWatchEventMsg.getOccurredAt(), k8sWatchEvent, serviceInfo.orElse(null));
    };
  }

  @Bean
  public ItemProcessor<PublishedMessage, EnrichedEvent<K8sWatchEvent>> processor(
      ItemProcessor<PublishedMessage, PublishedMessage> normalizer,
      ItemProcessor<PublishedMessage, EnrichedEvent<K8sWatchEvent>> enricher) {
    return new CompositeItemProcessorBuilder<PublishedMessage, EnrichedEvent<K8sWatchEvent>>()
        .delegates(ImmutableList.of(normalizer, enricher))
        .build();
  }

  @Bean
  public ItemWriter<EnrichedEvent<K8sWatchEvent>> writer(
      K8sYamlDao k8sYamlDao, CostEventService costEventService, WatchEventCostEstimator watchEventCostEstimator) {
    return enrichedK8sEvents
        -> costEventService.create(
            enrichedK8sEvents.stream()
                .map(enrichedK8sEvent -> {
                  String oldYamlRef = null;
                  String newYamlRef = null;
                  K8sWatchEvent k8sWatchEvent = enrichedK8sEvent.getEvent();
                  K8sWatchEvent.Type watchEventType = k8sWatchEvent.getType();
                  switch (watchEventType) {
                    case TYPE_ADDED:
                      newYamlRef = k8sYamlDao.ensureYamlSaved(enrichedK8sEvent.getAccountId(),
                          k8sWatchEvent.getClusterId(), k8sWatchEvent.getResourceRef().getUid(),
                          k8sWatchEvent.getNewResourceVersion(), k8sWatchEvent.getNewResourceYaml());
                      break;
                    case TYPE_UPDATED:
                      oldYamlRef = k8sYamlDao.ensureYamlSaved(enrichedK8sEvent.getAccountId(),
                          k8sWatchEvent.getClusterId(), k8sWatchEvent.getResourceRef().getUid(),
                          k8sWatchEvent.getOldResourceVersion(), k8sWatchEvent.getOldResourceYaml());
                      newYamlRef = k8sYamlDao.ensureYamlSaved(enrichedK8sEvent.getAccountId(),
                          k8sWatchEvent.getClusterId(), k8sWatchEvent.getResourceRef().getUid(),
                          k8sWatchEvent.getNewResourceVersion(), k8sWatchEvent.getNewResourceYaml());
                      break;
                    case TYPE_DELETED:
                      oldYamlRef = k8sYamlDao.ensureYamlSaved(enrichedK8sEvent.getAccountId(),
                          k8sWatchEvent.getClusterId(), k8sWatchEvent.getResourceRef().getUid(),
                          k8sWatchEvent.getOldResourceVersion(), k8sWatchEvent.getOldResourceYaml());
                      break;
                    default:
                      Switch.unhandled(watchEventType);
                  }
                  EstimatedCostDiff estimatedCostDiff = watchEventCostEstimator.estimateCostDiff(enrichedK8sEvent);
                  BigDecimal diffAmount = estimatedCostDiff.getDiffAmount();
                  BigDecimal diffPercent = estimatedCostDiff.getDiffAmountPercent();
                  CostEventDataBuilder costEventDataBuilder =
                      CostEventData.builder()
                          .accountId(enrichedK8sEvent.getAccountId())
                          .clusterType(ClusterType.K8S.name())
                          .costEventSource(CostEventSource.K8S_CLUSTER.name())
                          .costEventType(CostEventType.K8S_RESOURCE_CHANGE.name())
                          .clusterId(k8sWatchEvent.getClusterId())
                          .startTimestamp(enrichedK8sEvent.getOccurredAt())
                          .eventDescription(k8sWatchEvent.getDescription())
                          .namespace(k8sWatchEvent.getResourceRef().getNamespace())
                          .workloadName(k8sWatchEvent.getResourceRef().getName())
                          .workloadType(k8sWatchEvent.getResourceRef().getKind())
                          .instanceId(k8sWatchEvent.getResourceRef().getUid())
                          .billingAmount(diffAmount)
                          .oldYamlRef(oldYamlRef)
                          .newYamlRef(newYamlRef)
                          .costChangePercent(diffPercent);
                  if (enrichedK8sEvent.getHarnessServiceInfo() != null) {
                    costEventDataBuilder =
                        costEventDataBuilder.appId(enrichedK8sEvent.getHarnessServiceInfo().getAppId())
                            .serviceId(enrichedK8sEvent.getHarnessServiceInfo().getServiceId())
                            .envId(enrichedK8sEvent.getHarnessServiceInfo().getEnvId())
                            .cloudProviderId(k8sWatchEvent.getCloudProviderId());
                  }
                  return costEventDataBuilder.build();
                })
                .collect(Collectors.toList()));
  }

  @Bean
  public Step k8sWatchEventsStep(ItemReader<? extends PublishedMessage> reader,
      ItemProcessor<? super PublishedMessage, ? extends EnrichedEvent<K8sWatchEvent>> processor,
      ItemWriter<? super EnrichedEvent<K8sWatchEvent>> writer,
      SkipListener<PublishedMessage, PublishedMessage> skipListener) {
    return stepBuilderFactory.get("k8sWatchEventsStep")
        .<PublishedMessage, EnrichedEvent<K8sWatchEvent>>chunk(BATCH_SIZE)
        .faultTolerant()
        .retry(Exception.class)
        .retryLimit(1)
        .skip(Exception.class)
        .skipLimit(50)
        .listener(skipListener)
        .reader(reader)
        .processor(processor)
        .writer(writer)
        .build();
  }
  @Bean
  @Autowired
  public Job k8sWatchEventsJob(JobBuilderFactory jobBuilderFactory, Step k8sWatchEventsStep) {
    return jobBuilderFactory.get(BatchJobType.K8S_WATCH_EVENT.name())
        .incrementer(new RunIdIncrementer())
        .listener(batchJobExecutionListener)
        .start(k8sWatchEventsStep)
        .build();
  }
}
