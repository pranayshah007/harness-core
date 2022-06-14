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
import io.harness.pms.sdk.core.data.ExecutionSweepingOutput;
import io.harness.pms.sdk.core.data.Outcome;

import com.fasterxml.jackson.annotation.JsonTypeName;
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

  public GitopsClustersOutcome appendCluster(@NotNull String env, @NotNull String clusterName) {
    clustersData.add(ClusterData.builder().env(env).clusterName(clusterName).build());
    return this;
  }

  public GitopsClustersOutcome appendCluster(String envGroup, @NotNull String env, @NotNull String clusterName) {
    clustersData.add(ClusterData.builder().envGroup(envGroup).env(env).clusterName(clusterName).build());
    return this;
  }

  public GitopsClustersOutcome appendCluster(
      String envGroup, @NotNull String env, @NotNull String clusterName, Map<String, Object> variables) {
    clustersData.add(
        ClusterData.builder().envGroup(envGroup).env(env).clusterName(clusterName).variables(variables).build());
    return this;
  }

  @Data
  @Builder
  @JsonTypeName("clusterData")
  @RecasterAlias("io.harness.cdng.gitops.steps.GitopsClustersOutcome.ClusterData")
  public static class ClusterData {
    String envGroup;
    String env;
    String clusterName;
    Map<String, Object> variables;
  }
}
