/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.artifact.resources.googleartifactregistry.service;

import static io.harness.cdng.artifact.resources.googleartifactregistry.mappers.GARResourceMapper.toGarResponse;
import static io.harness.connector.ConnectorModule.DEFAULT_CONNECTOR_SERVICE;
import static io.harness.exception.WingsException.USER;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DelegateTaskRequest;
import io.harness.beans.IdentifierRef;
import io.harness.cdng.artifact.NGArtifactConstants;
import io.harness.cdng.artifact.resources.googleartifactregistry.dtos.GARBuildDetailsDTO;
import io.harness.cdng.artifact.resources.googleartifactregistry.dtos.GARResponseDTO;
import io.harness.cdng.artifact.resources.googleartifactregistry.dtos.GarRequestDTO;
import io.harness.cdng.artifact.utils.ArtifactUtils;
import io.harness.common.NGTaskType;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.services.ConnectorService;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.delegate.beans.RemoteMethodReturnValueData;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorDTO;
import io.harness.delegate.task.artifacts.ArtifactDelegateRequestUtils;
import io.harness.delegate.task.artifacts.ArtifactSourceType;
import io.harness.delegate.task.artifacts.ArtifactTaskType;
import io.harness.delegate.task.artifacts.gar.GarDelegateRequest;
import io.harness.delegate.task.artifacts.gar.GarDelegateResponse;
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

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.MutablePair;

@Singleton
@Slf4j
@OwnedBy(HarnessTeam.CDC)
public class GARResourceServiceImpl implements GARResourceService {
  private final ConnectorService connectorService;
  private final SecretManagerClientService secretManagerClientService;
  private static final int TIMEOUTINSEC = 30;
  private static final int MAXBUILDS = -1;
  @Inject private DelegateGrpcClientWrapper delegateGrpcClientWrapper;
  @Inject ExceptionManager exceptionManager;
  public static final List<RegionGar> GAR_REGIONS =
      Arrays
          .asList("asia", "asia-east1", "asia-east2", "asia-northeast1", "asia-northeast2", "asia-northeast3",
              "asia-south1", "asia-south2", "asia-southeast1", "asia-southeast2", "australia-southeast1",
              "australia-southeast2", "europe", "europe-central2", "europe-north1", "europe-southwest1", "europe-west1",
              "europe-west2", "europe-west3", "europe-west4", "europe-west6", "europe-west8", "europe-west9",
              "northamerica-northeast1", "northamerica-northeast2", "southamerica-east1", "southamerica-west1", "us",
              "us-central1", "us-east1", "us-east4", "us-east5", "us-south1", "us-west1", "us-west2", "us-west3",
              "us-west4")
          .stream()
          .map((String region) -> new RegionGar(region, region))
          .collect(Collectors.toList());

  @Inject
  public GARResourceServiceImpl(@Named(DEFAULT_CONNECTOR_SERVICE) ConnectorService connectorService,
      SecretManagerClientService secretManagerClientService) {
    this.connectorService = connectorService;
    this.secretManagerClientService = secretManagerClientService;
  }

  @Override
  public GARResponseDTO getBuildDetails(IdentifierRef googleArtifactRegistryRef, String region, String repositoryName,
      String project, String pkg, String version, String versionRegex, String orgIdentifier, String projectIdentifier) {
    GcpConnectorDTO connector = getConnector(googleArtifactRegistryRef);
    BaseNGAccess baseNGAccess =
        getBaseNGAccess(googleArtifactRegistryRef.getAccountIdentifier(), orgIdentifier, projectIdentifier);
    List<EncryptedDataDetail> encryptionDetails = getEncryptionDetails(connector, baseNGAccess);
    GarDelegateRequest googleArtifactDelegateRequest =
        ArtifactDelegateRequestUtils.getGoogleArtifactDelegateRequest(region, repositoryName, project, pkg, version,
            versionRegex, connector, encryptionDetails, ArtifactSourceType.GOOGLE_ARTIFACT_REGISTRY, MAXBUILDS);

    try {
      ArtifactTaskExecutionResponse artifactTaskExecutionResponse = executeSyncTask(googleArtifactDelegateRequest,
          ArtifactTaskType.GET_BUILDS, baseNGAccess, "Google Artifact Registry Get Builds task failure due to error");
      return getGarResponseDTO(artifactTaskExecutionResponse);
    } catch (DelegateServiceDriverException ex) {
      throw new HintException(
          String.format(HintException.DELEGATE_NOT_AVAILABLE, DocumentLinksConstants.DELEGATE_INSTALLATION_LINK),
          new DelegateNotAvailableException(ex.getCause().getMessage(), WingsException.USER));
    } catch (ExplanationException e) {
      throw new HintException(HintException.HINT_GCP_ACCESS_DENIED, new InvalidRequestException(e.getMessage(), USER));
    }
  }

  @Override
  public GARBuildDetailsDTO getLastSuccessfulBuild(IdentifierRef googleArtifactRegistryRef, String region,
      String repositoryName, String project, String pkg, GarRequestDTO garRequestDTO, String orgIdentifier,
      String projectIdentifier) {
    ArtifactUtils.validateIfAllValuesAssigned(MutablePair.of(NGArtifactConstants.REGION, region),
        MutablePair.of(NGArtifactConstants.REPOSITORY_NAME, repositoryName),
        MutablePair.of(NGArtifactConstants.PROJECT, project), MutablePair.of(NGArtifactConstants.PACKAGE, pkg));
    ArtifactUtils.validateIfAnyValueAssigned(MutablePair.of(NGArtifactConstants.VERSION, garRequestDTO.getVersion()),
        MutablePair.of(NGArtifactConstants.VERSION_REGEX, garRequestDTO.getVersionRegex()));

    GcpConnectorDTO connector = getConnector(googleArtifactRegistryRef);
    BaseNGAccess baseNGAccess =
        getBaseNGAccess(googleArtifactRegistryRef.getAccountIdentifier(), orgIdentifier, projectIdentifier);
    List<EncryptedDataDetail> encryptionDetails = getEncryptionDetails(connector, baseNGAccess);
    GarDelegateRequest googleArtifactDelegateRequest = ArtifactDelegateRequestUtils.getGoogleArtifactDelegateRequest(
        region, repositoryName, project, pkg, garRequestDTO.getVersion(), garRequestDTO.getVersionRegex(), connector,
        encryptionDetails, ArtifactSourceType.GOOGLE_ARTIFACT_REGISTRY, MAXBUILDS);

    try {
      ArtifactTaskExecutionResponse artifactTaskExecutionResponse =
          executeSyncTask(googleArtifactDelegateRequest, ArtifactTaskType.GET_LAST_SUCCESSFUL_BUILD, baseNGAccess,
              "Google Artifact Registry Get last successful build task failure due to error");
      GARResponseDTO garResponseDTO = getGarResponseDTO(artifactTaskExecutionResponse);
      if (garResponseDTO.getBuildDetailsList().size() != 1) {
        throw new ArtifactServerException("Google Artifact Registry get last successful build task failure.");
      }
      return garResponseDTO.getBuildDetailsList().get(0);
    } catch (DelegateServiceDriverException ex) {
      throw new HintException(
          String.format(HintException.DELEGATE_NOT_AVAILABLE, DocumentLinksConstants.DELEGATE_INSTALLATION_LINK),
          new DelegateNotAvailableException(ex.getCause().getMessage(), WingsException.USER));
    } catch (ExplanationException e) {
      throw new HintException(HintException.HINT_GCP_ACCESS_DENIED, new InvalidRequestException(e.getMessage(), USER));
    }
  }

  private GcpConnectorDTO getConnector(IdentifierRef GoogleArtifactRegistryRef) {
    Optional<ConnectorResponseDTO> connectorDTO = connectorService.get(GoogleArtifactRegistryRef.getAccountIdentifier(),
        GoogleArtifactRegistryRef.getOrgIdentifier(), GoogleArtifactRegistryRef.getProjectIdentifier(),
        GoogleArtifactRegistryRef.getIdentifier());

    if (!connectorDTO.isPresent() || !isAGoogleArtifactRegistryConnector(connectorDTO.get())) {
      throw new InvalidRequestException(
          String.format("Connector not found for identifier : [%s] with scope: [%s]",
              GoogleArtifactRegistryRef.getIdentifier(), GoogleArtifactRegistryRef.getScope()),
          WingsException.USER);
    }
    ConnectorInfoDTO connectors = connectorDTO.get().getConnector();
    return (GcpConnectorDTO) connectors.getConnectorConfig();
  }

  private boolean isAGoogleArtifactRegistryConnector(@Valid @NotNull ConnectorResponseDTO connectorResponseDTO) {
    return ConnectorType.GCP == (connectorResponseDTO.getConnector().getConnectorType());
  }

  private BaseNGAccess getBaseNGAccess(String accountId, String orgIdentifier, String projectIdentifier) {
    return BaseNGAccess.builder()
        .accountIdentifier(accountId)
        .orgIdentifier(orgIdentifier)
        .projectIdentifier(projectIdentifier)
        .build();
  }

  private List<EncryptedDataDetail> getEncryptionDetails(
      @Nonnull GcpConnectorDTO gcpConnectorDTO, @Nonnull NGAccess ngAccess) {
    if (gcpConnectorDTO.getCredential() != null && gcpConnectorDTO.getCredential().getConfig() != null) {
      return secretManagerClientService.getEncryptionDetails(ngAccess, gcpConnectorDTO.getCredential().getConfig());
    }
    return new ArrayList<>();
  }

  private ArtifactTaskExecutionResponse executeSyncTask(GarDelegateRequest googleArtifactDelegateRequest,
      ArtifactTaskType taskType, BaseNGAccess ngAccess, String ifFailedMessage) {
    DelegateResponseData responseData = getResponseData(ngAccess, googleArtifactDelegateRequest, taskType);
    return getTaskExecutionResponse(responseData, ifFailedMessage);
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
        throw new ArtifactServerException("Unexpected error during authentication to Google Artifact Registry server "
                + remoteMethodReturnValueData.getReturnValue(),
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

  private DelegateResponseData getResponseData(
      BaseNGAccess ngAccess, GarDelegateRequest delegateRequest, ArtifactTaskType artifactTaskType) {
    ArtifactTaskParameters artifactTaskParameters = ArtifactTaskParameters.builder()
                                                        .accountId(ngAccess.getAccountIdentifier())
                                                        .artifactTaskType(artifactTaskType)
                                                        .attributes(delegateRequest)
                                                        .build();
    Map<String, String> abstractions = ArtifactUtils.getTaskSetupAbstractions(ngAccess);
    final DelegateTaskRequest delegateTaskRequest =
        DelegateTaskRequest.builder()
            .accountId(ngAccess.getAccountIdentifier())
            .taskType(NGTaskType.GOOGLE_ARTIFACT_REGISTRY_TASK_NG.name())
            .taskParameters(artifactTaskParameters)
            .executionTimeout(java.time.Duration.ofSeconds(TIMEOUTINSEC))
            .taskSetupAbstractions(abstractions)
            .taskSelectors(delegateRequest.getGcpConnectorDTO().getDelegateSelectors())
            .build();
    try {
      return delegateGrpcClientWrapper.executeSyncTaskV2(delegateTaskRequest);
    } catch (DelegateServiceDriverException ex) {
      throw exceptionManager.processException(ex, WingsException.ExecutionContext.MANAGER, log);
    }
  }
  private GARResponseDTO getGarResponseDTO(ArtifactTaskExecutionResponse artifactTaskExecutionResponse) {
    List<GarDelegateResponse> garDelegateResponses =
        artifactTaskExecutionResponse.getArtifactDelegateResponses()
            .stream()
            .map(delegateResponse -> (GarDelegateResponse) delegateResponse)
            .collect(Collectors.toList());
    return toGarResponse(garDelegateResponses);
  }
}
