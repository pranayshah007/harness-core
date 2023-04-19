/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.artifactory;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.ABHISHEK;
import static io.harness.rule.OwnerRule.ACASIAN;
import static io.harness.rule.OwnerRule.RAFAEL;
import static io.harness.rule.OwnerRule.TMACARI;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.jfrog.artifactory.client.model.impl.PackageTypeImpl.docker;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ArtifactMetaInfo;
import io.harness.category.element.UnitTests;
import io.harness.exception.WingsException;
import io.harness.rule.Owner;

import software.wings.helpers.ext.jenkins.BuildDetails;

import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.jfrog.artifactory.client.model.impl.PackageTypeImpl;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@OwnedBy(CDP)
public class ArtifactoryClientImplTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @InjectMocks ArtifactoryClientImpl artifactoryClient;

  /**
   * The Wire mock rule.
   */
  @Rule
  public WireMockRule wireMockRule =
      new WireMockRule(WireMockConfiguration.wireMockConfig()
                           .usingFilesUnderClasspath("960-api-services/src/test/resources")
                           .disableRequestJournal()
                           .port(0));

  String url;

  private ArtifactoryConfigRequest artifactoryConfig;
  private ArtifactoryConfigRequest artifactoryConfigAnonymous;
  private static final String SHA = "sha256:047ad42f407271c2ea16f62b25a0df3f6d63e3d8df7efdf531939050031576d9";

  @Before
  public void setUp() throws IllegalAccessException {
    url = String.format("http://localhost:%d/artifactory/", wireMockRule.port());
    artifactoryConfig = ArtifactoryConfigRequest.builder()
                            .artifactoryUrl(url)
                            .username("admin")
                            .password("dummy123!".toCharArray())
                            .build();
    artifactoryConfigAnonymous = ArtifactoryConfigRequest.builder().artifactoryUrl(url).build();
  }

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testGetRepositories() {
    Map<String, String> repositories = artifactoryClient.getRepositories(
        artifactoryConfig, Arrays.stream(PackageTypeImpl.values()).filter(type -> docker != type).collect(toList()));
    assertThat(repositories.size()).isEqualTo(4);
    assertThat(repositories.get("harness-maven")).isEqualTo("harness-maven");
    assertThat(repositories.get("harness-rpm")).isEqualTo("harness-rpm");
    assertThat(repositories.get("harness-maven-snapshots")).isEqualTo("harness-maven-snapshots");
    assertThat(repositories.get("harness-ivy")).isEqualTo("harness-ivy");
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void shouldGetRpmFilePaths() {
    List<BuildDetails> builds = artifactoryClient.getBuildDetails(artifactoryConfig, "harness-rpm", "todolist*/", 50);
    assertThat(builds).isNotNull();
    assertThat(builds).extracting(BuildDetails::getNumber).contains("todolist-1.0-2.x86_64.rpm");
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void shouldGetCorrectBuildNoWithAnyWildcardMatch() {
    List<BuildDetails> builds = artifactoryClient.getBuildDetails(
        artifactoryConfig, "harness-maven", "io/harness/todolist/todolist/*/*.war", 50);
    assertThat(builds).isNotNull();
    assertThat(builds)
        .extracting(BuildDetails::getNumber)
        .isEqualTo(Lists.newArrayList("1.2/todolist-1.2.war", "1.0.1.0-SNAPSHOT/todolist-1.0.1.0-20170930.195729-1.war",
            "1.0.0-SNAPSHOT/todolist-1.0.0-20170930.195402-1.war", "1.1/todolist-1.1.war", "1.0/todolist-1.0.war"))
        .contains("1.0.0-SNAPSHOT/todolist-1.0.0-20170930.195402-1.war");
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void shouldGetCorrectBuildNoForAtLeastOneWildcardPattern() {
    List<BuildDetails> builds = artifactoryClient.getBuildDetails(
        artifactoryConfig, "harness-maven", "io/harness/todolist/todolist/[0-9]+/*.war", 50);
    assertThat(builds).isNotNull();
    assertThat(builds)
        .extracting(BuildDetails::getNumber)
        .isEqualTo(Lists.newArrayList("1.2/todolist-1.2.war", "1.0.1.0-SNAPSHOT/todolist-1.0.1.0-20170930.195729-1.war",
            "1.0.0-SNAPSHOT/todolist-1.0.0-20170930.195402-1.war", "1.1/todolist-1.1.war", "1.0/todolist-1.0.war"));
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void shouldGetCorrectBuildNoForArtifactPathsWithoutAnyWildcardCharacter() {
    List<BuildDetails> builds = artifactoryClient.getBuildDetails(
        artifactoryConfig, "harness-maven", "/io/harness/todolist/todolist/1.0/todolist-1.0.war", 50);
    assertThat(builds).isNotNull();
    assertThat(builds)
        .extracting(BuildDetails::getNumber)
        .isEqualTo(Lists.newArrayList("io/harness/todolist/todolist/1.2/todolist-1.2.war",
            "io/harness/todolist/todolist/1.0.1.0-SNAPSHOT/todolist-1.0.1.0-20170930.195729-1.war",
            "io/harness/todolist/todolist/1.0.0-SNAPSHOT/todolist-1.0.0-20170930.195402-1.war",
            "io/harness/todolist/todolist/1.1/todolist-1.1.war", "io/harness/todolist/todolist/1.0/todolist-1.0.war"));
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void shouldGetCorrectBuildNoForArtifactPathsWithoutAnyWildcardCharacter1() {
    List<BuildDetails> builds = artifactoryClient.getBuildDetails(
        artifactoryConfig, "harness-maven", "io/harness/todolist/todolist/1.0/*.war", 50);
    assertThat(builds).isNotNull();
    assertThat(builds).extracting(BuildDetails::getNumber).contains("todolist-1.0.war");
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void shouldDownloadArtifact() {
    Map<String, String> metadata = ImmutableMap.of(
        "artifactPath", "harness-rpm/todolist-1.0-2.x86_64.rpm", "artifactFileName", "todolist-1.0-2.x86_64.rpm");
    InputStream artifactInputStream = artifactoryClient.downloadArtifacts(
        artifactoryConfig, "harness-rpm", metadata, "artifactPath", "artifactFileName");
    assertThat(artifactInputStream).isNotNull();
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void shouldGetArtifactFileSize() {
    Map<String, String> metadata =
        ImmutableMap.of("artifactPath", "harness-maven/io/harness/todolist/todolist/1.1/todolist-1.1.war");
    Long fileSize = artifactoryClient.getFileSize(artifactoryConfig, metadata, "artifactPath");
    assertThat(fileSize).isNotNull();
    assertThat(fileSize).isEqualTo(1776799L);
  }

  @Test
  @Owner(developers = RAFAEL)
  @Category(UnitTests.class)
  public void shouldGetLabels() {
    List<Map<String, String>> labels = artifactoryClient.getLabels(artifactoryConfig, "image", "docker", "version");
    Map<String, String> assertionMap = ImmutableMap.copyOf(labels.get(0));
    assertThat(assertionMap.size()).isGreaterThan(0);
    assertThat(assertionMap.containsKey("harness.test"));
    assertThat(assertionMap.containsKey("maintainer"));
    assertThat(assertionMap.get("harness.test").equals("passed"));
    assertThat(assertionMap.get("maintainer").equals("Test Harness.io"));
  }

  @Test
  @Owner(developers = RAFAEL)
  @Category(UnitTests.class)
  public void shouldGetLabelsIsEmpty() {
    List<Map<String, String>> labels =
        artifactoryClient.getLabels(artifactoryConfig, "image", "docker", "versionEmpty");
    Map<String, String> assertionMap = ImmutableMap.copyOf(labels.get(0));
    assertThat(assertionMap.size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = RAFAEL)
  @Category(UnitTests.class)
  public void shouldGetLabelsIs401() {
    assertThatThrownBy(() -> artifactoryClient.getLabels(artifactoryConfig, "image", "docker", "version407"))
        .isInstanceOf(WingsException.class);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void shouldGetArtifactMetaInfo() {
    ArtifactMetaInfo artifactMetaInfo =
        artifactoryClient.getArtifactMetaInfo(artifactoryConfig, "image", "docker", "version");
    Map<String, String> label = artifactMetaInfo.getLabels();
    assertThat(label.size()).isGreaterThan(0);
    assertThat(label.containsKey("harness.test"));
    assertThat(label.containsKey("maintainer"));
    assertThat(label.get("harness.test").equals("passed"));
    assertThat(label.get("maintainer").equals("Test Harness.io"));
    assertThat(artifactMetaInfo.getSha()).isEqualTo(SHA);
    assertThat(artifactMetaInfo.getShaV2()).isEqualTo(SHA);
  }
}
