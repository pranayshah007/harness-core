/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.helpers.ext.gcr;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.rule.OwnerRule.ABHISHEK;
import static io.harness.rule.OwnerRule.ABOSII;
import static io.harness.rule.OwnerRule.DEEPAK_PUTHRAYA;
import static io.harness.rule.OwnerRule.SHIVAM;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.OwnedBy;
import io.harness.artifacts.beans.BuildDetailsInternal;
import io.harness.artifacts.docker.service.DockerRegistryUtils;
import io.harness.artifacts.gcr.GcrRestClient;
import io.harness.artifacts.gcr.beans.GcrInternalConfig;
import io.harness.artifacts.gcr.service.GcrApiServiceImpl;
import io.harness.beans.ArtifactMetaInfo;
import io.harness.category.element.UnitTests;
import io.harness.exception.HintException;
import io.harness.exception.WingsException;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.service.mappers.artifact.GcrConfigToInternalMapper;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.google.common.collect.Lists;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;

@OwnedBy(CDC)
public class GcrApiServiceTest extends WingsBaseTest {
  @InjectMocks GcrApiServiceImpl gcrApiService;
  @Mock DockerRegistryUtils dockerRegistryUtils;
  GcrApiServiceImpl gcrService;
  private static final String SHA = "sha256:1123234243";
  @Rule
  public WireMockRule wireMockRule = new WireMockRule(
      WireMockConfiguration.wireMockConfig().usingFilesUnderDirectory("400-rest/src/test/resources").port(0));
  private String url;
  String basicAuthHeader = "auth";
  GcrInternalConfig gcpInternalConfig;
  @Mock GcrRestClient gcrRestClient;

  private static final Map<String, String> label = Map.of("a", "b", "c", "d");
  private static final String LATEST = "latest";
  private static final String SOME_IMAGE = "someImage";

  @Before
  public void setUp() {
    gcrService = spy(gcrApiService);
    url = "localhost:" + wireMockRule.port();
    gcpInternalConfig = GcrConfigToInternalMapper.toGcpInternalConfig(url, basicAuthHeader);
    wireMockRule.stubFor(WireMock.get(WireMock.urlEqualTo("/v2/someImage/tags/list"))
                             .withHeader("Authorization", equalTo("auth"))
                             .willReturn(aResponse().withStatus(200).withBody(
                                 "{\"name\":\"someImage\",\"tags\":[\"v1\",\"v2\",\"latest\"]}")));

    wireMockRule.stubFor(
        WireMock.get(WireMock.urlEqualTo("/v2/someImage/manifests/latest"))
            .withHeader("Authorization", equalTo("auth"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Docker-Content-Digest", SHA)
                    .withBody("{\n"
                        + "    \"schemaVersion\": 2,\n"
                        + "    \"mediaType\": \"application/vnd.docker.distribution.manifest.v2+json\",\n"
                        + "    \"config\": {\n"
                        + "        \"mediaType\": \"application/vnd.docker.container.image.v1+json\",\n"
                        + "        \"size\": 1457,\n"
                        + "        \"digest\": \"sha256:7a80323521ccd4c2b4b423fa6e38e5cea156600f40cd855e464cc52a321a24dd\"\n"
                        + "    },\n"
                        + "    \"layers\": [\n"
                        + "        {\n"
                        + "            \"mediaType\": \"application/vnd.docker.image.rootfs.diff.tar.gzip\",\n"
                        + "            \"size\": 773262,\n"
                        + "            \"digest\": \"sha256:50783e0dfb64b73019e973e7bce2c0d5a882301b781327ca153b876ad758dbd3\"\n"
                        + "        }\n"
                        + "    ]\n"
                        + "}")));

    wireMockRule.stubFor(
        WireMock.get(WireMock.urlEqualTo("/v2/someImage/manifests/sha256:1123234243"))
            .withHeader("Authorization", equalTo("auth"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Docker-Content-Digest", SHA)
                    .withBody("{\n"
                        + "    \"schemaVersion\": 2,\n"
                        + "    \"mediaType\": \"application/vnd.docker.distribution.manifest.v2+json\",\n"
                        + "    \"config\": {\n"
                        + "        \"mediaType\": \"application/vnd.docker.container.image.v1+json\",\n"
                        + "        \"size\": 1457,\n"
                        + "        \"digest\": \"sha256:7a80323521ccd4c2b4b423fa6e38e5cea156600f40cd855e464cc52a321a24dd\"\n"
                        + "    },\n"
                        + "    \"layers\": [\n"
                        + "        {\n"
                        + "            \"mediaType\": \"application/vnd.docker.image.rootfs.diff.tar.gzip\",\n"
                        + "            \"size\": 773262,\n"
                        + "            \"digest\": \"sha256:50783e0dfb64b73019e973e7bce2c0d5a882301b781327ca153b876ad758dbd3\"\n"
                        + "        }\n"
                        + "    ]\n"
                        + "}")));

    wireMockRule.stubFor(WireMock.get(WireMock.urlEqualTo("/v2/noImage/tags/list"))
                             .withHeader("Authorization", equalTo("auth"))
                             .willReturn(aResponse().withStatus(404)));

    wireMockRule.stubFor(
        WireMock.get(WireMock.urlEqualTo("/v2/invalidProject/tags/list"))
            .withHeader("Authorization", equalTo("auth"))
            .willReturn(aResponse().withStatus(403).withBody(
                "{\"errors\":[{\"code\":\"UNKNOWN\",\"message\":\"Project 'project:project-name' not found or deleted.\"}]}")));

    wireMockRule.stubFor(WireMock.get(WireMock.urlEqualTo("/v2/teapot/tags/list"))
                             .withHeader("Authorization", equalTo("auth"))
                             .willReturn(aResponse().withStatus(418).withBody("I'm a teapot")));
    when(gcrService.getUrl(anyString())).thenReturn("http://" + url);
    // Remove retry back-off for faster testing.
    gcrService.retry = Retry.of("GCRRegistryTest", RetryConfig.custom().maxAttempts(GcrApiServiceImpl.RETRIES).build());
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void shouldGetBuilds() {
    List<BuildDetailsInternal> actual = gcrService.getBuilds(gcpInternalConfig, SOME_IMAGE, 100);
    assertThat(actual).hasSize(3);
    assertThat(actual.stream().map(BuildDetailsInternal::getNumber).collect(Collectors.toList()))
        .isEqualTo(Lists.newArrayList(LATEST, "v1", "v2"));

    gcrService.getBuilds(GcrConfigToInternalMapper.toGcpInternalConfig(url, basicAuthHeader), SOME_IMAGE, 100);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void shouldGetBuildFailure() {
    assertThatThrownBy(() -> gcrService.getBuilds(gcpInternalConfig, "noImage", 100))
        .isInstanceOf(HintException.class)
        .hasMessage("Invalid request: Image name [noImage] does not exist in Google Container Registry.");

    assertThatThrownBy(() -> gcrService.getBuilds(gcpInternalConfig, "invalidProject", 100))
        .extracting(ex -> ((WingsException) ex).getParams().get("message"))
        .isEqualTo(
            "Failed to retrieve [invalidProject] from Google Container Registry. [UNKNOWN][Project 'project:project-name' not found or deleted.]");

    assertThatThrownBy(() -> gcrService.getBuilds(gcpInternalConfig, "teapot", 100))
        .extracting(ex -> ((WingsException) ex).getParams().get("message"))
        .isEqualTo("Failed to retrieve [teapot] from Google Container Registry. I'm a teapot");
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void testVerifyImageName() {
    assertThat(gcrService.verifyImageName(gcpInternalConfig, SOME_IMAGE)).isTrue();
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void testValidateCredentials() {
    assertThat(gcrService.validateCredentials(gcpInternalConfig, SOME_IMAGE)).isTrue();
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void shouldGetBuild() {
    when(dockerRegistryUtils.parseArtifactMetaInfoResponse(any(), any()))
        .thenReturn(ArtifactMetaInfo.builder().sha(SHA).labels(label).build());
    BuildDetailsInternal actual = gcrService.verifyBuildNumber(gcpInternalConfig, SOME_IMAGE, LATEST);
    assertThat(actual.getNumber()).isEqualTo(LATEST);
    ArtifactMetaInfo artifactMetaInfo = actual.getArtifactMetaInfo();
    assertThat(artifactMetaInfo.getSha()).isEqualTo(SHA);
    assertThat(artifactMetaInfo.getShaV2()).isEqualTo(SHA);
    assertThat(artifactMetaInfo.getLabels()).isEqualTo(label);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void shouldGetBuild_SHA() {
    when(dockerRegistryUtils.parseArtifactMetaInfoResponse(any(), any()))
        .thenReturn(ArtifactMetaInfo.builder().sha(SHA).labels(label).build());
    BuildDetailsInternal actual = gcrService.verifyBuildNumber(gcpInternalConfig, SOME_IMAGE, SHA);
    assertThat(actual.getNumber()).isEqualTo(SHA);
    ArtifactMetaInfo artifactMetaInfo = actual.getArtifactMetaInfo();
    assertThat(artifactMetaInfo.getSha()).isEqualTo(SHA);
    assertThat(artifactMetaInfo.getShaV2()).isEqualTo(SHA);
    assertThat(artifactMetaInfo.getLabels()).isEqualTo(label);
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void shouldRetryFetchToken() {
    final GcrRestClient restClient = Mockito.spy(gcrRestClient);
    when(gcrService.getGcrRestClient(gcpInternalConfig.getRegistryHostname())).thenReturn(restClient);
    doThrow(RuntimeException.class)
        .when(restClient)
        .getImageManifest(gcpInternalConfig.getBasicAuthHeader(), "realm-value", "tag");
    try {
      gcrService.fetchImage(gcpInternalConfig, "realm-value", "tag");
    } catch (Exception e) {
      verify(restClient, times(10)).getImageManifest(gcpInternalConfig.getBasicAuthHeader(), "realm-value", "tag");
    }
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void shouldRetryListTag() {
    final GcrRestClient restClient = Mockito.spy(gcrRestClient);
    when(gcrService.getGcrRestClient(gcpInternalConfig.getRegistryHostname())).thenReturn(restClient);
    doThrow(RuntimeException.class)
        .when(restClient)
        .listImageTags(gcpInternalConfig.getBasicAuthHeader(), "realm-value");
    try {
      gcrService.listImageTag(gcpInternalConfig, "realm-value");
    } catch (Exception e) {
      verify(restClient, times(10)).listImageTags(gcpInternalConfig.getBasicAuthHeader(), "realm-value");
    }
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void getArtifactMetaInfoTest() throws Exception {
    when(dockerRegistryUtils.parseArtifactMetaInfoResponse(any(), any()))
        .thenReturn(ArtifactMetaInfo.builder().sha(SHA).labels(label).build());
    ArtifactMetaInfo artifactMetaInfo = gcrService.getArtifactMetaInfo(gcpInternalConfig, SOME_IMAGE, LATEST);
    assertThat(artifactMetaInfo.getSha()).isEqualTo(SHA);
    assertThat(artifactMetaInfo.getLabels()).isEqualTo(label);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void getArtifactMetaInfoV2Test() throws Exception {
    when(dockerRegistryUtils.parseArtifactMetaInfoResponse(any(), any()))
        .thenReturn(ArtifactMetaInfo.builder().sha(SHA).labels(label).build());
    ArtifactMetaInfo artifactMetaInfo = gcrService.getArtifactMetaInfoV2(gcpInternalConfig, SOME_IMAGE, LATEST);
    assertThat(artifactMetaInfo.getSha()).isEqualTo(SHA);
    assertThat(artifactMetaInfo.getLabels()).isEqualTo(label);
  }
}
