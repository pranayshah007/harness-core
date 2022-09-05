package io.harness.entities.deploymentinfo;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@Builder
@EqualsAndHashCode(callSuper = true)
@OwnedBy(HarnessTeam.CDP)
public class EcsDeploymentInfo extends DeploymentInfo {
  @NotNull private String region;
  @NotNull private String clusterArn;
  @NotNull private String serviceName;
  @NotNull private String infraStructureKey;
}
