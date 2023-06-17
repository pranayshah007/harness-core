/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.execution;

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

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GitOpsExecutionSummaryDetails {
  Set<Environment> environments = new HashSet<>();
  List<Cluster> clusters = new ArrayList<>();

  public void addSingleEnvironment(@NotEmpty String envId, @NotEmpty String envName, @NotEmpty String envType) {
    environments.add(Environment.builder().identifier(envId).name(envName).type(envType).build());
  }

  public void addSingleEnvironmentWithinEnvGroup(@NotEmpty String envGroupId, @NotEmpty String envGroupName,
      @NotEmpty String envId, @NotEmpty String envName, @NotEmpty String envType) {
    environments.add(Environment.builder()
                         .identifier(envId)
                         .name(envName)
                         .type(envType)
                         .envGroupName(envGroupName)
                         .envGroupIdentifier(envGroupId)
                         .build());
  }

  @Data
  @Builder
  public static class Environment {
    String name;
    String type;
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
    String agentId;
    String scope;
  }
}
