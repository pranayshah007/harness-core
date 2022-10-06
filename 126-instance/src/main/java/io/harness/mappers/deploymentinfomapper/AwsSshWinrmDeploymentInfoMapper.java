/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.mappers.deploymentinfomapper;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.dtos.deploymentinfo.AwsSshWinrmDeploymentInfoDTO;
import io.harness.entities.deploymentinfo.AwsSshWinrmDeploymentInfo;

import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.CDP)
@UtilityClass
public class AwsSshWinrmDeploymentInfoMapper {
  public AwsSshWinrmDeploymentInfoDTO toDTO(AwsSshWinrmDeploymentInfo deploymentInfo) {
    return AwsSshWinrmDeploymentInfoDTO.builder()
        .serviceType(deploymentInfo.getServiceType())
        .infrastructureKey(deploymentInfo.getInfrastructureKey())
        .host(deploymentInfo.getHost())
        .build();
  }

  public AwsSshWinrmDeploymentInfo toEntity(AwsSshWinrmDeploymentInfoDTO deploymentInfoDTO) {
    return AwsSshWinrmDeploymentInfo.builder()
        .serviceType(deploymentInfoDTO.getServiceType())
        .infrastructureKey(deploymentInfoDTO.getInfrastructureKey())
        .host(deploymentInfoDTO.getHost())
        .build();
  }
}
