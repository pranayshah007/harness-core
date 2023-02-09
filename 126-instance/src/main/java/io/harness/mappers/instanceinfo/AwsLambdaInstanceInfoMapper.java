/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.mappers.instanceinfo;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.dtos.instanceinfo.AwsLambdaInstanceInfoDTO;
import io.harness.dtos.instanceinfo.GoogleFunctionInstanceInfoDTO;
import io.harness.entities.instanceinfo.AwsLambdaInstanceInfo;
import io.harness.entities.instanceinfo.GoogleFunctionInstanceInfo;
import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.CDP)
@UtilityClass
public class AwsLambdaInstanceInfoMapper {
  public AwsLambdaInstanceInfoDTO toDTO(AwsLambdaInstanceInfo awsLambdaInstanceInfo) {
    return AwsLambdaInstanceInfoDTO.builder()
        .functionName(awsLambdaInstanceInfo.getFunctionName())
        .region(awsLambdaInstanceInfo.getRegion())
        .source(awsLambdaInstanceInfo.getSource())
        .updatedTime(awsLambdaInstanceInfo.getUpdatedTime())
        .memorySize(awsLambdaInstanceInfo.getMemorySize())
        .runTime(awsLambdaInstanceInfo.getRunTime())
        .infraStructureKey(awsLambdaInstanceInfo.getInfraStructureKey())
        .build();
  }

  public AwsLambdaInstanceInfo toEntity(AwsLambdaInstanceInfoDTO awsLambdaInstanceInfoDTO) {
    return AwsLambdaInstanceInfo.builder()
        .functionName(awsLambdaInstanceInfoDTO.getFunctionName())
        .project(awsLambdaInstanceInfoDTO.getProject())
        .region(awsLambdaInstanceInfoDTO.getRegion())
        .revision(awsLambdaInstanceInfoDTO.getRevision())
        .source(awsLambdaInstanceInfoDTO.getSource())
        .updatedTime(awsLambdaInstanceInfoDTO.getUpdatedTime())
        .memorySize(awsLambdaInstanceInfoDTO.getMemorySize())
        .runTime(awsLambdaInstanceInfoDTO.getRunTime())
        .infraStructureKey(awsLambdaInstanceInfoDTO.getInfraStructureKey())
        .build();
  }
}
