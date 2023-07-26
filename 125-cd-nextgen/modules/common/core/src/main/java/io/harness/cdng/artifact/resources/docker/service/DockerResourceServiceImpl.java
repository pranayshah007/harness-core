/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.artifact.resources.docker.service;
import static io.harness.connector.ConnectorModule.DEFAULT_CONNECTOR_SERVICE;
import static io.harness.exception.WingsException.USER;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.utils.DelegateOwner.getNGTaskSetupAbstractionsWithOwner;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.DelegateTaskRequest;
import io.harness.beans.FeatureName;
import io.harness.beans.IdentifierRef;
import io.harness.cdng.artifact.NGArtifactConstants;
import io.harness.cdng.artifact.resources.docker.dtos.DockerBuildDetailsDTO;
import io.harness.cdng.artifact.resources.docker.dtos.DockerRequestDTO;
import io.harness.cdng.artifact.resources.docker.dtos.DockerResponseDTO;
import io.harness.cdng.artifact.resources.docker.mappers.DockerResourceMapper;
import io.harness.cdng.artifact.utils.ArtifactUtils;
import io.harness.cdng.featureFlag.CDFeatureFlagHelper;
import io.harness.common.NGTaskType;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.services.ConnectorService;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.delegate.beans.RemoteMethodReturnValueData;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.docker.DockerConnectorDTO;
import io.harness.delegate.task.artifacts.ArtifactDelegateRequestUtils;
import io.harness.delegate.task.artifacts.ArtifactSourceType;
import io.harness.delegate.task.artifacts.ArtifactTaskType;
import io.harness.delegate.task.artifacts.docker.DockerArtifactDelegateRequest;
import io.harness.delegate.task.artifacts.docker.DockerArtifactDelegateResponse;
import io.harness.delegate.task.artifacts.request.ArtifactTaskParameters;
import io.harness.delegate.task.artifacts.response.ArtifactTaskExecutionResponse;
import io.harness.delegate.task.artifacts.response.ArtifactTaskResponse;
import io.harness.exception.ArtifactServerException;
import io.harness.exception.DelegateNotAvailableException;
import io.harness.exception.DelegateServiceDriverException;
import io.harness.exception.ExplanationException;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.MutablePair;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_ARTIFACTS})
@Singleton
@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public class DockerResourceServiceImpl implements DockerResourceService {
  private final CDFeatureFlagHelper cdFeatureFlagHelper;
  private final ConnectorService connectorService;
  private final SecretManagerClientService secretManagerClientService;
  @Inject private DelegateGrpcClientWrapper delegateGrpcClientWrapper;
  @Inject ExceptionManager exceptionManager;
  @VisibleForTesting static final int timeoutInSecs = 30;

  @Inject
  public DockerResourceServiceImpl(@Named(DEFAULT_CONNECTOR_SERVICE) ConnectorService connectorService,
      SecretManagerClientService secretManagerClientService, CDFeatureFlagHelper cdFeatureFlagHelper) {
    this.connectorService = connectorService;
    this.secretManagerClientService = secretManagerClientService;
    this.cdFeatureFlagHelper = cdFeatureFlagHelper;
  }

  @Override
  public DockerResponseDTO getBuildDetails(IdentifierRef dockerConnectorRef, String imagePath, String orgIdentifier,
      String projectIdentifier, String tagRegex) {
    DockerConnectorDTO connector = getConnector(dockerConnectorRef);
    BaseNGAccess baseNGAccess =
        getBaseNGAccess(dockerConnectorRef.getAccountIdentifier(), orgIdentifier, projectIdentifier);
    List<EncryptedDataDetail> encryptionDetails = getEncryptionDetails(connector, baseNGAccess);
    DockerArtifactDelegateRequest dockerRequest = ArtifactDelegateRequestUtils.getDockerDelegateRequest(
        imagePath, null, tagRegex, null, null, connector, encryptionDetails, ArtifactSourceType.DOCKER_REGISTRY, false);
    try {
      ArtifactTaskExecutionResponse artifactTaskExecutionResponse = executeSyncTask(
          dockerRequest, ArtifactTaskType.GET_BUILDS, baseNGAccess, "Docker Get Builds task failure due to error");
      return getDockerResponseDTO(artifactTaskExecutionResponse);
    } catch (DelegateServiceDriverException ex) {
      throw new HintException(
          String.format(HintException.DELEGATE_NOT_AVAILABLE, DocumentLinksConstants.DELEGATE_INSTALLATION_LINK),
          new DelegateNotAvailableException(ex.getCause().getMessage(), WingsException.USER));
    } catch (ExplanationException e) {
      if (e.getMessage().contains("no metadata was returned")) {
        throw new HintException(
            HintException.HINT_DOCKER_HUB_INVALID_IMAGE_PATH, new InvalidRequestException(e.getMessage(), USER));
      }
      throw new HintException(
          HintException.HINT_DOCKER_HUB_ACCESS_DENIED, new InvalidRequestException(e.getMessage(), USER));
    }
  }

  @Override
  public DockerResponseDTO getLabels(IdentifierRef dockerConnectorRef, String imagePath,
      DockerRequestDTO dockerRequestDTO, String orgIdentifier, String projectIdentifier) {
    DockerConnectorDTO connector = getConnector(dockerConnectorRef);
    BaseNGAccess baseNGAccess =
        getBaseNGAccess(dockerConnectorRef.getAccountIdentifier(), orgIdentifier, projectIdentifier);
    List<EncryptedDataDetail> encryptionDetails = getEncryptionDetails(connector, baseNGAccess);
    DockerArtifactDelegateRequest dockerRequest = ArtifactDelegateRequestUtils.getDockerDelegateRequest(imagePath,
        dockerRequestDTO.getTag(), dockerRequestDTO.getTagRegex(), dockerRequestDTO.getTagsList(), null, connector,
        encryptionDetails, ArtifactSourceType.DOCKER_REGISTRY, false);
    ArtifactTaskExecutionResponse artifactTaskExecutionResponse = executeSyncTask(
        dockerRequest, ArtifactTaskType.GET_LABELS, baseNGAccess, "Docker Get labels task failure due to error");
    return getDockerResponseDTO(artifactTaskExecutionResponse);
  }

  @Override
  public DockerBuildDetailsDTO getSuccessfulBuild(IdentifierRef dockerConnectorRef, String imagePath,
      DockerRequestDTO dockerRequestDTO, String orgIdentifier, String projectIdentifier) {
    ArtifactUtils.validateIfAllValuesAssigned(MutablePair.of(NGArtifactConstants.IMAGE_PATH, imagePath));
    ArtifactUtils.validateIfAnyValueAssigned(MutablePair.of(NGArtifactConstants.TAG, dockerRequestDTO.getTag()),
        MutablePair.of(NGArtifactConstants.TAG_REGEX, dockerRequestDTO.getTagRegex()));
    DockerConnectorDTO connector = getConnector(dockerConnectorRef);
    BaseNGAccess baseNGAccess =
        getBaseNGAccess(dockerConnectorRef.getAccountIdentifier(), orgIdentifier, projectIdentifier);
    List<EncryptedDataDetail> encryptionDetails = getEncryptionDetails(connector, baseNGAccess);
    boolean shouldFetchDockerV2DigestSHA256 = cdFeatureFlagHelper.isEnabled(
        dockerConnectorRef.getAccountIdentifier(), FeatureName.CD_NG_DOCKER_ARTIFACT_DIGEST);
    DockerArtifactDelegateRequest dockerRequest = ArtifactDelegateRequestUtils.getDockerDelegateRequest(imagePath,
        dockerRequestDTO.getTag(), dockerRequestDTO.getTagRegex(), dockerRequestDTO.getTagsList(), null, connector,
        encryptionDetails, ArtifactSourceType.DOCKER_REGISTRY, shouldFetchDockerV2DigestSHA256);
    ArtifactTaskExecutionResponse artifactTaskExecutionResponse =
        executeSyncTask(dockerRequest, ArtifactTaskType.GET_LAST_SUCCESSFUL_BUILD, baseNGAccess,
            "Docker Get last successful build task failure due to error");
    DockerResponseDTO dockerResponseDTO = getDockerResponseDTO(artifactTaskExecutionResponse);
    if (dockerResponseDTO.getBuildDetailsList().size() != 1) {
      throw new ArtifactServerException("Docker get last successful build task failure.");
    }
    return dockerResponseDTO.getBuildDetailsList().get(0);
  }

  @Override
  public boolean validateArtifactServer(
      IdentifierRef dockerConnectorRef, String orgIdentifier, String projectIdentifier) {
    DockerConnectorDTO connector = getConnector(dockerConnectorRef);
    BaseNGAccess baseNGAccess =
        getBaseNGAccess(dockerConnectorRef.getAccountIdentifier(), orgIdentifier, projectIdentifier);
    List<EncryptedDataDetail> encryptionDetails = getEncryptionDetails(connector, baseNGAccess);
    DockerArtifactDelegateRequest dockerRequest = ArtifactDelegateRequestUtils.getDockerDelegateRequest(
        null, null, null, null, null, connector, encryptionDetails, ArtifactSourceType.DOCKER_REGISTRY, false);
    ArtifactTaskExecutionResponse artifactTaskExecutionResponse =
        executeSyncTask(dockerRequest, ArtifactTaskType.VALIDATE_ARTIFACT_SERVER, baseNGAccess,
            "Docker validate artifact server task failure due to error");
    return artifactTaskExecutionResponse.isArtifactServerValid();
  }

  @Override
  public boolean validateArtifactSource(
      String imagePath, IdentifierRef dockerConnectorRef, String orgIdentifier, String projectIdentifier) {
    DockerConnectorDTO connector = getConnector(dockerConnectorRef);
    BaseNGAccess baseNGAccess =
        getBaseNGAccess(dockerConnectorRef.getAccountIdentifier(), orgIdentifier, projectIdentifier);
    List<EncryptedDataDetail> encryptionDetails = getEncryptionDetails(connector, baseNGAccess);
    DockerArtifactDelegateRequest dockerRequest = ArtifactDelegateRequestUtils.getDockerDelegateRequest(
        imagePath, null, null, null, null, connector, encryptionDetails, ArtifactSourceType.DOCKER_REGISTRY, false);
    ArtifactTaskExecutionResponse artifactTaskExecutionResponse =
        executeSyncTask(dockerRequest, ArtifactTaskType.VALIDATE_ARTIFACT_SOURCE, baseNGAccess,
            "Docker validate artifact source task failure due to error");
    return artifactTaskExecutionResponse.isArtifactSourceValid();
  }

  private DockerConnectorDTO getConnector(IdentifierRef dockerConnectorRef) {
    Optional<ConnectorResponseDTO> connectorDTO =
        connectorService.get(dockerConnectorRef.getAccountIdentifier(), dockerConnectorRef.getOrgIdentifier(),
            dockerConnectorRef.getProjectIdentifier(), dockerConnectorRef.getIdentifier());

    if (!connectorDTO.isPresent() || !isADockerConnector(connectorDTO.get())) {
      throw new InvalidRequestException(String.format("Connector not found for identifier : [%s] with scope: [%s]",
                                            dockerConnectorRef.getIdentifier(), dockerConnectorRef.getScope()),
          WingsException.USER);
    }
    ConnectorInfoDTO connectors = connectorDTO.get().getConnector();
    return (DockerConnectorDTO) connectors.getConnectorConfig();
  }

  private boolean isADockerConnector(@Valid @NotNull ConnectorResponseDTO connectorResponseDTO) {
    return ConnectorType.DOCKER == (connectorResponseDTO.getConnector().getConnectorType());
  }

  private BaseNGAccess getBaseNGAccess(String accountId, String orgIdentifier, String projectIdentifier) {
    return BaseNGAccess.builder()
        .accountIdentifier(accountId)
        .orgIdentifier(orgIdentifier)
        .projectIdentifier(projectIdentifier)
        .build();
  }

  private List<EncryptedDataDetail> getEncryptionDetails(
      @Nonnull DockerConnectorDTO dockerConnectorDTO, @Nonnull NGAccess ngAccess) {
    if (dockerConnectorDTO.getAuth() != null && dockerConnectorDTO.getAuth().getCredentials() != null) {
      return secretManagerClientService.getEncryptionDetails(ngAccess, dockerConnectorDTO.getAuth().getCredentials());
    }
    return new ArrayList<>();
  }

  private ArtifactTaskExecutionResponse executeSyncTask(DockerArtifactDelegateRequest dockerRequest,
      ArtifactTaskType taskType, BaseNGAccess ngAccess, String ifFailedMessage) {
    DelegateResponseData responseData = getResponseData(ngAccess, dockerRequest, taskType);
    return getTaskExecutionResponse(responseData, ifFailedMessage);
  }

  private DelegateResponseData getResponseData(
      BaseNGAccess ngAccess, DockerArtifactDelegateRequest delegateRequest, ArtifactTaskType artifactTaskType) {
    ArtifactTaskParameters artifactTaskParameters = ArtifactTaskParameters.builder()
                                                        .accountId(ngAccess.getAccountIdentifier())
                                                        .artifactTaskType(artifactTaskType)
                                                        .attributes(delegateRequest)
                                                        .build();
    Map<String, String> owner = getNGTaskSetupAbstractionsWithOwner(
        ngAccess.getAccountIdentifier(), ngAccess.getOrgIdentifier(), ngAccess.getProjectIdentifier());
    Map<String, String> abstractions = new HashMap<>(owner);
    if (ngAccess.getOrgIdentifier() != null) {
      abstractions.put("orgIdentifier", ngAccess.getOrgIdentifier());
    }
    if (ngAccess.getProjectIdentifier() != null && ngAccess.getOrgIdentifier() != null) {
      abstractions.put("projectIdentifier", ngAccess.getProjectIdentifier());
    }
    final DelegateTaskRequest delegateTaskRequest =
        DelegateTaskRequest.builder()
            .accountId(ngAccess.getAccountIdentifier())
            .taskType(NGTaskType.DOCKER_ARTIFACT_TASK_NG.name())
            .taskParameters(artifactTaskParameters)
            .executionTimeout(java.time.Duration.ofSeconds(timeoutInSecs))
            .taskSetupAbstractions(abstractions)
            .taskSelectors(delegateRequest.getDockerConnectorDTO().getDelegateSelectors())
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
        throw new ArtifactServerException(
            "Unexpected error during authentication to docker server " + remoteMethodReturnValueData.getReturnValue(),
            WingsException.USER);
      }
    }
    ArtifactTaskResponse artifactTaskResponse = (ArtifactTaskResponse) responseData;
    if (artifactTaskResponse.getCommandExecutionStatus() != SUCCESS) {
      throw new ArtifactServerException(ifFailedMessage + " - " + artifactTaskResponse.getErrorMessage()
          + " with error code: " + artifactTaskResponse.getErrorCode());
    }
    return artifactTaskResponse.getArtifactTaskExecutionResponse();
  }

  private DockerResponseDTO getDockerResponseDTO(ArtifactTaskExecutionResponse artifactTaskExecutionResponse) {
    List<DockerArtifactDelegateResponse> dockerArtifactDelegateResponses =
        artifactTaskExecutionResponse.getArtifactDelegateResponses()
            .stream()
            .map(delegateResponse -> (DockerArtifactDelegateResponse) delegateResponse)
            .collect(Collectors.toList());
    return DockerResourceMapper.toDockerResponse(dockerArtifactDelegateResponses);
  }
}
