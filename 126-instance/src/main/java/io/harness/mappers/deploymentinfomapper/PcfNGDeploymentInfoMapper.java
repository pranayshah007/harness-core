package io.harness.mappers.deploymentinfomapper;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.dtos.deploymentinfo.PcfNGDeploymentInfoDTO;
import io.harness.entities.deploymentinfo.PcfNGDeploymentInfo;

import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.CDP)
@UtilityClass
public class PcfNGDeploymentInfoMapper {
  public PcfNGDeploymentInfoDTO toDTO(PcfNGDeploymentInfo pcfNGDeploymentInfo) {
    return PcfNGDeploymentInfoDTO.builder()
        .applicationGuid(pcfNGDeploymentInfo.getApplicationGuid())
        .applicationName(pcfNGDeploymentInfo.getApplicationName())
        .build();
  }

  public PcfNGDeploymentInfo toEntity(PcfNGDeploymentInfoDTO pcfNGDeploymentInfoDTO) {
    return PcfNGDeploymentInfo.builder()
        .applicationGuid(pcfNGDeploymentInfoDTO.getApplicationGuid())
        .applicationName(pcfNGDeploymentInfoDTO.getApplicationName())
        .build();
  }
}
