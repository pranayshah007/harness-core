/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.servicenow.resources.service;

import static io.harness.connector.ConnectorModule.DEFAULT_CONNECTOR_SERVICE;
import static io.harness.utils.DelegateOwner.getNGTaskSetupAbstractionsWithOwner;

import io.harness.beans.DelegateTaskRequest;
import io.harness.beans.IdentifierRef;
import io.harness.common.NGTaskType;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.services.ConnectorService;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.delegate.beans.RemoteMethodReturnValueData;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.servicenow.ServiceNowConnectorDTO;
import io.harness.delegate.task.servicenow.ServiceNowTaskNGParameters;
import io.harness.delegate.task.servicenow.ServiceNowTaskNGParameters.ServiceNowTaskNGParametersBuilder;
import io.harness.delegate.task.servicenow.ServiceNowTaskNGResponse;
import io.harness.eraro.ErrorCode;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.ServiceNowException;
import io.harness.exception.WingsException;
import io.harness.ng.core.BaseNGAccess;
import io.harness.ng.core.NGAccess;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.service.DelegateGrpcClientWrapper;
import io.harness.servicenow.ServiceNowActionNG;
import io.harness.servicenow.ServiceNowFieldNG;
import io.harness.servicenow.ServiceNowTemplate;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ServiceNowResourceServiceImpl implements ServiceNowResourceService {
  private static final Duration TIMEOUT = Duration.ofSeconds(30);

  private final ConnectorService connectorService;
  private final SecretManagerClientService secretManagerClientService;
  private final DelegateGrpcClientWrapper delegateGrpcClientWrapper;

  @Inject
  public ServiceNowResourceServiceImpl(@Named(DEFAULT_CONNECTOR_SERVICE) ConnectorService connectorService,
      SecretManagerClientService secretManagerClientService, DelegateGrpcClientWrapper delegateGrpcClientWrapper) {
    this.connectorService = connectorService;
    this.secretManagerClientService = secretManagerClientService;
    this.delegateGrpcClientWrapper = delegateGrpcClientWrapper;
  }

  @Override
  public List<ServiceNowFieldNG> getIssueCreateMetadata(
      IdentifierRef serviceNowConnectorRef, String orgId, String projectId, String ticketType) {
    ServiceNowTaskNGParametersBuilder parametersBuilder = ServiceNowTaskNGParameters.builder()
                                                              .action(ServiceNowActionNG.GET_TICKET_CREATE_METADATA)
                                                              .ticketType(ticketType);
    return obtainServiceNowTaskNGResponse(serviceNowConnectorRef, orgId, projectId, parametersBuilder)
        .getServiceNowFieldNGList();
  }

  @Override
  public List<ServiceNowFieldNG> getMetadata(
      IdentifierRef serviceNowConnectorRef, String orgId, String projectId, String ticketType) {
    ServiceNowTaskNGParametersBuilder parametersBuilder =
        ServiceNowTaskNGParameters.builder().action(ServiceNowActionNG.GET_METADATA).ticketType(ticketType);
    return obtainServiceNowTaskNGResponse(serviceNowConnectorRef, orgId, projectId, parametersBuilder)
        .getServiceNowFieldNGList();
  }

  @Override
  public List<ServiceNowTemplate> getTemplateList(IdentifierRef connectorRef, String orgId, String projectId, int limit,
      int offset, String templateName, String ticketType) {
    ServiceNowTaskNGParametersBuilder parametersBuilder = ServiceNowTaskNGParameters.builder()
                                                              .action(ServiceNowActionNG.GET_TEMPLATE)
                                                              .ticketType(ticketType)
                                                              .templateListLimit(limit)
                                                              .templateListOffset(offset)
                                                              .templateName(templateName);

    return obtainServiceNowTaskNGResponse(connectorRef, orgId, projectId, parametersBuilder)
        .getServiceNowTemplateList();
  }

  private ServiceNowTaskNGResponse obtainServiceNowTaskNGResponse(IdentifierRef serviceNowConnectorRef, String orgId,
      String projectId, ServiceNowTaskNGParametersBuilder parametersBuilder) {
    ServiceNowConnectorDTO connector = getConnector(serviceNowConnectorRef);
    BaseNGAccess baseNGAccess = getBaseNGAccess(serviceNowConnectorRef, orgId, projectId);
    ServiceNowTaskNGParameters taskParameters =
        parametersBuilder.encryptionDetails(getEncryptionDetails(connector, baseNGAccess))
            .serviceNowConnectorDTO(connector)
            .build();

    final DelegateTaskRequest delegateTaskRequest = createDelegateTaskRequest(baseNGAccess, taskParameters);
    DelegateResponseData responseData = delegateGrpcClientWrapper.executeSyncTask(delegateTaskRequest);
    if (responseData instanceof ErrorNotifyResponseData) {
      ErrorNotifyResponseData errorNotifyResponseData = (ErrorNotifyResponseData) responseData;
      throw new ServiceNowException(
          errorNotifyResponseData.getErrorMessage(), ErrorCode.SERVICENOW_ERROR, WingsException.USER);
    } else if (responseData instanceof RemoteMethodReturnValueData) {
      RemoteMethodReturnValueData remoteMethodReturnValueData = (RemoteMethodReturnValueData) responseData;
      if (remoteMethodReturnValueData.getException() instanceof InvalidRequestException) {
        throw(InvalidRequestException)(remoteMethodReturnValueData.getException());
      } else {
        throw new ServiceNowException("Unexpected error during authentication to ServiceNow server "
                + remoteMethodReturnValueData.getReturnValue(),
            ErrorCode.SERVICENOW_ERROR, WingsException.USER);
      }
    }

    return (ServiceNowTaskNGResponse) responseData;
  }

  private ServiceNowConnectorDTO getConnector(IdentifierRef serviceNowConnectorRef) {
    Optional<ConnectorResponseDTO> connectorDTO =
        connectorService.get(serviceNowConnectorRef.getAccountIdentifier(), serviceNowConnectorRef.getOrgIdentifier(),
            serviceNowConnectorRef.getProjectIdentifier(), serviceNowConnectorRef.getIdentifier());
    if (!connectorDTO.isPresent() || ConnectorType.SERVICENOW != connectorDTO.get().getConnector().getConnectorType()) {
      throw new InvalidRequestException(
          String.format("ServiceNow connector not found for identifier : [%s] with scope: [%s]",
              serviceNowConnectorRef.getIdentifier(), serviceNowConnectorRef.getScope()),
          WingsException.USER);
    }

    ConnectorInfoDTO connectors = connectorDTO.get().getConnector();
    return (ServiceNowConnectorDTO) connectors.getConnectorConfig();
  }

  private BaseNGAccess getBaseNGAccess(IdentifierRef ref, String orgId, String projectId) {
    return BaseNGAccess.builder()
        .accountIdentifier(ref.getAccountIdentifier())
        .orgIdentifier(orgId)
        .projectIdentifier(projectId)
        .build();
  }

  private List<EncryptedDataDetail> getEncryptionDetails(
      ServiceNowConnectorDTO serviceNowConnectorDTO, NGAccess ngAccess) {
    return secretManagerClientService.getEncryptionDetails(ngAccess, serviceNowConnectorDTO);
  }

  private DelegateTaskRequest createDelegateTaskRequest(
      BaseNGAccess baseNGAccess, ServiceNowTaskNGParameters taskNGParameters) {
    final Map<String, String> ngTaskSetupAbstractionsWithOwner = getNGTaskSetupAbstractionsWithOwner(
        baseNGAccess.getAccountIdentifier(), baseNGAccess.getOrgIdentifier(), baseNGAccess.getProjectIdentifier());

    return DelegateTaskRequest.builder()
        .accountId(baseNGAccess.getAccountIdentifier())
        .taskType(NGTaskType.SERVICENOW_TASK_NG.name())
        .taskParameters(taskNGParameters)
        .taskSelectors(taskNGParameters.getDelegateSelectors())
        .executionTimeout(TIMEOUT)
        .taskSetupAbstractions(ngTaskSetupAbstractionsWithOwner)
        .build();
  }
}
