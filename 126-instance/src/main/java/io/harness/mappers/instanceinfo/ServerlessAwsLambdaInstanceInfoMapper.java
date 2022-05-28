/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.mappers.instanceinfo;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.dtos.instanceinfo.ServerlessAwsLambdaInstanceInfoDTO;
import io.harness.entities.instanceinfo.ServerlessAwsLambdaInstanceInfo;

import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.CDP)
@UtilityClass
public class ServerlessAwsLambdaInstanceInfoMapper {
  public ServerlessAwsLambdaInstanceInfoDTO toDTO(ServerlessAwsLambdaInstanceInfo serverlessAwsLambdaInstanceInfo) {
    return ServerlessAwsLambdaInstanceInfoDTO.builder()
        .functionName(serverlessAwsLambdaInstanceInfo.getFunctionName())
        .handler(serverlessAwsLambdaInstanceInfo.getHandler())
        .memorySize(serverlessAwsLambdaInstanceInfo.getMemorySize())
        .region(serverlessAwsLambdaInstanceInfo.getRegion())
        .runTime(serverlessAwsLambdaInstanceInfo.getRunTime())
        .serviceName(serverlessAwsLambdaInstanceInfo.getServiceName())
        .stage(serverlessAwsLambdaInstanceInfo.getStage())
        .timeout(serverlessAwsLambdaInstanceInfo.getTimeout())
        .infraStructureKey(serverlessAwsLambdaInstanceInfo.getInfraStructureKey())
        .build();
  }

  public ServerlessAwsLambdaInstanceInfo toEntity(
      ServerlessAwsLambdaInstanceInfoDTO serverlessAwsLambdaInstanceInfoDTO) {
    return ServerlessAwsLambdaInstanceInfo.builder()
        .functionName(serverlessAwsLambdaInstanceInfoDTO.getFunctionName())
        .handler(serverlessAwsLambdaInstanceInfoDTO.getHandler())
        .memorySize(serverlessAwsLambdaInstanceInfoDTO.getMemorySize())
        .region(serverlessAwsLambdaInstanceInfoDTO.getRegion())
        .serviceName(serverlessAwsLambdaInstanceInfoDTO.getServiceName())
        .runTime(serverlessAwsLambdaInstanceInfoDTO.getRunTime())
        .stage(serverlessAwsLambdaInstanceInfoDTO.getStage())
        .timeout(serverlessAwsLambdaInstanceInfoDTO.getTimeout())
        .infraStructureKey(serverlessAwsLambdaInstanceInfoDTO.getInfraStructureKey())
        .build();
  }
}
