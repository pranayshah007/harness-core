/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.artifact.resources.bamboo;

import static io.harness.connector.ConnectorModule.DEFAULT_CONNECTOR_SERVICE;
import static io.harness.exception.WingsException.USER;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;

import io.harness.beans.DelegateTaskRequest;
import io.harness.beans.DelegateTaskRequest.DelegateTaskRequestBuilder;
import io.harness.beans.IdentifierRef;
import io.harness.cdng.artifact.resources.bamboo.dtos.BambooPlanKeysDTO;
import io.harness.cdng.artifact.resources.bamboo.mappers.BambooResourceMapper;
import io.harness.common.NGTaskType;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.services.ConnectorService;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.delegate.beans.RemoteMethodReturnValueData;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.bamboo.BambooConnectorDTO;
import io.harness.delegate.task.artifacts.ArtifactDelegateRequestUtils;
import io.harness.delegate.task.artifacts.ArtifactSourceType;
import io.harness.delegate.task.artifacts.ArtifactTaskType;
import io.harness.delegate.task.artifacts.bamboo.BambooArtifactDelegateRequest;
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
import java.util.Optional;
import javax.annotation.Nonnull;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BambooResourceServiceImpl implements BambooResourceService {
  private final ConnectorService connectorService;
  private final SecretManagerClientService secretManagerClientService;
  @Inject private DelegateGrpcClientWrapper delegateGrpcClientWrapper;
  @Inject ExceptionManager exceptionManager;
  @VisibleForTesting static final int TIME_OUT_IN_SECS = 30;
  @VisibleForTesting static final String TASK_SETUP_ABSTRACTION = "owner";

  @Inject
  public BambooResourceServiceImpl(@Named(DEFAULT_CONNECTOR_SERVICE) ConnectorService connectorService,
      SecretManagerClientService secretManagerClientService) {
    this.connectorService = connectorService;
    this.secretManagerClientService = secretManagerClientService;
  }

  @Override
  public BambooPlanKeysDTO getPlanName(
      IdentifierRef bambooConnectorRef, String orgIdentifier, String projectIdentifier) {
    BambooConnectorDTO connector = getConnector(bambooConnectorRef);
    BaseNGAccess baseNGAccess =
        getBaseNGAccess(bambooConnectorRef.getAccountIdentifier(), orgIdentifier, projectIdentifier);
    List<EncryptedDataDetail> encryptionDetails = getEncryptionDetails(connector, baseNGAccess);
    BambooArtifactDelegateRequest bambooRequest =
        ArtifactDelegateRequestUtils.getBambooDelegateArtifactRequest(bambooConnectorRef.getIdentifier(), connector,
            encryptionDetails, ArtifactSourceType.BAMBOO, null, null, null, null);
    try {
      ArtifactTaskExecutionResponse artifactTaskExecutionResponse = executeSyncTask(
          bambooRequest, ArtifactTaskType.GET_PLANS, baseNGAccess, "Bamboo Get Plans task failure due to error");
      return BambooResourceMapper.toBambooJobDetailsDTO(artifactTaskExecutionResponse);
    } catch (DelegateServiceDriverException ex) {
      log.info(ex.getMessage());
      throw new HintException(
          String.format(HintException.DELEGATE_NOT_AVAILABLE, DocumentLinksConstants.DELEGATE_INSTALLATION_LINK),
          new DelegateNotAvailableException(ex.getCause().getMessage(), WingsException.USER));
    } catch (ExplanationException e) {
      log.info(e.getMessage());
      throw new HintException(
          HintException.HINT_BAMBOO_ACCESS_DENIED, new InvalidRequestException(e.getMessage(), USER));
    }
  }

  @Override
  public List<String> getArtifactPath(
      IdentifierRef bambooConnectorRef, String orgIdentifier, String projectIdentifier, String planName) {
    BambooConnectorDTO connector = getConnector(bambooConnectorRef);
    BaseNGAccess baseNGAccess =
        getBaseNGAccess(bambooConnectorRef.getAccountIdentifier(), orgIdentifier, projectIdentifier);
    List<EncryptedDataDetail> encryptionDetails = getEncryptionDetails(connector, baseNGAccess);
    BambooArtifactDelegateRequest bambooRequest =
        ArtifactDelegateRequestUtils.getBambooDelegateArtifactRequest(bambooConnectorRef.getIdentifier(), connector,
            encryptionDetails, ArtifactSourceType.BAMBOO, planName, null, null, null);
    try {
      ArtifactTaskExecutionResponse artifactTaskExecutionResponse = executeSyncTask(bambooRequest,
          ArtifactTaskType.GET_ARTIFACT_PATH, baseNGAccess, "Bamboo Get Artifact Paths task failure due to error");
      return artifactTaskExecutionResponse.getArtifactPath();
    } catch (DelegateServiceDriverException ex) {
      log.info(ex.getMessage());
      throw new HintException(
          String.format(HintException.DELEGATE_NOT_AVAILABLE, DocumentLinksConstants.DELEGATE_INSTALLATION_LINK),
          new DelegateNotAvailableException(ex.getCause().getMessage(), WingsException.USER));
    } catch (ExplanationException e) {
      log.info(e.getMessage());
      throw new HintException(
          HintException.HINT_BAMBOO_ACCESS_DENIED, new InvalidRequestException(e.getMessage(), USER));
    }
  }

  @Override
  public List<BuildDetails> getBuilds(IdentifierRef bambooConnectorRef, String orgIdentifier, String projectIdentifier,
      String planName, List<String> artifactPath) {
    BambooConnectorDTO connector = getConnector(bambooConnectorRef);
    BaseNGAccess baseNGAccess =
        getBaseNGAccess(bambooConnectorRef.getAccountIdentifier(), orgIdentifier, projectIdentifier);
    List<EncryptedDataDetail> encryptionDetails = getEncryptionDetails(connector, baseNGAccess);
    BambooArtifactDelegateRequest bambooRequest =
        ArtifactDelegateRequestUtils.getBambooDelegateArtifactRequest(bambooConnectorRef.getIdentifier(), connector,
            encryptionDetails, ArtifactSourceType.BAMBOO, planName, artifactPath, null, null);
    try {
      ArtifactTaskExecutionResponse artifactTaskExecutionResponse = executeSyncTask(bambooRequest,
          ArtifactTaskType.GET_BUILDS, baseNGAccess, "Bamboo Get Artifact Paths task failure due to error");
      return artifactTaskExecutionResponse.getBuildDetails();
    } catch (DelegateServiceDriverException ex) {
      log.info(ex.getMessage());
      throw new HintException(
          String.format(HintException.DELEGATE_NOT_AVAILABLE, DocumentLinksConstants.DELEGATE_INSTALLATION_LINK),
          new DelegateNotAvailableException(ex.getCause().getMessage(), WingsException.USER));
    } catch (ExplanationException e) {
      log.info(e.getMessage());
      throw new HintException(
          HintException.HINT_BAMBOO_ACCESS_DENIED, new InvalidRequestException(e.getMessage(), USER));
    }
  }

  private List<EncryptedDataDetail> getEncryptionDetails(
      @Nonnull BambooConnectorDTO bambooConnectorDTO, @Nonnull NGAccess ngAccess) {
    if (bambooConnectorDTO.getAuth() != null && bambooConnectorDTO.getAuth().getCredentials() != null) {
      return secretManagerClientService.getEncryptionDetails(ngAccess, bambooConnectorDTO.getAuth().getCredentials());
    }
    return new ArrayList<>();
  }

  private BaseNGAccess getBaseNGAccess(String accountId, String orgIdentifier, String projectIdentifier) {
    return BaseNGAccess.builder()
        .accountIdentifier(accountId)
        .orgIdentifier(orgIdentifier)
        .projectIdentifier(projectIdentifier)
        .build();
  }

  private BambooConnectorDTO getConnector(IdentifierRef bambooConnectorRef) {
    Optional<ConnectorResponseDTO> connectorDTO =
        connectorService.get(bambooConnectorRef.getAccountIdentifier(), bambooConnectorRef.getOrgIdentifier(),
            bambooConnectorRef.getProjectIdentifier(), bambooConnectorRef.getIdentifier());

    if (!connectorDTO.isPresent() || !isABambooConnector(connectorDTO.get())) {
      throw new InvalidRequestException(String.format("Connector not found for identifier : [%s] with scope: [%s]",
                                            bambooConnectorRef.getIdentifier(), bambooConnectorRef.getScope()),
          WingsException.USER);
    }
    ConnectorInfoDTO connectors = connectorDTO.get().getConnector();
    return (BambooConnectorDTO) connectors.getConnectorConfig();
  }

  private boolean isABambooConnector(@Valid @NotNull ConnectorResponseDTO connectorResponseDTO) {
    return ConnectorType.BAMBOO == (connectorResponseDTO.getConnector().getConnectorType());
  }

  private ArtifactTaskExecutionResponse executeSyncTask(BambooArtifactDelegateRequest bambooArtifactDelegateRequest,
      ArtifactTaskType taskType, BaseNGAccess ngAccess, String ifFailedMessage) {
    DelegateResponseData responseData = getResponseData(ngAccess, bambooArtifactDelegateRequest, taskType);
    return getTaskExecutionResponse(responseData, ifFailedMessage);
  }

  private DelegateResponseData getResponseData(BaseNGAccess ngAccess,
      BambooArtifactDelegateRequest bambooArtifactDelegateRequest, ArtifactTaskType artifactTaskType) {
    ArtifactTaskParameters artifactTaskParameters = ArtifactTaskParameters.builder()
                                                        .accountId(ngAccess.getAccountIdentifier())
                                                        .artifactTaskType(artifactTaskType)
                                                        .attributes(bambooArtifactDelegateRequest)
                                                        .build();
    DelegateTaskRequestBuilder delegateTaskRequestBuilder =
        DelegateTaskRequest.builder()
            .accountId(ngAccess.getAccountIdentifier())
            .taskType(NGTaskType.BAMBOO_ARTIFACT_TASK_NG.name())
            .taskParameters(artifactTaskParameters)
            .executionTimeout(java.time.Duration.ofSeconds(TIME_OUT_IN_SECS))
            .taskSetupAbstraction("ng", "true")
            .taskSelectors(bambooArtifactDelegateRequest.getBambooConnectorDTO().getDelegateSelectors());
    if (EmptyPredicate.isEmpty(ngAccess.getOrgIdentifier())
        && EmptyPredicate.isEmpty(ngAccess.getProjectIdentifier())) {
      delegateTaskRequestBuilder.taskSetupAbstraction(TASK_SETUP_ABSTRACTION, ngAccess.getAccountIdentifier());
    } else if (EmptyPredicate.isEmpty(ngAccess.getProjectIdentifier())
        && EmptyPredicate.isNotEmpty(ngAccess.getOrgIdentifier())) {
      delegateTaskRequestBuilder.taskSetupAbstraction("orgIdentifier", ngAccess.getOrgIdentifier())
          .taskSetupAbstraction(TASK_SETUP_ABSTRACTION, ngAccess.getOrgIdentifier());
    } else {
      delegateTaskRequestBuilder.taskSetupAbstraction("orgIdentifier", ngAccess.getOrgIdentifier())
          .taskSetupAbstraction("projectIdentifier", ngAccess.getProjectIdentifier())
          .taskSetupAbstraction(
              TASK_SETUP_ABSTRACTION, ngAccess.getOrgIdentifier() + "/" + ngAccess.getProjectIdentifier());
    }
    final DelegateTaskRequest delegateTaskRequest = delegateTaskRequestBuilder.build();
    try {
      return delegateGrpcClientWrapper.executeSyncTaskV2(delegateTaskRequest);
    } catch (DelegateServiceDriverException ex) {
      log.info(ex.getMessage());
      throw exceptionManager.processException(ex, WingsException.ExecutionContext.MANAGER, log);
    }
  }

  private ArtifactTaskExecutionResponse getTaskExecutionResponse(
      DelegateResponseData responseData, String ifFailedMessage) {
    if (responseData instanceof ErrorNotifyResponseData) {
      ErrorNotifyResponseData errorNotifyResponseData = (ErrorNotifyResponseData) responseData;
      throw new ArtifactServerException(ifFailedMessage + " - " + errorNotifyResponseData.getErrorMessage());
    }
    if (responseData instanceof RemoteMethodReturnValueData) {
      RemoteMethodReturnValueData remoteMethodReturnValueData = (RemoteMethodReturnValueData) responseData;
      if (remoteMethodReturnValueData.getException() instanceof InvalidRequestException) {
        throw(InvalidRequestException)(remoteMethodReturnValueData.getException());
      } else {
        throw new ArtifactServerException(
            "Unexpected error during authentication to bamboo server " + remoteMethodReturnValueData.getReturnValue(),
            USER);
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
