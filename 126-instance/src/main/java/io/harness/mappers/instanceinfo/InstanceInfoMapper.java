/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.mappers.instanceinfo;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.dtos.instanceinfo.InstanceInfoDTO;
import io.harness.dtos.instanceinfo.K8sInstanceInfoDTO;
import io.harness.dtos.instanceinfo.NativeHelmInstanceInfoDTO;
import io.harness.dtos.instanceinfo.ReferenceInstanceInfoDTO;
import io.harness.dtos.instanceinfo.ServerlessAwsLambdaInstanceInfoDTO;
import io.harness.entities.instanceinfo.InstanceInfo;
import io.harness.entities.instanceinfo.K8sInstanceInfo;
import io.harness.entities.instanceinfo.NativeHelmInstanceInfo;
import io.harness.entities.instanceinfo.ReferenceInstanceInfo;
import io.harness.entities.instanceinfo.ServerlessAwsLambdaInstanceInfo;
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
    }
    throw new InvalidRequestException(
        "No InstanceInfoMapper toEntity found for instanceInfoDTO : {}" + instanceInfoDTO);
  }
}
