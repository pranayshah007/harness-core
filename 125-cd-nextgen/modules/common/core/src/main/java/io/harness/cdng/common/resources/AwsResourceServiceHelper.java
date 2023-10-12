/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.common.resources;
import static io.harness.connector.ConnectorModule.DEFAULT_CONNECTOR_SERVICE;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.DelegateTaskRequest;
import io.harness.beans.IdentifierRef;
import io.harness.cdng.artifact.utils.ArtifactUtils;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.services.ConnectorService;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsManualConfigSpecDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsTaskParams;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.artifacts.ecr.EcrArtifactDelegateRequest;
import io.harness.exception.DelegateServiceDriverException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.exception.exceptionmanager.ExceptionManager;
import io.harness.ng.core.BaseNGAccess;
import io.harness.ng.core.NGAccess;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.service.DelegateGrpcClientWrapper;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nonnull;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_K8S})
@Singleton
@Slf4j
@OwnedBy(HarnessTeam.CDP)
public class AwsResourceServiceHelper {
  private final ConnectorService connectorService;
  private final SecretManagerClientService secretManagerClientService;
  private final DelegateGrpcClientWrapper delegateGrpcClientWrapper;
  @Inject ExceptionManager exceptionManager;
  @VisibleForTesting static final int timeoutInSecs = 30;

  @Inject
  public AwsResourceServiceHelper(@Named(DEFAULT_CONNECTOR_SERVICE) ConnectorService connectorService,
      SecretManagerClientService secretManagerClientService, DelegateGrpcClientWrapper delegateGrpcClientWrapper) {
    this.connectorService = connectorService;
    this.secretManagerClientService = secretManagerClientService;
    this.delegateGrpcClientWrapper = delegateGrpcClientWrapper;
  }

  public AwsConnectorDTO getAwsConnector(IdentifierRef connectorRef) {
    Optional<ConnectorResponseDTO> connectorDTO = connectorService.get(connectorRef.getAccountIdentifier(),
        connectorRef.getOrgIdentifier(), connectorRef.getProjectIdentifier(), connectorRef.getIdentifier());

    if (!connectorDTO.isPresent() || !isAAwsConnector(connectorDTO.get())) {
      throw new InvalidRequestException(String.format("Connector not found for identifier : [%s] with scope: [%s]",
                                            connectorRef.getIdentifier(), connectorRef.getScope()),
          WingsException.USER);
    }
    ConnectorInfoDTO connectors = connectorDTO.get().getConnector();
    return (AwsConnectorDTO) connectors.getConnectorConfig();
  }

  private boolean isAAwsConnector(@Valid @NotNull ConnectorResponseDTO connectorResponseDTO) {
    return ConnectorType.AWS == (connectorResponseDTO.getConnector().getConnectorType());
  }

  public BaseNGAccess getBaseNGAccess(String accountId, String orgIdentifier, String projectIdentifier) {
    return BaseNGAccess.builder()
        .accountIdentifier(accountId)
        .orgIdentifier(orgIdentifier)
        .projectIdentifier(projectIdentifier)
        .build();
  }

  public List<EncryptedDataDetail> getAwsEncryptionDetails(
      @Nonnull AwsConnectorDTO awsConnectorDTO, @Nonnull NGAccess ngAccess) {
    if (awsConnectorDTO.getCredential() != null && awsConnectorDTO.getCredential().getConfig() != null) {
      return secretManagerClientService.getEncryptionDetails(
          ngAccess, (AwsManualConfigSpecDTO) awsConnectorDTO.getCredential().getConfig());
    }
    return Collections.emptyList();
  }

  public DelegateResponseData getResponseData(BaseNGAccess ngAccess, TaskParameters taskParameters, String taskType) {
    return getResponseData(ngAccess, taskParameters, taskType, Duration.ofSeconds(timeoutInSecs));
  }
  public DelegateResponseData getResponseData(
      BaseNGAccess ngAccess, TaskParameters taskParameters, String taskType, Duration timeout) {
    Map<String, String> taskSetupAbstractions = ArtifactUtils.getTaskSetupAbstractions(ngAccess);

    final DelegateTaskRequest delegateTaskRequest =
        DelegateTaskRequest.builder()
            .accountId(ngAccess.getAccountIdentifier())
            .taskType(taskType)
            .taskParameters(taskParameters)
            .executionTimeout(timeout)
            .taskSetupAbstractions(taskSetupAbstractions)
            .taskSelectors(((AwsTaskParams) taskParameters).getAwsConnector().getDelegateSelectors())
            .build();
    try {
      return delegateGrpcClientWrapper.executeSyncTaskV2(delegateTaskRequest);
    } catch (DelegateServiceDriverException ex) {
      throw exceptionManager.processException(ex, WingsException.ExecutionContext.MANAGER, log);
    }
  }

  public DelegateResponseData getResponseData(
      BaseNGAccess ngAccess, EcrArtifactDelegateRequest ecrRequest, TaskParameters taskParameters, String taskType) {
    Map<String, String> taskSetupAbstractions = ArtifactUtils.getTaskSetupAbstractions(ngAccess);
    final DelegateTaskRequest delegateTaskRequest =
        DelegateTaskRequest.builder()
            .accountId(ngAccess.getAccountIdentifier())
            .taskType(taskType)
            .taskParameters(taskParameters)
            .executionTimeout(java.time.Duration.ofSeconds(timeoutInSecs))
            .taskSetupAbstractions(taskSetupAbstractions)
            .taskSelectors(ecrRequest.getAwsConnectorDTO().getDelegateSelectors())
            .build();
    try {
      return delegateGrpcClientWrapper.executeSyncTaskV2(delegateTaskRequest);
    } catch (DelegateServiceDriverException ex) {
      throw exceptionManager.processException(ex, WingsException.ExecutionContext.MANAGER, log);
    }
  }
}
