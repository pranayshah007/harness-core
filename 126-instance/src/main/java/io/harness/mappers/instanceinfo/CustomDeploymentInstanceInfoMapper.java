package io.harness.mappers.instanceinfo;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.dtos.instanceinfo.CustomDeploymentInstanceInfoDTO;
import io.harness.entities.instanceinfo.CustomDeploymentInstanceInfo;

import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.CDP)
@UtilityClass
public class CustomDeploymentInstanceInfoMapper {
  public CustomDeploymentInstanceInfoDTO toDTO(CustomDeploymentInstanceInfo customDeploymentInstanceInfo) {
    return CustomDeploymentInstanceInfoDTO.builder()
        .instanceFetchScript(customDeploymentInstanceInfo.getInstanceFetchScript())
        .hostname(customDeploymentInstanceInfo.getHostname())
        .properties(customDeploymentInstanceInfo.getProperties())
        .build();
  }
  public CustomDeploymentInstanceInfo toEntity(CustomDeploymentInstanceInfoDTO customDeploymentInstanceInfoDTO) {
    return CustomDeploymentInstanceInfo.builder()
        .instanceFetchScript(customDeploymentInstanceInfoDTO.getInstanceFetchScript())
        .hostname(customDeploymentInstanceInfoDTO.getHostname())
        .properties(customDeploymentInstanceInfoDTO.getProperties())
        .build();
  }
}
