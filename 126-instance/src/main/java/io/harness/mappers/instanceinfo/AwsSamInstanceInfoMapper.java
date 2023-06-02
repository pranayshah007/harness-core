/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.mappers.instanceinfo;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.dtos.instanceinfo.AwsSamInstanceInfoDTO;
import io.harness.entities.instanceinfo.AwsSamInstanceInfo;

import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.CDP)
@UtilityClass
public class AwsSamInstanceInfoMapper {
  public AwsSamInstanceInfoDTO toDTO(AwsSamInstanceInfo awsSamInstanceInfo) {
    return AwsSamInstanceInfoDTO.builder()
        .functionName(awsSamInstanceInfo.getFunctionName())
        .region(awsSamInstanceInfo.getRegion())
        .handler(awsSamInstanceInfo.getHandler())
        .memorySize(awsSamInstanceInfo.getMemorySize())
        .runTime(awsSamInstanceInfo.getRunTime())
        .infraStructureKey(awsSamInstanceInfo.getInfraStructureKey())
        .build();
  }

  public AwsSamInstanceInfo toEntity(AwsSamInstanceInfoDTO awsSamInstanceInfoDTO) {
    return AwsSamInstanceInfo.builder()
        .functionName(awsSamInstanceInfoDTO.getFunctionName())
        .region(awsSamInstanceInfoDTO.getRegion())
        .handler(awsSamInstanceInfoDTO.getHandler())
        .memorySize(awsSamInstanceInfoDTO.getMemorySize())
        .runTime(awsSamInstanceInfoDTO.getRunTime())
        .infraStructureKey(awsSamInstanceInfoDTO.getInfraStructureKey())
        .build();
  }
}
