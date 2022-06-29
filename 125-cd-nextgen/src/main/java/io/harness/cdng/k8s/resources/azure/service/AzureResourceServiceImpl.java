/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.k8s.resources.azure.service;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.cdng.azure.AzureHelperService;
import io.harness.cdng.azure.resources.dtos.AzureTagDTO;
import io.harness.cdng.azure.resources.dtos.AzureTagsDTO;
import io.harness.cdng.k8s.resources.azure.dtos.AzureClusterDTO;
import io.harness.cdng.k8s.resources.azure.dtos.AzureClustersDTO;
import io.harness.cdng.k8s.resources.azure.dtos.AzureDeploymentSlotDTO;
import io.harness.cdng.k8s.resources.azure.dtos.AzureDeploymentSlotsDTO;
import io.harness.cdng.k8s.resources.azure.dtos.AzureResourceGroupDTO;
import io.harness.cdng.k8s.resources.azure.dtos.AzureResourceGroupsDTO;
import io.harness.cdng.k8s.resources.azure.dtos.AzureSubscriptionDTO;
import io.harness.cdng.k8s.resources.azure.dtos.AzureSubscriptionsDTO;
import io.harness.cdng.k8s.resources.azure.dtos.AzureWebAppNamesDTO;
import io.harness.delegate.beans.azure.response.AzureClustersResponse;
import io.harness.delegate.beans.azure.response.AzureDeploymentSlotsResponse;
import io.harness.delegate.beans.azure.response.AzureResourceGroupsResponse;
import io.harness.delegate.beans.azure.response.AzureSubscriptionsResponse;
import io.harness.delegate.beans.azure.response.AzureTagsResponse;
import io.harness.delegate.beans.azure.response.AzureWebAppNamesResponse;
import io.harness.delegate.beans.connector.azureconnector.AzureAdditionalParams;
import io.harness.delegate.beans.connector.azureconnector.AzureConnectorDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureTaskParams;
import io.harness.delegate.beans.connector.azureconnector.AzureTaskType;
import io.harness.ng.core.BaseNGAccess;
import io.harness.security.encryption.EncryptedDataDetail;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Singleton
@OwnedBy(HarnessTeam.CDP)
public class AzureResourceServiceImpl implements AzureResourceService {
  public static final Integer AZURE_CUSTOM_TIMEOUT_IN_SEC = 60;
  @Inject AzureHelperService azureHelperService;

  @Override
  public AzureSubscriptionsDTO getSubscriptions(
      IdentifierRef connectorRef, String orgIdentifier, String projectIdentifier) {
    AzureConnectorDTO connector = azureHelperService.getConnector(connectorRef);
    BaseNGAccess baseNGAccess =
        azureHelperService.getBaseNGAccess(connectorRef.getAccountIdentifier(), orgIdentifier, projectIdentifier);
    List<EncryptedDataDetail> encryptionDetails = azureHelperService.getEncryptionDetails(connector, baseNGAccess);
    AzureTaskParams azureTaskParamsTaskParams = AzureTaskParams.builder()
                                                    .azureTaskType(AzureTaskType.LIST_SUBSCRIPTIONS)
                                                    .azureConnector(connector)
                                                    .encryptionDetails(encryptionDetails)
                                                    .delegateSelectors(connector.getDelegateSelectors())
                                                    .build();

    AzureSubscriptionsResponse subscriptionResponse = (AzureSubscriptionsResponse) azureHelperService.executeSyncTask(
        azureTaskParamsTaskParams, baseNGAccess, "Azure list subscriptions task failure due to error");
    return AzureSubscriptionsDTO.builder()
        .subscriptions(subscriptionResponse.getSubscriptions()
                           .entrySet()
                           .stream()
                           .map(entry
                               -> AzureSubscriptionDTO.builder()
                                      .subscriptionId(entry.getKey())
                                      .subscriptionName(entry.getValue())
                                      .build())
                           .collect(Collectors.toList()))
        .build();
  }

  @Override
  public AzureResourceGroupsDTO getResourceGroups(
      IdentifierRef connectorRef, String orgIdentifier, String projectIdentifier, String subscriptionId) {
    AzureConnectorDTO connector = azureHelperService.getConnector(connectorRef);
    BaseNGAccess baseNGAccess =
        azureHelperService.getBaseNGAccess(connectorRef.getAccountIdentifier(), orgIdentifier, projectIdentifier);
    List<EncryptedDataDetail> encryptionDetails = azureHelperService.getEncryptionDetails(connector, baseNGAccess);

    Map<AzureAdditionalParams, String> additionalParams = new HashMap<>();
    additionalParams.put(AzureAdditionalParams.SUBSCRIPTION_ID, subscriptionId);

    AzureTaskParams azureTaskParamsTaskParams = AzureTaskParams.builder()
                                                    .azureTaskType(AzureTaskType.LIST_RESOURCE_GROUPS)
                                                    .azureConnector(connector)
                                                    .encryptionDetails(encryptionDetails)
                                                    .delegateSelectors(connector.getDelegateSelectors())
                                                    .additionalParams(additionalParams)
                                                    .build();

    AzureResourceGroupsResponse resourceGroupsResponse =
        (AzureResourceGroupsResponse) azureHelperService.executeSyncTask(
            azureTaskParamsTaskParams, baseNGAccess, "Azure list resource groups task failure due to error");
    return AzureResourceGroupsDTO.builder()
        .resourceGroups(resourceGroupsResponse.getResourceGroups()
                            .stream()
                            .map(resourceGroup -> AzureResourceGroupDTO.builder().resourceGroup(resourceGroup).build())
                            .collect(Collectors.toList()))
        .build();
  }

  @Override
  public AzureClustersDTO getClusters(IdentifierRef connectorRef, String orgIdentifier, String projectIdentifier,
      String subscriptionId, String resourceGroup) {
    AzureConnectorDTO connector = azureHelperService.getConnector(connectorRef);
    BaseNGAccess baseNGAccess =
        azureHelperService.getBaseNGAccess(connectorRef.getAccountIdentifier(), orgIdentifier, projectIdentifier);
    List<EncryptedDataDetail> encryptionDetails = azureHelperService.getEncryptionDetails(connector, baseNGAccess);

    Map<AzureAdditionalParams, String> additionalParams = new HashMap<>();
    additionalParams.put(AzureAdditionalParams.SUBSCRIPTION_ID, subscriptionId);
    additionalParams.put(AzureAdditionalParams.RESOURCE_GROUP, resourceGroup);
    AzureTaskParams azureTaskParamsTaskParams = AzureTaskParams.builder()
                                                    .azureTaskType(AzureTaskType.LIST_CLUSTERS)
                                                    .azureConnector(connector)
                                                    .encryptionDetails(encryptionDetails)
                                                    .delegateSelectors(connector.getDelegateSelectors())
                                                    .additionalParams(additionalParams)
                                                    .build();

    AzureClustersResponse clustersResponse = (AzureClustersResponse) azureHelperService.executeSyncTask(
        azureTaskParamsTaskParams, baseNGAccess, "Azure list cluster task failure due to error");
    return AzureClustersDTO.builder()
        .clusters(clustersResponse.getClusters()
                      .stream()
                      .map(cluster -> AzureClusterDTO.builder().cluster(cluster).build())
                      .collect(Collectors.toList()))
        .build();
  }

  @Override
  public AzureWebAppNamesDTO getWebAppNames(IdentifierRef connectorRef, String orgIdentifier, String projectIdentifier,
      String subscriptionId, String resourceGroup) {
    AzureConnectorDTO connector = azureHelperService.getConnector(connectorRef);
    BaseNGAccess baseNGAccess =
        azureHelperService.getBaseNGAccess(connectorRef.getAccountIdentifier(), orgIdentifier, projectIdentifier);
    List<EncryptedDataDetail> encryptionDetails = azureHelperService.getEncryptionDetails(connector, baseNGAccess);

    Map<AzureAdditionalParams, String> additionalParams = new HashMap<>();
    additionalParams.put(AzureAdditionalParams.SUBSCRIPTION_ID, subscriptionId);
    additionalParams.put(AzureAdditionalParams.RESOURCE_GROUP, resourceGroup);

    AzureTaskParams azureTaskParamsTaskParams = AzureTaskParams.builder()
                                                    .azureTaskType(AzureTaskType.LIST_WEBAPP_NAMES)
                                                    .azureConnector(connector)
                                                    .encryptionDetails(encryptionDetails)
                                                    .delegateSelectors(connector.getDelegateSelectors())
                                                    .additionalParams(additionalParams)
                                                    .build();

    AzureWebAppNamesResponse azureWebAppNamesResponse =
        (AzureWebAppNamesResponse) azureHelperService.executeSyncTask(azureTaskParamsTaskParams, baseNGAccess,
            "Azure list Web App names task failure due to error", Optional.of(AZURE_CUSTOM_TIMEOUT_IN_SEC));
    return AzureWebAppNamesDTO.builder().webAppNames(azureWebAppNamesResponse.getWebAppNames()).build();
  }

  @Override
  public AzureDeploymentSlotsDTO getAppServiceDeploymentSlots(IdentifierRef connectorRef, String orgIdentifier,
      String projectIdentifier, String subscriptionId, String resourceGroup, String webAppName) {
    AzureConnectorDTO connector = azureHelperService.getConnector(connectorRef);
    BaseNGAccess baseNGAccess =
        azureHelperService.getBaseNGAccess(connectorRef.getAccountIdentifier(), orgIdentifier, projectIdentifier);
    List<EncryptedDataDetail> encryptionDetails = azureHelperService.getEncryptionDetails(connector, baseNGAccess);

    Map<AzureAdditionalParams, String> additionalParams = new HashMap<>();
    additionalParams.put(AzureAdditionalParams.SUBSCRIPTION_ID, subscriptionId);
    additionalParams.put(AzureAdditionalParams.RESOURCE_GROUP, resourceGroup);
    additionalParams.put(AzureAdditionalParams.WEB_APP_NAME, webAppName);

    AzureTaskParams azureTaskParamsTaskParams = AzureTaskParams.builder()
                                                    .azureTaskType(AzureTaskType.LIST_DEPLOYMENT_SLOTS)
                                                    .azureConnector(connector)
                                                    .encryptionDetails(encryptionDetails)
                                                    .delegateSelectors(connector.getDelegateSelectors())
                                                    .additionalParams(additionalParams)
                                                    .build();

    AzureDeploymentSlotsResponse azureWebAppNamesResponse =
        (AzureDeploymentSlotsResponse) azureHelperService.executeSyncTask(
            azureTaskParamsTaskParams, baseNGAccess, "Azure list Web App deployment slots task failure due to error");

    return AzureDeploymentSlotsDTO.builder()
        .deploymentSlots(azureWebAppNamesResponse.getDeploymentSlots()
                             .stream()
                             .map(deploymentSlot
                                 -> AzureDeploymentSlotDTO.builder()
                                        .name(deploymentSlot.getName())
                                        .type(deploymentSlot.getType())
                                        .build())
                             .collect(Collectors.toList()))
        .build();
  }

  @Override
  public AzureTagsDTO getTags(
      IdentifierRef connectorRef, String orgIdentifier, String projectIdentifier, String subscriptionId) {
    AzureConnectorDTO connector = azureHelperService.getConnector(connectorRef);
    BaseNGAccess baseNGAccess =
        azureHelperService.getBaseNGAccess(connectorRef.getAccountIdentifier(), orgIdentifier, projectIdentifier);
    List<EncryptedDataDetail> encryptionDetails = azureHelperService.getEncryptionDetails(connector, baseNGAccess);

    Map<AzureAdditionalParams, String> additionalParams =
        Collections.singletonMap(AzureAdditionalParams.SUBSCRIPTION_ID, subscriptionId);

    AzureTaskParams azureTaskParamsTaskParams = AzureTaskParams.builder()
                                                    .azureTaskType(AzureTaskType.LIST_TAGS)
                                                    .azureConnector(connector)
                                                    .encryptionDetails(encryptionDetails)
                                                    .delegateSelectors(connector.getDelegateSelectors())
                                                    .additionalParams(additionalParams)
                                                    .build();

    AzureTagsResponse tagsResponse = (AzureTagsResponse) azureHelperService.executeSyncTask(
        azureTaskParamsTaskParams, baseNGAccess, "Azure list tags task failure due to error");
    return AzureTagsDTO.builder()
        .tags(tagsResponse.getTags()
                  .stream()
                  .map(tag -> AzureTagDTO.builder().tag(tag).build())
                  .collect(Collectors.toList()))
        .build();
  }
}
