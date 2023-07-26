/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.artifact.resources.ami;
import static io.harness.connector.ConnectorModule.DEFAULT_CONNECTOR_SERVICE;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.DelegateTaskRequest;
import io.harness.beans.IdentifierRef;
import io.harness.cdng.artifact.utils.ArtifactUtils;
import io.harness.cdng.common.resources.AwsResourceServiceHelper;
import io.harness.common.NGTaskType;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.services.ConnectorService;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.delegate.beans.RemoteMethodReturnValueData;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.task.artifacts.ArtifactDelegateRequestUtils;
import io.harness.delegate.task.artifacts.ArtifactSourceType;
import io.harness.delegate.task.artifacts.ArtifactTaskType;
import io.harness.delegate.task.artifacts.ami.AMIArtifactDelegateRequest;
import io.harness.delegate.task.artifacts.ami.AMIFilter;
import io.harness.delegate.task.artifacts.ami.AMITag;
import io.harness.delegate.task.artifacts.request.ArtifactTaskParameters;
import io.harness.delegate.task.artifacts.response.ArtifactTaskExecutionResponse;
import io.harness.delegate.task.artifacts.response.ArtifactTaskResponse;
import io.harness.exception.ArtifactServerException;
import io.harness.exception.HintException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.ng.core.BaseNGAccess;
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
import javax.validation.Valid;
import javax.validation.constraints.NotNull;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_ARTIFACTS})
public class AMIResourceServiceImpl implements AMIResourceService {
  private final ConnectorService connectorService;

  private final SecretManagerClientService secretManagerClientService;

  private final AwsResourceServiceHelper serviceHelper;

  @Inject private DelegateGrpcClientWrapper delegateGrpcClientWrapper;

  @VisibleForTesting static final int timeoutInSecs = 90;

  @Inject
  public AMIResourceServiceImpl(@Named(DEFAULT_CONNECTOR_SERVICE) ConnectorService connectorService,
      SecretManagerClientService secretManagerClientService, AwsResourceServiceHelper serviceHelper) {
    this.connectorService = connectorService;

    this.secretManagerClientService = secretManagerClientService;

    this.serviceHelper = serviceHelper;
  }

  @Override
  public List<BuildDetails> listVersions(IdentifierRef connectorRef, String accountId, String orgIdentifier,
      String projectIdentifier, String region, List<AMITag> tags, List<AMIFilter> filters, String versionRegex) {
    AwsConnectorDTO awsConnectorDTO = getConnector(connectorRef);

    BaseNGAccess baseNGAccess = getBaseNGAccess(accountId, orgIdentifier, projectIdentifier);

    List<EncryptedDataDetail> encryptionDetails = serviceHelper.getAwsEncryptionDetails(awsConnectorDTO, baseNGAccess);

    AMIArtifactDelegateRequest amiArtifactDelegateRequest =
        ArtifactDelegateRequestUtils.getAMIArtifactDelegateRequest(tags, filters, region, null, versionRegex,
            connectorRef.getIdentifier(), awsConnectorDTO, encryptionDetails, ArtifactSourceType.AMI);

    ArtifactTaskExecutionResponse artifactTaskExecutionResponse = getResponse(amiArtifactDelegateRequest, baseNGAccess,
        ArtifactTaskType.GET_BUILDS, "AMI Artifacts - Get Builds task failure due to error.");

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
  public List<String> listTags(
      IdentifierRef connectorRef, String accountId, String orgIdentifier, String projectIdentifier, String region) {
    AwsConnectorDTO awsConnectorDTO = getConnector(connectorRef);

    BaseNGAccess baseNGAccess = getBaseNGAccess(accountId, orgIdentifier, projectIdentifier);

    List<EncryptedDataDetail> encryptionDetails = serviceHelper.getAwsEncryptionDetails(awsConnectorDTO, baseNGAccess);

    AMIArtifactDelegateRequest amiArtifactDelegateRequest =
        ArtifactDelegateRequestUtils.getAMIArtifactDelegateRequest(null, null, region, null, null,
            connectorRef.getIdentifier(), awsConnectorDTO, encryptionDetails, ArtifactSourceType.AMI);

    ArtifactTaskExecutionResponse artifactTaskExecutionResponse = getResponse(amiArtifactDelegateRequest, baseNGAccess,
        ArtifactTaskType.GET_AMI_TAGS, "AMI Artifacts - list tags task failure due to error.");

    if (artifactTaskExecutionResponse != null) {
      if (artifactTaskExecutionResponse.getAmiTags() == null) {
        return new ArrayList<>();
      }
    } else {
      return new ArrayList<>();
    }

    return artifactTaskExecutionResponse.getAmiTags().getTags();
  }

  private ArtifactTaskExecutionResponse getResponse(
      AMIArtifactDelegateRequest request, BaseNGAccess baseNGAccess, ArtifactTaskType taskType, String errorMessage) {
    try {
      return executeSyncTask(request, taskType, baseNGAccess, errorMessage);
    } catch (Exception ex) {
      throw new HintException(String.format(ex.getMessage()));
    }
  }

  private ArtifactTaskExecutionResponse executeSyncTask(AMIArtifactDelegateRequest amiArtifactDelegateRequest,
      ArtifactTaskType taskType, BaseNGAccess ngAccess, String ifFailedMessage) {
    DelegateResponseData responseData = getResponseData(ngAccess, amiArtifactDelegateRequest, taskType);

    return getTaskExecutionResponse(responseData, ifFailedMessage);
  }

  private DelegateResponseData getResponseData(
      BaseNGAccess ngAccess, AMIArtifactDelegateRequest amiArtifactDelegateRequest, ArtifactTaskType artifactTaskType) {
    ArtifactTaskParameters artifactTaskParameters = ArtifactTaskParameters.builder()
                                                        .accountId(ngAccess.getAccountIdentifier())
                                                        .artifactTaskType(artifactTaskType)
                                                        .attributes(amiArtifactDelegateRequest)
                                                        .build();
    Map<String, String> abstractions = ArtifactUtils.getTaskSetupAbstractions(ngAccess);
    final DelegateTaskRequest delegateTaskRequest =
        DelegateTaskRequest.builder()
            .accountId(ngAccess.getAccountIdentifier())
            .taskType(NGTaskType.AMI_ARTIFACT_TASK_NG.name())
            .taskParameters(artifactTaskParameters)
            .executionTimeout(java.time.Duration.ofSeconds(timeoutInSecs))
            .taskSetupAbstractions(abstractions)
            .taskSelectors(amiArtifactDelegateRequest.getAwsConnectorDTO().getDelegateSelectors())
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
        throw new ArtifactServerException("Unexpected error during authentication to AMI Artifacts Server"
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

  private AwsConnectorDTO getConnector(IdentifierRef connectorRef) {
    Optional<ConnectorResponseDTO> connectorDTO = connectorService.get(connectorRef.getAccountIdentifier(),
        connectorRef.getOrgIdentifier(), connectorRef.getProjectIdentifier(), connectorRef.getIdentifier());

    if (!connectorDTO.isPresent() || !isAnAwsConnector(connectorDTO.get())) {
      throw new InvalidRequestException(String.format("Connector not found for identifier : [%s] with scope: [%s]",
                                            connectorRef.getIdentifier(), connectorRef.getScope()),
          WingsException.USER);
    }

    ConnectorInfoDTO connectors = connectorDTO.get().getConnector();

    return (AwsConnectorDTO) connectors.getConnectorConfig();
  }

  private boolean isAnAwsConnector(@Valid @NotNull ConnectorResponseDTO connectorResponseDTO) {
    return ConnectorType.AWS == (connectorResponseDTO.getConnector().getConnectorType());
  }

  private BaseNGAccess getBaseNGAccess(String accountId, String orgIdentifier, String projectIdentifier) {
    return BaseNGAccess.builder()
        .accountIdentifier(accountId)
        .orgIdentifier(orgIdentifier)
        .projectIdentifier(projectIdentifier)
        .build();
  }
}
