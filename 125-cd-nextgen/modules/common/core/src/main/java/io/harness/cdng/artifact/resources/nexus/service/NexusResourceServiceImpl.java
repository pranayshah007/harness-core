/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.artifact.resources.nexus.service;

import static io.harness.connector.ConnectorModule.DEFAULT_CONNECTOR_SERVICE;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DelegateTaskRequest;
import io.harness.beans.IdentifierRef;
import io.harness.cdng.artifact.resources.nexus.dtos.NexusBuildDetailsDTO;
import io.harness.cdng.artifact.resources.nexus.dtos.NexusRequestDTO;
import io.harness.cdng.artifact.resources.nexus.dtos.NexusResponseDTO;
import io.harness.cdng.artifact.resources.nexus.mappers.NexusResourceMapper;
import io.harness.cdng.artifact.utils.ArtifactUtils;
import io.harness.common.NGTaskType;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.services.ConnectorService;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.delegate.beans.RemoteMethodReturnValueData;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.nexusconnector.NexusConnectorDTO;
import io.harness.delegate.task.artifacts.ArtifactDelegateRequestUtils;
import io.harness.delegate.task.artifacts.ArtifactSourceType;
import io.harness.delegate.task.artifacts.ArtifactTaskType;
import io.harness.delegate.task.artifacts.nexus.NexusArtifactDelegateRequest;
import io.harness.delegate.task.artifacts.nexus.NexusArtifactDelegateResponse;
import io.harness.delegate.task.artifacts.request.ArtifactTaskParameters;
import io.harness.delegate.task.artifacts.response.ArtifactTaskExecutionResponse;
import io.harness.delegate.task.artifacts.response.ArtifactTaskResponse;
import io.harness.exception.ArtifactServerException;
import io.harness.exception.DelegateNotAvailableException;
import io.harness.exception.DelegateServiceDriverException;
import io.harness.exception.HintException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.NexusRegistryException;
import io.harness.exception.WingsException;
import io.harness.exception.exceptionmanager.ExceptionManager;
import io.harness.exception.exceptionmanager.exceptionhandler.DocumentLinksConstants;
import io.harness.ng.core.BaseNGAccess;
import io.harness.ng.core.NGAccess;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.service.DelegateGrpcClientWrapper;

import software.wings.helpers.ext.nexus.NexusRepositories;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
@OwnedBy(HarnessTeam.CDP)
public class NexusResourceServiceImpl implements NexusResourceService {
  private final ConnectorService connectorService;
  private final SecretManagerClientService secretManagerClientService;
  private DelegateGrpcClientWrapper delegateGrpcClientWrapper;
  @Inject ExceptionManager exceptionManager;
  @VisibleForTesting static final int timeoutInSecs = 30;

  @Inject
  public NexusResourceServiceImpl(@Named(DEFAULT_CONNECTOR_SERVICE) ConnectorService connectorService,
      SecretManagerClientService secretManagerClientService, DelegateGrpcClientWrapper delegateGrpcClientWrapper) {
    this.connectorService = connectorService;
    this.secretManagerClientService = secretManagerClientService;
    this.delegateGrpcClientWrapper = delegateGrpcClientWrapper;
  }

  @Override
  public NexusResponseDTO getBuildDetails(IdentifierRef nexusConnectorRef, String repositoryName, String repositoryPort,
      String artifactPath, String repositoryFormat, String artifactRepositoryUrl, String orgIdentifier,
      String projectIdentifier, String groupId, String artifactId, String extension, String classifier,
      String packageName, String group) {
    NexusConnectorDTO connector = getConnector(nexusConnectorRef);
    BaseNGAccess baseNGAccess =
        getBaseNGAccess(nexusConnectorRef.getAccountIdentifier(), orgIdentifier, projectIdentifier);
    List<EncryptedDataDetail> encryptionDetails = getEncryptionDetails(connector, baseNGAccess);
    NexusArtifactDelegateRequest nexusRequest =
        ArtifactDelegateRequestUtils.getNexusArtifactDelegateRequest(repositoryName, repositoryPort, artifactPath,
            repositoryFormat, artifactRepositoryUrl, null, null, null, connector, encryptionDetails,
            ArtifactSourceType.NEXUS3_REGISTRY, groupId, artifactId, extension, classifier, packageName, group);
    try {
      ArtifactTaskExecutionResponse artifactTaskExecutionResponse = executeSyncTask(nexusRequest,
          ArtifactTaskType.GET_BUILDS, baseNGAccess, "Nexus Artifact Get Builds task failure due to error");
      return getNexusResponseDTO(artifactTaskExecutionResponse);
    } catch (DelegateServiceDriverException ex) {
      throw new HintException(
          String.format(HintException.DELEGATE_NOT_AVAILABLE, DocumentLinksConstants.DELEGATE_INSTALLATION_LINK),
          new DelegateNotAvailableException(ex.getCause().getMessage(), WingsException.USER));
    }
  }

  @Override
  public NexusResponseDTO getBuildDetails(IdentifierRef nexusConnectorRef, String repositoryName, String repositoryPort,
      String artifactPath, String repositoryFormat, String artifactRepositoryUrl, String orgIdentifier,
      String projectIdentifier) {
    NexusConnectorDTO connector = getConnector(nexusConnectorRef);
    BaseNGAccess baseNGAccess =
        getBaseNGAccess(nexusConnectorRef.getAccountIdentifier(), orgIdentifier, projectIdentifier);
    List<EncryptedDataDetail> encryptionDetails = getEncryptionDetails(connector, baseNGAccess);
    NexusArtifactDelegateRequest nexusRequest = ArtifactDelegateRequestUtils.getNexusArtifactDelegateRequest(
        repositoryName, repositoryPort, artifactPath, repositoryFormat, artifactRepositoryUrl, null, null, null,
        connector, encryptionDetails, ArtifactSourceType.NEXUS3_REGISTRY, null, null);
    try {
      ArtifactTaskExecutionResponse artifactTaskExecutionResponse = executeSyncTask(nexusRequest,
          ArtifactTaskType.GET_BUILDS, baseNGAccess, "Nexus Artifact Get Builds task failure due to error");
      return getNexusResponseDTO(artifactTaskExecutionResponse);
    } catch (DelegateServiceDriverException ex) {
      throw new HintException(
          String.format(HintException.DELEGATE_NOT_AVAILABLE, DocumentLinksConstants.DELEGATE_INSTALLATION_LINK),
          new DelegateNotAvailableException(ex.getCause().getMessage(), WingsException.USER));
    }
  }

  @Override
  public NexusBuildDetailsDTO getSuccessfulBuild(IdentifierRef nexusConnectorRef, String repositoryName,
      String repositoryPort, String artifactPath, String repositoryFormat, String artifactRepositoryUrl,
      NexusRequestDTO nexusRequestDTO, String orgIdentifier, String projectIdentifier) {
    NexusConnectorDTO connector = getConnector(nexusConnectorRef);
    BaseNGAccess baseNGAccess =
        getBaseNGAccess(nexusConnectorRef.getAccountIdentifier(), orgIdentifier, projectIdentifier);
    List<EncryptedDataDetail> encryptionDetails = getEncryptionDetails(connector, baseNGAccess);
    NexusArtifactDelegateRequest nexusRequest =
        ArtifactDelegateRequestUtils.getNexusArtifactDelegateRequest(repositoryName, repositoryPort, artifactPath,
            repositoryFormat, artifactRepositoryUrl, nexusRequestDTO.getTag(), nexusRequestDTO.getTagRegex(), null,
            connector, encryptionDetails, ArtifactSourceType.NEXUS3_REGISTRY, null, null);
    ArtifactTaskExecutionResponse artifactTaskExecutionResponse =
        executeSyncTask(nexusRequest, ArtifactTaskType.GET_LAST_SUCCESSFUL_BUILD, baseNGAccess,
            "Nexus Get last successful build task failure due to error");
    NexusResponseDTO nexusResponseDTO = getNexusResponseDTO(artifactTaskExecutionResponse);
    if (nexusResponseDTO.getBuildDetailsList().size() != 1) {
      throw new NexusRegistryException(
          "Nexus get last successful build task failure. Expected was to get 1 build, but instead got "
          + nexusResponseDTO.getBuildDetailsList().size() + " builds.");
    }
    return nexusResponseDTO.getBuildDetailsList().get(0);
  }

  @Override
  public boolean validateArtifactServer(
      IdentifierRef nexusConnectorRef, String orgIdentifier, String projectIdentifier) {
    NexusConnectorDTO connector = getConnector(nexusConnectorRef);
    BaseNGAccess baseNGAccess =
        getBaseNGAccess(nexusConnectorRef.getAccountIdentifier(), orgIdentifier, projectIdentifier);
    List<EncryptedDataDetail> encryptionDetails = getEncryptionDetails(connector, baseNGAccess);
    NexusArtifactDelegateRequest nexusRequest =
        ArtifactDelegateRequestUtils.getNexusArtifactDelegateRequest(null, null, null, null, null, null, null, null,
            connector, encryptionDetails, ArtifactSourceType.NEXUS3_REGISTRY, null, null);
    ArtifactTaskExecutionResponse artifactTaskExecutionResponse =
        executeSyncTask(nexusRequest, ArtifactTaskType.VALIDATE_ARTIFACT_SERVER, baseNGAccess,
            "Nexus validate artifact server task failure due to error");
    return artifactTaskExecutionResponse.isArtifactServerValid();
  }

  @Override
  public List<NexusRepositories> getRepositories(
      IdentifierRef nexusConnectorRef, String orgIdentifier, String projectIdentifier, String repositoryFormat) {
    NexusConnectorDTO connector = getConnector(nexusConnectorRef);
    BaseNGAccess baseNGAccess =
        getBaseNGAccess(nexusConnectorRef.getAccountIdentifier(), orgIdentifier, projectIdentifier);
    List<EncryptedDataDetail> encryptionDetails = getEncryptionDetails(connector, baseNGAccess);
    NexusArtifactDelegateRequest nexusRequest =
        ArtifactDelegateRequestUtils.getNexusArtifactDelegateRequest(null, null, null, repositoryFormat, null, null,
            null, null, connector, encryptionDetails, ArtifactSourceType.NEXUS3_REGISTRY, null, null);
    ArtifactTaskExecutionResponse artifactTaskExecutionResponse = executeSyncTask(nexusRequest,
        ArtifactTaskType.GET_NEXUS_REPOSITORIES, baseNGAccess, "Nexus get Repository task failure due to error");
    return artifactTaskExecutionResponse.getRepositories();
  }

  @Override
  public List<String> getGroupIds(String accountId, String orgIdentifier, String projectIdentifier,
      IdentifierRef nexusConnectorRef, String repositoryFormat, String repository,
      ArtifactSourceType artifactSourceType) {
    NexusConnectorDTO connector = getConnector(nexusConnectorRef);

    BaseNGAccess baseNGAccess =
        getBaseNGAccess(nexusConnectorRef.getAccountIdentifier(), orgIdentifier, projectIdentifier);

    List<EncryptedDataDetail> encryptionDetails = getEncryptionDetails(connector, baseNGAccess);

    NexusArtifactDelegateRequest nexusRequest =
        ArtifactDelegateRequestUtils.getNexusArtifactDelegateRequest(repository, null, null, repositoryFormat, null,
            null, null, null, connector, encryptionDetails, artifactSourceType, null, null);

    ArtifactTaskExecutionResponse artifactTaskExecutionResponse = executeSyncTask(nexusRequest,
        ArtifactTaskType.GET_NEXUS_GROUP_IDS, baseNGAccess, "Nexus get groupIds task failure due to error");

    return artifactTaskExecutionResponse.getNexusGroupIds();
  }

  @Override
  public List<String> getArtifactIds(String accountId, String orgIdentifier, String projectIdentifier,
      IdentifierRef nexusConnectorRef, String repositoryFormat, String repository, String groupId,
      ArtifactSourceType artifactSourceType) {
    NexusConnectorDTO connector = getConnector(nexusConnectorRef);

    BaseNGAccess baseNGAccess =
        getBaseNGAccess(nexusConnectorRef.getAccountIdentifier(), orgIdentifier, projectIdentifier);

    List<EncryptedDataDetail> encryptionDetails = getEncryptionDetails(connector, baseNGAccess);

    NexusArtifactDelegateRequest nexusRequest =
        ArtifactDelegateRequestUtils.getNexusArtifactDelegateRequest(repository, null, null, repositoryFormat, null,
            null, null, null, connector, encryptionDetails, artifactSourceType, groupId, null);

    ArtifactTaskExecutionResponse artifactTaskExecutionResponse = executeSyncTask(nexusRequest,
        ArtifactTaskType.GET_NEXUS_ARTIFACTIDS, baseNGAccess, "Nexus get artifactIds task failure due to error");

    return artifactTaskExecutionResponse.getNexusArtifactIds();
  }

  private NexusConnectorDTO getConnector(IdentifierRef nexusConnectorRef) {
    Optional<ConnectorResponseDTO> connectorDTO =
        connectorService.get(nexusConnectorRef.getAccountIdentifier(), nexusConnectorRef.getOrgIdentifier(),
            nexusConnectorRef.getProjectIdentifier(), nexusConnectorRef.getIdentifier());

    if (!connectorDTO.isPresent() || !isANexusConnector(connectorDTO.get())) {
      throw new InvalidRequestException(String.format("Connector not found for identifier : [%s] with scope: [%s]",
                                            nexusConnectorRef.getIdentifier(), nexusConnectorRef.getScope()),
          WingsException.USER);
    }
    ConnectorInfoDTO connectors = connectorDTO.get().getConnector();
    return (NexusConnectorDTO) connectors.getConnectorConfig();
  }

  private boolean isANexusConnector(@NotNull ConnectorResponseDTO connectorResponseDTO) {
    return ConnectorType.NEXUS == (connectorResponseDTO.getConnector().getConnectorType());
  }

  private BaseNGAccess getBaseNGAccess(String accountId, String orgIdentifier, String projectIdentifier) {
    return BaseNGAccess.builder()
        .accountIdentifier(accountId)
        .orgIdentifier(orgIdentifier)
        .projectIdentifier(projectIdentifier)
        .build();
  }

  private List<EncryptedDataDetail> getEncryptionDetails(
      @Nonnull NexusConnectorDTO nexusConnectorDTO, @Nonnull NGAccess ngAccess) {
    if (nexusConnectorDTO.getAuth() != null && nexusConnectorDTO.getAuth().getCredentials() != null) {
      return secretManagerClientService.getEncryptionDetails(ngAccess, nexusConnectorDTO.getAuth().getCredentials());
    }
    return new ArrayList<>();
  }

  private ArtifactTaskExecutionResponse executeSyncTask(NexusArtifactDelegateRequest nexusRequest,
      ArtifactTaskType taskType, BaseNGAccess ngAccess, String ifFailedMessage) {
    DelegateResponseData responseData = getResponseData(ngAccess, nexusRequest, taskType);
    return getTaskExecutionResponse(responseData, ifFailedMessage);
  }

  private DelegateResponseData getResponseData(
      BaseNGAccess ngAccess, NexusArtifactDelegateRequest delegateRequest, ArtifactTaskType artifactTaskType) {
    ArtifactTaskParameters artifactTaskParameters = ArtifactTaskParameters.builder()
                                                        .accountId(ngAccess.getAccountIdentifier())
                                                        .artifactTaskType(artifactTaskType)
                                                        .attributes(delegateRequest)
                                                        .build();
    Map<String, String> abstractions = ArtifactUtils.getTaskSetupAbstractions(ngAccess);
    final DelegateTaskRequest delegateTaskRequest =
        DelegateTaskRequest.builder()
            .accountId(ngAccess.getAccountIdentifier())
            .taskType(NGTaskType.NEXUS_ARTIFACT_TASK_NG.name())
            .taskParameters(artifactTaskParameters)
            .executionTimeout(java.time.Duration.ofSeconds(timeoutInSecs))
            .taskSetupAbstractions(abstractions)
            .taskSelectors(delegateRequest.getNexusConnectorDTO().getDelegateSelectors())
            .build();
    try {
      return delegateGrpcClientWrapper.executeSyncTaskV2(delegateTaskRequest);
    } catch (DelegateServiceDriverException ex) {
      throw exceptionManager.processException(ex, WingsException.ExecutionContext.MANAGER, log);
    }
  }

  private ArtifactTaskExecutionResponse getTaskExecutionResponse(
      DelegateResponseData responseData, String ifFailedMessage) {
    if (responseData instanceof ErrorNotifyResponseData) {
      ErrorNotifyResponseData errorNotifyResponseData = (ErrorNotifyResponseData) responseData;
      throw new ArtifactServerException(ifFailedMessage + " - " + errorNotifyResponseData.getErrorMessage());
    } else if (responseData instanceof RemoteMethodReturnValueData) {
      RemoteMethodReturnValueData remoteMethodReturnValueData = (RemoteMethodReturnValueData) responseData;
      if (remoteMethodReturnValueData.getException() instanceof InvalidRequestException) {
        throw(InvalidRequestException)(remoteMethodReturnValueData.getException());
      } else {
        throw new NexusRegistryException(
            "Unexpected error during authentication to nexus server " + remoteMethodReturnValueData.getReturnValue());
      }
    }
    ArtifactTaskResponse artifactTaskResponse = (ArtifactTaskResponse) responseData;
    if (artifactTaskResponse.getCommandExecutionStatus() != SUCCESS) {
      throw new NexusRegistryException(ifFailedMessage + " - " + artifactTaskResponse.getErrorMessage()
          + " with error code: " + artifactTaskResponse.getErrorCode());
    }
    return artifactTaskResponse.getArtifactTaskExecutionResponse();
  }

  private NexusResponseDTO getNexusResponseDTO(ArtifactTaskExecutionResponse artifactTaskExecutionResponse) {
    List<NexusArtifactDelegateResponse> nexusArtifactDelegateResponses =
        artifactTaskExecutionResponse.getArtifactDelegateResponses()
            .stream()
            .map(delegateResponse -> (NexusArtifactDelegateResponse) delegateResponse)
            .collect(Collectors.toList());
    return NexusResourceMapper.toNexusResponse(nexusArtifactDelegateResponses);
  }
}
