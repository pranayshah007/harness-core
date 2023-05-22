/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.artifact.resources.ecr;

import static io.harness.rule.OwnerRule.ABHISHEK;
import static io.harness.rule.OwnerRule.vivekveman;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.category.element.UnitTests;
import io.harness.cdng.artifact.resources.ecr.dtos.EcrBuildDetailsDTO;
import io.harness.cdng.artifact.resources.ecr.dtos.EcrListImagesDTO;
import io.harness.cdng.artifact.resources.ecr.dtos.EcrRequestDTO;
import io.harness.cdng.artifact.resources.ecr.dtos.EcrResponseDTO;
import io.harness.cdng.artifact.resources.ecr.service.EcrResourceServiceImpl;
import io.harness.cdng.common.resources.AwsResourceServiceHelper;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.services.ConnectorService;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialType;
import io.harness.delegate.beans.connector.awsconnector.AwsManualConfigSpecDTO;
import io.harness.delegate.task.artifacts.ecr.EcrArtifactDelegateResponse;
import io.harness.delegate.task.artifacts.response.ArtifactBuildDetailsNG;
import io.harness.delegate.task.artifacts.response.ArtifactTaskExecutionResponse;
import io.harness.delegate.task.artifacts.response.ArtifactTaskResponse;
import io.harness.encryption.Scope;
import io.harness.encryption.SecretRefData;
import io.harness.exception.ArtifactServerException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.exceptionmanager.ExceptionManager;
import io.harness.logging.CommandExecutionStatus;
import io.harness.ng.core.BaseNGAccess;
import io.harness.rule.Owner;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.service.DelegateGrpcClientWrapper;

import io.fabric8.utils.Lists;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@OwnedBy(HarnessTeam.CDC)
public class EcrResourceServiceImplTest extends CategoryTest {
  private static final String ORG_IDENTIFIER = "orgIdentifier";
  private static final String PROJECT_IDENTIFIER = "projectIdentifier";
  private static final String ACCOUNT_ID = "accountId";
  private static final String IMAGE_PATH = "imagePath";
  private static final String REGION = "region";
  private static final String IDENTIFIER = "identifier";
  private static final String INPUT = "<+input>-abc";
  private static final String TAG = "tag";
  private static final IdentifierRef IDENTIFIER_REF = IdentifierRef.builder()
                                                          .accountIdentifier(ACCOUNT_ID)
                                                          .identifier(IDENTIFIER)
                                                          .projectIdentifier(PROJECT_IDENTIFIER)
                                                          .orgIdentifier(ORG_IDENTIFIER)
                                                          .scope(Scope.PROJECT)
                                                          .build();
  private static final EcrRequestDTO ECR_REQUEST_DTO = EcrRequestDTO.builder().region(REGION).build();
  private static final String IMAGE_PATH_MESSAGE = "value for imagePath is empty or not provided";
  private static final String REGION_MESSAGE = "value for region is empty or not provided";
  private static final String TAG_TAG_REGEX_MESSAGE = "value for tag, tagRegex is empty or not provided";
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  @Mock ConnectorService connectorService;
  @Mock AwsResourceServiceHelper serviceHelper;
  @Mock SecretManagerClientService secretManagerClientService;
  @Mock DelegateGrpcClientWrapper delegateGrpcClientWrapper;
  @Mock ExceptionManager exceptionManager;
  @InjectMocks EcrResourceServiceImpl ecrResourceService;
  private ConnectorResponseDTO getConnector() {
    AwsConnectorDTO awsConnectorDTO =
        AwsConnectorDTO.builder()
            .credential(
                AwsCredentialDTO.builder()
                    .awsCredentialType(AwsCredentialType.MANUAL_CREDENTIALS)
                    .config(AwsManualConfigSpecDTO.builder()
                                .accessKey("access-key")
                                .secretKeyRef(SecretRefData.builder().decryptedValue("secret".toCharArray()).build())
                                .build())
                    .build())
            .build();
    ConnectorInfoDTO connectorInfoDTO = ConnectorInfoDTO.builder()
                                            .connectorType(ConnectorType.GCP)
                                            .connectorConfig(awsConnectorDTO)
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
                                     .scope(Scope.PROJECT)
                                     .build();

    ConnectorResponseDTO connectorResponse = getConnector();
    when(connectorService.get(ACCOUNT_ID, ORG_IDENTIFIER, PROJECT_IDENTIFIER, "identifier"))
        .thenReturn(Optional.of(connectorResponse));

    EncryptedDataDetail encryptedDataDetail = EncryptedDataDetail.builder().build();

    when(secretManagerClientService.getEncryptionDetails(any(), any()))
        .thenReturn(Lists.newArrayList(encryptedDataDetail));

    when(serviceHelper.getBaseNGAccess(ACCOUNT_ID, ORG_IDENTIFIER, PROJECT_IDENTIFIER))
        .thenReturn(BaseNGAccess.builder()
                        .accountIdentifier(ACCOUNT_ID)
                        .orgIdentifier(ORG_IDENTIFIER)
                        .projectIdentifier(PROJECT_IDENTIFIER)
                        .build());
    ArtifactBuildDetailsNG artifactBuildDetailsNG = ArtifactBuildDetailsNG.builder().number("tag").build();

    EcrArtifactDelegateResponse ecrArtifactDelegateResponse =
        new EcrArtifactDelegateResponse(artifactBuildDetailsNG, null, null, null, null, null, null);

    when(serviceHelper.getResponseData(any(), any(), any(), any()))
        .thenReturn(ArtifactTaskResponse.builder()
                        .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                        .artifactTaskExecutionResponse(
                            ArtifactTaskExecutionResponse.builder()
                                .artifactDelegateResponses(Collections.singletonList(ecrArtifactDelegateResponse))
                                .build())
                        .build());

    EcrResponseDTO ecrResponseDTO =
        ecrResourceService.getBuildDetails(connectorRef, IMAGE_PATH, REGION, ORG_IDENTIFIER, PROJECT_IDENTIFIER);
    assertThat(ecrResponseDTO).isNotNull();
    assertThat(ecrResponseDTO.getBuildDetailsList().get(0).getTag()).isEqualTo("tag");
  }

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testvalidateArtifactServer() {
    IdentifierRef connectorRef = IdentifierRef.builder()
                                     .accountIdentifier(ACCOUNT_ID)
                                     .identifier("identifier")
                                     .projectIdentifier(PROJECT_IDENTIFIER)
                                     .orgIdentifier(ORG_IDENTIFIER)
                                     .scope(Scope.PROJECT)
                                     .build();

    ConnectorResponseDTO connectorResponse = getConnector();
    when(connectorService.get(ACCOUNT_ID, ORG_IDENTIFIER, PROJECT_IDENTIFIER, "identifier"))
        .thenReturn(Optional.of(connectorResponse));

    EncryptedDataDetail encryptedDataDetail = EncryptedDataDetail.builder().build();

    when(secretManagerClientService.getEncryptionDetails(any(), any()))
        .thenReturn(Lists.newArrayList(encryptedDataDetail));

    when(serviceHelper.getBaseNGAccess(ACCOUNT_ID, ORG_IDENTIFIER, PROJECT_IDENTIFIER))
        .thenReturn(BaseNGAccess.builder()
                        .accountIdentifier(ACCOUNT_ID)
                        .orgIdentifier(ORG_IDENTIFIER)
                        .projectIdentifier(PROJECT_IDENTIFIER)
                        .build());

    when(serviceHelper.getResponseData(any(), any(), any(), any()))
        .thenReturn(ArtifactTaskResponse.builder()
                        .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                        .artifactTaskExecutionResponse(
                            ArtifactTaskExecutionResponse.builder().isArtifactServerValid(true).build())
                        .build());

    Boolean res =
        ecrResourceService.validateArtifactServer(connectorRef, IMAGE_PATH, ORG_IDENTIFIER, PROJECT_IDENTIFIER, REGION);

    assertThat(res).isTrue();
  }

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testvalidateArtifactSource() {
    IdentifierRef connectorRef = IdentifierRef.builder()
                                     .accountIdentifier(ACCOUNT_ID)
                                     .identifier("identifier")
                                     .projectIdentifier(PROJECT_IDENTIFIER)
                                     .orgIdentifier(ORG_IDENTIFIER)
                                     .scope(Scope.PROJECT)
                                     .build();

    ConnectorResponseDTO connectorResponse = getConnector();
    when(connectorService.get(ACCOUNT_ID, ORG_IDENTIFIER, PROJECT_IDENTIFIER, "identifier"))
        .thenReturn(Optional.of(connectorResponse));

    EncryptedDataDetail encryptedDataDetail = EncryptedDataDetail.builder().build();

    when(secretManagerClientService.getEncryptionDetails(any(), any()))
        .thenReturn(Lists.newArrayList(encryptedDataDetail));

    when(serviceHelper.getBaseNGAccess(ACCOUNT_ID, ORG_IDENTIFIER, PROJECT_IDENTIFIER))
        .thenReturn(BaseNGAccess.builder()
                        .accountIdentifier(ACCOUNT_ID)
                        .orgIdentifier(ORG_IDENTIFIER)
                        .projectIdentifier(PROJECT_IDENTIFIER)
                        .build());

    when(serviceHelper.getResponseData(any(), any(), any(), any()))
        .thenReturn(ArtifactTaskResponse.builder()
                        .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                        .artifactTaskExecutionResponse(
                            ArtifactTaskExecutionResponse.builder().isArtifactServerValid(true).build())
                        .build());

    Boolean res =
        ecrResourceService.validateArtifactSource(IMAGE_PATH, connectorRef, REGION, ORG_IDENTIFIER, PROJECT_IDENTIFIER);

    assertThat(res).isTrue();
  }
  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testGetImages() {
    IdentifierRef connectorRef = IdentifierRef.builder()
                                     .accountIdentifier(ACCOUNT_ID)
                                     .identifier("identifier")
                                     .projectIdentifier(PROJECT_IDENTIFIER)
                                     .orgIdentifier(ORG_IDENTIFIER)
                                     .scope(Scope.PROJECT)
                                     .build();

    ConnectorResponseDTO connectorResponse = getConnector();
    when(connectorService.get(ACCOUNT_ID, ORG_IDENTIFIER, PROJECT_IDENTIFIER, "identifier"))
        .thenReturn(Optional.of(connectorResponse));

    EncryptedDataDetail encryptedDataDetail = EncryptedDataDetail.builder().build();

    when(secretManagerClientService.getEncryptionDetails(any(), any()))
        .thenReturn(Lists.newArrayList(encryptedDataDetail));

    when(serviceHelper.getBaseNGAccess(ACCOUNT_ID, ORG_IDENTIFIER, PROJECT_IDENTIFIER))
        .thenReturn(BaseNGAccess.builder()
                        .accountIdentifier(ACCOUNT_ID)
                        .orgIdentifier(ORG_IDENTIFIER)
                        .projectIdentifier(PROJECT_IDENTIFIER)
                        .build());

    // list ls
    List<String> ls = new ArrayList<>();
    ls.add("first");
    ls.add("second");

    when(serviceHelper.getResponseData(any(), any(), any(), any()))
        .thenReturn(
            ArtifactTaskResponse.builder()
                .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                .artifactTaskExecutionResponse(
                    ArtifactTaskExecutionResponse.builder().artifactImages(ls).isArtifactServerValid(true).build())
                .build());

    EcrListImagesDTO ecrListImagesDTO =
        ecrResourceService.getImages(connectorRef, REGION, ORG_IDENTIFIER, PROJECT_IDENTIFIER);

    assertThat(ecrListImagesDTO.getImages()).isEqualTo(ls);
  }

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testgetSuccessfulBuild() {
    EcrRequestDTO ecrRequestDTO = EcrRequestDTO.builder().region(REGION).tag(TAG).build();

    ConnectorResponseDTO connectorResponse = getConnector();
    when(connectorService.get(ACCOUNT_ID, ORG_IDENTIFIER, PROJECT_IDENTIFIER, IDENTIFIER))
        .thenReturn(Optional.of(connectorResponse));

    EncryptedDataDetail encryptedDataDetail = EncryptedDataDetail.builder().build();

    when(secretManagerClientService.getEncryptionDetails(any(), any()))
        .thenReturn(Lists.newArrayList(encryptedDataDetail));

    when(serviceHelper.getBaseNGAccess(ACCOUNT_ID, ORG_IDENTIFIER, PROJECT_IDENTIFIER))
        .thenReturn(BaseNGAccess.builder()
                        .accountIdentifier(ACCOUNT_ID)
                        .orgIdentifier(ORG_IDENTIFIER)
                        .projectIdentifier(PROJECT_IDENTIFIER)
                        .build());
    ArtifactBuildDetailsNG artifactBuildDetailsNG = ArtifactBuildDetailsNG.builder().number(TAG).build();

    EcrArtifactDelegateResponse ecrArtifactDelegateResponse =
        new EcrArtifactDelegateResponse(artifactBuildDetailsNG, null, null, null, null, null, null);

    when(serviceHelper.getResponseData(any(), any(), any(), any()))
        .thenReturn(ArtifactTaskResponse.builder()
                        .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                        .artifactTaskExecutionResponse(
                            ArtifactTaskExecutionResponse.builder()
                                .artifactDelegateResponses(Collections.singletonList(ecrArtifactDelegateResponse))
                                .build())
                        .build());

    EcrBuildDetailsDTO ecrResponseDTO = ecrResourceService.getSuccessfulBuild(
        IDENTIFIER_REF, IMAGE_PATH, ecrRequestDTO, ORG_IDENTIFIER, PROJECT_IDENTIFIER);

    assertThat(ecrResponseDTO).isNotNull();

    assertThat(ecrResponseDTO.getTag()).isEqualTo(TAG);
  }
  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testgetSuccessfulBuildException() {
    EcrRequestDTO ecrRequestDTO = EcrRequestDTO.builder().region(REGION).tag(TAG).build();

    ConnectorResponseDTO connectorResponse = getConnector();
    when(connectorService.get(ACCOUNT_ID, ORG_IDENTIFIER, PROJECT_IDENTIFIER, IDENTIFIER))
        .thenReturn(Optional.of(connectorResponse));

    EncryptedDataDetail encryptedDataDetail = EncryptedDataDetail.builder().build();

    when(secretManagerClientService.getEncryptionDetails(any(), any()))
        .thenReturn(Lists.newArrayList(encryptedDataDetail));

    when(serviceHelper.getBaseNGAccess(ACCOUNT_ID, ORG_IDENTIFIER, PROJECT_IDENTIFIER))
        .thenReturn(BaseNGAccess.builder()
                        .accountIdentifier(ACCOUNT_ID)
                        .orgIdentifier(ORG_IDENTIFIER)
                        .projectIdentifier(PROJECT_IDENTIFIER)
                        .build());
    ArtifactBuildDetailsNG artifactBuildDetailsNG = ArtifactBuildDetailsNG.builder().number(TAG).build();

    EcrArtifactDelegateResponse ecrArtifactDelegateResponse =
        new EcrArtifactDelegateResponse(artifactBuildDetailsNG, null, null, null, null, null, null);

    when(serviceHelper.getResponseData(any(), any(), any(), any()))
        .thenReturn(
            ArtifactTaskResponse.builder()
                .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                .artifactTaskExecutionResponse(
                    ArtifactTaskExecutionResponse.builder().artifactDelegateResponses(Collections.emptyList()).build())
                .build());
    assertThatThrownBy(()
                           -> ecrResourceService.getSuccessfulBuild(
                               IDENTIFIER_REF, IMAGE_PATH, ecrRequestDTO, ORG_IDENTIFIER, PROJECT_IDENTIFIER))
        .isInstanceOf(ArtifactServerException.class)
        .hasMessage("Ecr get last successful build task failure.");
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void testGetSuccessfulBuild_ImagePath_Null() {
    assertThatThrownBy(()
                           -> ecrResourceService.getSuccessfulBuild(
                               IDENTIFIER_REF, null, ECR_REQUEST_DTO, ORG_IDENTIFIER, PROJECT_IDENTIFIER))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(IMAGE_PATH_MESSAGE);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void testGetSuccessfulBuild_ImagePath_Empty() {
    assertThatThrownBy(()
                           -> ecrResourceService.getSuccessfulBuild(
                               IDENTIFIER_REF, "", ECR_REQUEST_DTO, ORG_IDENTIFIER, PROJECT_IDENTIFIER))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(IMAGE_PATH_MESSAGE);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void testGetSuccessfulBuild_ImagePath_Input() {
    assertThatThrownBy(()
                           -> ecrResourceService.getSuccessfulBuild(
                               IDENTIFIER_REF, INPUT, ECR_REQUEST_DTO, ORG_IDENTIFIER, PROJECT_IDENTIFIER))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(IMAGE_PATH_MESSAGE);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void testGetSuccessfulBuild_Region_Null() {
    assertThatThrownBy(()
                           -> ecrResourceService.getSuccessfulBuild(IDENTIFIER_REF, IMAGE_PATH,
                               EcrRequestDTO.builder().build(), ORG_IDENTIFIER, PROJECT_IDENTIFIER))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(REGION_MESSAGE);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void testGetSuccessfulBuild_Region_Empty() {
    assertThatThrownBy(()
                           -> ecrResourceService.getSuccessfulBuild(IDENTIFIER_REF, IMAGE_PATH,
                               EcrRequestDTO.builder().region("").build(), ORG_IDENTIFIER, PROJECT_IDENTIFIER))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(REGION_MESSAGE);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void testGetSuccessfulBuild_Region_Input() {
    assertThatThrownBy(()
                           -> ecrResourceService.getSuccessfulBuild(IDENTIFIER_REF, IMAGE_PATH,
                               EcrRequestDTO.builder().region(INPUT).build(), ORG_IDENTIFIER, PROJECT_IDENTIFIER))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(REGION_MESSAGE);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void testGetSuccessfulBuild_Tag_Null() {
    assertThatThrownBy(()
                           -> ecrResourceService.getSuccessfulBuild(
                               IDENTIFIER_REF, IMAGE_PATH, ECR_REQUEST_DTO, ORG_IDENTIFIER, PROJECT_IDENTIFIER))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(TAG_TAG_REGEX_MESSAGE);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void testGetSuccessfulBuild_Tag_Empty() {
    assertThatThrownBy(
        ()
            -> ecrResourceService.getSuccessfulBuild(IDENTIFIER_REF, IMAGE_PATH,
                EcrRequestDTO.builder().region(REGION).tag("").build(), ORG_IDENTIFIER, PROJECT_IDENTIFIER))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(TAG_TAG_REGEX_MESSAGE);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void testGetSuccessfulBuild_Tag_Input() {
    assertThatThrownBy(
        ()
            -> ecrResourceService.getSuccessfulBuild(IDENTIFIER_REF, IMAGE_PATH,
                EcrRequestDTO.builder().region(REGION).tag(INPUT).build(), ORG_IDENTIFIER, PROJECT_IDENTIFIER))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(TAG_TAG_REGEX_MESSAGE);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void testGetSuccessfulBuild_TagRegex_Null() {
    assertThatThrownBy(()
                           -> ecrResourceService.getSuccessfulBuild(
                               IDENTIFIER_REF, IMAGE_PATH, ECR_REQUEST_DTO, ORG_IDENTIFIER, PROJECT_IDENTIFIER))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(TAG_TAG_REGEX_MESSAGE);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void testGetSuccessfulBuild_TagRegex_Empty() {
    assertThatThrownBy(
        ()
            -> ecrResourceService.getSuccessfulBuild(IDENTIFIER_REF, IMAGE_PATH,
                EcrRequestDTO.builder().region(REGION).tagRegex("").build(), ORG_IDENTIFIER, PROJECT_IDENTIFIER))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(TAG_TAG_REGEX_MESSAGE);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void testGetSuccessfulBuild_TagRegex_Input() {
    assertThatThrownBy(
        ()
            -> ecrResourceService.getSuccessfulBuild(IDENTIFIER_REF, IMAGE_PATH,
                EcrRequestDTO.builder().region(REGION).tagRegex(INPUT).build(), ORG_IDENTIFIER, PROJECT_IDENTIFIER))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(TAG_TAG_REGEX_MESSAGE);
  }
}
