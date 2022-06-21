/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.gitops.steps;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.sdk.core.steps.io.StepParameters;

import java.util.List;
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
  private String envGroupName;
  private boolean deployToAllEnvs;
  @Singular private List<EnvClusterRefs> envClusterRefs;

  public static ClusterStepParameters WithEnvGroup(@NotNull Metadata envGroup) {
    return ClusterStepParameters.builder()
        .envGroupRef(envGroup.getIdentifier())
        .envGroupName(envGroup.getName())
        .deployToAllEnvs(true)
        .build();
  }
}
