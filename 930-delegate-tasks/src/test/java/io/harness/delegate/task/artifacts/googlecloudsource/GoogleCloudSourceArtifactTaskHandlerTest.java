/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.artifacts.googlecloudsource;

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
public class GoogleCloudSourceArtifactTaskHandlerTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock GcsHelperService gcsHelperService;
  @InjectMocks GoogleCloudSourceArtifactTaskHandler artifactTaskHandler;
  private static String GCP_PASSWORD = "password";
  private static String PATH = "path/abc";
  private static String PROJECT = "cd-play";
  private static String REPO = "repo";

  @Test
  @Owner(developers = PRAGYESH)
  @Category(UnitTests.class)
  public void testGetLastSuccessfulBuild() {
    BuildDetails buildDetails = BuildDetails.Builder.aBuildDetails().withArtifactPath(PATH).build();
    List<BuildDetails> builds = List.of(buildDetails);
    doReturn(builds).when(gcsHelperService).listBuilds(any());
    ArtifactTaskExecutionResponse actualArtifactTaskExecutionResponse =
        artifactTaskHandler.getLastSuccessfulBuild(getRequest(PATH));
    assertThat(actualArtifactTaskExecutionResponse).isNotNull();
    assertThat(actualArtifactTaskExecutionResponse.getArtifactDelegateResponses().size()).isEqualTo(1);
    assertThat(actualArtifactTaskExecutionResponse.getArtifactDelegateResponses().get(0))
        .isInstanceOf(GoogleCloudSourceArtifactDelegateResponse.class);
    GoogleCloudSourceArtifactDelegateResponse attributes =
        (GoogleCloudSourceArtifactDelegateResponse) actualArtifactTaskExecutionResponse.getArtifactDelegateResponses()
            .get(0);
    assertThat(attributes.getSourceDirectory()).isEqualTo(PATH);
  }

  @Test
  @Owner(developers = PRAGYESH)
  @Category(UnitTests.class)
  public void testGetLastSuccessfulBuildFailure() {
    BuildDetails buildDetails = BuildDetails.Builder.aBuildDetails().withArtifactPath(PATH).build();
    List<BuildDetails> builds = List.of(buildDetails);

    doReturn(builds).when(gcsHelperService).listBuilds(any());
    assertThatThrownBy(() -> artifactTaskHandler.getLastSuccessfulBuild(getRequest("")))
        .isInstanceOf(InvalidRequestException.class);
  }

  private GoogleCloudSourceArtifactDelegateRequest getRequest(String path) {
    GcpConnectorCredentialDTO gcpConnectorCredentialDTO =
        GcpConnectorCredentialDTO.builder().config(createGcpConnectorCredentialConfig()).build();
    return GoogleCloudSourceArtifactDelegateRequest.builder()
        .sourceType(ArtifactSourceType.GOOGLE_CLOUD_SOURCE_ARTIFACT)
        .project(PROJECT)
        .repository(REPO)
        .sourceDirectory(path)
        .branch("master")
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
