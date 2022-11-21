package io.harness.ng.overview.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
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
    List<InstanceGroupedByArtifact> instanceGroupedByArtifactList;
  }

  @Value
  @Builder
  public static class InstanceGroupedByArtifact {
    String artifactVersion;
    String artifactPath;
    List<InstanceGroupedByEnvironment> instanceGroupedByEnvironmentList;
  }

  @Value
  @Builder
  public static class InstanceGroupedByEnvironment {
    String envId;
    String envName;
    List<InstanceGroupedByInfrastructure> instanceGroupedByInfraList;
    List<InstanceGroupedByInfrastructure> instanceGroupedByClusterList;
  }

  @Value
  @Builder
  public static class InstanceGroupedByInfrastructure {
    String infraIdentifier;
    String infraName;
    String clusterIdentifier;
    String agentIdentifier;
    List<InstanceGroupedByPipelineExecution> instanceGroupedByPipelineExecutionList;
  }

  @Getter
  @Setter
  @AllArgsConstructor
  public static class InstanceGroupedByPipelineExecution {
    Integer count;
    String lastPipelineExecutionId;
    String lastPipelineExecutionName;
    Long lastDeployedAt;
  }
}
