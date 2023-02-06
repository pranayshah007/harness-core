/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.mappers.deploymentinfomapper;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.dtos.deploymentinfo.AwsLambdaDeploymentInfoDTO;
import io.harness.dtos.deploymentinfo.GoogleFunctionDeploymentInfoDTO;
import io.harness.entities.deploymentinfo.AwsLambdaDeploymentInfo;
import io.harness.entities.deploymentinfo.GoogleFunctionDeploymentInfo;
import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.CDP)
@UtilityClass
public class AwsLambdaDeploymentInfoMapper {
  public AwsLambdaDeploymentInfoDTO toDTO(AwsLambdaDeploymentInfo awsLambdaDeploymentInfo) {
    return AwsLambdaDeploymentInfoDTO.builder()
        .revision(awsLambdaDeploymentInfo.getRevision())
        .functionName(awsLambdaDeploymentInfo.getFunctionName())
        .project(awsLambdaDeploymentInfo.getProject())
        .region(awsLambdaDeploymentInfo.getRegion())
        .infraStructureKey(awsLambdaDeploymentInfo.getInfraStructureKey())
        .build();
  }

  public AwsLambdaDeploymentInfo toEntity(AwsLambdaDeploymentInfoDTO awsLambdaDeploymentInfoDTO) {
    return AwsLambdaDeploymentInfo.builder()
        .revision(awsLambdaDeploymentInfoDTO.getRevision())
        .functionName(awsLambdaDeploymentInfoDTO.getFunctionName())
        .project(awsLambdaDeploymentInfoDTO.getProject())
        .region(awsLambdaDeploymentInfoDTO.getRegion())
        .infraStructureKey(awsLambdaDeploymentInfoDTO.getInfraStructureKey())
        .build();
  }
}
