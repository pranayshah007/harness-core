/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.mappers.deploymentinfomapper;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.dtos.deploymentinfo.PdcDeploymentInfoDTO;
import io.harness.entities.deploymentinfo.PdcDeploymentInfo;

import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.CDP)
@UtilityClass
public class PdcDeploymentInfoMapper {
  public PdcDeploymentInfoDTO toDTO(PdcDeploymentInfo pdcDeploymentInfo) {
    return PdcDeploymentInfoDTO.builder()
        .serviceType(pdcDeploymentInfo.getServiceType())
        .infrastructureKey(pdcDeploymentInfo.getInfrastructureKey())
        .host(pdcDeploymentInfo.getHost())
        .build();
  }

  public PdcDeploymentInfo toEntity(PdcDeploymentInfoDTO pdcDeploymentInfoDTO) {
    return PdcDeploymentInfo.builder()
        .serviceType(pdcDeploymentInfoDTO.getServiceType())
        .infrastructureKey(pdcDeploymentInfoDTO.getInfrastructureKey())
        .host(pdcDeploymentInfoDTO.getHost())
        .build();
  }
}
