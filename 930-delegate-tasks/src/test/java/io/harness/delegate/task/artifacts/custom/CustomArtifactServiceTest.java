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
import static org.mockito.Mockito.doReturn;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.SecretDetail;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.NGDelegateLogCallback;
import io.harness.delegate.task.artifacts.response.ArtifactTaskExecutionResponse;
import io.harness.delegate.task.shell.CustomArtifactScriptExecutionOnDelegateNG;
import io.harness.delegate.task.shell.ShellScriptTaskResponseNG;
import io.harness.exception.ArtifactoryRegistryException;
import io.harness.exception.InvalidArtifactServerException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptionConfig;

import software.wings.beans.CustomSecretNGManagerConfig;
import software.wings.helpers.ext.jenkins.BuildDetails;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class CustomArtifactServiceTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @InjectMocks private CustomArtifactService customArtifactService;
  @Mock CustomArtifactScriptExecutionOnDelegateNG customArtifactScriptExecutionOnDelegateNG;
  private static final String ARTIFACT_RESULT_PATH = "HARNESS_ARTIFACT_RESULT_PATH";

  private final LogCallback logCallback =
      new NGDelegateLogCallback(null, "Execute", false, CommandUnitsProgress.builder().build());

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testGetBuildDetails() {
    CustomArtifactDelegateRequest customArtifactDelegateRequest = CustomArtifactDelegateRequest.builder()
                                                                      .artifactsArrayPath("results")
                                                                      .versionPath("version")
                                                                      .script("echo script")
                                                                      .workingDirectory("/tmp")
                                                                      .build();
    ArtifactTaskExecutionResponse artifactTaskExecutionResponse = ArtifactTaskExecutionResponse.builder().build();
    doReturn(ShellScriptTaskResponseNG.builder().status(CommandExecutionStatus.SUCCESS).build())
        .when(customArtifactScriptExecutionOnDelegateNG)
        .executeOnDelegate(any(), any());
    doReturn(Collections.singletonList(BuildDetails.Builder.aBuildDetails().withNumber("version").build()))
        .when(customArtifactScriptExecutionOnDelegateNG)
        .getBuildDetails(any(), any(), any());
    ArtifactTaskExecutionResponse artifactTaskExecutionResponse1 =
        customArtifactService.getBuilds(customArtifactDelegateRequest, logCallback);
    assertThat(artifactTaskExecutionResponse1).isNotNull();
    assertThat(artifactTaskExecutionResponse1.getBuildDetails().get(0).getNumber()).isEqualTo("version");
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testSecretExpressionResolver() {
    Map<String, SecretDetail> secretDetailMap = new HashMap<>();
    Map<String, EncryptionConfig> encryptionConfigs = new HashMap<>();
    secretDetailMap.put("Auth", SecretDetail.builder().build());
    encryptionConfigs.put("Auth", CustomSecretNGManagerConfig.builder().build());
    CustomArtifactDelegateRequest customArtifactDelegateRequest =
        CustomArtifactDelegateRequest.builder()
            .artifactsArrayPath("results")
            .versionPath("version")
            .script("echo script <+secretDelegate.obtain(\"Auth\")>, 123456788")
            .expressionFunctorToken(123456788)
            .workingDirectory("/tmp")
            .secretDetails(secretDetailMap)
            .encryptionConfigs(encryptionConfigs)
            .build();
    ArtifactTaskExecutionResponse artifactTaskExecutionResponse = ArtifactTaskExecutionResponse.builder().build();
    doReturn(ShellScriptTaskResponseNG.builder().status(CommandExecutionStatus.SUCCESS).build())
        .when(customArtifactScriptExecutionOnDelegateNG)
        .executeOnDelegate(any(), any());
    doReturn(Collections.singletonList(BuildDetails.Builder.aBuildDetails().withNumber("version").build()))
        .when(customArtifactScriptExecutionOnDelegateNG)
        .getBuildDetails(any(), any(), any());
    try {
      customArtifactService.getBuilds(customArtifactDelegateRequest, logCallback);
    } catch (ArtifactoryRegistryException e) {
      assertThat(e.getMessage()).contains("Failed to resolve the expression");
    }
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testGetBuildDetailsFailedScript() {
    CustomArtifactDelegateRequest customArtifactDelegateRequest = CustomArtifactDelegateRequest.builder()
                                                                      .artifactsArrayPath("results")
                                                                      .versionPath("version")
                                                                      .script("echo script")
                                                                      .workingDirectory("/tmp")
                                                                      .build();
    ArtifactTaskExecutionResponse artifactTaskExecutionResponse = ArtifactTaskExecutionResponse.builder().build();
    doReturn(ShellScriptTaskResponseNG.builder().status(CommandExecutionStatus.FAILURE).build())
        .when(customArtifactScriptExecutionOnDelegateNG)
        .executeOnDelegate(any(), any());
    try {
      ArtifactTaskExecutionResponse artifactTaskExecutionResponse1 =
          customArtifactService.getBuilds(customArtifactDelegateRequest, logCallback);
    } catch (InvalidArtifactServerException ex) {
      assertThat(ex.getMessage()).isEqualTo("INVALID_ARTIFACT_SERVER");
    }
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testGetLastSuccessfulBuildDetails() {
    CustomArtifactDelegateRequest customArtifactDelegateRequest = CustomArtifactDelegateRequest.builder()
                                                                      .artifactsArrayPath("results")
                                                                      .versionPath("version")
                                                                      .script("echo script")
                                                                      .workingDirectory("/tmp")
                                                                      .version("version")
                                                                      .build();
    ArtifactTaskExecutionResponse artifactTaskExecutionResponse = ArtifactTaskExecutionResponse.builder().build();
    doReturn(ShellScriptTaskResponseNG.builder().status(CommandExecutionStatus.SUCCESS).build())
        .when(customArtifactScriptExecutionOnDelegateNG)
        .executeOnDelegate(any(), any());
    doReturn(Collections.singletonList(BuildDetails.Builder.aBuildDetails().withNumber("version").build()))
        .when(customArtifactScriptExecutionOnDelegateNG)
        .getBuildDetails(any(), any(), any());
    ArtifactTaskExecutionResponse artifactTaskExecutionResponse1 =
        customArtifactService.getLastSuccessfulBuild(customArtifactDelegateRequest, logCallback);
    assertThat(artifactTaskExecutionResponse1).isNotNull();
    assertThat(artifactTaskExecutionResponse1.getBuildDetails().get(0).getNumber()).isEqualTo("version");
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testGetLastSuccessfulBuildDetailsVerify() {
    CustomArtifactDelegateRequest customArtifactDelegateRequest = CustomArtifactDelegateRequest.builder()
                                                                      .artifactsArrayPath("results")
                                                                      .versionPath("version")
                                                                      .script("echo script")
                                                                      .workingDirectory("/tmp")
                                                                      .build();
    ArtifactTaskExecutionResponse artifactTaskExecutionResponse = ArtifactTaskExecutionResponse.builder().build();
    doReturn(ShellScriptTaskResponseNG.builder().status(CommandExecutionStatus.SUCCESS).build())
        .when(customArtifactScriptExecutionOnDelegateNG)
        .executeOnDelegate(any(), any());
    doReturn(Collections.singletonList(BuildDetails.Builder.aBuildDetails().withNumber("version2").build()))
        .when(customArtifactScriptExecutionOnDelegateNG)
        .getBuildDetails(any(), any(), any());
    try {
      ArtifactTaskExecutionResponse artifactTaskExecutionResponse1 =
          customArtifactService.getLastSuccessfulBuild(customArtifactDelegateRequest, logCallback);
    } catch (InvalidArtifactServerException ex) {
      assertThat(ex.getMessage()).isEqualTo("INVALID_ARTIFACT_SERVER");
    }
  }
}