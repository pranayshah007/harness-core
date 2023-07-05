/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.artifact.resources.artifactory.service;

import static io.harness.connector.ConnectorModule.DEFAULT_CONNECTOR_SERVICE;
import static io.harness.delegate.beans.artifactory.ArtifactoryTaskParams.TaskType.FETCH_BUILDS;
import static io.harness.delegate.beans.artifactory.ArtifactoryTaskParams.TaskType.FETCH_IMAGE_PATHS;
import static io.harness.delegate.beans.artifactory.ArtifactoryTaskParams.TaskType.FETCH_REPOSITORIES;
import static io.harness.eraro.ErrorCode.ARTIFACT_SERVER_ERROR;
import static io.harness.exception.WingsException.USER;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;

import static software.wings.beans.TaskType.NG_ARTIFACTORY_TASK;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DelegateTaskRequest;
import io.harness.beans.IdentifierRef;
import io.harness.cdng.artifact.NGArtifactConstants;
import io.harness.cdng.artifact.resources.artifactory.dtos.ArtifactoryArtifactBuildDetailsDTO;
import io.harness.cdng.artifact.resources.artifactory.dtos.ArtifactoryBuildDetailsDTO;
import io.harness.cdng.artifact.resources.artifactory.dtos.ArtifactoryImagePathsDTO;
import io.harness.cdng.artifact.resources.artifactory.dtos.ArtifactoryRepoDetailsDTO;
import io.harness.cdng.artifact.resources.artifactory.dtos.ArtifactoryRequestDTO;
import io.harness.cdng.artifact.resources.artifactory.dtos.ArtifactoryResponseDTO;
import io.harness.cdng.artifact.resources.artifactory.mappers.ArtifactoryResourceMapper;
import io.harness.cdng.artifact.utils.ArtifactUtils;
import io.harness.common.NGTaskType;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.services.ConnectorService;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.delegate.beans.RemoteMethodReturnValueData;
import io.harness.delegate.beans.artifactory.ArtifactoryFetchBuildsResponse;
import io.harness.delegate.beans.artifactory.ArtifactoryFetchImagePathResponse;
import io.harness.delegate.beans.artifactory.ArtifactoryFetchRepositoriesResponse;
import io.harness.delegate.beans.artifactory.ArtifactoryTaskParams;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryConnectorDTO;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.artifacts.ArtifactDelegateRequestUtils;
import io.harness.delegate.task.artifacts.ArtifactSourceDelegateRequest;
import io.harness.delegate.task.artifacts.ArtifactSourceType;
import io.harness.delegate.task.artifacts.ArtifactTaskType;
import io.harness.delegate.task.artifacts.artifactory.ArtifactoryArtifactDelegateRequest;
import io.harness.delegate.task.artifacts.artifactory.ArtifactoryArtifactDelegateResponse;
import io.harness.delegate.task.artifacts.artifactory.ArtifactoryGenericArtifactDelegateRequest;
import io.harness.delegate.task.artifacts.request.ArtifactTaskParameters;
import io.harness.delegate.task.artifacts.response.ArtifactTaskExecutionResponse;
import io.harness.delegate.task.artifacts.response.ArtifactTaskResponse;
import io.harness.exception.ArtifactServerException;
import io.harness.exception.ArtifactoryRegistryException;
import io.harness.exception.ArtifactoryServerException;
import io.harness.exception.DelegateNotAvailableException;
import io.harness.exception.DelegateServiceDriverException;
import io.harness.exception.HintException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.exception.exceptionmanager.ExceptionManager;
import io.harness.exception.exceptionmanager.exceptionhandler.DocumentLinksConstants;
import io.harness.ng.core.BaseNGAccess;
import io.harness.ng.core.NGAccess;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.service.DelegateGrpcClientWrapper;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.validation.constraints.NotNull;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.MutablePair;

@Singleton
@Slf4j
@OwnedBy(HarnessTeam.CDC)
public class ArtifactoryResourceServiceImpl implements ArtifactoryResourceService {
  private static ConnectorService connectorService;
  private final DelegateGrpcClientWrapper delegateGrpcClientWrapper;
  private final SecretManagerClientService secretManagerClientService;
  @Inject ExceptionManager exceptionManager;

  @VisibleForTesting static final int timeoutInSecs = 60;
  private static final String FAILED_TO_FETCH_REPOSITORIES = "Failed to fetch repositories";
  private static final String FAILED_TO_FETCH_ARTIFACTS = "Failed to fetch artifacts";
  private static final String FAILED_TO_FETCH_IMAGE_PATHS = "Failed to fetch image path";

  @Inject
  public ArtifactoryResourceServiceImpl(@Named(DEFAULT_CONNECTOR_SERVICE) ConnectorService connectorService,
      DelegateGrpcClientWrapper delegateGrpcClientWrapper, SecretManagerClientService secretManagerClientService) {
    this.connectorService = connectorService;
    this.delegateGrpcClientWrapper = delegateGrpcClientWrapper;
    this.secretManagerClientService = secretManagerClientService;
  }

  @Override
  public ArtifactoryRepoDetailsDTO getRepositories(@NonNull String repositoryType, @NonNull IdentifierRef connectorRef,
      String orgIdentifier, String projectIdentifier) {
    Optional<ConnectorResponseDTO> connectorDTO = connectorService.get(connectorRef.getAccountIdentifier(),
        connectorRef.getOrgIdentifier(), connectorRef.getProjectIdentifier(), connectorRef.getIdentifier());
    if (!connectorDTO.isPresent()
        || ConnectorType.ARTIFACTORY != connectorDTO.get().getConnector().getConnectorType()) {
      throw new InvalidRequestException(String.format("Connector not found for identifier : [%s] with scope: [%s]",
                                            connectorRef.getIdentifier(), connectorRef.getScope()),
          WingsException.USER);
    }
    BaseNGAccess baseNGAccess = getBaseNGAccess(connectorRef.getAccountIdentifier(), orgIdentifier, projectIdentifier);

    ConnectorInfoDTO connectors = connectorDTO.get().getConnector();
    ArtifactoryConnectorDTO artifactoryConnectorDTO = (ArtifactoryConnectorDTO) connectors.getConnectorConfig();

    DelegateResponseData delegateResponseData = getResponseData(baseNGAccess,
        ArtifactoryTaskParams.builder()
            .artifactoryConnectorDTO(artifactoryConnectorDTO)
            .encryptedDataDetails(getEncryptionDetails(artifactoryConnectorDTO, baseNGAccess))
            .taskType(FETCH_REPOSITORIES)
            .repoType(repositoryType)
            .build(),
        NG_ARTIFACTORY_TASK.name());

    return ArtifactoryRepoDetailsDTO.builder()
        .repositories(getArtifactoryFetchRepositoriesResponse(delegateResponseData).getRepositories())
        .build();
  }

  @Override
  public List<ArtifactoryArtifactBuildDetailsDTO> getBuildDetails(@NonNull String repositoryName,
      @NonNull String filePath, int maxVersions, @NonNull IdentifierRef connectorRef, @NonNull String orgIdentifier,
      @NonNull String projectIdentifier) {
    Optional<ConnectorResponseDTO> connectorDTO = connectorService.get(connectorRef.getAccountIdentifier(),
        connectorRef.getOrgIdentifier(), connectorRef.getProjectIdentifier(), connectorRef.getIdentifier());
    if (!connectorDTO.isPresent()
        || ConnectorType.ARTIFACTORY != connectorDTO.get().getConnector().getConnectorType()) {
      throw new InvalidRequestException(String.format("Connector not found for identifier : [%s] with scope: [%s]",
                                            connectorRef.getIdentifier(), connectorRef.getScope()),
          WingsException.USER);
    }
    BaseNGAccess baseNGAccess = getBaseNGAccess(connectorRef.getAccountIdentifier(), orgIdentifier, projectIdentifier);

    ConnectorInfoDTO connectors = connectorDTO.get().getConnector();
    ArtifactoryConnectorDTO artifactoryConnectorDTO = (ArtifactoryConnectorDTO) connectors.getConnectorConfig();
    ArtifactoryTaskParams artifactoryTaskParams =
        ArtifactoryTaskParams.builder()
            .artifactoryConnectorDTO(artifactoryConnectorDTO)
            .encryptedDataDetails(getEncryptionDetails(artifactoryConnectorDTO, baseNGAccess))
            .taskType(FETCH_BUILDS)
            .repoName(repositoryName)
            .filePath(filePath)
            .maxVersions(maxVersions)
            .build();

    DelegateResponseData delegateResponseData =
        getResponseData(baseNGAccess, artifactoryTaskParams, NG_ARTIFACTORY_TASK.name());

    return ArtifactoryResourceMapper.toArtifactoryArtifactBuildDetailsDTO(
        getArtifactoryFetchBuildsResponse(delegateResponseData).getBuildDetails());
  }

  private DelegateResponseData getResponseData(BaseNGAccess ngAccess, TaskParameters taskParameters, String taskType) {
    Map<String, String> abstractions = ArtifactUtils.getTaskSetupAbstractions(ngAccess);

    final DelegateTaskRequest delegateTaskRequest = DelegateTaskRequest.builder()
                                                        .accountId(ngAccess.getAccountIdentifier())
                                                        .taskType(taskType)
                                                        .taskParameters(taskParameters)
                                                        .executionTimeout(java.time.Duration.ofSeconds(timeoutInSecs))
                                                        .taskSetupAbstractions(abstractions)
                                                        .build();
    try {
      return delegateGrpcClientWrapper.executeSyncTaskV2(delegateTaskRequest);
    } catch (DelegateServiceDriverException ex) {
      throw exceptionManager.processException(ex, WingsException.ExecutionContext.MANAGER, log);
    }
  }

  private ArtifactoryFetchRepositoriesResponse getArtifactoryFetchRepositoriesResponse(
      DelegateResponseData responseData) {
    handleErrorResponseData(responseData, FAILED_TO_FETCH_REPOSITORIES);

    ArtifactoryFetchRepositoriesResponse taskResponse = (ArtifactoryFetchRepositoriesResponse) responseData;
    if (taskResponse.getCommandExecutionStatus() != SUCCESS) {
      throw new ArtifactoryServerException(FAILED_TO_FETCH_REPOSITORIES, ARTIFACT_SERVER_ERROR, USER);
    }
    return taskResponse;
  }
  private ArtifactoryFetchImagePathResponse getArtifactoryFetchImagePathResponse(DelegateResponseData responseData) {
    handleErrorResponseData(responseData, FAILED_TO_FETCH_REPOSITORIES);

    ArtifactoryFetchImagePathResponse taskResponse = (ArtifactoryFetchImagePathResponse) responseData;
    if (taskResponse.getCommandExecutionStatus() != SUCCESS) {
      throw new ArtifactoryServerException(FAILED_TO_FETCH_IMAGE_PATHS, ARTIFACT_SERVER_ERROR, USER);
    }
    return taskResponse;
  }

  private ArtifactoryFetchBuildsResponse getArtifactoryFetchBuildsResponse(DelegateResponseData responseData) {
    handleErrorResponseData(responseData, FAILED_TO_FETCH_ARTIFACTS);

    ArtifactoryFetchBuildsResponse taskResponse = (ArtifactoryFetchBuildsResponse) responseData;
    if (taskResponse.getCommandExecutionStatus() != SUCCESS) {
      throw new ArtifactoryServerException(FAILED_TO_FETCH_ARTIFACTS, ARTIFACT_SERVER_ERROR, USER);
    }
    return taskResponse;
  }

  private void handleErrorResponseData(DelegateResponseData responseData, String errorMessage) {
    if (responseData instanceof ErrorNotifyResponseData) {
      ErrorNotifyResponseData errorNotifyResponseData = (ErrorNotifyResponseData) responseData;
      throw new ArtifactoryServerException(
          errorMessage + " - " + errorNotifyResponseData.getErrorMessage(), ARTIFACT_SERVER_ERROR, USER);
    }
  }

  @Override
  public ArtifactoryResponseDTO getBuildDetails(IdentifierRef artifactoryConnectorRef, String repositoryName,
      String artifactPath, String repositoryFormat, String artifactRepositoryUrl, String orgIdentifier,
      String projectIdentifier) {
    ArtifactUtils.validateIfAllValuesAssigned(MutablePair.of(NGArtifactConstants.REPOSITORY, repositoryName),
        MutablePair.of(NGArtifactConstants.ARTIFACT_PATH, artifactPath));
    ArtifactoryConnectorDTO connector = getConnector(artifactoryConnectorRef);
    BaseNGAccess baseNGAccess =
        getBaseNGAccess(artifactoryConnectorRef.getAccountIdentifier(), orgIdentifier, projectIdentifier);
    List<EncryptedDataDetail> encryptionDetails = getEncryptionDetails(connector, baseNGAccess);
    ArtifactSourceDelegateRequest artifactoryRequest =
        ArtifactDelegateRequestUtils.getArtifactoryArtifactDelegateRequest(repositoryName, artifactPath,
            repositoryFormat, artifactRepositoryUrl, null, null, null, connector, encryptionDetails,
            ArtifactSourceType.ARTIFACTORY_REGISTRY);
    try {
      ArtifactTaskExecutionResponse artifactTaskExecutionResponse = executeSyncTask(artifactoryRequest,
          ArtifactTaskType.GET_BUILDS, baseNGAccess, "Artifactory Artifact Get Builds task failure due to error");
      return ArtifactoryResourceMapper.getArtifactoryResponseDTO(artifactTaskExecutionResponse, repositoryFormat);
    } catch (DelegateServiceDriverException ex) {
      throw new HintException(
          String.format(HintException.DELEGATE_NOT_AVAILABLE, DocumentLinksConstants.DELEGATE_INSTALLATION_LINK),
          new DelegateNotAvailableException(ex.getCause().getMessage(), WingsException.USER));
    }
  }

  @Override
  public ArtifactoryBuildDetailsDTO getSuccessfulBuild(IdentifierRef artifactoryConnectorRef, String repositoryName,
      String artifactPath, String repositoryFormat, String artifactRepositoryUrl,
      ArtifactoryRequestDTO artifactoryRequestDTO, String orgIdentifier, String projectIdentifier) {
    ArtifactUtils.validateIfAllValuesAssigned(MutablePair.of(NGArtifactConstants.REPOSITORY, repositoryName),
        MutablePair.of(NGArtifactConstants.REPOSITORY_FORMAT, repositoryFormat));
    ArtifactUtils.validateIfAnyValueAssigned(MutablePair.of(NGArtifactConstants.TAG, artifactoryRequestDTO.getTag()),
        MutablePair.of(NGArtifactConstants.TAG_REGEX, artifactoryRequestDTO.getTagRegex()));
    ArtifactoryConnectorDTO connector = getConnector(artifactoryConnectorRef);
    BaseNGAccess baseNGAccess =
        getBaseNGAccess(artifactoryConnectorRef.getAccountIdentifier(), orgIdentifier, projectIdentifier);
    List<EncryptedDataDetail> encryptionDetails = getEncryptionDetails(connector, baseNGAccess);
    ArtifactoryArtifactDelegateRequest artifactoryRequest =
        (ArtifactoryArtifactDelegateRequest) ArtifactDelegateRequestUtils.getArtifactoryArtifactDelegateRequest(
            repositoryName, artifactPath, repositoryFormat, artifactRepositoryUrl, artifactoryRequestDTO.getTag(),
            artifactoryRequestDTO.getTagRegex(), null, connector, encryptionDetails,
            ArtifactSourceType.ARTIFACTORY_REGISTRY);
    ArtifactTaskExecutionResponse artifactTaskExecutionResponse =
        executeSyncTask(artifactoryRequest, ArtifactTaskType.GET_LAST_SUCCESSFUL_BUILD, baseNGAccess,
            "Artifactory Get last successful build task failure due to error");
    ArtifactoryResponseDTO artifactoryResponseDTO = getArtifactoryResponseDTO(artifactTaskExecutionResponse);
    if (artifactoryResponseDTO.getBuildDetailsList().size() != 1) {
      throw new ArtifactoryRegistryException(
          "Artifactory get last successful build task failure. Expected was to get 1 build, but instead got "
          + artifactoryResponseDTO.getBuildDetailsList().size() + " builds.");
    }
    return artifactoryResponseDTO.getBuildDetailsList().get(0);
  }

  @Override
  public boolean validateArtifactServer(
      IdentifierRef artifactoryConnectorRef, String orgIdentifier, String projectIdentifier) {
    ArtifactoryConnectorDTO connector = getConnector(artifactoryConnectorRef);
    BaseNGAccess baseNGAccess =
        getBaseNGAccess(artifactoryConnectorRef.getAccountIdentifier(), orgIdentifier, projectIdentifier);
    List<EncryptedDataDetail> encryptionDetails = getEncryptionDetails(connector, baseNGAccess);
    ArtifactoryArtifactDelegateRequest artifactoryRequest =
        (ArtifactoryArtifactDelegateRequest) ArtifactDelegateRequestUtils.getArtifactoryArtifactDelegateRequest(null,
            null, null, null, null, null, null, connector, encryptionDetails, ArtifactSourceType.ARTIFACTORY_REGISTRY);
    ArtifactTaskExecutionResponse artifactTaskExecutionResponse =
        executeSyncTask(artifactoryRequest, ArtifactTaskType.VALIDATE_ARTIFACT_SERVER, baseNGAccess,
            "Artifactory validate artifact server task failure due to error");
    return artifactTaskExecutionResponse.isArtifactServerValid();
  }

  @Override
  public ArtifactoryImagePathsDTO getImagePaths(@NonNull String repositoryType, @NonNull IdentifierRef connectorRef,
      String orgIdentifier, String projectIdentifier, @NotNull String repository) {
    ArtifactUtils.validateIfAllValuesAssigned(MutablePair.of(NGArtifactConstants.REPOSITORY, repository));

    Optional<ConnectorResponseDTO> connectorDTO = connectorService.get(connectorRef.getAccountIdentifier(),
        connectorRef.getOrgIdentifier(), connectorRef.getProjectIdentifier(), connectorRef.getIdentifier());
    if (!connectorDTO.isPresent()
        || ConnectorType.ARTIFACTORY != connectorDTO.get().getConnector().getConnectorType()) {
      throw new InvalidRequestException(String.format("Connector not found for identifier : [%s] with scope: [%s]",
                                            connectorRef.getIdentifier(), connectorRef.getScope()),
          WingsException.USER);
    }
    BaseNGAccess baseNGAccess = getBaseNGAccess(connectorRef.getAccountIdentifier(), orgIdentifier, projectIdentifier);

    ConnectorInfoDTO connectors = connectorDTO.get().getConnector();
    ArtifactoryConnectorDTO artifactoryConnectorDTO = (ArtifactoryConnectorDTO) connectors.getConnectorConfig();

    DelegateResponseData delegateResponseData = getResponseData(baseNGAccess,
        ArtifactoryTaskParams.builder()
            .artifactoryConnectorDTO(artifactoryConnectorDTO)
            .encryptedDataDetails(getEncryptionDetails(artifactoryConnectorDTO, baseNGAccess))
            .taskType(FETCH_IMAGE_PATHS)
            .repoType(repositoryType)
            .repoName(repository)
            .build(),
        NG_ARTIFACTORY_TASK.name());
    ArtifactoryFetchImagePathResponse imagePathResponse = getArtifactoryFetchImagePathResponse(delegateResponseData);
    return ArtifactoryImagePathsDTO.builder().imagePaths(imagePathResponse.getArtifactoryImagePathsFetchDTO()).build();
  }

  public static ArtifactoryConnectorDTO getConnector(IdentifierRef artifactoryConnectorRef) {
    Optional<ConnectorResponseDTO> connectorDTO =
        connectorService.get(artifactoryConnectorRef.getAccountIdentifier(), artifactoryConnectorRef.getOrgIdentifier(),
            artifactoryConnectorRef.getProjectIdentifier(), artifactoryConnectorRef.getIdentifier());

    if (!connectorDTO.isPresent() || !isAArtifactoryConnector(connectorDTO.get())) {
      throw new ArtifactoryRegistryException(String.format("Connector not found for identifier : [%s] with scope: [%s]",
          artifactoryConnectorRef.getIdentifier(), artifactoryConnectorRef.getScope()));
    }
    ConnectorInfoDTO connectors = connectorDTO.get().getConnector();
    return (ArtifactoryConnectorDTO) connectors.getConnectorConfig();
  }

  private static boolean isAArtifactoryConnector(@NotNull ConnectorResponseDTO connectorResponseDTO) {
    return ConnectorType.ARTIFACTORY == (connectorResponseDTO.getConnector().getConnectorType());
  }

  private BaseNGAccess getBaseNGAccess(String accountId, String orgIdentifier, String projectIdentifier) {
    return BaseNGAccess.builder()
        .accountIdentifier(accountId)
        .orgIdentifier(orgIdentifier)
        .projectIdentifier(projectIdentifier)
        .build();
  }

  private List<EncryptedDataDetail> getEncryptionDetails(
      @Nonnull ArtifactoryConnectorDTO artifactoryConnectorDTO, @Nonnull NGAccess ngAccess) {
    if (artifactoryConnectorDTO.getAuth().getCredentials() != null) {
      return secretManagerClientService.getEncryptionDetails(
          ngAccess, artifactoryConnectorDTO.getAuth().getCredentials());
    }
    return Collections.emptyList();
  }

  private ArtifactTaskExecutionResponse executeSyncTask(ArtifactSourceDelegateRequest artifactoryRequest,
      ArtifactTaskType taskType, BaseNGAccess ngAccess, String ifFailedMessage) {
    DelegateResponseData responseData = getResponseData(ngAccess, artifactoryRequest, taskType);
    return getTaskExecutionResponse(responseData, ifFailedMessage);
  }

  private DelegateResponseData getResponseData(
      BaseNGAccess ngAccess, ArtifactSourceDelegateRequest delegateRequest, ArtifactTaskType artifactTaskType) {
    Set<String> delegateSelectors;
    if (delegateRequest instanceof ArtifactoryGenericArtifactDelegateRequest) {
      delegateSelectors = ((ArtifactoryGenericArtifactDelegateRequest) delegateRequest)
                              .getArtifactoryConnectorDTO()
                              .getDelegateSelectors();
    } else {
      delegateSelectors =
          ((ArtifactoryArtifactDelegateRequest) delegateRequest).getArtifactoryConnectorDTO().getDelegateSelectors();
    }
    ArtifactTaskParameters artifactTaskParameters = ArtifactTaskParameters.builder()
                                                        .accountId(ngAccess.getAccountIdentifier())
                                                        .artifactTaskType(artifactTaskType)
                                                        .attributes(delegateRequest)
                                                        .build();
    Map<String, String> abstractions = ArtifactUtils.getTaskSetupAbstractions(ngAccess);

    final DelegateTaskRequest delegateTaskRequest = DelegateTaskRequest.builder()
                                                        .accountId(ngAccess.getAccountIdentifier())
                                                        .taskType(NGTaskType.ARTIFACTORY_ARTIFACT_TASK_NG.name())
                                                        .taskParameters(artifactTaskParameters)
                                                        .executionTimeout(java.time.Duration.ofSeconds(timeoutInSecs))
                                                        .taskSetupAbstractions(abstractions)
                                                        .taskSelectors(delegateSelectors)
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
        throw new ArtifactoryRegistryException("Unexpected error during authentication to artifactory server "
            + remoteMethodReturnValueData.getReturnValue());
      }
    }
    ArtifactTaskResponse artifactTaskResponse = (ArtifactTaskResponse) responseData;
    if (artifactTaskResponse.getCommandExecutionStatus() != SUCCESS) {
      throw new ArtifactoryRegistryException(ifFailedMessage + " - " + artifactTaskResponse.getErrorMessage()
          + " with error code: " + artifactTaskResponse.getErrorCode());
    }
    return artifactTaskResponse.getArtifactTaskExecutionResponse();
  }

  private ArtifactoryResponseDTO getArtifactoryResponseDTO(
      ArtifactTaskExecutionResponse artifactTaskExecutionResponse) {
    List<ArtifactoryArtifactDelegateResponse> artifactoryArtifactDelegateResponses =
        artifactTaskExecutionResponse.getArtifactDelegateResponses()
            .stream()
            .map(delegateResponse -> (ArtifactoryArtifactDelegateResponse) delegateResponse)
            .collect(Collectors.toList());
    return ArtifactoryResourceMapper.toArtifactoryDockerResponse(artifactoryArtifactDelegateResponses);
  }
}