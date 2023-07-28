/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.artifact.resources.azureartifacts;
import static io.harness.connector.ConnectorModule.DEFAULT_CONNECTOR_SERVICE;
import static io.harness.exception.WingsException.USER;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.DelegateTaskRequest;
import io.harness.beans.IdentifierRef;
import io.harness.cdng.artifact.utils.ArtifactUtils;
import io.harness.common.NGTaskType;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.services.ConnectorService;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.delegate.beans.RemoteMethodReturnValueData;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.azureartifacts.AzureArtifactsAuthenticationDTO;
import io.harness.delegate.beans.connector.azureartifacts.AzureArtifactsAuthenticationType;
import io.harness.delegate.beans.connector.azureartifacts.AzureArtifactsConnectorDTO;
import io.harness.delegate.beans.connector.azureartifacts.AzureArtifactsCredentialsDTO;
import io.harness.delegate.beans.connector.azureartifacts.AzureArtifactsTokenDTO;
import io.harness.delegate.task.artifacts.ArtifactDelegateRequestUtils;
import io.harness.delegate.task.artifacts.ArtifactSourceType;
import io.harness.delegate.task.artifacts.ArtifactTaskType;
import io.harness.delegate.task.artifacts.azureartifacts.AzureArtifactsDelegateRequest;
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
import io.harness.exception.exceptionmanager.exceptionhandler.DocumentLinksConstants;
import io.harness.ng.core.BaseNGAccess;
import io.harness.ng.core.NGAccess;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.service.DelegateGrpcClientWrapper;

import software.wings.helpers.ext.azure.devops.AzureArtifactsFeed;
import software.wings.helpers.ext.azure.devops.AzureArtifactsPackage;
import software.wings.helpers.ext.azure.devops.AzureDevopsProject;
import software.wings.helpers.ext.jenkins.BuildDetails;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nonnull;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_ARTIFACTS})
public class AzureArtifactsResourceServiceImpl implements AzureArtifactsResourceService {
  private final ConnectorService connectorService;
  private final SecretManagerClientService secretManagerClientService;

  @Inject private DelegateGrpcClientWrapper delegateGrpcClientWrapper;

  @VisibleForTesting static final int timeoutInSecs = 90;

  @Inject
  public AzureArtifactsResourceServiceImpl(@Named(DEFAULT_CONNECTOR_SERVICE) ConnectorService connectorService,
      SecretManagerClientService secretManagerClientService) {
    this.connectorService = connectorService;

    this.secretManagerClientService = secretManagerClientService;
  }

  @Override
  public List<AzureArtifactsPackage> listAzureArtifactsPackages(IdentifierRef connectorRef, String accountId,
      String orgIdentifier, String projectIdentifier, String project, String feed, String packageType) {
    AzureArtifactsConnectorDTO azureArtifactsConnector = getConnector(connectorRef);

    BaseNGAccess baseNGAccess = getBaseNGAccess(connectorRef.getAccountIdentifier(), orgIdentifier, projectIdentifier);

    List<EncryptedDataDetail> encryptionDetails = getEncryptionDetails(azureArtifactsConnector, baseNGAccess);

    AzureArtifactsDelegateRequest azureArtifactsDelegateRequest =
        ArtifactDelegateRequestUtils.getAzureArtifactsDelegateRequest(null, packageType, null, null, project, null,
            feed, connectorRef.getIdentifier(), azureArtifactsConnector, encryptionDetails,
            ArtifactSourceType.AZURE_ARTIFACTS);

    ArtifactTaskExecutionResponse artifactTaskExecutionResponse =
        getResponse(azureArtifactsDelegateRequest, baseNGAccess, ArtifactTaskType.GET_AZURE_PACKAGES,
            "Azure Artifacts - List Packages task failure due to error.");

    if (artifactTaskExecutionResponse != null) {
      if (artifactTaskExecutionResponse.getAzureArtifactsPackages() == null) {
        return new ArrayList<>();
      }
    } else {
      return new ArrayList<>();
    }

    return artifactTaskExecutionResponse.getAzureArtifactsPackages();
  }

  @Override
  public List<BuildDetails> listVersionsOfAzureArtifactsPackage(IdentifierRef connectorRef, String accountId,
      String orgIdentifier, String projectIdentifier, String project, String feed, String packageType,
      String packageName, String versionRegex) {
    AzureArtifactsConnectorDTO azureArtifactsConnector = getConnector(connectorRef);

    BaseNGAccess baseNGAccess = getBaseNGAccess(connectorRef.getAccountIdentifier(), orgIdentifier, projectIdentifier);

    List<EncryptedDataDetail> encryptionDetails = getEncryptionDetails(azureArtifactsConnector, baseNGAccess);

    AzureArtifactsDelegateRequest azureArtifactsDelegateRequest =
        ArtifactDelegateRequestUtils.getAzureArtifactsDelegateRequest(packageName, packageType, null, versionRegex,
            project, null, feed, connectorRef.getIdentifier(), azureArtifactsConnector, encryptionDetails,
            ArtifactSourceType.AZURE_ARTIFACTS);

    ArtifactTaskExecutionResponse artifactTaskExecutionResponse = getResponse(azureArtifactsDelegateRequest,
        baseNGAccess, ArtifactTaskType.GET_BUILDS, "Azure Artifacts - Get Builds task failure due to error.");

    if (artifactTaskExecutionResponse != null) {
      if (artifactTaskExecutionResponse.getBuildDetails() == null) {
        return new ArrayList<>();
      }
    } else {
      return new ArrayList<>();
    }

    return artifactTaskExecutionResponse.getBuildDetails();
  }

  @Override
  public BuildDetails getLastSuccessfulVersion(IdentifierRef connectorRef, String accountId, String orgIdentifier,
      String projectIdentifier, String project, String feed, String packageType, String packageName, String version,
      String versionRegex) {
    AzureArtifactsConnectorDTO azureArtifactsConnector = getConnector(connectorRef);

    BaseNGAccess baseNGAccess = getBaseNGAccess(connectorRef.getAccountIdentifier(), orgIdentifier, projectIdentifier);

    List<EncryptedDataDetail> encryptionDetails = getEncryptionDetails(azureArtifactsConnector, baseNGAccess);

    AzureArtifactsDelegateRequest azureArtifactsDelegateRequest =
        ArtifactDelegateRequestUtils.getAzureArtifactsDelegateRequest(packageName, packageType, version, versionRegex,
            project, null, feed, connectorRef.getIdentifier(), azureArtifactsConnector, encryptionDetails,
            ArtifactSourceType.AZURE_ARTIFACTS);

    ArtifactTaskExecutionResponse artifactTaskExecutionResponse =
        getResponse(azureArtifactsDelegateRequest, baseNGAccess, ArtifactTaskType.GET_LAST_SUCCESSFUL_BUILD,
            "Azure Artifacts - Get Last Successful Build task failure due to error.");

    if (artifactTaskExecutionResponse != null) {
      if (artifactTaskExecutionResponse.getBuildDetails() == null) {
        return null;
      }
    } else {
      return null;
    }

    return artifactTaskExecutionResponse.getBuildDetails().get(0);
  }

  @Override
  public List<AzureDevopsProject> listAzureArtifactsProjects(
      IdentifierRef connectorRef, String accountId, String orgIdentifier, String projectIdentifier) {
    AzureArtifactsConnectorDTO azureArtifactsConnector = getConnector(connectorRef);

    BaseNGAccess baseNGAccess = getBaseNGAccess(connectorRef.getAccountIdentifier(), orgIdentifier, projectIdentifier);

    List<EncryptedDataDetail> encryptionDetails = getEncryptionDetails(azureArtifactsConnector, baseNGAccess);

    AzureArtifactsDelegateRequest azureArtifactsDelegateRequest =
        ArtifactDelegateRequestUtils.getAzureArtifactsDelegateRequest(null, null, null, null, null, null, null,
            connectorRef.getIdentifier(), azureArtifactsConnector, encryptionDetails,
            ArtifactSourceType.AZURE_ARTIFACTS);

    ArtifactTaskExecutionResponse artifactTaskExecutionResponse =
        getResponse(azureArtifactsDelegateRequest, baseNGAccess, ArtifactTaskType.GET_AZURE_PROJECTS,
            "Azure Artifacts - List Projects task failure due to error.");

    if (artifactTaskExecutionResponse != null) {
      if (artifactTaskExecutionResponse.getAzureArtifactsProjects() == null) {
        return new ArrayList<>();
      }
    } else {
      return new ArrayList<>();
    }

    return artifactTaskExecutionResponse.getAzureArtifactsProjects();
  }

  @Override
  public List<AzureArtifactsFeed> listAzureArtifactsFeeds(
      IdentifierRef connectorRef, String accountId, String orgIdentifier, String projectIdentifier, String project) {
    AzureArtifactsConnectorDTO azureArtifactsConnector = getConnector(connectorRef);

    BaseNGAccess baseNGAccess = getBaseNGAccess(connectorRef.getAccountIdentifier(), orgIdentifier, projectIdentifier);

    List<EncryptedDataDetail> encryptionDetails = getEncryptionDetails(azureArtifactsConnector, baseNGAccess);

    AzureArtifactsDelegateRequest azureArtifactsDelegateRequest =
        ArtifactDelegateRequestUtils.getAzureArtifactsDelegateRequest(null, null, null, null, project, null, null,
            connectorRef.getIdentifier(), azureArtifactsConnector, encryptionDetails,
            ArtifactSourceType.AZURE_ARTIFACTS);

    ArtifactTaskExecutionResponse artifactTaskExecutionResponse = getResponse(azureArtifactsDelegateRequest,
        baseNGAccess, ArtifactTaskType.GET_AZURE_FEEDS, "Azure Artifacts - List Feeds task failure due to error.");

    if (artifactTaskExecutionResponse != null) {
      if (artifactTaskExecutionResponse.getAzureArtifactsFeeds() == null) {
        return new ArrayList<>();
      }
    } else {
      return new ArrayList<>();
    }

    return artifactTaskExecutionResponse.getAzureArtifactsFeeds();
  }

  private ArtifactTaskExecutionResponse getResponse(AzureArtifactsDelegateRequest request, BaseNGAccess baseNGAccess,
      ArtifactTaskType taskType, String errorMessage) {
    try {
      return executeSyncTask(request, taskType, baseNGAccess, errorMessage);
    } catch (DelegateServiceDriverException ex) {
      throw new HintException(
          String.format(HintException.DELEGATE_NOT_AVAILABLE, DocumentLinksConstants.DELEGATE_INSTALLATION_LINK),
          new DelegateNotAvailableException(ex.getCause().getMessage(), WingsException.USER));

    } catch (ExplanationException e) {
      throw new HintException(
          HintException.HINT_AZURE_ACCESS_DENIED, new InvalidRequestException(e.getMessage(), USER));
    }
  }

  private AzureArtifactsConnectorDTO getConnector(IdentifierRef connectorRef) {
    Optional<ConnectorResponseDTO> connectorDTO = connectorService.get(connectorRef.getAccountIdentifier(),
        connectorRef.getOrgIdentifier(), connectorRef.getProjectIdentifier(), connectorRef.getIdentifier());

    if (!connectorDTO.isPresent() || !isAnAzureArtifactsConnector(connectorDTO.get())) {
      throw new InvalidRequestException(String.format("Connector not found for identifier : [%s] with scope: [%s]",
                                            connectorRef.getIdentifier(), connectorRef.getScope()),
          WingsException.USER);
    }

    ConnectorInfoDTO connectors = connectorDTO.get().getConnector();

    return (AzureArtifactsConnectorDTO) connectors.getConnectorConfig();
  }

  private boolean isAnAzureArtifactsConnector(@Valid @NotNull ConnectorResponseDTO connectorResponseDTO) {
    return ConnectorType.AZURE_ARTIFACTS == (connectorResponseDTO.getConnector().getConnectorType());
  }

  private BaseNGAccess getBaseNGAccess(String accountId, String orgIdentifier, String projectIdentifier) {
    return BaseNGAccess.builder()
        .accountIdentifier(accountId)
        .orgIdentifier(orgIdentifier)
        .projectIdentifier(projectIdentifier)
        .build();
  }

  private List<EncryptedDataDetail> getEncryptionDetails(
      @Nonnull AzureArtifactsConnectorDTO azureArtifactsConnectorDTO, @Nonnull NGAccess ngAccess) {
    List<EncryptedDataDetail> encryptedDataDetails = new ArrayList<>();

    if (azureArtifactsConnectorDTO != null) {
      if (azureArtifactsConnectorDTO.getAuth() != null
          && azureArtifactsConnectorDTO.getAuth().getCredentials() != null) {
        AzureArtifactsAuthenticationDTO azureArtifactsAuthenticationDTO = azureArtifactsConnectorDTO.getAuth();

        AzureArtifactsCredentialsDTO azureArtifactsCredentialsDTO = azureArtifactsAuthenticationDTO.getCredentials();

        if (azureArtifactsCredentialsDTO.getType() == AzureArtifactsAuthenticationType.PERSONAL_ACCESS_TOKEN) {
          AzureArtifactsTokenDTO azureArtifactsTokenDTO = azureArtifactsCredentialsDTO.getCredentialsSpec();

          encryptedDataDetails = secretManagerClientService.getEncryptionDetails(ngAccess, azureArtifactsTokenDTO);
        } else {
          throw new InvalidRequestException("Please authenticate with the personal access token");
        }
      }
    }

    return encryptedDataDetails;
  }

  private ArtifactTaskExecutionResponse executeSyncTask(AzureArtifactsDelegateRequest azureArtifactsDelegateRequest,
      ArtifactTaskType taskType, BaseNGAccess ngAccess, String ifFailedMessage) {
    DelegateResponseData responseData = getResponseData(ngAccess, azureArtifactsDelegateRequest, taskType);

    return getTaskExecutionResponse(responseData, ifFailedMessage);
  }

  private DelegateResponseData getResponseData(BaseNGAccess ngAccess,
      AzureArtifactsDelegateRequest azureArtifactsDelegateRequest, ArtifactTaskType artifactTaskType) {
    ArtifactTaskParameters artifactTaskParameters = ArtifactTaskParameters.builder()
                                                        .accountId(ngAccess.getAccountIdentifier())
                                                        .artifactTaskType(artifactTaskType)
                                                        .attributes(azureArtifactsDelegateRequest)
                                                        .build();

    Map<String, String> abstractions = ArtifactUtils.getTaskSetupAbstractions(ngAccess);

    final DelegateTaskRequest delegateTaskRequest =
        DelegateTaskRequest.builder()
            .accountId(ngAccess.getAccountIdentifier())
            .taskType(NGTaskType.AZURE_ARTIFACT_TASK_NG.name())
            .taskParameters(artifactTaskParameters)
            .executionTimeout(java.time.Duration.ofSeconds(timeoutInSecs))
            .taskSetupAbstractions(abstractions)
            .taskSelectors(azureArtifactsDelegateRequest.getAzureArtifactsConnectorDTO().getDelegateSelectors())
            .build();

    return delegateGrpcClientWrapper.executeSyncTaskV2(delegateTaskRequest);
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
        throw new ArtifactServerException("Unexpected error during authentication to Azure Artifacts Server "
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
}
