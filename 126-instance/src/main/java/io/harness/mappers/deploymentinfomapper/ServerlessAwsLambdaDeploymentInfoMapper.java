/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.mappers.deploymentinfomapper;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.dtos.deploymentinfo.ServerlessAwsLambdaDeploymentInfoDTO;
import io.harness.entities.deploymentinfo.ServerlessAwsLambdaDeploymentInfo;

import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.CDP)
@UtilityClass
public class ServerlessAwsLambdaDeploymentInfoMapper {
  public ServerlessAwsLambdaDeploymentInfoDTO toDTO(
      ServerlessAwsLambdaDeploymentInfo serverlessAwsLambdaDeploymentInfo) {
    return ServerlessAwsLambdaDeploymentInfoDTO.builder()
        .serviceName(serverlessAwsLambdaDeploymentInfo.getServiceName())
        .region(serverlessAwsLambdaDeploymentInfo.getRegion())
        .functions(serverlessAwsLambdaDeploymentInfo.getFunctions())
        .infraStructureKey(serverlessAwsLambdaDeploymentInfo.getInfraStructureKey())
        .build();
  }

  public ServerlessAwsLambdaDeploymentInfo toEntity(
      ServerlessAwsLambdaDeploymentInfoDTO serverlessAwsLambdaDeploymentInfoDTO) {
    return ServerlessAwsLambdaDeploymentInfo.builder()
        .serviceName(serverlessAwsLambdaDeploymentInfoDTO.getServiceName())
        .region(serverlessAwsLambdaDeploymentInfoDTO.getRegion())
        .functions(serverlessAwsLambdaDeploymentInfoDTO.getFunctions())
        .infraStructureKey(serverlessAwsLambdaDeploymentInfoDTO.getInfraStructureKey())
        .build();
  }
}
