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
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.delegate.task.artifacts.response.ArtifactTaskExecutionResponse;
import io.harness.delegate.task.shell.CustomArtifactScriptExecutionOnDelegateNG;
import io.harness.rule.Owner;

import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class CustomArtifactTaskHandlerTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @InjectMocks CustomArtifactTaskHandler customArtifactTaskHandler;
  @Mock private CustomArtifactService customArtifactService;
  @Mock CustomArtifactScriptExecutionOnDelegateNG customArtifactScriptExecutionOnDelegateNG;

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testGetBuilds() {
    CustomArtifactDelegateRequest customArtifactDelegateRequest = CustomArtifactDelegateRequest.builder()
                                                                      .artifactsArrayPath("results")
                                                                      .versionPath("version")
                                                                      .script("script")
                                                                      .build();
    ArtifactTaskExecutionResponse artifactTaskExecutionResponse = ArtifactTaskExecutionResponse.builder().build();
    doReturn(artifactTaskExecutionResponse).when(customArtifactService).getBuilds(customArtifactDelegateRequest, null);
    ArtifactTaskExecutionResponse getBuilds = customArtifactTaskHandler.getBuilds(customArtifactDelegateRequest);
    assertThat(getBuilds).isNotNull();
    verify(customArtifactService).getBuilds(eq(customArtifactDelegateRequest), any());
    verify(customArtifactService, times(1)).getBuilds(any(), any());
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testLastSuccessfulBuilds() {
    CustomArtifactDelegateRequest customArtifactDelegateRequest = CustomArtifactDelegateRequest.builder()
                                                                      .artifactsArrayPath("results")
                                                                      .versionPath("version")
                                                                      .script("script")
                                                                      .build();
    ArtifactTaskExecutionResponse artifactTaskExecutionResponse = ArtifactTaskExecutionResponse.builder().build();
    doReturn(artifactTaskExecutionResponse)
        .when(customArtifactService)
        .getLastSuccessfulBuild(customArtifactDelegateRequest, null);
    ArtifactTaskExecutionResponse getBuilds =
        customArtifactTaskHandler.getLastSuccessfulBuild(customArtifactDelegateRequest);
    assertThat(getBuilds).isNotNull();
    verify(customArtifactService).getLastSuccessfulBuild(eq(customArtifactDelegateRequest), any());
    verify(customArtifactService, times(1)).getLastSuccessfulBuild(any(), any());
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testDecryptRequestDTOs() {
    CustomArtifactDelegateRequest customArtifactDelegateRequest = CustomArtifactDelegateRequest.builder()
                                                                      .artifactsArrayPath("results")
                                                                      .versionPath("version")
                                                                      .script("script")
                                                                      .build();
    customArtifactTaskHandler.decryptRequestDTOs(customArtifactDelegateRequest);
  }
}