/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gar;

import static io.harness.rule.OwnerRule.ABHISHEK;
import static io.harness.rule.OwnerRule.RAKSHIT_AGARWAL;
import static io.harness.rule.OwnerRule.SARTHAK_KASAT;
import static io.harness.rule.OwnerRule.vivekveman;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.notMatching;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.artifacts.beans.BuildDetailsInternal;
import io.harness.artifacts.docker.service.DockerRegistryUtils;
import io.harness.artifacts.gar.beans.GarInternalConfig;
import io.harness.artifacts.gar.service.GARApiServiceImpl;
import io.harness.beans.ArtifactMetaInfo;
import io.harness.category.element.UnitTests;
import io.harness.exception.WingsException;
import io.harness.rule.Owner;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.assertj.core.util.Lists;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class GARApiServiceTest extends CategoryTest {
  @InjectMocks GARApiServiceImpl garApiService;
  @Mock DockerRegistryUtils dockerRegistryUtils;
  GARApiServiceImpl garApiServiceImpl;

  @Rule
  public WireMockRule wireMockRule = new WireMockRule(
      WireMockConfiguration.wireMockConfig().usingFilesUnderDirectory("400-rest/src/test/resources").port(0));

  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  private String url;
  String basicAuthHeader = "auth";
  GarInternalConfig gcpInternalConfig;

  private static final String SHA = "sha1:2323242";
  private static final Map<String, String> label = Map.of("a", "b", "c", "d");
  private static final String IMAGE = "image";
  private static final String LATEST_10 = "latest10";

  @Before
  public void setUp() {
    garApiServiceImpl = spy(garApiService);
    url = "localhost:" + wireMockRule.port();
    gcpInternalConfig = GarInternalConfig.builder()
                            .region("us")
                            .project("cd-play")
                            .pkg("mongo")
                            .bearerToken("bearerToken")
                            .repositoryName("vivek-repo")
                            .maxBuilds(10000)
                            .build();
    wireMockRule.stubFor(
        WireMock
            .get(WireMock.urlPathEqualTo(
                "/v1/projects/cd-play/locations/us/repositories/vivek-repo/packages/mongo/versions"))
            .withHeader("Authorization", equalTo("bearerToken"))
            .willReturn(aResponse().withStatus(200).withBody("{\n"
                + "  \"versions\": [\n"
                + "    {\n"
                + "      \"name\": \"projects/cd-play/locations/us-south1/repositories/vivek-repo/packages/mongo/versions/sha256:33908b2777fab67fb40ba604c2796cf6b83e88129a10e345466cb584049c33b0\",\n"
                + "      \"createTime\": \"2022-07-26T10:01:04.563871Z\",\n"
                + "      \"updateTime\": \"2022-10-18T05:35:23.602863Z\",\n"
                + "      \"relatedTags\": [\n"
                + "        {\n"
                + "          \"name\": \"projects/cd-play/locations/us-south1/repositories/vivek-repo/packages/mongo/tags/hello\",\n"
                + "          \"version\": \"projects/cd-play/locations/us-south1/repositories/vivek-repo/packages/mongo/versions/sha256:33908b2777fab67fb40ba604c2796cf6b83e88129a10e345466cb584049c33b0\"\n"
                + "        },\n"
                + "        {\n"
                + "          \"name\": \"projects/cd-play/locations/us-south1/repositories/vivek-repo/packages/mongo/tags/hello1\",\n"
                + "          \"version\": \"projects/cd-play/locations/us-south1/repositories/vivek-repo/packages/mongo/versions/sha256:33908b2777fab67fb40ba604c2796cf6b83e88129a10e345466cb584049c33b0\"\n"
                + "        }\n"
                + "      ],\n"
                + "      \"metadata\": {\n"
                + "        \"name\": \"projects/cd-play/locations/us-south1/repositories/vivek-repo/dockerImages/mongo@sha256:33908b2777fab67fb40ba604c2796cf6b83e88129a10e345466cb584049c33b0\",\n"
                + "        \"imageSizeBytes\": \"55365574\",\n"
                + "        \"mediaType\": \"application/vnd.docker.distribution.manifest.v2+json\",\n"
                + "        \"buildTime\": \"2022-07-19T18:40:47.278146013Z\"\n"
                + "      }\n"
                + "    },\n"
                + "    {\n"
                + "      \"name\": \"projects/cd-play/locations/us-south1/repositories/vivek-repo/packages/mongo/versions/sha256:38cd16441be083f00bf2c3e0e307292531b6d98eb77c09271cf43f2b58ce9f9e\",\n"
                + "      \"createTime\": \"2022-07-26T10:07:10.596991Z\",\n"
                + "      \"updateTime\": \"2022-09-01T11:01:04.862703Z\",\n"
                + "      \"relatedTags\": [\n"
                + "        {\n"
                + "          \"name\": \"projects/cd-play/locations/us-south1/repositories/vivek-repo/packages/mongo/tags/latest\",\n"
                + "          \"version\": \"projects/cd-play/locations/us-south1/repositories/vivek-repo/packages/mongo/versions/sha256:38cd16441be083f00bf2c3e0e307292531b6d98eb77c09271cf43f2b58ce9f9e\"\n"
                + "        },\n"
                + "        {\n"
                + "          \"name\": \"projects/cd-play/locations/us-south1/repositories/vivek-repo/packages/mongo/tags/latest1\",\n"
                + "          \"version\": \"projects/cd-play/locations/us-south1/repositories/vivek-repo/packages/mongo/versions/sha256:38cd16441be083f00bf2c3e0e307292531b6d98eb77c09271cf43f2b58ce9f9e\"\n"
                + "        },\n"
                + "        {\n"
                + "          \"name\": \"projects/cd-play/locations/us-south1/repositories/vivek-repo/packages/mongo/tags/latest10\",\n"
                + "          \"version\": \"projects/cd-play/locations/us-south1/repositories/vivek-repo/packages/mongo/versions/sha256:38cd16441be083f00bf2c3e0e307292531b6d98eb77c09271cf43f2b58ce9f9e\"\n"
                + "        }\n"
                + "      ],\n"
                + "      \"metadata\": {\n"
                + "        \"buildTime\": \"2022-06-22T02:10:54.278228662Z\",\n"
                + "        \"name\": \"projects/cd-play/locations/us-south1/repositories/vivek-repo/dockerImages/mongo@sha256:38cd16441be083f00bf2c3e0e307292531b6d98eb77c09271cf43f2b58ce9f9e\",\n"
                + "        \"imageSizeBytes\": \"155649666\",\n"
                + "        \"mediaType\": \"application/vnd.docker.distribution.manifest.v2+json\"\n"
                + "      }\n"
                + "    }\n"
                + "  ]\n"
                + "}\n")));
    wireMockRule.stubFor(WireMock
                             .get(WireMock.urlPathEqualTo(
                                 "/v1/projects/cd-play/locations/us/repositories/vivek-repo/packages/mongo/versions"))
                             .withHeader("Authorization", notMatching("bearerToken"))
                             .willReturn(aResponse().withStatus(401).withBody("Wrong bearer Token")));
    wireMockRule.stubFor(
        WireMock
            .get(WireMock.urlPathEqualTo(
                "/v1/projects/cd-play/locations/us/repositories/vivek-repo/packages/wrongpackage/versions"))
            .withHeader("Authorization", equalTo("bearerToken"))
            .willReturn(aResponse().withStatus(404).withBody("Test Body")));
    wireMockRule.stubFor(
        WireMock
            .get(WireMock.urlPathEqualTo(
                "/v1/projects/cd-play/locations/us/repositories/vivek-repo/packages/mongo/tags/latest10"))
            .withHeader("Authorization", equalTo("bearerToken"))
            .willReturn(aResponse().withStatus(200).withBody("{\n"
                + "    \"name\": \"projects/cd-play/locations/us-south1/repositories/vivek-repo/packages/mongo/tags/latest10\",\n"
                + "    \"version\": \"projects/cd-play/locations/us-south1/repositories/vivek-repo/packages/mongo/versions/sha256:38cd16441be083f00bf2c3e0e307292531b6d98eb77c09271cf43f2b58ce9f9e\"\n"
                + "}")));
    wireMockRule.stubFor(WireMock.get(WireMock.urlPathEqualTo("/v2/cd-play1/vivek-repo/package/manifests/latest10"))
                             .withHeader("Authorization", equalTo("bearerToken"))
                             .willReturn(aResponse().withStatus(403).withBody("Response 403")));
    wireMockRule.stubFor(
        WireMock.get(WireMock.urlPathEqualTo("/v2/cd-play/vivek-repo/mongo/manifests/latest10"))
            .withHeader("Authorization", equalTo("bearerToken"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Docker-Content-Digest", SHA)
                    .withBody("{\n"
                        + "    \"name\": \"projects/cd-play/locations/us-south1/repositories/vivek-repo/packages/mongo/tags/latest10\",\n"
                        + "    \"version\": \"projects/cd-play/locations/us-south1/repositories/vivek-repo/packages/mongo/versions/sha256:38cd16441be083f00bf2c3e0e307292531b6d98eb77c09271cf43f2b58ce9f9e\"\n"
                        + "}")));
    wireMockRule.stubFor(
        WireMock.get(WireMock.urlPathEqualTo("/v2/cd-play/vivek-repo/mongo/manifests/hello1"))
            .withHeader("Authorization", equalTo("bearerToken"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Docker-Content-Digest", SHA)
                    .withBody("{\n"
                        + "    \"name\": \"projects/cd-play/locations/us-south1/repositories/vivek-repo/packages/mongo/tags/hello1\",\n"
                        + "    \"version\": \"projects/cd-play/locations/us-south1/repositories/vivek-repo/packages/mongo/versions/sha256:38cd16441be083f00bf2c3e0e307292531b6d98eb77c09271cf43f2b58ce9f9e\"\n"
                        + "}")));
    wireMockRule.stubFor(
        WireMock
            .get(WireMock.urlPathEqualTo("/v1/projects/cd-play/locations/us-south1/repositories/vivek-repo/packages"))
            .withHeader("Authorization", equalTo("bearerToken"))
            .willReturn(aResponse().withStatus(200).withBody(
                "{\"packages\":[{\"name\":\"projects/cd-play/locations/us-south1/repositories/vivek-repo/packages/hellowithlabels\",\"createTime\":\"2022-09-14T03:10:05.836970Z\",\"updateTime\":\"2022-09-14T03:10:05.836970Z\"},{\"name\":\"projects/cd-play/locations/us-south1/repositories/vivek-repo/packages/mongo\",\"createTime\":\"2022-08-17T09:49:15.509927Z\",\"updateTime\":\"2022-09-14T03:49:07.951039Z\"}]}\n")));
    wireMockRule.stubFor(
        WireMock
            .get(WireMock.urlPathEqualTo("/v1/projects/cd-play/locations/us-south1/repositories/vivek-repo/packages"))
            .withHeader("Authorization", notMatching("bearerToken"))
            .willReturn(aResponse().withStatus(401).withBody("Wrong bearer Token")));
    wireMockRule.stubFor(WireMock
                             .get(WireMock.urlPathEqualTo(
                                 "/v1/projects/cd-play/locations/wrong-region/repositories/wrong-repo/packages"))
                             .withHeader("Authorization", equalTo("bearerToken"))
                             .willReturn(aResponse().withStatus(404).withBody("Test Body")));
    wireMockRule.stubFor(
        WireMock.get(WireMock.urlPathEqualTo("/v2/cd-play/vivek-repo/mongo/manifests/sha1:2323242"))
            .withHeader("Authorization", equalTo("bearerToken"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Docker-Content-Digest", SHA)
                    .withBody("{\n"
                        + "    \"name\": \"projects/cd-play/locations/us-south1/repositories/vivek-repo/packages/mongo/tags/latest10\",\n"
                        + "    \"version\": \"projects/cd-play/locations/us-south1/repositories/vivek-repo/packages/mongo/versions/sha256:38cd16441be083f00bf2c3e0e307292531b6d98eb77c09271cf43f2b58ce9f9e\"\n"
                        + "}")));

    when(garApiServiceImpl.getUrl()).thenReturn("http://" + url);
  }
  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void getBuildsTest() {
    List<BuildDetailsInternal> actual = garApiServiceImpl.getBuilds(gcpInternalConfig, "", 100);
    assertThat(actual).hasSize(5);
    assertThat(actual.stream().map(BuildDetailsInternal::getNumber).collect(Collectors.toList()))
        .isEqualTo(Lists.newArrayList("hello1", "hello", LATEST_10, "latest1", "latest"));

    actual = garApiServiceImpl.getBuilds(gcpInternalConfig, "latest1", 100);
    assertThat(actual).hasSize(2);
    assertThat(actual.stream().map(BuildDetailsInternal::getNumber).collect(Collectors.toList()))
        .isEqualTo(Lists.newArrayList(LATEST_10, "latest1"));
  }

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void getLastSuccessfulBuildFromRegexTest() {
    when(garApiServiceImpl.getGarRestClientDockerRegistryAPIUrl(any())).thenReturn("http://" + url);
    when(dockerRegistryUtils.parseArtifactMetaInfoResponse(any(), any()))
        .thenReturn(ArtifactMetaInfo.builder().sha(SHA).labels(label).build());
    BuildDetailsInternal actual = garApiServiceImpl.getLastSuccessfulBuildFromRegex(gcpInternalConfig, "latest");
    assertThat(actual.getNumber()).isEqualTo(LATEST_10);
  }

  @Test
  @Owner(developers = SARTHAK_KASAT)
  @Category(UnitTests.class)
  public void getLastSuccessfulBuild() {
    when(garApiServiceImpl.getGarRestClientDockerRegistryAPIUrl(any())).thenReturn("http://" + url);
    when(dockerRegistryUtils.parseArtifactMetaInfoResponse(any(), any()))
        .thenReturn(ArtifactMetaInfo.builder().sha(SHA).labels(label).build());
    BuildDetailsInternal actual = garApiServiceImpl.getLastSuccessfulBuildFromRegex(gcpInternalConfig, ".*?");
    assertThat(actual.getNumber()).isEqualTo("hello1");
  }

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void verifyBuildNumberTest() {
    when(garApiServiceImpl.getGarRestClientDockerRegistryAPIUrl(any())).thenReturn("http://" + url);
    when(dockerRegistryUtils.parseArtifactMetaInfoResponse(any(), any()))
        .thenReturn(ArtifactMetaInfo.builder().sha(SHA).labels(label).build());
    BuildDetailsInternal actual = garApiServiceImpl.verifyBuildNumber(gcpInternalConfig, LATEST_10);
    assertThat(actual.getNumber()).isEqualTo(LATEST_10);
    assertThat(actual.getMetadata().get(IMAGE)).isEqualTo("us-docker.pkg.dev/cd-play/vivek-repo/mongo:latest10");
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void verifyBuildNumberTest_SHA() {
    when(garApiServiceImpl.getGarRestClientDockerRegistryAPIUrl(any())).thenReturn("http://" + url);
    when(dockerRegistryUtils.parseArtifactMetaInfoResponse(any(), any()))
        .thenReturn(ArtifactMetaInfo.builder().sha(SHA).labels(label).build());
    BuildDetailsInternal actual = garApiServiceImpl.verifyBuildNumber(gcpInternalConfig, SHA);
    assertThat(actual.getNumber()).isEqualTo(SHA);
    assertThat(actual.getMetadata().get(IMAGE)).isEqualTo("us-docker.pkg.dev/cd-play/vivek-repo/mongo@sha1:2323242");
  }

  @Test
  @Owner(developers = RAKSHIT_AGARWAL)
  @Category(UnitTests.class)
  public void getPackagesTest() {
    List<BuildDetailsInternal> actual = garApiServiceImpl.getPackages(gcpInternalConfig, "us-south1", "vivek-repo");
    assertThat(actual).hasSize(2);

    assertThat(actual.stream().map(BuildDetailsInternal::getUiDisplayName).collect(Collectors.toList()))
        .isEqualTo(
            Lists.newArrayList("projects/cd-play/locations/us-south1/repositories/vivek-repo/packages/hellowithlabels",
                "projects/cd-play/locations/us-south1/repositories/vivek-repo/packages/mongo"));
  }

  @Test
  @Owner(developers = RAKSHIT_AGARWAL)
  @Category(UnitTests.class)
  public void getPackagesTest_HintException() {
    GarInternalConfig modifiedInternalConfig =
        GarInternalConfig.builder().project("cd-play").bearerToken("wrongbearerToken").maxBuilds(10000).build();
    assertThatThrownBy(() -> garApiServiceImpl.getPackages(modifiedInternalConfig, "us-south1", "vivek-repo"))
        .extracting(ex -> ((WingsException) ex).getParams().get("message"))
        .isEqualTo(
            "The connector provided does not have sufficient privileges to access Google artifact registry"); // 401
    modifiedInternalConfig.setBearerToken("bearerToken");
    assertThatThrownBy(() -> garApiServiceImpl.getPackages(modifiedInternalConfig, "wrong-region", "wrong-repo"))
        .extracting(ex -> ((WingsException) ex).getParams().get("message"))
        .isEqualTo("Please provide valid values for region, project and repository fields."); // 404
  }

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void hintExecptionTest() {
    when(garApiServiceImpl.getGarRestClientDockerRegistryAPIUrl(any())).thenReturn("http://" + url);
    GarInternalConfig modiifedInternalConfig = GarInternalConfig.builder()
                                                   .region("us")
                                                   .project("cd-play")
                                                   .pkg("mongo")
                                                   .bearerToken("wrongbearerToken")
                                                   .repositoryName("vivek-repo")
                                                   .maxBuilds(10000)
                                                   .build();
    assertThatThrownBy(() -> garApiServiceImpl.getBuilds(modiifedInternalConfig, "", 100))
        .extracting(ex -> ((WingsException) ex).getParams().get("message"))
        .isEqualTo(
            "The connector provided does not have sufficient privileges to access Google artifact registry"); // 401
    modiifedInternalConfig.setBearerToken("bearerToken");
    modiifedInternalConfig.setPkg("wrongpackage");
    assertThatThrownBy(() -> garApiServiceImpl.getBuilds(modiifedInternalConfig, "", 100))
        .extracting(ex -> ((WingsException) ex).getParams().get("message"))
        .isEqualTo("Please provide valid values for region, project, repository, package and version fields."); // 404
    GarInternalConfig modiifedInternalConfig1 = GarInternalConfig.builder()
                                                    .region("us")
                                                    .project("cd-play1")
                                                    .pkg("package")
                                                    .bearerToken("bearerToken")
                                                    .repositoryName("vivek-repo")
                                                    .maxBuilds(10000)
                                                    .build();
    assertThatThrownBy(() -> garApiServiceImpl.verifyBuildNumber(modiifedInternalConfig1, LATEST_10))
        .extracting(ex -> ((WingsException) ex).getParams().get("message"))
        .isEqualTo("Connector provided does not have access to project. Please check the project field."); // 403
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void getGarRestClientDockerRegistryAPIUrlTest() {
    String url = garApiServiceImpl.getGarRestClientDockerRegistryAPIUrl("region");
    assertThat(url).isEqualTo("https://region-docker.pkg.dev");
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void getArtifactMetaInfoTest() throws IOException {
    when(garApiServiceImpl.getGarRestClientDockerRegistryAPIUrl(any())).thenReturn("http://" + url);
    when(dockerRegistryUtils.parseArtifactMetaInfoResponse(any(), any()))
        .thenReturn(ArtifactMetaInfo.builder().sha(SHA).labels(label).build());
    ArtifactMetaInfo artifactMetaInfo = garApiServiceImpl.getArtifactMetaInfoV1(gcpInternalConfig, LATEST_10);
    assertThat(artifactMetaInfo.getSha()).isEqualTo(SHA);
    assertThat(artifactMetaInfo.getLabels()).isEqualTo(label);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void getArtifactMetaInfoTestV2() throws IOException {
    when(garApiServiceImpl.getGarRestClientDockerRegistryAPIUrl(any())).thenReturn("http://" + url);
    when(dockerRegistryUtils.parseArtifactMetaInfoResponse(any(), any()))
        .thenReturn(ArtifactMetaInfo.builder().sha(SHA).labels(label).build());
    ArtifactMetaInfo artifactMetaInfo = garApiServiceImpl.getArtifactMetaInfoV2(gcpInternalConfig, LATEST_10);
    assertThat(artifactMetaInfo.getSha()).isEqualTo(SHA);
    assertThat(artifactMetaInfo.getLabels()).isEqualTo(label);
  }
}
