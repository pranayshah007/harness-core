/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.mappers.instanceinfo;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.dtos.instanceinfo.AsgInstanceInfoDTO;
import io.harness.dtos.instanceinfo.AwsLambdaInstanceInfoDTO;
import io.harness.dtos.instanceinfo.AwsSshWinrmInstanceInfoDTO;
import io.harness.dtos.instanceinfo.AzureSshWinrmInstanceInfoDTO;
import io.harness.dtos.instanceinfo.AzureWebAppInstanceInfoDTO;
import io.harness.dtos.instanceinfo.CustomDeploymentInstanceInfoDTO;
import io.harness.dtos.instanceinfo.EcsInstanceInfoDTO;
import io.harness.dtos.instanceinfo.GitOpsInstanceInfoDTO;
import io.harness.dtos.instanceinfo.GoogleFunctionInstanceInfoDTO;
import io.harness.dtos.instanceinfo.InstanceInfoDTO;
import io.harness.dtos.instanceinfo.K8sInstanceInfoDTO;
import io.harness.dtos.instanceinfo.NativeHelmInstanceInfoDTO;
import io.harness.dtos.instanceinfo.PdcInstanceInfoDTO;
import io.harness.dtos.instanceinfo.ReferenceInstanceInfoDTO;
import io.harness.dtos.instanceinfo.ServerlessAwsLambdaInstanceInfoDTO;
import io.harness.dtos.instanceinfo.SpotInstanceInfoDTO;
import io.harness.dtos.instanceinfo.TasInstanceInfoDTO;
import io.harness.entities.instanceinfo.AsgInstanceInfo;
import io.harness.entities.instanceinfo.AwsLambdaInstanceInfo;
import io.harness.entities.instanceinfo.AwsSshWinrmInstanceInfo;
import io.harness.entities.instanceinfo.AzureSshWinrmInstanceInfo;
import io.harness.entities.instanceinfo.AzureWebAppNGInstanceInfo;
import io.harness.entities.instanceinfo.CustomDeploymentInstanceInfo;
import io.harness.entities.instanceinfo.EcsInstanceInfo;
import io.harness.entities.instanceinfo.GitopsInstanceInfo;
import io.harness.entities.instanceinfo.GoogleFunctionInstanceInfo;
import io.harness.entities.instanceinfo.InstanceInfo;
import io.harness.entities.instanceinfo.K8sInstanceInfo;
import io.harness.entities.instanceinfo.NativeHelmInstanceInfo;
import io.harness.entities.instanceinfo.PdcInstanceInfo;
import io.harness.entities.instanceinfo.ReferenceInstanceInfo;
import io.harness.entities.instanceinfo.ServerlessAwsLambdaInstanceInfo;
import io.harness.entities.instanceinfo.SpotInstanceInfo;
import io.harness.entities.instanceinfo.TasInstanceInfo;
import io.harness.exception.InvalidRequestException;

import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.DX)
@UtilityClass
public class InstanceInfoMapper {
  public InstanceInfoDTO toDTO(InstanceInfo instanceInfo) {
    if (instanceInfo instanceof ReferenceInstanceInfo) {
      return ReferenceInstanceInfoMapper.toDTO((ReferenceInstanceInfo) instanceInfo);
    } else if (instanceInfo instanceof K8sInstanceInfo) {
      return K8sInstanceInfoMapper.toDTO((K8sInstanceInfo) instanceInfo);
    } else if (instanceInfo instanceof NativeHelmInstanceInfo) {
      return NativeHelmInstanceInfoMapper.toDTO((NativeHelmInstanceInfo) instanceInfo);
    } else if (instanceInfo instanceof ServerlessAwsLambdaInstanceInfo) {
      return ServerlessAwsLambdaInstanceInfoMapper.toDTO((ServerlessAwsLambdaInstanceInfo) instanceInfo);
    } else if (instanceInfo instanceof GitopsInstanceInfo) {
      return GitOpsInstanceInfoMapper.toDTO((GitopsInstanceInfo) instanceInfo);
    } else if (instanceInfo instanceof AzureWebAppNGInstanceInfo) {
      return AzureWebAppInstanceInfoMapper.toDTO((AzureWebAppNGInstanceInfo) instanceInfo);
    } else if (instanceInfo instanceof EcsInstanceInfo) {
      return EcsInstanceInfoMapper.toDTO((EcsInstanceInfo) instanceInfo);
    } else if (instanceInfo instanceof PdcInstanceInfo) {
      return PdcInstanceInfoMapper.toDTO((PdcInstanceInfo) instanceInfo);
    } else if (instanceInfo instanceof AzureSshWinrmInstanceInfo) {
      return AzureSshWinrmInstanceInfoMapper.toDTO((AzureSshWinrmInstanceInfo) instanceInfo);
    } else if (instanceInfo instanceof AwsSshWinrmInstanceInfo) {
      return AwsSshWinrmInstanceInfoMapper.toDTO((AwsSshWinrmInstanceInfo) instanceInfo);
    } else if (instanceInfo instanceof CustomDeploymentInstanceInfo) {
      return CustomDeploymentInstanceInfoMapper.toDTO((CustomDeploymentInstanceInfo) instanceInfo);
    } else if (instanceInfo instanceof SpotInstanceInfo) {
      return SpotInstanceInfoMapper.toDTO((SpotInstanceInfo) instanceInfo);
    } else if (instanceInfo instanceof TasInstanceInfo) {
      return TasInstanceInfoMapper.toDTO((TasInstanceInfo) instanceInfo);
    } else if (instanceInfo instanceof AsgInstanceInfo) {
      return AsgInstanceInfoMapper.toDTO((AsgInstanceInfo) instanceInfo);
    } else if (instanceInfo instanceof GoogleFunctionInstanceInfo) {
      return GoogleFunctionInstanceInfoMapper.toDTO((GoogleFunctionInstanceInfo) instanceInfo);
    } else if (instanceInfo instanceof AwsLambdaInstanceInfo) {
      return AwsLambdaInstanceInfoMapper.toDTO((AwsLambdaInstanceInfo) instanceInfo);
    }
    throw new InvalidRequestException("No InstanceInfoMapper toDTO found for instanceInfo : {}" + instanceInfo);
  }

  public InstanceInfo toEntity(InstanceInfoDTO instanceInfoDTO) {
    if (instanceInfoDTO instanceof ReferenceInstanceInfoDTO) {
      return ReferenceInstanceInfoMapper.toEntity((ReferenceInstanceInfoDTO) instanceInfoDTO);
    } else if (instanceInfoDTO instanceof K8sInstanceInfoDTO) {
      return K8sInstanceInfoMapper.toEntity((K8sInstanceInfoDTO) instanceInfoDTO);
    } else if (instanceInfoDTO instanceof NativeHelmInstanceInfoDTO) {
      return NativeHelmInstanceInfoMapper.toEntity((NativeHelmInstanceInfoDTO) instanceInfoDTO);
    } else if (instanceInfoDTO instanceof ServerlessAwsLambdaInstanceInfoDTO) {
      return ServerlessAwsLambdaInstanceInfoMapper.toEntity((ServerlessAwsLambdaInstanceInfoDTO) instanceInfoDTO);
    } else if (instanceInfoDTO instanceof GitOpsInstanceInfoDTO) {
      return GitOpsInstanceInfoMapper.toEntity((GitOpsInstanceInfoDTO) instanceInfoDTO);
    } else if (instanceInfoDTO instanceof AzureWebAppInstanceInfoDTO) {
      return AzureWebAppInstanceInfoMapper.toEntity((AzureWebAppInstanceInfoDTO) instanceInfoDTO);
    } else if (instanceInfoDTO instanceof EcsInstanceInfoDTO) {
      return EcsInstanceInfoMapper.toEntity((EcsInstanceInfoDTO) instanceInfoDTO);
    } else if (instanceInfoDTO instanceof PdcInstanceInfoDTO) {
      return PdcInstanceInfoMapper.toEntity((PdcInstanceInfoDTO) instanceInfoDTO);
    } else if (instanceInfoDTO instanceof AzureSshWinrmInstanceInfoDTO) {
      return AzureSshWinrmInstanceInfoMapper.toEntity((AzureSshWinrmInstanceInfoDTO) instanceInfoDTO);
    } else if (instanceInfoDTO instanceof AwsSshWinrmInstanceInfoDTO) {
      return AwsSshWinrmInstanceInfoMapper.toEntity((AwsSshWinrmInstanceInfoDTO) instanceInfoDTO);
    } else if (instanceInfoDTO instanceof CustomDeploymentInstanceInfoDTO) {
      return CustomDeploymentInstanceInfoMapper.toEntity((CustomDeploymentInstanceInfoDTO) instanceInfoDTO);
    } else if (instanceInfoDTO instanceof SpotInstanceInfoDTO) {
      return SpotInstanceInfoMapper.toEntity((SpotInstanceInfoDTO) instanceInfoDTO);
    } else if (instanceInfoDTO instanceof TasInstanceInfoDTO) {
      return TasInstanceInfoMapper.toEntity((TasInstanceInfoDTO) instanceInfoDTO);
    } else if (instanceInfoDTO instanceof AsgInstanceInfoDTO) {
      return AsgInstanceInfoMapper.toEntity((AsgInstanceInfoDTO) instanceInfoDTO);
    } else if (instanceInfoDTO instanceof GoogleFunctionInstanceInfoDTO) {
      return GoogleFunctionInstanceInfoMapper.toEntity((GoogleFunctionInstanceInfoDTO) instanceInfoDTO);
    } else if (instanceInfoDTO instanceof AwsLambdaInstanceInfoDTO) {
      return AwsLambdaInstanceInfoMapper.toEntity((AwsLambdaInstanceInfoDTO) instanceInfoDTO);
    }
    throw new InvalidRequestException(
        "No InstanceInfoMapper toEntity found for instanceInfoDTO : {}" + instanceInfoDTO);
  }
}
