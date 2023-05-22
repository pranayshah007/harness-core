/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.artifact.resources.gcr.service;

import static io.harness.rule.OwnerRule.ABHISHEK;
import static io.harness.rule.OwnerRule.ARCHIT;
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
import io.harness.cdng.artifact.resources.gcr.dtos.GcrBuildDetailsDTO;
import io.harness.cdng.artifact.resources.gcr.dtos.GcrRequestDTO;
import io.harness.cdng.artifact.resources.gcr.dtos.GcrResponseDTO;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.services.ConnectorService;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorCredentialDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorDTO;
import io.harness.delegate.task.artifacts.ArtifactTaskType;
import io.harness.delegate.task.artifacts.gcr.GcrArtifactDelegateResponse;
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

@OwnedBy(HarnessTeam.PIPELINE)
public class GcrResourceServiceImplTest extends CategoryTest {
  private static final String ACCOUNT_ID = "accountId";
  private static final String IMAGE_PATH = "imagePath";
  private static final String REGISTRY_HOSTNAME = "registryHostname";
  private static final String ORG_IDENTIFIER = "orgIdentifier";
  private static final String PROJECT_IDENTIFIER = "projectIdentifier";
  private static final String IDENTIFIER = "identifier";
  private static final String INPUT = "<+input>-abc";
  private static final IdentifierRef IDENTIFIER_REF = IdentifierRef.builder()
                                                          .accountIdentifier(ACCOUNT_ID)
                                                          .identifier(IDENTIFIER)
                                                          .projectIdentifier(PROJECT_IDENTIFIER)
                                                          .orgIdentifier(ORG_IDENTIFIER)
                                                          .build();
  private static final GcrRequestDTO GCR_REQUEST_DTO =
      GcrRequestDTO.builder().registryHostname(REGISTRY_HOSTNAME).build();
  private static final String IMAGE_PATH_MESSAGE = "value for imagePath is empty or not provided";
  private static final String REGISTRY_HOST_NAME_MESSAGE = "value for registryHostname is empty or not provided";
  private static final String TAG_TAG_REGEX_MESSAGE = "value for tag, tagRegex is empty or not provided";

  @Mock ConnectorService connectorService;
  @Mock SecretManagerClientService secretManagerClientService;
  @Mock DelegateGrpcClientWrapper delegateGrpcClientWrapper;
  @Mock ExceptionManager exceptionManager;
  @Spy @InjectMocks GcrResourceServiceImpl gcrResourceService;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  private ConnectorResponseDTO getConnector() {
    ConnectorInfoDTO connectorInfoDTO =
        ConnectorInfoDTO.builder()
            .connectorType(ConnectorType.GCP)
            .connectorConfig(GcpConnectorDTO.builder()
                                 .delegateSelectors(Collections.emptySet())
                                 .credential(GcpConnectorCredentialDTO.builder().build())
                                 .build())
            .projectIdentifier("dummyProject")
            .orgIdentifier("dummyOrg")
            .build();
    return ConnectorResponseDTO.builder().connector(connectorInfoDTO).build();
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testGetBuildDetails() {
    IdentifierRef identifierRef = IdentifierRef.builder()
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

    GcrResponseDTO gcrResponseDTO = gcrResourceService.getBuildDetails(
        identifierRef, IMAGE_PATH, REGISTRY_HOSTNAME, ORG_IDENTIFIER, PROJECT_IDENTIFIER);
    assertThat(gcrResponseDTO).isNotNull();

    ArgumentCaptor<DelegateTaskRequest> delegateTaskRequestCaptor = ArgumentCaptor.forClass(DelegateTaskRequest.class);
    verify(connectorService).get(ACCOUNT_ID, ORG_IDENTIFIER, PROJECT_IDENTIFIER, "identifier");
    verify(delegateGrpcClientWrapper).executeSyncTaskV2(delegateTaskRequestCaptor.capture());
    DelegateTaskRequest delegateTaskRequest = delegateTaskRequestCaptor.getValue();
    ArtifactTaskParameters artifactTaskParameters = (ArtifactTaskParameters) delegateTaskRequest.getTaskParameters();
    assertThat(artifactTaskParameters.getArtifactTaskType()).isEqualTo(ArtifactTaskType.GET_BUILDS);
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testGetSuccessfulBuild() {
    GcrRequestDTO gcrRequestDTO = GcrRequestDTO.builder().registryHostname(REGISTRY_HOSTNAME).tag("version").build();
    ConnectorResponseDTO connectorDTO = getConnector();
    when(connectorService.get(ACCOUNT_ID, ORG_IDENTIFIER, PROJECT_IDENTIFIER, IDENTIFIER))
        .thenReturn(Optional.of(connectorDTO));
    EncryptedDataDetail encryptedDataDetail = EncryptedDataDetail.builder().build();
    when(secretManagerClientService.getEncryptionDetails(any(), any()))
        .thenReturn(Lists.newArrayList(encryptedDataDetail));
    when(delegateGrpcClientWrapper.executeSyncTaskV2(any()))
        .thenReturn(
            ArtifactTaskResponse.builder()
                .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                .artifactTaskExecutionResponse(ArtifactTaskExecutionResponse.builder()
                                                   .artifactDelegateResponses(Lists.newArrayList(
                                                       GcrArtifactDelegateResponse.builder()
                                                           .buildDetails(ArtifactBuildDetailsNG.builder().build())
                                                           .build()))
                                                   .build())
                .build());

    GcrBuildDetailsDTO gcrBuildDetailsDTO = gcrResourceService.getSuccessfulBuild(
        IDENTIFIER_REF, IMAGE_PATH, gcrRequestDTO, ORG_IDENTIFIER, PROJECT_IDENTIFIER);
    assertThat(gcrBuildDetailsDTO).isNotNull();

    ArgumentCaptor<DelegateTaskRequest> delegateTaskRequestCaptor = ArgumentCaptor.forClass(DelegateTaskRequest.class);
    verify(connectorService).get(ACCOUNT_ID, ORG_IDENTIFIER, PROJECT_IDENTIFIER, IDENTIFIER);
    verify(delegateGrpcClientWrapper).executeSyncTaskV2(delegateTaskRequestCaptor.capture());
    DelegateTaskRequest delegateTaskRequest = delegateTaskRequestCaptor.getValue();
    ArtifactTaskParameters artifactTaskParameters = (ArtifactTaskParameters) delegateTaskRequest.getTaskParameters();
    assertThat(artifactTaskParameters.getArtifactTaskType()).isEqualTo(ArtifactTaskType.GET_LAST_SUCCESSFUL_BUILD);
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testValidateArtifactServer() {
    IdentifierRef identifierRef = IdentifierRef.builder()
                                      .accountIdentifier(ACCOUNT_ID)
                                      .identifier("identifier")
                                      .projectIdentifier(PROJECT_IDENTIFIER)
                                      .orgIdentifier(ORG_IDENTIFIER)
                                      .build();
    ConnectorResponseDTO connectorDTO = getConnector();
    when(connectorService.get(ACCOUNT_ID, ORG_IDENTIFIER, PROJECT_IDENTIFIER, "identifier"))
        .thenReturn(Optional.of(connectorDTO));
    EncryptedDataDetail encryptedDataDetail = EncryptedDataDetail.builder().build();
    when(secretManagerClientService.getEncryptionDetails(any(), any()))
        .thenReturn(Lists.newArrayList(encryptedDataDetail));
    when(delegateGrpcClientWrapper.executeSyncTaskV2(any()))
        .thenReturn(
            ArtifactTaskResponse.builder()
                .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                .artifactTaskExecutionResponse(ArtifactTaskExecutionResponse.builder()
                                                   .artifactDelegateResponses(Lists.newArrayList(
                                                       GcrArtifactDelegateResponse.builder()
                                                           .buildDetails(ArtifactBuildDetailsNG.builder().build())
                                                           .build()))
                                                   .build())
                .build());

    boolean response = gcrResourceService.validateArtifactServer(
        identifierRef, IMAGE_PATH, ORG_IDENTIFIER, PROJECT_IDENTIFIER, REGISTRY_HOSTNAME);
    assertThat(response).isFalse();

    ArgumentCaptor<DelegateTaskRequest> delegateTaskRequestCaptor = ArgumentCaptor.forClass(DelegateTaskRequest.class);
    verify(connectorService).get(ACCOUNT_ID, ORG_IDENTIFIER, PROJECT_IDENTIFIER, "identifier");
    verify(delegateGrpcClientWrapper).executeSyncTaskV2(delegateTaskRequestCaptor.capture());
    DelegateTaskRequest delegateTaskRequest = delegateTaskRequestCaptor.getValue();
    ArtifactTaskParameters artifactTaskParameters = (ArtifactTaskParameters) delegateTaskRequest.getTaskParameters();
    assertThat(artifactTaskParameters.getArtifactTaskType()).isEqualTo(ArtifactTaskType.VALIDATE_ARTIFACT_SERVER);
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testValidateArtifactSource() {
    IdentifierRef identifierRef = IdentifierRef.builder()
                                      .accountIdentifier(ACCOUNT_ID)
                                      .identifier("identifier")
                                      .projectIdentifier(PROJECT_IDENTIFIER)
                                      .orgIdentifier(ORG_IDENTIFIER)
                                      .build();
    ConnectorResponseDTO connectorDTO = getConnector();
    when(connectorService.get(ACCOUNT_ID, ORG_IDENTIFIER, PROJECT_IDENTIFIER, "identifier"))
        .thenReturn(Optional.of(connectorDTO));
    EncryptedDataDetail encryptedDataDetail = EncryptedDataDetail.builder().build();
    when(secretManagerClientService.getEncryptionDetails(any(), any()))
        .thenReturn(Lists.newArrayList(encryptedDataDetail));
    when(delegateGrpcClientWrapper.executeSyncTaskV2(any()))
        .thenReturn(
            ArtifactTaskResponse.builder()
                .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                .artifactTaskExecutionResponse(ArtifactTaskExecutionResponse.builder()
                                                   .artifactDelegateResponses(Lists.newArrayList(
                                                       GcrArtifactDelegateResponse.builder()
                                                           .buildDetails(ArtifactBuildDetailsNG.builder().build())
                                                           .build()))
                                                   .build())
                .build());

    boolean response = gcrResourceService.validateArtifactSource(
        IMAGE_PATH, identifierRef, REGISTRY_HOSTNAME, ORG_IDENTIFIER, PROJECT_IDENTIFIER);
    assertThat(response).isFalse();

    ArgumentCaptor<DelegateTaskRequest> delegateTaskRequestCaptor = ArgumentCaptor.forClass(DelegateTaskRequest.class);
    verify(connectorService).get(ACCOUNT_ID, ORG_IDENTIFIER, PROJECT_IDENTIFIER, "identifier");
    verify(delegateGrpcClientWrapper).executeSyncTaskV2(delegateTaskRequestCaptor.capture());
    DelegateTaskRequest delegateTaskRequest = delegateTaskRequestCaptor.getValue();
    ArtifactTaskParameters artifactTaskParameters = (ArtifactTaskParameters) delegateTaskRequest.getTaskParameters();
    assertThat(artifactTaskParameters.getArtifactTaskType()).isEqualTo(ArtifactTaskType.VALIDATE_ARTIFACT_SOURCE);
  }

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testimagePathBlankCheck() {
    IdentifierRef identifierRef = IdentifierRef.builder()
                                      .accountIdentifier(ACCOUNT_ID)
                                      .identifier("identifier")
                                      .projectIdentifier(PROJECT_IDENTIFIER)
                                      .orgIdentifier(ORG_IDENTIFIER)
                                      .build();
    assertThatThrownBy(()
                           -> gcrResourceService.getBuildDetails(
                               identifierRef, "", REGISTRY_HOSTNAME, ORG_IDENTIFIER, PROJECT_IDENTIFIER))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("imagePath cannot be null");
  }
  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testDelegateServiceDriverException() {
    IdentifierRef identifierRef = IdentifierRef.builder()
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

    when(delegateGrpcClientWrapper.executeSyncTaskV2(any()))
        .thenThrow(new DelegateServiceDriverException("DelegateServiceDriverException"));

    when(exceptionManager.processException(any(), any(), any()))
        .thenThrow(new WingsException("wings exception message"));

    assertThatThrownBy(()
                           -> gcrResourceService.getBuildDetails(
                               identifierRef, IMAGE_PATH, REGISTRY_HOSTNAME, ORG_IDENTIFIER, PROJECT_IDENTIFIER))
        .isInstanceOf(WingsException.class)
        .hasMessage("wings exception message");
  }

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testErrorNotifyResponseDataException() {
    IdentifierRef identifierRef = IdentifierRef.builder()
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
                           -> gcrResourceService.getBuildDetails(
                               identifierRef, IMAGE_PATH, REGISTRY_HOSTNAME, ORG_IDENTIFIER, PROJECT_IDENTIFIER))
        .isInstanceOf(ArtifactServerException.class)
        .hasMessage("Gcr Get Builds task failure due to error - Testing");
  }
  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testFailureExecution() {
    IdentifierRef identifierRef = IdentifierRef.builder()
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
                           -> gcrResourceService.getBuildDetails(
                               identifierRef, IMAGE_PATH, REGISTRY_HOSTNAME, ORG_IDENTIFIER, PROJECT_IDENTIFIER))
        .isInstanceOf(WingsException.class)
        .hasMessage("Gcr Get Builds task failure due to error - Test failed with error code: DEFAULT_ERROR_CODE");
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void testGetSuccessfulBuild_ImagePath_NULL() {
    assertThatThrownBy(()
                           -> gcrResourceService.getSuccessfulBuild(
                               IDENTIFIER_REF, null, GCR_REQUEST_DTO, ORG_IDENTIFIER, PROJECT_IDENTIFIER))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(IMAGE_PATH_MESSAGE);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void testGetSuccessfulBuild_ImagePath_Empty() {
    assertThatThrownBy(()
                           -> gcrResourceService.getSuccessfulBuild(
                               IDENTIFIER_REF, "", GCR_REQUEST_DTO, ORG_IDENTIFIER, PROJECT_IDENTIFIER))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(IMAGE_PATH_MESSAGE);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void testGetSuccessfulBuild_ImagePath_INPUT() {
    assertThatThrownBy(()
                           -> gcrResourceService.getSuccessfulBuild(
                               IDENTIFIER_REF, INPUT, GCR_REQUEST_DTO, ORG_IDENTIFIER, PROJECT_IDENTIFIER))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(IMAGE_PATH_MESSAGE);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void testGetSuccessfulBuild_RegistryHostName_NULL() {
    assertThatThrownBy(()
                           -> gcrResourceService.getSuccessfulBuild(IDENTIFIER_REF, IMAGE_PATH,
                               GcrRequestDTO.builder().build(), ORG_IDENTIFIER, PROJECT_IDENTIFIER))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(REGISTRY_HOST_NAME_MESSAGE);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void testGetSuccessfulBuild_RegistryHostName_Empty() {
    assertThatThrownBy(
        ()
            -> gcrResourceService.getSuccessfulBuild(IDENTIFIER_REF, IMAGE_PATH,
                GcrRequestDTO.builder().registryHostname("").build(), ORG_IDENTIFIER, PROJECT_IDENTIFIER))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(REGISTRY_HOST_NAME_MESSAGE);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void testGetSuccessfulBuild_RegistryHostName_INPUT() {
    assertThatThrownBy(
        ()
            -> gcrResourceService.getSuccessfulBuild(IDENTIFIER_REF, IMAGE_PATH,
                GcrRequestDTO.builder().registryHostname(INPUT).build(), ORG_IDENTIFIER, PROJECT_IDENTIFIER))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(REGISTRY_HOST_NAME_MESSAGE);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void testGetSuccessfulBuild_Tag_NULL() {
    assertThatThrownBy(()
                           -> gcrResourceService.getSuccessfulBuild(
                               IDENTIFIER_REF, IMAGE_PATH, GCR_REQUEST_DTO, ORG_IDENTIFIER, PROJECT_IDENTIFIER))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(TAG_TAG_REGEX_MESSAGE);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void testGetSuccessfulBuild_Tag_Empty() {
    assertThatThrownBy(()
                           -> gcrResourceService.getSuccessfulBuild(IDENTIFIER_REF, IMAGE_PATH,
                               GcrRequestDTO.builder().registryHostname(REGISTRY_HOSTNAME).tag("").build(),
                               ORG_IDENTIFIER, PROJECT_IDENTIFIER))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(TAG_TAG_REGEX_MESSAGE);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void testGetSuccessfulBuild_Tag_INPUT() {
    assertThatThrownBy(()
                           -> gcrResourceService.getSuccessfulBuild(IDENTIFIER_REF, IMAGE_PATH,
                               GcrRequestDTO.builder().registryHostname(REGISTRY_HOSTNAME).tag(INPUT).build(),
                               ORG_IDENTIFIER, PROJECT_IDENTIFIER))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(TAG_TAG_REGEX_MESSAGE);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void testGetSuccessfulBuild_TagRegex_NULL() {
    assertThatThrownBy(()
                           -> gcrResourceService.getSuccessfulBuild(
                               IDENTIFIER_REF, IMAGE_PATH, GCR_REQUEST_DTO, ORG_IDENTIFIER, PROJECT_IDENTIFIER))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(TAG_TAG_REGEX_MESSAGE);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void testGetSuccessfulBuild_TagRegex_Empty() {
    assertThatThrownBy(()
                           -> gcrResourceService.getSuccessfulBuild(IDENTIFIER_REF, IMAGE_PATH,
                               GcrRequestDTO.builder().registryHostname(REGISTRY_HOSTNAME).tagRegex("").build(),
                               ORG_IDENTIFIER, PROJECT_IDENTIFIER))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(TAG_TAG_REGEX_MESSAGE);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void testGetSuccessfulBuild_TagRegex_INPUT() {
    assertThatThrownBy(()
                           -> gcrResourceService.getSuccessfulBuild(IDENTIFIER_REF, IMAGE_PATH,
                               GcrRequestDTO.builder().registryHostname(REGISTRY_HOSTNAME).tagRegex(INPUT).build(),
                               ORG_IDENTIFIER, PROJECT_IDENTIFIER))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(TAG_TAG_REGEX_MESSAGE);
  }
}
