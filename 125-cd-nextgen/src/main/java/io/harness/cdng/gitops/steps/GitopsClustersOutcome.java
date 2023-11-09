/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.gitops.steps;

import static io.harness.annotations.dev.HarnessTeam.GITOPS;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.gitops.mappers.ClusterEntityMapper;
import io.harness.cdng.gitops.mappers.ScopeAndRef;
import io.harness.pms.sdk.core.data.ExecutionSweepingOutput;
import io.harness.pms.sdk.core.data.Outcome;

import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.Value;

@Value
@JsonTypeName("gitopsClustersOutcome")
@RecasterAlias("io.harness.cdng.gitops.steps.GitopsClustersOutcome")
@OwnedBy(GITOPS)
public class GitopsClustersOutcome implements Outcome, ExecutionSweepingOutput {
  @NotNull List<ClusterData> clustersData;

  public GitopsClustersOutcome appendCluster(
      @NotNull Metadata env, @NotNull Metadata cluster, @NotNull String envType, String agentId) {
    ScopeAndRef scopedAndRefForCluster = ClusterEntityMapper.getScopeFromClusterRef(cluster.getIdentifier());
    clustersData.add(ClusterData.builder()
                         .envId(env.getIdentifier())
                         .envName(env.getName())
                         .envType(envType)
                         .clusterName(cluster.getName())
                         .clusterId(scopedAndRefForCluster.getRef())
                         .variables(Collections.emptyMap())
                         .scope(scopedAndRefForCluster.getScope().toString())
                         .agentId(agentId)
                         .build());
    return this;
  }

  public GitopsClustersOutcome appendCluster(@NotNull Metadata envGroup, @NotNull Metadata env, @NotNull String envType,
      @NotNull Metadata cluster, String agentId) {
    ScopeAndRef scopedAndRefForCluster = ClusterEntityMapper.getScopeFromClusterRef(cluster.getIdentifier());
    clustersData.add(ClusterData.builder()
                         .envGroupId(envGroup.getIdentifier())
                         .envGroupName(envGroup.getName())
                         .envId(env.getIdentifier())
                         .envName(env.getName())
                         .envType(envType)
                         .clusterId(scopedAndRefForCluster.getRef())
                         .scope(scopedAndRefForCluster.getScope().toString())
                         .clusterName(cluster.getName())
                         .variables(Collections.emptyMap())
                         .agentId(agentId)
                         .build());
    return this;
  }

  public GitopsClustersOutcome appendCluster(@NotNull Metadata envGroup, @NotNull Metadata env, @NotNull String envType,
      @NotNull Metadata cluster, Map<String, Object> variables, String agentId) {
    ScopeAndRef scopedAndRefForCluster = ClusterEntityMapper.getScopeFromClusterRef(cluster.getIdentifier());
    clustersData.add(ClusterData.builder()
                         .envGroupId(envGroup.getIdentifier())
                         .envGroupName(envGroup.getName())
                         .envId(env.getIdentifier())
                         .envName(env.getName())
                         .envType(envType)
                         .clusterId(scopedAndRefForCluster.getRef())
                         .scope(scopedAndRefForCluster.getScope().toString())
                         .clusterName(cluster.getName())
                         .variables(variables)
                         .agentId(agentId)
                         .build());
    return this;
  }

  @Data
  @Builder
  @JsonTypeName("clusterData")
  @RecasterAlias("io.harness.cdng.gitops.steps.GitopsClustersOutcome.ClusterData")
  public static class ClusterData {
    String envGroupId;
    String envGroupName;
    String envId;
    String envName;

    String envType;
    String clusterId;
    String clusterName;
    String scope;
    String agentId;
    Map<String, Object> variables;
  }
}
