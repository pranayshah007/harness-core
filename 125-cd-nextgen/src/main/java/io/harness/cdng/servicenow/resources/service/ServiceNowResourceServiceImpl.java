/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.servicenow.resources.service;

import static io.harness.connector.ConnectorModule.DEFAULT_CONNECTOR_SERVICE;
import static io.harness.servicenow.ServiceNowUtils.getListOfFieldsFromStringOfFields;
import static io.harness.utils.DelegateOwner.getNGTaskSetupAbstractionsWithOwner;

import static java.util.Objects.isNull;
import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.DelegateTaskRequest;
import io.harness.beans.FeatureName;
import io.harness.beans.IdentifierRef;
import io.harness.cdng.featureFlag.CDFeatureFlagHelper;
import io.harness.cdng.servicenow.ServiceNowTemplateTypeEnum;
import io.harness.cdng.servicenow.utils.ServiceNowFieldNGUtils;
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
import io.harness.exception.DelegateNotAvailableException;
import io.harness.exception.DelegateServiceDriverException;
import io.harness.exception.HintException;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.ServiceNowException;
import io.harness.exception.WingsException;
import io.harness.exception.exceptionmanager.exceptionhandler.DocumentLinksConstants;
import io.harness.ng.core.BaseNGAccess;
import io.harness.ng.core.NGAccess;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.service.DelegateGrpcClientWrapper;
import io.harness.servicenow.ServiceNowActionNG;
import io.harness.servicenow.ServiceNowFieldNG;
import io.harness.servicenow.ServiceNowFieldSchemaNG;
import io.harness.servicenow.ServiceNowFieldTypeNG;
import io.harness.servicenow.ServiceNowStagingTable;
import io.harness.servicenow.ServiceNowTemplate;
import io.harness.servicenow.ServiceNowTicketNG;
import io.harness.servicenow.ServiceNowTicketTypeDTO;
import io.harness.servicenow.ServiceNowTicketTypeNG;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.api.client.util.Joiner;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import io.fabric8.utils.Lists;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_APPROVALS})
@Slf4j
public class ServiceNowResourceServiceImpl implements ServiceNowResourceService {
  private static final Duration TIMEOUT = Duration.ofSeconds(30);
  private static final String SYS_ID_FIELD_KEY = "sys_id";
  private static final String CHANGE_REQUEST = "CHANGE_REQUEST";

  private static final List<String> DEFAULT_READ_ONLY_STANDARD_TEMPLATE_FIELDS =
      Lists.newArrayList("description", "backout_plan", "test_plan", "implementation_plan");

  private final ConnectorService connectorService;
  private final SecretManagerClientService secretManagerClientService;
  private final DelegateGrpcClientWrapper delegateGrpcClientWrapper;
  private final CDFeatureFlagHelper cdFeatureFlagHelper;

  @Inject
  public ServiceNowResourceServiceImpl(@Named(DEFAULT_CONNECTOR_SERVICE) ConnectorService connectorService,
      SecretManagerClientService secretManagerClientService, DelegateGrpcClientWrapper delegateGrpcClientWrapper,
      CDFeatureFlagHelper cdFeatureFlagHelper) {
    this.connectorService = connectorService;
    this.secretManagerClientService = secretManagerClientService;
    this.delegateGrpcClientWrapper = delegateGrpcClientWrapper;
    this.cdFeatureFlagHelper = cdFeatureFlagHelper;
  }

  @Override
  public List<ServiceNowFieldNG> getIssueCreateMetadata(
      IdentifierRef serviceNowConnectorRef, String orgId, String projectId, String ticketType) {
    if (cdFeatureFlagHelper.isEnabled(
            serviceNowConnectorRef.getAccountIdentifier(), FeatureName.CDS_SERVICENOW_USE_METADATA_V2)) {
      try {
        ServiceNowTaskNGParametersBuilder parametersBuilder =
            ServiceNowTaskNGParameters.builder().action(ServiceNowActionNG.GET_METADATA_V2).ticketType(ticketType);
        return parseFieldsAndSetSysIdAsNotMandatory(
            obtainServiceNowTaskNGResponse(serviceNowConnectorRef, orgId, projectId, parametersBuilder)
                .getServiceNowFieldJsonNGListAsString());
      } catch (Exception ex) {
        // InvalidArgumentsException may occur when delegate task expired or not picked.
        log.warn("Exception while executing GET_METADATA_V2 servicenow task", ex);
        if (!isResultingFromDelegateNotHavingActionEnum(ex)) {
          throw ex;
        }
      }
    }
    ServiceNowTaskNGParametersBuilder parametersBuilder = ServiceNowTaskNGParameters.builder()
                                                              .action(ServiceNowActionNG.GET_TICKET_CREATE_METADATA)
                                                              .ticketType(ticketType);
    return obtainServiceNowTaskNGResponse(serviceNowConnectorRef, orgId, projectId, parametersBuilder)
        .getServiceNowFieldNGList()
        .stream()
        .map(this::populateFieldTypeFromInternalType)
        .collect(Collectors.toList());
  }

  private ServiceNowFieldNG populateFieldTypeFromInternalType(ServiceNowFieldNG serviceNowFieldNG) {
    ServiceNowFieldTypeNG serviceNowFieldTypeNG =
        ServiceNowFieldTypeNG.fromTypeString(serviceNowFieldNG.getInternalType());
    String typeStrOutput = isNull(serviceNowFieldTypeNG) || serviceNowFieldTypeNG.equals(ServiceNowFieldTypeNG.UNKNOWN)
        ? null
        : serviceNowFieldNG.getInternalType();
    serviceNowFieldNG.setSchema(ServiceNowFieldSchemaNG.builder()
                                    .array(false)
                                    .customType(null)
                                    .type(serviceNowFieldTypeNG)
                                    .typeStr(typeStrOutput)
                                    .build());
    serviceNowFieldNG.setInternalType(null);
    return serviceNowFieldNG;
  }

  private List<ServiceNowFieldNG> parseFieldsAndSetSysIdAsNotMandatory(String jsonNodeListAsString) {
    List<JsonNode> jsonNodeList = ServiceNowFieldNGUtils.parseServiceNowMetadataResponse(jsonNodeListAsString);
    return jsonNodeList.stream()
        .map(jsonNode -> {
          ServiceNowFieldNG serviceNowFieldNG = ServiceNowFieldNGUtils.parseServiceNowFieldNG(jsonNode);

          if (SYS_ID_FIELD_KEY.equals(serviceNowFieldNG.getKey())) {
            serviceNowFieldNG.setRequired(false);
          }
          return serviceNowFieldNG;
        })
        .collect(Collectors.toList());
  }

  @Override
  public List<ServiceNowFieldNG> getMetadata(
      IdentifierRef serviceNowConnectorRef, String orgId, String projectId, String ticketType) {
    if (cdFeatureFlagHelper.isEnabled(
            serviceNowConnectorRef.getAccountIdentifier(), FeatureName.CDS_SERVICENOW_USE_METADATA_V2)) {
      try {
        ServiceNowTaskNGParametersBuilder parametersBuilder =
            ServiceNowTaskNGParameters.builder().action(ServiceNowActionNG.GET_METADATA_V2).ticketType(ticketType);
        return parseFieldsAndSetSysIdAsNotMandatory(
            obtainServiceNowTaskNGResponse(serviceNowConnectorRef, orgId, projectId, parametersBuilder)
                .getServiceNowFieldJsonNGListAsString());
      } catch (Exception ex) {
        // InvalidArgumentsException may occur when delegate task expired or not picked.
        log.warn("Exception while executing GET_METADATA_V2 servicenow task", ex);
        if (!isResultingFromDelegateNotHavingActionEnum(ex)) {
          throw ex;
        }
      }
    }

    ServiceNowTaskNGParametersBuilder parametersBuilder =
        ServiceNowTaskNGParameters.builder().action(ServiceNowActionNG.GET_METADATA).ticketType(ticketType);
    return obtainServiceNowTaskNGResponse(serviceNowConnectorRef, orgId, projectId, parametersBuilder)
        .getServiceNowFieldNGList();
  }

  public List<ServiceNowTemplate> getTemplateListForStandardTemplate(IdentifierRef connectorRef, String orgId,
      String projectId, int limit, int offset, String templateName, String ticketType, String searchTerm) {
    ServiceNowTaskNGParametersBuilder parametersBuilder = ServiceNowTaskNGParameters.builder()
                                                              .action(ServiceNowActionNG.GET_STANDARD_TEMPLATE)
                                                              .ticketType(ticketType)
                                                              .templateListLimit(limit)
                                                              .templateListOffset(offset)
                                                              .templateName(templateName)
                                                              .searchTerm(searchTerm);

    if (StringUtils.isBlank(templateName)) {
      return obtainServiceNowTaskNGResponse(connectorRef, orgId, projectId, parametersBuilder)
          .getServiceNowTemplateList();
    }

    ServiceNowTaskNGResponse response =
        obtainServiceNowTaskNGResponse(connectorRef, orgId, projectId, parametersBuilder);

    String serviceNowFieldJsonNGListAsString = response.getServiceNowFieldJsonNGListAsString();

    return Collections.singletonList(ServiceNowTemplate.builder()
                                         .name(response.getServiceNowTemplateList().get(0).getName())
                                         .sys_id(response.getServiceNowTemplateList().get(0).getSys_id())
                                         .fields(getListOfFieldsFromStringOfFields(serviceNowFieldJsonNGListAsString))
                                         .build());
  }
  public List<ServiceNowTemplate> getTemplateList(IdentifierRef connectorRef, String orgId, String projectId, int limit,
      int offset, String templateName, String ticketType, String searchTerm, ServiceNowTemplateTypeEnum templateType) {
    // Offset provided from ui is equivalent to page number, So calculating the actual offset value.
    offset = offset * limit;

    if (ServiceNowTemplateTypeEnum.STANDARD == templateType) {
      if (!(CHANGE_REQUEST.equalsIgnoreCase(ticketType))) {
        throw new InvalidRequestException(
            "Get template metadata is supported for ticketType CHANGE_REQUEST and templateType STANDARD");
      }
      if (!cdFeatureFlagHelper.isEnabled(
              connectorRef.getAccountIdentifier(), FeatureName.CDS_GET_SERVICENOW_STANDARD_TEMPLATE)) {
        throw new InvalidRequestException(
            "Feature flag CDS_GET_SERVICENOW_STANDARD_TEMPLATE is not enabled for this account");
      }
      return getTemplateListForStandardTemplate(
          connectorRef, orgId, projectId, limit, offset, templateName, ticketType, searchTerm);
    }

    ServiceNowTaskNGParametersBuilder parametersBuilder = ServiceNowTaskNGParameters.builder()
                                                              .action(ServiceNowActionNG.GET_TEMPLATE)
                                                              .ticketType(ticketType)
                                                              .templateListLimit(limit)
                                                              .templateListOffset(offset)
                                                              .templateName(templateName)
                                                              .searchTerm(searchTerm);

    return obtainServiceNowTaskNGResponse(connectorRef, orgId, projectId, parametersBuilder)
        .getServiceNowTemplateList();
  }

  @Override
  public List<ServiceNowStagingTable> getStagingTableList(IdentifierRef connectorRef, String orgId, String projectId) {
    ServiceNowTaskNGParametersBuilder parametersBuilder =
        ServiceNowTaskNGParameters.builder().action(ServiceNowActionNG.GET_IMPORT_SET_STAGING_TABLES);
    return obtainServiceNowTaskNGResponse(connectorRef, orgId, projectId, parametersBuilder)
        .getServiceNowStagingTableList();
  }

  @Override
  public List<ServiceNowTicketTypeDTO> getTicketTypesV2(IdentifierRef connectorRef, String orgId, String projectId) {
    ServiceNowTaskNGParametersBuilder parametersBuilder =
        ServiceNowTaskNGParameters.builder().action(ServiceNowActionNG.GET_TICKET_TYPES);
    List<ServiceNowTicketTypeDTO> serviceNowStandardTicketTypeList =
        Arrays.stream(ServiceNowTicketTypeNG.values())
            .map(ticketType -> new ServiceNowTicketTypeDTO(ticketType.name(), ticketType.getDisplayName()))
            .collect(Collectors.toList());
    try {
      return obtainServiceNowTaskNGResponse(connectorRef, orgId, projectId, parametersBuilder)
          .getServiceNowTicketTypeList();
    } catch (Exception ex) {
      // InvalidArgumentsException may occur when delegate task expired or not picked.
      log.warn("Exception while executing GET_TICKET_TYPES servicenow task", ex);
      if (isResultingFromDelegateNotHavingActionEnum(ex)) {
        return serviceNowStandardTicketTypeList;
      }
      throw ex;
    }
  }

  @Override
  public ServiceNowTicketNG getTicketDetails(IdentifierRef connectorRef, String orgId, String projectId,
      String ticketType, String ticketNumber, List<String> fieldsList) {
    String queryFields = null;
    if (fieldsList != null) {
      queryFields = Joiner.on(',').join(fieldsList);
    }
    ServiceNowTaskNGParametersBuilder parametersBuilder = ServiceNowTaskNGParameters.builder()
                                                              .action(ServiceNowActionNG.GET_TICKET)
                                                              .queryFields(isBlank(queryFields) ? null : queryFields)
                                                              .ticketType(ticketType)
                                                              .ticketNumber(ticketNumber);
    return obtainServiceNowTaskNGResponse(connectorRef, orgId, projectId, parametersBuilder).getTicket();
  }

  @Override
  public List<String> getStandardTemplateReadOnlyFields(IdentifierRef connectorRef, String orgId, String projectId) {
    try {
      ServiceNowTaskNGParametersBuilder parametersBuilder =
          ServiceNowTaskNGParameters.builder().action(ServiceNowActionNG.GET_STANDARD_TEMPLATES_READONLY_FIELDS);
      String serviceNowStandardTemplateReadOnlyFields =
          obtainServiceNowTaskNGResponse(connectorRef, orgId, projectId, parametersBuilder)
              .getServiceNowStandardTemplateReadOnlyFields();
      if (StringUtils.isBlank(serviceNowStandardTemplateReadOnlyFields)) {
        return Collections.emptyList();
      }
      return List.of(serviceNowStandardTemplateReadOnlyFields.split(","));
    } catch (ServiceNowException e) {
      if (e.getMessage() != null
          && e.getMessage().contains("User is unauthorized to access table: std_change_properties")) {
        log.warn("Acl related error occured while fetching readonly fields:", e);
        return DEFAULT_READ_ONLY_STANDARD_TEMPLATE_FIELDS;
      }
      throw e;
    } catch (Exception e) {
      throw e;
    }
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
    DelegateResponseData responseData;
    try {
      responseData = delegateGrpcClientWrapper.executeSyncTaskV2(delegateTaskRequest);
    } catch (DelegateServiceDriverException ex) {
      log.warn("Exception while executing servicenow task", ex);
      throw buildDelegateNotAvailableHintException("Delegates are not available for performing servicenow operation.");
    }

    if (responseData instanceof ErrorNotifyResponseData) {
      ErrorNotifyResponseData errorNotifyResponseData = (ErrorNotifyResponseData) responseData;
      throw new ServiceNowException(
          errorNotifyResponseData.getErrorMessage(), ErrorCode.SERVICENOW_ERROR, WingsException.USER);
    } else if (responseData instanceof RemoteMethodReturnValueData) {
      RemoteMethodReturnValueData remoteMethodReturnValueData = (RemoteMethodReturnValueData) responseData;
      if (remoteMethodReturnValueData.getException() instanceof InvalidRequestException) {
        throw (InvalidRequestException) (remoteMethodReturnValueData.getException());
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
    if (!isNull(serviceNowConnectorDTO.getAuth()) && !isNull(serviceNowConnectorDTO.getAuth().getCredentials())) {
      return secretManagerClientService.getEncryptionDetails(
          ngAccess, serviceNowConnectorDTO.getAuth().getCredentials());
    }
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
        .executionTimeout(TIMEOUT)
        .taskSetupAbstractions(ngTaskSetupAbstractionsWithOwner)
        .build();
  }

  private HintException buildDelegateNotAvailableHintException(String delegateDownErrorMessage) {
    return new HintException(
        String.format(HintException.DELEGATE_NOT_AVAILABLE, DocumentLinksConstants.DELEGATE_INSTALLATION_LINK),
        new DelegateNotAvailableException(delegateDownErrorMessage, WingsException.USER));
  }

  private static boolean isResultingFromDelegateNotHavingActionEnum(Exception ex) {
    return (HintException.class.equals(ex.getClass()) && !isNull(ex.getCause())
               && DelegateNotAvailableException.class.equals(ex.getCause().getClass()))
        || InvalidArgumentsException.class.equals(ex.getClass());
  }
}
