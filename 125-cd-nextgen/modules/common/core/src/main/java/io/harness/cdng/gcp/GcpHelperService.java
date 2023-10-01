/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.gcp;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.connector.ConnectorModule.DEFAULT_CONNECTOR_SERVICE;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DecryptableEntity;
import io.harness.beans.DelegateTaskRequest;
import io.harness.beans.IdentifierRef;
import io.harness.cdng.artifact.utils.ArtifactUtils;
import io.harness.common.NGTaskType;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.services.ConnectorService;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpCredentialType;
import io.harness.delegate.beans.connector.gcpconnector.GcpManualDetailsDTO;
import io.harness.delegate.task.gcp.GcpTaskType;
import io.harness.delegate.task.gcp.request.GcpRequest;
import io.harness.delegate.task.gcp.request.GcpTaskParameters;
import io.harness.delegate.task.gcp.response.GcpTaskResponse;
import io.harness.exception.DelegateServiceDriverException;
import io.harness.exception.GcpServerException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.exception.exceptionmanager.ExceptionManager;
import io.harness.ng.core.BaseNGAccess;
import io.harness.ng.core.NGAccess;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.service.DelegateGrpcClientWrapper;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nonnull;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
@OwnedBy(CDP)
public class GcpHelperService {
  @Inject @Named(DEFAULT_CONNECTOR_SERVICE) private ConnectorService connectorService;
  @Inject private SecretManagerClientService secretManagerClientService;
  @Inject private DelegateGrpcClientWrapper delegateGrpcClientWrapper;
  @Inject ExceptionManager exceptionManager;

  public <T extends GcpTaskResponse> T executeSyncTask(
      BaseNGAccess ngAccess, GcpRequest request, GcpTaskType type, String description) {
    DelegateResponseData responseData = getResponseData(ngAccess, request, type);
    return getTaskExecutionResponse(description, responseData);
  }

  public <T extends GcpTaskResponse> T executeSyncTaskV2(DelegateTaskRequest delegateTaskRequest, String description) {
    try {
      DelegateResponseData responseData = delegateGrpcClientWrapper.executeSyncTaskV2(delegateTaskRequest);
      return getTaskExecutionResponse(description, responseData);
    } catch (DelegateServiceDriverException ex) {
      throw exceptionManager.processException(ex, WingsException.ExecutionContext.MANAGER, log);
    }
  }

  public GcpConnectorDTO getConnector(IdentifierRef connectorRef) {
    Optional<ConnectorResponseDTO> connectorDTO = connectorService.get(connectorRef.getAccountIdentifier(),
        connectorRef.getOrgIdentifier(), connectorRef.getProjectIdentifier(), connectorRef.getIdentifier());

    if (!connectorDTO.isPresent() || !isGcpConnector(connectorDTO.get())) {
      throw new InvalidRequestException(format("Connector not found for identifier: [%s] with scope: [%s]",
                                            connectorRef.getIdentifier(), connectorRef.getScope()),
          WingsException.USER);
    }
    ConnectorInfoDTO connectors = connectorDTO.get().getConnector();
    return (GcpConnectorDTO) connectors.getConnectorConfig();
  }

  public List<EncryptedDataDetail> getEncryptionDetails(
      @Nonnull GcpConnectorDTO gcpConnectorDTO, @Nonnull NGAccess ngAccess) {
    List<DecryptableEntity> gcpDecryptableEntities = gcpConnectorDTO.getDecryptableEntities();
    if (isNotEmpty(gcpDecryptableEntities)) {
      return secretManagerClientService.getEncryptionDetails(ngAccess, gcpDecryptableEntities.get(0));
    }

    return Collections.emptyList();
  }

  public GcpManualDetailsDTO getManualDetailsDTO(GcpConnectorDTO gcpConnectorDTO) {
    if (gcpConnectorDTO.getCredential().getGcpCredentialType() == GcpCredentialType.MANUAL_CREDENTIALS) {
      return (GcpManualDetailsDTO) gcpConnectorDTO.getCredential().getConfig();
    }

    return null;
  }

  private DelegateResponseData getResponseData(BaseNGAccess ngAccess, GcpRequest gcpRequest, GcpTaskType gcpTaskType) {
    GcpTaskParameters gcpTaskParameters = GcpTaskParameters.builder()
                                              .accountId(ngAccess.getAccountIdentifier())
                                              .gcpTaskType(gcpTaskType)
                                              .gcpRequest(gcpRequest)
                                              .build();
    Map<String, String> abstractions = ArtifactUtils.getTaskSetupAbstractions(ngAccess);
    final DelegateTaskRequest delegateTaskRequest = DelegateTaskRequest.builder()
                                                        .accountId(ngAccess.getAccountIdentifier())
                                                        .taskType(NGTaskType.GCP_TASK.name())
                                                        .taskParameters(gcpTaskParameters)
                                                        .executionTimeout(gcpRequest.getExecutionTimeout())
                                                        .taskSetupAbstractions(abstractions)
                                                        .taskSelectors(gcpRequest.getDelegateSelectors())
                                                        .build();
    try {
      return delegateGrpcClientWrapper.executeSyncTaskV2(delegateTaskRequest);
    } catch (DelegateServiceDriverException ex) {
      throw exceptionManager.processException(ex, WingsException.ExecutionContext.MANAGER, log);
    }
  }

  private <T extends GcpTaskResponse> T getTaskExecutionResponse(
      String taskDescription, DelegateResponseData responseData) {
    if (responseData instanceof ErrorNotifyResponseData) {
      ErrorNotifyResponseData errorNotifyResponseData = (ErrorNotifyResponseData) responseData;
      throw new GcpServerException(
          format("Failed to %s - %s", taskDescription, errorNotifyResponseData.getErrorMessage()));
    }

    GcpTaskResponse gcpTaskResponse = (GcpTaskResponse) responseData;
    if (gcpTaskResponse.getCommandExecutionStatus() != SUCCESS) {
      throw new GcpServerException(format("Failed to %s - %s with error detail: %s", taskDescription,
          gcpTaskResponse.getErrorMessage(), gcpTaskResponse.getErrorDetail()));
    }

    return (T) gcpTaskResponse;
  }

  private boolean isGcpConnector(@Valid @NotNull ConnectorResponseDTO connectorResponseDTO) {
    return ConnectorType.GCP == (connectorResponseDTO.getConnector().getConnectorType());
  }
}
