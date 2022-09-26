/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.mappers.instanceinfo;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.dtos.instanceinfo.EcsInstanceInfoDTO;
import io.harness.entities.instanceinfo.EcsInstanceInfo;

import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.CDP)
@UtilityClass
public class EcsInstanceInfoMapper {
  public EcsInstanceInfoDTO toDTO(EcsInstanceInfo ecsInstanceInfo) {
    return EcsInstanceInfoDTO.builder()
        .serviceName(ecsInstanceInfo.getServiceName())
        .clusterArn(ecsInstanceInfo.getClusterArn())
        .launchType(ecsInstanceInfo.getLaunchType())
        .region(ecsInstanceInfo.getRegion())
        .taskArn(ecsInstanceInfo.getTaskArn())
        .taskDefinitionArn(ecsInstanceInfo.getTaskDefinitionArn())
        .containers(ecsInstanceInfo.getContainers())
        .startedBy(ecsInstanceInfo.getStartedBy())
        .startedAt(ecsInstanceInfo.getStartedAt())
        .version(ecsInstanceInfo.getVersion())
        .infraStructureKey(ecsInstanceInfo.getInfraStructureKey())
        .build();
  }

  public EcsInstanceInfo toEntity(EcsInstanceInfoDTO ecsInstanceInfoDTO) {
    return EcsInstanceInfo.builder()
        .serviceName(ecsInstanceInfoDTO.getServiceName())
        .clusterArn(ecsInstanceInfoDTO.getClusterArn())
        .launchType(ecsInstanceInfoDTO.getLaunchType())
        .region(ecsInstanceInfoDTO.getRegion())
        .taskArn(ecsInstanceInfoDTO.getTaskArn())
        .taskDefinitionArn(ecsInstanceInfoDTO.getTaskDefinitionArn())
        .containers(ecsInstanceInfoDTO.getContainers())
        .startedBy(ecsInstanceInfoDTO.getStartedBy())
        .startedAt(ecsInstanceInfoDTO.getStartedAt())
        .version(ecsInstanceInfoDTO.getVersion())
        .infraStructureKey(ecsInstanceInfoDTO.getInfraStructureKey())
        .build();
  }
}
