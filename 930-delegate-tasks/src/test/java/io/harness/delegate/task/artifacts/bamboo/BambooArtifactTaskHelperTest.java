/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.artifacts.bamboo;

import static io.harness.rule.OwnerRule.SHIVAM;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.connector.bamboo.BambooAuthType;
import io.harness.delegate.beans.connector.bamboo.BambooAuthenticationDTO;
import io.harness.delegate.beans.connector.bamboo.BambooConnectorDTO;
import io.harness.delegate.task.artifacts.ArtifactTaskType;
import io.harness.delegate.task.artifacts.request.ArtifactTaskParameters;
import io.harness.delegate.task.artifacts.response.ArtifactTaskExecutionResponse;
import io.harness.delegate.task.artifacts.response.ArtifactTaskResponse;
import io.harness.rule.Owner;

import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class BambooArtifactTaskHelperTest extends CategoryTest {
  @Mock private BambooArtifactTaskHandler bambooArtifactTaskHandler;

  @InjectMocks private BambooArtifactTaskHelper bambooArtifactTaskHelper;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void getArtifactCollectResponseGetLastSuccessfulBuildTest() {
    doNothing().when(bambooArtifactTaskHandler).decryptRequestDTOs(any());
    String jobName = "FIS_Cleared_Derivatives_Core/NextGen/Build Custom Branch Images/keepbranch%2Fbo-development";
    BambooConnectorDTO bambooConnectorDTO =
        BambooConnectorDTO.builder()
            .bambooUrl("https://bamboo.com")
            .auth(BambooAuthenticationDTO.builder().authType(BambooAuthType.USER_PASSWORD).build())
            .build();
    BambooArtifactDelegateRequest bambooArtifactDelegateRequest =
        BambooArtifactDelegateRequest.builder()
            .artifactPaths(Collections.singletonList("artifactPath"))
            .planKey("plan")
            .bambooConnectorDTO(bambooConnectorDTO)
            .buildNumber("45")
            .build();
    ArtifactTaskParameters artifactTaskParameters = ArtifactTaskParameters.builder()
                                                        .attributes(bambooArtifactDelegateRequest)
                                                        .artifactTaskType(ArtifactTaskType.GET_LAST_SUCCESSFUL_BUILD)
                                                        .build();
    ArtifactTaskExecutionResponse artifactTaskExecutionResponse = ArtifactTaskExecutionResponse.builder().build();
    when(bambooArtifactTaskHandler.getLastSuccessfulBuild(bambooArtifactDelegateRequest))
        .thenReturn(artifactTaskExecutionResponse);

    ArtifactTaskResponse artifactTaskResponse =
        bambooArtifactTaskHelper.getArtifactCollectResponse(artifactTaskParameters);
    assertThat(artifactTaskResponse).isNotNull();
    assertThat(artifactTaskResponse.getArtifactTaskExecutionResponse()).isEqualTo(artifactTaskExecutionResponse);

    verify(bambooArtifactTaskHandler).decryptRequestDTOs(any());
    verify(bambooArtifactTaskHandler).getLastSuccessfulBuild(bambooArtifactDelegateRequest);
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void getArtifactCollectResponseGetBuildTest() {
    doNothing().when(bambooArtifactTaskHandler).decryptRequestDTOs(any());
    String jobName = "FIS_Cleared_Derivatives_Core/NextGen/Build Custom Branch Images/keepbranch%2Fbo-development";
    BambooConnectorDTO bambooConnectorDTO =
        BambooConnectorDTO.builder()
            .bambooUrl("https://bamboo.com")
            .auth(BambooAuthenticationDTO.builder().authType(BambooAuthType.USER_PASSWORD).build())
            .build();
    BambooArtifactDelegateRequest bambooArtifactDelegateRequest =
        BambooArtifactDelegateRequest.builder()
            .artifactPaths(Collections.singletonList("artifactPath"))
            .planKey("plan")
            .bambooConnectorDTO(bambooConnectorDTO)
            .buildNumber("45")
            .build();
    ArtifactTaskParameters artifactTaskParameters = ArtifactTaskParameters.builder()
                                                        .attributes(bambooArtifactDelegateRequest)
                                                        .artifactTaskType(ArtifactTaskType.GET_BUILDS)
                                                        .build();
    ArtifactTaskExecutionResponse artifactTaskExecutionResponse = ArtifactTaskExecutionResponse.builder().build();
    when(bambooArtifactTaskHandler.getBuilds(bambooArtifactDelegateRequest)).thenReturn(artifactTaskExecutionResponse);

    ArtifactTaskResponse artifactTaskResponse =
        bambooArtifactTaskHelper.getArtifactCollectResponse(artifactTaskParameters);
    assertThat(artifactTaskResponse).isNotNull();
    assertThat(artifactTaskResponse.getArtifactTaskExecutionResponse()).isEqualTo(artifactTaskExecutionResponse);

    verify(bambooArtifactTaskHandler).decryptRequestDTOs(any());
    verify(bambooArtifactTaskHandler).getBuilds(bambooArtifactDelegateRequest);
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void getArtifactCollectResponseGetPlansTest() {
    doNothing().when(bambooArtifactTaskHandler).decryptRequestDTOs(any());
    String jobName = "FIS_Cleared_Derivatives_Core/NextGen/Build Custom Branch Images/keepbranch%2Fbo-development";
    BambooConnectorDTO bambooConnectorDTO =
        BambooConnectorDTO.builder()
            .bambooUrl("https://bamboo.com")
            .auth(BambooAuthenticationDTO.builder().authType(BambooAuthType.USER_PASSWORD).build())
            .build();
    BambooArtifactDelegateRequest bambooArtifactDelegateRequest =
        BambooArtifactDelegateRequest.builder()
            .artifactPaths(Collections.singletonList("artifactPath"))
            .planKey("plan")
            .bambooConnectorDTO(bambooConnectorDTO)
            .buildNumber("45")
            .build();
    ArtifactTaskParameters artifactTaskParameters = ArtifactTaskParameters.builder()
                                                        .attributes(bambooArtifactDelegateRequest)
                                                        .artifactTaskType(ArtifactTaskType.GET_PLANS)
                                                        .build();
    ArtifactTaskExecutionResponse artifactTaskExecutionResponse = ArtifactTaskExecutionResponse.builder().build();
    when(bambooArtifactTaskHandler.getPlans(bambooArtifactDelegateRequest)).thenReturn(artifactTaskExecutionResponse);

    ArtifactTaskResponse artifactTaskResponse =
        bambooArtifactTaskHelper.getArtifactCollectResponse(artifactTaskParameters);
    assertThat(artifactTaskResponse).isNotNull();
    assertThat(artifactTaskResponse.getArtifactTaskExecutionResponse()).isEqualTo(artifactTaskExecutionResponse);

    verify(bambooArtifactTaskHandler).decryptRequestDTOs(any());
    verify(bambooArtifactTaskHandler).getPlans(bambooArtifactDelegateRequest);
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void getArtifactCollectResponseGetPathTest() {
    doNothing().when(bambooArtifactTaskHandler).decryptRequestDTOs(any());
    String jobName = "FIS_Cleared_Derivatives_Core/NextGen/Build Custom Branch Images/keepbranch%2Fbo-development";
    BambooConnectorDTO bambooConnectorDTO =
        BambooConnectorDTO.builder()
            .bambooUrl("https://bamboo.com")
            .auth(BambooAuthenticationDTO.builder().authType(BambooAuthType.USER_PASSWORD).build())
            .build();
    BambooArtifactDelegateRequest bambooArtifactDelegateRequest =
        BambooArtifactDelegateRequest.builder()
            .artifactPaths(Collections.singletonList("artifactPath"))
            .planKey("plan")
            .bambooConnectorDTO(bambooConnectorDTO)
            .buildNumber("45")
            .build();
    ArtifactTaskParameters artifactTaskParameters = ArtifactTaskParameters.builder()
                                                        .attributes(bambooArtifactDelegateRequest)
                                                        .artifactTaskType(ArtifactTaskType.GET_ARTIFACT_PATH)
                                                        .build();
    ArtifactTaskExecutionResponse artifactTaskExecutionResponse = ArtifactTaskExecutionResponse.builder().build();
    when(bambooArtifactTaskHandler.getArtifactPaths(bambooArtifactDelegateRequest))
        .thenReturn(artifactTaskExecutionResponse);

    ArtifactTaskResponse artifactTaskResponse =
        bambooArtifactTaskHelper.getArtifactCollectResponse(artifactTaskParameters);
    assertThat(artifactTaskResponse).isNotNull();
    assertThat(artifactTaskResponse.getArtifactTaskExecutionResponse()).isEqualTo(artifactTaskExecutionResponse);

    verify(bambooArtifactTaskHandler).decryptRequestDTOs(any());
    verify(bambooArtifactTaskHandler).getArtifactPaths(bambooArtifactDelegateRequest);
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void getArtifactCollectResponseValidateServerTest() {
    doNothing().when(bambooArtifactTaskHandler).decryptRequestDTOs(any());
    String jobName = "FIS_Cleared_Derivatives_Core/NextGen/Build Custom Branch Images/keepbranch%2Fbo-development";
    BambooConnectorDTO bambooConnectorDTO =
        BambooConnectorDTO.builder()
            .bambooUrl("https://bamboo.com")
            .auth(BambooAuthenticationDTO.builder().authType(BambooAuthType.USER_PASSWORD).build())
            .build();
    BambooArtifactDelegateRequest bambooArtifactDelegateRequest =
        BambooArtifactDelegateRequest.builder()
            .artifactPaths(Collections.singletonList("artifactPath"))
            .planKey("plan")
            .bambooConnectorDTO(bambooConnectorDTO)
            .buildNumber("45")
            .build();
    ArtifactTaskParameters artifactTaskParameters = ArtifactTaskParameters.builder()
                                                        .attributes(bambooArtifactDelegateRequest)
                                                        .artifactTaskType(ArtifactTaskType.VALIDATE_ARTIFACT_SERVER)
                                                        .build();
    ArtifactTaskExecutionResponse artifactTaskExecutionResponse = ArtifactTaskExecutionResponse.builder().build();
    when(bambooArtifactTaskHandler.validateArtifactServer(bambooArtifactDelegateRequest))
        .thenReturn(artifactTaskExecutionResponse);

    ArtifactTaskResponse artifactTaskResponse =
        bambooArtifactTaskHelper.getArtifactCollectResponse(artifactTaskParameters);
    assertThat(artifactTaskResponse).isNotNull();
    assertThat(artifactTaskResponse.getArtifactTaskExecutionResponse()).isEqualTo(artifactTaskExecutionResponse);

    verify(bambooArtifactTaskHandler).decryptRequestDTOs(any());
    verify(bambooArtifactTaskHandler).validateArtifactServer(bambooArtifactDelegateRequest);
  }
}
