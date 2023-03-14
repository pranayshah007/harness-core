/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.provision.terraformcloud.resources.service;

import static io.harness.connector.ConnectorModule.DEFAULT_CONNECTOR_SERVICE;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DelegateTaskRequest;
import io.harness.beans.IdentifierRef;
import io.harness.cdng.artifact.utils.ArtifactUtils;
import io.harness.cdng.provision.terraformcloud.resources.dtos.OrganizationDTO;
import io.harness.cdng.provision.terraformcloud.resources.dtos.OrganizationsDTO;
import io.harness.cdng.provision.terraformcloud.resources.dtos.WorkspaceDTO;
import io.harness.cdng.provision.terraformcloud.resources.dtos.WorkspacesDTO;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.services.ConnectorService;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.terraformcloudconnector.TerraformCloudConnectorDTO;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.terraformcloud.request.TerraformCloudGetOrganizationsTaskParams;
import io.harness.delegate.task.terraformcloud.request.TerraformCloudGetWorkspacesTaskParams;
import io.harness.delegate.task.terraformcloud.request.TerraformCloudTaskParams;
import io.harness.delegate.task.terraformcloud.response.TerraformCloudOrganizationsTaskResponse;
import io.harness.delegate.task.terraformcloud.response.TerraformCloudWorkspacesTaskResponse;
import io.harness.exception.DelegateServiceDriverException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.TerraformCloudException;
import io.harness.exception.WingsException;
import io.harness.exception.exceptionmanager.ExceptionManager;
import io.harness.ng.core.BaseNGAccess;
import io.harness.ng.core.NGAccess;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.service.DelegateGrpcClientWrapper;

import software.wings.beans.TaskType;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@OwnedBy(HarnessTeam.CDP)
public class TerraformCloudResourceServiceImpl implements TerraformCloudResourceService {
  private static final String FAILED_TO_GET_ORGANIZATIONS_ERROR_MESSAGE =
      "Failed to get terraform cloud organizations: %s";
  private static final String FAILED_TO_GET_WORKSPACES_ERROR_MESSAGE = "Failed to get terraform cloud workspaces: %s";
  private static final int TIMEOUT_IN_SECONDS = 60;

  private final ConnectorService connectorService;
  private final SecretManagerClientService secretManagerClientService;
  private final DelegateGrpcClientWrapper delegateGrpcClientWrapper;
  @Inject ExceptionManager exceptionManager;

  @Inject
  public TerraformCloudResourceServiceImpl(@Named(DEFAULT_CONNECTOR_SERVICE) ConnectorService connectorService,
      SecretManagerClientService secretManagerClientService, DelegateGrpcClientWrapper delegateGrpcClientWrapper) {
    this.connectorService = connectorService;
    this.secretManagerClientService = secretManagerClientService;
    this.delegateGrpcClientWrapper = delegateGrpcClientWrapper;
  }

  @Override
  public OrganizationsDTO getOrganizations(IdentifierRef connectorRef, String orgIdentifier, String projectIdentifier) {
    BaseNGAccess access = getBaseNGAccess(connectorRef.getAccountIdentifier(), orgIdentifier, projectIdentifier);

    TerraformCloudConnectorDTO connector = getConnector(connectorRef);
    List<EncryptedDataDetail> encryptedData = getEncryptionDetails(connector, access);

    TerraformCloudGetOrganizationsTaskParams taskParams = TerraformCloudGetOrganizationsTaskParams.builder()
                                                              .terraformCloudConnectorDTO(connector)
                                                              .encryptionDetails(encryptedData)
                                                              .build();

    DelegateResponseData responseData = getResponseData(access, taskParams, TaskType.TERRAFORM_CLOUD_TASK_NG.name());
    return getOrganizations(responseData);
  }

  @Override
  public WorkspacesDTO getWorkspaces(
      IdentifierRef connectorRef, String orgIdentifier, String projectIdentifier, String organization) {
    BaseNGAccess access = getBaseNGAccess(connectorRef.getAccountIdentifier(), orgIdentifier, projectIdentifier);

    TerraformCloudConnectorDTO connector = getConnector(connectorRef);
    List<EncryptedDataDetail> encryptedData = getEncryptionDetails(connector, access);

    TerraformCloudGetWorkspacesTaskParams taskParams = TerraformCloudGetWorkspacesTaskParams.builder()
                                                           .terraformCloudConnectorDTO(connector)
                                                           .encryptionDetails(encryptedData)
                                                           .organization(organization)
                                                           .build();

    DelegateResponseData responseData = getResponseData(access, taskParams, TaskType.TERRAFORM_CLOUD_TASK_NG.name());
    return getWorkspaces(responseData);
  }

  private TerraformCloudConnectorDTO getConnector(IdentifierRef connectorRef) {
    Optional<ConnectorResponseDTO> connectorDTO = connectorService.get(connectorRef.getAccountIdentifier(),
        connectorRef.getOrgIdentifier(), connectorRef.getProjectIdentifier(), connectorRef.getIdentifier());

    if (connectorDTO.isEmpty() || !isTerraformCloudConnector(connectorDTO.get())) {
      throw new InvalidRequestException(String.format("Connector not found for identifier : [%s] with scope: [%s]",
                                            connectorRef.getIdentifier(), connectorRef.getScope()),
          WingsException.USER);
    }
    ConnectorInfoDTO connectors = connectorDTO.get().getConnector();
    return (TerraformCloudConnectorDTO) connectors.getConnectorConfig();
  }

  private BaseNGAccess getBaseNGAccess(String accountId, String orgIdentifier, String projectIdentifier) {
    return BaseNGAccess.builder()
        .accountIdentifier(accountId)
        .orgIdentifier(orgIdentifier)
        .projectIdentifier(projectIdentifier)
        .build();
  }

  private List<EncryptedDataDetail> getEncryptionDetails(
      @Nonnull TerraformCloudConnectorDTO terraformCloudConnectorDTO, @Nonnull NGAccess ngAccess) {
    if (terraformCloudConnectorDTO.getCredential() != null
        && terraformCloudConnectorDTO.getCredential().getSpec() != null) {
      return secretManagerClientService.getEncryptionDetails(
          ngAccess, terraformCloudConnectorDTO.getCredential().getSpec());
    }
    return Collections.emptyList();
  }

  private DelegateResponseData getResponseData(BaseNGAccess ngAccess, TaskParameters taskParameters, String taskType) {
    final DelegateTaskRequest delegateTaskRequest =
        DelegateTaskRequest.builder()
            .accountId(ngAccess.getAccountIdentifier())
            .taskType(taskType)
            .taskParameters(taskParameters)
            .executionTimeout(java.time.Duration.ofSeconds(TIMEOUT_IN_SECONDS))
            .taskSetupAbstractions(ArtifactUtils.getTaskSetupAbstractions(ngAccess))
            .taskSelectors(
                ((TerraformCloudTaskParams) taskParameters).getTerraformCloudConnectorDTO().getDelegateSelectors())
            .build();
    try {
      return delegateGrpcClientWrapper.executeSyncTaskV2(delegateTaskRequest);
    } catch (DelegateServiceDriverException ex) {
      throw exceptionManager.processException(ex, WingsException.ExecutionContext.MANAGER, log);
    }
  }

  private boolean isTerraformCloudConnector(@Valid @NotNull ConnectorResponseDTO connectorResponseDTO) {
    return ConnectorType.TERRAFORM_CLOUD == (connectorResponseDTO.getConnector().getConnectorType());
  }

  private OrganizationsDTO getOrganizations(DelegateResponseData responseData) {
    if (responseData instanceof ErrorNotifyResponseData) {
      ErrorNotifyResponseData errorNotifyResponseData = (ErrorNotifyResponseData) responseData;
      throw new TerraformCloudException(
          String.format(FAILED_TO_GET_ORGANIZATIONS_ERROR_MESSAGE, errorNotifyResponseData.getErrorMessage()));
    }
    TerraformCloudOrganizationsTaskResponse response = (TerraformCloudOrganizationsTaskResponse) responseData;
    if (response.getCommandExecutionStatus() != SUCCESS) {
      throw new TerraformCloudException(
          String.format(FAILED_TO_GET_ORGANIZATIONS_ERROR_MESSAGE, response.getErrorSummary()));
    }
    List<OrganizationDTO> organizations =
        response.getOrganizations()
            .entrySet()
            .stream()
            .map(entry
                -> OrganizationDTO.builder().organizationId(entry.getKey()).organizationName(entry.getValue()).build())
            .collect(Collectors.toList());
    return OrganizationsDTO.builder().organizations(organizations).build();
  }

  private WorkspacesDTO getWorkspaces(DelegateResponseData responseData) {
    if (responseData instanceof ErrorNotifyResponseData) {
      ErrorNotifyResponseData errorNotifyResponseData = (ErrorNotifyResponseData) responseData;
      throw new TerraformCloudException(
          String.format(FAILED_TO_GET_WORKSPACES_ERROR_MESSAGE, errorNotifyResponseData.getErrorMessage()));
    }
    TerraformCloudWorkspacesTaskResponse response = (TerraformCloudWorkspacesTaskResponse) responseData;
    if (response.getCommandExecutionStatus() != SUCCESS) {
      throw new TerraformCloudException(
          String.format(FAILED_TO_GET_WORKSPACES_ERROR_MESSAGE, response.getErrorSummary()));
    }
    List<WorkspaceDTO> workspaces =
        response.getWorkspaces()
            .entrySet()
            .stream()
            .map(entry -> WorkspaceDTO.builder().workspaceId(entry.getKey()).workspaceName(entry.getValue()).build())
            .collect(Collectors.toList());
    return WorkspacesDTO.builder().workspaces(workspaces).build();
  }
}
