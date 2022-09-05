package io.harness.dtos.deploymentinfo;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.k8s.ServiceSpecType;
import io.harness.util.InstanceSyncKey;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@Builder
@EqualsAndHashCode(callSuper = true)
@OwnedBy(CDP)
public class EcsDeploymentInfoDTO extends DeploymentInfoDTO {
  @NotNull private String region;
  @NotNull private String clusterArn;
  @NotNull private String serviceName;
  @NotNull private String infraStructureKey;

  @Override
  public String getType() {
    return ServiceSpecType.ECS;
  }

  @Override
  public String prepareInstanceSyncHandlerKey() {
    return InstanceSyncKey.builder().part(infraStructureKey).part(serviceName).build().toString();
  }
}
