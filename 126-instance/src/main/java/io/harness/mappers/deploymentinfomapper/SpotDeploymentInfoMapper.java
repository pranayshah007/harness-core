/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.mappers.deploymentinfomapper;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.dtos.deploymentinfo.SpotDeploymentInfoDTO;
import io.harness.entities.deploymentinfo.SpotDeploymentInfo;

import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.CDP)
@UtilityClass
public class SpotDeploymentInfoMapper {
  public SpotDeploymentInfoDTO toDTO(SpotDeploymentInfo deploymentInfo) {
    return SpotDeploymentInfoDTO.builder()
        .infrastructureKey(deploymentInfo.getInfrastructureKey())
        .elastigroupEc2InstancesMap(deploymentInfo.getElastigroupEc2InstancesMap())
        .build();
  }

  public SpotDeploymentInfo toEntity(SpotDeploymentInfoDTO deploymentInfoDTO) {
    return SpotDeploymentInfo.builder()
        .infrastructureKey(deploymentInfoDTO.getInfrastructureKey())
        .elastigroupEc2InstancesMap(deploymentInfoDTO.getElastigroupEc2InstancesMap())
        .build();
  }
}
