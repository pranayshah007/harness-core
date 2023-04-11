/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.config.k8s.recommendation;

import static io.harness.batch.processing.cloudevents.aws.ecs.service.tasklet.AwsECSServiceRecommendationTasklet.getMonthlySavings;
import static io.harness.batch.processing.config.k8s.recommendation.estimators.ContainerResourceRequirementEstimators.burstableRecommender;
import static io.harness.batch.processing.config.k8s.recommendation.estimators.ContainerResourceRequirementEstimators.customRecommender;
import static io.harness.batch.processing.config.k8s.recommendation.estimators.ContainerResourceRequirementEstimators.guaranteedRecommender;
import static io.harness.batch.processing.config.k8s.recommendation.estimators.ContainerResourceRequirementEstimators.recommendedRecommender;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static software.wings.graphql.datafetcher.ce.recommendation.entity.ResourceRequirement.CPU;
import static software.wings.graphql.datafetcher.ce.recommendation.entity.ResourceRequirement.MEMORY;

import static java.math.RoundingMode.HALF_UP;
import static java.time.Duration.between;
import static java.util.Collections.emptyMap;
import static java.util.Optional.ofNullable;

import io.harness.batch.processing.service.intfc.WorkloadRepository;
import io.harness.batch.processing.tasklet.support.K8sLabelServiceInfoFetcher;
import io.harness.batch.processing.tasklet.util.ClusterHelper;
import io.harness.ccm.commons.beans.recommendation.ResourceId;
import io.harness.ccm.commons.dao.recommendation.RecommendationCrudService;
import io.harness.ccm.commons.entities.k8s.K8sWorkload;
import io.harness.ccm.commons.entities.k8s.recommendation.K8sWorkloadRecommendation;
import io.harness.ccm.commons.entities.k8s.recommendation.PartialRecommendationHistogram;
import io.harness.ccm.commons.utils.ResourceAmountUtils;
import io.harness.ccm.graphql.core.recommendation.RecommendationsIgnoreListService;
import io.harness.histogram.Histogram;

import software.wings.graphql.datafetcher.ce.recommendation.entity.ContainerRecommendation;
import software.wings.graphql.datafetcher.ce.recommendation.entity.Cost;
import software.wings.graphql.datafetcher.ce.recommendation.entity.PartialHistogramAggragator;
import software.wings.graphql.datafetcher.ce.recommendation.entity.ResourceRequirement;

import com.cronutils.utils.Preconditions;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import io.kubernetes.client.custom.Quantity;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.ItemWriter;

@Slf4j
class ComputedRecommendationWriter implements ItemWriter<K8sWorkloadRecommendation> {
  public static final Duration RECOMMENDATION_TTL = Duration.ofDays(15);

  private static final long podMinCpuMilliCores = 25L;
  private static final long podMinMemoryBytes = 250_000_000L;
  private static final int cpuScale = 3;
  private static final int memoryScale = 1;
  private static final int costScale = 3;
  private static final Set<Integer> requiredPercentiles = ImmutableSet.of(50, 80, 90, 95, 99, 100);
  private static final String PERCENTILE_KEY = "p%d";

  private final WorkloadRecommendationDao workloadRecommendationDao;
  private final WorkloadCostService workloadCostService;
  private final WorkloadRepository workloadRepository;
  private final K8sLabelServiceInfoFetcher k8sLabelServiceInfoFetcher;
  private final RecommendationCrudService recommendationCrudService;
  private final ClusterHelper clusterHelper;
  private final RecommendationsIgnoreListService ignoreListService;

  private final Instant jobStartDate;

  ComputedRecommendationWriter(WorkloadRecommendationDao workloadRecommendationDao,
      WorkloadCostService workloadCostService, WorkloadRepository workloadRepository,
      K8sLabelServiceInfoFetcher k8sLabelServiceInfoFetcher, RecommendationCrudService recommendationCrudService,
      ClusterHelper clusterHelper, RecommendationsIgnoreListService ignoreListService, Instant jobStartDate) {
    this.workloadRecommendationDao = workloadRecommendationDao;
    this.workloadCostService = workloadCostService;
    this.workloadRepository = workloadRepository;
    this.k8sLabelServiceInfoFetcher = k8sLabelServiceInfoFetcher;
    this.recommendationCrudService = recommendationCrudService;
    this.clusterHelper = clusterHelper;
    this.ignoreListService = ignoreListService;
    this.jobStartDate = jobStartDate;
  }

  void addHarnessSvcInfo(ResourceId workloadId, K8sWorkloadRecommendation k8sWorkloadRecommendation) {
    Map<String, String> labels = workloadRepository.getWorkload(workloadId)
                                     .map(K8sWorkload::getLabels)
                                     .map(K8sWorkload::decodeDotsInKey)
                                     .orElse(emptyMap());
    k8sLabelServiceInfoFetcher.fetchHarnessServiceInfoFromCache(workloadId.getAccountId(), labels)
        .ifPresent(k8sWorkloadRecommendation::setHarnessServiceInfo);
  }

  @Override
  public void write(List<? extends K8sWorkloadRecommendation> items) {
    for (K8sWorkloadRecommendation recommendation : items) {
      if (!recommendation.isDirty()) {
        log.warn("Skipping as dirty flag is not set");
        continue;
      }
      ResourceId workloadId = ResourceId.builder()
                                  .accountId(recommendation.getAccountId())
                                  .clusterId(recommendation.getClusterId())
                                  .kind(recommendation.getWorkloadType())
                                  .namespace(recommendation.getNamespace())
                                  .name(recommendation.getWorkloadName())
                                  .build();
      addHarnessSvcInfo(workloadId, recommendation);
      Map<String, ContainerRecommendation> containerRecommendations =
          ofNullable(recommendation.getContainerRecommendations()).orElseGet(HashMap::new);
      recommendation.setContainerRecommendations(containerRecommendations);

      List<PartialRecommendationHistogram> partialRecommendationHistogramList =
          workloadRecommendationDao.fetchPartialRecommendationHistogramForWorkload(
              workloadId, jobStartDate.minus(Duration.ofDays(7)), jobStartDate);
      Map<String, Histogram> cpuHistograms = new HashMap<>();
      Map<String, Histogram> memoryHistograms = new HashMap<>();
      PartialHistogramAggragator.aggregateInto(partialRecommendationHistogramList, cpuHistograms, memoryHistograms);

      Map<String, ContainerState> containerStates = new WorkloadState(recommendation).getContainerStateMap();
      int minNumDays = Integer.MAX_VALUE;
      if (isNotEmpty(containerRecommendations)) {
        final double factor = 1.0 / containerRecommendations.size();
        final long containerMinCpuMilliCores = ResourceAmountUtils.scaleResourceAmount(podMinCpuMilliCores, factor);
        final long containerMinMemoryBytes = ResourceAmountUtils.scaleResourceAmount(podMinMemoryBytes, factor);
        for (Map.Entry<String, ContainerRecommendation> entry : containerRecommendations.entrySet()) {
          String containerName = entry.getKey();
          ContainerRecommendation containerRecommendation = entry.getValue();
          ContainerState containerState = containerStates.get(containerName);
          if (containerState != null) {
            ResourceRequirement current = containerRecommendation.getCurrent();
            long curCpuMilliCores = Optional.ofNullable(current)
                                        .map(ResourceRequirement::getRequests)
                                        .map(s -> s.get(CPU))
                                        .map(Quantity::fromString)
                                        .map(Quantity::getNumber)
                                        .map(BigDecimal::doubleValue)
                                        .map(ResourceAmountUtils::cpuAmountFromCores)
                                        .orElse(containerMinCpuMilliCores);
            long curMemoryBytes = Optional.ofNullable(current)
                                      .map(ResourceRequirement::getRequests)
                                      .map(s -> s.get(MEMORY))
                                      .map(Quantity::fromString)
                                      .map(Quantity::getNumber)
                                      .map(BigDecimal::doubleValue)
                                      .map(ResourceAmountUtils::memoryAmountFromBytes)
                                      .orElse(containerMinMemoryBytes);
            // use cur if it's less than min.
            Map<String, Long> minContainerResources =
                ResourceAmountUtils.makeResourceMap(Math.min(containerMinCpuMilliCores, curCpuMilliCores),
                    Math.min(containerMinMemoryBytes, curMemoryBytes));
            ResourceRequirement burstable =
                burstableRecommender(minContainerResources).getEstimatedResourceRequirements(containerState);
            ResourceRequirement guaranteed =
                guaranteedRecommender(minContainerResources).getEstimatedResourceRequirements(containerState);
            ResourceRequirement recommended =
                recommendedRecommender(minContainerResources).getEstimatedResourceRequirements(containerState);

            Map<String, ResourceRequirement> computedPercentiles = new HashMap<>();

            Histogram cpuHistogram = cpuHistograms.get(containerName);
            Histogram memoryHistogram = memoryHistograms.get(containerName);

            // assuming partialHistogram may not have the data for some containerName
            if (cpuHistogram != null && memoryHistogram != null) {
              // container state constructed from last 7 days partialHistogram aggregated data
              ContainerState containerStateFromPartialHistogram = new ContainerState();
              containerStateFromPartialHistogram.setCpuHistogram(cpuHistogram);
              containerStateFromPartialHistogram.setMemoryHistogram(memoryHistogram);

              for (Integer percentile : requiredPercentiles) {
                computedPercentiles.put(String.format(PERCENTILE_KEY, percentile),
                    customRecommender(minContainerResources, percentile / 100.0)
                        .getEstimatedResourceRequirements(containerStateFromPartialHistogram));
              }
            } else {
              log.warn("partialHistogram does not have the data for containerName:{}, workloadId: {}", containerName,
                  workloadId);
            }

            if (current != null) {
              burstable = copyExtendedResources(current, burstable);
              guaranteed = copyExtendedResources(current, guaranteed);
              recommended = copyExtendedResources(current, recommended);

              for (Integer percentile : requiredPercentiles) {
                computedPercentiles.computeIfPresent(
                    String.format(PERCENTILE_KEY, percentile), (k, v) -> copyExtendedResources(current, v));
              }
            }
            containerRecommendation.setBurstable(burstable);
            containerRecommendation.setGuaranteed(guaranteed);
            containerRecommendation.setRecommended(recommended);
            containerRecommendation.setPercentileBased(computedPercentiles);
            int days =
                (int) between(containerState.getFirstSampleStart(), containerState.getLastSampleStart()).toDays();
            // upper bound by 8 days
            days = Math.min(days, 8);
            containerRecommendation.setNumDays(days);
            minNumDays = Math.min(minNumDays, days);
            containerRecommendation.setTotalSamplesCount(containerState.getTotalSamplesCount());
          }
        }
      }
      recommendation.setNumDays(minNumDays == Integer.MAX_VALUE ? 0 : minNumDays);
      Instant startInclusive = jobStartDate.minus(Duration.ofDays(7));
      Cost lastDayCost = workloadCostService.getLastAvailableDayCost(workloadId, startInclusive);
      BigDecimal monthlySavings = null;
      if (lastDayCost != null) {
        recommendation.setLastDayCost(lastDayCost);
        recommendation.setLastDayCostAvailable(true);

        setContainerLevelCost(containerRecommendations, lastDayCost);

        monthlySavings = estimateMonthlySavings(containerRecommendations, lastDayCost);
        recommendation.setEstimatedSavings(monthlySavings);
      } else {
        recommendation.setLastDayCostAvailable(false);
        log.debug("Unable to get lastDayCost for workload {}", workloadId);
      }
      recommendation.setTtl(Instant.now().plus(RECOMMENDATION_TTL));
      recommendation.setDirty(false);

      final String uuid = workloadRecommendationDao.save(recommendation);
      Preconditions.checkNotNullNorEmpty(uuid, "unexpected, uuid can't be null or empty");

      final String clusterName = clusterHelper.fetchClusterName(workloadId.getClusterId());
      recommendationCrudService.upsertWorkloadRecommendation(uuid, workloadId, clusterName, recommendation);

      ignoreListService.updateWorkloadRecommendationState(uuid, recommendation.getAccountId(), clusterName,
          recommendation.getNamespace(), recommendation.getWorkloadName());
    }
  }

  @VisibleForTesting
  public void setContainerLevelCost(Map<String, ContainerRecommendation> containerRecommendationMap, Cost lastDayCost) {
    BigDecimal totalCpu = totalCurrentResourceValue(containerRecommendationMap, CPU);
    BigDecimal totalMemory = totalCurrentResourceValue(containerRecommendationMap, MEMORY);

    if (BigDecimal.ZERO.compareTo(totalCpu) == 0 || BigDecimal.ZERO.compareTo(totalMemory) == 0) {
      return;
    }

    for (ContainerRecommendation containerRecommendation : containerRecommendationMap.values()) {
      if (containerRecommendationMap.size() == 1) {
        containerRecommendation.setLastDayCost(lastDayCost);
      } else {
        BigDecimal containerCpu = getResourceValue(containerRecommendation.getCurrent(), CPU, BigDecimal.ZERO);
        BigDecimal containerMemory = getResourceValue(containerRecommendation.getCurrent(), MEMORY, BigDecimal.ZERO);

        BigDecimal fractionCpu = containerCpu.setScale(cpuScale, HALF_UP).divide(totalCpu, costScale, HALF_UP);
        BigDecimal fractionMemory =
            containerMemory.setScale(memoryScale, HALF_UP).divide(totalMemory, costScale, HALF_UP);

        Cost containerCost = Cost.builder()
                                 .cpu(lastDayCost.getCpu().multiply(fractionCpu))
                                 .memory(lastDayCost.getMemory().multiply(fractionMemory))
                                 .build();

        containerRecommendation.setLastDayCost(containerCost);
      }
    }
  }

  private static Map<String, String> extendedResourcesMap(Map<String, String> resourceMap) {
    ImmutableSet<String> standardResources = ImmutableSet.of("cpu", "memory");
    return ofNullable(resourceMap)
        .orElse(emptyMap())
        .entrySet()
        .stream()
        .filter(e -> !standardResources.contains(e.getKey()))
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  static ResourceRequirement copyExtendedResources(ResourceRequirement current, ResourceRequirement recommended) {
    HashMap<String, String> mergedRequests = new HashMap<>(extendedResourcesMap(current.getRequests()));
    mergedRequests.putAll(ofNullable(recommended.getRequests()).orElse(emptyMap()));
    HashMap<String, String> mergedLimits = new HashMap<>(extendedResourcesMap(current.getLimits()));
    mergedLimits.putAll(ofNullable(recommended.getLimits()).orElse(emptyMap()));
    return ResourceRequirement.builder().requests(mergedRequests).limits(mergedLimits).build();
  }

  @Nullable
  BigDecimal estimateMonthlySavings(
      Map<String, ContainerRecommendation> containerRecommendations, @NotNull final Cost lastDayCost) {
    /*
     we have last day's cost for the workload for cpu & memory.
     find percentage diff at workload level, and multiply by the last day's cost to get dailyDiff
     multiply by -30 to convert dailyDiff to  monthly savings.
    */
    BigDecimal cpuChangePercent = resourceChangePercent(containerRecommendations, CPU);
    BigDecimal memoryChangePercent = resourceChangePercent(containerRecommendations, MEMORY);
    return getMonthlySavings(lastDayCost, cpuChangePercent, memoryChangePercent);
  }

  /**
   *  Get the percentage change in a resource between current and recommended for the pod, null if un-computable.
   */
  static BigDecimal resourceChangePercent(
      Map<String, ContainerRecommendation> containerRecommendations, String resource) {
    BigDecimal resourceCurrent = BigDecimal.ZERO;
    BigDecimal resourceChange = BigDecimal.ZERO;
    boolean atLeastOneContainerComputable = false;
    for (ContainerRecommendation containerRecommendation : containerRecommendations.values()) {
      BigDecimal current = getResourceValue(containerRecommendation.getCurrent(), resource, null);

      ResourceRequirement recommendedResource = containerRecommendation.getGuaranteed();
      if (containerRecommendation.getPercentileBased() != null
          && containerRecommendation.getPercentileBased().containsKey(String.format(PERCENTILE_KEY, 95))) {
        recommendedResource = containerRecommendation.getPercentileBased().get(String.format(PERCENTILE_KEY, 95));
      }
      BigDecimal recommended = getResourceValue(recommendedResource, resource, null);

      if (current != null && recommended != null) {
        resourceChange = resourceChange.add(recommended.subtract(current));
        resourceCurrent = resourceCurrent.add(current);
        atLeastOneContainerComputable = true;
      }
    }
    if (atLeastOneContainerComputable && resourceCurrent.compareTo(BigDecimal.ZERO) != 0) {
      return resourceChange.setScale(3, HALF_UP).divide(resourceCurrent, HALF_UP);
    }
    return null;
  }

  @NonNull
  static BigDecimal totalCurrentResourceValue(
      Map<String, ContainerRecommendation> containerRecommendations, String resource) {
    BigDecimal totalResource = BigDecimal.ZERO;

    for (ContainerRecommendation containerRecommendation : containerRecommendations.values()) {
      BigDecimal current = getResourceValue(containerRecommendation.getCurrent(), resource, BigDecimal.ZERO);
      totalResource = totalResource.setScale(4, HALF_UP).add(current);
    }

    return totalResource;
  }

  static BigDecimal getResourceValue(
      ResourceRequirement resourceRequirement, String resource, BigDecimal defaultValue) {
    return ofNullable(resourceRequirement)
        .map(ResourceRequirement::getRequests)
        .map(requests -> requests.get(resource))
        .map(Quantity::fromString)
        .map(Quantity::getNumber)
        .orElse(defaultValue);
  }
}
