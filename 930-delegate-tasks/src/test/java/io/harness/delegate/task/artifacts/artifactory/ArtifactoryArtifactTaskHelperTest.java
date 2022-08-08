/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.artifacts.artifactory;

import static io.harness.rule.OwnerRule.MLUKIC;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryAuthenticationDTO;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryConnectorDTO;
import io.harness.delegate.task.artifacts.ArtifactTaskType;
import io.harness.delegate.task.artifacts.request.ArtifactTaskParameters;
import io.harness.delegate.task.artifacts.response.ArtifactTaskExecutionResponse;
import io.harness.delegate.task.artifacts.response.ArtifactTaskResponse;
import io.harness.rule.Owner;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.CDP)
public class ArtifactoryArtifactTaskHelperTest extends CategoryTest {
  @Mock private ArtifactoryArtifactTaskHandler artifactoryArtifactTaskHandler;

  @InjectMocks private ArtifactoryArtifactTaskHelper artifactoryArtifactTaskHelper;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testGetArtifactCollectResponseGetLastSuccessfulBuild() {
    doNothing()
        .when(artifactoryArtifactTaskHandler)
        .decryptRequestDTOs(ArtifactoryArtifactDelegateRequest.builder().build());
    ArtifactoryArtifactDelegateRequest artifactoryArtifactDelegateRequest =
        ArtifactoryArtifactDelegateRequest.builder()
            .artifactoryConnectorDTO(
                ArtifactoryConnectorDTO.builder().auth(ArtifactoryAuthenticationDTO.builder().build()).build())
            .build();
    ArtifactTaskParameters artifactTaskParameters = ArtifactTaskParameters.builder()
                                                        .attributes(artifactoryArtifactDelegateRequest)
                                                        .artifactTaskType(ArtifactTaskType.GET_LAST_SUCCESSFUL_BUILD)
                                                        .build();
    ArtifactTaskExecutionResponse artifactTaskExecutionResponse = ArtifactTaskExecutionResponse.builder().build();
    when(artifactoryArtifactTaskHandler.getLastSuccessfulBuild(artifactoryArtifactDelegateRequest))
        .thenReturn(artifactTaskExecutionResponse);

    ArtifactTaskResponse artifactTaskResponse =
        artifactoryArtifactTaskHelper.getArtifactCollectResponse(artifactTaskParameters);
    assertThat(artifactTaskResponse).isNotNull();
    assertThat(artifactTaskResponse.getArtifactTaskExecutionResponse()).isEqualTo(artifactTaskExecutionResponse);

    verify(artifactoryArtifactTaskHandler).decryptRequestDTOs(artifactoryArtifactDelegateRequest);
    verify(artifactoryArtifactTaskHandler).getLastSuccessfulBuild(artifactoryArtifactDelegateRequest);
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testGetArtifactCollectResponseGetBuilds() {
    doNothing()
        .when(artifactoryArtifactTaskHandler)
        .decryptRequestDTOs(ArtifactoryArtifactDelegateRequest.builder().build());
    ArtifactoryArtifactDelegateRequest artifactoryArtifactDelegateRequest =
        ArtifactoryArtifactDelegateRequest.builder()
            .artifactoryConnectorDTO(
                ArtifactoryConnectorDTO.builder().auth(ArtifactoryAuthenticationDTO.builder().build()).build())
            .build();
    ArtifactTaskParameters artifactTaskParameters = ArtifactTaskParameters.builder()
                                                        .attributes(artifactoryArtifactDelegateRequest)
                                                        .artifactTaskType(ArtifactTaskType.GET_BUILDS)
                                                        .build();
    ArtifactTaskExecutionResponse artifactTaskExecutionResponse = ArtifactTaskExecutionResponse.builder().build();
    when(artifactoryArtifactTaskHandler.getBuilds(artifactoryArtifactDelegateRequest))
        .thenReturn(artifactTaskExecutionResponse);

    ArtifactTaskResponse artifactTaskResponse =
        artifactoryArtifactTaskHelper.getArtifactCollectResponse(artifactTaskParameters);
    assertThat(artifactTaskResponse).isNotNull();
    assertThat(artifactTaskResponse.getArtifactTaskExecutionResponse()).isEqualTo(artifactTaskExecutionResponse);

    verify(artifactoryArtifactTaskHandler).decryptRequestDTOs(artifactoryArtifactDelegateRequest);
    verify(artifactoryArtifactTaskHandler).getBuilds(artifactoryArtifactDelegateRequest);
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testGetArtifactCollectResponseValidateArtifactServers() {
    doNothing()
        .when(artifactoryArtifactTaskHandler)
        .decryptRequestDTOs(ArtifactoryArtifactDelegateRequest.builder().build());
    ArtifactoryArtifactDelegateRequest artifactoryArtifactDelegateRequest =
        ArtifactoryArtifactDelegateRequest.builder()
            .artifactoryConnectorDTO(
                ArtifactoryConnectorDTO.builder().auth(ArtifactoryAuthenticationDTO.builder().build()).build())
            .build();
    ArtifactTaskParameters artifactTaskParameters = ArtifactTaskParameters.builder()
                                                        .attributes(artifactoryArtifactDelegateRequest)
                                                        .artifactTaskType(ArtifactTaskType.VALIDATE_ARTIFACT_SERVER)
                                                        .build();
    ArtifactTaskExecutionResponse artifactTaskExecutionResponse = ArtifactTaskExecutionResponse.builder().build();
    when(artifactoryArtifactTaskHandler.validateArtifactServer(artifactoryArtifactDelegateRequest))
        .thenReturn(artifactTaskExecutionResponse);

    ArtifactTaskResponse artifactTaskResponse =
        artifactoryArtifactTaskHelper.getArtifactCollectResponse(artifactTaskParameters);
    assertThat(artifactTaskResponse).isNotNull();
    assertThat(artifactTaskResponse.getArtifactTaskExecutionResponse()).isEqualTo(artifactTaskExecutionResponse);

    verify(artifactoryArtifactTaskHandler).decryptRequestDTOs(artifactoryArtifactDelegateRequest);
    verify(artifactoryArtifactTaskHandler).validateArtifactServer(artifactoryArtifactDelegateRequest);
  }
}
