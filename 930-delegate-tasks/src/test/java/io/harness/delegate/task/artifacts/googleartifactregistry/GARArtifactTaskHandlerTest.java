/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.artifacts.googleartifactregistry;

import static io.harness.rule.OwnerRule.ABHISHEK;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.artifact.ArtifactMetadataKeys;
import io.harness.artifacts.beans.BuildDetailsInternal;
import io.harness.artifacts.gar.service.GARApiServiceImpl;
import io.harness.beans.ArtifactMetaInfo;
import io.harness.category.element.UnitTests;
import io.harness.delegate.task.artifacts.gar.GarDelegateRequest;
import io.harness.delegate.task.artifacts.response.ArtifactTaskExecutionResponse;
import io.harness.rule.Owner;

import java.io.IOException;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class GARArtifactTaskHandlerTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @InjectMocks GARArtifactTaskHandler garArtifactTaskHandler;
  @Mock GARApiServiceImpl garApiService;

  private static final String VERSION = "version";
  private static final String SHA_V2 = "shaV2";
  private static final String SHA = "sha";
  private static final String TOKEN = "token";

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void getLastSuccessfulBuildTest_versionArtifactMetaInfo() throws IOException {
    GARArtifactTaskHandler garArtifactTaskHandler1 = spy(garArtifactTaskHandler);
    GarDelegateRequest garDelegateRequest = GarDelegateRequest.builder().version(VERSION).build();
    ArtifactMetaInfo artifactMetaInfo = ArtifactMetaInfo.builder().sha(SHA).shaV2(SHA_V2).build();
    BuildDetailsInternal buildDetailsInternal =
        BuildDetailsInternal.builder().artifactMetaInfo(artifactMetaInfo).build();
    doReturn(TOKEN).when(garArtifactTaskHandler1).getToken(any(), anyBoolean());
    when(garApiService.verifyBuildNumber(any(), anyString())).thenReturn(buildDetailsInternal);
    ArtifactTaskExecutionResponse artifactTaskExecutionResponse =
        garArtifactTaskHandler1.getLastSuccessfulBuild(garDelegateRequest);
    Map<String, String> map =
        artifactTaskExecutionResponse.getArtifactDelegateResponses().get(0).getBuildDetails().getMetadata();
    assertThat(map.get(ArtifactMetadataKeys.SHAV2)).isEqualTo(SHA_V2);
    assertThat(map.get(ArtifactMetadataKeys.SHA)).isEqualTo(SHA);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void getLastSuccessfulBuildTest_regexArtifactMetaInfo() throws IOException {
    GARArtifactTaskHandler garArtifactTaskHandler1 = spy(garArtifactTaskHandler);
    GarDelegateRequest garDelegateRequest = GarDelegateRequest.builder().versionRegex(VERSION).build();
    ArtifactMetaInfo artifactMetaInfo = ArtifactMetaInfo.builder().sha(SHA).shaV2(SHA_V2).build();
    BuildDetailsInternal buildDetailsInternal =
        BuildDetailsInternal.builder().artifactMetaInfo(artifactMetaInfo).build();
    doReturn(TOKEN).when(garArtifactTaskHandler1).getToken(any(), anyBoolean());
    when(garApiService.getLastSuccessfulBuildFromRegex(any(), anyString())).thenReturn(buildDetailsInternal);
    ArtifactTaskExecutionResponse artifactTaskExecutionResponse =
        garArtifactTaskHandler1.getLastSuccessfulBuild(garDelegateRequest);
    Map<String, String> map =
        artifactTaskExecutionResponse.getArtifactDelegateResponses().get(0).getBuildDetails().getMetadata();
    assertThat(map.get(ArtifactMetadataKeys.SHAV2)).isEqualTo(SHA_V2);
    assertThat(map.get(ArtifactMetadataKeys.SHA)).isEqualTo(SHA);
  }
}