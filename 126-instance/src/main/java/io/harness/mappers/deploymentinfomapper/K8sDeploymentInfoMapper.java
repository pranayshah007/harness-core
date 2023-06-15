/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.mappers.deploymentinfomapper;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.dtos.deploymentinfo.K8sDeploymentInfoDTO;
import io.harness.entities.deploymentinfo.K8sDeploymentInfo;

import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.DX)
@UtilityClass
public class K8sDeploymentInfoMapper {
  public K8sDeploymentInfoDTO toDTO(K8sDeploymentInfo k8sDeploymentInfo) {
    return K8sDeploymentInfoDTO.builder()
        .blueGreenStageColor(k8sDeploymentInfo.getBlueGreenStageColor())
        .namespaces(k8sDeploymentInfo.getNamespaces())
        .releaseName(k8sDeploymentInfo.getReleaseName())
        .cloudConfigMetadata(k8sDeploymentInfo.getCloudConfigMetadata())
        .build();
  }

  public K8sDeploymentInfo toEntity(K8sDeploymentInfoDTO k8sDeploymentInfoDTO) {
    return K8sDeploymentInfo.builder()
        .blueGreenStageColor(k8sDeploymentInfoDTO.getBlueGreenStageColor())
        .namespaces(k8sDeploymentInfoDTO.getNamespaces())
        .releaseName(k8sDeploymentInfoDTO.getReleaseName())
        .cloudConfigMetadata(k8sDeploymentInfoDTO.getCloudConfigMetadata())
        .build();
  }
}
