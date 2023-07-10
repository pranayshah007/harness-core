/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.servicenow.resources.service;

import static io.harness.rule.OwnerRule.NAMANG;
import static io.harness.rule.OwnerRule.PRABU;
import static io.harness.rule.OwnerRule.vivekveman;

import static junit.framework.TestCase.assertTrue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.beans.DecryptableEntity;
import io.harness.beans.DelegateTaskRequest;
import io.harness.beans.IdentifierRef;
import io.harness.category.element.UnitTests;
import io.harness.cdng.featureFlag.CDFeatureFlagHelper;
import io.harness.common.NGTaskType;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.services.ConnectorService;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.servicenow.ServiceNowAuthCredentialsDTO;
import io.harness.delegate.beans.connector.servicenow.ServiceNowAuthType;
import io.harness.delegate.beans.connector.servicenow.ServiceNowAuthenticationDTO;
import io.harness.delegate.beans.connector.servicenow.ServiceNowConnectorDTO;
import io.harness.delegate.beans.connector.servicenow.ServiceNowUserNamePasswordDTO;
import io.harness.delegate.task.servicenow.ServiceNowTaskNGParameters;
import io.harness.delegate.task.servicenow.ServiceNowTaskNGResponse;
import io.harness.encryption.SecretRefData;
import io.harness.exception.DelegateNotAvailableException;
import io.harness.exception.DelegateServiceDriverException;
import io.harness.exception.HintException;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.exception.exceptionmanager.exceptionhandler.DocumentLinksConstants;
import io.harness.rule.Owner;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.service.DelegateGrpcClientWrapper;
import io.harness.servicenow.ServiceNowActionNG;
import io.harness.servicenow.ServiceNowFieldNG;
import io.harness.servicenow.ServiceNowFieldSchemaNG;
import io.harness.servicenow.ServiceNowFieldTypeNG;
import io.harness.servicenow.ServiceNowStagingTable;
import io.harness.servicenow.ServiceNowTemplate;
import io.harness.servicenow.ServiceNowTicketTypeDTO;
import io.harness.servicenow.ServiceNowTicketTypeNG;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class ServiceNowResourceServiceTest extends CategoryTest {
  private static final String ACCOUNT_ID = "accountId";
  private static final String ORG_IDENTIFIER = "orgIdentifier";
  private static final String PROJECT_IDENTIFIER = "projectIdentifier";
  private static final String IDENTIFIER = "identifier";
  private static final String TEMPLATE_NAME = "TEMPLATE_NAME";
  private static final IdentifierRef identifierRef = IdentifierRef.builder()
                                                         .accountIdentifier(ACCOUNT_ID)
                                                         .identifier(IDENTIFIER)
                                                         .projectIdentifier(PROJECT_IDENTIFIER)
                                                         .orgIdentifier(ORG_IDENTIFIER)
                                                         .build();
  private static final ObjectMapper mapper = new ObjectMapper();
  @Mock ConnectorService connectorService;
  @Mock SecretManagerClientService secretManagerClientService;
  @Mock DelegateGrpcClientWrapper delegateGrpcClientWrapper;
  @Mock CDFeatureFlagHelper cdFeatureFlagHelper;

  @InjectMocks @Inject ServiceNowResourceServiceImpl serviceNowResourceService;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    when(connectorService.get(any(), any(), any(), any())).thenReturn(Optional.of(getConnector(false)));
    when(secretManagerClientService.getEncryptionDetails(any(), any()))
        .thenReturn(Lists.newArrayList(EncryptedDataDetail.builder().build()));
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void testGetIssueCreateMeta() {
    when(cdFeatureFlagHelper.isEnabled(any(), any())).thenReturn(false);
    List<ServiceNowFieldNG> serviceNowFieldNGList =
        Arrays.asList(ServiceNowFieldNG.builder().name("name1").key("key1").internalType("string").build(),
            ServiceNowFieldNG.builder().name("name2").key("key2").internalType("unknown_xyz").build());
    List<ServiceNowFieldNG> serviceNowFieldNGListExpected =
        Arrays.asList(ServiceNowFieldNG.builder()
                          .name("name1")
                          .key("key1")
                          .internalType(null)
                          .schema(ServiceNowFieldSchemaNG.builder()
                                      .array(false)
                                      .customType(null)
                                      .typeStr("string")
                                      .type(ServiceNowFieldTypeNG.STRING)
                                      .build())
                          .build(),
            ServiceNowFieldNG.builder()
                .name("name2")
                .key("key2")
                .internalType(null)
                .schema(ServiceNowFieldSchemaNG.builder()
                            .array(false)
                            .customType(null)
                            .typeStr(null)
                            .type(ServiceNowFieldTypeNG.UNKNOWN)
                            .build())
                .build());
    when(delegateGrpcClientWrapper.executeSyncTaskV2(any()))
        .thenReturn(ServiceNowTaskNGResponse.builder().serviceNowFieldNGList(serviceNowFieldNGList).build());
    List<ServiceNowFieldNG> serviceNowFieldNGListReturn = serviceNowResourceService.getIssueCreateMetadata(
        identifierRef, ORG_IDENTIFIER, PROJECT_IDENTIFIER, "CHANGE_REQUEST");
    assertTrue(serviceNowFieldNGListReturn.size() == serviceNowFieldNGListExpected.size()
        && serviceNowFieldNGListReturn.containsAll(serviceNowFieldNGListExpected)
        && serviceNowFieldNGListExpected.containsAll(serviceNowFieldNGListReturn));
    ArgumentCaptor<DelegateTaskRequest> requestArgumentCaptor = ArgumentCaptor.forClass(DelegateTaskRequest.class);
    verify(delegateGrpcClientWrapper).executeSyncTaskV2(requestArgumentCaptor.capture());
    assertThat(requestArgumentCaptor.getValue().getTaskType()).isEqualTo(NGTaskType.SERVICENOW_TASK_NG.name());
    assertThat(requestArgumentCaptor.getValue().getAccountId()).isEqualTo(ACCOUNT_ID);
    ServiceNowTaskNGParameters parameters =
        (ServiceNowTaskNGParameters) requestArgumentCaptor.getValue().getTaskParameters();
    assertThat(parameters.getAction()).isEqualTo(ServiceNowActionNG.GET_TICKET_CREATE_METADATA);
    assertThat(parameters.getTicketType()).isEqualTo("CHANGE_REQUEST");
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testGetIssueCreateMetaWhenDelegateResponseNotHaveType() {
    when(cdFeatureFlagHelper.isEnabled(any(), any())).thenReturn(false);
    ServiceNowFieldNG field1 = ServiceNowFieldNG.builder().name("name1").key("key1").build();
    ServiceNowFieldNG field2 = ServiceNowFieldNG.builder().name("name2").key("key2").build();
    List<ServiceNowFieldNG> serviceNowFieldNGList = Arrays.asList(field1, field2);
    List<ServiceNowFieldNG> serviceNowFieldNGListExpected = Arrays.asList(
        ServiceNowFieldNG.builder()
            .name("name1")
            .key("key1")
            .internalType(null)
            .schema(ServiceNowFieldSchemaNG.builder().array(false).customType(null).typeStr(null).type(null).build())
            .build(),
        ServiceNowFieldNG.builder()
            .name("name2")
            .key("key2")
            .internalType(null)
            .schema(ServiceNowFieldSchemaNG.builder().array(false).customType(null).typeStr(null).type(null).build())
            .build());
    when(delegateGrpcClientWrapper.executeSyncTaskV2(any()))
        .thenReturn(ServiceNowTaskNGResponse.builder().serviceNowFieldNGList(serviceNowFieldNGList).build());
    List<ServiceNowFieldNG> serviceNowFieldNGListReturn = serviceNowResourceService.getIssueCreateMetadata(
        identifierRef, ORG_IDENTIFIER, PROJECT_IDENTIFIER, "CHANGE_REQUEST");
    assertTrue(serviceNowFieldNGListReturn.size() == serviceNowFieldNGListExpected.size()
        && serviceNowFieldNGListReturn.containsAll(serviceNowFieldNGListExpected)
        && serviceNowFieldNGListExpected.containsAll(serviceNowFieldNGListReturn));
    ArgumentCaptor<DelegateTaskRequest> requestArgumentCaptor = ArgumentCaptor.forClass(DelegateTaskRequest.class);
    verify(delegateGrpcClientWrapper).executeSyncTaskV2(requestArgumentCaptor.capture());
    assertThat(requestArgumentCaptor.getValue().getTaskType()).isEqualTo(NGTaskType.SERVICENOW_TASK_NG.name());
    assertThat(requestArgumentCaptor.getValue().getAccountId()).isEqualTo(ACCOUNT_ID);
    ServiceNowTaskNGParameters parameters =
        (ServiceNowTaskNGParameters) requestArgumentCaptor.getValue().getTaskParameters();
    assertThat(parameters.getAction()).isEqualTo(ServiceNowActionNG.GET_TICKET_CREATE_METADATA);
    assertThat(parameters.getTicketType()).isEqualTo("CHANGE_REQUEST");
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testGetIssueCreateMetaWhenDelegateResponseNotHaveTypeAndFFOn() throws IOException {
    when(cdFeatureFlagHelper.isEnabled(any(), any())).thenReturn(true);
    JsonNode columnsResponse = readResource("servicenow/resources/service/serviceNowMetadataResponse.json");

    when(delegateGrpcClientWrapper.executeSyncTaskV2(any()))
        .thenReturn(
            ServiceNowTaskNGResponse.builder().serviceNowFieldJsonNGListAsString(columnsResponse.toString()).build());
    List<ServiceNowFieldNG> serviceNowFieldNGListResponse =
        serviceNowResourceService.getMetadata(identifierRef, ORG_IDENTIFIER, PROJECT_IDENTIFIER, "CHANGE_TASK");
    assertThat(serviceNowFieldNGListResponse.size()).isEqualTo(8);
    for (ServiceNowFieldNG serviceNowField : serviceNowFieldNGListResponse) {
      if ("sys_id".equals(serviceNowField.getKey())) {
        assertThat(serviceNowField.isRequired()).isFalse();
      }
    }
    ArgumentCaptor<DelegateTaskRequest> requestArgumentCaptor = ArgumentCaptor.forClass(DelegateTaskRequest.class);
    verify(delegateGrpcClientWrapper).executeSyncTaskV2(requestArgumentCaptor.capture());
    ServiceNowTaskNGParameters parameters =
        (ServiceNowTaskNGParameters) requestArgumentCaptor.getValue().getTaskParameters();
    assertThat(parameters.getAction()).isEqualTo(ServiceNowActionNG.GET_METADATA_V2);
    assertThat(parameters.getTicketType()).isEqualTo("CHANGE_TASK");
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testGetIssueCreateMetaWhenDelegateResponseNotHaveTypeAndFFOnWhenKryoError() {
    when(cdFeatureFlagHelper.isEnabled(any(), any())).thenReturn(true);
    ServiceNowFieldNG field1 = ServiceNowFieldNG.builder().name("name1").key("key1").build();
    ServiceNowFieldNG field2 = ServiceNowFieldNG.builder().name("name2").key("key2").build();
    List<ServiceNowFieldNG> serviceNowFieldNGList = Arrays.asList(field1, field2);
    List<ServiceNowFieldNG> serviceNowFieldNGListExpected = Arrays.asList(
        ServiceNowFieldNG.builder()
            .name("name1")
            .key("key1")
            .internalType(null)
            .schema(ServiceNowFieldSchemaNG.builder().array(false).customType(null).typeStr(null).type(null).build())
            .build(),
        ServiceNowFieldNG.builder()
            .name("name2")
            .key("key2")
            .internalType(null)
            .schema(ServiceNowFieldSchemaNG.builder().array(false).customType(null).typeStr(null).type(null).build())
            .build());
    when(delegateGrpcClientWrapper.executeSyncTaskV2(any()))
        .thenThrow(new HintException(
            "kryo exception occurred", new DelegateNotAvailableException("Delegate might not be available")))
        .thenReturn(ServiceNowTaskNGResponse.builder().serviceNowFieldNGList(serviceNowFieldNGList).build());
    List<ServiceNowFieldNG> serviceNowFieldNGListReturn = serviceNowResourceService.getIssueCreateMetadata(
        identifierRef, ORG_IDENTIFIER, PROJECT_IDENTIFIER, "CHANGE_REQUEST");
    assertTrue(serviceNowFieldNGListReturn.size() == serviceNowFieldNGListExpected.size()
        && serviceNowFieldNGListReturn.containsAll(serviceNowFieldNGListExpected)
        && serviceNowFieldNGListExpected.containsAll(serviceNowFieldNGListReturn));
    ArgumentCaptor<DelegateTaskRequest> requestArgumentCaptor = ArgumentCaptor.forClass(DelegateTaskRequest.class);
    verify(delegateGrpcClientWrapper, times(2)).executeSyncTaskV2(requestArgumentCaptor.capture());
    assertThat(requestArgumentCaptor.getValue().getTaskType()).isEqualTo(NGTaskType.SERVICENOW_TASK_NG.name());
    assertThat(requestArgumentCaptor.getValue().getAccountId()).isEqualTo(ACCOUNT_ID);
    ServiceNowTaskNGParameters parameters =
        (ServiceNowTaskNGParameters) requestArgumentCaptor.getAllValues().get(0).getTaskParameters();
    assertThat(parameters.getAction()).isEqualTo(ServiceNowActionNG.GET_METADATA_V2);
    assertThat(parameters.getTicketType()).isEqualTo("CHANGE_REQUEST");
    parameters = (ServiceNowTaskNGParameters) requestArgumentCaptor.getAllValues().get(1).getTaskParameters();
    assertThat(parameters.getAction()).isEqualTo(ServiceNowActionNG.GET_TICKET_CREATE_METADATA);
    assertThat(parameters.getTicketType()).isEqualTo("CHANGE_REQUEST");
  }

  private ConnectorResponseDTO getConnector(boolean updatedYAML) {
    if (updatedYAML) {
      ConnectorInfoDTO connectorInfoDTO =
          ConnectorInfoDTO.builder()
              .connectorType(ConnectorType.SERVICENOW)
              .connectorConfig(ServiceNowConnectorDTO.builder()
                                   .serviceNowUrl("url")
                                   .username("username")
                                   .passwordRef(SecretRefData.builder().build())
                                   .auth(ServiceNowAuthenticationDTO.builder()
                                             .authType(ServiceNowAuthType.USER_PASSWORD)
                                             .credentials(ServiceNowUserNamePasswordDTO.builder()
                                                              .username("username")
                                                              .passwordRef(SecretRefData.builder().build())
                                                              .build())
                                             .build())
                                   .build())
              .build();
      return ConnectorResponseDTO.builder().connector(connectorInfoDTO).build();
    }
    ConnectorInfoDTO connectorInfoDTO = ConnectorInfoDTO.builder()
                                            .connectorType(ConnectorType.SERVICENOW)
                                            .connectorConfig(ServiceNowConnectorDTO.builder()
                                                                 .serviceNowUrl("url")
                                                                 .username("username")
                                                                 .passwordRef(SecretRefData.builder().build())
                                                                 .build())
                                            .build();
    return ConnectorResponseDTO.builder().connector(connectorInfoDTO).build();
  }

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testGetMetadata() {
    when(cdFeatureFlagHelper.isEnabled(any(), any())).thenReturn(false);
    List<ServiceNowFieldNG> serviceNowFieldNGList =
        Arrays.asList(ServiceNowFieldNG.builder().name("name1").key("key1").build(),
            ServiceNowFieldNG.builder().name("name2").key("key2").build());
    when(delegateGrpcClientWrapper.executeSyncTaskV2(any()))
        .thenReturn(ServiceNowTaskNGResponse.builder().serviceNowFieldNGList(serviceNowFieldNGList).build());
    assertThat(serviceNowResourceService.getMetadata(identifierRef, ORG_IDENTIFIER, PROJECT_IDENTIFIER, "CHANGE_TASK"))
        .isEqualTo(serviceNowFieldNGList);
    ArgumentCaptor<DelegateTaskRequest> requestArgumentCaptor = ArgumentCaptor.forClass(DelegateTaskRequest.class);
    verify(delegateGrpcClientWrapper).executeSyncTaskV2(requestArgumentCaptor.capture());
    ServiceNowTaskNGParameters parameters =
        (ServiceNowTaskNGParameters) requestArgumentCaptor.getValue().getTaskParameters();
    assertThat(parameters.getAction()).isEqualTo(ServiceNowActionNG.GET_METADATA);
    assertThat(parameters.getTicketType()).isEqualTo("CHANGE_TASK");
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testGetMetadataWhenFFEnabled() throws IOException {
    when(cdFeatureFlagHelper.isEnabled(any(), any())).thenReturn(true);
    JsonNode columnsResponse = readResource("servicenow/resources/service/serviceNowMetadataResponse.json");

    when(delegateGrpcClientWrapper.executeSyncTaskV2(any()))
        .thenReturn(
            ServiceNowTaskNGResponse.builder().serviceNowFieldJsonNGListAsString(columnsResponse.toString()).build());
    List<ServiceNowFieldNG> serviceNowFieldNGListResponse =
        serviceNowResourceService.getMetadata(identifierRef, ORG_IDENTIFIER, PROJECT_IDENTIFIER, "CHANGE_TASK");
    assertThat(serviceNowFieldNGListResponse.size()).isEqualTo(8);
    for (ServiceNowFieldNG serviceNowField : serviceNowFieldNGListResponse) {
      if ("sys_id".equals(serviceNowField.getKey())) {
        assertThat(serviceNowField.isRequired()).isFalse();
      }
    }
    ArgumentCaptor<DelegateTaskRequest> requestArgumentCaptor = ArgumentCaptor.forClass(DelegateTaskRequest.class);
    verify(delegateGrpcClientWrapper).executeSyncTaskV2(requestArgumentCaptor.capture());
    ServiceNowTaskNGParameters parameters =
        (ServiceNowTaskNGParameters) requestArgumentCaptor.getValue().getTaskParameters();
    assertThat(parameters.getAction()).isEqualTo(ServiceNowActionNG.GET_METADATA_V2);
    assertThat(parameters.getTicketType()).isEqualTo("CHANGE_TASK");
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testGetMetadataWhenFFEnabledWhenV2ThrowsKryoError() {
    when(cdFeatureFlagHelper.isEnabled(any(), any())).thenReturn(true);
    List<ServiceNowFieldNG> serviceNowFieldNGList =
        Arrays.asList(ServiceNowFieldNG.builder().name("name1").key("key1").build(),
            ServiceNowFieldNG.builder().name("name2").key("key2").build());

    when(delegateGrpcClientWrapper.executeSyncTaskV2(any()))
        .thenThrow(new InvalidArgumentsException("The task got expired or not picked up"))
        .thenReturn(ServiceNowTaskNGResponse.builder().serviceNowFieldNGList(serviceNowFieldNGList).build());
    List<ServiceNowFieldNG> serviceNowFieldNGListResponse =
        serviceNowResourceService.getMetadata(identifierRef, ORG_IDENTIFIER, PROJECT_IDENTIFIER, "CHANGE_TASK");
    assertThat(serviceNowFieldNGListResponse).isEqualTo(serviceNowFieldNGList);

    ArgumentCaptor<DelegateTaskRequest> requestArgumentCaptor = ArgumentCaptor.forClass(DelegateTaskRequest.class);
    verify(delegateGrpcClientWrapper, times(2)).executeSyncTaskV2(requestArgumentCaptor.capture());
    ServiceNowTaskNGParameters parameters =
        (ServiceNowTaskNGParameters) requestArgumentCaptor.getAllValues().get(0).getTaskParameters();
    assertThat(parameters.getAction()).isEqualTo(ServiceNowActionNG.GET_METADATA_V2);
    assertThat(parameters.getTicketType()).isEqualTo("CHANGE_TASK");
    parameters = (ServiceNowTaskNGParameters) requestArgumentCaptor.getAllValues().get(1).getTaskParameters();
    assertThat(parameters.getAction()).isEqualTo(ServiceNowActionNG.GET_METADATA);
    assertThat(parameters.getTicketType()).isEqualTo("CHANGE_TASK");
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testGetMetadataWhenFFEnabledWhenV2TaskExpired() {
    when(cdFeatureFlagHelper.isEnabled(any(), any())).thenReturn(true);
    List<ServiceNowFieldNG> serviceNowFieldNGList =
        Arrays.asList(ServiceNowFieldNG.builder().name("name1").key("key1").build(),
            ServiceNowFieldNG.builder().name("name2").key("key2").build());

    when(delegateGrpcClientWrapper.executeSyncTaskV2(any()))
        .thenThrow(new HintException(
            "Kryo exception no enum constant", new DelegateNotAvailableException("Delegate might be not available")))
        .thenReturn(ServiceNowTaskNGResponse.builder().serviceNowFieldNGList(serviceNowFieldNGList).build());
    List<ServiceNowFieldNG> serviceNowFieldNGListResponse =
        serviceNowResourceService.getMetadata(identifierRef, ORG_IDENTIFIER, PROJECT_IDENTIFIER, "CHANGE_TASK");
    assertThat(serviceNowFieldNGListResponse).isEqualTo(serviceNowFieldNGList);

    ArgumentCaptor<DelegateTaskRequest> requestArgumentCaptor = ArgumentCaptor.forClass(DelegateTaskRequest.class);
    verify(delegateGrpcClientWrapper, times(2)).executeSyncTaskV2(requestArgumentCaptor.capture());
    ServiceNowTaskNGParameters parameters =
        (ServiceNowTaskNGParameters) requestArgumentCaptor.getAllValues().get(0).getTaskParameters();
    assertThat(parameters.getAction()).isEqualTo(ServiceNowActionNG.GET_METADATA_V2);
    assertThat(parameters.getTicketType()).isEqualTo("CHANGE_TASK");
    parameters = (ServiceNowTaskNGParameters) requestArgumentCaptor.getAllValues().get(1).getTaskParameters();
    assertThat(parameters.getAction()).isEqualTo(ServiceNowActionNG.GET_METADATA);
    assertThat(parameters.getTicketType()).isEqualTo("CHANGE_TASK");
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testGetMetadataWhenFFEnabledWhenRandomError() {
    when(cdFeatureFlagHelper.isEnabled(any(), any())).thenReturn(true);
    List<ServiceNowFieldNG> serviceNowFieldNGList =
        Arrays.asList(ServiceNowFieldNG.builder().name("name1").key("key1").build(),
            ServiceNowFieldNG.builder().name("name2").key("key2").build());

    when(delegateGrpcClientWrapper.executeSyncTaskV2(any())).thenThrow(new InvalidRequestException("random"));
    assertThatThrownBy(
        () -> serviceNowResourceService.getMetadata(identifierRef, ORG_IDENTIFIER, PROJECT_IDENTIFIER, "CHANGE_TASK"))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testgetTemplateList() {
    List<ServiceNowTemplate> serviceNowFieldNGList1 =
        Arrays.asList(ServiceNowTemplate.builder().name("name1").sys_id("key1").build(),
            ServiceNowTemplate.builder().name("name2").sys_id("key2").build());
    when(delegateGrpcClientWrapper.executeSyncTaskV2(any()))
        .thenReturn(ServiceNowTaskNGResponse.builder().serviceNowTemplateList(serviceNowFieldNGList1).build());
    assertThat(serviceNowResourceService.getTemplateList(
                   identifierRef, ORG_IDENTIFIER, PROJECT_IDENTIFIER, 0, 0, TEMPLATE_NAME, "CHANGE_TASK"))
        .isEqualTo(serviceNowFieldNGList1);
    ArgumentCaptor<DelegateTaskRequest> requestArgumentCaptor = ArgumentCaptor.forClass(DelegateTaskRequest.class);
    verify(delegateGrpcClientWrapper).executeSyncTaskV2(requestArgumentCaptor.capture());
    ServiceNowTaskNGParameters parameters =
        (ServiceNowTaskNGParameters) requestArgumentCaptor.getValue().getTaskParameters();
    assertThat(parameters.getAction()).isEqualTo(ServiceNowActionNG.GET_TEMPLATE);
    assertThat(parameters.getTicketType()).isEqualTo("CHANGE_TASK");
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testGetStagingTableList() {
    List<ServiceNowStagingTable> serviceNowStagingTableList =
        Arrays.asList(ServiceNowStagingTable.builder().name("name1").label("label1").build(),
            ServiceNowStagingTable.builder().name("name2").label("label2").build());
    when(delegateGrpcClientWrapper.executeSyncTaskV2(any()))
        .thenReturn(ServiceNowTaskNGResponse.builder().serviceNowStagingTableList(serviceNowStagingTableList).build());
    assertThat(serviceNowResourceService.getStagingTableList(identifierRef, ORG_IDENTIFIER, PROJECT_IDENTIFIER))
        .isEqualTo(serviceNowStagingTableList);
    ArgumentCaptor<DelegateTaskRequest> requestArgumentCaptor = ArgumentCaptor.forClass(DelegateTaskRequest.class);
    ArgumentCaptor<DecryptableEntity> requestArgumentCaptorForSecretService =
        ArgumentCaptor.forClass(DecryptableEntity.class);
    verify(delegateGrpcClientWrapper).executeSyncTaskV2(requestArgumentCaptor.capture());
    ServiceNowTaskNGParameters parameters =
        (ServiceNowTaskNGParameters) requestArgumentCaptor.getValue().getTaskParameters();
    verify(secretManagerClientService).getEncryptionDetails(any(), requestArgumentCaptorForSecretService.capture());
    assertThat(requestArgumentCaptorForSecretService.getValue() instanceof ServiceNowConnectorDTO).isTrue();
    assertThat(parameters.getAction()).isEqualTo(ServiceNowActionNG.GET_IMPORT_SET_STAGING_TABLES);
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testGetStagingTableListWithUpdatedConnectorFlow() {
    List<ServiceNowStagingTable> serviceNowStagingTableList =
        Arrays.asList(ServiceNowStagingTable.builder().name("name1").label("label1").build(),
            ServiceNowStagingTable.builder().name("name2").label("label2").build());
    when(connectorService.get(any(), any(), any(), any())).thenReturn(Optional.of(getConnector(true)));
    when(delegateGrpcClientWrapper.executeSyncTaskV2(any()))
        .thenReturn(ServiceNowTaskNGResponse.builder().serviceNowStagingTableList(serviceNowStagingTableList).build());
    assertThat(serviceNowResourceService.getStagingTableList(identifierRef, ORG_IDENTIFIER, PROJECT_IDENTIFIER))
        .isEqualTo(serviceNowStagingTableList);
    ArgumentCaptor<DelegateTaskRequest> requestArgumentCaptor = ArgumentCaptor.forClass(DelegateTaskRequest.class);
    ArgumentCaptor<DecryptableEntity> requestArgumentCaptorForSecretService =
        ArgumentCaptor.forClass(DecryptableEntity.class);
    verify(delegateGrpcClientWrapper).executeSyncTaskV2(requestArgumentCaptor.capture());
    ServiceNowTaskNGParameters parameters =
        (ServiceNowTaskNGParameters) requestArgumentCaptor.getValue().getTaskParameters();
    verify(secretManagerClientService).getEncryptionDetails(any(), requestArgumentCaptorForSecretService.capture());
    assertThat(requestArgumentCaptorForSecretService.getValue() instanceof ServiceNowAuthCredentialsDTO).isTrue();
    assertThat(parameters.getAction()).isEqualTo(ServiceNowActionNG.GET_IMPORT_SET_STAGING_TABLES);
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testGetStagingTableListWhenDelegatesAreDown() {
    when(delegateGrpcClientWrapper.executeSyncTaskV2(any()))
        .thenThrow(new DelegateServiceDriverException("delegates not available"));
    assertThatThrownBy(
        () -> serviceNowResourceService.getStagingTableList(identifierRef, ORG_IDENTIFIER, PROJECT_IDENTIFIER))
        .isInstanceOf(HintException.class)
        .hasMessage(
            String.format(HintException.DELEGATE_NOT_AVAILABLE, DocumentLinksConstants.DELEGATE_INSTALLATION_LINK))
        .hasCause(new DelegateNotAvailableException(
            "Delegates are not available for performing servicenow operation.", WingsException.USER));
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testGetTicketTypesV2WithUpdatedConnectorFlow() throws JsonProcessingException {
    String ticketType1 = "        {\n"
        + "            \"name\": \"change_task\",\n"
        + "            \"label\": \"Change Task\"\n"
        + "        }";
    String ticketType2 = "        {\n"
        + "            \"name\": \"change_request_imac\"\n"
        + "        }";
    ServiceNowTicketTypeDTO ticketTypeDTO1 = new ServiceNowTicketTypeDTO(mapper.readTree(ticketType1));
    ServiceNowTicketTypeDTO ticketTypeDTO2 = new ServiceNowTicketTypeDTO(mapper.readTree(ticketType2));
    assertThat(ticketTypeDTO1.getKey()).isEqualTo("change_task");
    assertThat(ticketTypeDTO1.getName()).isEqualTo("Change Task");
    assertThat(ticketTypeDTO2.getKey()).isEqualTo("change_request_imac");
    assertThat(ticketTypeDTO2.getName()).isEqualTo("change_request_imac");
    List<ServiceNowTicketTypeDTO> serviceNowTicketTypeList = Arrays.asList(ticketTypeDTO1, ticketTypeDTO2);

    when(connectorService.get(any(), any(), any(), any())).thenReturn(Optional.of(getConnector(true)));
    when(delegateGrpcClientWrapper.executeSyncTaskV2(any()))
        .thenReturn(ServiceNowTaskNGResponse.builder().serviceNowTicketTypeList(serviceNowTicketTypeList).build());
    assertThat(serviceNowResourceService.getTicketTypesV2(identifierRef, ORG_IDENTIFIER, PROJECT_IDENTIFIER))
        .isEqualTo(serviceNowTicketTypeList);
    ArgumentCaptor<DelegateTaskRequest> requestArgumentCaptor = ArgumentCaptor.forClass(DelegateTaskRequest.class);

    verify(delegateGrpcClientWrapper).executeSyncTaskV2(requestArgumentCaptor.capture());
    ServiceNowTaskNGParameters parameters =
        (ServiceNowTaskNGParameters) requestArgumentCaptor.getValue().getTaskParameters();

    assertThat(parameters.getAction()).isEqualTo(ServiceNowActionNG.GET_TICKET_TYPES);
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testGetTicketTypesV2WithUpdatedConnectorFlowWhenKryo() {
    List<ServiceNowTicketTypeDTO> serviceNowStandardTicketTypeList =
        Arrays.stream(ServiceNowTicketTypeNG.values())
            .map(ticketType -> new ServiceNowTicketTypeDTO(ticketType.name(), ticketType.getDisplayName()))
            .collect(Collectors.toList());

    when(connectorService.get(any(), any(), any(), any())).thenReturn(Optional.of(getConnector(true)));
    when(delegateGrpcClientWrapper.executeSyncTaskV2(any()))
        .thenThrow(new InvalidArgumentsException("enum constant not available"));
    List<ServiceNowTicketTypeDTO> serviceNowTicketTypeListResponse =
        serviceNowResourceService.getTicketTypesV2(identifierRef, ORG_IDENTIFIER, PROJECT_IDENTIFIER);
    assertThat(serviceNowTicketTypeListResponse.size()).isEqualTo(4);
    assertTrue(serviceNowTicketTypeListResponse.containsAll(serviceNowStandardTicketTypeList));
    assertTrue(serviceNowStandardTicketTypeList.containsAll(serviceNowTicketTypeListResponse));
    ArgumentCaptor<DelegateTaskRequest> requestArgumentCaptor = ArgumentCaptor.forClass(DelegateTaskRequest.class);

    verify(delegateGrpcClientWrapper).executeSyncTaskV2(requestArgumentCaptor.capture());
    ServiceNowTaskNGParameters parameters =
        (ServiceNowTaskNGParameters) requestArgumentCaptor.getValue().getTaskParameters();

    assertThat(parameters.getAction()).isEqualTo(ServiceNowActionNG.GET_TICKET_TYPES);
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testGetTicketTypesV2WithUpdatedConnectorFlowWhenTaskExpired() {
    List<ServiceNowTicketTypeDTO> serviceNowStandardTicketTypeList =
        Arrays.stream(ServiceNowTicketTypeNG.values())
            .map(ticketType -> new ServiceNowTicketTypeDTO(ticketType.name(), ticketType.getDisplayName()))
            .collect(Collectors.toList());

    when(connectorService.get(any(), any(), any(), any())).thenReturn(Optional.of(getConnector(true)));
    when(delegateGrpcClientWrapper.executeSyncTaskV2(any()))
        .thenThrow(new HintException("Task was expired", new DelegateNotAvailableException("Delegates not available")));
    List<ServiceNowTicketTypeDTO> serviceNowTicketTypeListResponse =
        serviceNowResourceService.getTicketTypesV2(identifierRef, ORG_IDENTIFIER, PROJECT_IDENTIFIER);
    assertThat(serviceNowTicketTypeListResponse.size()).isEqualTo(4);
    assertTrue(serviceNowTicketTypeListResponse.containsAll(serviceNowStandardTicketTypeList));
    assertTrue(serviceNowStandardTicketTypeList.containsAll(serviceNowTicketTypeListResponse));
    ArgumentCaptor<DelegateTaskRequest> requestArgumentCaptor = ArgumentCaptor.forClass(DelegateTaskRequest.class);

    verify(delegateGrpcClientWrapper).executeSyncTaskV2(requestArgumentCaptor.capture());
    ServiceNowTaskNGParameters parameters =
        (ServiceNowTaskNGParameters) requestArgumentCaptor.getValue().getTaskParameters();

    assertThat(parameters.getAction()).isEqualTo(ServiceNowActionNG.GET_TICKET_TYPES);
  }

  private JsonNode readResource(String filePath) throws IOException {
    ClassLoader classLoader = this.getClass().getClassLoader();
    final URL jsonFile = classLoader.getResource(filePath);
    ObjectMapper mapper = new ObjectMapper();
    return mapper.readTree(jsonFile);
  }
}
