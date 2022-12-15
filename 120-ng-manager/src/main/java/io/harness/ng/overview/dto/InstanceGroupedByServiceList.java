/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.overview.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.Value;

@Value
@Builder
public class InstanceGroupedByServiceList {
  List<InstanceGroupedByService> instanceGroupedByServiceList;

  @Value
  @Builder
  public static class InstanceGroupedByService {
    String serviceId;
    String serviceName;
    Long lastDeployedAt;
    List<InstanceGroupedByArtifactV2> instanceGroupedByArtifactList;
  }

  @Value
  @Builder
  public static class InstanceGroupedByArtifactV2 {
    String artifactVersion;
    String artifactPath;
    boolean latest;
    Long lastDeployedAt;
    List<InstanceGroupedByEnvironmentV2> instanceGroupedByEnvironmentList;
  }

  @Value
  @Builder
  public static class InstanceGroupedByEnvironmentV2 {
    String envId;
    String envName;
    Long lastDeployedAt;
    List<InstanceGroupedByInfrastructureV2> instanceGroupedByInfraList;
    List<InstanceGroupedByInfrastructureV2> instanceGroupedByClusterList;
  }

  @Value
  @Builder
  public static class InstanceGroupedByInfrastructureV2 {
    String infraIdentifier;
    String infraName;
    String clusterIdentifier;
    String agentIdentifier;
    Long lastDeployedAt;
    List<InstanceGroupedByPipelineExecution> instanceGroupedByPipelineExecutionList;
  }

  @Getter
  @Setter
  @AllArgsConstructor
  @EqualsAndHashCode
  public static class InstanceGroupedByPipelineExecution {
    Integer count;
    String lastPipelineExecutionId;
    String lastPipelineExecutionName;
    Long lastDeployedAt;
  }
}
