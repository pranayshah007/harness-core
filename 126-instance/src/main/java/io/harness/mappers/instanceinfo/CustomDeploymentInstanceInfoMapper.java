/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.mappers.instanceinfo;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.dtos.instanceinfo.CustomDeploymentInstanceInfoDTO;
import io.harness.entities.instanceinfo.CustomDeploymentInstanceInfo;

import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.CDP)
@UtilityClass
public class CustomDeploymentInstanceInfoMapper {
  public CustomDeploymentInstanceInfoDTO toDTO(CustomDeploymentInstanceInfo customDeploymentInstanceInfo) {
    return CustomDeploymentInstanceInfoDTO.builder()
        .instanceFetchScript(customDeploymentInstanceInfo.getInstanceFetchScript())
        .hostname(customDeploymentInstanceInfo.getHostname())
        .properties(customDeploymentInstanceInfo.getProperties())
        .build();
  }
  public CustomDeploymentInstanceInfo toEntity(CustomDeploymentInstanceInfoDTO customDeploymentInstanceInfoDTO) {
    return CustomDeploymentInstanceInfo.builder()
        .instanceFetchScript(customDeploymentInstanceInfoDTO.getInstanceFetchScript())
        .hostname(customDeploymentInstanceInfoDTO.getHostname())
        .properties(customDeploymentInstanceInfoDTO.getProperties())
        .build();
  }
}
