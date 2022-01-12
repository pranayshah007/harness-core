/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.mappers.instanceinfo;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.dtos.instanceinfo.NativeHelmInstanceInfoDTO;
import io.harness.entities.instanceinfo.NativeHelmInstanceInfo;

import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.CDP)
@UtilityClass
public class NativeHelmInstanceInfoMapper {
  public NativeHelmInstanceInfoDTO toDTO(NativeHelmInstanceInfo nativeHelmInstanceInfo) {
    return NativeHelmInstanceInfoDTO.builder()
        .namespace(nativeHelmInstanceInfo.getNamespace())
        .ip(nativeHelmInstanceInfo.getIp())
        .podName(nativeHelmInstanceInfo.getPodName())
        .releaseName(nativeHelmInstanceInfo.getReleaseName())
        .helmChartInfo(nativeHelmInstanceInfo.getHelmChartInfo())
        .helmVersion(nativeHelmInstanceInfo.getHelmVersion())
        .build();
  }

  public NativeHelmInstanceInfo toEntity(NativeHelmInstanceInfoDTO nativeHelmInstanceInfoDTO) {
    return NativeHelmInstanceInfo.builder()
        .namespace(nativeHelmInstanceInfoDTO.getNamespace())
        .ip(nativeHelmInstanceInfoDTO.getIp())
        .podName(nativeHelmInstanceInfoDTO.getPodName())
        .releaseName(nativeHelmInstanceInfoDTO.getReleaseName())
        .helmChartInfo(nativeHelmInstanceInfoDTO.getHelmChartInfo())
        .helmVersion(nativeHelmInstanceInfoDTO.getHelmVersion())
        .build();
  }
}
