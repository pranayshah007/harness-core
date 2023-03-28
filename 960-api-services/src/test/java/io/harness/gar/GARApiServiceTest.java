/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gar;

import static io.harness.rule.OwnerRule.ABHISHEK;
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
import io.harness.artifacts.gar.beans.GarInternalConfig;
import io.harness.artifacts.gar.service.GARApiServiceImpl;
import io.harness.beans.ArtifactMetaInfo;
import io.harness.category.element.UnitTests;
import io.harness.exception.WingsException;
import io.harness.rule.Owner;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import java.util.List;
import java.util.stream.Collectors;
import org.assertj.core.util.Lists;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class GARApiServiceTest extends CategoryTest {
  GARApiServiceImpl garApiServiceImpl = spy(new GARApiServiceImpl());

  @Rule
  public WireMockRule wireMockRule = new WireMockRule(
      WireMockConfiguration.wireMockConfig().usingFilesUnderDirectory("400-rest/src/test/resources").port(0));
  private String url;
  String basicAuthHeader = "auth";
  GarInternalConfig gcpInternalConfig;

  private static final String SHA = "sha1";

  @Before
  public void setUp() {
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
                "/v1/projects/cd-play/locations/us/repositories/vivek-repo/packages/mongo/tags"))
            .withHeader("Authorization", equalTo("bearerToken"))
            .willReturn(aResponse().withStatus(200).withBody("{\n"
                + "    \"tags\": [\n"
                + "        {\n"
                + "            \"name\": \"projects/cd-play/locations/us-south1/repositories/vivek-repo/packages/mongo/tags/latest\",\n"
                + "            \"version\": \"projects/cd-play/locations/us-south1/repositories/vivek-repo/packages/mongo/versions/sha256:38cd16441be083f00bf2c3e0e307292531b6d98eb77c09271cf43f2b58ce9f9e\"\n"
                + "        },\n"
                + "        {\n"
                + "            \"name\": \"projects/cd-play/locations/us-south1/repositories/vivek-repo/packages/mongo/tags/latest1\",\n"
                + "            \"version\": \"projects/cd-play/locations/us-south1/repositories/vivek-repo/packages/mongo/versions/sha256:38cd16441be083f00bf2c3e0e307292531b6d98eb77c09271cf43f2b58ce9f9e\"\n"
                + "        },\n"
                + "        {\n"
                + "            \"name\": \"projects/cd-play/locations/us-south1/repositories/vivek-repo/packages/mongo/tags/latest10\",\n"
                + "            \"version\": \"projects/cd-play/locations/us-south1/repositories/vivek-repo/packages/mongo/versions/sha256:38cd16441be083f00bf2c3e0e307292531b6d98eb77c09271cf43f2b58ce9f9e\"\n"
                + "        }\n"
                + "    ]\n"
                + "}")));
    wireMockRule.stubFor(WireMock
                             .get(WireMock.urlPathEqualTo(
                                 "/v1/projects/cd-play/locations/us/repositories/vivek-repo/packages/mongo/tags"))
                             .withHeader("Authorization", notMatching("bearerToken"))
                             .willReturn(aResponse().withStatus(401).withBody("Wrong bearer Token")));
    wireMockRule.stubFor(
        WireMock
            .get(WireMock.urlPathEqualTo(
                "/v1/projects/cd-play/locations/us/repositories/vivek-repo/packages/wrongpackage/tags"))
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
    wireMockRule.stubFor(
        WireMock
            .get(WireMock.urlPathEqualTo(
                "/v1/projects/cd-play1/locations/us/repositories/vivek-repo/packages/package/tags/package"))
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

    when(garApiServiceImpl.getUrl()).thenReturn("http://" + url);
  }
  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void getBuildsTest() {
    List<BuildDetailsInternal> actual = garApiServiceImpl.getBuilds(gcpInternalConfig, "", 100);
    assertThat(actual).hasSize(3);
    assertThat(actual.stream().map(BuildDetailsInternal::getNumber).collect(Collectors.toList()))
        .isEqualTo(Lists.newArrayList("latest10", "latest1", "latest"));

    actual = garApiServiceImpl.getBuilds(gcpInternalConfig, "latest1", 100);
    assertThat(actual).hasSize(2);
    assertThat(actual.stream().map(BuildDetailsInternal::getNumber).collect(Collectors.toList()))
        .isEqualTo(Lists.newArrayList("latest10", "latest1"));
  }

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void getLastSuccessfulBuildFromRegexTest() {
    BuildDetailsInternal actual = garApiServiceImpl.getLastSuccessfulBuildFromRegex(gcpInternalConfig, "latest");
    assertThat(actual.getNumber()).isEqualTo("latest10");
  }

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void verifyBuildNumberTest() {
    BuildDetailsInternal actual = garApiServiceImpl.verifyBuildNumber(gcpInternalConfig, "latest10");
    assertThat(actual.getNumber()).isEqualTo("latest10");
  }
  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void hintExecptionTest() {
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
    assertThatThrownBy(() -> garApiServiceImpl.verifyBuildNumber(modiifedInternalConfig1, "package"))
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
  public void getArtifactMetaInfoTest() {
    when(garApiServiceImpl.getGarRestClientDockerRegistryAPIUrl(any())).thenReturn("http://" + url);
    ArtifactMetaInfo artifactMetaInfo = garApiServiceImpl.getArtifactMetaInfo(gcpInternalConfig, "latest10");
    assertThat(artifactMetaInfo.getShaV2()).isEqualTo(SHA);
  }
}
