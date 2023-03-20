/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.config.k8s.recommendation.estimators;

import static io.harness.ccm.commons.utils.ResourceAmountUtils.convertToReadableForm;

import static software.wings.graphql.datafetcher.ce.recommendation.entity.ResourceRequirement.CPU;
import static software.wings.graphql.datafetcher.ce.recommendation.entity.ResourceRequirement.MEMORY;

import static io.kubernetes.client.custom.Quantity.Format.DECIMAL_SI;

import software.wings.graphql.datafetcher.ce.recommendation.entity.K8sWorkloadRecommendationPreset;
import software.wings.graphql.datafetcher.ce.recommendation.entity.ResourceRequirement;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import lombok.experimental.UtilityClass;

@UtilityClass
public class ContainerResourceRequirementEstimators {
  // 50th, 90th & 95th percentiles of cpu usage as lower, target, and upper cpu recommendations
  private static final double TARGET_CPU_PERCENTILE = 0.9;
  private static final double LOWER_BOUND_CPU_PERCENTILE = 0.5;
  private static final double UPPER_BOUND_CPU_PERCENTILE = 0.95;

  // 50th, 90th & 95th percentiles of memory usage peaks as lower, target, and upper memory recommendations
  private static final double TARGET_MEMORY_PERCENTILE = 0.9;
  private static final double LOWER_BOUND_MEMORY_PERCENTILE = 0.5;
  private static final double UPPER_BOUND_MEMORY_PERCENTILE = 0.95;

  private static final double SAFETY_MARGIN_FRACTION = 0.15; // bump all recommendations by 15%

  private static final ResourceEstimator TARGET_ESTIMATOR =
      PercentileEstimator.of(TARGET_CPU_PERCENTILE, TARGET_MEMORY_PERCENTILE).withMargin(SAFETY_MARGIN_FRACTION);

  private static final ResourceEstimator UPPER_BOUND_ESTIMATOR =
      PercentileEstimator.of(UPPER_BOUND_CPU_PERCENTILE, UPPER_BOUND_MEMORY_PERCENTILE)
          .withMargin(SAFETY_MARGIN_FRACTION)
          .withConfidenceMultiplier(1.0, 1.0);

  static final ResourceEstimator LOWER_BOUND_ESTIMATOR =
      PercentileEstimator.of(LOWER_BOUND_CPU_PERCENTILE, LOWER_BOUND_MEMORY_PERCENTILE)
          .withMargin(SAFETY_MARGIN_FRACTION)
          .withConfidenceMultiplier(0.001, -2.0);

  public static ContainerResourceRequirementEstimator burstableRecommender(Map<String, Long> minResources) {
    return cs
        -> ResourceRequirement.builder()
               .requests(convertToReadableForm(
                   LOWER_BOUND_ESTIMATOR.withMinResources(minResources).getResourceEstimation(cs), DECIMAL_SI))
               .limits(convertToReadableForm(
                   UPPER_BOUND_ESTIMATOR.withMinResources(minResources).getResourceEstimation(cs), DECIMAL_SI))
               .build();
  }

  public static ContainerResourceRequirementEstimator guaranteedRecommender(Map<String, Long> minResources) {
    return cs -> {
      Map<String, String> resources =
          convertToReadableForm(TARGET_ESTIMATOR.withMinResources(minResources).getResourceEstimation(cs), DECIMAL_SI);
      return ResourceRequirement.builder().requests(resources).limits(resources).build();
    };
  }

  public static ContainerResourceRequirementEstimator customRecommender(
      Map<String, Long> minResources, double percentile) {
    final ResourceEstimator requiredEstimator = PercentileEstimator.of(percentile, percentile);
    return cs -> {
      Map<String, String> resources =
          convertToReadableForm(requiredEstimator.withMinResources(minResources).getResourceEstimation(cs), DECIMAL_SI);
      return ResourceRequirement.builder().requests(resources).limits(resources).build();
    };
  }

  public static ContainerResourceRequirementEstimator recommendedRecommender(Map<String, Long> minResources) {
    K8sWorkloadRecommendationPreset defaultPreset = K8sWorkloadRecommendationPreset.builder()
                                                        .cpuRequest(0.8)
                                                        .cpuLimit(-1)
                                                        .memoryRequest(0.8)
                                                        .memoryLimit(0.95)
                                                        .safetyMargin(SAFETY_MARGIN_FRACTION)
                                                        .build();
    return recommender(minResources, defaultPreset);
  }

  public static ContainerResourceRequirementEstimator recommender(
      Map<String, Long> minResources, K8sWorkloadRecommendationPreset preset) {
    return cs -> {
      Set<String> noLimitResources = new HashSet<>();
      if (preset.getCpuLimit() <= 0) {
        noLimitResources.add(CPU);
      }
      if (preset.getMemoryLimit() <= 0) {
        noLimitResources.add(MEMORY);
      }
      ResourceEstimator requestEstimator = PercentileEstimator.of(preset.getCpuRequest(), preset.getMemoryRequest())
                                               .withMargin(preset.getSafetyMargin())
                                               .withMinResources(minResources);
      ResourceEstimator limitEstimator = PercentileEstimator.of(preset.getCpuLimit(), preset.getMemoryLimit())
                                             .withMargin(preset.getSafetyMargin())
                                             .withMinResources(minResources)
                                             .omitResources(noLimitResources);
      return ResourceRequirement.builder()
          .requests(convertToReadableForm(requestEstimator.getResourceEstimation(cs), DECIMAL_SI))
          .limits(convertToReadableForm(limitEstimator.getResourceEstimation(cs), DECIMAL_SI))
          .build();
    };
  }
}
