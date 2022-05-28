/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.gitops.mappers;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.gitops.beans.ClusterBatchRequest;
import io.harness.cdng.gitops.beans.ClusterRequest;
import io.harness.cdng.gitops.beans.ClusterResponse;
import io.harness.cdng.gitops.entity.Cluster;

import java.util.List;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.GITOPS)
@UtilityClass
public class ClusterEntityMapper {
  public Cluster toEntity(String accountId, ClusterRequest request) {
    return Cluster.builder()
        .accountId(accountId)
        .orgIdentifier(request.getOrgIdentifier())
        .projectIdentifier(request.getProjectIdentifier())
        .clusterRef(request.getIdentifier())
        .envRef(request.getEnvRef())
        .build();
  }

  public List<Cluster> toEntities(String accountId, ClusterBatchRequest request) {
    return request.getClusters()
        .stream()
        .map(r
            -> Cluster.builder()
                   .accountId(accountId)
                   .orgIdentifier(request.getOrgIdentifier())
                   .projectIdentifier(request.getProjectIdentifier())
                   .envRef(request.getEnvRef())
                   .clusterRef(r.getIdentifier())
                   .build())
        .collect(Collectors.toList());
  }

  public ClusterResponse writeDTO(Cluster cluster) {
    return ClusterResponse.builder()
        .orgIdentifier(cluster.getOrgIdentifier())
        .projectIdentifier(cluster.getProjectIdentifier())
        .clusterRef(cluster.getClusterRef())
        .envRef(cluster.getEnvRef())
        .build();
  }
}
