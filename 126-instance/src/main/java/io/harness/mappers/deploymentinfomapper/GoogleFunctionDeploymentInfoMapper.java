/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.mappers.deploymentinfomapper;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.dtos.deploymentinfo.GoogleFunctionDeploymentInfoDTO;
import io.harness.entities.deploymentinfo.GoogleFunctionDeploymentInfo;

import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.CDP)
@UtilityClass
public class GoogleFunctionDeploymentInfoMapper {
  public GoogleFunctionDeploymentInfoDTO toDTO(GoogleFunctionDeploymentInfo googleFunctionDeploymentInfo) {
    return GoogleFunctionDeploymentInfoDTO.builder()
        .environmentType(googleFunctionDeploymentInfo.getEnvironmentType())
        .functionName(googleFunctionDeploymentInfo.getFunctionName())
        .project(googleFunctionDeploymentInfo.getProject())
        .region(googleFunctionDeploymentInfo.getRegion())
        .infraStructureKey(googleFunctionDeploymentInfo.getInfraStructureKey())
        .build();
  }

  public GoogleFunctionDeploymentInfo toEntity(GoogleFunctionDeploymentInfoDTO googleFunctionDeploymentInfoDTO) {
    return GoogleFunctionDeploymentInfo.builder()
        .environmentType(googleFunctionDeploymentInfoDTO.getEnvironmentType())
        .functionName(googleFunctionDeploymentInfoDTO.getFunctionName())
        .project(googleFunctionDeploymentInfoDTO.getProject())
        .region(googleFunctionDeploymentInfoDTO.getRegion())
        .infraStructureKey(googleFunctionDeploymentInfoDTO.getInfraStructureKey())
        .build();
  }
}
