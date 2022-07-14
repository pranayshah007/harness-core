/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.mappers.instanceinfo;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.dtos.instanceinfo.AzureWebAppInstanceInfoDTO;
import io.harness.entities.instanceinfo.AzureWebAppNGInstanceInfo;

import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.CDP)
@UtilityClass
public class AzureWebAppInstanceInfoMapper {
  public AzureWebAppInstanceInfoDTO toDTO(AzureWebAppNGInstanceInfo azureWebAppNGInstanceInfo) {
    return AzureWebAppInstanceInfoDTO.builder()
        .instanceName(azureWebAppNGInstanceInfo.getInstanceName())
        .resourceGroup(azureWebAppNGInstanceInfo.getResourceGroup())
        .subscriptionId(azureWebAppNGInstanceInfo.getSubscriptionId())
        .appName(azureWebAppNGInstanceInfo.getAppName())
        .deploySlot(azureWebAppNGInstanceInfo.getDeploySlot())
        .deploySlotId(azureWebAppNGInstanceInfo.getDeploySlotId())
        .appServicePlanId(azureWebAppNGInstanceInfo.getHostName())
        .instanceIp(azureWebAppNGInstanceInfo.getInstanceIp())
        .instanceState(azureWebAppNGInstanceInfo.getInstanceState())
        .instanceId(azureWebAppNGInstanceInfo.getInstanceId())
        .build();
  }

  public AzureWebAppNGInstanceInfo toEntity(AzureWebAppInstanceInfoDTO azureWebAppInstanceInfoDTO) {
    return AzureWebAppNGInstanceInfo.builder()
        .instanceName(azureWebAppInstanceInfoDTO.getInstanceName())
        .resourceGroup(azureWebAppInstanceInfoDTO.getResourceGroup())
        .subscriptionId(azureWebAppInstanceInfoDTO.getSubscriptionId())
        .appName(azureWebAppInstanceInfoDTO.getAppName())
        .deploySlot(azureWebAppInstanceInfoDTO.getDeploySlot())
        .deploySlotId(azureWebAppInstanceInfoDTO.getDeploySlotId())
        .appServicePlanId(azureWebAppInstanceInfoDTO.getAppServicePlanId())
        .instanceIp(azureWebAppInstanceInfoDTO.getInstanceIp())
        .instanceState(azureWebAppInstanceInfoDTO.getInstanceState())
        .instanceId(azureWebAppInstanceInfoDTO.getInstanceId())
        .build();
  }
}
