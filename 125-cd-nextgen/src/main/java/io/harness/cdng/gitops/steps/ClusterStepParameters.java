package io.harness.cdng.gitops.steps;

import static java.util.Collections.singletonList;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.sdk.core.steps.io.StepParameters;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Singular;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(HarnessTeam.CDP)
@Data
@Builder
@TypeAlias("clusterStepParameters")
@RecasterAlias("io.harness.cdng.gitops.steps.ClusterStepParameters")
@NoArgsConstructor
@AllArgsConstructor
public class ClusterStepParameters implements StepParameters {
  private String envGroupRef;
  private boolean deployToAllEnvs;
  @Singular private Collection<EnvClusterRefs> envClusterRefs;

  public ClusterStepParameters and(
      @NotNull String envRef, boolean deployToAllClusters, Collection<String> clusterRefs) {
    if (envClusterRefs == null) {
      envClusterRefs = new ArrayList<>();
    }
    envClusterRefs.add(
        EnvClusterRefs.builder().envRef(envRef).clusterRefs(clusterRefs).deployToAll(deployToAllClusters).build());
    return this;
  }

  public static ClusterStepParameters WithEnvGroupRef(@NotNull String envGroupRef) {
    return ClusterStepParameters.builder().envGroupRef(envGroupRef).deployToAllEnvs(true).build();
  }

  public static ClusterStepParameters WithEnv(@NotNull String envRef) {
    return ClusterStepParameters.builder()
        .envClusterRefs(Collections.singletonList(EnvClusterRefs.builder().envRef(envRef).deployToAll(true).build()))
        .build();
  }

  public static ClusterStepParameters WithEnvAndClusterRefs(
      @NotNull String envRef, @NotNull Collection<String> clusterRefs) {
    return ClusterStepParameters.builder()
        .envClusterRefs(
            Collections.singletonList(EnvClusterRefs.builder().envRef(envRef).clusterRefs(clusterRefs).build()))
        .build();
  }

  public static ClusterStepParameters WithEnvGroupAndEnv(@NotNull String envGroupRef, @NotNull String env) {
    return ClusterStepParameters.builder()
        .envGroupRef(envGroupRef)
        .envClusterRefs(singletonList(EnvClusterRefs.builder().envRef(env).deployToAll(true).build()))
        .build();
  }

  public ClusterStepParameters(String envGroupRef, String env) {
    this.envGroupRef = envGroupRef;
    this.envClusterRefs = singletonList(EnvClusterRefs.builder().envRef(env).deployToAll(true).build());
  }

  @Data
  @Builder
  public static class EnvClusterRefs {
    private String envRef;
    private Collection<String> clusterRefs;
    boolean deployToAll;
  }
}
