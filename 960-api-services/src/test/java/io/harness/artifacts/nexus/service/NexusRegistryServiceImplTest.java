/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.artifacts.nexus.service;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.rule.OwnerRule.MLUKIC;
import static io.harness.rule.OwnerRule.SHIVAM;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.artifact.ArtifactMetadataKeys;
import io.harness.artifacts.beans.BuildDetailsInternal;
import io.harness.category.element.UnitTests;
import io.harness.exception.HintException;
import io.harness.nexus.NexusClientImpl;
import io.harness.nexus.NexusRequest;
import io.harness.nexus.service.NexusRegistryServiceImpl;
import io.harness.rule.Owner;

import software.wings.utils.RepositoryFormat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.CDP)
public class NexusRegistryServiceImplTest extends CategoryTest {
  @Mock NexusClientImpl nexusClient;
  @InjectMocks NexusRegistryServiceImpl nexusRegistryService;

  private static final String NEXUS_URL_HOSTNAME = "nexus.harness.io";
  private static final String NEXUS_URL = "https://" + NEXUS_URL_HOSTNAME;
  private static final String NEXUS_USERNAME = "username";
  private static final String NEXUS_PASSWORD = "password";
  private static final int MAX_NO_OF_TAGS_PER_IMAGE = 10000;

  private static Map<String, List<BuildDetailsInternal>> buildDetailsData;

  @Before
  public void before() {
    MockitoAnnotations.initMocks(this);

    buildDetailsData = new HashMap<>();

    List<BuildDetailsInternal> bdiList = new ArrayList<>();
    String repo = "test1";
    String repoUrl = "nexus.harness.io:8001";
    String imageName = "superApp";
    bdiList.add(createBuildDetails(repoUrl, null, repo, imageName, "3.0"));
    bdiList.add(createBuildDetails(repoUrl, null, repo, imageName, "2.0"));
    bdiList.add(createBuildDetails(repoUrl, null, repo, imageName, "1.0"));
    buildDetailsData.put("bdi1", bdiList);

    bdiList = new ArrayList<>();
    repo = "test2";
    repoUrl = "nexus.harness.io:8002";
    imageName = "super/duper/app";
    bdiList.add(createBuildDetails(repoUrl, null, repo, imageName, "2.5.3"));
    bdiList.add(createBuildDetails(repoUrl, null, repo, imageName, "2.5"));
    bdiList.add(createBuildDetails(repoUrl, null, repo, imageName, "2.4.2"));
    bdiList.add(createBuildDetails(repoUrl, null, repo, imageName, "2.4.1"));
    buildDetailsData.put("bdi2", bdiList);

    bdiList = new ArrayList<>();
    repo = "test2";
    repoUrl = "nexus.harness.io:8002";
    imageName = "extra/megaapp";
    bdiList.add(createBuildDetails(repoUrl, null, repo, imageName, "a4"));
    bdiList.add(createBuildDetails(repoUrl, null, repo, imageName, "b23"));
    bdiList.add(createBuildDetails(repoUrl, null, repo, imageName, "latest"));
    bdiList.add(createBuildDetails(repoUrl, null, repo, imageName, "basic"));
    buildDetailsData.put("bdi3", bdiList);
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testGetBuilds() throws IOException {
    NexusRequest nexusConfig = NexusRequest.builder()
                                   .nexusUrl(NEXUS_URL)
                                   .username(NEXUS_USERNAME)
                                   .password(NEXUS_PASSWORD.toCharArray())
                                   .artifactRepositoryUrl("nexus.harness.io:8001")
                                   .build();

    doReturn(buildDetailsData.get("bdi1"))
        .when(nexusClient)
        .getDockerArtifactVersions(
            nexusConfig, "test1", null, "superApp", RepositoryFormat.docker.name(), Integer.MAX_VALUE);

    List<BuildDetailsInternal> response = nexusRegistryService.getBuilds(nexusConfig, "test1", null, "superApp",
        RepositoryFormat.docker.name(), null, null, null, null, null, null, Integer.MAX_VALUE);
    assertThat(response).isNotNull();
    assertThat(response.size()).isEqualTo(3);
    for (BuildDetailsInternal bdi : response) {
      assertThat(bdi.getMetadata().get(ArtifactMetadataKeys.IMAGE)).startsWith("nexus.harness.io:8001/superApp:");
      assertThat(bdi.getMetadata().get(ArtifactMetadataKeys.IMAGE)).isNotEqualTo("nexus.harness.io:8001/superApp:");
    }
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testGetBuildForInvalidFormat() throws IOException {
    NexusRequest nexusConfig = NexusRequest.builder()
                                   .nexusUrl(NEXUS_URL)
                                   .username(NEXUS_USERNAME)
                                   .password(NEXUS_PASSWORD.toCharArray())
                                   .artifactRepositoryUrl("nexus.harness.io:8001")
                                   .build();
    doReturn(buildDetailsData.get("bdi1"))
        .when(nexusClient)
        .getDockerArtifactVersions(nexusConfig, "test1", null, "superApp", "test", Integer.MAX_VALUE);
    try {
      nexusRegistryService.getBuilds(
          nexusConfig, "test1", null, "superApp", "test", null, null, null, null, null, null, Integer.MAX_VALUE);
    } catch (HintException ex) {
      assertThat(ex.getMessage()).isEqualTo("Please check your artifact YAML configuration.");
    }
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testGetBuildsForMaven() throws IOException {
    NexusRequest nexusConfig = NexusRequest.builder()
                                   .nexusUrl(NEXUS_URL)
                                   .username(NEXUS_USERNAME)
                                   .password(NEXUS_PASSWORD.toCharArray())
                                   .artifactRepositoryUrl("nexus.harness.io:8001")
                                   .build();
    doReturn(buildDetailsData.get("bdi1"))
        .when(nexusClient)
        .getArtifactsVersions(nexusConfig, "test1", "groupId", "superApp", "war", "", Integer.MAX_VALUE);
    List<BuildDetailsInternal> response = nexusRegistryService.getBuilds(nexusConfig, "test1", null, "superApp",
        RepositoryFormat.maven.name(), "groupId", "superApp", "war", "", null, null, Integer.MAX_VALUE);
    assertThat(response).isNotNull();
    assertThat(response.size()).isEqualTo(3);
    for (BuildDetailsInternal bdi : response) {
      assertThat(bdi.getMetadata().get(ArtifactMetadataKeys.IMAGE)).startsWith("nexus.harness.io:8001/superApp:");
      assertThat(bdi.getMetadata().get(ArtifactMetadataKeys.IMAGE)).isNotEqualTo("nexus.harness.io:8001/superApp:");
    }
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testGetBuildsForNPM() throws IOException {
    NexusRequest nexusConfig = NexusRequest.builder()
                                   .nexusUrl(NEXUS_URL)
                                   .username(NEXUS_USERNAME)
                                   .password(NEXUS_PASSWORD.toCharArray())
                                   .artifactRepositoryUrl("nexus.harness.io:8001")
                                   .build();

    doReturn(buildDetailsData.get("bdi1"))
        .when(nexusClient)
        .getArtifactsVersions(nexusConfig, "npm", "test1", "packageName");
    List<BuildDetailsInternal> response = nexusRegistryService.getBuilds(nexusConfig, "test1", null, "superApp",
        RepositoryFormat.npm.name(), "groupId", "superApp", "war", "", "packageName", null, Integer.MAX_VALUE);
    assertThat(response).isNotNull();
    assertThat(response.size()).isEqualTo(3);
    for (BuildDetailsInternal bdi : response) {
      assertThat(bdi.getMetadata().get(ArtifactMetadataKeys.IMAGE)).startsWith("nexus.harness.io:8001/superApp:");
      assertThat(bdi.getMetadata().get(ArtifactMetadataKeys.IMAGE)).isNotEqualTo("nexus.harness.io:8001/superApp:");
    }
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testGetBuildsForNuget() throws IOException {
    NexusRequest nexusConfig = NexusRequest.builder()
                                   .nexusUrl(NEXUS_URL)
                                   .username(NEXUS_USERNAME)
                                   .password(NEXUS_PASSWORD.toCharArray())
                                   .artifactRepositoryUrl("nexus.harness.io:8001")
                                   .build();
    doReturn(buildDetailsData.get("bdi1"))
        .when(nexusClient)
        .getArtifactsVersions(nexusConfig, "nuget", "test1", "packageName");
    List<BuildDetailsInternal> response = nexusRegistryService.getBuilds(nexusConfig, "test1", null, "superApp",
        RepositoryFormat.nuget.name(), "groupId", "superApp", "war", "", "packageName", null, Integer.MAX_VALUE);
    assertThat(response).isNotNull();
    assertThat(response.size()).isEqualTo(3);
    for (BuildDetailsInternal bdi : response) {
      assertThat(bdi.getMetadata().get(ArtifactMetadataKeys.IMAGE)).startsWith("nexus.harness.io:8001/superApp:");
      assertThat(bdi.getMetadata().get(ArtifactMetadataKeys.IMAGE)).isNotEqualTo("nexus.harness.io:8001/superApp:");
    }
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testGetBuildsForRaw() throws IOException {
    NexusRequest nexusConfig = NexusRequest.builder()
                                   .nexusUrl(NEXUS_URL)
                                   .username(NEXUS_USERNAME)
                                   .password(NEXUS_PASSWORD.toCharArray())
                                   .artifactRepositoryUrl("nexus.harness.io:8001")
                                   .build();
    doReturn(buildDetailsData.get("bdi1")).when(nexusClient).getPackageNames(nexusConfig, "raw", "group");
    List<BuildDetailsInternal> response = nexusRegistryService.getBuilds(nexusConfig, "raw", null, "superApp",
        RepositoryFormat.raw.name(), "groupId", "superApp", "war", "", "packageName", "group", Integer.MAX_VALUE);
    assertThat(response).isNotNull();
    assertThat(response.size()).isEqualTo(3);
    for (BuildDetailsInternal bdi : response) {
      assertThat(bdi.getMetadata().get(ArtifactMetadataKeys.IMAGE)).startsWith("nexus.harness.io:8001/superApp:");
      assertThat(bdi.getMetadata().get(ArtifactMetadataKeys.IMAGE)).isNotEqualTo("nexus.harness.io:8001/superApp:");
    }
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testGetLastSuccessfulBuildFromRegex_1() throws IOException {
    NexusRequest nexusConfig = NexusRequest.builder()
                                   .nexusUrl(NEXUS_URL)
                                   .username(NEXUS_USERNAME)
                                   .password(NEXUS_PASSWORD.toCharArray())
                                   .artifactRepositoryUrl("nexus.harness.io:8001")
                                   .build();
    doReturn(buildDetailsData.get("bdi1"))
        .when(nexusClient)
        .getDockerArtifactVersions(
            nexusConfig, "test1", null, "superApp", RepositoryFormat.docker.name(), Integer.MAX_VALUE);
    BuildDetailsInternal response =
        nexusRegistryService.getLastSuccessfulBuildFromRegex(nexusConfig, "test1", null, "superApp",
            RepositoryFormat.docker.name(), "[\\d]{1}.0", null, null, null, null, null, null, Integer.MAX_VALUE);
    assertThat(response).isNotNull();
    assertThat(response.getMetadata().get(ArtifactMetadataKeys.TAG)).isEqualTo("3.0");
    assertThat(response.getMetadata().get(ArtifactMetadataKeys.IMAGE)).isEqualTo("nexus.harness.io:8001/superApp:3.0");
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testGetLastSuccessfulBuildFromRegex_2() throws IOException {
    NexusRequest nexusConfig = NexusRequest.builder()
                                   .nexusUrl(NEXUS_URL)
                                   .username(NEXUS_USERNAME)
                                   .password(NEXUS_PASSWORD.toCharArray())
                                   .artifactRepositoryUrl("nexus.harness.io:8002")
                                   .build();
    doReturn(buildDetailsData.get("bdi2"))
        .when(nexusClient)
        .getDockerArtifactVersions(
            nexusConfig, "test2", null, "super/duper/app", RepositoryFormat.docker.name(), Integer.MAX_VALUE);
    BuildDetailsInternal response = nexusRegistryService.getLastSuccessfulBuildFromRegex(nexusConfig, "test2", null,
        "super/duper/app", RepositoryFormat.docker.name(), "[\\d]{1}.[\\d]{1}.[\\d]{1}", null, null, null, null, null,
        null, Integer.MAX_VALUE);
    assertThat(response).isNotNull();
    assertThat(response.getMetadata().get(ArtifactMetadataKeys.TAG)).isEqualTo("2.5.3");
    assertThat(response.getMetadata().get(ArtifactMetadataKeys.IMAGE))
        .isEqualTo("nexus.harness.io:8002/super/duper/app:2.5.3");
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testGetLastSuccessfulBuildFromRegex_3() throws IOException {
    NexusRequest nexusConfig = NexusRequest.builder()
                                   .nexusUrl(NEXUS_URL)
                                   .username(NEXUS_USERNAME)
                                   .password(NEXUS_PASSWORD.toCharArray())
                                   .artifactRepositoryUrl("nexus.harness.io:8002")
                                   .build();

    doReturn(buildDetailsData.get("bdi3"))
        .when(nexusClient)
        .getDockerArtifactVersions(
            nexusConfig, "test2", null, "extra/megaapp", RepositoryFormat.docker.name(), Integer.MAX_VALUE);
    BuildDetailsInternal response =
        nexusRegistryService.getLastSuccessfulBuildFromRegex(nexusConfig, "test2", null, "extra/megaapp",
            RepositoryFormat.docker.name(), "\\\\*", null, null, null, null, null, null, Integer.MAX_VALUE);
    assertThat(response).isNotNull();
    assertThat(response.getMetadata().get(ArtifactMetadataKeys.TAG)).isEqualTo("latest");
    assertThat(response.getMetadata().get(ArtifactMetadataKeys.IMAGE))
        .isEqualTo("nexus.harness.io:8002/extra/megaapp:latest");
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testGetLastSuccessfulBuildFromRegexException() throws IOException {
    NexusRequest nexusConfig = NexusRequest.builder()
                                   .nexusUrl(NEXUS_URL)
                                   .username(NEXUS_USERNAME)
                                   .password(NEXUS_PASSWORD.toCharArray())
                                   .artifactRepositoryUrl("nexus.harness.io:8002")
                                   .build();
    doReturn(buildDetailsData.get("bdi3"))
        .when(nexusClient)
        .getDockerArtifactVersions(
            nexusConfig, "test2", null, "extra/megaapp", RepositoryFormat.docker.name(), Integer.MAX_VALUE);
    try {
      BuildDetailsInternal response =
          nexusRegistryService.getLastSuccessfulBuildFromRegex(nexusConfig, "test2", null, "extra/megaapp",
              RepositoryFormat.docker.name(), "/**/TEST", null, null, null, null, null, null, Integer.MAX_VALUE);
    } catch (HintException e) {
      assertThat(e.getMessage()).isEqualTo("Please check tagRegex field in Nexus artifact configuration.");
    }
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testGetLastSuccessfulBuildFromRegex_NoArtifactFound() throws IOException {
    NexusRequest nexusConfig = NexusRequest.builder()
                                   .nexusUrl(NEXUS_URL)
                                   .username(NEXUS_USERNAME)
                                   .password(NEXUS_PASSWORD.toCharArray())
                                   .artifactRepositoryUrl("nexus.harness.io:8002")
                                   .build();
    doReturn(buildDetailsData.get("bdi3"))
        .when(nexusClient)
        .getDockerArtifactVersions(
            nexusConfig, "test2", null, "extra/megaapp", RepositoryFormat.docker.name(), Integer.MAX_VALUE);
    assertThatThrownBy(()
                           -> nexusRegistryService.getLastSuccessfulBuildFromRegex(nexusConfig, "test2", null,
                               "extra/megaapp", RepositoryFormat.docker.name(), "noArtifactFound", null, null, null,
                               null, null, null, Integer.MAX_VALUE))
        .isInstanceOf(HintException.class);
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testVerifyBuildNumber() throws IOException {
    NexusRequest nexusConfig = NexusRequest.builder()
                                   .nexusUrl(NEXUS_URL)
                                   .username(NEXUS_USERNAME)
                                   .password(NEXUS_PASSWORD.toCharArray())
                                   .artifactRepositoryUrl("nexus.harness.io:8002")
                                   .build();
    doReturn(buildDetailsData.get("bdi3"))
        .when(nexusClient)
        .getBuildDetails(nexusConfig, "test2", null, "extra/megaapp", RepositoryFormat.docker.name(), "b23");
    BuildDetailsInternal response = nexusRegistryService.verifyBuildNumber(nexusConfig, "test2", null, "extra/megaapp",
        RepositoryFormat.docker.name(), "b23", null, null, null, null, null, null, Integer.MAX_VALUE);
    assertThat(response).isNotNull();
    assertThat(response.getMetadata().get(ArtifactMetadataKeys.TAG)).isEqualTo("b23");
    assertThat(response.getMetadata().get(ArtifactMetadataKeys.IMAGE))
        .isEqualTo("nexus.harness.io:8002/extra/megaapp:b23");
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testGetBuildNumber() throws IOException {
    NexusRequest nexusConfig = NexusRequest.builder()
                                   .nexusUrl(NEXUS_URL)
                                   .username(NEXUS_USERNAME)
                                   .password(NEXUS_PASSWORD.toCharArray())
                                   .artifactRepositoryUrl("nexus.harness.io:8002")
                                   .build();
    doReturn(buildDetailsData.get("bdi3"))
        .when(nexusClient)
        .getBuildDetails(nexusConfig, "test2", null, "extra/megaapp", RepositoryFormat.docker.name(), "b23");
    BuildDetailsInternal response = nexusRegistryService.verifyBuildNumber(nexusConfig, "test2", null, "extra/megaapp",
        RepositoryFormat.docker.name(), "b23", null, null, null, null, null, null, Integer.MAX_VALUE);
    assertThat(response).isNotNull();
    assertThat(response.getMetadata().get(ArtifactMetadataKeys.TAG)).isEqualTo("b23");
    assertThat(response.getMetadata().get(ArtifactMetadataKeys.IMAGE))
        .isEqualTo("nexus.harness.io:8002/extra/megaapp:b23");
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testVerifyBuildNumberForMaven() {
    NexusRequest nexusConfig = NexusRequest.builder()
                                   .nexusUrl(NEXUS_URL)
                                   .username(NEXUS_USERNAME)
                                   .password(NEXUS_PASSWORD.toCharArray())
                                   .artifactRepositoryUrl("nexus.harness.io:8002")
                                   .build();
    BuildDetailsInternal buildDetailsInternal =
        BuildDetailsInternal.builder().number("extra/megaapp").buildUrl("http://localhost:8080").build();
    doReturn(Collections.singletonList(buildDetailsInternal))
        .when(nexusClient)
        .getArtifactsVersions(nexusConfig, "test2", "group", "extra/megaapp", null, null, Integer.MAX_VALUE);
    try {
      BuildDetailsInternal response = nexusRegistryService.verifyBuildNumber(nexusConfig, "test2", null,
          "extra/megaapp", RepositoryFormat.maven.name(), "b23", null, null, null, null, null, null, Integer.MAX_VALUE);
    } catch (HintException exception) {
      assertThat(exception.getMessage()).isEqualTo("Please check your Nexus repository for artifacts with same tag.");
    }
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void validateCredentials() throws IOException {
    NexusRequest nexusConfig = NexusRequest.builder()
                                   .nexusUrl(NEXUS_URL)
                                   .username(NEXUS_USERNAME)
                                   .password(NEXUS_PASSWORD.toCharArray())
                                   .artifactRepositoryUrl("nexus.harness.io:8002")
                                   .build();
    doReturn(true).when(nexusClient).isRunning(nexusConfig);
    boolean response = nexusRegistryService.validateCredentials(nexusConfig);
    assertThat(response).isNotNull();
    assertThat(response).isEqualTo(true);
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testGetRepository() {
    NexusRequest nexusConfig = NexusRequest.builder()
                                   .nexusUrl(NEXUS_URL)
                                   .username(NEXUS_USERNAME)
                                   .password(NEXUS_PASSWORD.toCharArray())
                                   .artifactRepositoryUrl("nexus.harness.io:8002")
                                   .build();
    BuildDetailsInternal buildDetailsInternal =
        BuildDetailsInternal.builder().number("extra/megaapp").buildUrl("http://localhost:8080").build();
    doReturn(Collections.singletonMap("test", "repo"))
        .when(nexusClient)
        .getRepositories(nexusConfig, RepositoryFormat.maven.name());
    Map<String, String> response = nexusRegistryService.getRepository(nexusConfig, RepositoryFormat.maven.name());
    assertThat(response).isNotNull();
  }

  private BuildDetailsInternal createBuildDetails(
      String repoUrl, String port, String repoName, String imageName, String tag) {
    return BuildDetailsInternal.builder()
        .number(tag)
        .metadata(createBuildMetadata(repoUrl, port, repoName, imageName, tag))
        .build();
  }

  private String generateArtifactPullUrl(String hostname, String port, String imagePath, String imageTag) {
    return hostname + (isEmpty(port) ? "" : ":" + port) + "/" + imagePath + ":" + imageTag;
  }

  private Map<String, String> createBuildMetadata(
      String hostname, String port, String repoName, String imagePath, String imageTag) {
    Map<String, String> metadata = new HashMap<>();
    metadata.put(ArtifactMetadataKeys.IMAGE, generateArtifactPullUrl(hostname, port, imagePath, imageTag));
    metadata.put(ArtifactMetadataKeys.TAG, imageTag);
    return metadata;
  }
}
