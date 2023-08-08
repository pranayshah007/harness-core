/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.artifact.resources.jenkins.service;
import static io.harness.connector.ConnectorModule.DEFAULT_CONNECTOR_SERVICE;
import static io.harness.exception.WingsException.USER;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.DelegateTaskRequest;
import io.harness.beans.DelegateTaskRequest.DelegateTaskRequestBuilder;
import io.harness.beans.IdentifierRef;
import io.harness.cdng.artifact.resources.jenkins.dtos.JenkinsJobDetailsDTO;
import io.harness.cdng.artifact.resources.jenkins.mappers.JenkinsResourceMapper;
import io.harness.common.NGTaskType;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.services.ConnectorService;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.delegate.beans.RemoteMethodReturnValueData;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.jenkins.JenkinsConnectorDTO;
import io.harness.delegate.task.artifacts.ArtifactDelegateRequestUtils;
import io.harness.delegate.task.artifacts.ArtifactSourceType;
import io.harness.delegate.task.artifacts.ArtifactTaskType;
import io.harness.delegate.task.artifacts.jenkins.JenkinsArtifactDelegateRequest;
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
import software.wings.helpers.ext.jenkins.JobDetails;

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

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_ARTIFACTS})
@Slf4j
public class JenkinsResourceServiceImpl implements JenkinsResourceService {
  private final ConnectorService connectorService;
  private final SecretManagerClientService secretManagerClientService;
  @Inject private DelegateGrpcClientWrapper delegateGrpcClientWrapper;
  @Inject ExceptionManager exceptionManager;
  @VisibleForTesting static final int timeoutInSecs = 30;

  @Inject
  public JenkinsResourceServiceImpl(@Named(DEFAULT_CONNECTOR_SERVICE) ConnectorService connectorService,
      SecretManagerClientService secretManagerClientService) {
    this.connectorService = connectorService;
    this.secretManagerClientService = secretManagerClientService;
  }

  @Override
  public JenkinsJobDetailsDTO getJobDetails(
      IdentifierRef jenkinsConnectorRef, String orgIdentifier, String projectIdentifier, String parentJobName) {
    JenkinsConnectorDTO connector = getConnector(jenkinsConnectorRef);
    BaseNGAccess baseNGAccess =
        getBaseNGAccess(jenkinsConnectorRef.getAccountIdentifier(), orgIdentifier, projectIdentifier);
    List<EncryptedDataDetail> encryptionDetails = getEncryptionDetails(connector, baseNGAccess);
    JenkinsArtifactDelegateRequest jenkinsRequest =
        ArtifactDelegateRequestUtils.getJenkinsDelegateRequest(jenkinsConnectorRef.getIdentifier(), connector,
            encryptionDetails, ArtifactSourceType.JENKINS, null, parentJobName, null, null, null);
    try {
      ArtifactTaskExecutionResponse artifactTaskExecutionResponse = executeSyncTask(
          jenkinsRequest, ArtifactTaskType.GET_JOBS, baseNGAccess, "Jenkins Get Job task failure due to error");
      return JenkinsResourceMapper.toJenkinsJobDetailsDTO(artifactTaskExecutionResponse);
    } catch (DelegateServiceDriverException ex) {
      throw new HintException(
          String.format(HintException.DELEGATE_NOT_AVAILABLE, DocumentLinksConstants.DELEGATE_INSTALLATION_LINK),
          new DelegateNotAvailableException(ex.getCause().getMessage(), WingsException.USER));
    } catch (ExplanationException e) {
      throw new HintException(
          HintException.HINT_DOCKER_HUB_ACCESS_DENIED, new InvalidRequestException(e.getMessage(), USER));
    }
  }

  @Override
  public List<String> getArtifactPath(
      IdentifierRef jenkinsConnectorRef, String orgIdentifier, String projectIdentifier, String jobName) {
    JenkinsConnectorDTO connector = getConnector(jenkinsConnectorRef);
    BaseNGAccess baseNGAccess =
        getBaseNGAccess(jenkinsConnectorRef.getAccountIdentifier(), orgIdentifier, projectIdentifier);
    List<EncryptedDataDetail> encryptionDetails = getEncryptionDetails(connector, baseNGAccess);
    JenkinsArtifactDelegateRequest jenkinsRequest =
        ArtifactDelegateRequestUtils.getJenkinsDelegateRequest(jenkinsConnectorRef.getIdentifier(), connector,
            encryptionDetails, ArtifactSourceType.JENKINS, null, null, jobName, null);
    try {
      ArtifactTaskExecutionResponse artifactTaskExecutionResponse = executeSyncTask(jenkinsRequest,
          ArtifactTaskType.GET_ARTIFACT_PATH, baseNGAccess, "Jenkins Get Job task failure due to error");
      return artifactTaskExecutionResponse.getArtifactPath();
    } catch (DelegateServiceDriverException ex) {
      throw new HintException(
          String.format(HintException.DELEGATE_NOT_AVAILABLE, DocumentLinksConstants.DELEGATE_INSTALLATION_LINK),
          new DelegateNotAvailableException(ex.getCause().getMessage(), WingsException.USER));
    } catch (ExplanationException e) {
      throw new HintException(
          HintException.HINT_DOCKER_HUB_ACCESS_DENIED, new InvalidRequestException(e.getMessage(), USER));
    }
  }

  public List<BuildDetails> getBuildForJob(IdentifierRef jenkinsConnectorRef, String orgIdentifier,
      String projectIdentifier, String jobName, List<String> artifactPath) {
    JenkinsConnectorDTO connector = getConnector(jenkinsConnectorRef);
    BaseNGAccess baseNGAccess =
        getBaseNGAccess(jenkinsConnectorRef.getAccountIdentifier(), orgIdentifier, projectIdentifier);
    List<EncryptedDataDetail> encryptionDetails = getEncryptionDetails(connector, baseNGAccess);
    JenkinsArtifactDelegateRequest jenkinsRequest =
        ArtifactDelegateRequestUtils.getJenkinsDelegateRequest(jenkinsConnectorRef.getIdentifier(), connector,
            encryptionDetails, ArtifactSourceType.JENKINS, null, null, jobName, artifactPath);
    try {
      ArtifactTaskExecutionResponse artifactTaskExecutionResponse = executeSyncTask(
          jenkinsRequest, ArtifactTaskType.GET_BUILDS, baseNGAccess, "Jenkins Get Builds task failure due to error");
      return artifactTaskExecutionResponse.getBuildDetails();
    } catch (DelegateServiceDriverException ex) {
      throw new HintException(
          String.format(HintException.DELEGATE_NOT_AVAILABLE, DocumentLinksConstants.DELEGATE_INSTALLATION_LINK),
          new DelegateNotAvailableException(ex.getCause().getMessage(), USER));
    } catch (ExplanationException e) {
      throw new HintException(
          HintException.HINT_DOCKER_HUB_ACCESS_DENIED, new InvalidRequestException(e.getMessage(), USER));
    }
  }

  public List<JobDetails> getJobParameters(
      IdentifierRef jenkinsConnectorRef, String orgIdentifier, String projectIdentifier, String jobName) {
    JenkinsConnectorDTO connector = getConnector(jenkinsConnectorRef);
    BaseNGAccess baseNGAccess =
        getBaseNGAccess(jenkinsConnectorRef.getAccountIdentifier(), orgIdentifier, projectIdentifier);
    List<EncryptedDataDetail> encryptionDetails = getEncryptionDetails(connector, baseNGAccess);
    JenkinsArtifactDelegateRequest jenkinsRequest =
        ArtifactDelegateRequestUtils.getJenkinsDelegateRequest(jenkinsConnectorRef.getIdentifier(), connector,
            encryptionDetails, ArtifactSourceType.JENKINS, null, null, jobName, null);
    try {
      ArtifactTaskExecutionResponse artifactTaskExecutionResponse = executeSyncTask(jenkinsRequest,
          ArtifactTaskType.GET_JOB_PARAMETERS, baseNGAccess, "Jenkins Get Job task failure due to error");
      return artifactTaskExecutionResponse.getJobDetails();
    } catch (DelegateServiceDriverException ex) {
      throw new HintException(
          String.format(HintException.DELEGATE_NOT_AVAILABLE, DocumentLinksConstants.DELEGATE_INSTALLATION_LINK),
          new DelegateNotAvailableException(ex.getCause().getMessage(), USER));
    } catch (ExplanationException e) {
      throw new HintException(
          HintException.HINT_DOCKER_HUB_ACCESS_DENIED, new InvalidRequestException(e.getMessage(), USER));
    }
  }

  private ArtifactTaskExecutionResponse executeSyncTask(JenkinsArtifactDelegateRequest jenkinsRequest,
      ArtifactTaskType taskType, BaseNGAccess ngAccess, String ifFailedMessage) {
    DelegateResponseData responseData = getResponseData(ngAccess, jenkinsRequest, taskType);
    return getTaskExecutionResponse(responseData, ifFailedMessage);
  }

  private DelegateResponseData getResponseData(
      BaseNGAccess ngAccess, JenkinsArtifactDelegateRequest delegateRequest, ArtifactTaskType artifactTaskType) {
    ArtifactTaskParameters artifactTaskParameters = ArtifactTaskParameters.builder()
                                                        .accountId(ngAccess.getAccountIdentifier())
                                                        .artifactTaskType(artifactTaskType)
                                                        .attributes(delegateRequest)
                                                        .build();
    DelegateTaskRequestBuilder delegateTaskRequestBuilder =
        DelegateTaskRequest.builder()
            .accountId(ngAccess.getAccountIdentifier())
            .taskType(NGTaskType.JENKINS_ARTIFACT_TASK_NG.name())
            .taskParameters(artifactTaskParameters)
            .executionTimeout(java.time.Duration.ofSeconds(timeoutInSecs))
            .taskSetupAbstraction("ng", "true")
            .taskSelectors(delegateRequest.getJenkinsConnectorDTO().getDelegateSelectors());
    if (EmptyPredicate.isEmpty(ngAccess.getOrgIdentifier())
        && EmptyPredicate.isEmpty(ngAccess.getProjectIdentifier())) {
      delegateTaskRequestBuilder.taskSetupAbstraction("owner", ngAccess.getAccountIdentifier());
    } else if (EmptyPredicate.isEmpty(ngAccess.getProjectIdentifier())
        && EmptyPredicate.isNotEmpty(ngAccess.getOrgIdentifier())) {
      delegateTaskRequestBuilder.taskSetupAbstraction("orgIdentifier", ngAccess.getOrgIdentifier())
          .taskSetupAbstraction("owner", ngAccess.getOrgIdentifier());
    } else {
      delegateTaskRequestBuilder.taskSetupAbstraction("orgIdentifier", ngAccess.getOrgIdentifier())
          .taskSetupAbstraction("projectIdentifier", ngAccess.getProjectIdentifier())
          .taskSetupAbstraction("owner", ngAccess.getOrgIdentifier() + "/" + ngAccess.getProjectIdentifier());
    }
    final DelegateTaskRequest delegateTaskRequest = delegateTaskRequestBuilder.build();
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

  private List<EncryptedDataDetail> getEncryptionDetails(
      @Nonnull JenkinsConnectorDTO jenkinsConnectorDTO, @Nonnull NGAccess ngAccess) {
    if (jenkinsConnectorDTO.getAuth() != null && jenkinsConnectorDTO.getAuth().getCredentials() != null) {
      return secretManagerClientService.getEncryptionDetails(ngAccess, jenkinsConnectorDTO.getAuth().getCredentials());
    }
    return new ArrayList<>();
  }

  private JenkinsConnectorDTO getConnector(IdentifierRef jenkinsConnectorRef) {
    Optional<ConnectorResponseDTO> connectorDTO =
        connectorService.get(jenkinsConnectorRef.getAccountIdentifier(), jenkinsConnectorRef.getOrgIdentifier(),
            jenkinsConnectorRef.getProjectIdentifier(), jenkinsConnectorRef.getIdentifier());

    if (!connectorDTO.isPresent() || !isAJenkinsConnector(connectorDTO.get())) {
      throw new InvalidRequestException(String.format("Connector not found for identifier : [%s] with scope: [%s]",
                                            jenkinsConnectorRef.getIdentifier(), jenkinsConnectorRef.getScope()),
          WingsException.USER);
    }
    ConnectorInfoDTO connectors = connectorDTO.get().getConnector();
    return (JenkinsConnectorDTO) connectors.getConnectorConfig();
  }

  private boolean isAJenkinsConnector(@Valid @NotNull ConnectorResponseDTO connectorResponseDTO) {
    return ConnectorType.JENKINS == (connectorResponseDTO.getConnector().getConnectorType());
  }

  private BaseNGAccess getBaseNGAccess(String accountId, String orgIdentifier, String projectIdentifier) {
    return BaseNGAccess.builder()
        .accountIdentifier(accountId)
        .orgIdentifier(orgIdentifier)
        .projectIdentifier(projectIdentifier)
        .build();
  }
}
