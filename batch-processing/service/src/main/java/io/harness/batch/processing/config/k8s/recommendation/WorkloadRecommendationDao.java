/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.config.k8s.recommendation;

import io.harness.ccm.commons.beans.recommendation.ResourceId;
import io.harness.ccm.commons.entities.k8s.recommendation.K8sWorkloadRecommendation;
import io.harness.ccm.commons.entities.k8s.recommendation.K8sWorkloadRecommendation.K8sWorkloadRecommendationKeys;
import io.harness.ccm.commons.entities.k8s.recommendation.PartialRecommendationHistogram;
import io.harness.ccm.commons.entities.k8s.recommendation.PartialRecommendationHistogram.PartialRecommendationHistogramKeys;
import io.harness.persistence.HPersistence;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import javax.validation.constraints.NotNull;
import org.springframework.stereotype.Repository;

@Repository
public class WorkloadRecommendationDao {
  private final HPersistence hPersistence;

  public WorkloadRecommendationDao(HPersistence hPersistence) {
    this.hPersistence = hPersistence;
  }

  @NotNull
  K8sWorkloadRecommendation fetchRecommendationForWorkload(ResourceId workloadId) {
    return Optional
        .ofNullable(hPersistence.createQuery(K8sWorkloadRecommendation.class)
                        .field(K8sWorkloadRecommendationKeys.accountId)
                        .equal(workloadId.getAccountId())
                        .field(K8sWorkloadRecommendationKeys.clusterId)
                        .equal(workloadId.getClusterId())
                        .field(K8sWorkloadRecommendationKeys.namespace)
                        .equal(workloadId.getNamespace())
                        .field(K8sWorkloadRecommendationKeys.workloadName)
                        .equal(workloadId.getName())
                        .field(K8sWorkloadRecommendationKeys.workloadType)
                        .equal(workloadId.getKind())
                        .get())
        .orElseGet(()
                       -> K8sWorkloadRecommendation.builder()
                              .accountId(workloadId.getAccountId())
                              .clusterId(workloadId.getClusterId())
                              .namespace(workloadId.getNamespace())
                              .workloadName(workloadId.getName())
                              .workloadType(workloadId.getKind())
                              .containerRecommendations(new HashMap<>())
                              .containerCheckpoints(new HashMap<>())
                              .build());
  }

  String save(K8sWorkloadRecommendation recommendation) {
    return hPersistence.save(recommendation);
  }

  @NotNull
  PartialRecommendationHistogram fetchPartialRecommendationHistogramForWorkload(
      ResourceId workloadId, Instant jobStartDate) {
    return Optional
        .ofNullable(hPersistence.createQuery(PartialRecommendationHistogram.class)
                        .field(PartialRecommendationHistogramKeys.accountId)
                        .equal(workloadId.getAccountId())
                        .field(PartialRecommendationHistogramKeys.clusterId)
                        .equal(workloadId.getClusterId())
                        .field(PartialRecommendationHistogramKeys.namespace)
                        .equal(workloadId.getNamespace())
                        .field(PartialRecommendationHistogramKeys.workloadName)
                        .equal(workloadId.getName())
                        .field(PartialRecommendationHistogramKeys.workloadType)
                        .equal(workloadId.getKind())
                        .field(PartialRecommendationHistogramKeys.date)
                        .equal(jobStartDate)
                        .get())
        .orElseGet(()
                       -> PartialRecommendationHistogram.builder()
                              .accountId(workloadId.getAccountId())
                              .clusterId(workloadId.getClusterId())
                              .namespace(workloadId.getNamespace())
                              .workloadName(workloadId.getName())
                              .workloadType(workloadId.getKind())
                              .date(jobStartDate)
                              .containerCheckpoints(new HashMap<>())
                              .build());
  }

  @NotNull
  List<PartialRecommendationHistogram> fetchPartialRecommendationHistogramForWorkload(
      ResourceId workloadId, Instant startDate, Instant endDate) {
    return hPersistence.createQuery(PartialRecommendationHistogram.class)
        .field(PartialRecommendationHistogramKeys.accountId)
        .equal(workloadId.getAccountId())
        .field(PartialRecommendationHistogramKeys.clusterId)
        .equal(workloadId.getClusterId())
        .field(PartialRecommendationHistogramKeys.namespace)
        .equal(workloadId.getNamespace())
        .field(PartialRecommendationHistogramKeys.workloadName)
        .equal(workloadId.getName())
        .field(PartialRecommendationHistogramKeys.workloadType)
        .equal(workloadId.getKind())
        .field(PartialRecommendationHistogramKeys.date)
        .greaterThanOrEq(startDate)
        .field(PartialRecommendationHistogramKeys.date)
        .lessThanOrEq(endDate)
        .asList();
  }

  public void save(PartialRecommendationHistogram partialRecommendationHistogram) {
    hPersistence.save(partialRecommendationHistogram);
  }
}
