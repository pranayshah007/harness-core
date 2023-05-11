/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.artifacts.googlecloudstorage;

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

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.CDP)
public class GoogleCloudStorageArtifactTaskHelperTest extends CategoryTest {
  @InjectMocks private GoogleCloudStorageArtifactTaskHelper googleCloudStorageArtifactTaskHelper;
  @Mock GoogleCloudStorageArtifactTaskHandler artifactTaskHandler;

  private static String GCP_PASSWORD = "password";
  private static String ARTIFACT_PATH = "path/abc";
  private static String PROJECT = "cd-play";
  private static String BUCKET = "bucket";
  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = PRAGYESH)
  @Category(UnitTests.class)
  public void testGetArtifactCollectResponse() {
    doNothing().when(artifactTaskHandler).decryptRequestDTOs(any());
    ArtifactTaskExecutionResponse artifactTaskExecutionResponse =
        ArtifactTaskExecutionResponse.builder()
            .artifactDelegateResponse(GoogleCloudStorageArtifactDelegateResponse.builder()
                                          .sourceType(ArtifactSourceType.GOOGLE_CLOUD_STORAGE_ARTIFACT)
                                          .project(PROJECT)
                                          .bucket(BUCKET)
                                          .artifactPath(ARTIFACT_PATH)
                                          .build())
            .build();
    ArtifactTaskParameters artifactTaskParameters =
        getRequest(ARTIFACT_PATH, ArtifactTaskType.GET_LAST_SUCCESSFUL_BUILD);
    GoogleCloudStorageArtifactDelegateRequest googleCloudStorageArtifactDelegateRequest =
        (GoogleCloudStorageArtifactDelegateRequest) artifactTaskParameters.getAttributes();
    when(artifactTaskHandler.getLastSuccessfulBuild(googleCloudStorageArtifactDelegateRequest))
        .thenReturn(artifactTaskExecutionResponse);
    ArtifactTaskResponse actualArtifactTaskResponse =
        googleCloudStorageArtifactTaskHelper.getArtifactCollectResponse(artifactTaskParameters, null);
    assertThat(actualArtifactTaskResponse).isNotNull();
    assertThat(actualArtifactTaskResponse.getArtifactTaskExecutionResponse().getArtifactDelegateResponses().size())
        .isEqualTo(1);
    assertThat(actualArtifactTaskResponse.getArtifactTaskExecutionResponse().getArtifactDelegateResponses().get(0))
        .isInstanceOf(GoogleCloudStorageArtifactDelegateResponse.class);
    GoogleCloudStorageArtifactDelegateResponse attributes =
        (GoogleCloudStorageArtifactDelegateResponse) actualArtifactTaskResponse.getArtifactTaskExecutionResponse()
            .getArtifactDelegateResponses()
            .get(0);
    assertThat(attributes.getArtifactPath()).isEqualTo(ARTIFACT_PATH);
  }

  @Test
  @Owner(developers = PRAGYESH)
  @Category(UnitTests.class)
  public void testGetArtifactCollectResponseFailure() {
    doNothing().when(artifactTaskHandler).decryptRequestDTOs(any());
    ArtifactTaskResponse actualArtifactTaskResponse = googleCloudStorageArtifactTaskHelper.getArtifactCollectResponse(
        getRequest(ARTIFACT_PATH, ArtifactTaskType.GET_ARTIFACT_PATH), null);
    assertThat(actualArtifactTaskResponse).isNotNull();
    assertThat(actualArtifactTaskResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.FAILURE);
    assertThat(actualArtifactTaskResponse.getErrorCode()).isEqualTo(ErrorCode.INVALID_ARGUMENT);
  }

  private ArtifactTaskParameters getRequest(String artifactPath, ArtifactTaskType artifactTaskType) {
    GcpConnectorCredentialDTO gcpConnectorCredentialDTO =
        GcpConnectorCredentialDTO.builder().config(createGcpConnectorCredentialConfig()).build();
    return ArtifactTaskParameters.builder()
        .artifactTaskType(artifactTaskType)
        .attributes(GoogleCloudStorageArtifactDelegateRequest.builder()
                        .sourceType(ArtifactSourceType.GOOGLE_CLOUD_STORAGE_ARTIFACT)
                        .project(PROJECT)
                        .bucket(BUCKET)
                        .artifactPath(artifactPath)
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
