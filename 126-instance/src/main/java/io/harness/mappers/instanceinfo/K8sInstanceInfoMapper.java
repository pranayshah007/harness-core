/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.mappers.instanceinfo;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.dtos.instanceinfo.K8sInstanceInfoDTO;
import io.harness.entities.instanceinfo.K8sInstanceInfo;

import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.DX)
@UtilityClass
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_K8S})
public class K8sInstanceInfoMapper {
  public K8sInstanceInfoDTO toDTO(K8sInstanceInfo k8sInstanceInfo) {
    return K8sInstanceInfoDTO.builder()
        .blueGreenColor(k8sInstanceInfo.getBlueGreenColor())
        .containerList(k8sInstanceInfo.getContainerList())
        .namespace(k8sInstanceInfo.getNamespace())
        .podIP(k8sInstanceInfo.getPodIP())
        .podName(k8sInstanceInfo.getPodName())
        .releaseName(k8sInstanceInfo.getReleaseName())
        .helmChartInfo(k8sInstanceInfo.getHelmChartInfo())
        .canary(Boolean.TRUE.equals(k8sInstanceInfo.getCanary()))
        .build();
  }

  public K8sInstanceInfo toEntity(K8sInstanceInfoDTO k8sInstanceInfoDTO) {
    return K8sInstanceInfo.builder()
        .blueGreenColor(k8sInstanceInfoDTO.getBlueGreenColor())
        .containerList(k8sInstanceInfoDTO.getContainerList())
        .namespace(k8sInstanceInfoDTO.getNamespace())
        .podIP(k8sInstanceInfoDTO.getPodIP())
        .podName(k8sInstanceInfoDTO.getPodName())
        .releaseName(k8sInstanceInfoDTO.getReleaseName())
        .helmChartInfo(k8sInstanceInfoDTO.getHelmChartInfo())
        .canary(k8sInstanceInfoDTO.isCanary())
        .build();
  }
}
