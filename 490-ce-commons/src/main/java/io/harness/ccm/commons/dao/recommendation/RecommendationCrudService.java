/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.commons.dao.recommendation;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.commons.beans.JobConstants;
import io.harness.ccm.commons.beans.recommendation.NodePoolId;
import io.harness.ccm.commons.beans.recommendation.RecommendationOverviewStats;
import io.harness.ccm.commons.beans.recommendation.ResourceId;
import io.harness.ccm.commons.entities.k8s.recommendation.K8sWorkloadRecommendation;

import lombok.NonNull;

@OwnedBy(HarnessTeam.CE)
public interface RecommendationCrudService {
  void upsertWorkloadRecommendation(@NonNull String uuid, @NonNull ResourceId workloadId, @NonNull String clusterName,
      @NonNull K8sWorkloadRecommendation recommendation);

  void upsertNodeRecommendation(String entityUuid, @NonNull JobConstants jobConstants, @NonNull NodePoolId nodePoolId,
      @NonNull String clusterName, @NonNull RecommendationOverviewStats stats, String cloudProvider);
}
