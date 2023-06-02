/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.artifacts.artifactory;

import static io.harness.rule.OwnerRule.MLUKIC;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.artifact.ArtifactMetadataKeys;
import io.harness.artifactory.ArtifactoryClientImpl;
import io.harness.artifactory.ArtifactoryConfigRequest;
import io.harness.artifacts.beans.BuildDetailsInternal;
import io.harness.category.element.UnitTests;
import io.harness.exception.ArtifactoryRegistryException;
import io.harness.exception.ArtifactoryServerException;
import io.harness.exception.ExplanationException;
import io.harness.exception.HintException;
import io.harness.exception.InvalidRequestException;
import io.harness.rule.Owner;

import software.wings.utils.RepositoryFormat;

import com.github.tomakehurst.wiremock.core.Options;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@OwnedBy(HarnessTeam.CDP)
public class ArtifactoryClientImplTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  @Rule
  public WireMockRule wireMockRule =
      new WireMockRule(WireMockConfiguration.wireMockConfig().port(Options.DYNAMIC_PORT), false);
  @Rule
  public WireMockRule wireMockRule2 =
      new WireMockRule(WireMockConfiguration.wireMockConfig()
                           .usingFilesUnderClasspath("960-api-services/src/test/resources")
                           .disableRequestJournal()
                           .bindAddress("127.0.0.1")
                           .port(0));

  @InjectMocks private ArtifactoryClientImpl artifactoryClient;
  private ArtifactoryConfigRequest artifactoryConfig2;

  private static String url;
  private static String url2;

  @Before
  public void before() {
    MockitoAnnotations.initMocks(this);
    url = "http://localhost:" + wireMockRule.port();

    url2 = String.format("http://127.0.0.1:%d/artifactory/", wireMockRule2.port());
    artifactoryConfig2 = ArtifactoryConfigRequest.builder()
                             .artifactoryUrl(url2)
                             .username("admin")
                             .password("dummy123!".toCharArray())
                             .hasCredentials(true)
                             .build();
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testValidateArtifactServer() {
    ArtifactoryConfigRequest artifactoryConfigRequest = ArtifactoryConfigRequest.builder()
                                                            .artifactoryUrl(url)
                                                            .username("username")
                                                            .password("password".toCharArray())
                                                            .hasCredentials(true)
                                                            .artifactRepositoryUrl(url)
                                                            .build();

    wireMockRule.stubFor(get(urlEqualTo("/api/repositories/")).willReturn(aResponse().withStatus(200)));

    boolean response = artifactoryClient.validateArtifactServer(artifactoryConfigRequest);
    assertThat(response).isEqualTo(true);

    wireMockRule.stubFor(get(urlEqualTo("/api/repositories/")).willReturn(aResponse().withStatus(400)));

    assertThatThrownBy(() -> artifactoryClient.validateArtifactServer(artifactoryConfigRequest))
        .isInstanceOf(HintException.class)
        .getCause()
        .isInstanceOf(ExplanationException.class)
        .getCause()
        .isInstanceOf(ArtifactoryServerException.class);
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testIsRunning() {
    ArtifactoryConfigRequest artifactoryConfigRequest = ArtifactoryConfigRequest.builder()
                                                            .artifactoryUrl(url)
                                                            .username("username")
                                                            .password("password".toCharArray())
                                                            .hasCredentials(true)
                                                            .artifactRepositoryUrl(url)
                                                            .build();

    wireMockRule.stubFor(get(urlEqualTo("/api/repositories/")).willReturn(aResponse().withStatus(200)));

    boolean response = artifactoryClient.isRunning(artifactoryConfigRequest);
    assertThat(response).isEqualTo(true);

    wireMockRule.stubFor(get(urlEqualTo("/api/repositories/")).willReturn(aResponse().withStatus(407)));

    assertThatThrownBy(() -> artifactoryClient.validateArtifactServer(artifactoryConfigRequest))
        .isInstanceOf(HintException.class)
        .getCause()
        .isInstanceOf(ExplanationException.class)
        .getCause()
        .isInstanceOf(InvalidRequestException.class);

    wireMockRule.stubFor(get(urlEqualTo("/api/repositories/")).willReturn(aResponse().withStatus(404)));

    assertThatThrownBy(() -> artifactoryClient.validateArtifactServer(artifactoryConfigRequest))
        .isInstanceOf(HintException.class)
        .getCause()
        .isInstanceOf(ExplanationException.class)
        .getCause()
        .isInstanceOf(ArtifactoryServerException.class);
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testGetArtifactsDetailsSuccess() {
    String repoKey = "TestRepoKey";
    String artifactPath = "test/artifact";

    wireMockRule2.stubFor(get(urlEqualTo("/artifactory/api/repositories/" + repoKey))
                              .willReturn(aResponse().withStatus(200).withBody("{\n"
                                  + "    \"key\": \"testRepoKey\",\n"
                                  + "    \"packageType\": \"docker\",\n"
                                  + "    \"description\": \"\",\n"
                                  + "    \"rclass\": \"local\"\n"
                                  + "}")));

    wireMockRule2.stubFor(get(urlEqualTo("/artifactory/api/docker/" + repoKey + "/v2/_catalog"))
                              .willReturn(aResponse().withStatus(200).withBody("{\n"
                                  + "    \"repositories\": [\n"
                                  + "        \"alpine\",\n"
                                  + "        \"nginx\",\n"
                                  + "        \"test\",\n"
                                  + "        \"foo\",\n"
                                  + "        \"" + artifactPath + "\"\n"
                                  + "    ]\n"
                                  + "}")));

    wireMockRule2.stubFor(get(urlEqualTo("/artifactory/api/docker/" + repoKey + "/v2/" + artifactPath + "/tags/list"))
                              .willReturn(aResponse().withStatus(200).withBody("{\n"
                                  + "    \"name\": \"" + artifactPath + "\",\n"
                                  + "    \"tags\": [\n"
                                  + "        \"1.0\",\n"
                                  + "        \"2.0\",\n"
                                  + "        \"3-unit\",\n"
                                  + "        \"3-test\"\n"
                                  + "    ]\n"
                                  + "}")));

    List<BuildDetailsInternal> response = artifactoryClient.getArtifactsDetails(
        artifactoryConfig2, repoKey, artifactPath, RepositoryFormat.docker.name());

    assertThat(response.size()).isEqualTo(4);
    List<String> dockerPullCommands =
        response.stream().map(bdi -> bdi.getMetadata().get(ArtifactMetadataKeys.IMAGE)).collect(Collectors.toList());

    String fullRepoPath = "127-" + repoKey + ".0.0.1:" + wireMockRule2.port() + "/" + artifactPath;
    assertThat(dockerPullCommands)
        .contains(fullRepoPath + ":1.0", fullRepoPath + ":2.0", fullRepoPath + ":3-unit", fullRepoPath + ":3-test");
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testGetArtifactsDetailsNonexistentRepository() {
    String repoKey = "TestRepoKey";
    String artifactPath = "test/artifact";

    wireMockRule2.stubFor(get(urlEqualTo("/artifactory/api/repositories/" + repoKey))
                              .willReturn(aResponse().withStatus(400).withBody("{\n"
                                  + "    \"errors\": [\n"
                                  + "        {\n"
                                  + "            \"status\": 400,\n"
                                  + "            \"message\": \"Bad Request\"\n"
                                  + "        }\n"
                                  + "    ]\n"
                                  + "}")));

    assertThatThrownBy(()
                           -> artifactoryClient.getArtifactsDetails(
                               artifactoryConfig2, repoKey, artifactPath, RepositoryFormat.docker.name()))
        .isInstanceOf(HintException.class)
        .getCause()
        .isInstanceOf(ExplanationException.class)
        .getCause()
        .isInstanceOf(ArtifactoryRegistryException.class);
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testGetArtifactsDetailsNonexistentDockerImage() {
    String repoKey = "TestRepoKey";
    String artifactPath = "test/artifact";

    wireMockRule2.stubFor(get(urlEqualTo("/artifactory/api/repositories/" + repoKey))
                              .willReturn(aResponse().withStatus(200).withBody("{\n"
                                  + "    \"key\": \"testRepoKey\",\n"
                                  + "    \"packageType\": \"docker\",\n"
                                  + "    \"description\": \"\",\n"
                                  + "    \"rclass\": \"local\"\n"
                                  + "}")));

    wireMockRule2.stubFor(get(urlEqualTo("/artifactory/api/docker/" + repoKey + "/v2/_catalog"))
                              .willReturn(aResponse().withStatus(404).withBody("{\n"
                                  + "    \"errors\": [\n"
                                  + "        {\n"
                                  + "            \"status\": 404,\n"
                                  + "            \"message\": \"Not Found\"\n"
                                  + "        }\n"
                                  + "    ]\n"
                                  + "}")));

    assertThatThrownBy(()
                           -> artifactoryClient.getArtifactsDetails(
                               artifactoryConfig2, repoKey, artifactPath, RepositoryFormat.docker.name()))
        .isInstanceOf(HintException.class)
        .getCause()
        .isInstanceOf(ExplanationException.class);
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testGetArtifactsDetailsNonexistentDockerImage2() {
    String repoKey = "TestRepoKey";
    String artifactPath = "test/artifact";

    wireMockRule2.stubFor(get(urlEqualTo("/artifactory/api/repositories/" + repoKey))
                              .willReturn(aResponse().withStatus(200).withBody("{\n"
                                  + "    \"key\": \"testRepoKey\",\n"
                                  + "    \"packageType\": \"docker\",\n"
                                  + "    \"description\": \"\",\n"
                                  + "    \"rclass\": \"local\"\n"
                                  + "}")));

    wireMockRule2.stubFor(get(urlEqualTo("/artifactory/api/docker/" + repoKey + "/v2/_catalog"))
                              .willReturn(aResponse().withStatus(200).withBody("{\n"
                                  + "    \"repositories\": [\n"
                                  + "        \"alpine\",\n"
                                  + "        \"nginx\",\n"
                                  + "        \"test\",\n"
                                  + "        \"foo\"\n"
                                  + "    ]\n"
                                  + "}")));

    assertThatThrownBy(()
                           -> artifactoryClient.getArtifactsDetails(
                               artifactoryConfig2, repoKey, artifactPath, RepositoryFormat.docker.name()))
        .isInstanceOf(HintException.class)
        .getCause()
        .isInstanceOf(ExplanationException.class)
        .getCause()
        .isInstanceOf(ArtifactoryRegistryException.class);
  }
}
