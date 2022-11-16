package io.harness.mappers.deploymentinfomapper;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.dtos.deploymentinfo.TasDeploymentInfoDTO;
import io.harness.entities.deploymentinfo.TasDeploymentInfo;

import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.CDP)
@UtilityClass
public class TasDeploymentInfoMapper {
  public TasDeploymentInfoDTO toDTO(TasDeploymentInfo tasDeploymentInfo) {
    return TasDeploymentInfoDTO.builder()
        .applicationGuid(tasDeploymentInfo.getApplicationGuid())
        .applicationName(tasDeploymentInfo.getApplicationName())
        .build();
  }

  public TasDeploymentInfo toEntity(TasDeploymentInfoDTO tasDeploymentInfoDTO) {
    return TasDeploymentInfo.builder()
        .applicationGuid(tasDeploymentInfoDTO.getApplicationGuid())
        .applicationName(tasDeploymentInfoDTO.getApplicationName())
        .build();
  }
}
