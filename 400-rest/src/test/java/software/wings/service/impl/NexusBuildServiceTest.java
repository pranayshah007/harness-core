/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.annotations.dev.HarnessModule._930_DELEGATE_TASKS;
import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.rule.OwnerRule.DEEPAK_PUTHRAYA;
import static io.harness.rule.OwnerRule.SRINIVAS;
import static io.harness.rule.OwnerRule.VINICIUS;

import static software.wings.helpers.ext.jenkins.BuildDetails.Builder.aBuildDetails;
import static software.wings.utils.RepositoryFormat.docker;
import static software.wings.utils.RepositoryFormat.maven;
import static software.wings.utils.RepositoryFormat.npm;
import static software.wings.utils.RepositoryFormat.nuget;
import static software.wings.utils.RepositoryFormat.raw;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.ARTIFACT_GROUP_ID;
import static software.wings.utils.WingsTestConstants.ARTIFACT_NAME;
import static software.wings.utils.WingsTestConstants.ARTIFACT_STREAM_ID;
import static software.wings.utils.WingsTestConstants.ARTIFACT_STREAM_NAME;
import static software.wings.utils.WingsTestConstants.BUILD_JOB_NAME;
import static software.wings.utils.WingsTestConstants.SETTING_ID;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.eraro.ErrorCode;
import io.harness.exception.WingsException;
import io.harness.nexus.NexusClientImpl;
import io.harness.nexus.NexusRequest;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.artifact.ArtifactStreamAttributes;
import software.wings.beans.artifact.ArtifactStreamType;
import software.wings.beans.artifact.NexusArtifactStream;
import software.wings.beans.config.NexusConfig;
import software.wings.helpers.ext.jenkins.BuildDetails;
import software.wings.helpers.ext.jenkins.JobDetails;
import software.wings.helpers.ext.nexus.NexusService;
import software.wings.service.intfc.NexusBuildService;
import software.wings.service.intfc.security.EncryptionService;
import software.wings.service.mappers.artifact.NexusConfigToNexusRequestMapper;
import software.wings.utils.ArtifactType;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.AdditionalMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;

/**
 * Created by srinivas on 4/1/17.
 */
@OwnedBy(CDC)
@TargetModule(_930_DELEGATE_TASKS)
public class NexusBuildServiceTest extends WingsBaseTest {
  @Mock private NexusService nexusService;
  @Mock private NexusClientImpl nexusClient;
  @Mock private EncryptionService encryptionService;
  @Inject @InjectMocks private NexusBuildService nexusBuildService;

  private static final String DEFAULT_NEXUS_URL = "http://localhost:8881/nexus/";

  private NexusConfig nexusConfig;
  private NexusRequest nexusRequest;

  private static final NexusArtifactStream nexusArtifactStream =
      NexusArtifactStream.builder()
          .uuid(ARTIFACT_STREAM_ID)
          .appId(APP_ID)
          .settingId(SETTING_ID)
          .sourceName(ARTIFACT_STREAM_NAME)
          .jobname(BUILD_JOB_NAME)
          .groupId(ARTIFACT_GROUP_ID)
          .artifactPaths(Stream.of(ARTIFACT_NAME).collect(toList()))
          .build();

  @Before
  public void setup() {
    nexusConfig =
        NexusConfig.builder().nexusUrl(DEFAULT_NEXUS_URL).username("admin").password("admin123".toCharArray()).build();
    nexusRequest = NexusConfigToNexusRequestMapper.toNexusRequest(nexusConfig, encryptionService, null);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldGetPlans() {
    when(nexusClient.getRepositories(nexusRequest))
        .thenReturn(ImmutableMap.of("snapshots", "Snapshots", "releases", "Releases"));
    Map<String, String> jobs = nexusBuildService.getPlans(nexusConfig, null);
    assertThat(jobs).hasSize(2).containsEntry("releases", "Releases");
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldGetJobs() {
    when(nexusClient.getRepositories(nexusRequest))
        .thenReturn(ImmutableMap.of("snapshots", "Snapshots", "releases", "Releases"));
    List<JobDetails> jobs = nexusBuildService.getJobs(nexusConfig, null, Optional.empty());
    List<String> jobNames = nexusBuildService.extractJobNameFromJobDetails(jobs);
    assertThat(jobNames).hasSize(2).containsExactlyInAnyOrder("releases", "snapshots");
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldGetArtifactPaths() {
    when(nexusService.getArtifactPaths(nexusRequest, "releases")).thenReturn(Lists.newArrayList("/fakepath"));
    when(nexusService.getArtifactPaths(nexusRequest, "releases", "groupId"))
        .thenReturn(Lists.newArrayList("/fakepath2"));
    when(nexusService.getArtifactNames(nexusRequest, "releases", "groupId"))
        .thenReturn(Lists.newArrayList("/fakeName1"));
    when(nexusService.getArtifactNames(nexusRequest, "releases", "groupId", maven.name()))
        .thenReturn(Lists.newArrayList("/fakeName2"));

    List<String> jobs = nexusBuildService.getArtifactPaths("releases", null, nexusConfig, null);
    assertThat(jobs).hasSize(1).containsExactlyInAnyOrder("/fakepath");

    jobs = nexusBuildService.getArtifactPaths("releases", "groupId", nexusConfig, null);
    assertThat(jobs).hasSize(1).containsExactlyInAnyOrder("/fakeName1");

    jobs = nexusBuildService.getArtifactPaths("releases", null, nexusConfig, null, null);
    assertThat(jobs).hasSize(1).containsExactlyInAnyOrder("/fakepath");

    jobs = nexusBuildService.getArtifactPaths("releases", "groupId", nexusConfig, null, maven.name());
    assertThat(jobs).hasSize(1).containsExactlyInAnyOrder("/fakeName2");
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldGetBuilds() {
    when(nexusService.getVersions(nexusRequest, BUILD_JOB_NAME, ARTIFACT_GROUP_ID, ARTIFACT_NAME, null, null))
        .thenReturn(
            Lists.newArrayList(aBuildDetails().withNumber("3.0").build(), aBuildDetails().withNumber("2.1.2").build()));
    List<BuildDetails> buildDetails = nexusBuildService.getBuilds(
        "nexus", nexusArtifactStream.fetchArtifactStreamAttributes(null), nexusConfig, null);
    assertThat(buildDetails).hasSize(2).extracting(BuildDetails::getNumber).containsExactly("3.0", "2.1.2");
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldValidateInvalidUrl() {
    NexusConfig nexusConfig = NexusConfig.builder()
                                  .nexusUrl("BAD_URL")
                                  .username("username")
                                  .password("password".toCharArray())
                                  .accountId(ACCOUNT_ID)
                                  .build();
    try {
      nexusBuildService.validateArtifactServer(nexusConfig, Collections.emptyList());
    } catch (WingsException e) {
      assertThat(e.getMessage()).isEqualTo(ErrorCode.INVALID_ARTIFACT_SERVER.toString());
      assertThat(e.getParams()).isNotEmpty();
      assertThat(e.getParams().get("message")).isEqualTo("Could not reach Nexus Server at : BAD_URL");
    }
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void shouldValidateArtifactServer() {
    // Makes an actual network request to the given nexus url.
    NexusConfig config = NexusConfig.builder()
                             .nexusUrl("https://harness.io")
                             .username("username")
                             .password("password".toCharArray())
                             .accountId(ACCOUNT_ID)
                             .build();
    NexusRequest request = NexusConfigToNexusRequestMapper.toNexusRequest(config, encryptionService, null);
    nexusBuildService.validateArtifactServer(config, Collections.emptyList());
    verify(nexusClient).isRunning(eq(request));
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void shouldFailPlansForNonDockerImages() {
    assertThatThrownBy(() -> {
      nexusBuildService.getPlans(nexusConfig, Collections.emptyList(), ArtifactType.JAR, docker.name());
    }).isInstanceOf(WingsException.class);
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void ShouldCollectPlans() {
    // For Docker ArtifactType
    nexusBuildService.getPlans(nexusConfig, Collections.emptyList(), ArtifactType.DOCKER, docker.name());
    verify(nexusClient).getRepositories(eq(nexusRequest), eq(docker.name()));

    nexusBuildService.getPlans(nexusConfig, Collections.emptyList(), ArtifactType.JAR, maven.name());
    verify(nexusClient).getRepositories(eq(nexusRequest), eq(maven.name()));
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void ShouldCollectBuild() {
    ArtifactStreamAttributes nugetAttributes = ArtifactStreamAttributes.builder()
                                                   .repositoryFormat(nuget.name())
                                                   .jobName("someJobName")
                                                   .nexusPackageName("someNexusPackage")
                                                   .artifactStreamType(ArtifactStreamType.NEXUS.name())
                                                   .build();
    String buildNo = "someBuildNumber";
    List<BuildDetails> allBuilds =
        IntStream.rangeClosed(1, 100)
            .boxed()
            .sorted(Comparator.reverseOrder())
            .map(i -> BuildDetails.Builder.aBuildDetails().withNumber(String.valueOf(i)).build())
            .collect(toList());
    when(nexusService.getVersion(AdditionalMatchers.or(eq(nuget.name()), eq(npm.name())), eq(nexusRequest),
             eq("someJobName"), eq("someNexusPackage"), eq("someBuildNumber")))
        .thenReturn(allBuilds);

    BuildDetails actual =
        nexusBuildService.getBuild(APP_ID, nugetAttributes, nexusConfig, Collections.emptyList(), buildNo);
    verify(nexusService)
        .getVersion(
            eq(nuget.name()), eq(nexusRequest), eq("someJobName"), eq("someNexusPackage"), eq("someBuildNumber"));
    assertThat(actual).isEqualTo(allBuilds.get(0));

    ArtifactStreamAttributes npmAttributes = ArtifactStreamAttributes.builder()
                                                 .repositoryFormat(npm.name())
                                                 .jobName("someJobName")
                                                 .nexusPackageName("someNexusPackage")
                                                 .artifactStreamType(ArtifactStreamType.NEXUS.name())
                                                 .build();
    actual = nexusBuildService.getBuild(APP_ID, npmAttributes, nexusConfig, Collections.emptyList(), buildNo);
    verify(nexusService)
        .getVersion(eq(npm.name()), eq(nexusRequest), eq("someJobName"), eq("someNexusPackage"), eq("someBuildNumber"));
    assertThat(actual).isEqualTo(allBuilds.get(0));

    ArtifactStreamAttributes attributes = ArtifactStreamAttributes.builder()
                                              .repositoryFormat("ANYTHING_ELSE")
                                              .jobName("someJobName")
                                              .nexusPackageName("someNexusPackage")
                                              .groupId("someGroupID")
                                              .artifactName("someArtifactName")
                                              .extension("someExtension")
                                              .classifier("someClassifier")
                                              .artifactStreamType(ArtifactStreamType.NEXUS.name())
                                              .build();
    nexusBuildService.getBuild(APP_ID, attributes, nexusConfig, Collections.emptyList(), buildNo);
    verify(nexusService)
        .getVersion(eq(nexusRequest), eq("someJobName"), eq("someGroupID"), eq("someArtifactName"), eq("someExtension"),
            eq("someClassifier"), eq(buildNo));
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void shouldCollectDockerBuilds() {
    ArtifactStreamAttributes attributes = ArtifactStreamAttributes.builder()
                                              .artifactType(ArtifactType.DOCKER)
                                              .repositoryFormat(docker.name())
                                              .artifactStreamType(ArtifactStreamType.NEXUS.name())
                                              .build();
    nexusBuildService.getBuilds(APP_ID, attributes, nexusConfig, Collections.emptyList());
    verify(nexusService).getBuilds(eq(nexusRequest), eq(attributes), anyInt());
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void shouldCollectNugetAndNpmBuilds() {
    ArtifactStreamAttributes attributes = ArtifactStreamAttributes.builder()
                                              .jobName("someJobName")
                                              .nexusPackageName("someNexusPackageName")
                                              .repositoryFormat(npm.name())
                                              .artifactStreamType(ArtifactStreamType.NEXUS.name())
                                              .build();
    nexusBuildService.getBuilds(APP_ID, attributes, nexusConfig, Collections.emptyList());
    verify(nexusService)
        .getVersions(eq(npm.name()), eq(nexusRequest), eq("someJobName"), eq("someNexusPackageName"), any());

    attributes = ArtifactStreamAttributes.builder()
                     .jobName("someJobName")
                     .nexusPackageName("someNexusPackageName")
                     .repositoryFormat(nuget.name())
                     .artifactStreamType(ArtifactStreamType.NEXUS.name())
                     .build();
    nexusBuildService.getBuilds(APP_ID, attributes, nexusConfig, Collections.emptyList());
    verify(nexusService)
        .getVersions(eq(npm.name()), eq(nexusRequest), eq("someJobName"), eq("someNexusPackageName"), any());
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void shouldCollectRawBuilds() {
    ArtifactStreamAttributes attributes = ArtifactStreamAttributes.builder()
                                              .jobName("someJobName")
                                              .nexusPackageName("someNexusPackageName")
                                              .repositoryFormat(raw.name())
                                              .artifactStreamType(ArtifactStreamType.NEXUS.name())
                                              .build();
    nexusBuildService.getBuilds(APP_ID, attributes, nexusConfig, Collections.emptyList());
    verify(nexusService)
        .getPackageNames(eq(nexusRequest), eq("someJobName"), eq("someNexusPackageName"), eq(raw.name()));
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void shouldValidateArtifactSource() {
    ArtifactStreamAttributes attributes = ArtifactStreamAttributes.builder().build();
    boolean actual = nexusBuildService.validateArtifactSource(nexusConfig, null, attributes);
    Assertions.assertThat(actual).isTrue();
    verify(nexusService, never()).existsVersion(any(), any(), any(), any(), any(), any());

    attributes = ArtifactStreamAttributes.builder().extension("someExtension").build();
    actual = nexusBuildService.validateArtifactSource(nexusConfig, null, attributes);
    verify(nexusService, times(1)).existsVersion(any(), any(), any(), any(), eq("someExtension"), any());
    Assertions.assertThat(actual).isFalse();

    attributes = ArtifactStreamAttributes.builder().classifier("someClassifier").build();
    actual = nexusBuildService.validateArtifactSource(nexusConfig, null, attributes);
    verify(nexusService, times(1)).existsVersion(any(), any(), any(), any(), any(), eq("someClassifier"));
    Assertions.assertThat(actual).isFalse();
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void shouldReturnLastSuccessfulBuild() {
    ArtifactStreamAttributes attributes = ArtifactStreamAttributes.builder()
                                              .artifactStreamType(ArtifactStreamType.NEXUS.name())
                                              .jobName("someJob")
                                              .groupId("someGroup")
                                              .artifactName("artifactName")
                                              .build();
    nexusBuildService.getLastSuccessfulBuild(APP_ID, attributes, nexusConfig, null);
    verify(nexusService).getLatestVersion(eq(nexusRequest), eq("someJob"), eq("someGroup"), eq("artifactName"));
  }
}
