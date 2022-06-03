/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.tasklet.util;

import static java.util.Optional.ofNullable;

import io.harness.ccm.cluster.dao.ClusterRecordDao;
import io.harness.ccm.commons.entities.ClusterRecord;
import io.harness.ccm.commons.service.intf.ClusterRecordService;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class ClusterHelperImpl implements ClusterHelper {
  @Inject private ClusterRecordDao cgClusterRecordDao;
  @Inject private ClusterRecordService clusterRecordService;

  private final Cache<String, String> clusterIdNameCache =
      Caffeine.newBuilder().maximumSize(200).expireAfterWrite(1, TimeUnit.HOURS).build();

  @Override
  public String fetchClusterName(@NonNull String clusterId) {
    String clusterName = clusterIdNameCache.getIfPresent(clusterId);

    if (clusterName == null) {
      clusterName = fetchNGClusterName(clusterId);
    }

    if (clusterName == null) {
      clusterName = fetchCGClusterName(clusterId);
    }

    if (clusterName == null) {
      // better return clusterId than null, to facilitate debugging during ng migration
      log.warn("Ideally un-reachable code");
      clusterName = clusterId;
    } else {
      clusterIdNameCache.put(clusterId, clusterName);
    }

    return clusterName;
  }

  @Nullable
  private String fetchNGClusterName(@NonNull String clusterId) {
    final ClusterRecord clusterRecord = clusterRecordService.get(clusterId);
    return ofNullable(clusterRecord).map(ClusterRecord::getClusterName).orElse(null);
  }

  @Nullable
  private String fetchCGClusterName(@NonNull String clusterId) {
    final io.harness.ccm.cluster.entities.ClusterRecord clusterRecord = cgClusterRecordDao.get(clusterId);

    if (clusterRecord != null) {
      return clusterRecord.getCluster().getClusterName();
    }

    return null;
  }
}
