/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.rule.OwnerRule.AADITI;
import static io.harness.rule.OwnerRule.RAMA;

import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.ARTIFACT_STREAM_ID;
import static software.wings.utils.WingsTestConstants.ARTIFACT_STREAM_NAME;
import static software.wings.utils.WingsTestConstants.BUILD_JOB_NAME;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.when;

import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.AwsConfig;
import software.wings.beans.artifact.AmazonS3ArtifactStream;
import software.wings.delegatetasks.DelegateFileManager;
import software.wings.helpers.ext.amazons3.AmazonS3Service;
import software.wings.helpers.ext.jenkins.BuildDetails;
import software.wings.helpers.ext.jenkins.BuildDetails.Builder;
import software.wings.service.intfc.AmazonS3BuildService;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

/**
 * @author rktummala on 09/30/17
 */
public class AmazonS3BuildServiceTest extends WingsBaseTest {
  @Mock private AmazonS3Service amazonS3Service;
  @Inject @InjectMocks private DelegateFileManager delegateFileManager;
  @Inject @InjectMocks private AmazonS3BuildService amazonS3BuildService;
  private static final List<String> artifactPaths = Lists.newArrayList("path1", "path2");
  private static final AwsConfig awsConfig = AwsConfig.builder()
                                                 .accessKey("access".toCharArray())
                                                 .secretKey("secret".toCharArray())
                                                 .accountId("accountId")
                                                 .build();
  private static final AmazonS3ArtifactStream amazonS3ArtifactStream = createAmazonS3ArtifactStream(artifactPaths);
  private static final AmazonS3ArtifactStream amazonS3ArtifactStream2 =
      createAmazonS3ArtifactStream(Lists.newArrayList("testfolder/"));
  private static final AmazonS3ArtifactStream amazonS3ArtifactStream3 =
      createAmazonS3ArtifactStream(Lists.newArrayList("testfolder/*"));

  private static AmazonS3ArtifactStream createAmazonS3ArtifactStream(List<String> artifactPaths) {
    return AmazonS3ArtifactStream.builder()
        .uuid(ARTIFACT_STREAM_ID)
        .appId(APP_ID)
        .settingId("")
        .sourceName(ARTIFACT_STREAM_NAME)
        .jobname(BUILD_JOB_NAME)
        .artifactPaths(artifactPaths)
        .build();
  }

  @Before
  public void setUp() throws Exception {}

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void shouldGetBuilds() {
    List<BuildDetails> buildDetails = Lists.newArrayList(
        Builder.aBuildDetails().withNumber("10").withRevision("10").withArtifactPath("artifact1").build());
    when(amazonS3Service.getArtifactsBuildDetails(any(), any(), any(), any(), anyBoolean())).thenReturn(buildDetails);
    List<BuildDetails> builds = amazonS3BuildService.getBuilds(
        APP_ID, amazonS3ArtifactStream.fetchArtifactStreamAttributes(null), awsConfig, null);
    assertThat(builds).hasSize(1).extracting(BuildDetails::getNumber).containsExactly("10");
    assertThat(builds).extracting(BuildDetails::getArtifactPath).containsExactly("artifact1");
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldGetBuildsForArtifactPathWithTrailingSlash() {
    List<BuildDetails> buildDetails = Lists.newArrayList(Builder.aBuildDetails()
                                                             .withNumber("testfolder/todolist-1.war")
                                                             .withRevision("testfolder/todolist-1.war")
                                                             .withArtifactPath("testfolder/todolist-1.war")
                                                             .build(),
        Builder.aBuildDetails()
            .withNumber("testfolder/todolist-2.war")
            .withRevision("testfolder/todolist-2.war")
            .withArtifactPath("testfolder/todolist-2.war")
            .build());
    when(amazonS3Service.getArtifactsBuildDetails(any(), any(), any(), any(), anyBoolean())).thenReturn(buildDetails);
    List<BuildDetails> builds = amazonS3BuildService.getBuilds(
        APP_ID, amazonS3ArtifactStream2.fetchArtifactStreamAttributes(null), awsConfig, null);
    assertThat(builds)
        .hasSize(2)
        .extracting(BuildDetails::getNumber)
        .containsExactly("testfolder/todolist-1.war", "testfolder/todolist-2.war");
    assertThat(builds)
        .extracting(BuildDetails::getArtifactPath)
        .containsExactly("testfolder/todolist-1.war", "testfolder/todolist-2.war");
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldGetBuildsForArtifactPathWithWildcard() {
    List<BuildDetails> buildDetails = Lists.newArrayList(Builder.aBuildDetails()
                                                             .withNumber("testfolder/todolist.war")
                                                             .withRevision("testfolder/todolist.war")
                                                             .withArtifactPath("testfolder/todolist.war")
                                                             .build(),
        Builder.aBuildDetails()
            .withNumber("testfolder/todolist-2.war")
            .withRevision("testfolder/todolist-2.war")
            .withArtifactPath("testfolder/todolist-2.war")
            .build(),
        Builder.aBuildDetails()
            .withNumber("testfolder/testfolder 2/todolist-3.war")
            .withRevision("testfolder/testfolder 2/todolist-3.war")
            .withArtifactPath("testfolder/testfolder 2/todolist-3.war")
            .build());
    when(amazonS3Service.getArtifactsBuildDetails(any(), any(), any(), any(), anyBoolean())).thenReturn(buildDetails);
    List<BuildDetails> builds = amazonS3BuildService.getBuilds(
        APP_ID, amazonS3ArtifactStream3.fetchArtifactStreamAttributes(null), awsConfig, null);
    assertThat(builds)
        .hasSize(3)
        .extracting(BuildDetails::getNumber)
        .containsExactly(
            "testfolder/todolist.war", "testfolder/todolist-2.war", "testfolder/testfolder 2/todolist-3.war");
    assertThat(builds)
        .extracting(BuildDetails::getArtifactPath)
        .containsExactly(
            "testfolder/todolist.war", "testfolder/todolist-2.war", "testfolder/testfolder 2/todolist-3.war");
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void shouldGetPlans() {
    when(amazonS3Service.getBuckets(awsConfig, null))
        .thenReturn(ImmutableMap.of("bucket1", "bucket1", "bucket2", "bucket2"));
    Map<String, String> plans = amazonS3BuildService.getPlans(awsConfig, null);
    assertThat(plans).hasSize(2).containsKeys("bucket1", "bucket2");
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void shouldGetArtifactPaths() {
    when(amazonS3Service.getArtifactPaths(any(), any(), any())).thenReturn(Lists.newArrayList("path1"));

    List<String> artifactPaths = amazonS3BuildService.getArtifactPaths(BUILD_JOB_NAME, null, awsConfig, null);
    assertThat(artifactPaths.size()).isEqualTo(1);
  }
}
