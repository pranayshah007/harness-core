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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.GITOPS)
@UtilityClass
public class ClusterEntityMapper {
  public static final String ORG = "org";
  public static final String ORG_PREFIX = "org.";

  public Cluster toEntity(String accountId, ClusterRequest request) {
    return Cluster.builder()
        .accountId(accountId)
        .orgIdentifier(request.getOrgIdentifier())
        .projectIdentifier(request.getProjectIdentifier())
        .clusterRef(getScopedClusterRef(request.getScope(), request.getIdentifier()))
        .agentIdentifier(request.getAgentIdentifier())
        .envRef(request.getEnvRef())
        .build();
  }

  public List<Cluster> toEntities(String accountId, ClusterBatchRequest request) {
    return request.getClusters()
        .stream()
        .map(r
            -> Cluster.builder()
                   .accountId(accountId)
                   .agentIdentifier(r.getAgentIdentifier())
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
                   .agentIdentifier(r.getAgentIdentifier())
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
        .agentIdentifier(cluster.getAgentIdentifier())
        .clusterRef(scopeFromClusterRef.getOriginalRef())
        .scope(scopeFromClusterRef.getScope())
        .envRef(cluster.getEnvRef())
        .linkedAt(cluster.getCreatedAt())
        .build();
  }

  public ClusterResponse writeDTO(Cluster cluster, Map<String, ClusterFromGitops> clusterFromGitops) {
    String clusterRef = cluster.getClusterRef();
    ClusterFromGitops gitOpsCluster = clusterFromGitops.getOrDefault(clusterRef, ClusterFromGitops.builder().build());
    final ScopeAndRef scopeFromClusterRef = getScopeFromClusterRef(clusterRef);

    switch (scopeFromClusterRef.getScope()) {
      case PROJECT:
        return ClusterResponse.builder()
            .name(
                clusterFromGitops.getOrDefault("project." + clusterRef, ClusterFromGitops.builder().build()).getName())
            .tags(
                clusterFromGitops.getOrDefault("project." + clusterRef, ClusterFromGitops.builder().build()).getTags())
            .orgIdentifier(cluster.getOrgIdentifier())
            .projectIdentifier(cluster.getProjectIdentifier())
            .accountIdentifier(cluster.getAccountId())
            .agentIdentifier(cluster.getAgentIdentifier())
            .clusterRef(scopeFromClusterRef.getOriginalRef())
            .scope(scopeFromClusterRef.getScope())
            .envRef(cluster.getEnvRef())
            .linkedAt(cluster.getCreatedAt())
            .build();
      case ACCOUNT:
        return ClusterResponse.builder()
            .name(gitOpsCluster.getName())
            .tags(gitOpsCluster.getTags())
            .clusterRef(scopeFromClusterRef.getOriginalRef())
            .accountIdentifier(cluster.getAccountId())
            .agentIdentifier(cluster.getAgentIdentifier())
            .scope(scopeFromClusterRef.getScope())
            .envRef(cluster.getEnvRef())
            .linkedAt(cluster.getCreatedAt())
            .build();
      case ORGANIZATION:
        return ClusterResponse.builder()
            .name(gitOpsCluster.getName())
            .tags(gitOpsCluster.getTags())
            .orgIdentifier(cluster.getOrgIdentifier())
            .accountIdentifier(cluster.getAccountId())
            .agentIdentifier(cluster.getAgentIdentifier())
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
        .agentIdentifier(cluster.getAgentIdentifier())
        .name(cluster.name())
        .scopeLevel(scopeLevel)
        .tags(cluster.getTags() == null ? Collections.emptyMap() : cluster.getTags())
        .build();
  }
  public String getScopedClusterRef(ScopeLevel scopeLevel, String ref) {
    if (scopeLevel == ScopeLevel.ACCOUNT) {
      return String.format("%s.%s", scopeLevel.toString().toLowerCase(), ref);
    } else if (scopeLevel == ScopeLevel.ORGANIZATION) {
      return String.format("%s.%s", ORG, ref);
    } else {
      return ref;
    }
  }

  public ScopeAndRef getScopeFromClusterRef(String ref) {
    String[] split = ref.split("\\.");
    if (split.length == 1) {
      return new ScopeAndRef(ref, ref, ScopeLevel.PROJECT);
    }

    if (ScopeLevel.ACCOUNT.toString().equalsIgnoreCase(split[0])) {
      return new ScopeAndRef(ref, split[1], ScopeLevel.ACCOUNT);
    }

    if (ORG.equalsIgnoreCase(split[0])) {
      return new ScopeAndRef(ref, split[1], ScopeLevel.ORGANIZATION);
    }

    throw new InvalidRequestException("Invalid cluster reference %s:" + ref);
  }
}
