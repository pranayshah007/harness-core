/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.overview.dto;

import io.harness.ng.core.environment.beans.EnvironmentType;

import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class InstanceGroupedByEnvironmentList {
  @NotNull List<InstanceGroupedByEnvironment> instanceGroupedByEnvironmentList;

  @Value
  @Builder
  public static class InstanceGroupedByEnvironment {
    @NotNull String envId;
    String envName;
    List<String> envGroups;
    @NotNull List<InstanceGroupedByEnvironmentType> instanceGroupedByEnvironmentTypeList;
    long lastDeployedAt;
  }

  @Value
  @Builder
  public static class InstanceGroupedByEnvironmentType {
    EnvironmentType environmentType;
    @NotNull List<InstanceGroupedByInfrastructure> instanceGroupedByInfrastructureList;
    long lastDeployedAt;
  }

  @Value
  @Builder
  public static class InstanceGroupedByInfrastructure {
    String infrastructureId;
    String infrastructureName;
    String clusterId;
    String agentId;
    @NotNull List<InstanceGroupedByArtifact> instanceGroupedByArtifactList;
    long lastDeployedAt;
  }

  @Value
  @Builder
  public static class InstanceGroupedByArtifact {
    String artifact; // displayName of artifact
    Integer count;
    long lastDeployedAt;
  }
}
