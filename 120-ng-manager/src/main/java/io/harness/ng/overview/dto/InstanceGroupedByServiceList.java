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
    List<InstanceGroupedByArtifactV2> instanceGroupedByArtifactList;
  }

  @Value
  @Builder
  public static class InstanceGroupedByArtifactV2 {
    String artifactVersion;
    String artifactPath;
    List<InstanceGroupedByEnvironmentV2> instanceGroupedByEnvironmentList;
  }

  @Value
  @Builder
  public static class InstanceGroupedByEnvironmentV2 {
    String envId;
    String envName;
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

    @Override
    public boolean equals(Object obj) {
      if (this == obj)
        return true;
      if (obj == null)
        return false;
      if (getClass() != obj.getClass())
        return false;
      InstanceGroupedByPipelineExecution other = (InstanceGroupedByPipelineExecution) obj;
      if (count == null) {
        if (other.count != null)
          return false;
      } else if (!count.equals(other.count))
        return false;
      if (lastPipelineExecutionId == null) {
        if (other.lastPipelineExecutionId != null)
          return false;
      } else if (!lastPipelineExecutionId.equals(other.lastPipelineExecutionId))
        return false;
      if (lastPipelineExecutionName == null) {
        if (other.lastPipelineExecutionName != null)
          return false;
      } else if (!lastPipelineExecutionName.equals(other.lastPipelineExecutionName))
        return false;
      if (lastDeployedAt == null) {
        if (other.lastDeployedAt != null)
          return false;
      } else if (!lastDeployedAt.equals(other.lastDeployedAt))
        return false;
      return true;
    }
  }
}
