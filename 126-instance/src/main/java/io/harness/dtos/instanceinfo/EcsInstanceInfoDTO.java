package io.harness.dtos.instanceinfo;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.ecs.EcsContainer;
import io.harness.util.InstanceSyncKey;

import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@OwnedBy(HarnessTeam.CDP)
@Data
@Builder
@EqualsAndHashCode(callSuper = true)
public class EcsInstanceInfoDTO extends InstanceInfoDTO {
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

  @Override
  public String prepareInstanceKey() {
    return InstanceSyncKey.builder().part(infraStructureKey).part(serviceName).part(taskArn).build().toString();
  }

  @Override
  public String prepareInstanceSyncHandlerKey() {
    return InstanceSyncKey.builder().part(infraStructureKey).part(serviceName).build().toString();
  }

  @Override
  public String getPodName() {
    return taskArn;
  }

  @Override
  public String getType() {
    return "ECS";
  }
}
