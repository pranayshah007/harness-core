/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.mappers.deploymentinfomapper;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.dtos.deploymentinfo.AwsSamDeploymentInfoDTO;
import io.harness.entities.deploymentinfo.AwsSamDeploymentInfo;

import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.CDP)
@UtilityClass
public class AwsSamDeploymentInfoMapper {
  public AwsSamDeploymentInfoDTO toDTO(AwsSamDeploymentInfo awsSamDeploymentInfo) {
    return AwsSamDeploymentInfoDTO.builder()
        .functions(awsSamDeploymentInfo.getFunctions())
        .region(awsSamDeploymentInfo.getRegion())
        .infraStructureKey(awsSamDeploymentInfo.getInfraStructureKey())
        .build();
  }

  public AwsSamDeploymentInfo toEntity(AwsSamDeploymentInfoDTO awsSamDeploymentInfoDTO) {
    return AwsSamDeploymentInfo.builder()
        .functions(awsSamDeploymentInfoDTO.getFunctions())
        .region(awsSamDeploymentInfoDTO.getRegion())
        .infraStructureKey(awsSamDeploymentInfoDTO.getInfraStructureKey())
        .build();
  }
}
