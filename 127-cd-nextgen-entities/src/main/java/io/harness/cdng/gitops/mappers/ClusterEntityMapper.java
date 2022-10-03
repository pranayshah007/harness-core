/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.gitops.mappers;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeLevel;
import io.harness.cdng.gitops.beans.ClusterBatchRequest;
import io.harness.cdng.gitops.beans.ClusterFromGitops;
import io.harness.cdng.gitops.beans.ClusterRequest;
import io.harness.cdng.gitops.beans.ClusterResponse;
import io.harness.cdng.gitops.entity.Cluster;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
        .clusterRef(getScopedClusterRef(request.getScope(), request.getIdentifier()))
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
                   .clusterRef(getScopedClusterRef(r.getScope(), r.getIdentifier()))
                   .build())
        .collect(Collectors.toList());
  }

  public List<Cluster> toEntities(
      String accountId, String orgId, String projectId, String envRef, List<ClusterFromGitops> clusters) {
    if (EmptyPredicate.isEmpty(clusters)) {
      return new ArrayList<>();
    }
    return clusters.stream()
        .map(r
            -> Cluster.builder()
                   .accountId(accountId)
                   .orgIdentifier(orgId)
                   .projectIdentifier(projectId)
                   .envRef(envRef)
                   .clusterRef(getScopedClusterRef(r.getScopeLevel(), r.getIdentifier()))
                   .build())
        .collect(Collectors.toList());
  }

  public ClusterResponse writeDTO(Cluster cluster) {
    final ScopeAndRef scopeFromClusterRef = getScopeFromClusterRef(cluster.getClusterRef());
    return ClusterResponse.builder()
        .orgIdentifier(cluster.getOrgIdentifier())
        .projectIdentifier(cluster.getProjectIdentifier())
        .clusterRef(scopeFromClusterRef.getOriginalRef())
        .scope(scopeFromClusterRef.getScope())
        .envRef(cluster.getEnvRef())
        .linkedAt(cluster.getCreatedAt())
        .build();
  }

  public ClusterResponse writeDTO(Cluster cluster, Map<String, ClusterFromGitops> clusterFromGitops) {
    String clusterRef = cluster.getClusterRef().toLowerCase();
    ClusterFromGitops gitOpsCluster = clusterFromGitops.getOrDefault(clusterRef, ClusterFromGitops.builder().build());
    final ScopeAndRef scopeFromClusterRef = getScopeFromClusterRef(clusterRef);

    switch (scopeFromClusterRef.getScope()) {
      case PROJECT:
        return ClusterResponse.builder()
            .name(
                clusterFromGitops.getOrDefault("project." + clusterRef, ClusterFromGitops.builder().build()).getName())
            .orgIdentifier(cluster.getOrgIdentifier())
            .projectIdentifier(cluster.getProjectIdentifier())
            .clusterRef(scopeFromClusterRef.getOriginalRef())
            .scope(scopeFromClusterRef.getScope())
            .envRef(cluster.getEnvRef())
            .linkedAt(cluster.getCreatedAt())
            .build();
      case ACCOUNT:
        return ClusterResponse.builder()
            .name(gitOpsCluster.getName())
            .clusterRef(scopeFromClusterRef.getOriginalRef())
            .accountIdentifier(cluster.getAccountId())
            .scope(scopeFromClusterRef.getScope())
            .envRef(cluster.getEnvRef())
            .linkedAt(cluster.getCreatedAt())
            .build();
      case ORGANIZATION:
        return ClusterResponse.builder()
            .name(gitOpsCluster.getName())
            .orgIdentifier(cluster.getOrgIdentifier())
            .accountIdentifier(cluster.getAccountId())
            .clusterRef(scopeFromClusterRef.getOriginalRef())
            .scope(scopeFromClusterRef.getScope())
            .envRef(cluster.getEnvRef())
            .linkedAt(cluster.getCreatedAt())
            .build();
      default:
        throw new InvalidRequestException("Invalid cluster reference %s:" + clusterRef);
    }
  }

  public ClusterFromGitops writeDTO(ScopeLevel scopeLevel, io.harness.gitops.models.Cluster cluster) {
    return ClusterFromGitops.builder()
        .identifier(cluster.getIdentifier())
        .name(cluster.name())
        .scopeLevel(scopeLevel)
        .build();
  }
  public String getScopedClusterRef(ScopeLevel scopeLevel, String ref) {
    return scopeLevel != null && scopeLevel != ScopeLevel.PROJECT
        ? String.format("%s.%s", scopeLevel.toString().toLowerCase(), ref)
        : ref;
  }

  public ScopeAndRef getScopeFromClusterRef(String ref) {
    String[] split = ref.split("\\.");
    if (split.length == 1) {
      return new ScopeAndRef(ref, ref, ScopeLevel.PROJECT);
    }

    if (ScopeLevel.ACCOUNT.toString().equalsIgnoreCase(split[0])) {
      return new ScopeAndRef(ref, split[1], ScopeLevel.ACCOUNT);
    }

    if (ScopeLevel.ORGANIZATION.toString().equalsIgnoreCase(split[0])) {
      return new ScopeAndRef(ref, split[1], ScopeLevel.ORGANIZATION);
    }

    throw new InvalidRequestException("Invalid cluster reference %s:" + ref);
  }
}
