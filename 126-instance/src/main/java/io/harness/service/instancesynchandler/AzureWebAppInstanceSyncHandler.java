/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.service.instancesynchandler;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.infra.beans.AzureWebAppInfrastructureOutcome;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.delegate.beans.instancesync.ServerInstanceInfo;
import io.harness.delegate.beans.instancesync.info.AzureWebAppServerInstanceInfo;
import io.harness.dtos.deploymentinfo.AzureWebAppDeploymentInfoDTO;
import io.harness.dtos.deploymentinfo.DeploymentInfoDTO;
import io.harness.dtos.instanceinfo.AzureWebAppInstanceInfoDTO;
import io.harness.dtos.instanceinfo.InstanceInfoDTO;
import io.harness.entities.InstanceType;
import io.harness.exception.InvalidArgumentsException;
import io.harness.models.infrastructuredetails.AzureWebAppInfrastructureDetails;
import io.harness.models.infrastructuredetails.InfrastructureDetails;
import io.harness.ng.core.infrastructure.InfrastructureKind;
import io.harness.perpetualtask.PerpetualTaskType;

import com.google.inject.Singleton;
import java.util.List;
import org.apache.commons.lang3.tuple.Pair;

@OwnedBy(HarnessTeam.CDP)
@Singleton
public class AzureWebAppInstanceSyncHandler extends AbstractInstanceSyncHandler {
  @Override
  public String getPerpetualTaskType() {
    return PerpetualTaskType.AZURE_WEB_APP_NG_INSTANCE_SYNC;
  }

  @Override
  public InstanceType getInstanceType() {
    return InstanceType.AZURE_WEB_APP_INSTANCE;
  }

  @Override
  public String getInfrastructureKind() {
    return InfrastructureKind.AZURE_WEB_APP;
  }

  @Override
  public InfrastructureDetails getInfrastructureDetails(InstanceInfoDTO instanceInfoDTO) {
    if (instanceInfoDTO instanceof AzureWebAppInstanceInfoDTO) {
      AzureWebAppInstanceInfoDTO azureWebAppInstanceInfoDTO = (AzureWebAppInstanceInfoDTO) instanceInfoDTO;
      return AzureWebAppInfrastructureDetails.builder()
          .subscriptionId(azureWebAppInstanceInfoDTO.getSubscriptionId())
          .resourceGroup(azureWebAppInstanceInfoDTO.getResourceGroup())
          .build();
    }
    throw new InvalidArgumentsException(Pair.of("instanceInfoDTO", "Must be instance of AzureWebAppInstanceInfoDTO"));
  }

  @Override
  public DeploymentInfoDTO getDeploymentInfo(
      InfrastructureOutcome infrastructureOutcome, List<ServerInstanceInfo> serverInstanceInfoList) {
    if (isEmpty(serverInstanceInfoList)) {
      throw new InvalidArgumentsException("Parameter serverInstanceInfoList cannot be null or empty");
    }
    if (!(infrastructureOutcome instanceof AzureWebAppInfrastructureOutcome)) {
      throw new InvalidArgumentsException(
          Pair.of("infrastructureOutcome", "Must be instance of AzureWebAppInfrastructureOutcome"));
    }

    if (!(serverInstanceInfoList.get(0) instanceof AzureWebAppServerInstanceInfo)) {
      throw new InvalidArgumentsException(
          Pair.of("serverInstanceInfo", "Must be instance of AzureWebAppServerInstanceInfo"));
    }
    AzureWebAppServerInstanceInfo azureWebAppServerInstanceInfo =
        (AzureWebAppServerInstanceInfo) serverInstanceInfoList.get(0);

    return AzureWebAppDeploymentInfoDTO.builder()
        .appName(azureWebAppServerInstanceInfo.getAppName())
        .subscriptionId(azureWebAppServerInstanceInfo.getSubscriptionId())
        .resourceGroup(azureWebAppServerInstanceInfo.getResourceGroup())
        .slotName(azureWebAppServerInstanceInfo.getDeploySlot())
        .build();
  }

  @Override
  protected InstanceInfoDTO getInstanceInfoForServerInstance(ServerInstanceInfo serverInstanceInfo) {
    if (serverInstanceInfo instanceof AzureWebAppServerInstanceInfo) {
      AzureWebAppServerInstanceInfo azureWebAppServerInstanceInfo = (AzureWebAppServerInstanceInfo) serverInstanceInfo;

      return AzureWebAppInstanceInfoDTO.builder()
          .subscriptionId(azureWebAppServerInstanceInfo.getSubscriptionId())
          .resourceGroup(azureWebAppServerInstanceInfo.getResourceGroup())
          .appName(azureWebAppServerInstanceInfo.getAppName())
          .deploySlot(azureWebAppServerInstanceInfo.getDeploySlot())
          .instanceIp(azureWebAppServerInstanceInfo.getInstanceIp())
          .appServicePlanId(azureWebAppServerInstanceInfo.getAppServicePlanId())
          .instanceName(azureWebAppServerInstanceInfo.getInstanceName())
          .instanceState(azureWebAppServerInstanceInfo.getInstanceState())
          .instanceType(azureWebAppServerInstanceInfo.getInstanceType())
          .deploySlotId(azureWebAppServerInstanceInfo.getDeploySlotId())
          .hostName(azureWebAppServerInstanceInfo.getHostName())
          .instanceId(azureWebAppServerInstanceInfo.getInstanceId())
          .build();
    }
    throw new InvalidArgumentsException(
        Pair.of("serverInstanceInfo", "Must be instance of AzureWebAppServerInstanceInfo"));
  }
}
