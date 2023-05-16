/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.artifacts.docker;

import static io.harness.rule.OwnerRule.SAHIL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.connector.docker.DockerAuthenticationDTO;
import io.harness.delegate.beans.connector.docker.DockerConnectorDTO;
import io.harness.delegate.task.artifacts.ArtifactTaskType;
import io.harness.delegate.task.artifacts.request.ArtifactTaskParameters;
import io.harness.delegate.task.artifacts.response.ArtifactTaskExecutionResponse;
import io.harness.delegate.task.artifacts.response.ArtifactTaskResponse;
import io.harness.logging.CommandExecutionStatus;
import io.harness.rule.Owner;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class DockerArtifactTaskHelperTest extends CategoryTest {
  @Mock private DockerArtifactTaskHandler dockerArtifactTaskHandler;

  @InjectMocks private DockerArtifactTaskHelper dockerArtifactTaskHelper;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testGetArtifactCollectResponseGetLastSuccessfulBuild() {
    doNothing().when(dockerArtifactTaskHandler).decryptRequestDTOs(any());
    DockerArtifactDelegateRequest dockerArtifactDelegateRequest =
        DockerArtifactDelegateRequest.builder()
            .dockerConnectorDTO(DockerConnectorDTO.builder().auth(DockerAuthenticationDTO.builder().build()).build())
            .build();
    ArtifactTaskParameters artifactTaskParameters = ArtifactTaskParameters.builder()
                                                        .attributes(dockerArtifactDelegateRequest)
                                                        .artifactTaskType(ArtifactTaskType.GET_LAST_SUCCESSFUL_BUILD)
                                                        .build();
    ArtifactTaskExecutionResponse artifactTaskExecutionResponse = ArtifactTaskExecutionResponse.builder().build();
    when(dockerArtifactTaskHandler.getLastSuccessfulBuild(dockerArtifactDelegateRequest))
        .thenReturn(artifactTaskExecutionResponse);

    ArtifactTaskResponse artifactTaskResponse =
        dockerArtifactTaskHelper.getArtifactCollectResponse(artifactTaskParameters);
    assertThat(artifactTaskResponse).isNotNull();
    assertThat(artifactTaskResponse.getArtifactTaskExecutionResponse()).isEqualTo(artifactTaskExecutionResponse);

    verify(dockerArtifactTaskHandler).decryptRequestDTOs(any());
    verify(dockerArtifactTaskHandler).getLastSuccessfulBuild(dockerArtifactDelegateRequest);
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testGetArtifactCollectResponseGetBuilds() {
    doNothing().when(dockerArtifactTaskHandler).decryptRequestDTOs(any());
    DockerArtifactDelegateRequest dockerArtifactDelegateRequest =
        DockerArtifactDelegateRequest.builder()
            .dockerConnectorDTO(DockerConnectorDTO.builder().auth(DockerAuthenticationDTO.builder().build()).build())
            .build();
    ArtifactTaskParameters artifactTaskParameters = ArtifactTaskParameters.builder()
                                                        .attributes(dockerArtifactDelegateRequest)
                                                        .artifactTaskType(ArtifactTaskType.GET_BUILDS)
                                                        .build();
    ArtifactTaskExecutionResponse artifactTaskExecutionResponse = ArtifactTaskExecutionResponse.builder().build();
    when(dockerArtifactTaskHandler.getBuilds(dockerArtifactDelegateRequest)).thenReturn(artifactTaskExecutionResponse);

    ArtifactTaskResponse artifactTaskResponse =
        dockerArtifactTaskHelper.getArtifactCollectResponse(artifactTaskParameters);
    assertThat(artifactTaskResponse).isNotNull();
    assertThat(artifactTaskResponse.getArtifactTaskExecutionResponse()).isEqualTo(artifactTaskExecutionResponse);

    verify(dockerArtifactTaskHandler).decryptRequestDTOs(any());
    verify(dockerArtifactTaskHandler).getBuilds(dockerArtifactDelegateRequest);
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testGetArtifactCollectResponseGetLabels() {
    doNothing().when(dockerArtifactTaskHandler).decryptRequestDTOs(any());
    DockerArtifactDelegateRequest dockerArtifactDelegateRequest =
        DockerArtifactDelegateRequest.builder()
            .dockerConnectorDTO(DockerConnectorDTO.builder().auth(DockerAuthenticationDTO.builder().build()).build())
            .build();
    ArtifactTaskParameters artifactTaskParameters = ArtifactTaskParameters.builder()
                                                        .attributes(dockerArtifactDelegateRequest)
                                                        .artifactTaskType(ArtifactTaskType.GET_LABELS)
                                                        .build();
    ArtifactTaskExecutionResponse artifactTaskExecutionResponse = ArtifactTaskExecutionResponse.builder().build();
    when(dockerArtifactTaskHandler.getLabels(dockerArtifactDelegateRequest)).thenReturn(artifactTaskExecutionResponse);

    ArtifactTaskResponse artifactTaskResponse =
        dockerArtifactTaskHelper.getArtifactCollectResponse(artifactTaskParameters);
    assertThat(artifactTaskResponse).isNotNull();
    assertThat(artifactTaskResponse.getArtifactTaskExecutionResponse()).isEqualTo(artifactTaskExecutionResponse);

    verify(dockerArtifactTaskHandler).decryptRequestDTOs(any());
    verify(dockerArtifactTaskHandler).getLabels(dockerArtifactDelegateRequest);
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testGetArtifactCollectResponseValidateArtifactServers() {
    doNothing().when(dockerArtifactTaskHandler).decryptRequestDTOs(any());
    DockerArtifactDelegateRequest dockerArtifactDelegateRequest =
        DockerArtifactDelegateRequest.builder()
            .dockerConnectorDTO(DockerConnectorDTO.builder().auth(DockerAuthenticationDTO.builder().build()).build())
            .build();
    ArtifactTaskParameters artifactTaskParameters = ArtifactTaskParameters.builder()
                                                        .attributes(dockerArtifactDelegateRequest)
                                                        .artifactTaskType(ArtifactTaskType.VALIDATE_ARTIFACT_SERVER)
                                                        .build();
    ArtifactTaskExecutionResponse artifactTaskExecutionResponse = ArtifactTaskExecutionResponse.builder().build();
    when(dockerArtifactTaskHandler.validateArtifactServer(dockerArtifactDelegateRequest))
        .thenReturn(artifactTaskExecutionResponse);

    ArtifactTaskResponse artifactTaskResponse =
        dockerArtifactTaskHelper.getArtifactCollectResponse(artifactTaskParameters);
    assertThat(artifactTaskResponse).isNotNull();
    assertThat(artifactTaskResponse.getArtifactTaskExecutionResponse()).isEqualTo(artifactTaskExecutionResponse);

    verify(dockerArtifactTaskHandler).decryptRequestDTOs(any());
    verify(dockerArtifactTaskHandler).validateArtifactServer(dockerArtifactDelegateRequest);
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testGetArtifactCollectResponseValidateArtifactSource() {
    doNothing().when(dockerArtifactTaskHandler).decryptRequestDTOs(any());
    DockerArtifactDelegateRequest dockerArtifactDelegateRequest =
        DockerArtifactDelegateRequest.builder()
            .dockerConnectorDTO(DockerConnectorDTO.builder()
                                    .dockerRegistryUrl("")
                                    .auth(DockerAuthenticationDTO.builder().build())
                                    .build())
            .build();
    ArtifactTaskParameters artifactTaskParameters = ArtifactTaskParameters.builder()
                                                        .attributes(dockerArtifactDelegateRequest)
                                                        .artifactTaskType(ArtifactTaskType.VALIDATE_ARTIFACT_SOURCE)
                                                        .build();
    ArtifactTaskExecutionResponse artifactTaskExecutionResponse = ArtifactTaskExecutionResponse.builder().build();
    when(dockerArtifactTaskHandler.validateArtifactImage(dockerArtifactDelegateRequest))
        .thenReturn(artifactTaskExecutionResponse);

    ArtifactTaskResponse artifactTaskResponse =
        dockerArtifactTaskHelper.getArtifactCollectResponse(artifactTaskParameters);
    assertThat(artifactTaskResponse).isNotNull();
    assertThat(artifactTaskResponse.getArtifactTaskExecutionResponse()).isEqualTo(artifactTaskExecutionResponse);

    verify(dockerArtifactTaskHandler).decryptRequestDTOs(any());
    verify(dockerArtifactTaskHandler).validateArtifactImage(dockerArtifactDelegateRequest);
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testGetArtifactCollectResponsegetFeeds() {
    doNothing().when(dockerArtifactTaskHandler).decryptRequestDTOs(any());
    DockerArtifactDelegateRequest dockerArtifactDelegateRequest =
        DockerArtifactDelegateRequest.builder()
            .dockerConnectorDTO(DockerConnectorDTO.builder().auth(DockerAuthenticationDTO.builder().build()).build())
            .build();
    ArtifactTaskParameters artifactTaskParameters = ArtifactTaskParameters.builder()
                                                        .attributes(dockerArtifactDelegateRequest)
                                                        .artifactTaskType(ArtifactTaskType.GET_FEEDS)
                                                        .build();

    ArtifactTaskResponse artifactTaskResponse =
        dockerArtifactTaskHelper.getArtifactCollectResponse(artifactTaskParameters);
    assertThat(artifactTaskResponse).isNotNull();
    assertThat(artifactTaskResponse.getArtifactTaskExecutionResponse()).isNull();
    assertThat(artifactTaskResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.FAILURE);

    verify(dockerArtifactTaskHandler).decryptRequestDTOs(any());
  }
}
