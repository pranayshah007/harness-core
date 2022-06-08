package io.harness.cdng.gitops.steps;

import static io.harness.annotations.dev.HarnessTeam.GITOPS;
import static io.harness.data.structure.CollectionUtils.emptyIfNull;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.sdk.core.data.ExecutionSweepingOutput;
import io.harness.pms.sdk.core.data.Outcome;
import io.harness.yaml.core.variables.NGVariable;

import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
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
      @NotNull String env, @NotNull String clusterName, List<NGVariable> variables) {
    final Map<String, Object> outputVars = emptyIfNull(variables).stream().collect(
        Collectors.toMap(NGVariable::getName, v -> v.getCurrentValue().fetchFinalValue()));
    clustersData.add(ClusterData.builder().env(env).clusterName(clusterName).variables(outputVars).build());
    return this;
  }

  @Data
  @Builder
  private static class ClusterData {
    String envGroup;
    String env;
    String clusterName;
    Map<String, Object> variables;
  }
}
