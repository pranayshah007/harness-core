/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.artifact.resources.githubpackages.service;

import static io.harness.connector.ConnectorModule.DEFAULT_CONNECTOR_SERVICE;
import static io.harness.exception.WingsException.USER;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.utils.DelegateOwner.getNGTaskSetupAbstractionsWithOwner;

import io.harness.beans.DelegateTaskRequest;
import io.harness.beans.IdentifierRef;
import io.harness.cdng.artifact.resources.githubpackages.dtos.GithubPackagesResponseDTO;
import io.harness.common.NGTaskType;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.services.ConnectorService;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.delegate.beans.RemoteMethodReturnValueData;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.scm.github.GithubConnectorDTO;
import io.harness.delegate.task.artifacts.ArtifactDelegateRequestUtils;
import io.harness.delegate.task.artifacts.ArtifactSourceType;
import io.harness.delegate.task.artifacts.ArtifactTaskType;
import io.harness.delegate.task.artifacts.githubpackages.GithubPackagesArtifactDelegateRequest;
import io.harness.delegate.task.artifacts.request.ArtifactTaskParameters;
import io.harness.delegate.task.artifacts.response.ArtifactTaskExecutionResponse;
import io.harness.delegate.task.artifacts.response.ArtifactTaskResponse;
import io.harness.exception.*;
import io.harness.exception.exceptionmanager.exceptionhandler.DocumentLinksConstants;
import io.harness.ng.core.BaseNGAccess;
import io.harness.ng.core.NGAccess;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.service.DelegateGrpcClientWrapper;

import software.wings.helpers.ext.jenkins.BuildDetails;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.*;
import javax.annotation.Nonnull;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;

public class GithubPackagesResourceServiceImpl implements GithubPackagesResourceService {
  private final ConnectorService connectorService;
  private final SecretManagerClientService secretManagerClientService;
  @Inject private DelegateGrpcClientWrapper delegateGrpcClientWrapper;
  @VisibleForTesting static final int timeoutInSecs = 30;

  @Inject
  public GithubPackagesResourceServiceImpl(@Named(DEFAULT_CONNECTOR_SERVICE) ConnectorService connectorService,
      SecretManagerClientService secretManagerClientService) {
    this.connectorService = connectorService;
    this.secretManagerClientService = secretManagerClientService;
  }

  @Override
  public GithubPackagesResponseDTO getPackageDetails(
      IdentifierRef connectorRef, String accountId, String orgIdentifier, String projectIdentifier) {
    return null;
  }

  @Override
  public GithubPackagesResponseDTO getVersionsOfPackage(IdentifierRef connectorRef, String packageName,
      String versionRegex, String accountId, String orgIdentifier, String projectIdentifier) {
    if (EmptyPredicate.isEmpty(versionRegex)) {
      return null;
    }

    GithubConnectorDTO githubConnector = getConnector(connectorRef);

    BaseNGAccess baseNGAccess = getBaseNGAccess(connectorRef.getAccountIdentifier(), orgIdentifier, projectIdentifier);

    List<EncryptedDataDetail> encryptionDetails = getEncryptionDetails(githubConnector, baseNGAccess);

    GithubPackagesArtifactDelegateRequest githubPackagesArtifactDelegateRequest =
        ArtifactDelegateRequestUtils.getGithubPackagesDelegateRequest(packageName, null, versionRegex, null,
            githubConnector, encryptionDetails, ArtifactSourceType.GITHUB_PACKAGES);

    try {
      ArtifactTaskExecutionResponse artifactTaskExecutionResponse =
          executeSyncTask(githubPackagesArtifactDelegateRequest, ArtifactTaskType.GET_BUILDS, baseNGAccess,
              "Github Packages Get Builds task failure due to error");

      // return

    } catch (DelegateServiceDriverException ex) {
      throw new HintException(
          String.format(HintException.DELEGATE_NOT_AVAILABLE, DocumentLinksConstants.DELEGATE_INSTALLATION_LINK),
          new DelegateNotAvailableException(ex.getCause().getMessage(), WingsException.USER));
    } catch (ExplanationException e) {
      throw new HintException(
          HintException.HINT_DOCKER_HUB_ACCESS_DENIED, new InvalidRequestException(e.getMessage(), USER));
    }
    return null;
  }

  private ArtifactTaskExecutionResponse executeSyncTask(
      GithubPackagesArtifactDelegateRequest githubPackagesArtifactDelegateRequest, ArtifactTaskType taskType,
      BaseNGAccess ngAccess, String ifFailedMessage) {
    DelegateResponseData responseData = getResponseData(ngAccess, githubPackagesArtifactDelegateRequest, taskType);
    return getTaskExecutionResponse(responseData, ifFailedMessage);
  }

  private DelegateResponseData getResponseData(
      BaseNGAccess ngAccess, GithubPackagesArtifactDelegateRequest delegateRequest, ArtifactTaskType artifactTaskType) {
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
            .taskType(NGTaskType.GITHUB_PACKAGES_TASK_NG.name())
            .taskParameters(artifactTaskParameters)
            .executionTimeout(java.time.Duration.ofSeconds(timeoutInSecs))
            .taskSetupAbstractions(abstractions)
            .taskSelectors(delegateRequest.getGithubConnectorDTO().getDelegateSelectors())
            .build();

    return delegateGrpcClientWrapper.executeSyncTask(delegateTaskRequest);
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
            "Unexpected error during authentication to Github server " + remoteMethodReturnValueData.getReturnValue(),
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

  private void getGithubPackagesResponseDTO(ArtifactTaskExecutionResponse artifactTaskExecutionResponse) {}

  private GithubConnectorDTO getConnector(IdentifierRef connectorRef) {
    Optional<ConnectorResponseDTO> connectorDTO = connectorService.get(connectorRef.getAccountIdentifier(),
        connectorRef.getOrgIdentifier(), connectorRef.getProjectIdentifier(), connectorRef.getIdentifier());

    if (!connectorDTO.isPresent() || !isAGithubConnector(connectorDTO.get())) {
      throw new InvalidRequestException(String.format("Connector not found for identifier : [%s] with scope: [%s]",
                                            connectorRef.getIdentifier(), connectorRef.getScope()),
          WingsException.USER);
    }
    ConnectorInfoDTO connectors = connectorDTO.get().getConnector();
    return (GithubConnectorDTO) connectors.getConnectorConfig();
  }

  private boolean isAGithubConnector(@Valid @NotNull ConnectorResponseDTO connectorResponseDTO) {
    return ConnectorType.GITHUB == (connectorResponseDTO.getConnector().getConnectorType());
  }

  private BaseNGAccess getBaseNGAccess(String accountId, String orgIdentifier, String projectIdentifier) {
    return BaseNGAccess.builder()
        .accountIdentifier(accountId)
        .orgIdentifier(orgIdentifier)
        .projectIdentifier(projectIdentifier)
        .build();
  }

  private List<EncryptedDataDetail> getEncryptionDetails(
      @Nonnull GithubConnectorDTO githubConnectorDTO, @Nonnull NGAccess ngAccess) {
    if (githubConnectorDTO.getAuthentication() != null
        && githubConnectorDTO.getAuthentication().getCredentials() != null) {
      return secretManagerClientService.getEncryptionDetails(
          ngAccess, githubConnectorDTO.getAuthentication().getCredentials());
    }
    return new ArrayList<>();
  }
}
