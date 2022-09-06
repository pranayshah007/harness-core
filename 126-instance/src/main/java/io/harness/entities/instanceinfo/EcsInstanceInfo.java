package io.harness.entities.instanceinfo;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.ecs.EcsContainer;

import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@OwnedBy(HarnessTeam.CDP)
@Data
@Builder
@EqualsAndHashCode(callSuper = true)
public class EcsInstanceInfo extends InstanceInfo {
  @NotNull private String region;
  @NotNull private String clusterArn;
  @NotNull private String taskArn;
  @NotNull private String taskDefinitionArn;
  private String launchType;
  @NotNull private String serviceName;
  @NotNull private List<EcsContainer> containers; // list of containers
  private long startedAt;
  private String startedBy;
  private Long version;
  private String infraStructureKey; // harness concept
}
