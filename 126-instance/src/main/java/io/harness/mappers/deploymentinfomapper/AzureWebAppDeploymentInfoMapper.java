/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.mappers.deploymentinfomapper;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.dtos.deploymentinfo.AzureWebAppDeploymentInfoDTO;
import io.harness.entities.deploymentinfo.AzureWebAppNGDeploymentInfo;

import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.CDP)
@UtilityClass
public class AzureWebAppDeploymentInfoMapper {
  public AzureWebAppDeploymentInfoDTO toDTO(AzureWebAppNGDeploymentInfo azureWebAppNGDeploymentInfo) {
    return AzureWebAppDeploymentInfoDTO.builder()
        .appName(azureWebAppNGDeploymentInfo.getAppName())
        .subscriptionId(azureWebAppNGDeploymentInfo.getSubscriptionId())
        .resourceGroup(azureWebAppNGDeploymentInfo.getResourceGroup())
        .slotName(azureWebAppNGDeploymentInfo.getSlotName())
        .build();
  }

  public AzureWebAppNGDeploymentInfo toEntity(AzureWebAppDeploymentInfoDTO azureWebAppDeploymentInfoDTO) {
    return AzureWebAppNGDeploymentInfo.builder()
        .appName(azureWebAppDeploymentInfoDTO.getAppName())
        .subscriptionId(azureWebAppDeploymentInfoDTO.getSubscriptionId())
        .resourceGroup(azureWebAppDeploymentInfoDTO.getResourceGroup())
        .slotName(azureWebAppDeploymentInfoDTO.getSlotName())
        .build();
  }
}
