/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.service.instancesyncperpetualtask.instancesyncperpetualtaskhandler.azure;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.cdng.azure.AzureEncryptionDetailsHelper;
import io.harness.cdng.azure.AzureHelperService;
import io.harness.cdng.infra.beans.AzureWebAppInfrastructureOutcome;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.delegate.beans.connector.azureconnector.AzureCapabilityHelper;
import io.harness.delegate.beans.connector.azureconnector.AzureConnectorDTO;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.task.azure.appservice.webapp.AzureWebAppDeploymentReleaseData;
import io.harness.delegate.task.azure.appservice.webapp.ng.AzureWebAppInfraDelegateConfig;
import io.harness.dtos.InfrastructureMappingDTO;
import io.harness.dtos.deploymentinfo.AzureWebAppDeploymentInfoDTO;
import io.harness.dtos.deploymentinfo.DeploymentInfoDTO;
import io.harness.ng.core.BaseNGAccess;
import io.harness.perpetualtask.PerpetualTaskExecutionBundle;
import io.harness.perpetualtask.instancesync.AzureWebAppDeploymentRelease;
import io.harness.perpetualtask.instancesync.AzureWebAppNGInstanceSyncPerpetualTaskParams;
import io.harness.service.instancesyncperpetualtask.instancesyncperpetualtaskhandler.InstanceSyncPerpetualTaskHandler;
import io.harness.utils.IdentifierRefHelper;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;

@Singleton
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@OwnedBy(HarnessTeam.CDP)
public class AzureWebAppInstanceSyncPerpetualTaskHandler extends InstanceSyncPerpetualTaskHandler {
  @Inject private AzureEncryptionDetailsHelper azureEncryptionDetailsHelper;
  @Inject private AzureHelperService azureHelperService;

  @Override
  public PerpetualTaskExecutionBundle getExecutionBundle(InfrastructureMappingDTO infrastructureMappingDTO,
      List<DeploymentInfoDTO> deploymentInfoDTOList, InfrastructureOutcome infrastructureOutcome) {
    List<AzureWebAppDeploymentReleaseData> deploymentReleaseDataList =
        populateDeploymentReleaseList(infrastructureMappingDTO, deploymentInfoDTOList, infrastructureOutcome);

    Any perpetualTaskPack = packAzureWebAppInstanceSyncPerpetualTaskParams(
        infrastructureMappingDTO.getAccountIdentifier(), deploymentReleaseDataList);

    List<ExecutionCapability> executionCapabilities = getExecutionCapabilities(deploymentReleaseDataList);

    return createPerpetualTaskExecutionBundle(perpetualTaskPack, executionCapabilities,
        infrastructureMappingDTO.getOrgIdentifier(), infrastructureMappingDTO.getProjectIdentifier());
  }

  private List<ExecutionCapability> getExecutionCapabilities(
      List<AzureWebAppDeploymentReleaseData> deploymentReleaseDataList) {
    Optional<AzureWebAppDeploymentReleaseData> deploymentReleaseSample = deploymentReleaseDataList.stream().findFirst();
    if (!deploymentReleaseSample.isPresent()) {
      return Collections.emptyList();
    }
    return AzureCapabilityHelper.fetchRequiredExecutionCapabilities(
        deploymentReleaseSample.get().getAzureWebAppInfraDelegateConfig().getAzureConnectorDTO(), null);
  }

  private Any packAzureWebAppInstanceSyncPerpetualTaskParams(
      String accountIdentifier, List<AzureWebAppDeploymentReleaseData> deploymentReleaseData) {
    return Any.pack(createAzureWebAppInstanceSyncPerpetualTaskParams(accountIdentifier, deploymentReleaseData));
  }

  private AzureWebAppNGInstanceSyncPerpetualTaskParams createAzureWebAppInstanceSyncPerpetualTaskParams(
      String accountIdentifier, List<AzureWebAppDeploymentReleaseData> deploymentReleaseData) {
    return AzureWebAppNGInstanceSyncPerpetualTaskParams.newBuilder()
        .setAccountId(accountIdentifier)
        .addAllAzureWebAppDeploymentReleaseList(toAzureWebAppDeploymentReleaseList(deploymentReleaseData))
        .build();
  }

  private List<AzureWebAppDeploymentRelease> toAzureWebAppDeploymentReleaseList(
      List<AzureWebAppDeploymentReleaseData> deploymentReleaseData) {
    return deploymentReleaseData.stream().map(this::toAzureWebAppDeploymentRelease).collect(Collectors.toList());
  }

  private AzureWebAppDeploymentRelease toAzureWebAppDeploymentRelease(AzureWebAppDeploymentReleaseData releaseData) {
    return AzureWebAppDeploymentRelease.newBuilder()
        .setAzureWebAppInfraDelegateConfig(
            ByteString.copyFrom(kryoSerializer.asBytes(releaseData.getAzureWebAppInfraDelegateConfig())))
        .setSubscriptionId(releaseData.getSubscriptionId())
        .setResourceGroupName(releaseData.getResourceGroupName())
        .setAppName(releaseData.getAppName())
        .setSlotName(releaseData.getSlotName())
        .build();
  }

  private List<AzureWebAppDeploymentReleaseData> populateDeploymentReleaseList(
      InfrastructureMappingDTO infrastructureMappingDTO, List<DeploymentInfoDTO> deploymentInfoDTOList,
      InfrastructureOutcome infrastructureOutcome) {
    return deploymentInfoDTOList.stream()
        .filter(Objects::nonNull)
        .map(AzureWebAppDeploymentInfoDTO.class ::cast)
        .map(deploymentInfoDTO
            -> toAzureWebAppDeploymentReleaseData(infrastructureMappingDTO, deploymentInfoDTO, infrastructureOutcome))
        .collect(Collectors.toList());
  }

  private AzureWebAppDeploymentReleaseData toAzureWebAppDeploymentReleaseData(
      InfrastructureMappingDTO infrastructureMappingDTO, AzureWebAppDeploymentInfoDTO deploymentInfoDTO,
      InfrastructureOutcome infrastructureOutcome) {
    AzureWebAppInfraDelegateConfig azureWebAppInfraDelegateConfig =
        getAzureWebAppInfraDelegateConfig(infrastructureMappingDTO, infrastructureOutcome,
            deploymentInfoDTO.getAppName(), deploymentInfoDTO.getSlotName());

    return AzureWebAppDeploymentReleaseData.builder()
        .appName(deploymentInfoDTO.getAppName())
        .resourceGroupName(deploymentInfoDTO.getResourceGroup())
        .subscriptionId(deploymentInfoDTO.getSubscriptionId())
        .slotName(deploymentInfoDTO.getSlotName())
        .azureWebAppInfraDelegateConfig(azureWebAppInfraDelegateConfig)
        .build();
  }

  private AzureWebAppInfraDelegateConfig getAzureWebAppInfraDelegateConfig(
      InfrastructureMappingDTO infrastructureMappingDTO, InfrastructureOutcome infrastructureOutcome, String appName,
      String deploymentSlot) {
    BaseNGAccess baseNGAccess = getBaseNGAccess(infrastructureMappingDTO);

    AzureWebAppInfrastructureOutcome azureWebAppInfrastructureOutcome =
        (AzureWebAppInfrastructureOutcome) infrastructureOutcome;
    IdentifierRef identifierRef =
        IdentifierRefHelper.getIdentifierRef(azureWebAppInfrastructureOutcome.getConnectorRef(),
            baseNGAccess.getAccountIdentifier(), baseNGAccess.getOrgIdentifier(), baseNGAccess.getProjectIdentifier());

    AzureConnectorDTO connectorDTO = azureHelperService.getConnector(identifierRef);

    return AzureWebAppInfraDelegateConfig.builder()
        .appName(appName)
        .deploymentSlot(deploymentSlot)
        .subscription(azureWebAppInfrastructureOutcome.getSubscription())
        .resourceGroup(azureWebAppInfrastructureOutcome.getResourceGroup())
        .encryptionDataDetails(azureEncryptionDetailsHelper.getEncryptionDetails(connectorDTO, baseNGAccess))
        .azureConnectorDTO(connectorDTO)
        .build();
  }

  private BaseNGAccess getBaseNGAccess(InfrastructureMappingDTO infrastructureMappingDTO) {
    return BaseNGAccess.builder()
        .accountIdentifier(infrastructureMappingDTO.getAccountIdentifier())
        .orgIdentifier(infrastructureMappingDTO.getOrgIdentifier())
        .projectIdentifier(infrastructureMappingDTO.getProjectIdentifier())
        .build();
  }
}
