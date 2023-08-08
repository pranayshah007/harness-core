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

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.DelegateTaskRequest;
import io.harness.beans.IdentifierRef;
import io.harness.cdng.artifact.NGArtifactConstants;
import io.harness.cdng.artifact.resources.githubpackages.dtos.GithubPackagesResponseDTO;
import io.harness.cdng.artifact.resources.githubpackages.mappers.GithubPackagesResourceMapper;
import io.harness.cdng.artifact.utils.ArtifactUtils;
import io.harness.common.NGTaskType;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.services.ConnectorService;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.delegate.beans.RemoteMethodReturnValueData;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.scm.github.GithubApiAccessDTO;
import io.harness.delegate.beans.connector.scm.github.GithubApiAccessType;
import io.harness.delegate.beans.connector.scm.github.GithubConnectorDTO;
import io.harness.delegate.beans.connector.scm.github.GithubTokenSpecDTO;
import io.harness.delegate.task.artifacts.ArtifactDelegateRequestUtils;
import io.harness.delegate.task.artifacts.ArtifactSourceType;
import io.harness.delegate.task.artifacts.ArtifactTaskType;
import io.harness.delegate.task.artifacts.githubpackages.GithubPackagesArtifactDelegateRequest;
import io.harness.delegate.task.artifacts.githubpackages.GithubPackagesArtifactDelegateResponse;
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

import software.wings.helpers.ext.jenkins.BuildDetails;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.ArrayList;
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
@Slf4j
public class GithubPackagesResourceServiceImpl implements GithubPackagesResourceService {
  private final ConnectorService connectorService;
  private final SecretManagerClientService secretManagerClientService;
  @Inject private DelegateGrpcClientWrapper delegateGrpcClientWrapper;
  @Inject ExceptionManager exceptionManager;
  @VisibleForTesting static final int timeoutInSecs = 90;

  @Inject
  public GithubPackagesResourceServiceImpl(@Named(DEFAULT_CONNECTOR_SERVICE) ConnectorService connectorService,
      SecretManagerClientService secretManagerClientService) {
    this.connectorService = connectorService;
    this.secretManagerClientService = secretManagerClientService;
  }

  @Override
  public GithubPackagesResponseDTO getPackageDetails(IdentifierRef connectorRef, String accountId, String orgIdentifier,
      String projectIdentifier, String packageType, String org) {
    GithubConnectorDTO githubConnector = getConnector(connectorRef);

    BaseNGAccess baseNGAccess = getBaseNGAccess(connectorRef.getAccountIdentifier(), orgIdentifier, projectIdentifier);

    List<EncryptedDataDetail> encryptionDetails = getEncryptionDetails(githubConnector, baseNGAccess);

    GithubPackagesArtifactDelegateRequest githubPackagesArtifactDelegateRequest =
        ArtifactDelegateRequestUtils.getGithubPackagesDelegateRequest(null, packageType, null, null, org,
            connectorRef.getIdentifier(), githubConnector, encryptionDetails, ArtifactSourceType.GITHUB_PACKAGES);

    try {
      ArtifactTaskExecutionResponse artifactTaskExecutionResponse =
          executeSyncTask(githubPackagesArtifactDelegateRequest, ArtifactTaskType.GET_GITHUB_PACKAGES, baseNGAccess,
              "Github Packages Get Packages task failure due to error");

      return getGithubPackagesResponseDTO(artifactTaskExecutionResponse);

    } catch (DelegateServiceDriverException ex) {
      throw new HintException(
          String.format(HintException.DELEGATE_NOT_AVAILABLE, DocumentLinksConstants.DELEGATE_INSTALLATION_LINK),
          new DelegateNotAvailableException(ex.getCause().getMessage(), WingsException.USER));

    } catch (ExplanationException e) {
      throw new HintException(
          HintException.HINT_GITHUB_ACCESS_DENIED, new InvalidRequestException(e.getMessage(), USER));
    }
  }

  @Override
  public List<BuildDetails> getVersionsOfPackage(IdentifierRef connectorRef, String packageName, String packageType,
      String versionRegex, String org, String accountId, String orgIdentifier, String projectIdentifier) {
    if (EmptyPredicate.isEmpty(versionRegex)) {
      return new ArrayList<>();
    }

    GithubConnectorDTO githubConnector = getConnector(connectorRef);

    BaseNGAccess baseNGAccess = getBaseNGAccess(connectorRef.getAccountIdentifier(), orgIdentifier, projectIdentifier);

    List<EncryptedDataDetail> encryptionDetails = getEncryptionDetails(githubConnector, baseNGAccess);

    GithubPackagesArtifactDelegateRequest githubPackagesArtifactDelegateRequest =
        ArtifactDelegateRequestUtils.getGithubPackagesDelegateRequest(packageName, packageType, null, versionRegex, org,
            connectorRef.getIdentifier(), githubConnector, encryptionDetails, ArtifactSourceType.GITHUB_PACKAGES);

    try {
      ArtifactTaskExecutionResponse artifactTaskExecutionResponse =
          executeSyncTask(githubPackagesArtifactDelegateRequest, ArtifactTaskType.GET_BUILDS, baseNGAccess,
              "Github Packages Get Builds task failure due to error");

      return artifactTaskExecutionResponse.getBuildDetails();

    } catch (DelegateServiceDriverException ex) {
      throw new HintException(
          String.format(HintException.DELEGATE_NOT_AVAILABLE, DocumentLinksConstants.DELEGATE_INSTALLATION_LINK),
          new DelegateNotAvailableException(ex.getCause().getMessage(), WingsException.USER));

    } catch (ExplanationException e) {
      throw new HintException(
          HintException.HINT_GITHUB_ACCESS_DENIED, new InvalidRequestException(e.getMessage(), USER));
    }
  }

  @Override
  public BuildDetails getLastSuccessfulVersion(IdentifierRef connectorRef, String packageName, String packageType,
      String version, String versionRegex, String org, String accountId, String orgIdentifier,
      String projectIdentifier) {
    if (EmptyPredicate.isEmpty(versionRegex) && EmptyPredicate.isEmpty(version)) {
      versionRegex = "*";
    }
    ArtifactUtils.validateIfAllValuesAssigned(MutablePair.of(NGArtifactConstants.PACKAGE_NAME, packageName),
        MutablePair.of(NGArtifactConstants.PACKAGE_TYPE, packageType));

    GithubConnectorDTO githubConnector = getConnector(connectorRef);

    BaseNGAccess baseNGAccess = getBaseNGAccess(connectorRef.getAccountIdentifier(), orgIdentifier, projectIdentifier);

    List<EncryptedDataDetail> encryptionDetails = getEncryptionDetails(githubConnector, baseNGAccess);

    GithubPackagesArtifactDelegateRequest githubPackagesArtifactDelegateRequest =
        ArtifactDelegateRequestUtils.getGithubPackagesDelegateRequest(packageName, packageType, version, versionRegex,
            org, connectorRef.getIdentifier(), githubConnector, encryptionDetails, ArtifactSourceType.GITHUB_PACKAGES);

    try {
      ArtifactTaskExecutionResponse artifactTaskExecutionResponse =
          executeSyncTask(githubPackagesArtifactDelegateRequest, ArtifactTaskType.GET_LAST_SUCCESSFUL_BUILD,
              baseNGAccess, "Github Packages Get Last Successful Build task failure due to error");

      return artifactTaskExecutionResponse.getBuildDetails().get(0);

    } catch (DelegateServiceDriverException ex) {
      throw new HintException(
          String.format(HintException.DELEGATE_NOT_AVAILABLE, DocumentLinksConstants.DELEGATE_INSTALLATION_LINK),
          new DelegateNotAvailableException(ex.getCause().getMessage(), WingsException.USER));

    } catch (ExplanationException e) {
      throw new HintException(
          HintException.HINT_GITHUB_ACCESS_DENIED, new InvalidRequestException(e.getMessage(), USER));
    }
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

    Map<String, String> abstractions = ArtifactUtils.getTaskSetupAbstractions(ngAccess);

    final DelegateTaskRequest delegateTaskRequest =
        DelegateTaskRequest.builder()
            .accountId(ngAccess.getAccountIdentifier())
            .taskType(NGTaskType.GITHUB_PACKAGES_TASK_NG.name())
            .taskParameters(artifactTaskParameters)
            .executionTimeout(java.time.Duration.ofSeconds(timeoutInSecs))
            .taskSetupAbstractions(abstractions)
            .taskSelectors(delegateRequest.getGithubConnectorDTO().getDelegateSelectors())
            .build();
    try {
      return delegateGrpcClientWrapper.executeSyncTaskV2(delegateTaskRequest);
    } catch (DelegateServiceDriverException ex) {
      throw exceptionManager.processException(ex, WingsException.ExecutionContext.MANAGER, log);
    }
  }

  @VisibleForTesting
  protected ArtifactTaskExecutionResponse getTaskExecutionResponse(
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

  private GithubPackagesResponseDTO getGithubPackagesResponseDTO(
      ArtifactTaskExecutionResponse artifactTaskExecutionResponse) {
    List<GithubPackagesArtifactDelegateResponse> githubPackagesArtifactDelegateResponses =
        artifactTaskExecutionResponse.getArtifactDelegateResponses()
            .stream()
            .map(delegateResponse -> (GithubPackagesArtifactDelegateResponse) delegateResponse)
            .collect(Collectors.toList());

    return GithubPackagesResourceMapper.toPackagesResponse(githubPackagesArtifactDelegateResponses);
  }

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

  @VisibleForTesting
  public boolean isAGithubConnector(@Valid @NotNull ConnectorResponseDTO connectorResponseDTO) {
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
    List<EncryptedDataDetail> encryptedDataDetails = new ArrayList<>();

    if (githubConnectorDTO.getApiAccess() != null) {
      encryptedDataDetails = getGithubEncryptionDetails(githubConnectorDTO, ngAccess);
    } else {
      throw new InvalidRequestException("Please enable API Access for the Github Connector");
    }

    return encryptedDataDetails;
  }

  private List<EncryptedDataDetail> getGithubEncryptionDetails(
      GithubConnectorDTO githubConnectorDTO, NGAccess ngAccess) {
    List<EncryptedDataDetail> encryptedDataDetails = new ArrayList<>();

    GithubApiAccessDTO githubApiAccessDTO = githubConnectorDTO.getApiAccess();

    GithubApiAccessType type = githubApiAccessDTO.getType();

    if (type == GithubApiAccessType.TOKEN) {
      GithubTokenSpecDTO githubTokenSpecDTO = (GithubTokenSpecDTO) githubApiAccessDTO.getSpec();

      encryptedDataDetails = secretManagerClientService.getEncryptionDetails(ngAccess, githubTokenSpecDTO);

    } else {
      throw new InvalidRequestException("Please select the authentication type for API Access as Token");
    }

    return encryptedDataDetails;
  }
}
