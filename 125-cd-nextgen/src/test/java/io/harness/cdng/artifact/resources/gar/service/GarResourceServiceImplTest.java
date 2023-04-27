/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.artifact.resources.gar.service;

import static io.harness.rule.OwnerRule.ABHISHEK;
import static io.harness.rule.OwnerRule.vivekveman;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DelegateTaskRequest;
import io.harness.beans.IdentifierRef;
import io.harness.category.element.UnitTests;
import io.harness.cdng.artifact.resources.googleartifactregistry.dtos.GARBuildDetailsDTO;
import io.harness.cdng.artifact.resources.googleartifactregistry.dtos.GARResponseDTO;
import io.harness.cdng.artifact.resources.googleartifactregistry.dtos.GarRequestDTO;
import io.harness.cdng.artifact.resources.googleartifactregistry.service.GARResourceServiceImpl;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.services.ConnectorService;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.delegate.beans.RemoteMethodReturnValueData;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorCredentialDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpDelegateDetailsDTO;
import io.harness.delegate.task.artifacts.ArtifactTaskType;
import io.harness.delegate.task.artifacts.gar.GarDelegateResponse;
import io.harness.delegate.task.artifacts.request.ArtifactTaskParameters;
import io.harness.delegate.task.artifacts.response.ArtifactBuildDetailsNG;
import io.harness.delegate.task.artifacts.response.ArtifactTaskExecutionResponse;
import io.harness.delegate.task.artifacts.response.ArtifactTaskResponse;
import io.harness.eraro.ErrorCode;
import io.harness.exception.ArtifactServerException;
import io.harness.exception.DelegateServiceDriverException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.exception.exceptionmanager.ExceptionManager;
import io.harness.logging.CommandExecutionStatus;
import io.harness.rule.Owner;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.service.DelegateGrpcClientWrapper;

import io.fabric8.utils.Lists;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

@OwnedBy(HarnessTeam.CDC)
public class GarResourceServiceImplTest extends CategoryTest {
  private static final String ACCOUNT_ID = "accountId";
  private static final String REGION = "imagePath";
  private static final String repositoryName = "repositoryName";
  private static final String project = "project";
  private static final String version = "version";
  private static final String pkg = "pkg";
  private static final String ORG_IDENTIFIER = "orgIdentifier";
  private static final String PROJECT_IDENTIFIER = "projectIdentifier";
  @Mock ConnectorService connectorService;
  @Mock SecretManagerClientService secretManagerClientService;
  @Mock DelegateGrpcClientWrapper delegateGrpcClientWrapper;
  @Spy @InjectMocks GARResourceServiceImpl garResourceService;
  @Mock ExceptionManager exceptionManager;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  private ConnectorResponseDTO getConnector() {
    GcpConnectorCredentialDTO gcpConnectorCredentialDTO = GcpConnectorCredentialDTO.builder().build();
    gcpConnectorCredentialDTO.setConfig(GcpDelegateDetailsDTO.builder().build());
    ConnectorInfoDTO connectorInfoDTO = ConnectorInfoDTO.builder()
                                            .connectorType(ConnectorType.GCP)
                                            .connectorConfig(GcpConnectorDTO.builder()
                                                                 .delegateSelectors(Collections.emptySet())
                                                                 .credential(gcpConnectorCredentialDTO)
                                                                 .build())
                                            .projectIdentifier("dummyProject")
                                            .orgIdentifier("dummyOrg")
                                            .build();
    return ConnectorResponseDTO.builder().connector(connectorInfoDTO).build();
  }
  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testGetBuildDetails() {
    IdentifierRef connectorRef = IdentifierRef.builder()
                                     .accountIdentifier(ACCOUNT_ID)
                                     .identifier("identifier")
                                     .projectIdentifier(PROJECT_IDENTIFIER)
                                     .orgIdentifier(ORG_IDENTIFIER)
                                     .build();
    ConnectorResponseDTO connectorResponse = getConnector();
    when(connectorService.get(ACCOUNT_ID, ORG_IDENTIFIER, PROJECT_IDENTIFIER, "identifier"))
        .thenReturn(Optional.of(connectorResponse));
    EncryptedDataDetail encryptedDataDetail = EncryptedDataDetail.builder().build();
    when(secretManagerClientService.getEncryptionDetails(any(), any()))
        .thenReturn(Lists.newArrayList(encryptedDataDetail));
    when(delegateGrpcClientWrapper.executeSyncTaskV2(any()))
        .thenReturn(
            ArtifactTaskResponse.builder()
                .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                .artifactTaskExecutionResponse(
                    ArtifactTaskExecutionResponse.builder().artifactDelegateResponses(new ArrayList<>()).build())
                .build());

    GARResponseDTO garResponseDTO = garResourceService.getBuildDetails(
        connectorRef, REGION, repositoryName, project, pkg, version, "", ORG_IDENTIFIER, PROJECT_IDENTIFIER);
    assertThat(garResponseDTO).isNotNull();
    ArgumentCaptor<DelegateTaskRequest> delegateTaskRequestCaptor = ArgumentCaptor.forClass(DelegateTaskRequest.class);
    verify(connectorService).get(ACCOUNT_ID, ORG_IDENTIFIER, PROJECT_IDENTIFIER, "identifier");
    verify(delegateGrpcClientWrapper).executeSyncTaskV2(delegateTaskRequestCaptor.capture());
    DelegateTaskRequest delegateTaskRequest = delegateTaskRequestCaptor.getValue();
    ArtifactTaskParameters artifactTaskParameters = (ArtifactTaskParameters) delegateTaskRequest.getTaskParameters();
    assertThat(artifactTaskParameters.getArtifactTaskType()).isEqualTo(ArtifactTaskType.GET_BUILDS);
  }
  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testErrorNotifyResponseDataException() {
    IdentifierRef connectorRef = IdentifierRef.builder()
                                     .accountIdentifier(ACCOUNT_ID)
                                     .identifier("identifier")
                                     .projectIdentifier(PROJECT_IDENTIFIER)
                                     .orgIdentifier(ORG_IDENTIFIER)
                                     .build();
    ConnectorResponseDTO connectorResponse = getConnector();
    when(connectorService.get(ACCOUNT_ID, ORG_IDENTIFIER, PROJECT_IDENTIFIER, "identifier"))
        .thenReturn(Optional.of(connectorResponse));
    EncryptedDataDetail encryptedDataDetail = EncryptedDataDetail.builder().build();
    when(secretManagerClientService.getEncryptionDetails(any(), any()))
        .thenReturn(Lists.newArrayList(encryptedDataDetail));
    when(delegateGrpcClientWrapper.executeSyncTaskV2(any()))
        .thenReturn(ErrorNotifyResponseData.builder().errorMessage("Testing").build());

    assertThatThrownBy(()
                           -> garResourceService.getBuildDetails(connectorRef, REGION, repositoryName, project, pkg,
                               version, "", ORG_IDENTIFIER, PROJECT_IDENTIFIER))
        .isInstanceOf(ArtifactServerException.class)
        .hasMessage("Google Artifact Registry Get Builds task failure due to error - Testing");
  }
  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testRemoteMethodReturnValueData() {
    IdentifierRef connectorRef = IdentifierRef.builder()
                                     .accountIdentifier(ACCOUNT_ID)
                                     .identifier("identifier")
                                     .projectIdentifier(PROJECT_IDENTIFIER)
                                     .orgIdentifier(ORG_IDENTIFIER)
                                     .build();
    ConnectorResponseDTO connectorResponse = getConnector();
    when(connectorService.get(ACCOUNT_ID, ORG_IDENTIFIER, PROJECT_IDENTIFIER, "identifier"))
        .thenReturn(Optional.of(connectorResponse));
    EncryptedDataDetail encryptedDataDetail = EncryptedDataDetail.builder().build();
    when(secretManagerClientService.getEncryptionDetails(any(), any()))
        .thenReturn(Lists.newArrayList(encryptedDataDetail));
    Object obj = new Object();
    when(delegateGrpcClientWrapper.executeSyncTaskV2(any()))
        .thenReturn(RemoteMethodReturnValueData.builder()
                        .exception(InvalidRequestException.builder().message("Testing").build())
                        .returnValue(obj)
                        .build());

    assertThatThrownBy(()
                           -> garResourceService.getBuildDetails(connectorRef, REGION, repositoryName, project, pkg,
                               version, "", ORG_IDENTIFIER, PROJECT_IDENTIFIER))
        .isInstanceOf(ArtifactServerException.class)
        .hasMessage("Unexpected error during authentication to Google Artifact Registry server " + obj);
  }
  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testDelegateServiceDriverException() {
    IdentifierRef connectorRef = IdentifierRef.builder()
                                     .accountIdentifier(ACCOUNT_ID)
                                     .identifier("identifier")
                                     .projectIdentifier(PROJECT_IDENTIFIER)
                                     .orgIdentifier(ORG_IDENTIFIER)
                                     .build();
    ConnectorResponseDTO connectorResponse = getConnector();
    when(connectorService.get(ACCOUNT_ID, ORG_IDENTIFIER, PROJECT_IDENTIFIER, "identifier"))
        .thenReturn(Optional.of(connectorResponse));
    EncryptedDataDetail encryptedDataDetail = EncryptedDataDetail.builder().build();
    when(secretManagerClientService.getEncryptionDetails(any(), any()))
        .thenReturn(Lists.newArrayList(encryptedDataDetail));
    Object obj = new Object();
    when(delegateGrpcClientWrapper.executeSyncTaskV2(any()))
        .thenThrow(new DelegateServiceDriverException("DelegateServiceDriverException"));

    when(exceptionManager.processException(any(), any(), any()))
        .thenThrow(new WingsException("wings exception message"));
    assertThatThrownBy(()
                           -> garResourceService.getBuildDetails(connectorRef, REGION, repositoryName, project, pkg,
                               version, "", ORG_IDENTIFIER, PROJECT_IDENTIFIER))
        .isInstanceOf(WingsException.class)
        .hasMessage("wings exception message");
  }
  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testFailureExecution() {
    IdentifierRef connectorRef = IdentifierRef.builder()
                                     .accountIdentifier(ACCOUNT_ID)
                                     .identifier("identifier")
                                     .projectIdentifier(PROJECT_IDENTIFIER)
                                     .orgIdentifier(ORG_IDENTIFIER)
                                     .build();
    ConnectorResponseDTO connectorResponse = getConnector();
    when(connectorService.get(ACCOUNT_ID, ORG_IDENTIFIER, PROJECT_IDENTIFIER, "identifier"))
        .thenReturn(Optional.of(connectorResponse));
    EncryptedDataDetail encryptedDataDetail = EncryptedDataDetail.builder().build();
    when(secretManagerClientService.getEncryptionDetails(any(), any()))
        .thenReturn(Lists.newArrayList(encryptedDataDetail));
    when(delegateGrpcClientWrapper.executeSyncTaskV2(any()))
        .thenReturn(
            ArtifactTaskResponse.builder()
                .commandExecutionStatus(CommandExecutionStatus.FAILURE)
                .errorCode(ErrorCode.DEFAULT_ERROR_CODE)
                .errorMessage("Test failed")
                .artifactTaskExecutionResponse(
                    ArtifactTaskExecutionResponse.builder().artifactDelegateResponses(new ArrayList<>()).build())
                .build());

    assertThatThrownBy(()
                           -> garResourceService.getBuildDetails(connectorRef, REGION, repositoryName, project, pkg,
                               version, "", ORG_IDENTIFIER, PROJECT_IDENTIFIER))
        .isInstanceOf(WingsException.class)
        .hasMessage(
            "Google Artifact Registry Get Builds task failure due to error - Test failed with error code: DEFAULT_ERROR_CODE");
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void testGetLastSuccessfulBuild() {
    IdentifierRef connectorRef = IdentifierRef.builder()
                                     .accountIdentifier(ACCOUNT_ID)
                                     .identifier("identifier")
                                     .projectIdentifier(PROJECT_IDENTIFIER)
                                     .orgIdentifier(ORG_IDENTIFIER)
                                     .build();
    ConnectorResponseDTO connectorResponse = getConnector();
    when(connectorService.get(ACCOUNT_ID, ORG_IDENTIFIER, PROJECT_IDENTIFIER, "identifier"))
        .thenReturn(Optional.of(connectorResponse));
    EncryptedDataDetail encryptedDataDetail = EncryptedDataDetail.builder().build();
    when(secretManagerClientService.getEncryptionDetails(any(), any()))
        .thenReturn(Lists.newArrayList(encryptedDataDetail));
    when(delegateGrpcClientWrapper.executeSyncTaskV2(any()))
        .thenReturn(
            ArtifactTaskResponse.builder()
                .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                .artifactTaskExecutionResponse(ArtifactTaskExecutionResponse.builder()
                                                   .artifactDelegateResponses(Arrays.asList(
                                                       GarDelegateResponse.builder()
                                                           .buildDetails(ArtifactBuildDetailsNG.builder().build())
                                                           .build()))
                                                   .build())
                .build());

    GARBuildDetailsDTO garResponseDTO = garResourceService.getLastSuccessfulBuild(connectorRef, REGION, repositoryName,
        project, pkg, GarRequestDTO.builder().version(version).build(), ORG_IDENTIFIER, PROJECT_IDENTIFIER);
    assertThat(garResponseDTO).isNotNull();
    ArgumentCaptor<DelegateTaskRequest> delegateTaskRequestCaptor = ArgumentCaptor.forClass(DelegateTaskRequest.class);
    verify(connectorService).get(ACCOUNT_ID, ORG_IDENTIFIER, PROJECT_IDENTIFIER, "identifier");
    verify(delegateGrpcClientWrapper).executeSyncTaskV2(delegateTaskRequestCaptor.capture());
    DelegateTaskRequest delegateTaskRequest = delegateTaskRequestCaptor.getValue();
    ArtifactTaskParameters artifactTaskParameters = (ArtifactTaskParameters) delegateTaskRequest.getTaskParameters();
    assertThat(artifactTaskParameters.getArtifactTaskType()).isEqualTo(ArtifactTaskType.GET_LAST_SUCCESSFUL_BUILD);
  }
}
