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
public class InstanceGroupedOnArtifactList {
  @NotNull List<InstanceGroupedOnArtifact> instanceGroupedOnArtifactList;

  @Value
  @Builder
  public static class InstanceGroupedOnArtifact {
    String artifact;
    long lastDeployedAt;
    @NotNull List<InstanceGroupedOnChartVersion> instanceGroupedOnChartVersionList;
  }

  @Value
  @Builder
  public static class InstanceGroupedOnChartVersion {
    String chartVersion;
    long lastDeployedAt;
    List<InstanceGroupedOnEnvironment> instanceGroupedOnEnvironmentList;
  }

  @Value
  @Builder
  public static class InstanceGroupedOnEnvironment {
    String envId;
    String envName;
    long lastDeployedAt;
    List<InstanceGroupedOnEnvironmentType> instanceGroupedOnEnvironmentTypeList;
  }

  @Value
  @Builder
  public static class InstanceGroupedOnEnvironmentType {
    EnvironmentType environmentType;
    long lastDeployedAt;
    List<InstanceGroupedOnInfrastructure> instanceGroupedOnInfrastructureList;
  }

  @Value
  @Builder
  public static class InstanceGroupedOnInfrastructure {
    String infrastructureId;
    String infrastructureName;
    String clusterId;
    String agentId;
    long lastDeployedAt;
    Integer count;
  }
}
