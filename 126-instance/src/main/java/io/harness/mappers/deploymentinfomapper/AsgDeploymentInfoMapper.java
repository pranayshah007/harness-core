/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.mappers.deploymentinfomapper;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.dtos.deploymentinfo.AsgDeploymentInfoDTO;
import io.harness.entities.deploymentinfo.AsgDeploymentInfo;

import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.CDP)
@UtilityClass
public class AsgDeploymentInfoMapper {
  public AsgDeploymentInfoDTO toDTO(AsgDeploymentInfo deploymentInfo) {
    return AsgDeploymentInfoDTO.builder()
        .region(deploymentInfo.getRegion())
        .infrastructureKey(deploymentInfo.getInfrastructureKey())
        .asgNameWithoutSuffix(deploymentInfo.getAsgNameWithoutSuffix())
        .executionStrategy(deploymentInfo.getExecutionStrategy())
        .build();
  }

  public AsgDeploymentInfo toEntity(AsgDeploymentInfoDTO deploymentInfoDTO) {
    return AsgDeploymentInfo.builder()
        .infrastructureKey(deploymentInfoDTO.getInfrastructureKey())
        .asgNameWithoutSuffix(deploymentInfoDTO.getAsgNameWithoutSuffix())
        .region(deploymentInfoDTO.getRegion())
        .executionStrategy(deploymentInfoDTO.getExecutionStrategy())
        .build();
  }
}
