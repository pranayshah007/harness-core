/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.artifacts.custom;

import static io.harness.rule.OwnerRule.SHIVAM;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
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

public class CustomArtifactTaskHelperTest extends CategoryTest {
  @Mock private CustomArtifactTaskHandler customArtifactTaskHandler;

  @InjectMocks private CustomArtifactTaskHelper customArtifactTaskHelper;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testGetArtifactCollectResponseGetLastSuccessfulBuild() {
    CustomArtifactDelegateRequest customArtifactDelegateRequest = CustomArtifactDelegateRequest.builder()
                                                                      .script("echo tag")
                                                                      .artifactsArrayPath("Path")
                                                                      .versionPath("versionPath")
                                                                      .version("custom")
                                                                      .build();
    ArtifactTaskParameters artifactTaskParameters = ArtifactTaskParameters.builder()
                                                        .attributes(customArtifactDelegateRequest)
                                                        .artifactTaskType(ArtifactTaskType.GET_LAST_SUCCESSFUL_BUILD)
                                                        .build();
    ArtifactTaskExecutionResponse artifactTaskExecutionResponse = ArtifactTaskExecutionResponse.builder().build();
    when(customArtifactTaskHandler.getLastSuccessfulBuild(eq(customArtifactDelegateRequest), any()))
        .thenReturn(artifactTaskExecutionResponse);

    ArtifactTaskResponse artifactTaskResponse =
        customArtifactTaskHelper.getArtifactCollectResponse(artifactTaskParameters);
    assertThat(artifactTaskResponse).isNotNull();
    assertThat(artifactTaskResponse.getArtifactTaskExecutionResponse()).isEqualTo(artifactTaskExecutionResponse);

    verify(customArtifactTaskHandler).getLastSuccessfulBuild(eq(customArtifactDelegateRequest), any());
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testGetArtifactCollectResponseGET_BUILDS() {
    CustomArtifactDelegateRequest customArtifactDelegateRequest = CustomArtifactDelegateRequest.builder()
                                                                      .script("echo tag")
                                                                      .artifactsArrayPath("Path")
                                                                      .versionPath("versionPath")
                                                                      .version("custom")
                                                                      .build();
    ArtifactTaskParameters artifactTaskParameters = ArtifactTaskParameters.builder()
                                                        .attributes(customArtifactDelegateRequest)
                                                        .artifactTaskType(ArtifactTaskType.GET_BUILDS)
                                                        .build();
    ArtifactTaskExecutionResponse artifactTaskExecutionResponse = ArtifactTaskExecutionResponse.builder().build();
    when(customArtifactTaskHandler.getBuilds(eq(customArtifactDelegateRequest), any()))
        .thenReturn(artifactTaskExecutionResponse);

    ArtifactTaskResponse artifactTaskResponse =
        customArtifactTaskHelper.getArtifactCollectResponse(artifactTaskParameters);
    assertThat(artifactTaskResponse).isNotNull();
    assertThat(artifactTaskResponse.getArtifactTaskExecutionResponse()).isEqualTo(artifactTaskExecutionResponse);

    verify(customArtifactTaskHandler).getBuilds(eq(customArtifactDelegateRequest), any());
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testGetArtifactCollectResponseDefault() {
    CustomArtifactDelegateRequest customArtifactDelegateRequest = CustomArtifactDelegateRequest.builder()
                                                                      .script("echo tag")
                                                                      .artifactsArrayPath("Path")
                                                                      .versionPath("versionPath")
                                                                      .version("custom")
                                                                      .build();
    ArtifactTaskParameters artifactTaskParameters = ArtifactTaskParameters.builder()
                                                        .attributes(customArtifactDelegateRequest)
                                                        .artifactTaskType(ArtifactTaskType.GET_JOB_PARAMETERS)
                                                        .build();
    ArtifactTaskExecutionResponse artifactTaskExecutionResponse = ArtifactTaskExecutionResponse.builder().build();
    ArtifactTaskResponse artifactTaskResponse =
        customArtifactTaskHelper.getArtifactCollectResponse(artifactTaskParameters);
    assertThat(artifactTaskResponse).isNotNull();
    assertThat(artifactTaskResponse.getErrorMessage())
        .isEqualTo("There is no Custom artifact task type impl defined for - GET_JOB_PARAMETERS");
  }
}