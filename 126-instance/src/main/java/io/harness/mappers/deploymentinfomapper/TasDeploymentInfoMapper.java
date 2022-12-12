/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */
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
