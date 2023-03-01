/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.artifacts.docker.service;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.exception.ExceptionUtils.getMessage;
import static io.harness.logging.LoggingInitializer.initializeLogging;
import static io.harness.rule.OwnerRule.ANSHUL;
import static io.harness.rule.OwnerRule.ARCHIT;
import static io.harness.rule.OwnerRule.YUVRAJ;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.artifacts.beans.BuildDetailsInternal;
import io.harness.artifacts.docker.DockerRegistryRestClient;
import io.harness.artifacts.docker.beans.DockerInternalConfig;
import io.harness.artifacts.docker.beans.DockerPublicImageTagResponse;
import io.harness.artifacts.docker.client.DockerRestClientFactory;
import io.harness.artifacts.docker.client.DockerRestClientFactoryImpl;
import io.harness.category.element.UnitTests;
import io.harness.exception.HintException;
import io.harness.exception.InvalidArtifactServerException;
import io.harness.rule.Owner;
import io.harness.serializer.JsonUtils;

import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@OwnedBy(CDC)
public class DockerPublicRegistryProcessorTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  @Rule
  public WireMockRule wireMockRule = new WireMockRule(
      WireMockConfiguration.wireMockConfig().usingFilesUnderDirectory("960-api-services/src/test/resources").port(0),
      false);

  @Mock private DockerRestClientFactory dockerRestClientFactory;
  @Mock private DockerRegistryUtils dockerRegistryUtils;
  @Mock private DockerPublicImageTagResponse dockerPublicImageTagResponse;
  @Mock private DockerPublicImageTagResponse.Result result;
  @InjectMocks DockerPublicRegistryProcessor dockerPublicRegistryProcessor;

  private static DockerInternalConfig dockerConfig;
  private static DockerRegistryRestClient dockerRegistryRestClient;

  @Before
  public void beforeClass() {
    String url = "http://localhost:" + wireMockRule.port() + "/";
    dockerConfig =
        DockerInternalConfig.builder().dockerRegistryUrl(url).username("username").password("password").build();
    dockerRegistryRestClient = new DockerRestClientFactoryImpl().getDockerRegistryRestClient(dockerConfig);
    initializeLogging();
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testPaginate() throws Exception {
    List<BuildDetailsInternal> images = dockerPublicRegistryProcessor.paginate(null, dockerConfig, "image", null, 10);
    assertThat(images).isEmpty();

    when(result.getName()).thenReturn("1");
    when(dockerPublicImageTagResponse.getResults()).thenReturn(asList(result));
    when(dockerPublicImageTagResponse.getNext()).thenReturn("http://localhost:" + wireMockRule.port() + "/v2/");
    images = dockerPublicRegistryProcessor.paginate(dockerPublicImageTagResponse, dockerConfig, "image", null, 10);
    assertThat(images).isNotEmpty();
    assertThat(images.get(0).getUiDisplayName()).isEqualTo("Tag# 1");
    assertThat(images.get(0).getBuildUrl()).isEqualTo("http://localhost:" + wireMockRule.port() + "/image/tags/1");
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testGetBuildsWithUnAuthorisedException() throws IOException {
    try {
      doReturn(dockerRegistryRestClient).when(dockerRestClientFactory).getDockerRegistryRestClient(dockerConfig);
      dockerPublicRegistryProcessor.getBuilds(dockerConfig, "image", 10);
      fail("Should not reach here");
    } catch (HintException ex) {
      assertThat(getMessage(ex))
          .isEqualTo(
              "Update the username & password. Check if the provided credentials are correct. Invalid Docker Registry credentials");
    }
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testGetBuildsWithNotFoundException() {
    try {
      doReturn(dockerRegistryRestClient).when(dockerRestClientFactory).getDockerRegistryRestClient(dockerConfig);
      dockerPublicRegistryProcessor.getBuilds(dockerConfig, "image-1", 10);
      fail("Should not reach here");
    } catch (IOException | HintException ex) {
      assertThat(getMessage(ex))
          .isEqualTo(
              "Unable to fetch the tags for the image. Check if the image exists and if the permissions are scoped for the authenticated user. Not Found");
    }
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testGetBuildsPaginatedException() throws IOException {
    try {
      doReturn(dockerRegistryRestClient).when(dockerRestClientFactory).getDockerRegistryRestClient(dockerConfig);
      dockerPublicRegistryProcessor.getBuilds(dockerConfig, "image-paginated", 1);
      fail("Should not reach here");
    } catch (InvalidArtifactServerException ex) {
      assertThat(getMessage(ex)).isEqualTo("Server Error");
    }
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testGetLabels() {
    doReturn(dockerRegistryRestClient).when(dockerRestClientFactory).getDockerRegistryRestClient(dockerConfig);
    Map<String, String> labelMap = new HashMap<>();
    labelMap.put("abc", "abc");
    Map<String, String> anotherLabelMap = new HashMap<>();
    anotherLabelMap.put("abc1", "abc1");
    List<Map<String, String>> labelsMapList = Arrays.asList(labelMap, anotherLabelMap);

    doReturn(dockerRegistryRestClient).when(dockerRestClientFactory).getDockerRegistryRestClient(dockerConfig);
    doReturn(labelsMapList).when(dockerRegistryUtils).getLabels(any(), any(), any(), any(), any(), any());

    List<Map<String, String>> resultLabelsMap =
        dockerPublicRegistryProcessor.getLabels(dockerConfig, "image", asList("abc", "latest"));
    assertThat(resultLabelsMap).isNotNull();
    assertThat(resultLabelsMap.size()).isEqualTo(2);
    assertThat(resultLabelsMap.get(0).get("abc")).isEqualTo("abc");
    assertThat(resultLabelsMap.get(1).get("abc1")).isEqualTo("abc1");
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testVerifyImageName() {
    doReturn(dockerRegistryRestClient).when(dockerRestClientFactory).getDockerRegistryRestClient(dockerConfig);

    DockerPublicImageTagResponse dockerPublicImageTagResponse = new DockerPublicImageTagResponse();

    wireMockRule.stubFor(
        get(urlEqualTo("/v2/repositories/image/tags?page_size=1"))
            .willReturn(aResponse().withStatus(200).withBody(JsonUtils.asJson(dockerPublicImageTagResponse))));

    boolean image = dockerPublicRegistryProcessor.verifyImageName(dockerConfig, "image");
    assertThat(image).isTrue();
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testVerifyBuildNumber() throws IOException {
    doReturn(dockerRegistryRestClient).when(dockerRestClientFactory).getDockerRegistryRestClient(dockerConfig);

    DockerPublicImageTagResponse dockerPublicImageTagResponse = new DockerPublicImageTagResponse();

    wireMockRule.stubFor(
        get(urlEqualTo("/v2/repositories/image/tags/"))
            .willReturn(aResponse().withStatus(200).withBody(JsonUtils.asJson(dockerPublicImageTagResponse))));

    BuildDetailsInternal buildDetailsInternal =
        dockerPublicRegistryProcessor.verifyBuildNumber(dockerConfig, "image", null);
    assertThat(buildDetailsInternal).isNotNull();
    assertThat(buildDetailsInternal.getNumber()).isNull();
  }
}
