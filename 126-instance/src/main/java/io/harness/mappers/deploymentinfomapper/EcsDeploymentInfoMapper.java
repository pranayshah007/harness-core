/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.mappers.deploymentinfomapper;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.dtos.deploymentinfo.EcsDeploymentInfoDTO;
import io.harness.entities.deploymentinfo.EcsDeploymentInfo;

import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.CDP)
@UtilityClass
public class EcsDeploymentInfoMapper {
  public EcsDeploymentInfoDTO toDTO(EcsDeploymentInfo ecsDeploymentInfo) {
    return EcsDeploymentInfoDTO.builder()
        .serviceName(ecsDeploymentInfo.getServiceName())
        .clusterArn(ecsDeploymentInfo.getClusterArn())
        .region(ecsDeploymentInfo.getRegion())
        .infraStructureKey(ecsDeploymentInfo.getInfraStructureKey())
        .build();
  }

  public EcsDeploymentInfo toEntity(EcsDeploymentInfoDTO ecsDeploymentInfoDTO) {
    return EcsDeploymentInfo.builder()
        .serviceName(ecsDeploymentInfoDTO.getServiceName())
        .clusterArn(ecsDeploymentInfoDTO.getClusterArn())
        .region(ecsDeploymentInfoDTO.getRegion())
        .infraStructureKey(ecsDeploymentInfoDTO.getInfraStructureKey())
        .build();
  }
}
