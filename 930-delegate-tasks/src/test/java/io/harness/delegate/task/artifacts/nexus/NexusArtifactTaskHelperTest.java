/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.artifacts.nexus;

import static io.harness.rule.OwnerRule.MLUKIC;
import static io.harness.rule.OwnerRule.SHIVAM;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.artifact.ArtifactMetadataKeys;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.connector.nexusconnector.NexusAuthenticationDTO;
import io.harness.delegate.beans.connector.nexusconnector.NexusConnectorDTO;
import io.harness.delegate.task.artifacts.ArtifactTaskType;
import io.harness.delegate.task.artifacts.request.ArtifactTaskParameters;
import io.harness.delegate.task.artifacts.response.ArtifactBuildDetailsNG;
import io.harness.delegate.task.artifacts.response.ArtifactTaskExecutionResponse;
import io.harness.delegate.task.artifacts.response.ArtifactTaskResponse;
import io.harness.logging.LogCallback;
import io.harness.rule.Owner;

import software.wings.utils.RepositoryFormat;

import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.CDP)
public class NexusArtifactTaskHelperTest extends CategoryTest {
  @Mock private NexusArtifactTaskHandler nexusArtifactTaskHandler;

  @InjectMocks private NexusArtifactTaskHelper nexusArtifactTaskHelper;
  @Mock LogCallback logCallback;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testGetArtifactCollectResponseGetLastSuccessfulBuild() {
    doNothing().when(nexusArtifactTaskHandler).decryptRequestDTOs(any());
    NexusArtifactDelegateRequest nexusArtifactDelegateRequest =
        NexusArtifactDelegateRequest.builder()
            .nexusConnectorDTO(NexusConnectorDTO.builder().auth(NexusAuthenticationDTO.builder().build()).build())
            .build();
    ArtifactTaskParameters artifactTaskParameters = ArtifactTaskParameters.builder()
                                                        .attributes(nexusArtifactDelegateRequest)
                                                        .artifactTaskType(ArtifactTaskType.GET_LAST_SUCCESSFUL_BUILD)
                                                        .build();
    ArtifactTaskExecutionResponse artifactTaskExecutionResponse = ArtifactTaskExecutionResponse.builder().build();
    when(nexusArtifactTaskHandler.getLastSuccessfulBuild(nexusArtifactDelegateRequest))
        .thenReturn(artifactTaskExecutionResponse);

    ArtifactTaskResponse artifactTaskResponse =
        nexusArtifactTaskHelper.getArtifactCollectResponse(artifactTaskParameters);
    assertThat(artifactTaskResponse).isNotNull();
    assertThat(artifactTaskResponse.getArtifactTaskExecutionResponse()).isEqualTo(artifactTaskExecutionResponse);

    verify(nexusArtifactTaskHandler).decryptRequestDTOs(any());
    verify(nexusArtifactTaskHandler).getLastSuccessfulBuild(nexusArtifactDelegateRequest);
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testGetArtifactCollectResponseGetLastSuccessfulBuildForDockerPullCommand() {
    ArtifactBuildDetailsNG buildDetailsNG =
        ArtifactBuildDetailsNG.builder()
            .buildUrl("https://docker.com")
            .metadata(Collections.singletonMap(ArtifactMetadataKeys.IMAGE, "version"))
            .build();
    NexusArtifactDelegateResponse nexusArtifactDelegateResponse = NexusArtifactDelegateResponse.builder()
                                                                      .repositoryFormat(RepositoryFormat.docker.name())
                                                                      .buildDetails(buildDetailsNG)
                                                                      .build();
    doNothing().when(nexusArtifactTaskHandler).decryptRequestDTOs(any());
    NexusArtifactDelegateRequest nexusArtifactDelegateRequest =
        NexusArtifactDelegateRequest.builder()
            .nexusConnectorDTO(NexusConnectorDTO.builder().auth(NexusAuthenticationDTO.builder().build()).build())
            .build();
    ArtifactTaskParameters artifactTaskParameters = ArtifactTaskParameters.builder()
                                                        .attributes(nexusArtifactDelegateRequest)
                                                        .artifactTaskType(ArtifactTaskType.GET_LAST_SUCCESSFUL_BUILD)
                                                        .build();
    ArtifactTaskExecutionResponse artifactTaskExecutionResponse =
        ArtifactTaskExecutionResponse.builder().artifactDelegateResponse(nexusArtifactDelegateResponse).build();
    when(nexusArtifactTaskHandler.getLastSuccessfulBuild(nexusArtifactDelegateRequest))
        .thenReturn(artifactTaskExecutionResponse);

    ArtifactTaskResponse artifactTaskResponse =
        nexusArtifactTaskHelper.getArtifactCollectResponse(artifactTaskParameters, logCallback);
    assertThat(artifactTaskResponse).isNotNull();
    assertThat(artifactTaskResponse.getArtifactTaskExecutionResponse()).isEqualTo(artifactTaskExecutionResponse);

    verify(nexusArtifactTaskHandler).decryptRequestDTOs(any());
    verify(nexusArtifactTaskHandler).getLastSuccessfulBuild(nexusArtifactDelegateRequest);
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testGetArtifactCollectResponseGetBuilds() {
    doNothing().when(nexusArtifactTaskHandler).decryptRequestDTOs(any());
    NexusArtifactDelegateRequest nexusArtifactDelegateRequest =
        NexusArtifactDelegateRequest.builder()
            .nexusConnectorDTO(NexusConnectorDTO.builder().auth(NexusAuthenticationDTO.builder().build()).build())
            .build();
    ArtifactTaskParameters artifactTaskParameters = ArtifactTaskParameters.builder()
                                                        .attributes(nexusArtifactDelegateRequest)
                                                        .artifactTaskType(ArtifactTaskType.GET_BUILDS)
                                                        .build();
    ArtifactTaskExecutionResponse artifactTaskExecutionResponse = ArtifactTaskExecutionResponse.builder().build();
    when(nexusArtifactTaskHandler.getBuilds(nexusArtifactDelegateRequest)).thenReturn(artifactTaskExecutionResponse);

    ArtifactTaskResponse artifactTaskResponse =
        nexusArtifactTaskHelper.getArtifactCollectResponse(artifactTaskParameters);
    assertThat(artifactTaskResponse).isNotNull();
    assertThat(artifactTaskResponse.getArtifactTaskExecutionResponse()).isEqualTo(artifactTaskExecutionResponse);

    verify(nexusArtifactTaskHandler).decryptRequestDTOs(any());
    verify(nexusArtifactTaskHandler).getBuilds(nexusArtifactDelegateRequest);
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testGetArtifactCollectResponseValidateArtifactServers() {
    doNothing().when(nexusArtifactTaskHandler).decryptRequestDTOs(any());
    NexusArtifactDelegateRequest nexusArtifactDelegateRequest =
        NexusArtifactDelegateRequest.builder()
            .nexusConnectorDTO(NexusConnectorDTO.builder().auth(NexusAuthenticationDTO.builder().build()).build())
            .build();
    ArtifactTaskParameters artifactTaskParameters = ArtifactTaskParameters.builder()
                                                        .attributes(nexusArtifactDelegateRequest)
                                                        .artifactTaskType(ArtifactTaskType.VALIDATE_ARTIFACT_SERVER)
                                                        .build();
    ArtifactTaskExecutionResponse artifactTaskExecutionResponse = ArtifactTaskExecutionResponse.builder().build();
    when(nexusArtifactTaskHandler.validateArtifactServer(nexusArtifactDelegateRequest))
        .thenReturn(artifactTaskExecutionResponse);

    ArtifactTaskResponse artifactTaskResponse =
        nexusArtifactTaskHelper.getArtifactCollectResponse(artifactTaskParameters);
    assertThat(artifactTaskResponse).isNotNull();
    assertThat(artifactTaskResponse.getArtifactTaskExecutionResponse()).isEqualTo(artifactTaskExecutionResponse);

    verify(nexusArtifactTaskHandler).decryptRequestDTOs(any());
    verify(nexusArtifactTaskHandler).validateArtifactServer(nexusArtifactDelegateRequest);
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testGetArtifactCollectResponseDefault() {
    doNothing().when(nexusArtifactTaskHandler).decryptRequestDTOs(any());
    NexusArtifactDelegateRequest nexusArtifactDelegateRequest =
        NexusArtifactDelegateRequest.builder()
            .nexusConnectorDTO(NexusConnectorDTO.builder().auth(NexusAuthenticationDTO.builder().build()).build())
            .build();
    ArtifactTaskParameters artifactTaskParameters = ArtifactTaskParameters.builder()
                                                        .attributes(nexusArtifactDelegateRequest)
                                                        .artifactTaskType(ArtifactTaskType.JENKINS_POLL_TASK)
                                                        .build();
    ArtifactTaskExecutionResponse artifactTaskExecutionResponse = ArtifactTaskExecutionResponse.builder().build();

    ArtifactTaskResponse artifactTaskResponse =
        nexusArtifactTaskHelper.getArtifactCollectResponse(artifactTaskParameters);
    assertThat(artifactTaskResponse).isNotNull();
    assertThat(artifactTaskResponse.getErrorMessage())
        .isEqualTo("There is no Nexus artifact task type impl defined for - " + ArtifactTaskType.JENKINS_POLL_TASK);
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testGetArtifactCollectResponseGetRepository() {
    doNothing().when(nexusArtifactTaskHandler).decryptRequestDTOs(any());
    NexusArtifactDelegateRequest nexusArtifactDelegateRequest =
        NexusArtifactDelegateRequest.builder()
            .nexusConnectorDTO(NexusConnectorDTO.builder().auth(NexusAuthenticationDTO.builder().build()).build())
            .build();
    ArtifactTaskParameters artifactTaskParameters = ArtifactTaskParameters.builder()
                                                        .attributes(nexusArtifactDelegateRequest)
                                                        .artifactTaskType(ArtifactTaskType.GET_NEXUS_REPOSITORIES)
                                                        .build();
    ArtifactTaskExecutionResponse artifactTaskExecutionResponse = ArtifactTaskExecutionResponse.builder().build();
    when(nexusArtifactTaskHandler.getRepositories(nexusArtifactDelegateRequest))
        .thenReturn(artifactTaskExecutionResponse);

    ArtifactTaskResponse artifactTaskResponse =
        nexusArtifactTaskHelper.getArtifactCollectResponse(artifactTaskParameters);
    assertThat(artifactTaskResponse).isNotNull();
    assertThat(artifactTaskResponse.getArtifactTaskExecutionResponse()).isEqualTo(artifactTaskExecutionResponse);

    verify(nexusArtifactTaskHandler).decryptRequestDTOs(any());
    verify(nexusArtifactTaskHandler).getRepositories(nexusArtifactDelegateRequest);
  }
}
