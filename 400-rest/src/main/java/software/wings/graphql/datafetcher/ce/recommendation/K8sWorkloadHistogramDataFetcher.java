/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.ce.recommendation;

import static io.harness.annotations.dev.HarnessTeam.CE;

import static software.wings.graphql.datafetcher.ce.recommendation.entity.RecommenderUtils.CPU_HISTOGRAM_FIRST_BUCKET_SIZE;
import static software.wings.graphql.datafetcher.ce.recommendation.entity.RecommenderUtils.HISTOGRAM_BUCKET_SIZE_GROWTH;
import static software.wings.graphql.datafetcher.ce.recommendation.entity.RecommenderUtils.MEMORY_HISTOGRAM_FIRST_BUCKET_SIZE;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.ccm.commons.entities.k8s.recommendation.PartialRecommendationHistogram;
import io.harness.ccm.commons.entities.k8s.recommendation.PartialRecommendationHistogram.PartialRecommendationHistogramKeys;
import io.harness.ccm.commons.utils.StrippedHistogram;
import io.harness.histogram.Histogram;
import io.harness.histogram.HistogramCheckpoint;
import io.harness.persistence.HIterator;
import io.harness.persistence.HPersistence;

import software.wings.graphql.datafetcher.AbstractObjectDataFetcher;
import software.wings.graphql.datafetcher.ce.recommendation.dto.QLContainerHistogramData;
import software.wings.graphql.datafetcher.ce.recommendation.dto.QLHistogramExp;
import software.wings.graphql.datafetcher.ce.recommendation.dto.QLK8SWorkloadHistogramData;
import software.wings.graphql.datafetcher.ce.recommendation.dto.QLK8sWorkloadParameters;
import software.wings.graphql.datafetcher.ce.recommendation.entity.PartialHistogramAggragator;
import software.wings.graphql.datafetcher.ce.recommendation.entity.RecommenderUtils;
import software.wings.security.PermissionAttribute;
import software.wings.security.annotations.AuthRule;

import com.google.inject.Inject;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.query.Query;

@Slf4j
@TargetModule(HarnessModule._375_CE_GRAPHQL)
@OwnedBy(CE)
public class K8sWorkloadHistogramDataFetcher
    extends AbstractObjectDataFetcher<QLK8SWorkloadHistogramData, QLK8sWorkloadParameters> {
  @Inject private HPersistence hPersistence;

  @Override
  @AuthRule(permissionType = PermissionAttribute.PermissionType.LOGGED_IN)
  protected QLK8SWorkloadHistogramData fetch(QLK8sWorkloadParameters parameters, String accountId) {
    Query<PartialRecommendationHistogram> query = hPersistence.createQuery(PartialRecommendationHistogram.class)
                                                      .field(PartialRecommendationHistogramKeys.accountId)
                                                      .equal(accountId)
                                                      .field(PartialRecommendationHistogramKeys.clusterId)
                                                      .equal(parameters.getCluster())
                                                      .field(PartialRecommendationHistogramKeys.namespace)
                                                      .equal(parameters.getNamespace())
                                                      .field(PartialRecommendationHistogramKeys.workloadName)
                                                      .equal(parameters.getWorkloadName())
                                                      .field(PartialRecommendationHistogramKeys.workloadType)
                                                      .equal(parameters.getWorkloadType())
                                                      .field(PartialRecommendationHistogramKeys.date)
                                                      .greaterThanOrEq(Instant.ofEpochMilli(parameters.getStartDate()))
                                                      .field(PartialRecommendationHistogramKeys.date)
                                                      .lessThanOrEq(Instant.ofEpochMilli(parameters.getEndDate()));

    // find all partial histograms that match this query and merge them
    Map<String, Histogram> cpuHistograms = new HashMap<>();
    Map<String, Histogram> memoryHistograms = new HashMap<>();
    try (HIterator<PartialRecommendationHistogram> partialRecommendationHistograms = new HIterator<>(query.fetch())) {
      PartialHistogramAggragator.aggregateInto(partialRecommendationHistograms, cpuHistograms, memoryHistograms);
    }

    final Set<String> commonContainerNames = new HashSet<>(cpuHistograms.keySet());
    commonContainerNames.retainAll(memoryHistograms.keySet());

    // Convert to the output format
    List<QLContainerHistogramData> containerHistogramDataList =
        commonContainerNames.stream()
            .map(containerName -> {
              Histogram memoryHistogram = memoryHistograms.get(containerName);
              HistogramCheckpoint memoryHistogramCp = memoryHistogram.saveToCheckpoint();
              Histogram cpuHistogram = cpuHistograms.get(containerName);
              HistogramCheckpoint cpuHistogramCp = cpuHistogram.saveToCheckpoint();
              int numBucketsMemory = RecommenderUtils.MEMORY_HISTOGRAM_OPTIONS.getNumBuckets();
              int numBucketsCpu = RecommenderUtils.CPU_HISTOGRAM_OPTIONS.getNumBuckets();
              StrippedHistogram memStripped = StrippedHistogram.fromCheckpoint(memoryHistogramCp, numBucketsMemory);
              StrippedHistogram cpuStripped = StrippedHistogram.fromCheckpoint(cpuHistogramCp, numBucketsCpu);
              return QLContainerHistogramData.builder()
                  .containerName(containerName)
                  .memoryHistogram(QLHistogramExp.builder()
                                       .numBuckets(memStripped.getNumBuckets())
                                       .minBucket(memStripped.getMinBucket())
                                       .maxBucket(memStripped.getMaxBucket())
                                       .firstBucketSize(MEMORY_HISTOGRAM_FIRST_BUCKET_SIZE)
                                       .growthRatio(HISTOGRAM_BUCKET_SIZE_GROWTH)
                                       .bucketWeights(memStripped.getBucketWeights())
                                       .precomputed(getPrecomputedPercentiles(memoryHistogram))
                                       .totalWeight(memoryHistogramCp.getTotalWeight())
                                       .build())
                  .cpuHistogram(QLHistogramExp.builder()
                                    .numBuckets(cpuStripped.getNumBuckets())
                                    .minBucket(cpuStripped.getMinBucket())
                                    .maxBucket(cpuStripped.getMaxBucket())
                                    .firstBucketSize(CPU_HISTOGRAM_FIRST_BUCKET_SIZE)
                                    .growthRatio(HISTOGRAM_BUCKET_SIZE_GROWTH)
                                    .bucketWeights(cpuStripped.getBucketWeights())
                                    .precomputed(getPrecomputedPercentiles(cpuHistogram))
                                    .totalWeight(cpuHistogramCp.getTotalWeight())
                                    .build())
                  .build();
            })
            .collect(Collectors.toList());

    return QLK8SWorkloadHistogramData.builder().containerHistogramDataList(containerHistogramDataList).build();
  }

  private double[] getPrecomputedPercentiles(Histogram histogram) {
    double[] result = new double[101];
    for (int p = 1; p <= 100; p++) {
      result[p] = histogram.getPercentile(p / 100.0);
    }
    return result;
  }
}
