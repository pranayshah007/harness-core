/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.artifactory;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.ACASIAN;
import static io.harness.rule.OwnerRule.TMACARI;

import static software.wings.helpers.ext.jenkins.BuildDetails.Builder.aBuildDetails;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.helpers.ext.jenkins.BuildDetails;

import com.google.common.collect.ImmutableMap;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(CDP)
public class ArtifactoryNgServiceImplTest extends CategoryTest {
  @Mock ArtifactoryClientImpl artifactoryClient;

  @InjectMocks ArtifactoryNgServiceImpl artifactoryNgService;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testGetRepositories() {
    Map<String, String> repositories = Collections.singletonMap("repo", "repo");
    ArtifactoryConfigRequest artifactoryConfigRequest = ArtifactoryConfigRequest.builder().build();
    doReturn(repositories).when(artifactoryClient).getRepositories(any(), any());

    Map<String, String> result = artifactoryNgService.getRepositories(artifactoryConfigRequest, "any");

    verify(artifactoryClient, times(1)).getRepositories(any(), any());
    assertThat(result).isEqualTo(repositories);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testBetBuildDetails() {
    List<BuildDetails> buildDetails = Collections.singletonList(aBuildDetails().build());
    ArtifactoryConfigRequest artifactoryConfigRequest = ArtifactoryConfigRequest.builder().build();
    doReturn(buildDetails).when(artifactoryClient).getBuildDetails(any(), any(), any(), anyInt());

    List<BuildDetails> result =
        artifactoryNgService.getBuildDetails(artifactoryConfigRequest, "repoName", "artifactPath", 10);

    verify(artifactoryClient, times(1)).getBuildDetails(any(), any(), any(), anyInt());
    assertThat(result).isEqualTo(buildDetails);
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void getFileSize() {
    Long artifactFileSize = 100L;
    Map<String, String> metadata = ImmutableMap.of("artifactPath", "path/to/artifactImage");
    ArtifactoryConfigRequest artifactoryConfigRequest = ArtifactoryConfigRequest.builder().build();
    doReturn(artifactFileSize).when(artifactoryClient).getFileSize(any(), any(), any());

    Long result = artifactoryNgService.getFileSize(artifactoryConfigRequest, metadata, "artifactPath");
    assertThat(result).isEqualTo(artifactFileSize);
    verify(artifactoryClient, times(1)).getFileSize(eq(artifactoryConfigRequest), eq(metadata), eq("artifactPath"));
  }
}
