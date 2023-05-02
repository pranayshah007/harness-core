/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.artifact.resources.acr.mappers;

import static io.harness.rule.OwnerRule.ABHISHEK;
import static io.harness.rule.OwnerRule.vivekveman;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.category.element.UnitTests;
import io.harness.cdng.artifact.resources.acr.dtos.AcrRegistriesDTO;
import io.harness.cdng.artifact.resources.acr.dtos.AcrRepositoriesDTO;
import io.harness.cdng.artifact.resources.acr.dtos.AcrRequestDTO;
import io.harness.cdng.artifact.resources.acr.service.AcrResourceServiceImpl;
import io.harness.cdng.azure.AzureHelperService;
import io.harness.connector.services.ConnectorService;
import io.harness.delegate.beans.azure.AcrBuildDetailsDTO;
import io.harness.delegate.beans.azure.AcrResponseDTO;
import io.harness.delegate.beans.azure.response.AzureRegistriesResponse;
import io.harness.delegate.beans.azure.response.AzureRepositoriesResponse;
import io.harness.delegate.beans.connector.azureconnector.AzureConnectorDTO;
import io.harness.delegate.task.artifacts.ArtifactTaskType;
import io.harness.delegate.task.artifacts.azure.AcrArtifactDelegateResponse;
import io.harness.delegate.task.artifacts.response.ArtifactBuildDetailsNG;
import io.harness.delegate.task.artifacts.response.ArtifactTaskExecutionResponse;
import io.harness.delegate.task.artifacts.response.ArtifactTaskResponse;
import io.harness.logging.CommandExecutionStatus;
import io.harness.ng.core.BaseNGAccess;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptedDataDetail;

import io.fabric8.utils.Lists;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@OwnedBy(HarnessTeam.CDC)
public class AcrResourceServiceImplTest extends CategoryTest {
  private static String ACCOUNT_ID = "accountId";

  private static String ORG_IDENTIFIER = "orgIdentifier";

  private static String PROJECT_IDENTIFIER = "projectIdentifier";

  private static String SUBSCRIPTION = "SUBSCRIPTION";

  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  @Mock ConnectorService connectorService;

  @InjectMocks AcrResourceServiceImpl acrResourceService;

  @Mock AzureHelperService azureHelperService;

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testGetBuildDetails() {
    AzureConnectorDTO azureArtifactsConnectorDTO = AzureConnectorDTO.builder().build();

    IdentifierRef identifierRef = IdentifierRef.builder()
                                      .accountIdentifier(ACCOUNT_ID)
                                      .identifier("identifier")
                                      .projectIdentifier(PROJECT_IDENTIFIER)
                                      .orgIdentifier(ORG_IDENTIFIER)
                                      .build();

    EncryptedDataDetail encryptedDataDetail = EncryptedDataDetail.builder().build();

    when(azureHelperService.getEncryptionDetails(any(), any())).thenReturn(Lists.newArrayList(encryptedDataDetail));

    ArtifactBuildDetailsNG artifactBuildDetailsNG = ArtifactBuildDetailsNG.builder().number("tag").build();

    AcrArtifactDelegateResponse ecrArtifactDelegateResponse =
        new AcrArtifactDelegateResponse(artifactBuildDetailsNG, null, null, null, null, null, null);

    when(azureHelperService.executeSyncTask(any(), any(), any()))
        .thenReturn(ArtifactTaskResponse.builder()
                        .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                        .artifactTaskExecutionResponse(
                            ArtifactTaskExecutionResponse.builder()
                                .artifactDelegateResponses(Collections.singletonList(ecrArtifactDelegateResponse))
                                .build())
                        .build());

    doReturn(azureArtifactsConnectorDTO).when(azureHelperService).getConnector(any());

    when(azureHelperService.getBaseNGAccess(ACCOUNT_ID, ORG_IDENTIFIER, PROJECT_IDENTIFIER))
        .thenReturn(BaseNGAccess.builder()
                        .accountIdentifier(ACCOUNT_ID)
                        .orgIdentifier(ORG_IDENTIFIER)
                        .projectIdentifier(PROJECT_IDENTIFIER)
                        .build());

    AcrResponseDTO acrResponseDTO = AcrResponseDTO.builder().build();

    when(azureHelperService.getAcrResponseDTO(any())).thenReturn(acrResponseDTO);

    AcrResponseDTO acrResponseDTOres = acrResourceService.getBuildDetails(
        identifierRef, SUBSCRIPTION, "registry", "repository", ORG_IDENTIFIER, PROJECT_IDENTIFIER);

    assertThat(acrResponseDTO).isEqualTo(acrResponseDTOres);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void testGetLastSuccessfulBuild() {
    AzureConnectorDTO azureArtifactsConnectorDTO = AzureConnectorDTO.builder().build();

    IdentifierRef identifierRef = IdentifierRef.builder()
                                      .accountIdentifier(ACCOUNT_ID)
                                      .identifier("identifier")
                                      .projectIdentifier(PROJECT_IDENTIFIER)
                                      .orgIdentifier(ORG_IDENTIFIER)
                                      .build();
    BaseNGAccess baseNGAccess = BaseNGAccess.builder()
                                    .accountIdentifier(ACCOUNT_ID)
                                    .orgIdentifier(ORG_IDENTIFIER)
                                    .projectIdentifier(PROJECT_IDENTIFIER)
                                    .build();

    ArtifactBuildDetailsNG artifactBuildDetailsNG = ArtifactBuildDetailsNG.builder().number("tag").build();
    EncryptedDataDetail encryptedDataDetail = EncryptedDataDetail.builder().build();

    AcrArtifactDelegateResponse ecrArtifactDelegateResponse =
        new AcrArtifactDelegateResponse(artifactBuildDetailsNG, null, null, null, null, null, null);
    ArtifactTaskExecutionResponse artifactTaskExecutionResponse =
        ArtifactTaskExecutionResponse.builder()
            .artifactDelegateResponses(Collections.singletonList(ecrArtifactDelegateResponse))
            .build();

    when(azureHelperService.getEncryptionDetails(azureArtifactsConnectorDTO, baseNGAccess))
        .thenReturn(Lists.newArrayList(encryptedDataDetail));
    when(azureHelperService.executeSyncTask(any(), eq(baseNGAccess), eq(ArtifactTaskType.GET_LAST_SUCCESSFUL_BUILD),
             eq("ACR Artifact Get Last Successful Build task failure due to error")))
        .thenReturn(ArtifactTaskResponse.builder()
                        .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                        .artifactTaskExecutionResponse(artifactTaskExecutionResponse)
                        .build());
    doReturn(azureArtifactsConnectorDTO).when(azureHelperService).getConnector(identifierRef);
    when(azureHelperService.getBaseNGAccess(ACCOUNT_ID, ORG_IDENTIFIER, PROJECT_IDENTIFIER)).thenReturn(baseNGAccess);

    AcrBuildDetailsDTO acrBuildDetailsDTO = AcrBuildDetailsDTO.builder().build();
    AcrResponseDTO acrResponseDTO =
        AcrResponseDTO.builder().buildDetailsList(Arrays.asList(acrBuildDetailsDTO)).build();

    when(azureHelperService.getAcrResponseDTO(artifactTaskExecutionResponse)).thenReturn(acrResponseDTO);

    AcrBuildDetailsDTO acrBuildDetailsDTOResult = acrResourceService.getLastSuccessfulBuild(identifierRef, SUBSCRIPTION,
        "registry", "repository", ORG_IDENTIFIER, PROJECT_IDENTIFIER, AcrRequestDTO.builder().build());

    assertThat(acrBuildDetailsDTO).isSameAs(acrBuildDetailsDTOResult);
    verify(azureHelperService).getAcrResponseDTO(artifactTaskExecutionResponse);
  }

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testGetRepositories() {
    AzureConnectorDTO azureArtifactsConnectorDTO = AzureConnectorDTO.builder().build();

    IdentifierRef identifierRef = IdentifierRef.builder()
                                      .accountIdentifier(ACCOUNT_ID)
                                      .identifier("identifier")
                                      .projectIdentifier(PROJECT_IDENTIFIER)
                                      .orgIdentifier(ORG_IDENTIFIER)
                                      .build();

    EncryptedDataDetail encryptedDataDetail = EncryptedDataDetail.builder().build();

    when(azureHelperService.getEncryptionDetails(any(), any())).thenReturn(Lists.newArrayList(encryptedDataDetail));

    AzureRepositoriesResponse acrArtifactDelegateResponse =
        AzureRepositoriesResponse.builder().repositories(Collections.singletonList("first")).build();

    when(azureHelperService.executeSyncTask(any(), any(), any())).thenReturn(acrArtifactDelegateResponse);

    doReturn(azureArtifactsConnectorDTO).when(azureHelperService).getConnector(any());

    when(azureHelperService.getBaseNGAccess(ACCOUNT_ID, ORG_IDENTIFIER, PROJECT_IDENTIFIER))
        .thenReturn(BaseNGAccess.builder()
                        .accountIdentifier(ACCOUNT_ID)
                        .orgIdentifier(ORG_IDENTIFIER)
                        .projectIdentifier(PROJECT_IDENTIFIER)
                        .build());

    AcrResponseDTO acrResponseDTO = AcrResponseDTO.builder().build();

    when(azureHelperService.getAcrResponseDTO(any())).thenReturn(acrResponseDTO);

    AcrRepositoriesDTO acrResponseDTOres =
        acrResourceService.getRepositories(identifierRef, ORG_IDENTIFIER, PROJECT_IDENTIFIER, SUBSCRIPTION, "registry");

    assertThat(acrResponseDTOres.getRepositories().get(0).getRepository()).isEqualTo("first");
  }
  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testGetRegistries() {
    AzureConnectorDTO azureArtifactsConnectorDTO = AzureConnectorDTO.builder().build();

    IdentifierRef identifierRef = IdentifierRef.builder()
                                      .accountIdentifier(ACCOUNT_ID)
                                      .identifier("identifier")
                                      .projectIdentifier(PROJECT_IDENTIFIER)
                                      .orgIdentifier(ORG_IDENTIFIER)
                                      .build();

    EncryptedDataDetail encryptedDataDetail = EncryptedDataDetail.builder().build();

    when(azureHelperService.getEncryptionDetails(any(), any())).thenReturn(Lists.newArrayList(encryptedDataDetail));

    AzureRegistriesResponse acrArtifactDelegateResponse =
        AzureRegistriesResponse.builder().containerRegistries(Collections.singletonList("first")).build();

    when(azureHelperService.executeSyncTask(any(), any(), any())).thenReturn(acrArtifactDelegateResponse);

    doReturn(azureArtifactsConnectorDTO).when(azureHelperService).getConnector(any());

    when(azureHelperService.getBaseNGAccess(ACCOUNT_ID, ORG_IDENTIFIER, PROJECT_IDENTIFIER))
        .thenReturn(BaseNGAccess.builder()
                        .accountIdentifier(ACCOUNT_ID)
                        .orgIdentifier(ORG_IDENTIFIER)
                        .projectIdentifier(PROJECT_IDENTIFIER)
                        .build());

    AcrResponseDTO acrResponseDTO = AcrResponseDTO.builder().build();

    when(azureHelperService.getAcrResponseDTO(any())).thenReturn(acrResponseDTO);

    AcrRegistriesDTO acrRegistriesDTO =
        acrResourceService.getRegistries(identifierRef, ORG_IDENTIFIER, PROJECT_IDENTIFIER, SUBSCRIPTION);

    assertThat(acrRegistriesDTO.getRegistries().get(0).getRegistry()).isEqualTo("first");
  }
}
