/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.perpetualtask.k8s.metrics.collector;

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static software.wings.graphql.datafetcher.ce.recommendation.entity.RecommenderUtils.RECOMMENDER_VERSION;
import static software.wings.graphql.datafetcher.ce.recommendation.entity.RecommenderUtils.checkpointToProto;

import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.ccm.commons.constants.Constants;
import io.harness.event.client.EventPublisher;
import io.harness.event.payloads.AggregatedStorage;
import io.harness.event.payloads.AggregatedUsage;
import io.harness.event.payloads.ContainerStateProto;
import io.harness.event.payloads.NodeMetric;
import io.harness.event.payloads.PVMetric;
import io.harness.event.payloads.PodMetric;
import io.harness.grpc.utils.HDurations;
import io.harness.grpc.utils.HTimestamps;
import io.harness.histogram.HistogramCheckpoint;
import io.harness.k8s.model.statssummary.PVCRef;
import io.harness.k8s.model.statssummary.PodStats;
import io.harness.k8s.model.statssummary.Volume;
import io.harness.perpetualtask.k8s.informer.ClusterDetails;
import io.harness.perpetualtask.k8s.metrics.client.K8sMetricsClient;
import io.harness.perpetualtask.k8s.metrics.client.model.node.NodeMetrics;
import io.harness.perpetualtask.k8s.metrics.client.model.pod.PodMetrics;
import io.harness.perpetualtask.k8s.metrics.recommender.ContainerState;
import io.harness.perpetualtask.k8s.utils.ApiExceptionLogger;
import io.harness.perpetualtask.k8s.watch.K8sControllerFetcher;
import io.harness.perpetualtask.k8s.watch.K8sResourceStandardizer;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.common.collect.ImmutableMap;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.TemporalAmount;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import lombok.Builder;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@TargetModule(HarnessModule._420_DELEGATE_AGENT)
@OwnedBy(CE)
public class K8sMetricCollector {
  private static final TemporalAmount AGGREGATION_WINDOW = Duration.ofMinutes(20);

  @Value
  @Builder
  private static class CacheKey {
    String name;
    @Nullable String namespace;
    @Nullable String containerName;
    @Nullable String uid;
  }

  private final EventPublisher eventPublisher;
  private final ClusterDetails clusterDetails;
  // to make sure that PVMetric is collected only once for a single node in a single window.
  private final Map<String, Boolean> isNodeProcessed = new ConcurrentHashMap<>();

  private final Cache<CacheKey, Aggregates> podMetricsCache = Caffeine.newBuilder().build();
  private final Cache<CacheKey, Aggregates> nodeMetricsCache = Caffeine.newBuilder().build();
  private final Cache<CacheKey, Aggregates> pvMetricsCache = Caffeine.newBuilder().build();

  private final Cache<CacheKey, ContainerState> containerStatesCache = Caffeine.newBuilder().build();

  private Instant lastMetricPublished;

  public K8sMetricCollector(EventPublisher eventPublisher, ClusterDetails clusterDetails, Instant lastMetricPublished) {
    this.eventPublisher = eventPublisher;
    this.clusterDetails = clusterDetails;
    this.lastMetricPublished = lastMetricPublished;
  }

  public void collectAndPublishMetrics(
      K8sMetricsClient k8sMetricsClient, Instant now, CoreV1Api coreV1Api, K8sControllerFetcher controllerFetcher) {
    collectNodeMetrics(k8sMetricsClient);
    collectPodMetricsAndContainerStates(k8sMetricsClient, coreV1Api, controllerFetcher);
    collectPVMetrics(k8sMetricsClient);
    if (now.isAfter(this.lastMetricPublished.plus(AGGREGATION_WINDOW))) {
      publishPending(now);
      isNodeProcessed.clear();
    }
  }

  public void publishPending(Instant now) {
    publishNodeMetrics();
    publishPodMetrics();
    publishContainerStates();
    publishPVMetrics();
    this.lastMetricPublished = now;
  }

  private void collectPVMetrics(final K8sMetricsClient k8sMetricsClient) {
    // this function performs one api call (nodeStatsSummary) for each node, so we use map to only fetch a nodeStats if
    // it failed the last time.
    isNodeProcessed.entrySet().stream().filter(e -> !e.getValue()).map(Map.Entry::getKey).forEach(nodeName -> {
      try {
        for (PodStats podStats : k8sMetricsClient.podStats().list(nodeName).getObject().getItems()) {
          String podUid = ofNullable(podStats.getPodRef().getUid()).orElse("");
          for (Volume volume : podStats.getVolumeList()) {
            PVCRef pvcRef = volume.getPvcRef();
            if (pvcRef != null) {
              long capacity = K8sResourceStandardizer.getMemoryByte(volume.getCapacityBytes());
              long used = K8sResourceStandardizer.getMemoryByte(volume.getUsedBytes());

              requireNonNull(
                  pvMetricsCache.get(
                      CacheKey.builder().name(pvcRef.getName()).namespace(pvcRef.getNamespace()).uid(podUid).build(),
                      key -> new Aggregates(com.google.protobuf.Duration.newBuilder().setNanos(0).build())))
                  .updateStorage(capacity, used, volume.getTime());
            }
          }
        }
        isNodeProcessed.put(nodeName, Boolean.TRUE);
      } catch (Exception ex) {
        ApiExceptionLogger.logWarnIfNotSeenRecently(ex, "Failed to collect pvMetrics for node:" + nodeName);
      }
    });
  }

  private void collectNodeMetrics(final K8sMetricsClient k8sMetricsClient) {
    List<NodeMetrics> nodeMetricsList = k8sMetricsClient.nodeMetrics().list().getObject().getItems();
    for (NodeMetrics nodeMetrics : nodeMetricsList) {
      long nodeCpuNano = K8sResourceStandardizer.getCpuNano(nodeMetrics.getUsage().getCpu());
      long nodeMemoryBytes = K8sResourceStandardizer.getMemoryByte(nodeMetrics.getUsage().getMemory());
      requireNonNull(nodeMetricsCache.get(CacheKey.builder().name(nodeMetrics.getMetadata().getName()).build(),
                         key -> new Aggregates(HDurations.parse(nodeMetrics.getWindow()))))
          .update(nodeCpuNano, nodeMemoryBytes, nodeMetrics.getTimestamp());
      isNodeProcessed.putIfAbsent(nodeMetrics.getMetadata().getName(), Boolean.FALSE);
    }
  }

  private void collectPodMetricsAndContainerStates(
      final K8sMetricsClient k8sMetricsClient, CoreV1Api coreV1Api, K8sControllerFetcher controllerFetcher) {
    List<PodMetrics> podMetricsList = k8sMetricsClient.podMetrics().list().getObject().getItems();
    for (PodMetrics podMetrics : podMetricsList) {
      if (!isEmpty(podMetrics.getContainers())) {
        long podCpuNano = 0;
        long podMemoryBytes = 0;
        for (PodMetrics.Container container : podMetrics.getContainers()) {
          long containerCpuNano = K8sResourceStandardizer.getCpuNano(container.getUsage().getCpu());
          podCpuNano += containerCpuNano;
          long containerMemoryBytes = K8sResourceStandardizer.getMemoryByte(container.getUsage().getMemory());
          podMemoryBytes += containerMemoryBytes;

          ContainerState containerState =
              requireNonNull(containerStatesCache.get(CacheKey.builder()
                                                          .name(podMetrics.getMetadata().getName())
                                                          .namespace(podMetrics.getMetadata().getNamespace())
                                                          .containerName(container.getName())
                                                          .build(),
                  key -> new ContainerState(key.namespace, key.name, key.containerName)));
          double containerCpuCores = K8sResourceStandardizer.getCpuCores(container.getUsage().getCpu()).doubleValue();
          containerState.addCpuSample(containerCpuCores, podMetrics.getTimestamp());
          containerState.addMemorySample(containerMemoryBytes, podMetrics.getTimestamp());
        }
        requireNonNull(podMetricsCache.get(CacheKey.builder()
                                               .name(podMetrics.getMetadata().getName())
                                               .namespace(podMetrics.getMetadata().getNamespace())
                                               .build(),
                           key -> new Aggregates(HDurations.parse(podMetrics.getWindow()))))
            .update(podCpuNano, podMemoryBytes, podMetrics.getTimestamp());
      }
    }
  }

  private void publishNodeMetrics() {
    nodeMetricsCache.asMap()
        .entrySet()
        .stream()
        .map(e -> {
          Aggregates aggregates = e.getValue();
          return NodeMetric.newBuilder()
              .setCloudProviderId(clusterDetails.getCloudProviderId())
              .setClusterId(clusterDetails.getClusterId())
              .setKubeSystemUid(clusterDetails.getKubeSystemUid())
              .setName(e.getKey().getName())
              .setTimestamp(aggregates.getAggregateTimestamp())
              .setWindow(aggregates.getAggregateWindow())
              .setAggregatedUsage(AggregatedUsage.newBuilder()
                                      .setAvgCpuNano(aggregates.getCpu().getAverage())
                                      .setMaxCpuNano(aggregates.getCpu().getMax())
                                      .setAvgMemoryByte(aggregates.getMemory().getAverage())
                                      .setMaxMemoryByte(aggregates.getMemory().getMax())
                                      .build())
              .build();
        })
        .forEach(nodeMetric
            -> eventPublisher.publishMessage(nodeMetric, nodeMetric.getTimestamp(),
                ImmutableMap.of(Constants.CLUSTER_ID_IDENTIFIER, clusterDetails.getClusterId())));
    nodeMetricsCache.invalidateAll();
  }

  private void publishPVMetrics() {
    pvMetricsCache.asMap()
        .entrySet()
        .stream()
        .map(e -> {
          Aggregates aggregates = e.getValue();
          return PVMetric.newBuilder()
              .setCloudProviderId(clusterDetails.getCloudProviderId())
              .setClusterId(clusterDetails.getClusterId())
              .setKubeSystemUid(clusterDetails.getKubeSystemUid())
              .setName(e.getKey().getNamespace() + "/" + e.getKey().getName())
              .setNamespace(e.getKey().getNamespace())
              .setPodUid(e.getKey().getUid())
              .setTimestamp(aggregates.getAggregateTimestamp())
              .setWindow(aggregates.getAggregateWindow())
              .setAggregatedStorage(AggregatedStorage.newBuilder()
                                        .setAvgCapacityByte(aggregates.getStorageCapacity().getAverage())
                                        .setAvgUsedByte(aggregates.getStorageUsed().getAverage())
                                        .build())
              .build();
        })
        .forEach(pvMetric
            -> eventPublisher.publishMessage(pvMetric, pvMetric.getTimestamp(),
                ImmutableMap.of(Constants.CLUSTER_ID_IDENTIFIER, clusterDetails.getClusterId())));
    pvMetricsCache.invalidateAll();
  }

  private void publishPodMetrics() {
    podMetricsCache.asMap()
        .entrySet()
        .stream()
        .map(e -> {
          Aggregates aggregates = e.getValue();
          return PodMetric.newBuilder()
              .setCloudProviderId(clusterDetails.getCloudProviderId())
              .setClusterId(clusterDetails.getClusterId())
              .setKubeSystemUid(clusterDetails.getKubeSystemUid())
              .setNamespace(e.getKey().getNamespace())
              .setName(e.getKey().getName())
              .setTimestamp(aggregates.getAggregateTimestamp())
              .setWindow(aggregates.getAggregateWindow())
              .setAggregatedUsage(AggregatedUsage.newBuilder()
                                      .setAvgCpuNano(aggregates.getCpu().getAverage())
                                      .setMaxCpuNano(aggregates.getCpu().getMax())
                                      .setAvgMemoryByte(aggregates.getMemory().getAverage())
                                      .setMaxMemoryByte(aggregates.getMemory().getMax())
                                      .build())
              .build();
        })
        .forEach(podMetric
            -> eventPublisher.publishMessage(podMetric, podMetric.getTimestamp(),
                ImmutableMap.of(Constants.CLUSTER_ID_IDENTIFIER, clusterDetails.getClusterId())));
    podMetricsCache.invalidateAll();
  }

  private void publishContainerStates() {
    containerStatesCache.asMap()
        .entrySet()
        .stream()
        .map(e -> {
          ContainerState containerState = e.getValue();
          HistogramCheckpoint histogramCheckpoint = containerState.getCpuHistogram().saveToCheckpoint();
          HistogramCheckpoint histogramCheckpointV2 = containerState.getCpuHistogramV2().saveToCheckpoint();
          return ContainerStateProto.newBuilder()
              .setCloudProviderId(clusterDetails.getCloudProviderId())
              .setClusterId(clusterDetails.getClusterId())
              .setKubeSystemUid(clusterDetails.getKubeSystemUid())
              .setNamespace(e.getKey().getNamespace())
              .setPodName(e.getKey().getName())
              .setContainerName(e.getKey().getContainerName())
              .setMemoryPeak(containerState.getMemoryPeak())
              .setMemoryPeakTime(HTimestamps.fromInstant(containerState.getMemoryPeakTime()))
              .setCpuHistogram(checkpointToProto(histogramCheckpoint))
              .setCpuHistogramV2(checkpointToProto(histogramCheckpointV2))
              .setFirstSampleStart(HTimestamps.fromInstant(containerState.getFirstSampleStart()))
              .setLastSampleStart(HTimestamps.fromInstant(containerState.getLastSampleStart()))
              .setTotalSamplesCount(containerState.getTotalSamplesCount())
              .setVersion(RECOMMENDER_VERSION)
              .build();
        })
        .forEach(containerStateProto
            -> eventPublisher.publishMessage(containerStateProto, containerStateProto.getFirstSampleStart(),
                ImmutableMap.of(Constants.CLUSTER_ID_IDENTIFIER, clusterDetails.getClusterId())));
    containerStatesCache.invalidateAll();
  }
}
