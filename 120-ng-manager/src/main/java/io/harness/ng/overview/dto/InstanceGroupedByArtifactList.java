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
    int count;
    String lastPipelineExecutionId;
    String lastPipelineExecutionName;
    String lastDeployedAt;
  }
}
