/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.commons.dao.recommendation;

import static com.google.common.base.MoreObjects.firstNonNull;
import static java.util.Optional.ofNullable;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.commons.beans.JobConstants;
import io.harness.ccm.commons.beans.recommendation.NodePoolId;
import io.harness.ccm.commons.beans.recommendation.RecommendationOverviewStats;
import io.harness.ccm.commons.beans.recommendation.ResourceId;
import io.harness.ccm.commons.entities.k8s.recommendation.K8sWorkloadRecommendation;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.math.BigDecimal;
import java.time.Instant;
import javax.annotation.Nullable;
import lombok.NonNull;

/**
 * //TODO(UTSAV): Migrate other methods containing logic from K8sRecommendationDAO to this class,
 * so that K8sRecommendationDAO remains pure DAO class. E.g., move #getServiceProvider()
 */
@Singleton
@OwnedBy(HarnessTeam.CE)
public class RecommendationCrudServiceImpl implements RecommendationCrudService {
  @Inject private K8sRecommendationDAO k8sRecommendationDAO;

  @Override
  public void upsertWorkloadRecommendation(@NonNull String uuid, @NonNull ResourceId workloadId,
      @NonNull String clusterName, @NonNull K8sWorkloadRecommendation recommendation) {
    final Double monthlyCost = calculateMonthlyCost(recommendation);
    final Double monthlySaving =
        ofNullable(recommendation.getEstimatedSavings()).map(BigDecimal::doubleValue).orElse(null);

    k8sRecommendationDAO.upsertCeRecommendation(uuid, workloadId, clusterName, monthlyCost, monthlySaving,
        recommendation.shouldShowRecommendation(),
        firstNonNull(recommendation.getLastReceivedUtilDataAt(), Instant.EPOCH));
  }

  @Override
  public void upsertNodeRecommendation(String entityUuid, JobConstants jobConstants, NodePoolId nodePoolId,
      String clusterName, RecommendationOverviewStats stats, String cloudProvider) {
    k8sRecommendationDAO.upsertCeRecommendation(entityUuid, jobConstants, nodePoolId, clusterName, stats,
        Instant.ofEpochMilli(jobConstants.getJobEndTime()), cloudProvider);
  }

  @Nullable
  private static Double calculateMonthlyCost(@NonNull K8sWorkloadRecommendation recommendation) {
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
