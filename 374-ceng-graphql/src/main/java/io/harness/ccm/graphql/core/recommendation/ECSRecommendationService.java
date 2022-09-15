/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.graphql.core.recommendation;

import static io.harness.ccm.RecommenderUtils.EPSILON;

import static software.wings.graphql.datafetcher.ce.recommendation.entity.ResourceRequirement.CPU;
import static software.wings.graphql.datafetcher.ce.recommendation.entity.ResourceRequirement.MEMORY;

import static io.kubernetes.client.custom.Quantity.Format.DECIMAL_SI;
import static java.math.RoundingMode.HALF_UP;

import io.harness.ccm.commons.dao.recommendation.ECSRecommendationDAO;
import io.harness.ccm.commons.entities.ecs.recommendation.ECSPartialRecommendationHistogram;
import io.harness.ccm.commons.entities.ecs.recommendation.ECSServiceRecommendation;
import io.harness.ccm.commons.utils.StrippedHistogram;
import io.harness.ccm.graphql.core.recommendation.fargate.CpuMillsAndMemoryBytes;
import io.harness.ccm.graphql.core.recommendation.fargate.FargateResourceValues;
import io.harness.ccm.graphql.dto.recommendation.ContainerHistogramDTO.HistogramExp;
import io.harness.ccm.graphql.dto.recommendation.ECSRecommendationDTO;
import io.harness.histogram.Histogram;
import io.harness.histogram.HistogramCheckpoint;
import io.harness.histogram.HistogramImpl;
import io.harness.histogram.LinearHistogramOptions;

import com.amazonaws.services.ecs.model.LaunchType;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.kubernetes.client.custom.Quantity;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import lombok.NonNull;

@Singleton
public class ECSRecommendationService {
  private static final int NUMBER_OF_BUCKETS = 1000;
  @Inject private ECSRecommendationDAO ecsRecommendationDAO;
  @Inject private FargateResourceValues fargateResourceValues;

  @Nullable
  public ECSRecommendationDTO getECSRecommendationById(@NonNull final String accountIdentifier, String id,
      @NonNull OffsetDateTime startTime, @NonNull OffsetDateTime endTime, Long bufferPercentage) {
    final Optional<ECSServiceRecommendation> ecsRecommendation =
        ecsRecommendationDAO.fetchECSRecommendationById(accountIdentifier, id);

    if (!ecsRecommendation.isPresent()) {
      return ECSRecommendationDTO.builder().build();
    }

    ECSServiceRecommendation recommendation = ecsRecommendation.get();

    final List<ECSPartialRecommendationHistogram> histogramList =
        ecsRecommendationDAO.fetchPartialRecommendationHistograms(accountIdentifier, recommendation.getClusterId(),
            recommendation.getServiceArn(), startTime.toInstant(), endTime.toInstant());
    return mergeHistogram(histogramList, recommendation, bufferPercentage);
  }

  @NonNull
  private ECSRecommendationDTO mergeHistogram(final List<ECSPartialRecommendationHistogram> histogramList,
      ECSServiceRecommendation recommendation, Long bufferPercentage) {
    long memoryMb = memoryMbFromReadableFormat(recommendation.getCurrentResourceRequirements().get(MEMORY));
    long cpuUnits = cpuUnitsFromReadableFormat(recommendation.getCurrentResourceRequirements().get(CPU));
    Histogram memoryHistogram = newHistogram(memoryMb);
    Histogram cpuHistogram = newHistogram(cpuUnits);

    for (ECSPartialRecommendationHistogram partialHistogram : histogramList) {
      Histogram partialMemoryHistogram = newHistogram(memoryMb);
      partialMemoryHistogram.loadFromCheckPoint(partialHistogram.getMemoryHistogram());
      memoryHistogram.merge(partialMemoryHistogram);
      Histogram partialCpuHistogram = newHistogram(cpuUnits);
      partialCpuHistogram.loadFromCheckPoint(partialHistogram.getCpuHistogram());
      cpuHistogram.merge(partialCpuHistogram);
    }

    HistogramCheckpoint memoryHistogramCp = memoryHistogram.saveToCheckpoint();
    HistogramCheckpoint cpuHistogramCp = cpuHistogram.saveToCheckpoint();
    StrippedHistogram memStripped = StrippedHistogram.fromCheckpoint(memoryHistogramCp, NUMBER_OF_BUCKETS + 1);
    StrippedHistogram cpuStripped = StrippedHistogram.fromCheckpoint(cpuHistogramCp, NUMBER_OF_BUCKETS + 1);
    Map<String, Map<String, String>> percentileBased = recommendation.getPercentileBasedResourceRecommendation();
    getRecommendationWithBuffer(percentileBased, bufferPercentage);
    if (recommendation.getLaunchType() != null && recommendation.getLaunchType().equals(LaunchType.FARGATE)) {
      getFargateRecommendationValues(percentileBased);
    }

    return ECSRecommendationDTO.builder()
        .id(recommendation.getUuid())
        .clusterName(recommendation.getClusterName())
        .serviceArn(recommendation.getServiceArn())
        .serviceName(recommendation.getServiceName())
        .launchType(recommendation.getLaunchType())
        .current(recommendation.getCurrentResourceRequirements())
        .percentileBased(percentileBased)
        .lastDayCost(recommendation.getLastDayCost())
        .memoryHistogram(HistogramExp.builder()
                             .numBuckets(memStripped.getNumBuckets())
                             .minBucket(memStripped.getMinBucket())
                             .maxBucket(memStripped.getMaxBucket())
                             .bucketWeights(memStripped.getBucketWeights())
                             .precomputed(getPrecomputedPercentiles(memoryHistogram))
                             .totalWeight(memoryHistogramCp.getTotalWeight())
                             .build())
        .cpuHistogram(HistogramExp.builder()
                          .numBuckets(cpuStripped.getNumBuckets())
                          .minBucket(cpuStripped.getMinBucket())
                          .maxBucket(cpuStripped.getMaxBucket())
                          .bucketWeights(cpuStripped.getBucketWeights())
                          .precomputed(getPrecomputedPercentiles(cpuHistogram))
                          .totalWeight(cpuHistogramCp.getTotalWeight())
                          .build())
        .build();
  }

  private void getRecommendationWithBuffer(Map<String, Map<String, String>> percentileBased, Long bufferPercentage) {
    if (percentileBased == null) {
      return;
    }
    for (Map.Entry<String, Map<String, String>> mapEntry : percentileBased.entrySet()) {
      long memoryMb = memoryMbFromReadableFormat(mapEntry.getValue().get(MEMORY));
      long cpuUnits = cpuUnitsFromReadableFormat(mapEntry.getValue().get(CPU));
      memoryMb += (long) ((double) memoryMb * (double) bufferPercentage) / 100.0;
      cpuUnits += (long) ((double) cpuUnits * (double) bufferPercentage) / 100.0;
      long memoryBytes = BigDecimal.valueOf(memoryMb).scaleByPowerOfTen(6).longValue();
      long cpuMilliUnits = BigDecimal.valueOf(cpuUnits).scaleByPowerOfTen(3).longValue();
      percentileBased.put(mapEntry.getKey(), convertToReadableForm(makeResourceMap(cpuMilliUnits, memoryBytes)));
    }
  }

  private void getFargateRecommendationValues(Map<String, Map<String, String>> percentileBased) {
    for (Map.Entry<String, Map<String, String>> percentileBasedEntry : percentileBased.entrySet()) {
      long percentileMemoryMb = memoryMbFromReadableFormat(percentileBasedEntry.getValue().get(MEMORY));
      long percentileCpuUnits = cpuUnitsFromReadableFormat(percentileBasedEntry.getValue().get(CPU));
      long memoryBytes = BigDecimal.valueOf(percentileMemoryMb).scaleByPowerOfTen(6).longValue();
      long cpuMilliUnits = BigDecimal.valueOf(percentileCpuUnits).scaleByPowerOfTen(3).longValue();
      CpuMillsAndMemoryBytes resourceValues = fargateResourceValues.get(cpuMilliUnits, memoryBytes);
      long cpuAmount = resourceValues.getCpuMilliUnits();
      long memoryAmount = resourceValues.getMemoryBytes();
      percentileBased.put(
          percentileBasedEntry.getKey(), convertToReadableForm(makeResourceMap(cpuAmount, memoryAmount)));
    }
  }

  private static Histogram newHistogram(long maxUnits) {
    // Histogram will have 1000 buckets
    return new HistogramImpl(new LinearHistogramOptions(maxUnits, maxUnits / (double) NUMBER_OF_BUCKETS, EPSILON));
  }

  private double[] getPrecomputedPercentiles(Histogram histogram) {
    double[] result = new double[101];
    for (int p = 1; p <= 100; p++) {
      result[p] = histogram.getPercentile(p / 100.0);
    }
    return result;
  }

  private static long cpuUnitsFromReadableFormat(String cpu) {
    return getAmountFromReadableFormat(cpu).longValue();
  }

  private static long memoryMbFromReadableFormat(String memory) {
    return getAmountFromReadableFormat(memory).scaleByPowerOfTen(-6).longValue();
  }

  private static BigDecimal getAmountFromReadableFormat(String s) {
    return Quantity.fromString(s).getNumber();
  }

  public static Map<String, Long> makeResourceMap(long cpuAmount, long memoryAmount) {
    return ImmutableMap.of(CPU, cpuAmount, MEMORY, memoryAmount);
  }

  public static Map<String, String> convertToReadableForm(Map<String, Long> resourceMap) {
    ImmutableMap.Builder<String, String> builder = ImmutableMap.<String, String>builder();
    if (resourceMap.containsKey(CPU)) {
      builder.put(CPU, readableCpuAmount(resourceMap.get(CPU)));
    }
    if (resourceMap.containsKey(MEMORY)) {
      builder.put(MEMORY, readableMemoryAmount(resourceMap.get(MEMORY)));
    }
    return builder.build();
  }

  static String readableCpuAmount(long cpuAmount) {
    BigDecimal cpuInCores = BigDecimal
                                // milliCore to core
                                .valueOf(cpuAmount, 3)
                                // round up to nearest milliCore
                                .setScale(3, HALF_UP);
    return toDecimalSuffixedString(cpuInCores);
  }

  static String readableMemoryAmount(long memoryAmount) {
    int maxAllowedStringLen = 5;
    BigDecimal memoryInBytes = BigDecimal.valueOf(memoryAmount);
    int scale = 0;
    while (true) {
      String memoryString = toDecimalSuffixedString(memoryInBytes);
      if (memoryString.length() <= maxAllowedStringLen) {
        return memoryString;
      }
      // Keep rounding up to next higher unit until we reach a human readable value
      scale -= 3;
      memoryInBytes = memoryInBytes.setScale(scale, HALF_UP);
    }
  }

  private static String toDecimalSuffixedString(BigDecimal number) {
    return new Quantity(number, DECIMAL_SI).toSuffixedString();
  }
}
