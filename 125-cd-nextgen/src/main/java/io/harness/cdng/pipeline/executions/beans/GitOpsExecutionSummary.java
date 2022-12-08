/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.pipeline.executions.beans;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Value;

@OwnedBy(HarnessTeam.CDC)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GitOpsExecutionSummary {
  Set<Environment> environments = new HashSet<>();
  List<Cluster> clusters = new ArrayList<>();

  public void addSingleEnvironment(@NotEmpty String envId, @NotEmpty String envName) {
    environments.add(Environment.builder().identifier(envId).name(envName).build());
  }

  public void addSingleEnvironmentWithinEnvGroup(
      @NotEmpty String envGroupId, @NotEmpty String envGroupName, @NotEmpty String envId, @NotEmpty String envName) {
    environments.add(Environment.builder()
                         .identifier(envId)
                         .name(envName)
                         .envGroupName(envGroupName)
                         .envGroupIdentifier(envGroupId)
                         .build());
  }

  @Data
  @Builder
  public static class Environment {
    String name;
    String identifier;
    String envGroupIdentifier;
    String envGroupName;
  }

  @Value
  @Builder
  public static class Cluster {
    String envGroupId;
    String envGroupName;
    String envId;
    String envName;
    String clusterId;
    String clusterName;
  }
}
