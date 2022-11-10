/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.mappers.instanceinfo;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.dtos.instanceinfo.SpotInstanceInfoDTO;
import io.harness.entities.instanceinfo.SpotInstanceInfo;

import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.CDP)
@UtilityClass
public class SpotInstanceInfoMapper {
  public SpotInstanceInfoDTO toDTO(SpotInstanceInfo instanceInfo) {
    return SpotInstanceInfoDTO.builder()
        .infrastructureKey(instanceInfo.getInfrastructureKey())
        .elastigroupId(instanceInfo.getElastigroupId())
        .ec2InstanceId(instanceInfo.getEc2InstanceId())
        .build();
  }

  public SpotInstanceInfo toEntity(SpotInstanceInfoDTO instanceInfoDTO) {
    return SpotInstanceInfo.builder()
        .infrastructureKey(instanceInfoDTO.getInfrastructureKey())
        .elastigroupId(instanceInfoDTO.getElastigroupId())
        .ec2InstanceId(instanceInfoDTO.getEc2InstanceId())
        .build();
  }
}
