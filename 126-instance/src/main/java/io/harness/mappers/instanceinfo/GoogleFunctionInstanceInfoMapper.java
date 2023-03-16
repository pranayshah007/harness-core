/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.mappers.instanceinfo;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.dtos.instanceinfo.GoogleFunctionInstanceInfoDTO;
import io.harness.entities.instanceinfo.GoogleFunctionInstanceInfo;

import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.CDP)
@UtilityClass
public class GoogleFunctionInstanceInfoMapper {
  public GoogleFunctionInstanceInfoDTO toDTO(GoogleFunctionInstanceInfo googleFunctionInstanceInfo) {
    return GoogleFunctionInstanceInfoDTO.builder()
        .revision(googleFunctionInstanceInfo.getRevision())
        .functionName(googleFunctionInstanceInfo.getFunctionName())
        .project(googleFunctionInstanceInfo.getProject())
        .region(googleFunctionInstanceInfo.getRegion())
        .source(googleFunctionInstanceInfo.getSource())
        .updatedTime(googleFunctionInstanceInfo.getUpdatedTime())
        .memorySize(googleFunctionInstanceInfo.getMemorySize())
        .runTime(googleFunctionInstanceInfo.getRunTime())
        .infraStructureKey(googleFunctionInstanceInfo.getInfraStructureKey())
        .build();
  }

  public GoogleFunctionInstanceInfo toEntity(GoogleFunctionInstanceInfoDTO googleFunctionInstanceInfoDTO) {
    return GoogleFunctionInstanceInfo.builder()
        .functionName(googleFunctionInstanceInfoDTO.getFunctionName())
        .project(googleFunctionInstanceInfoDTO.getProject())
        .region(googleFunctionInstanceInfoDTO.getRegion())
        .revision(googleFunctionInstanceInfoDTO.getRevision())
        .source(googleFunctionInstanceInfoDTO.getSource())
        .updatedTime(googleFunctionInstanceInfoDTO.getUpdatedTime())
        .memorySize(googleFunctionInstanceInfoDTO.getMemorySize())
        .runTime(googleFunctionInstanceInfoDTO.getRunTime())
        .infraStructureKey(googleFunctionInstanceInfoDTO.getInfraStructureKey())
        .build();
  }
}
