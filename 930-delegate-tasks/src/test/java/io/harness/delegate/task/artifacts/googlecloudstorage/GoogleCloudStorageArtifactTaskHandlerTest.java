/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.artifacts.googlecloudstorage;

import static io.harness.rule.OwnerRule.PRAGYESH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorCredentialDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpManualDetailsDTO;
import io.harness.delegate.task.artifacts.ArtifactSourceType;
import io.harness.delegate.task.artifacts.artifactory.ArtifactoryArtifactDelegateResponse;
import io.harness.delegate.task.artifacts.response.ArtifactBuildDetailsNG;
import io.harness.delegate.task.artifacts.response.ArtifactTaskExecutionResponse;
import io.harness.encryption.SecretRefData;
import io.harness.exception.InvalidRequestException;
import io.harness.googlecloudstorage.GcsHelperService;
import io.harness.rule.Owner;

import software.wings.helpers.ext.jenkins.BuildDetails;

import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@OwnedBy(HarnessTeam.CDP)
public class GoogleCloudStorageArtifactTaskHandlerTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock GcsHelperService gcsHelperService;
  @InjectMocks GoogleCloudStorageArtifactTaskHandler artifactTaskHandler;
  private static String GCP_PASSWORD = "password";
  private static String ARTIFACT_PATH = "path/abc";
  private static String PROJECT = "cd-play";
  private static String BUCKET = "bucket";

  @Test
  @Owner(developers = PRAGYESH)
  @Category(UnitTests.class)
  public void testGetLastSuccessfulBuild() {
    BuildDetails buildDetails = BuildDetails.Builder.aBuildDetails().withArtifactPath(ARTIFACT_PATH).build();
    List<BuildDetails> builds = List.of(buildDetails);
    doReturn(builds).when(gcsHelperService).listBuilds(any());
    ArtifactTaskExecutionResponse actualArtifactTaskExecutionResponse =
        artifactTaskHandler.getLastSuccessfulBuild(getRequest(ARTIFACT_PATH));
    assertThat(actualArtifactTaskExecutionResponse).isNotNull();
    assertThat(actualArtifactTaskExecutionResponse.getArtifactDelegateResponses().size()).isEqualTo(1);
    assertThat(actualArtifactTaskExecutionResponse.getArtifactDelegateResponses().get(0))
        .isInstanceOf(GoogleCloudStorageArtifactDelegateResponse.class);
    GoogleCloudStorageArtifactDelegateResponse attributes =
        (GoogleCloudStorageArtifactDelegateResponse) actualArtifactTaskExecutionResponse.getArtifactDelegateResponses()
            .get(0);
    assertThat(attributes.getArtifactPath()).isEqualTo(ARTIFACT_PATH);
  }

  @Test
  @Owner(developers = PRAGYESH)
  @Category(UnitTests.class)
  public void testGetBuild() {
    BuildDetails buildDetails = BuildDetails.Builder.aBuildDetails().withArtifactPath(ARTIFACT_PATH).build();
    List<BuildDetails> builds = List.of(buildDetails);

    doReturn(builds).when(gcsHelperService).listBuilds(any());
    ArtifactTaskExecutionResponse actualArtifactTaskExecutionResponse =
        artifactTaskHandler.getBuilds(getRequest(ARTIFACT_PATH));
    assertThat(actualArtifactTaskExecutionResponse).isNotNull();
    assertThat(actualArtifactTaskExecutionResponse.getArtifactDelegateResponses().size()).isEqualTo(1);
    assertThat(actualArtifactTaskExecutionResponse.getArtifactDelegateResponses().get(0))
        .isInstanceOf(GoogleCloudStorageArtifactDelegateResponse.class);
    GoogleCloudStorageArtifactDelegateResponse attributes =
        (GoogleCloudStorageArtifactDelegateResponse) actualArtifactTaskExecutionResponse.getArtifactDelegateResponses()
            .get(0);
    assertThat(attributes.getArtifactPath()).isEqualTo(ARTIFACT_PATH);
  }

  @Test
  @Owner(developers = PRAGYESH)
  @Category(UnitTests.class)
  public void testGetLastSuccessfulBuildFailure() {
    BuildDetails buildDetails = BuildDetails.Builder.aBuildDetails().withArtifactPath(ARTIFACT_PATH).build();
    List<BuildDetails> builds = List.of(buildDetails);

    doReturn(builds).when(gcsHelperService).listBuilds(any());
    assertThatThrownBy(() -> artifactTaskHandler.getLastSuccessfulBuild(getRequest("")))
        .isInstanceOf(InvalidRequestException.class);
  }

  private GoogleCloudStorageArtifactDelegateRequest getRequest(String artifactPath) {
    GcpConnectorCredentialDTO gcpConnectorCredentialDTO =
        GcpConnectorCredentialDTO.builder().config(createGcpConnectorCredentialConfig()).build();
    return GoogleCloudStorageArtifactDelegateRequest.builder()
        .sourceType(ArtifactSourceType.GOOGLE_CLOUD_STORAGE_ARTIFACT)
        .project(PROJECT)
        .bucket(BUCKET)
        .artifactPath(artifactPath)
        .gcpConnectorDTO(createGcpConnector(gcpConnectorCredentialDTO))
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
