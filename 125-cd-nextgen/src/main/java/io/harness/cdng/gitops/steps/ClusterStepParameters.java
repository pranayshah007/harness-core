/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.gitops.steps;

import static java.util.Collections.singletonList;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.sdk.core.steps.io.StepParameters;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
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
  @Singular private List<EnvClusterRefs> envClusterRefs;

  public ClusterStepParameters and(@NotNull String envRef, boolean deployToAllClusters, Set<String> clusterRefs) {
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

  public ClusterStepParameters(String envGroupRef, String env) {
    this.envGroupRef = envGroupRef;
    this.envClusterRefs = singletonList(EnvClusterRefs.builder().envRef(env).deployToAll(true).build());
  }
}
