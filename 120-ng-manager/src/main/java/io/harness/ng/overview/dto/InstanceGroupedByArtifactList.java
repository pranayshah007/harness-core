/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.overview.dto;

import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class InstanceGroupedByArtifactList {
  List<InstanceGroupedByArtifact> instanceGroupedByArtifactList;
  @Value
  @Builder
  public static class InstanceGroupedByArtifact {
    String artifactVersion;
    List<InstanceGroupedByEnvironment> instanceGroupedByEnvironmentList;
  }
  @Value
  @Builder
  public static class InstanceGroupedByEnvironment {
    String envId;
    String envName;
    List<InstanceGroupedByInfrastructure> instanceGroupedByInfraList;
  }
  @Value
  @Builder
  public static class InstanceGroupedByInfrastructure {
    String infraIdentifier;
    String infraName;
    Integer count;
    String lastPipelineExecutionId;
    String lastPipelineExecutionName;
    String lastDeployedAt;
  }
}
