/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.mappers.deploymentinfomapper;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.dtos.deploymentinfo.AzureSshWinrmDeploymentInfoDTO;
import io.harness.entities.deploymentinfo.AzureSshWinrmDeploymentInfo;

import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.CDP)
@UtilityClass
public class AzureSshWinrmDeploymentInfoMapper {
  public AzureSshWinrmDeploymentInfoDTO toDTO(AzureSshWinrmDeploymentInfo deploymentInfo) {
    return AzureSshWinrmDeploymentInfoDTO.builder()
        .serviceType(deploymentInfo.getServiceType())
        .infrastructureKey(deploymentInfo.getInfrastructureKey())
        .host(deploymentInfo.getHost())
        .build();
  }

  public AzureSshWinrmDeploymentInfo toEntity(AzureSshWinrmDeploymentInfoDTO deploymentInfoDTO) {
    return AzureSshWinrmDeploymentInfo.builder()
        .serviceType(deploymentInfoDTO.getServiceType())
        .infrastructureKey(deploymentInfoDTO.getInfrastructureKey())
        .host(deploymentInfoDTO.getHost())
        .build();
  }
}
