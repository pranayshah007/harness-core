/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.commons.beans.recommendation;

import io.harness.ccm.commons.beans.recommendation.models.RecommendClusterRequest;
import io.harness.exception.InvalidRequestException;

import lombok.NonNull;
import lombok.experimental.UtilityClass;

@UtilityClass
public class RecommendationUtils {
  public RecommendClusterRequest constructNodeRecommendationRequest(TotalResourceUsage totalResourceUsage) {
    if (!isResourceConsistent(totalResourceUsage)) {
      throw new InvalidRequestException(String.format("Inconsistent TotalResourceUsage: %s", totalResourceUsage));
    }

    long minNodes = 3L;
    long maxNodesPossible = (long) Math.min(Math.floor(totalResourceUsage.getSumcpu() / totalResourceUsage.getMaxcpu()),
        Math.floor(totalResourceUsage.getSummemory() / totalResourceUsage.getMaxmemory()));

    maxNodesPossible = Math.max(maxNodesPossible, 1L);
    if (maxNodesPossible < 3L) {
      minNodes = maxNodesPossible;
    }

    return RecommendClusterRequest.builder()
        .maxNodes(maxNodesPossible)
        .minNodes(minNodes)
        .sumCpu(totalResourceUsage.getSumcpu() / 1024.0D)
        .sumMem(totalResourceUsage.getSummemory() / 1024.0D)
        .allowBurst(true)
        .sameSize(true)
        .build();
  }

  public TotalResourceUsage getTotalResourceUsageGiB(TotalResourceUsage totalResourceUsage) {
    return TotalResourceUsage.builder()
        .maxcpu(totalResourceUsage.getMaxcpu() / 1024.0D)
        .maxmemory(totalResourceUsage.getMaxmemory() / 1024.0D)
        .build();
  }

  private boolean isResourceConsistent(@NonNull TotalResourceUsage resource) {
    boolean anyZero = Math.round(resource.getSumcpu()) == 0L || Math.round(resource.getSummemory()) == 0L
        || Math.round(resource.getMaxcpu()) == 0L || Math.round(resource.getMaxmemory()) == 0L;
    return !anyZero;
  }
}
