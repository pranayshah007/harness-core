/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.mappers.instanceinfo;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.dtos.instanceinfo.AsgInstanceInfoDTO;
import io.harness.entities.instanceinfo.AsgInstanceInfo;

import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.CDP)
@UtilityClass
public class AsgInstanceInfoMapper {
  public AsgInstanceInfoDTO toDTO(AsgInstanceInfo instanceInfo) {
    return AsgInstanceInfoDTO.builder()
        .region(instanceInfo.getRegion())
        .infrastructureKey(instanceInfo.getInfrastructureKey())
        .asgName(instanceInfo.getAsgName())
        .asgNameWithoutSuffix(instanceInfo.getAsgNameWithoutSuffix())
        .instanceId(instanceInfo.getInstanceId())
        .executionStrategy(instanceInfo.getExecutionStrategy())
        .production(instanceInfo.getProduction())
        .build();
  }

  public AsgInstanceInfo toEntity(AsgInstanceInfoDTO instanceInfoDTO) {
    return AsgInstanceInfo.builder()
        .region(instanceInfoDTO.getRegion())
        .infrastructureKey(instanceInfoDTO.getInfrastructureKey())
        .asgName(instanceInfoDTO.getAsgName())
        .asgNameWithoutSuffix(instanceInfoDTO.getAsgNameWithoutSuffix())
        .instanceId(instanceInfoDTO.getInstanceId())
        .executionStrategy(instanceInfoDTO.getExecutionStrategy())
        .production(instanceInfoDTO.getProduction())
        .build();
  }
}
