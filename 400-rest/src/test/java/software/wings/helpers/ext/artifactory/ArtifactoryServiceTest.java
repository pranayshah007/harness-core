/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.helpers.ext.artifactory;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.rule.OwnerRule.AADITI;
import static io.harness.rule.OwnerRule.AGORODETKI;
import static io.harness.rule.OwnerRule.DEEPAK_PUTHRAYA;
import static io.harness.rule.OwnerRule.GEORGE;
import static io.harness.rule.OwnerRule.SRINIVAS;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.joor.Reflect.on;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.artifactory.ArtifactoryClientImpl;
import io.harness.artifactory.ArtifactoryConfigRequest;
import io.harness.category.element.UnitTests;
import io.harness.delegate.task.ListNotifyResponseData;
import io.harness.exception.ArtifactoryServerException;
import io.harness.exception.ExplanationException;
import io.harness.exception.HintException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.rule.Owner;

import software.wings.beans.artifact.ArtifactMetadataKeys;
import software.wings.beans.artifact.ArtifactStreamAttributes;
import software.wings.beans.artifact.ArtifactStreamType;
import software.wings.helpers.ext.jenkins.BuildDetails;
import software.wings.utils.RepositoryType;

import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.google.common.collect.ImmutableMap;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.tuple.Pair;
import org.jfrog.artifactory.client.ArtifactoryClientBuilder;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@OwnedBy(CDC)
public class ArtifactoryServiceTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @InjectMocks ArtifactoryService artifactoryService = new ArtifactoryServiceImpl();
  @InjectMocks ArtifactoryClientImpl artifactoryClient = new ArtifactoryClientImpl();

  /**
   * The Wire mock rule.
   */
  @Rule
  public WireMockRule wireMockRule = new WireMockRule(WireMockConfiguration.wireMockConfig()
                                                          .usingFilesUnderClasspath("400-rest/src/test/resources")
                                                          .disableRequestJournal()
                                                          .port(0));

  String url;

  private ArtifactoryConfigRequest artifactoryConfig;
  private ArtifactoryConfigRequest artifactoryConfigAnonymous;

  @Before
  public void setUp() throws IllegalAccessException {
    url = String.format("http://localhost:%d/artifactory/", wireMockRule.port());
    artifactoryConfig = ArtifactoryConfigRequest.builder()
                            .artifactoryUrl(url)
                            .username("admin")
                            .password("dummy123!".toCharArray())
                            .build();
    artifactoryConfigAnonymous = ArtifactoryConfigRequest.builder().artifactoryUrl(url).build();
    on(artifactoryService).set("artifactoryClient", new ArtifactoryClientImpl());
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldGetMavenRepositories() {
    Map<String, String> repositories = artifactoryService.getRepositories(artifactoryConfig, "");
    assertThat(repositories).isNotNull();
    assertThat(repositories).containsKeys("harness-maven");
    assertThat(repositories).doesNotContainKeys("docker");
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldGetIvyRepositories() {
    Map<String, String> repositories = artifactoryService.getRepositories(artifactoryConfig, "");
    assertThat(repositories).isNotNull();
    assertThat(repositories).containsKeys("harness-ivy");
    assertThat(repositories).doesNotContainKeys("docker");
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldGetDockerRepositoriesWithArtifactType() {
    Map<String, String> repositories = artifactoryService.getRepositories(artifactoryConfig, RepositoryType.docker);
    assertThat(repositories).isNotNull();
    assertThat(repositories).containsKeys("docker");
    assertThat(repositories).doesNotContainKeys("harness-maven");
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldGetDockerRepositories() {
    Map<String, String> repositories = artifactoryService.getRepositories(artifactoryConfig);
    assertThat(repositories).isNotNull();
    assertThat(repositories).containsKeys("docker");
    assertThat(repositories).doesNotContainKeys("harness-maven");
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void testGetRepositoriesForMavenWithPackageType() {
    Map<String, String> repositories = artifactoryService.getRepositories(artifactoryConfig, "maven");
    assertThat(repositories).isNotNull();
    assertThat(repositories).containsKeys("harness-maven");
    assertThat(repositories).containsKeys("harness-maven-snapshots");
    assertThat(repositories).doesNotContainKeys("docker");
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldGetRpmRepositories() {
    Map<String, String> repositories = artifactoryService.getRepositories(artifactoryConfig, "");
    assertThat(repositories).isNotNull();
    assertThat(repositories).containsKeys("harness-rpm");
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldGetDockerImages() {
    List<String> repositories = artifactoryService.getRepoPaths(artifactoryConfig, "docker");
    assertThat(repositories).isNotNull();
    assertThat(repositories).contains("wingsplugins/todolist");
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldGetDockerTags() {
    List<BuildDetails> builds = artifactoryService.getBuilds(artifactoryConfig,
        ArtifactStreamAttributes.builder()
            .artifactStreamType(ArtifactStreamType.ARTIFACTORY.name())
            .metadataOnly(true)
            .jobName("docker")
            .imageName("wingsplugins/todolist")
            .artifactoryDockerRepositoryServer("harness.jfrog.com")
            .artifactServerEncryptedDataDetails(Collections.emptyList())
            .build(),
        50);
    assertThat(builds).isNotNull();
    assertThat(builds).extracting(BuildDetails::getNumber).contains("latest");
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldGetRpmFilePaths() {
    List<BuildDetails> builds =
        artifactoryService.getFilePaths(artifactoryConfig, "harness-rpm", "todolist*/", "generic", 50);
    assertThat(builds).isNotNull();
    assertThat(builds).extracting(BuildDetails::getNumber).contains("todolist-1.0-2.x86_64.rpm");
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldGetCorrectBuildNoWithAnyWildcardMatch() {
    List<BuildDetails> builds = artifactoryService.getFilePaths(
        artifactoryConfig, "harness-maven", "io/harness/todolist/todolist/*/*.war", "any", 50);
    assertThat(builds).isNotNull();
    assertThat(builds)
        .extracting(BuildDetails::getNumber)
        .contains("1.0.0-SNAPSHOT/todolist-1.0.0-20170930.195402-1.war");
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldGetCorrectBuildNoForAtLeastOneWildcardPattern() {
    List<BuildDetails> builds = artifactoryService.getFilePaths(
        artifactoryConfig, "harness-maven", "io/harness/todolist/todolist/[0-9]+/*.war", "any", 50);
    assertThat(builds).isNotNull();
    assertThat(builds)
        .extracting(BuildDetails::getNumber)
        .contains("1.0.0-SNAPSHOT/todolist-1.0.0-20170930.195402-1.war");
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldGetCorrectBuildNoForArtifactPathsWithoutAnyWildcardCharacter() {
    List<BuildDetails> builds = artifactoryService.getFilePaths(
        artifactoryConfig, "harness-maven", "/io/harness/todolist/todolist/1.0/todolist-1.0.war", "any", 50);
    assertThat(builds).isNotNull();
    assertThat(builds)
        .extracting(BuildDetails::getNumber)
        .contains("io/harness/todolist/todolist/1.0/todolist-1.0.war");
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldGetCorrectBuildNoForArtifactPathsWithoutAnyWildcardCharacter1() {
    List<BuildDetails> builds = artifactoryService.getFilePaths(
        artifactoryConfig, "harness-maven", "io/harness/todolist/todolist/1.0/*.war", "any", 50);
    assertThat(builds).isNotNull();
    assertThat(builds).extracting(BuildDetails::getNumber).contains("todolist-1.0.war");
  }

  @Test(expected = WingsException.class)
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldDownloadRpmArtifacts() {
    ListNotifyResponseData listNotifyResponseData =
        artifactoryService.downloadArtifacts(artifactoryConfig, "harness-rpm",
            ImmutableMap.of(ArtifactMetadataKeys.artifactPath, "harness-rpm/todolist-1.0-2.x86_64.rpm",
                ArtifactMetadataKeys.artifactFileName, "todolist-1.0-2.x86_64.rpm"),
            "delegateId", "taskId", "ACCOUNT_ID");
    assertThat(listNotifyResponseData).isNotNull();
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldValidateArtifactPath() {
    assertThat(artifactoryService.validateArtifactPath(artifactoryConfig, "harness-rpm", "todolist*", "generic"))
        .isTrue();
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldValidateArtifactPathAnonymous() {
    assertThat(
        artifactoryService.validateArtifactPath(artifactoryConfigAnonymous, "harness-rpm", "todolist*", "generic"))
        .isTrue();
  }

  @Test(expected = WingsException.class)
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldValidateArtifactPathPasswordEmpty() {
    ArtifactoryConfigRequest artifactoryConfigNoPassword = ArtifactoryConfigRequest.builder()
                                                               .artifactoryUrl("some url")
                                                               .username("some username")
                                                               .hasCredentials(true)
                                                               .build();
    artifactoryService.validateArtifactPath(artifactoryConfigNoPassword, "harness-rpm", "todolist*", "generic");
  }

  @Test(expected = WingsException.class)
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldValidateArtifactPathEmpty() {
    artifactoryService.validateArtifactPath(artifactoryConfig, "harness-rpm", "", "generic");
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldValidateArtifactPathMaven() {
    artifactoryService.validateArtifactPath(
        artifactoryConfig, "harness-rpm", "io/harness/todolist/*/todolist", "maven");
  }

  @Test(expected = WingsException.class)
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldDownloadRpmArtifact() {
    Pair<String, InputStream> pair = artifactoryService.downloadArtifact(artifactoryConfig, "harness-rpm",
        ImmutableMap.of(ArtifactMetadataKeys.artifactPath, "harness-rpm/todolist-1.0-2.x86_64.rpm",
            ArtifactMetadataKeys.artifactFileName, "todolist-1.0-2.x86_64.rpm"));
    assertThat(pair).isNotNull();
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldGetFileSize() {
    Map<String, String> metadata = new HashMap<>();
    metadata.put(ArtifactMetadataKeys.artifactPath, "harness-maven/io/harness/todolist/todolist/1.1/todolist-1.1.war");
    Long size = artifactoryService.getFileSize(artifactoryConfig, metadata);
    assertThat(size).isEqualTo(1776799L);
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldFailWhenSizeIsNull() {
    Map<String, String> metadata = new HashMap<>();
    metadata.put(ArtifactMetadataKeys.artifactPath, "harness-maven/io/harness/todolist/todolist/1.2/todolist-1.2.war");
    assertThatThrownBy(() -> artifactoryService.getFileSize(artifactoryConfig, metadata))
        .isInstanceOf(ArtifactoryServerException.class)
        .hasMessageContaining("Unable to get artifact file size. The file probably does not exist");
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldTestArtifactoryRunning() {
    assertThat(artifactoryClient.isRunning(artifactoryConfig)).isTrue();
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldThrowExceptionOnArtifactoryResponseWith407StatusCode() {
    ArtifactoryConfigRequest artifactoryConfig =
        ArtifactoryConfigRequest.builder()
            .artifactoryUrl(String.format("http://localhost:%d/artifactory-407/", wireMockRule.port()))
            .username("admin")
            .password("dummy123!".toCharArray())
            .build();

    assertThatThrownBy(() -> artifactoryClient.isRunning(artifactoryConfig))
        .isInstanceOf(HintException.class)
        .getCause()
        .isInstanceOf(ExplanationException.class)
        .getCause()
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Proxy Authentication Required");
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void shouldThrowExceptionOnArtifactoryResponseWith500StatusCode() {
    ArtifactoryConfigRequest artifactoryConfig =
        ArtifactoryConfigRequest.builder()
            .artifactoryUrl(String.format("http://localhost:%d/artifactory-500/", wireMockRule.port()))
            .username("admin")
            .password("dummy123!".toCharArray())
            .build();

    assertThatThrownBy(() -> artifactoryClient.isRunning(artifactoryConfig))
        .isInstanceOf(HintException.class)
        .getCause()
        .isInstanceOf(ExplanationException.class)
        .getCause()
        .isInstanceOf(ArtifactoryServerException.class)
        .hasMessageContaining("Request to server failed with status code: 500");
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void shouldThrowExceptionOnArtifactoryResponseWith500StatusCodeForGetRespositories() {
    ArtifactoryConfigRequest artifactoryConfig =
        ArtifactoryConfigRequest.builder()
            .artifactoryUrl(String.format("http://localhost:%d/artifactory-500/", wireMockRule.port()))
            .username("admin")
            .password("dummy123!".toCharArray())
            .build();

    assertThatThrownBy(() -> artifactoryService.getRepositories(artifactoryConfig))
        .isInstanceOf(HintException.class)
        .getCause()
        .isInstanceOf(ExplanationException.class)
        .getCause()
        .isInstanceOf(ArtifactoryServerException.class)
        .hasMessageContaining("Request to server failed with status code: 500");
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void testGetRepositoriesWithRepositoryType() {
    Map<String, String> repositories = artifactoryService.getRepositories(artifactoryConfig, RepositoryType.docker);
    assertThat(repositories).isNotNull();
    assertThat(repositories).containsKeys("docker");
    assertThat(repositories).doesNotContainKeys("harness-maven");
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void testGetMavenRepositoriesWithRepositoryType() {
    Map<String, String> repositories = artifactoryService.getRepositories(artifactoryConfig, RepositoryType.maven);
    assertThat(repositories).isNotNull();
    assertThat(repositories).containsKeys("harness-maven");
    assertThat(repositories).doesNotContainKeys("docker");
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void testGetAnyRepositoriesWithRepositoryType() {
    Map<String, String> repositories = artifactoryService.getRepositories(artifactoryConfig, RepositoryType.any);
    assertThat(repositories).isNotNull();
    assertThat(repositories).containsKeys("harness-rpm");
    assertThat(repositories).doesNotContainKeys("docker");
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void testGetDefaultRepositoriesWithRepositoryType() {
    Map<String, String> repositories = artifactoryService.getRepositories(artifactoryConfig, RepositoryType.nuget);
    assertThat(repositories).isNotNull();
    assertThat(repositories).containsKeys("harness-rpm");
    assertThat(repositories).doesNotContainKeys("docker");
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldGetFilePathsWithWildCardForAnonymousUser() {
    List<BuildDetails> builds =
        artifactoryService.getFilePaths(artifactoryConfigAnonymous, "harness-maven", "tdlist/*/*.war", "any", 50);
    assertThat(builds).isNotNull();
    assertThat(builds)
        .extracting(BuildDetails::getNumber)
        .contains("tdlist/1.1/tdlist-1.1.war", "tdlist/1.2/tdlist-1.2.war");
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void shouldGetFilePathsWithWildCardForAnonymousUser1() {
    List<BuildDetails> builds =
        artifactoryService.getFilePaths(artifactoryConfigAnonymous, "harness-maven", "tdlist/1.1/*.war", "any", 50);
    assertThat(builds).isNotNull();
    assertThat(builds).extracting(BuildDetails::getNumber).contains("tdlist-1.1.war");
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void shouldThrowExceptionWhenEmptyArtifactPath() {
    assertThatThrownBy(
        () -> artifactoryService.getFilePaths(artifactoryConfigAnonymous, "harness-maven", "    ", "any", 50))
        .isInstanceOf(ArtifactoryServerException.class)
        .extracting("message")
        .isEqualTo("Artifact path can not be empty");
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldGetFilePathsForAnonymousUser() {
    List<BuildDetails> builds =
        artifactoryService.getFilePaths(artifactoryConfigAnonymous, "harness-maven", "//myartifact/", "any", 50);
    assertThat(builds).isNotNull();
    assertThat(builds).extracting(BuildDetails::getNumber).contains("myartifact2");
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldAppendProxyConfig() {
    System.setProperty("http.proxyHost", "proxyHost");
    System.setProperty("proxyScheme", "http");
    System.setProperty("http.proxyPort", "123");
    ArtifactoryConfigRequest artifactoryConfig = ArtifactoryConfigRequest.builder().artifactoryUrl("url").build();
    ArtifactoryClientBuilder artifactoryClientBuilder = ArtifactoryClientBuilder.create();
    ((ArtifactoryServiceImpl) artifactoryService)
        .checkIfUseProxyAndAppendConfig(artifactoryClientBuilder, artifactoryConfig);

    assertThat(artifactoryClientBuilder.getProxy()).isNotNull();
    assertThat(artifactoryClientBuilder.getProxy().getHost()).isEqualTo("proxyHost");
    System.clearProperty("http.proxyHost");
    System.clearProperty("http.proxyPort");
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldNotAppendProxyConfigIfArtifactoryUrlIsInNonProxyList() {
    System.setProperty("http.proxyHost", "proxyHost");
    System.setProperty("http.proxyPort", "123");
    System.setProperty("http.nonProxyHosts", "url");
    ArtifactoryConfigRequest artifactoryConfig = ArtifactoryConfigRequest.builder().artifactoryUrl("url").build();
    ArtifactoryClientBuilder artifactoryClientBuilder = ArtifactoryClientBuilder.create();
    ((ArtifactoryServiceImpl) artifactoryService)
        .checkIfUseProxyAndAppendConfig(artifactoryClientBuilder, artifactoryConfig);

    assertThat(artifactoryClientBuilder.getProxy()).isNull();

    System.clearProperty("http.proxyHost");
    System.clearProperty("http.proxyPort");
    System.clearProperty("http.nonProxyHosts");
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldNotAppendProxyConfigWhenProxyIsNotEnabled() {
    ArtifactoryConfigRequest artifactoryConfig = ArtifactoryConfigRequest.builder().artifactoryUrl("url").build();
    ArtifactoryClientBuilder artifactoryClientBuilder = ArtifactoryClientBuilder.create();
    ((ArtifactoryServiceImpl) artifactoryService)
        .checkIfUseProxyAndAppendConfig(artifactoryClientBuilder, artifactoryConfig);

    assertThat(artifactoryClientBuilder.getProxy()).isNull();
  }
}
