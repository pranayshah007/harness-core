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
import io.harness.entities.instanceinfo.AwsLambdaInstanceInfo;

import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.CDP)
@UtilityClass
public class AwsLambdaInstanceInfoMapper {
  public AwsLambdaInstanceInfoDTO toDTO(AwsLambdaInstanceInfo awsLambdaInstanceInfo) {
    return AwsLambdaInstanceInfoDTO.builder()
        .functionName(awsLambdaInstanceInfo.getFunctionName())
        .region(awsLambdaInstanceInfo.getRegion())
        .source(awsLambdaInstanceInfo.getSource())
        .version(awsLambdaInstanceInfo.getVersion())
        .updatedTime(awsLambdaInstanceInfo.getUpdatedTime())
        .memorySize(awsLambdaInstanceInfo.getMemorySize())
        .runTime(awsLambdaInstanceInfo.getRunTime())
        .infraStructureKey(awsLambdaInstanceInfo.getInfraStructureKey())
        .tags(awsLambdaInstanceInfo.getTags())
        .aliases(awsLambdaInstanceInfo.getAliases())
        .description(awsLambdaInstanceInfo.getDescription())
        .functionArn(awsLambdaInstanceInfo.getFunctionArn())
        .handler(awsLambdaInstanceInfo.getHandler())
        .build();
  }

  public AwsLambdaInstanceInfo toEntity(AwsLambdaInstanceInfoDTO awsLambdaInstanceInfoDTO) {
    return AwsLambdaInstanceInfo.builder()
        .functionName(awsLambdaInstanceInfoDTO.getFunctionName())
        .region(awsLambdaInstanceInfoDTO.getRegion())
        .version(awsLambdaInstanceInfoDTO.getVersion())
        .source(awsLambdaInstanceInfoDTO.getSource())
        .updatedTime(awsLambdaInstanceInfoDTO.getUpdatedTime())
        .memorySize(awsLambdaInstanceInfoDTO.getMemorySize())
        .runTime(awsLambdaInstanceInfoDTO.getRunTime())
        .infraStructureKey(awsLambdaInstanceInfoDTO.getInfraStructureKey())
        .tags(awsLambdaInstanceInfoDTO.getTags())
        .aliases(awsLambdaInstanceInfoDTO.getAliases())
        .description(awsLambdaInstanceInfoDTO.getDescription())
        .functionArn(awsLambdaInstanceInfoDTO.getFunctionArn())
        .handler(awsLambdaInstanceInfoDTO.getHandler())
        .build();
  }
}
