/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.beans.instancesync.mapper;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.instancesync.ServerInstanceInfo;
import io.harness.delegate.beans.instancesync.info.AzureWebAppServerInstanceInfo;
import io.harness.delegate.task.azure.appservice.webapp.response.AzureAppDeploymentData;

import java.util.List;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(HarnessTeam.CDP)
public class AzureWebAppToServerInstanceInfoMapper {
  public List<ServerInstanceInfo> toServerInstanceInfoList(List<AzureAppDeploymentData> azureAppDeploymentData) {
    return azureAppDeploymentData.stream()
        .map(AzureWebAppToServerInstanceInfoMapper::toServerInstanceInfo)
        .collect(Collectors.toList());
  }

  public ServerInstanceInfo toServerInstanceInfo(AzureAppDeploymentData azureAppDeploymentData) {
    return AzureWebAppServerInstanceInfo.builder()
        .instanceId(azureAppDeploymentData.getInstanceId())
        .instanceType(azureAppDeploymentData.getInstanceType())
        .instanceName(azureAppDeploymentData.getInstanceName())
        .resourceGroup(azureAppDeploymentData.getResourceGroup())
        .subscriptionId(azureAppDeploymentData.getSubscriptionId())
        .appName(azureAppDeploymentData.getAppName())
        .deploySlot(azureAppDeploymentData.getDeploySlot())
        .deploySlotId(azureAppDeploymentData.getDeploySlotId())
        .appServicePlanId(azureAppDeploymentData.getAppServicePlanId())
        .hostName(azureAppDeploymentData.getHostName())
        .instanceIp(azureAppDeploymentData.getInstanceIp())
        .instanceState(azureAppDeploymentData.getInstanceState())
        .build();
  }
}
