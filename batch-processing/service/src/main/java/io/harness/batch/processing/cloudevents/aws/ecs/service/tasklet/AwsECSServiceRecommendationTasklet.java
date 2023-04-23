/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.cloudevents.aws.ecs.service.tasklet;

import static io.harness.ccm.RecommenderUtils.EPSILON;
import static io.harness.ccm.commons.utils.ResourceAmountUtils.convertToReadableForm;
import static io.harness.ccm.commons.utils.ResourceAmountUtils.makeResourceMap;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static software.wings.graphql.datafetcher.ce.recommendation.entity.ResourceRequirement.CPU;
import static software.wings.graphql.datafetcher.ce.recommendation.entity.ResourceRequirement.MEMORY;

import static io.kubernetes.client.custom.Quantity.Format.BINARY_SI;
import static java.math.RoundingMode.HALF_UP;
import static java.util.Optional.ofNullable;

import io.harness.batch.processing.billing.timeseries.service.impl.BillingDataServiceImpl;
import io.harness.batch.processing.billing.timeseries.service.impl.UtilizationDataServiceImpl;
import io.harness.batch.processing.ccm.CCMJobConstants;
import io.harness.batch.processing.cloudevents.aws.ecs.service.CEClusterDao;
import io.harness.batch.processing.cloudevents.aws.ecs.service.util.ClusterIdAndServiceArn;
import io.harness.batch.processing.cloudevents.aws.ecs.service.util.ECSUtilizationData;
import io.harness.batch.processing.dao.intfc.ECSServiceDao;
import io.harness.ccm.commons.beans.JobConstants;
import io.harness.ccm.commons.beans.Resource;
import io.harness.ccm.commons.dao.recommendation.ECSRecommendationDAO;
import io.harness.ccm.commons.entities.billing.CECluster;
import io.harness.ccm.commons.entities.ecs.ECSService;
import io.harness.ccm.commons.entities.ecs.recommendation.ECSPartialRecommendationHistogram;
import io.harness.ccm.commons.entities.ecs.recommendation.ECSServiceRecommendation;
import io.harness.ccm.graphql.core.recommendation.RecommendationsIgnoreListService;
import io.harness.ccm.graphql.core.recommendation.fargate.CpuMillsAndMemoryBytes;
import io.harness.ccm.graphql.core.recommendation.fargate.FargateResourceValues;
import io.harness.ccm.views.helper.AwsAccountFieldHelper;
import io.harness.ff.FeatureFlagService;
import io.harness.histogram.Histogram;
import io.harness.histogram.HistogramCheckpoint;
import io.harness.histogram.HistogramImpl;
import io.harness.histogram.LinearHistogramOptions;

import software.wings.graphql.datafetcher.ce.recommendation.entity.Cost;

import com.amazonaws.services.ecs.model.LaunchType;
import com.cronutils.utils.StringUtils;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.inject.Singleton;
import io.kubernetes.client.custom.Quantity;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.CollectionUtils;

@Slf4j
@Singleton
public class AwsECSServiceRecommendationTasklet implements Tasklet {
  @Autowired private CEClusterDao ceClusterDao;
  @Autowired private ECSServiceDao ecsServiceDao;
  @Autowired private UtilizationDataServiceImpl utilizationDataService;
  @Autowired private ECSRecommendationDAO ecsRecommendationDAO;
  @Autowired private BillingDataServiceImpl billingDataService;
  @Autowired private FeatureFlagService featureFlagService;
  @Autowired private FargateResourceValues fargateResourceValues;
  @Autowired private RecommendationsIgnoreListService ignoreListService;
  @Autowired private AwsAccountFieldHelper awsAccountFieldHelper;

  private static final int BATCH_SIZE = 20;
  private static final int MAX_UTILIZATION_WEIGHT = 1;
  private static final int RECOMMENDATION_FOR_DAYS = 7;
  private static final Set<Integer> requiredPercentiles = ImmutableSet.of(50, 80, 90, 95, 99, 100);
  private static final String PERCENTILE_KEY = "p%d";
  public static final Duration RECOMMENDATION_TTL = Duration.ofDays(30);

  @Override
  public RepeatStatus execute(StepContribution stepContribution, ChunkContext chunkContext) throws Exception {
    final JobConstants jobConstants = CCMJobConstants.fromContext(chunkContext);
    String accountId = jobConstants.getAccountId();
    Instant startTime = Instant.ofEpochMilli(jobConstants.getJobStartTime());
    Instant endTime = Instant.ofEpochMilli(jobConstants.getJobEndTime());
    // Get all clusters for current account
    Map<String, CECluster> ceClusters = ceClusterDao.getClusterIdMapping(accountId);
    if (CollectionUtils.isEmpty(ceClusters)) {
      return null;
    }
    List<String> clusterIds = new ArrayList<>(ceClusters.keySet());

    for (List<String> ceClustersPartition : Lists.partition(clusterIds, BATCH_SIZE)) {
      // Get utilization data for all clusters in this batch for a day
      Map<ClusterIdAndServiceArn, List<ECSUtilizationData>> utilMap =
          utilizationDataService.getUtilizationDataForECSClusters(
              accountId, ceClustersPartition, startTime.toString(), endTime.toString());

      // Fetch resource for services
      Map<String, ECSService> ecsServiceMap = ecsServiceDao.fetchServices(
          accountId, utilMap.keySet().stream().map(ClusterIdAndServiceArn::getServiceArn).collect(Collectors.toList()));

      for (ClusterIdAndServiceArn clusterIdAndServiceArn : utilMap.keySet()) {
        String clusterId = clusterIdAndServiceArn.getClusterId();
        String clusterName = ceClusters.get(clusterId).getClusterName();
        String serviceArn = clusterIdAndServiceArn.getServiceArn();
        String serviceName = serviceNameFromServiceArn(serviceArn);
        if (!ecsServiceMap.containsKey(serviceArn)) {
          log.debug("Skipping ECS recommendation as service info is not present for accountId: {}, service arn: {}",
              accountId, serviceArn);
          continue;
        }
        ECSService ecsService = ecsServiceMap.get(clusterIdAndServiceArn.getServiceArn());
        String awsAccountId = (ecsService.getAwsAccountId() == null) ? "" : ecsService.getAwsAccountId();
        if (StringUtils.isEmpty(awsAccountId)) {
          awsAccountId = ceClusters.get(clusterId).getInfraAccountId();
        }
        LaunchType launchType = ecsService.getLaunchType();
        Resource resource = ecsService.getResource();
        if (resource.getCpuUnits().equals(0.0) || resource.getMemoryMb().equals(0.0)) {
          log.debug("Skipping ECS recommendation as resource value is zero for accountId : {}, service arn: {}",
              accountId, serviceArn);
          continue;
        }
        long cpuMilliUnits = resource.getCpuUnits().longValue() * 1024L;
        long memoryBytes = resource.getMemoryMb().longValue() * 1024L * 1024L;
        List<ECSUtilizationData> utilData = utilMap.get(clusterIdAndServiceArn);
        // Create Partial Histogram for a day for this service
        ECSPartialRecommendationHistogram partialRecommendationHistogram = getPartialRecommendation(accountId,
            clusterId, clusterName, serviceArn, serviceName, startTime, utilData, cpuMilliUnits, memoryBytes);
        ecsRecommendationDAO.savePartialRecommendation(partialRecommendationHistogram);

        // Get Partial recommendations for last 7 days
        List<ECSPartialRecommendationHistogram> partialHistograms =
            ecsRecommendationDAO.fetchPartialRecommendationHistograms(accountId, clusterId, serviceArn,
                startTime.minus(Duration.ofDays(RECOMMENDATION_FOR_DAYS)), startTime.minusSeconds(1));
        // Add today's histogram to the list
        partialHistograms.add(partialRecommendationHistogram);

        // Create ECSServiceRecommendation
        ECSServiceRecommendation recommendation = getRecommendation(accountId, awsAccountId, clusterId, clusterName,
            serviceName, serviceArn, launchType, cpuMilliUnits, memoryBytes);

        // Merge partial recommendations and compute recommendations
        mergePartialRecommendations(partialHistograms, recommendation, cpuMilliUnits, memoryBytes);
        recommendation.setCurrentResourceRequirements(
            convertToReadableForm(makeResourceMap(cpuMilliUnits, memoryBytes), BINARY_SI));
        recommendation.setLastComputedRecommendationAt(startTime);
        recommendation.setLastUpdateTime(startTime);
        recommendation.setVersion(1);

        // Estimate savings
        Cost lastDayCost = billingDataService.getECSServiceLastAvailableDayCost(accountId, clusterId, serviceName,
            startTime.minus(Duration.ofDays(RECOMMENDATION_FOR_DAYS)).truncatedTo(ChronoUnit.DAYS));
        if (lastDayCost != null) {
          recommendation.setLastDayCost(lastDayCost);
          recommendation.setLastDayCostAvailable(true);
          BigDecimal monthlySavings = estimateMonthlySavings(recommendation.getCurrentResourceRequirements(),
              recommendation.getPercentileBasedResourceRecommendation().get(String.format(PERCENTILE_KEY, 95)),
              lastDayCost);
          recommendation.setEstimatedSavings(monthlySavings);
          recommendation.setValidRecommendation(true);
        } else {
          recommendation.setLastDayCostAvailable(false);
          recommendation.setValidRecommendation(false);
          log.debug("Unable to get lastDayCost for serviceArn: {}", serviceArn);
        }
        recommendation.setTtl(Instant.now().plus(RECOMMENDATION_TTL));
        recommendation.setDirty(false);

        // Save recommendation in mongo
        log.info("Saving ECS Recommendation: {}", recommendation);
        final ECSServiceRecommendation ecsServiceRecommendation =
            ecsRecommendationDAO.saveRecommendation(recommendation);
        final Double monthlyCost = calculateMonthlyCost(recommendation);
        final Double monthlySaving =
            ofNullable(recommendation.getEstimatedSavings()).map(BigDecimal::doubleValue).orElse(null);
        // Save recommendation in timescale
        String awsAccountIdAndName = awsAccountId;
        List<String> awsAccountIdAndNames =
            awsAccountFieldHelper.mergeAwsAccountNameWithValues(Collections.singletonList(awsAccountId), accountId);
        if (isNotEmpty(awsAccountIdAndNames)) {
          awsAccountIdAndName = awsAccountIdAndNames.get(0);
        }
        ecsRecommendationDAO.upsertCeRecommendation(ecsServiceRecommendation.getUuid(), accountId, awsAccountIdAndName,
            clusterName, serviceName, monthlyCost, monthlySaving, recommendation.shouldShowRecommendation(),
            recommendation.getLastReceivedUtilDataAt());
        ignoreListService.updateECSRecommendationState(
            ecsServiceRecommendation.getUuid(), accountId, clusterName, serviceName);
      }
    }

    return null;
  }

  ECSPartialRecommendationHistogram getPartialRecommendation(String accountId, String clusterId, String clusterName,
      String serviceArn, String serviceName, Instant startTime, List<ECSUtilizationData> utilData, long cpuMilliUnits,
      long memoryBytes) {
    return ECSPartialRecommendationHistogram.builder()
        .accountId(accountId)
        .clusterId(clusterId)
        .clusterName(clusterName)
        .serviceArn(serviceArn)
        .serviceName(serviceName)
        .date(startTime)
        .lastUpdateTime(startTime)
        .cpuHistogram(histogramCheckpointFromUtilData(utilData, cpuMilliUnits, CPU))
        .memoryHistogram(histogramCheckpointFromUtilData(utilData, memoryBytes, MEMORY))
        .firstSampleStart(utilData.isEmpty() ? null : utilData.get(0).getStartTime())
        .lastSampleStart(utilData.isEmpty() ? null : utilData.get(utilData.size() - 1).getStartTime())
        .totalSamplesCount(utilData.size())
        .windowEnd(utilData.isEmpty() ? null : utilData.get(utilData.size() - 1).getEndTime())
        .version(1)
        .build();
  }

  ECSServiceRecommendation getRecommendation(String accountId, String awsAccountId, String clusterId,
      String clusterName, String serviceName, String serviceArn, LaunchType launchType, long cpuMilliUnits,
      long memoryBytes) {
    return ECSServiceRecommendation.builder()
        .accountId(accountId)
        .awsAccountId(awsAccountId)
        .clusterId(clusterId)
        .clusterName(clusterName)
        .serviceName(serviceName)
        .serviceArn(serviceArn)
        .launchType(launchType)
        .cpuHistogram(newHistogram(cpuMilliUnits).saveToCheckpoint())
        .memoryHistogram(newHistogram(memoryBytes).saveToCheckpoint())
        .build();
  }

  void mergePartialRecommendations(List<ECSPartialRecommendationHistogram> partialHistograms,
      ECSServiceRecommendation recommendation, long cpuMilliUnits, long memoryBytes) {
    Histogram cpuHistogram = newHistogram(cpuMilliUnits);
    Histogram memoryHistogram = newHistogram(memoryBytes);
    Instant firstSampleStart = Instant.now();
    Instant lastSampleStart = Instant.EPOCH;
    Instant windowEnd = Instant.EPOCH;
    int totalSamplesCount = 0;
    long memoryPeak = 0;
    for (ECSPartialRecommendationHistogram partialHistogram : partialHistograms) {
      Histogram partialCpuHistogram = newHistogram(cpuMilliUnits);
      partialCpuHistogram.loadFromCheckPoint(partialHistogram.getCpuHistogram());
      cpuHistogram.merge(partialCpuHistogram);
      Histogram partialMemoryHistogram = newHistogram(memoryBytes);
      partialMemoryHistogram.loadFromCheckPoint(partialHistogram.getMemoryHistogram());
      memoryHistogram.merge(partialMemoryHistogram);
      if (partialHistogram.getFirstSampleStart() != null
          && partialHistogram.getFirstSampleStart().isBefore(firstSampleStart)) {
        firstSampleStart = partialHistogram.getFirstSampleStart();
      }
      if (partialHistogram.getLastSampleStart() != null
          && partialHistogram.getLastSampleStart().isAfter(lastSampleStart)) {
        lastSampleStart = partialHistogram.getLastSampleStart();
      }
      if (partialHistogram.getWindowEnd() != null && partialHistogram.getWindowEnd().isAfter(windowEnd)) {
        windowEnd = partialHistogram.getWindowEnd();
      }
      totalSamplesCount += partialHistogram.getTotalSamplesCount();
      memoryPeak = Math.max(partialHistogram.getMemoryPeak(), memoryPeak);
    }
    recommendation.setCpuHistogram(cpuHistogram.saveToCheckpoint());
    recommendation.setMemoryHistogram(memoryHistogram.saveToCheckpoint());
    recommendation.setMemoryPeak(memoryPeak);
    recommendation.setTotalSamplesCount(totalSamplesCount);
    recommendation.setFirstSampleStart(firstSampleStart);
    recommendation.setLastSampleStart(lastSampleStart);
    recommendation.setWindowEnd(windowEnd);
    recommendation.setLastReceivedUtilDataAt(lastSampleStart);
    recommendation.setNumDays(partialHistograms.size());

    // Compute percentile based recommendation
    Map<String, Map<String, String>> computedPercentiles = new HashMap<>();
    for (Integer percentile : requiredPercentiles) {
      long cpuAmount = (long) cpuHistogram.getPercentile(((double) percentile) / 100.0);
      long memoryAmount = (long) memoryHistogram.getPercentile(((double) percentile) / 100.0);
      if (recommendation.getLaunchType() != null && recommendation.getLaunchType().equals(LaunchType.FARGATE)) {
        CpuMillsAndMemoryBytes resourceValues = fargateResourceValues.get(cpuAmount, memoryAmount);
        if (null != resourceValues) {
          cpuAmount = resourceValues.getCpuMilliUnits();
          memoryAmount = resourceValues.getMemoryBytes();
        }
      }
      computedPercentiles.put(String.format(PERCENTILE_KEY, percentile),
          convertToReadableForm(makeResourceMap(cpuAmount, memoryAmount), BINARY_SI));
    }
    recommendation.setPercentileBasedResourceRecommendation(computedPercentiles);
  }

  BigDecimal estimateMonthlySavings(Map<String, String> current, Map<String, String> recommendation, Cost lastDayCost) {
    BigDecimal cpuChangePercent = resourceChangePercent(current, recommendation, CPU);
    BigDecimal memoryChangePercent = resourceChangePercent(current, recommendation, MEMORY);
    return getMonthlySavings(lastDayCost, cpuChangePercent, memoryChangePercent);
  }

  public static BigDecimal getMonthlySavings(
      Cost lastDayCost, BigDecimal cpuChangePercent, BigDecimal memoryChangePercent) {
    BigDecimal monthlySavings = null;
    if (cpuChangePercent != null || memoryChangePercent != null) {
      BigDecimal costChangeForDay = BigDecimal.ZERO;
      if (cpuChangePercent != null && lastDayCost.getCpu() != null) {
        costChangeForDay = costChangeForDay.add(cpuChangePercent.multiply(lastDayCost.getCpu()));
      }
      if (memoryChangePercent != null && lastDayCost.getMemory() != null) {
        costChangeForDay = costChangeForDay.add(memoryChangePercent.multiply(lastDayCost.getMemory()));
      }
      monthlySavings = costChangeForDay.multiply(BigDecimal.valueOf(-30)).setScale(2, HALF_UP);
    }
    return monthlySavings;
  }

  static BigDecimal resourceChangePercent(
      Map<String, String> current, Map<String, String> recommendation, String resource) {
    BigDecimal currentValue = getResourceValue(current, resource, BigDecimal.ZERO);
    BigDecimal recommendedValue = getResourceValue(recommendation, resource, BigDecimal.ZERO);
    if (currentValue.compareTo(BigDecimal.ZERO) != 0) {
      BigDecimal change = recommendedValue.subtract(currentValue);
      return change.setScale(3, HALF_UP).divide(currentValue, HALF_UP);
    }
    return null;
  }

  static BigDecimal getResourceValue(
      Map<String, String> resourceRequirement, String resource, BigDecimal defaultValue) {
    return ofNullable(resourceRequirement)
        .map(r -> r.get(resource))
        .map(Quantity::fromString)
        .map(Quantity::getNumber)
        .orElse(defaultValue);
  }

  private HistogramCheckpoint histogramCheckpointFromUtilData(
      List<ECSUtilizationData> utilizationForDay, long maxUnits, String resourceType) {
    Histogram histogram = histogramFromUtilData(utilizationForDay, maxUnits, resourceType);
    return histogram.saveToCheckpoint();
  }

  private Histogram histogramFromUtilData(
      List<ECSUtilizationData> utilizationForDay, long maxUnits, String resourceType) {
    Histogram histogram = newHistogram(maxUnits);
    // utilization data is in percentage
    if (resourceType.equals(CPU)) {
      for (ECSUtilizationData utilizationForHour : utilizationForDay) {
        histogram.addSample(utilizationForHour.getMaxCpuUtilization() * maxUnits, MAX_UTILIZATION_WEIGHT,
            utilizationForHour.getStartTime());
      }
    } else if (resourceType.equals(MEMORY)) {
      for (ECSUtilizationData utilizationForHour : utilizationForDay) {
        histogram.addSample(utilizationForHour.getMaxMemoryUtilization() * maxUnits, MAX_UTILIZATION_WEIGHT,
            utilizationForHour.getStartTime());
      }
    }
    return histogram;
  }

  private static String serviceNameFromServiceArn(String serviceArn) {
    return serviceArn.substring(serviceArn.lastIndexOf('/') + 1);
  }

  private static Histogram newHistogram(long maxUnits) {
    // Histogram will have 1000 buckets
    return new HistogramImpl(new LinearHistogramOptions(maxUnits, maxUnits / 1000.0, EPSILON));
  }

  @Nullable
  private static Double calculateMonthlyCost(@NonNull ECSServiceRecommendation recommendation) {
    if (recommendation.isLastDayCostAvailable()) {
      return BigDecimal.ZERO.add(recommendation.getLastDayCost().getCpu())
          .add(recommendation.getLastDayCost().getMemory())
          .multiply(BigDecimal.valueOf(30))
          .setScale(2, BigDecimal.ROUND_HALF_EVEN)
          .doubleValue();
    }
    return null;
  }
}
