package io.harness.delegate.beans.ecs;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@OwnedBy(HarnessTeam.CDP)
@Data
@Builder
public class EcsTask {
  private String clusterArn;
  private String taskArn;
  private String taskDefinitionArn;
  private String launchType;
  private String serviceName;
  private List<EcsContainer> containers; // list of containers
  private long startedAt;
  private String startedBy;
  private Long version;
}
