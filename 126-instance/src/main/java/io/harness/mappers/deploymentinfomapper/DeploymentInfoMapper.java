/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.mappers.deploymentinfomapper;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.dtos.deploymentinfo.DeploymentInfoDTO;
import io.harness.dtos.deploymentinfo.K8sDeploymentInfoDTO;
import io.harness.dtos.deploymentinfo.NativeHelmDeploymentInfoDTO;
import io.harness.dtos.deploymentinfo.ReferenceK8sPodInfoDTO;
import io.harness.dtos.deploymentinfo.ServerlessAwsLambdaDeploymentInfoDTO;
import io.harness.entities.deploymentinfo.DeploymentInfo;
import io.harness.entities.deploymentinfo.K8sDeploymentInfo;
import io.harness.entities.deploymentinfo.NativeHelmDeploymentInfo;
import io.harness.entities.deploymentinfo.ReferenceK8sPodInfo;
import io.harness.entities.deploymentinfo.ServerlessAwsLambdaDeploymentInfo;
import io.harness.exception.InvalidRequestException;

import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.DX)
@UtilityClass
public class DeploymentInfoMapper {
  public DeploymentInfoDTO toDTO(DeploymentInfo deploymentInfo) {
    if (deploymentInfo instanceof ReferenceK8sPodInfo) {
      return ReferenceK8sPodInfoMapper.toDTO((ReferenceK8sPodInfo) deploymentInfo);
    } else if (deploymentInfo instanceof K8sDeploymentInfo) {
      return K8sDeploymentInfoMapper.toDTO((K8sDeploymentInfo) deploymentInfo);
    } else if (deploymentInfo instanceof NativeHelmDeploymentInfo) {
      return NativeHelmDeploymentInfoMapper.toDTO((NativeHelmDeploymentInfo) deploymentInfo);
    } else if (deploymentInfo instanceof ServerlessAwsLambdaDeploymentInfo) {
      return ServerlessAwsLambdaDeploymentInfoMapper.toDTO((ServerlessAwsLambdaDeploymentInfo) deploymentInfo);
    }
    throw new InvalidRequestException("No DeploymentInfoMapper toDTO found for deploymentInfo : {}" + deploymentInfo);
  }

  public DeploymentInfo toEntity(DeploymentInfoDTO deploymentInfoDTO) {
    if (deploymentInfoDTO instanceof ReferenceK8sPodInfoDTO) {
      return ReferenceK8sPodInfoMapper.toEntity((ReferenceK8sPodInfoDTO) deploymentInfoDTO);
    } else if (deploymentInfoDTO instanceof K8sDeploymentInfoDTO) {
      return K8sDeploymentInfoMapper.toEntity((K8sDeploymentInfoDTO) deploymentInfoDTO);
    } else if (deploymentInfoDTO instanceof NativeHelmDeploymentInfoDTO) {
      return NativeHelmDeploymentInfoMapper.toEntity((NativeHelmDeploymentInfoDTO) deploymentInfoDTO);
    } else if (deploymentInfoDTO instanceof ServerlessAwsLambdaDeploymentInfoDTO) {
      return ServerlessAwsLambdaDeploymentInfoMapper.toEntity((ServerlessAwsLambdaDeploymentInfoDTO) deploymentInfoDTO);
    }
    throw new InvalidRequestException(
        "No DeploymentInfoMapper toEntity found for deploymentInfo : {}" + deploymentInfoDTO);
  }
}
