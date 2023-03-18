/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.provision.terraformcloud.resources.service;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.delegate.task.terraformcloud.TerraformCloudTaskType.GET_ORGANIZATIONS;
import static io.harness.delegate.task.terraformcloud.TerraformCloudTaskType.GET_WORKSPACES;
import static io.harness.rule.OwnerRule.TMACARI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DelegateTaskRequest;
import io.harness.beans.IdentifierRef;
import io.harness.category.element.UnitTests;
import io.harness.cdng.provision.terraformcloud.resources.dtos.OrganizationsDTO;
import io.harness.cdng.provision.terraformcloud.resources.dtos.WorkspacesDTO;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.services.ConnectorService;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.terraformcloudconnector.TerraformCloudConnectorDTO;
import io.harness.delegate.beans.connector.terraformcloudconnector.TerraformCloudCredentialDTO;
import io.harness.delegate.beans.connector.terraformcloudconnector.TerraformCloudTokenCredentialsDTO;
import io.harness.delegate.task.terraformcloud.request.TerraformCloudGetOrganizationsTaskParams;
import io.harness.delegate.task.terraformcloud.request.TerraformCloudGetWorkspacesTaskParams;
import io.harness.delegate.task.terraformcloud.response.TerraformCloudOrganizationsTaskResponse;
import io.harness.delegate.task.terraformcloud.response.TerraformCloudWorkspacesTaskResponse;
import io.harness.encryption.Scope;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.TerraformCloudException;
import io.harness.exception.exceptionmanager.ExceptionManager;
import io.harness.logging.CommandExecutionStatus;
import io.harness.rule.Owner;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.service.DelegateGrpcClientWrapper;

import software.wings.beans.TaskType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(CDP)
public class TerraformCloudResourceServiceImplTest extends CategoryTest {
  private static final String ACCOUNT_IDENTIFIER = "accountIdentifier";
  private static final String ORG_IDENTIFIER = "orgIdentifier";
  private static final String PROJECT_IDENTIFIER = "projectIdentifier";
  private static final String CONNECTOR_IDENTIFIER = "connectorIdentifier";

  @Mock private ConnectorService connectorService;
  @Mock private SecretManagerClientService secretManagerClientService;
  @Mock private DelegateGrpcClientWrapper delegateGrpcClientWrapper;
  @Mock private ExceptionManager exceptionManager;
  @InjectMocks private TerraformCloudResourceServiceImpl service;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testGetOrganizations() {
    TerraformCloudConnectorDTO terraformCloudConnectorDTO =
        TerraformCloudConnectorDTO.builder()
            .credential(
                TerraformCloudCredentialDTO.builder().spec(mock(TerraformCloudTokenCredentialsDTO.class)).build())
            .delegateSelectors(new HashSet<>())
            .build();
    Map<String, String> organizationsResponse = Collections.singletonMap("id1", "org1");
    List<EncryptedDataDetail> encryptedDataDetails = new ArrayList<>();
    IdentifierRef connectorRef = IdentifierRef.builder()
                                     .accountIdentifier(ACCOUNT_IDENTIFIER)
                                     .orgIdentifier(ORG_IDENTIFIER)
                                     .projectIdentifier(PROJECT_IDENTIFIER)
                                     .identifier(CONNECTOR_IDENTIFIER)
                                     .build();
    ConnectorResponseDTO connectorResponseDTO = ConnectorResponseDTO.builder()
                                                    .connector(ConnectorInfoDTO.builder()
                                                                   .connectorType(ConnectorType.TERRAFORM_CLOUD)
                                                                   .connectorConfig(terraformCloudConnectorDTO)
                                                                   .build())
                                                    .build();
    doReturn(Optional.of(connectorResponseDTO)).when(connectorService).get(any(), any(), any(), any());
    doReturn(encryptedDataDetails).when(secretManagerClientService).getEncryptionDetails(any(), any());
    doReturn(TerraformCloudOrganizationsTaskResponse.builder()
                 .organizations(organizationsResponse)
                 .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                 .build())
        .when(delegateGrpcClientWrapper)
        .executeSyncTaskV2(any());
    ArgumentCaptor<DelegateTaskRequest> requestCaptor = ArgumentCaptor.forClass(DelegateTaskRequest.class);

    OrganizationsDTO organizations = service.getOrganizations(connectorRef, ORG_IDENTIFIER, PROJECT_IDENTIFIER);

    verify(connectorService).get(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, CONNECTOR_IDENTIFIER);
    verify(delegateGrpcClientWrapper).executeSyncTaskV2(requestCaptor.capture());
    verify(secretManagerClientService).getEncryptionDetails(any(), any());

    DelegateTaskRequest delegateTaskRequest = requestCaptor.getValue();
    TerraformCloudGetOrganizationsTaskParams taskParams =
        (TerraformCloudGetOrganizationsTaskParams) delegateTaskRequest.getTaskParameters();

    assertThat(delegateTaskRequest.getTaskType()).isEqualTo(TaskType.TERRAFORM_CLOUD_TASK_NG.name());
    assertThat(taskParams.getTaskType()).isEqualTo(GET_ORGANIZATIONS);
    assertThat(taskParams.getEncryptionDetails()).isEqualTo(encryptedDataDetails);
    assertThat(taskParams.getTerraformCloudConnectorDTO()).isEqualTo(terraformCloudConnectorDTO);
    assertThat(organizations.getOrganizations().size()).isEqualTo(1);
    assertThat(organizations.getOrganizations().get(0).getOrganizationId()).isEqualTo("id1");
    assertThat(organizations.getOrganizations().get(0).getOrganizationName()).isEqualTo("org1");
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testGetOrganizationsNonSuccess() {
    TerraformCloudConnectorDTO terraformCloudConnectorDTO =
        TerraformCloudConnectorDTO.builder()
            .credential(
                TerraformCloudCredentialDTO.builder().spec(mock(TerraformCloudTokenCredentialsDTO.class)).build())
            .delegateSelectors(new HashSet<>())
            .build();
    List<EncryptedDataDetail> encryptedDataDetails = new ArrayList<>();
    IdentifierRef connectorRef = IdentifierRef.builder().build();
    ConnectorResponseDTO connectorResponseDTO = ConnectorResponseDTO.builder()
                                                    .connector(ConnectorInfoDTO.builder()
                                                                   .connectorType(ConnectorType.TERRAFORM_CLOUD)
                                                                   .connectorConfig(terraformCloudConnectorDTO)
                                                                   .build())
                                                    .build();
    doReturn(Optional.of(connectorResponseDTO)).when(connectorService).get(any(), any(), any(), any());
    doReturn(encryptedDataDetails).when(secretManagerClientService).getEncryptionDetails(any(), any());
    doReturn(TerraformCloudOrganizationsTaskResponse.builder()
                 .organizations(new HashMap<>())
                 .errorSummary("errorSummary")
                 .commandExecutionStatus(CommandExecutionStatus.FAILURE)
                 .build())
        .when(delegateGrpcClientWrapper)
        .executeSyncTaskV2(any());

    assertThatThrownBy(() -> service.getOrganizations(connectorRef, ORG_IDENTIFIER, PROJECT_IDENTIFIER))
        .isInstanceOf(TerraformCloudException.class)
        .hasMessage("Failed to get terraform cloud organizations: errorSummary");
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testGetOrganizationsErrorResponse() {
    TerraformCloudConnectorDTO terraformCloudConnectorDTO =
        TerraformCloudConnectorDTO.builder()
            .credential(
                TerraformCloudCredentialDTO.builder().spec(mock(TerraformCloudTokenCredentialsDTO.class)).build())
            .delegateSelectors(new HashSet<>())
            .build();
    List<EncryptedDataDetail> encryptedDataDetails = new ArrayList<>();
    IdentifierRef connectorRef = IdentifierRef.builder().build();
    ConnectorResponseDTO connectorResponseDTO = ConnectorResponseDTO.builder()
                                                    .connector(ConnectorInfoDTO.builder()
                                                                   .connectorType(ConnectorType.TERRAFORM_CLOUD)
                                                                   .connectorConfig(terraformCloudConnectorDTO)
                                                                   .build())
                                                    .build();
    doReturn(Optional.of(connectorResponseDTO)).when(connectorService).get(any(), any(), any(), any());
    doReturn(encryptedDataDetails).when(secretManagerClientService).getEncryptionDetails(any(), any());
    doReturn(ErrorNotifyResponseData.builder().errorMessage("errorMessage").build())
        .when(delegateGrpcClientWrapper)
        .executeSyncTaskV2(any());

    assertThatThrownBy(() -> service.getOrganizations(connectorRef, ORG_IDENTIFIER, PROJECT_IDENTIFIER))
        .isInstanceOf(TerraformCloudException.class)
        .hasMessage("Failed to get terraform cloud organizations: errorMessage");
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testGetWorkspaces() {
    TerraformCloudConnectorDTO terraformCloudConnectorDTO =
        TerraformCloudConnectorDTO.builder()
            .credential(
                TerraformCloudCredentialDTO.builder().spec(mock(TerraformCloudTokenCredentialsDTO.class)).build())
            .delegateSelectors(new HashSet<>())
            .build();
    Map<String, String> workspacesResponse = new HashMap<>();
    workspacesResponse.put("id1", "ws1");
    workspacesResponse.put("id2", "ws2");
    List<EncryptedDataDetail> encryptedDataDetails = new ArrayList<>();
    IdentifierRef connectorRef = IdentifierRef.builder()
                                     .accountIdentifier(ACCOUNT_IDENTIFIER)
                                     .orgIdentifier(ORG_IDENTIFIER)
                                     .projectIdentifier(PROJECT_IDENTIFIER)
                                     .identifier(CONNECTOR_IDENTIFIER)
                                     .build();
    ConnectorResponseDTO connectorResponseDTO = ConnectorResponseDTO.builder()
                                                    .connector(ConnectorInfoDTO.builder()
                                                                   .connectorType(ConnectorType.TERRAFORM_CLOUD)
                                                                   .connectorConfig(terraformCloudConnectorDTO)
                                                                   .build())
                                                    .build();
    doReturn(Optional.of(connectorResponseDTO)).when(connectorService).get(any(), any(), any(), any());
    doReturn(encryptedDataDetails).when(secretManagerClientService).getEncryptionDetails(any(), any());
    doReturn(TerraformCloudWorkspacesTaskResponse.builder()
                 .workspaces(workspacesResponse)
                 .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                 .build())
        .when(delegateGrpcClientWrapper)
        .executeSyncTaskV2(any());
    ArgumentCaptor<DelegateTaskRequest> requestCaptor = ArgumentCaptor.forClass(DelegateTaskRequest.class);

    WorkspacesDTO workspaces = service.getWorkspaces(connectorRef, ORG_IDENTIFIER, PROJECT_IDENTIFIER, "tfCloudOrg");

    verify(connectorService).get(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, CONNECTOR_IDENTIFIER);
    verify(delegateGrpcClientWrapper).executeSyncTaskV2(requestCaptor.capture());
    verify(secretManagerClientService).getEncryptionDetails(any(), any());

    DelegateTaskRequest delegateTaskRequest = requestCaptor.getValue();
    TerraformCloudGetWorkspacesTaskParams taskParams =
        (TerraformCloudGetWorkspacesTaskParams) delegateTaskRequest.getTaskParameters();

    assertThat(delegateTaskRequest.getTaskType()).isEqualTo(TaskType.TERRAFORM_CLOUD_TASK_NG.name());
    assertThat(taskParams.getTaskType()).isEqualTo(GET_WORKSPACES);
    assertThat(taskParams.getOrganization()).isEqualTo("tfCloudOrg");
    assertThat(taskParams.getEncryptionDetails()).isEqualTo(encryptedDataDetails);
    assertThat(taskParams.getTerraformCloudConnectorDTO()).isEqualTo(terraformCloudConnectorDTO);
    assertThat(workspaces.getWorkspaces().size()).isEqualTo(2);
    assertThat(workspaces.getWorkspaces().stream().anyMatch(
                   workspace -> workspace.getWorkspaceId().equals("id1") && workspace.getWorkspaceName().equals("ws1")))
        .isTrue();
    assertThat(workspaces.getWorkspaces().stream().anyMatch(
                   workspace -> workspace.getWorkspaceId().equals("id2") && workspace.getWorkspaceName().equals("ws2")))
        .isTrue();
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testGetWorkspacesNonSuccess() {
    TerraformCloudConnectorDTO terraformCloudConnectorDTO =
        TerraformCloudConnectorDTO.builder()
            .credential(
                TerraformCloudCredentialDTO.builder().spec(mock(TerraformCloudTokenCredentialsDTO.class)).build())
            .delegateSelectors(new HashSet<>())
            .build();

    List<EncryptedDataDetail> encryptedDataDetails = new ArrayList<>();
    IdentifierRef connectorRef = IdentifierRef.builder().build();
    ConnectorResponseDTO connectorResponseDTO = ConnectorResponseDTO.builder()
                                                    .connector(ConnectorInfoDTO.builder()
                                                                   .connectorType(ConnectorType.TERRAFORM_CLOUD)
                                                                   .connectorConfig(terraformCloudConnectorDTO)
                                                                   .build())
                                                    .build();
    doReturn(Optional.of(connectorResponseDTO)).when(connectorService).get(any(), any(), any(), any());
    doReturn(encryptedDataDetails).when(secretManagerClientService).getEncryptionDetails(any(), any());
    doReturn(TerraformCloudWorkspacesTaskResponse.builder()
                 .workspaces(new HashMap<>())
                 .errorSummary("errorSummary")
                 .commandExecutionStatus(CommandExecutionStatus.FAILURE)
                 .build())
        .when(delegateGrpcClientWrapper)
        .executeSyncTaskV2(any());
    ArgumentCaptor<DelegateTaskRequest> requestCaptor = ArgumentCaptor.forClass(DelegateTaskRequest.class);

    assertThatThrownBy(() -> service.getWorkspaces(connectorRef, ORG_IDENTIFIER, PROJECT_IDENTIFIER, "tfCloudOrg"))
        .isInstanceOf(TerraformCloudException.class)
        .hasMessage("Failed to get terraform cloud workspaces: errorSummary");
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testGetWorkspacesErrorResponse() {
    TerraformCloudConnectorDTO terraformCloudConnectorDTO =
        TerraformCloudConnectorDTO.builder()
            .credential(
                TerraformCloudCredentialDTO.builder().spec(mock(TerraformCloudTokenCredentialsDTO.class)).build())
            .delegateSelectors(new HashSet<>())
            .build();

    List<EncryptedDataDetail> encryptedDataDetails = new ArrayList<>();
    IdentifierRef connectorRef = IdentifierRef.builder().build();
    ConnectorResponseDTO connectorResponseDTO = ConnectorResponseDTO.builder()
                                                    .connector(ConnectorInfoDTO.builder()
                                                                   .connectorType(ConnectorType.TERRAFORM_CLOUD)
                                                                   .connectorConfig(terraformCloudConnectorDTO)
                                                                   .build())
                                                    .build();
    doReturn(Optional.of(connectorResponseDTO)).when(connectorService).get(any(), any(), any(), any());
    doReturn(encryptedDataDetails).when(secretManagerClientService).getEncryptionDetails(any(), any());
    doReturn(ErrorNotifyResponseData.builder().errorMessage("errorMessage").build())
        .when(delegateGrpcClientWrapper)
        .executeSyncTaskV2(any());

    assertThatThrownBy(() -> service.getWorkspaces(connectorRef, ORG_IDENTIFIER, PROJECT_IDENTIFIER, "tfCloudOrg"))
        .isInstanceOf(TerraformCloudException.class)
        .hasMessage("Failed to get terraform cloud workspaces: errorMessage");
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testGetWorkspacesInvalidConnector() {
    IdentifierRef connectorRef = IdentifierRef.builder()
                                     .accountIdentifier(ACCOUNT_IDENTIFIER)
                                     .orgIdentifier(ORG_IDENTIFIER)
                                     .projectIdentifier(PROJECT_IDENTIFIER)
                                     .identifier(CONNECTOR_IDENTIFIER)
                                     .scope(Scope.ACCOUNT)
                                     .build();
    ConnectorResponseDTO connectorResponseDTO = ConnectorResponseDTO.builder()
                                                    .connector(ConnectorInfoDTO.builder()
                                                                   .connectorType(ConnectorType.AWS)
                                                                   .connectorConfig(AwsConnectorDTO.builder().build())
                                                                   .build())
                                                    .build();
    doReturn(Optional.of(connectorResponseDTO)).when(connectorService).get(any(), any(), any(), any());

    assertThatThrownBy(() -> service.getWorkspaces(connectorRef, ORG_IDENTIFIER, PROJECT_IDENTIFIER, "tfCloudOrg"))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Connector not found for identifier : [connectorIdentifier] with scope: [ACCOUNT]");
  }
}
