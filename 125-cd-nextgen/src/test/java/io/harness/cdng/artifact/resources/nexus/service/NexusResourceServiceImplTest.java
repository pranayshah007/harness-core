/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.artifact.resources.nexus.service;

import static io.harness.rule.OwnerRule.ABHISHEK;
import static io.harness.rule.OwnerRule.MLUKIC;

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
import io.harness.cdng.artifact.resources.nexus.dtos.NexusBuildDetailsDTO;
import io.harness.cdng.artifact.resources.nexus.dtos.NexusRequestDTO;
import io.harness.cdng.artifact.resources.nexus.dtos.NexusResponseDTO;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.services.ConnectorService;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.nexusconnector.NexusAuthenticationDTO;
import io.harness.delegate.beans.connector.nexusconnector.NexusConnectorDTO;
import io.harness.delegate.task.artifacts.ArtifactTaskType;
import io.harness.delegate.task.artifacts.nexus.NexusArtifactDelegateResponse;
import io.harness.delegate.task.artifacts.request.ArtifactTaskParameters;
import io.harness.delegate.task.artifacts.response.ArtifactBuildDetailsNG;
import io.harness.delegate.task.artifacts.response.ArtifactTaskExecutionResponse;
import io.harness.delegate.task.artifacts.response.ArtifactTaskResponse;
import io.harness.exception.InvalidRequestException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.rule.Owner;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.service.DelegateGrpcClientWrapper;

import software.wings.utils.RepositoryFormat;

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

@OwnedBy(HarnessTeam.CDP)
public class NexusResourceServiceImplTest extends CategoryTest {
  private static String ACCOUNT_ID = "accountId";
  private static String REPO_NAME = "repoName";
  private static String IMAGE_PATH = "imagePath";
  private static String ORG_IDENTIFIER = "orgIdentifier";
  private static String PROJECT_IDENTIFIER = "projectIdentifier";
  private static String REPO_PORT = "8181";
  private static final String TAG = "tag";
  private static final String IDENTIFIER = "identifier";
  private static final String INPUT = "<+input>-abc";

  private static final IdentifierRef IDENTIFIER_REF = IdentifierRef.builder()
                                                          .accountIdentifier(ACCOUNT_ID)
                                                          .identifier(IDENTIFIER)
                                                          .projectIdentifier(PROJECT_IDENTIFIER)
                                                          .orgIdentifier(ORG_IDENTIFIER)
                                                          .build();
  private static final NexusRequestDTO NEXUS_REQUEST_DTO = NexusRequestDTO.builder().build();

  private static final String REPOSITORY_MESSAGE = "value for repository is empty or not provided";
  private static final String REPOSITORY_FORMAT_MESSAGE = "value for repositoryFormat is empty or not provided";
  private static final String TAG_TAG_REGEX_MESSAGE = "value for tag, tagRegex is empty or not provided";

  @Mock ConnectorService connectorService;
  @Mock SecretManagerClientService secretManagerClientService;
  @Mock DelegateGrpcClientWrapper delegateGrpcClientWrapper;

  @Spy @InjectMocks NexusResourceServiceImpl nexusResourceService;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  private ConnectorResponseDTO getConnector() {
    ConnectorInfoDTO connectorInfoDTO = ConnectorInfoDTO.builder()
                                            .connectorType(ConnectorType.NEXUS)
                                            .connectorConfig(NexusConnectorDTO.builder()
                                                                 .delegateSelectors(Collections.emptySet())
                                                                 .auth(NexusAuthenticationDTO.builder().build())
                                                                 .build())
                                            .orgIdentifier("dummyOrg")
                                            .projectIdentifier("dummyProject")
                                            .build();
    return ConnectorResponseDTO.builder().connector(connectorInfoDTO).build();
  }

  @Test
  @Owner(developers = MLUKIC)
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

    NexusResponseDTO nexusResponseDTO = nexusResourceService.getBuildDetails(identifierRef, REPO_NAME, REPO_PORT,
        IMAGE_PATH, RepositoryFormat.docker.name(), null, ORG_IDENTIFIER, PROJECT_IDENTIFIER);
    assertThat(nexusResponseDTO).isNotNull();

    ArgumentCaptor<DelegateTaskRequest> delegateTaskRequestCaptor = ArgumentCaptor.forClass(DelegateTaskRequest.class);
    verify(connectorService).get(ACCOUNT_ID, ORG_IDENTIFIER, PROJECT_IDENTIFIER, "identifier");
    verify(delegateGrpcClientWrapper).executeSyncTaskV2(delegateTaskRequestCaptor.capture());
    DelegateTaskRequest delegateTaskRequest = delegateTaskRequestCaptor.getValue();
    ArtifactTaskParameters artifactTaskParameters = (ArtifactTaskParameters) delegateTaskRequest.getTaskParameters();
    assertThat(artifactTaskParameters.getArtifactTaskType()).isEqualTo(ArtifactTaskType.GET_BUILDS);
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testGetSuccessfulBuild() {
    NexusRequestDTO nexusRequestDTO = NexusRequestDTO.builder().tag(TAG).build();
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
                                                       NexusArtifactDelegateResponse.builder()
                                                           .buildDetails(ArtifactBuildDetailsNG.builder().build())
                                                           .build()))
                                                   .build())
                .build());

    NexusBuildDetailsDTO dockerBuildDetailsDTO =
        nexusResourceService.getSuccessfulBuild(IDENTIFIER_REF, REPO_NAME, REPO_PORT, IMAGE_PATH,
            RepositoryFormat.docker.name(), null, nexusRequestDTO, ORG_IDENTIFIER, PROJECT_IDENTIFIER);
    assertThat(dockerBuildDetailsDTO).isNotNull();

    ArgumentCaptor<DelegateTaskRequest> delegateTaskRequestCaptor = ArgumentCaptor.forClass(DelegateTaskRequest.class);
    verify(connectorService).get(ACCOUNT_ID, ORG_IDENTIFIER, PROJECT_IDENTIFIER, IDENTIFIER);
    verify(delegateGrpcClientWrapper).executeSyncTaskV2(delegateTaskRequestCaptor.capture());
    DelegateTaskRequest delegateTaskRequest = delegateTaskRequestCaptor.getValue();
    ArtifactTaskParameters artifactTaskParameters = (ArtifactTaskParameters) delegateTaskRequest.getTaskParameters();
    assertThat(artifactTaskParameters.getArtifactTaskType()).isEqualTo(ArtifactTaskType.GET_LAST_SUCCESSFUL_BUILD);
  }

  @Test
  @Owner(developers = MLUKIC)
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
                                                       NexusArtifactDelegateResponse.builder()
                                                           .buildDetails(ArtifactBuildDetailsNG.builder().build())
                                                           .build()))
                                                   .build())
                .build());

    boolean response = nexusResourceService.validateArtifactServer(identifierRef, ORG_IDENTIFIER, PROJECT_IDENTIFIER);
    assertThat(response).isFalse();

    ArgumentCaptor<DelegateTaskRequest> delegateTaskRequestCaptor = ArgumentCaptor.forClass(DelegateTaskRequest.class);
    verify(connectorService).get(ACCOUNT_ID, ORG_IDENTIFIER, PROJECT_IDENTIFIER, "identifier");
    verify(delegateGrpcClientWrapper).executeSyncTaskV2(delegateTaskRequestCaptor.capture());
    DelegateTaskRequest delegateTaskRequest = delegateTaskRequestCaptor.getValue();
    ArtifactTaskParameters artifactTaskParameters = (ArtifactTaskParameters) delegateTaskRequest.getTaskParameters();
    assertThat(artifactTaskParameters.getArtifactTaskType()).isEqualTo(ArtifactTaskType.VALIDATE_ARTIFACT_SERVER);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void testGetSuccessfulBuild_Repository_Null() {
    assertThatThrownBy(
        ()
            -> nexusResourceService.getSuccessfulBuild(IDENTIFIER_REF, null, REPO_PORT, IMAGE_PATH,
                RepositoryFormat.docker.name(), null, NEXUS_REQUEST_DTO, ORG_IDENTIFIER, PROJECT_IDENTIFIER))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(REPOSITORY_MESSAGE);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void testGetSuccessfulBuild_Repository_Empty() {
    assertThatThrownBy(
        ()
            -> nexusResourceService.getSuccessfulBuild(IDENTIFIER_REF, "", REPO_PORT, IMAGE_PATH,
                RepositoryFormat.docker.name(), null, NEXUS_REQUEST_DTO, ORG_IDENTIFIER, PROJECT_IDENTIFIER))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(REPOSITORY_MESSAGE);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void testGetSuccessfulBuild_Repository_Input() {
    assertThatThrownBy(
        ()
            -> nexusResourceService.getSuccessfulBuild(IDENTIFIER_REF, INPUT, REPO_PORT, IMAGE_PATH,
                RepositoryFormat.docker.name(), null, NEXUS_REQUEST_DTO, ORG_IDENTIFIER, PROJECT_IDENTIFIER))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(REPOSITORY_MESSAGE);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void testGetSuccessfulBuild_RepositoryFormat_Null() {
    assertThatThrownBy(()
                           -> nexusResourceService.getSuccessfulBuild(IDENTIFIER_REF, REPO_NAME, REPO_PORT, IMAGE_PATH,
                               null, null, NEXUS_REQUEST_DTO, ORG_IDENTIFIER, PROJECT_IDENTIFIER))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(REPOSITORY_FORMAT_MESSAGE);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void testGetSuccessfulBuild_RepositoryFormat_Empty() {
    assertThatThrownBy(()
                           -> nexusResourceService.getSuccessfulBuild(IDENTIFIER_REF, REPO_NAME, REPO_PORT, IMAGE_PATH,
                               "", null, NEXUS_REQUEST_DTO, ORG_IDENTIFIER, PROJECT_IDENTIFIER))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(REPOSITORY_FORMAT_MESSAGE);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void testGetSuccessfulBuild_RepositoryFormat_Input() {
    assertThatThrownBy(()
                           -> nexusResourceService.getSuccessfulBuild(IDENTIFIER_REF, REPO_NAME, REPO_PORT, IMAGE_PATH,
                               INPUT, null, NEXUS_REQUEST_DTO, ORG_IDENTIFIER, PROJECT_IDENTIFIER))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(REPOSITORY_FORMAT_MESSAGE);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void testGetSuccessfulBuild_Tag_Null() {
    assertThatThrownBy(
        ()
            -> nexusResourceService.getSuccessfulBuild(IDENTIFIER_REF, REPO_NAME, REPO_PORT, IMAGE_PATH,
                RepositoryFormat.docker.name(), null, NEXUS_REQUEST_DTO, ORG_IDENTIFIER, PROJECT_IDENTIFIER))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(TAG_TAG_REGEX_MESSAGE);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void testGetSuccessfulBuild_Tag_Empty() {
    assertThatThrownBy(()
                           -> nexusResourceService.getSuccessfulBuild(IDENTIFIER_REF, REPO_NAME, REPO_PORT, IMAGE_PATH,
                               RepositoryFormat.docker.name(), null, NexusRequestDTO.builder().tag("").build(),
                               ORG_IDENTIFIER, PROJECT_IDENTIFIER))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(TAG_TAG_REGEX_MESSAGE);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void testGetSuccessfulBuild_Tag_Input() {
    assertThatThrownBy(()
                           -> nexusResourceService.getSuccessfulBuild(IDENTIFIER_REF, REPO_NAME, REPO_PORT, IMAGE_PATH,
                               RepositoryFormat.docker.name(), null, NexusRequestDTO.builder().tag(INPUT).build(),
                               ORG_IDENTIFIER, PROJECT_IDENTIFIER))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(TAG_TAG_REGEX_MESSAGE);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void testGetSuccessfulBuild_TagRegex_Null() {
    assertThatThrownBy(
        ()
            -> nexusResourceService.getSuccessfulBuild(IDENTIFIER_REF, REPO_NAME, REPO_PORT, IMAGE_PATH,
                RepositoryFormat.docker.name(), null, NEXUS_REQUEST_DTO, ORG_IDENTIFIER, PROJECT_IDENTIFIER))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(TAG_TAG_REGEX_MESSAGE);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void testGetSuccessfulBuild_TagRegex_Empty() {
    assertThatThrownBy(()
                           -> nexusResourceService.getSuccessfulBuild(IDENTIFIER_REF, REPO_NAME, REPO_PORT, IMAGE_PATH,
                               RepositoryFormat.docker.name(), null, NexusRequestDTO.builder().tagRegex("").build(),
                               ORG_IDENTIFIER, PROJECT_IDENTIFIER))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(TAG_TAG_REGEX_MESSAGE);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void testGetSuccessfulBuild_TagRegex_Input() {
    assertThatThrownBy(()
                           -> nexusResourceService.getSuccessfulBuild(IDENTIFIER_REF, REPO_NAME, REPO_PORT, IMAGE_PATH,
                               RepositoryFormat.docker.name(), null, NexusRequestDTO.builder().tagRegex(INPUT).build(),
                               ORG_IDENTIFIER, PROJECT_IDENTIFIER))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(TAG_TAG_REGEX_MESSAGE);
  }
}
