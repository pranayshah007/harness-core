/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.artifacts.googlecloudsource;

import static io.harness.rule.OwnerRule.PRAGYESH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorCredentialDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpManualDetailsDTO;
import io.harness.delegate.task.artifacts.ArtifactSourceType;
import io.harness.delegate.task.artifacts.ArtifactTaskType;
import io.harness.delegate.task.artifacts.request.ArtifactTaskParameters;
import io.harness.delegate.task.artifacts.response.ArtifactTaskExecutionResponse;
import io.harness.delegate.task.artifacts.response.ArtifactTaskResponse;
import io.harness.encryption.SecretRefData;
import io.harness.eraro.ErrorCode;
import io.harness.logging.CommandExecutionStatus;
import io.harness.rule.Owner;

import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@OwnedBy(HarnessTeam.CDP)
public class GoogleCloudSourceArtifactTaskHelperTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  @InjectMocks private GoogleCloudSourceArtifactTaskHelper googleCloudSourceArtifactTaskHelper;
  @Mock GoogleCloudSourceArtifactTaskHandler artifactTaskHandler;
  private static String GCP_PASSWORD = "password";
  private static String PATH = "path/abc";
  private static String PROJECT = "cd-play";
  private static String REPO = "repo";

  @Test
  @Owner(developers = PRAGYESH)
  @Category(UnitTests.class)
  public void testGetArtifactCollectResponse() {
    doNothing().when(artifactTaskHandler).decryptRequestDTOs(any());
    ArtifactTaskExecutionResponse artifactTaskExecutionResponse =
        ArtifactTaskExecutionResponse.builder()
            .artifactDelegateResponse(GoogleCloudSourceArtifactDelegateResponse.builder()
                                          .sourceType(ArtifactSourceType.GOOGLE_CLOUD_SOURCE_ARTIFACT)
                                          .project(PROJECT)
                                          .repository(REPO)
                                          .sourceDirectory(PATH)
                                          .build())
            .build();
    ArtifactTaskParameters artifactTaskParameters = getRequest(PATH, ArtifactTaskType.GET_LAST_SUCCESSFUL_BUILD);
    GoogleCloudSourceArtifactDelegateRequest googleCloudSourceArtifactDelegateRequest =
        (GoogleCloudSourceArtifactDelegateRequest) artifactTaskParameters.getAttributes();
    when(artifactTaskHandler.getLastSuccessfulBuild(googleCloudSourceArtifactDelegateRequest))
        .thenReturn(artifactTaskExecutionResponse);
    ArtifactTaskResponse actualArtifactTaskResponse =
        googleCloudSourceArtifactTaskHelper.getArtifactCollectResponse(artifactTaskParameters, null);
    assertThat(actualArtifactTaskResponse).isNotNull();
    assertThat(actualArtifactTaskResponse.getArtifactTaskExecutionResponse().getArtifactDelegateResponses().size())
        .isEqualTo(1);
    assertThat(actualArtifactTaskResponse.getArtifactTaskExecutionResponse().getArtifactDelegateResponses().get(0))
        .isInstanceOf(GoogleCloudSourceArtifactDelegateResponse.class);
    GoogleCloudSourceArtifactDelegateResponse attributes =
        (GoogleCloudSourceArtifactDelegateResponse) actualArtifactTaskResponse.getArtifactTaskExecutionResponse()
            .getArtifactDelegateResponses()
            .get(0);
    assertThat(attributes.getSourceDirectory()).isEqualTo(PATH);
  }

  @Test
  @Owner(developers = PRAGYESH)
  @Category(UnitTests.class)
  public void testGetArtifactCollectResponseFailure() {
    doNothing().when(artifactTaskHandler).decryptRequestDTOs(any());
    ArtifactTaskResponse actualArtifactTaskResponse = googleCloudSourceArtifactTaskHelper.getArtifactCollectResponse(
        getRequest(PATH, ArtifactTaskType.GET_ARTIFACT_PATH), null);
    assertThat(actualArtifactTaskResponse).isNotNull();
    assertThat(actualArtifactTaskResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.FAILURE);
    assertThat(actualArtifactTaskResponse.getErrorCode()).isEqualTo(ErrorCode.INVALID_ARGUMENT);
  }

  private ArtifactTaskParameters getRequest(String path, ArtifactTaskType artifactTaskType) {
    GcpConnectorCredentialDTO gcpConnectorCredentialDTO =
        GcpConnectorCredentialDTO.builder().config(createGcpConnectorCredentialConfig()).build();
    return ArtifactTaskParameters.builder()
        .artifactTaskType(artifactTaskType)
        .attributes(GoogleCloudSourceArtifactDelegateRequest.builder()
                        .sourceType(ArtifactSourceType.GOOGLE_CLOUD_SOURCE_ARTIFACT)
                        .project(PROJECT)
                        .repository(REPO)
                        .sourceDirectory(path)
                        .gcpConnectorDTO(createGcpConnector(gcpConnectorCredentialDTO))
                        .build())
        .build();
  }

  private GcpConnectorDTO createGcpConnector(GcpConnectorCredentialDTO GcpConnectorCredentialDTO) {
    return GcpConnectorDTO.builder().credential(GcpConnectorCredentialDTO).build();
  }

  private GcpManualDetailsDTO createGcpConnectorCredentialConfig() {
    return GcpManualDetailsDTO.builder()
        .secretKeyRef(SecretRefData.builder().decryptedValue(GCP_PASSWORD.toCharArray()).build())
        .build();
  }
}
