/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.config.k8s.recommendation;

import static io.harness.batch.processing.tasklet.util.InstanceMetaDataUtils.getValueForKeyFromInstanceMetaData;
import static io.harness.ccm.commons.beans.recommendation.ResourceId.NOT_FOUND;
import static io.harness.time.DurationUtils.truncate;

import static software.wings.graphql.datafetcher.ce.recommendation.entity.RecommenderUtils.MEMORY_AGGREGATION_INTERVAL;
import static software.wings.graphql.datafetcher.ce.recommendation.entity.RecommenderUtils.RECOMMENDER_VERSION;
import static software.wings.graphql.datafetcher.ce.recommendation.entity.RecommenderUtils.newCpuHistogram;
import static software.wings.graphql.datafetcher.ce.recommendation.entity.RecommenderUtils.newCpuHistogramV2;
import static software.wings.graphql.datafetcher.ce.recommendation.entity.RecommenderUtils.protoToCheckpoint;

import static java.time.Duration.between;

import io.harness.batch.processing.dao.intfc.InstanceDataDao;
import io.harness.ccm.commons.beans.recommendation.ResourceId;
import io.harness.ccm.commons.constants.InstanceMetaDataConstants;
import io.harness.ccm.commons.entities.batch.InstanceData;
import io.harness.ccm.commons.entities.events.PublishedMessage;
import io.harness.ccm.commons.entities.k8s.recommendation.K8sWorkloadRecommendation;
import io.harness.ccm.commons.entities.k8s.recommendation.PartialRecommendationHistogram;
import io.harness.event.payloads.ContainerStateProto;
import io.harness.grpc.utils.HTimestamps;
import io.harness.histogram.Histogram;

import software.wings.graphql.datafetcher.ce.recommendation.entity.ContainerCheckpoint;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.jetbrains.annotations.NotNull;
import org.springframework.batch.item.ItemWriter;

@Slf4j
class ContainerStateWriter implements ItemWriter<PublishedMessage> {
  private static final Set<Integer> ACCEPTED_VERSIONS = Collections.singleton(RECOMMENDER_VERSION);

  private final InstanceDataDao instanceDataDao;
  private final WorkloadRecommendationDao workloadRecommendationDao;

  private final LoadingCache<ResourceId, ResourceId> podToWorkload;

  private final Instant jobStartDate;

  ContainerStateWriter(
      InstanceDataDao instanceDataDao, WorkloadRecommendationDao workloadRecommendationDao, Instant jobStartDate) {
    this.jobStartDate = jobStartDate;
    this.podToWorkload = Caffeine.newBuilder().maximumSize(10000).build(this::fetchWorkloadIdForPod);
    this.instanceDataDao = instanceDataDao;
    this.workloadRecommendationDao = workloadRecommendationDao;
  }

  @Override
  public void write(List<? extends PublishedMessage> items) throws Exception {
    Map<ResourceId, WorkloadState> workloadToRecommendation = new HashMap<>();
    Map<ResourceId, WorkloadStateV2> workloadToPartialHistogram = new HashMap<>();
    for (PublishedMessage item : items) {
      String accountId = item.getAccountId();
      ContainerStateProto containerStateProto = (ContainerStateProto) item.getMessage();

      if (!ACCEPTED_VERSIONS.contains(containerStateProto.getVersion())) {
        log.warn("Skip incompatible version: {}. Accepted: {}", containerStateProto.getVersion(), ACCEPTED_VERSIONS);
        continue;
      }
      String clusterId = containerStateProto.getClusterId();
      String namespace = containerStateProto.getNamespace();
      String podName = containerStateProto.getPodName();
      ResourceId podId = ResourceId.builder()
                             .accountId(accountId)
                             .clusterId(clusterId)
                             .namespace(namespace)
                             .name(podName)
                             .kind("Pod")
                             .build();
      ResourceId workloadId = Objects.requireNonNull(podToWorkload.get(podId));
      // intentional reference equality
      if (workloadId == NOT_FOUND) {
        // pod to workload mapping not found in instanceData. Skip this item.
        log.debug("Skipping sample {} as pod to workload mapping not found", containerStateProto);
        continue;
      }
      WorkloadState workloadState = workloadToRecommendation.computeIfAbsent(workloadId, this::getWorkloadState);
      updateContainerStateMap(workloadState.getContainerStateMap(), containerStateProto);

      WorkloadStateV2 workloadStateV2 =
          workloadToPartialHistogram.computeIfAbsent(workloadId, this::getWorkloadStateV2);
      updateContainerStateMapSingleDay(workloadStateV2.getContainerStateMap(), containerStateProto);
    }
    persistChanges(workloadToRecommendation);
    persistChangesV2(workloadToPartialHistogram);
  }

  private void updateContainerStateMap(
      Map<String, ContainerState> containerStateMap, ContainerStateProto containerStateProto) {
    String containerName = containerStateProto.getContainerName();
    ContainerState containerState = containerStateMap.get(containerName);
    Instant firstSampleStart = HTimestamps.toInstant(containerStateProto.getFirstSampleStart());
    Instant lastSampleStart = HTimestamps.toInstant(containerStateProto.getLastSampleStart());
    if (containerState != null && containerState.getVersion() >= containerStateProto.getVersion()
        && firstSampleStart.isBefore(containerState.getLastSampleStart())) {
      log.debug("Skipping sample {} as interval already covered", containerStateProto);
    } else {
      if (containerState == null || containerState.getVersion() < containerStateProto.getVersion()) {
        // First sample seen for this container, or new version of proto for this container
        // Re-initialize containerState
        containerState = new ContainerState();
        containerState.setFirstSampleStart(firstSampleStart);
        containerStateMap.put(containerName, containerState);
        containerState.setVersion(containerStateProto.getVersion());
      }
      containerState.setLastUpdateTime(Instant.now());
      containerState.setLastSampleStart(lastSampleStart);
      containerState.setTotalSamplesCount(
          containerState.getTotalSamplesCount() + containerStateProto.getTotalSamplesCount());

      // Handle cpu
      // Just merge the histogram received from delegate into the existing histogram
      Histogram protoHistogram = newCpuHistogram();
      protoHistogram.loadFromCheckPoint(protoToCheckpoint(containerStateProto.getCpuHistogram()));
      containerState.getCpuHistogram().merge(protoHistogram);

      // Handle memory
      // Treat the memoryPeak received from delegate as a single memory sample to be added to existing histogram
      Instant memoryTs = HTimestamps.toInstant(containerStateProto.getMemoryPeakTime());
      if (containerState.getWindowEnd() == null) {
        containerState.setWindowEnd(memoryTs);
      }

      boolean addNewPeak = false;
      if (memoryTs.isBefore(containerState.getWindowEnd())) {
        long oldMaxMem = containerState.getMemoryPeak();
        if (oldMaxMem != 0 && containerStateProto.getMemoryPeak() > oldMaxMem) {
          containerState.getMemoryHistogram().subtractSample(oldMaxMem, 1.0, containerState.getWindowEnd());
          addNewPeak = true;
        }
      } else {
        // Shift the memory aggregation window to the next interval.
        Duration shift = truncate(between(containerState.getWindowEnd(), memoryTs), MEMORY_AGGREGATION_INTERVAL)
                             .plus(MEMORY_AGGREGATION_INTERVAL);
        containerState.setWindowEnd(containerState.getWindowEnd().plus(shift));
        containerState.setMemoryPeak(0);
        addNewPeak = true;
      }
      if (addNewPeak) {
        containerState.getMemoryHistogram().addSample(
            containerStateProto.getMemoryPeak(), 1.0, containerState.getWindowEnd());
        containerState.setMemoryPeak(containerStateProto.getMemoryPeak());
      }
    }
  }

  private void updateContainerStateMapSingleDay(
      Map<String, ContainerStateV2> containerStateMap, ContainerStateProto containerStateProto) {
    String containerName = containerStateProto.getContainerName();
    ContainerStateV2 containerState = containerStateMap.get(containerName);
    Instant firstSampleStart = HTimestamps.toInstant(containerStateProto.getFirstSampleStart());
    Instant lastSampleStart = HTimestamps.toInstant(containerStateProto.getLastSampleStart());
    if (containerState != null && containerState.getVersion() >= containerStateProto.getVersion()
        && firstSampleStart.isBefore(containerState.getLastSampleStart())) {
      log.debug("Skipping sample {} as interval already covered", containerStateProto);
    } else {
      if (containerState == null || containerState.getVersion() < containerStateProto.getVersion()) {
        // First sample seen for this container, or new version of proto for this container
        // Re-initialize containerState
        containerState = new ContainerStateV2();
        containerState.setFirstSampleStart(firstSampleStart);
        containerStateMap.put(containerName, containerState);
        containerState.setVersion(containerStateProto.getVersion());
      }
      containerState.setLastUpdateTime(Instant.now());
      containerState.setLastSampleStart(lastSampleStart);
      containerState.setTotalSamplesCount(
          containerState.getTotalSamplesCount() + containerStateProto.getTotalSamplesCount());

      // Handle cpu
      // Just merge the histogram received from delegate into the existing histogram
      Histogram protoHistogram = newCpuHistogramV2();
      protoHistogram.loadFromCheckPoint(protoToCheckpoint(containerStateProto.getCpuHistogramV2()));
      containerState.getCpuHistogram().merge(protoHistogram);

      // Just store a single value for memory - max memory for the day
      containerState.setMemoryPeak(Math.max(containerStateProto.getMemoryPeak(), containerState.getMemoryPeak()));
    }
  }

  @NotNull
  private WorkloadState getWorkloadState(ResourceId workloadId) {
    return new WorkloadState(workloadRecommendationDao.fetchRecommendationForWorkload(workloadId));
  }

  private WorkloadStateV2 getWorkloadStateV2(ResourceId workloadId) {
    return new WorkloadStateV2(
        workloadRecommendationDao.fetchPartialRecommendationHistogramForWorkload(workloadId, jobStartDate));
  }

  /**
   *  Update the cached changes into DB.
   */
  private void persistChanges(Map<ResourceId, WorkloadState> workloadToRecommendation) {
    workloadToRecommendation.forEach((workloadId, workloadState) -> {
      Map<String, ContainerState> containerStates = workloadState.getContainerStateMap();
      Map<String, ContainerCheckpoint> updatedContainerCheckpoints = containerStates.entrySet().stream().collect(
          Collectors.toMap(Map.Entry::getKey, cse -> cse.getValue().toContainerCheckpoint()));
      K8sWorkloadRecommendation recommendation = workloadState.getRecommendation();
      recommendation.setContainerCheckpoints(updatedContainerCheckpoints);
      recommendation.setDirty(true);
      Instant lastReceivedUtilDataTimestamp = updatedContainerCheckpoints.values()
                                                  .stream()
                                                  .map(ContainerCheckpoint::getLastSampleStart)
                                                  .max(Comparator.naturalOrder())
                                                  .orElse(Instant.EPOCH);
      recommendation.setLastReceivedUtilDataAt(lastReceivedUtilDataTimestamp);
      workloadRecommendationDao.save(recommendation);
    });
  }

  /**
   *  Update the cached changes into DB.
   */
  private void persistChangesV2(Map<ResourceId, WorkloadStateV2> workloadToPartialHistogram) {
    workloadToPartialHistogram.forEach((workloadId, workloadState) -> {
      Map<String, ContainerStateV2> containerStatesV2 = workloadState.getContainerStateMap();
      Map<String, ContainerCheckpoint> containerCheckpointsV2 = containerStatesV2.entrySet().stream().collect(
          Collectors.toMap(Map.Entry::getKey, cse -> cse.getValue().toContainerCheckpoint()));
      PartialRecommendationHistogram partialRecommendationHistogram = workloadState.getHistogram();
      partialRecommendationHistogram.setContainerCheckpoints(containerCheckpointsV2);
      workloadRecommendationDao.save(partialRecommendationHistogram);
    });
  }

  @NonNull
  ResourceId fetchWorkloadIdForPod(ResourceId pod) {
    InstanceData podInstance =
        instanceDataDao.getK8sPodInstance(pod.getAccountId(), pod.getClusterId(), pod.getNamespace(), pod.getName());
    if (podInstance == null) {
      log.warn("Could not find pod {}/{} in instanceData for clusterId={}", pod.getNamespace(), pod.getName(),
          pod.getClusterId());
      return NOT_FOUND;
    }
    String workloadName = getValueForKeyFromInstanceMetaData(InstanceMetaDataConstants.WORKLOAD_NAME, podInstance);
    String workloadType = getValueForKeyFromInstanceMetaData(InstanceMetaDataConstants.WORKLOAD_TYPE, podInstance);
    return ResourceId.builder()
        .accountId(pod.getAccountId())
        .clusterId(pod.getClusterId())
        .namespace(pod.getNamespace())
        .name(workloadName)
        .kind(workloadType)
        .build();
  }
}
