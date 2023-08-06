/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.artifact.resources.acr.service;
import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.IdentifierRef;
import io.harness.cdng.artifact.NGArtifactConstants;
import io.harness.cdng.artifact.resources.acr.dtos.AcrRegistriesDTO;
import io.harness.cdng.artifact.resources.acr.dtos.AcrRegistryDTO;
import io.harness.cdng.artifact.resources.acr.dtos.AcrRepositoriesDTO;
import io.harness.cdng.artifact.resources.acr.dtos.AcrRepositoryDTO;
import io.harness.cdng.artifact.resources.acr.dtos.AcrRequestDTO;
import io.harness.cdng.artifact.utils.ArtifactUtils;
import io.harness.cdng.azure.AzureHelperService;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.azure.AcrBuildDetailsDTO;
import io.harness.delegate.beans.azure.AcrResponseDTO;
import io.harness.delegate.beans.azure.response.AzureRegistriesResponse;
import io.harness.delegate.beans.azure.response.AzureRepositoriesResponse;
import io.harness.delegate.beans.connector.azureconnector.AzureAdditionalParams;
import io.harness.delegate.beans.connector.azureconnector.AzureConnectorDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureTaskParams;
import io.harness.delegate.beans.connector.azureconnector.AzureTaskType;
import io.harness.delegate.task.artifacts.ArtifactDelegateRequestUtils;
import io.harness.delegate.task.artifacts.ArtifactSourceType;
import io.harness.delegate.task.artifacts.ArtifactTaskType;
import io.harness.delegate.task.artifacts.azure.AcrArtifactDelegateRequest;
import io.harness.delegate.task.artifacts.response.ArtifactTaskResponse;
import io.harness.exception.ArtifactServerException;
import io.harness.exception.DelegateNotAvailableException;
import io.harness.exception.DelegateServiceDriverException;
import io.harness.exception.HintException;
import io.harness.exception.WingsException;
import io.harness.exception.exceptionmanager.exceptionhandler.DocumentLinksConstants;
import io.harness.ng.core.BaseNGAccess;
import io.harness.security.encryption.EncryptedDataDetail;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.lang3.tuple.MutablePair;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_ARTIFACTS})
@Singleton
@OwnedBy(HarnessTeam.CDP)
public class AcrResourceServiceImpl implements AcrResourceService {
  @Inject AzureHelperService azureHelperService;

  @Override
  public AcrRegistriesDTO getRegistries(
      IdentifierRef connectorRef, String orgIdentifier, String projectIdentifier, String subscriptionId) {
    AzureConnectorDTO connector = azureHelperService.getConnector(connectorRef);
    BaseNGAccess baseNGAccess =
        azureHelperService.getBaseNGAccess(connectorRef.getAccountIdentifier(), orgIdentifier, projectIdentifier);
    List<EncryptedDataDetail> encryptionDetails = azureHelperService.getEncryptionDetails(connector, baseNGAccess);

    Map<AzureAdditionalParams, String> additionalParams = new HashMap<>();
    additionalParams.put(AzureAdditionalParams.SUBSCRIPTION_ID, subscriptionId);

    AzureTaskParams azureTaskParamsTaskParams = AzureTaskParams.builder()
                                                    .azureTaskType(AzureTaskType.LIST_CONTAINER_REGISTRIES)
                                                    .azureConnector(connector)
                                                    .encryptionDetails(encryptionDetails)
                                                    .delegateSelectors(connector.getDelegateSelectors())
                                                    .additionalParams(additionalParams)
                                                    .build();

    AzureRegistriesResponse registriesResponse = (AzureRegistriesResponse) azureHelperService.executeSyncTask(
        azureTaskParamsTaskParams, baseNGAccess, "Azure list registries task failure due to error");
    return AcrRegistriesDTO.builder().registries(getRegistryDTOs(registriesResponse)).build();
  }

  private List<AcrRegistryDTO> getRegistryDTOs(AzureRegistriesResponse registriesResponse) {
    return registriesResponse.getContainerRegistries()
        .stream()
        .map(registry -> createRegistryDTO(registry))
        .collect(Collectors.toList());
  }

  private AcrRegistryDTO createRegistryDTO(String registryName) {
    return AcrRegistryDTO.builder().registry(registryName).build();
  }

  @Override
  public AcrRepositoriesDTO getRepositories(IdentifierRef connectorRef, String orgIdentifier, String projectIdentifier,
      String subscriptionId, String registry) {
    AzureConnectorDTO connector = azureHelperService.getConnector(connectorRef);
    BaseNGAccess baseNGAccess =
        azureHelperService.getBaseNGAccess(connectorRef.getAccountIdentifier(), orgIdentifier, projectIdentifier);
    List<EncryptedDataDetail> encryptionDetails = azureHelperService.getEncryptionDetails(connector, baseNGAccess);

    Map<AzureAdditionalParams, String> additionalParams = new HashMap<>();
    additionalParams.put(AzureAdditionalParams.SUBSCRIPTION_ID, subscriptionId);
    additionalParams.put(AzureAdditionalParams.CONTAINER_REGISTRY, registry);
    AzureTaskParams azureTaskParamsTaskParams = AzureTaskParams.builder()
                                                    .azureTaskType(AzureTaskType.LIST_REPOSITORIES)
                                                    .azureConnector(connector)
                                                    .encryptionDetails(encryptionDetails)
                                                    .delegateSelectors(connector.getDelegateSelectors())
                                                    .additionalParams(additionalParams)
                                                    .build();

    AzureRepositoriesResponse repositoriesResponse = (AzureRepositoriesResponse) azureHelperService.executeSyncTask(
        azureTaskParamsTaskParams, baseNGAccess, "Azure list repositories task failure due to error");
    return AcrRepositoriesDTO.builder().repositories(getRepositoryDTOs(repositoriesResponse)).build();
  }

  private List<AcrRepositoryDTO> getRepositoryDTOs(AzureRepositoriesResponse repositoriesResponse) {
    return repositoriesResponse.getRepositories()
        .stream()
        .map(repository -> createRepositoryDTO(repository))
        .collect(Collectors.toList());
  }

  private AcrRepositoryDTO createRepositoryDTO(String repoName) {
    return AcrRepositoryDTO.builder().repository(repoName).build();
  }

  @Override
  public AcrResponseDTO getBuildDetails(IdentifierRef connectorRef, String subscription, String registry,
      String repository, String orgIdentifier, String projectIdentifier) {
    AzureConnectorDTO connector = azureHelperService.getConnector(connectorRef);
    BaseNGAccess baseNGAccess =
        azureHelperService.getBaseNGAccess(connectorRef.getAccountIdentifier(), orgIdentifier, projectIdentifier);
    List<EncryptedDataDetail> encryptionDetails = azureHelperService.getEncryptionDetails(connector, baseNGAccess);

    AcrArtifactDelegateRequest acrArtifactDelegateRequest = ArtifactDelegateRequestUtils.getAcrDelegateRequest(
        subscription, registry, repository, connector, null, null, null, encryptionDetails, ArtifactSourceType.ACR);
    try {
      DelegateResponseData acrTaskExecutionResponse = azureHelperService.executeSyncTask(
          acrArtifactDelegateRequest, baseNGAccess, "ACR Artifact Get Builds task failure due to error");

      ArtifactTaskResponse artifactTaskResponse = (ArtifactTaskResponse) acrTaskExecutionResponse;
      return azureHelperService.getAcrResponseDTO(artifactTaskResponse.getArtifactTaskExecutionResponse());
    } catch (DelegateServiceDriverException ex) {
      throw new HintException(
          String.format(HintException.DELEGATE_NOT_AVAILABLE, DocumentLinksConstants.DELEGATE_INSTALLATION_LINK),
          new DelegateNotAvailableException(ex.getCause().getMessage(), WingsException.USER));
    }
  }

  @Override
  public AcrBuildDetailsDTO getLastSuccessfulBuild(IdentifierRef connectorRef, String subscription, String registry,
      String repository, String orgIdentifier, String projectIdentifier, AcrRequestDTO acrRequestDTO) {
    ArtifactUtils.validateIfAllValuesAssigned(MutablePair.of(NGCommonEntityConstants.SUBSCRIPTION_ID, subscription),
        MutablePair.of(NGArtifactConstants.REGISTRY, registry),
        MutablePair.of(NGArtifactConstants.REPOSITORY, repository));
    ArtifactUtils.validateIfAnyValueAssigned(MutablePair.of(NGArtifactConstants.TAG, acrRequestDTO.getTag()),
        MutablePair.of(NGArtifactConstants.TAG_REGEX, acrRequestDTO.getTagRegex()));
    AzureConnectorDTO connector = azureHelperService.getConnector(connectorRef);
    BaseNGAccess baseNGAccess =
        azureHelperService.getBaseNGAccess(connectorRef.getAccountIdentifier(), orgIdentifier, projectIdentifier);
    List<EncryptedDataDetail> encryptionDetails = azureHelperService.getEncryptionDetails(connector, baseNGAccess);

    AcrArtifactDelegateRequest acrArtifactDelegateRequest = ArtifactDelegateRequestUtils.getAcrDelegateRequest(
        subscription, registry, repository, connector, acrRequestDTO.getTag(), acrRequestDTO.getTagRegex(),
        acrRequestDTO.getTagsList(), encryptionDetails, ArtifactSourceType.ACR);
    try {
      DelegateResponseData acrTaskExecutionResponse = azureHelperService.executeSyncTask(acrArtifactDelegateRequest,
          baseNGAccess, ArtifactTaskType.GET_LAST_SUCCESSFUL_BUILD,
          "ACR Artifact Get Last Successful Build task failure due to error");

      ArtifactTaskResponse artifactTaskResponse = (ArtifactTaskResponse) acrTaskExecutionResponse;
      AcrResponseDTO acrResponseDTO =
          azureHelperService.getAcrResponseDTO(artifactTaskResponse.getArtifactTaskExecutionResponse());
      if (acrResponseDTO.getBuildDetailsList().size() != 1) {
        throw new ArtifactServerException("ACR Artifact get last successful build task failure.");
      }
      return acrResponseDTO.getBuildDetailsList().get(0);
    } catch (DelegateServiceDriverException ex) {
      throw new HintException(
          String.format(HintException.DELEGATE_NOT_AVAILABLE, DocumentLinksConstants.DELEGATE_INSTALLATION_LINK),
          new DelegateNotAvailableException(ex.getCause().getMessage(), WingsException.USER));
    }
  }
}
