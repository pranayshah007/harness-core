/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.artifacts.docker.service;

import static io.harness.delegate.beans.connector.docker.DockerRegistryProviderType.DOCKER_HUB;
import static io.harness.delegate.beans.connector.docker.DockerRegistryProviderType.HARBOR;
import static io.harness.delegate.beans.connector.docker.DockerRegistryProviderType.OTHER;
import static io.harness.exception.ExceptionUtils.getMessage;
import static io.harness.logging.LoggingInitializer.initializeLogging;
import static io.harness.rule.OwnerRule.ANSHUL;
import static io.harness.rule.OwnerRule.ARCHIT;
import static io.harness.rule.OwnerRule.DEEPAK_PUTHRAYA;
import static io.harness.rule.OwnerRule.FERNANDOD;
import static io.harness.rule.OwnerRule.ROHITKARELIA;
import static io.harness.rule.OwnerRule.SHIVAM;
import static io.harness.rule.OwnerRule.SRINIVAS;
import static io.harness.rule.OwnerRule.VINICIUS;
import static io.harness.rule.OwnerRule.vivekveman;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForClassTypes.fail;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.artifacts.beans.BuildDetailsInternal;
import io.harness.artifacts.docker.DockerImageTagResponse;
import io.harness.artifacts.docker.DockerRegistryRestClient;
import io.harness.artifacts.docker.DockerRegistryToken;
import io.harness.artifacts.docker.beans.DockerInternalConfig;
import io.harness.artifacts.docker.client.DockerRestClientFactory;
import io.harness.artifacts.docker.client.DockerRestClientFactoryImpl;
import io.harness.beans.ArtifactMetaInfo;
import io.harness.category.element.UnitTests;
import io.harness.context.GlobalContext;
import io.harness.eraro.ErrorCode;
import io.harness.exception.ExplanationException;
import io.harness.exception.HintException;
import io.harness.exception.InvalidArtifactServerException;
import io.harness.exception.runtime.DockerHubServerRuntimeException;
import io.harness.globalcontex.ErrorHandlingGlobalContextData;
import io.harness.manage.GlobalContextManager;
import io.harness.network.Http;
import io.harness.rule.Owner;
import io.harness.serializer.JsonUtils;

import com.github.tomakehurst.wiremock.core.Options;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
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
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@OwnedBy(HarnessTeam.PIPELINE)
public class DockerRegistryServiceImplTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  @Rule
  public WireMockRule wireMockRule =
      new WireMockRule(WireMockConfiguration.wireMockConfig()
                           .usingFilesUnderDirectory("960-api-services/src/test/resources")
                           .port(Options.DYNAMIC_PORT),
          false);
  @Mock private DockerRestClientFactory dockerRestClientFactory;
  @Mock private DockerRegistryUtils dockerRegistryUtils;
  @InjectMocks DockerRegistryServiceImpl dockerRegistryService;
  @Mock private DockerPublicRegistryProcessor dockerPublicRegistryProcessor;

  private static String url;
  private static DockerInternalConfig dockerConfig;
  private static DockerInternalConfig harborConfig;
  private static DockerRegistryToken dockerRegistryToken;
  private static DockerRegistryToken acrDockerRegistryToken;
  private static DockerRegistryRestClient dockerRegistryRestClient;
  private static DockerImageTagResponse dockerImageTagResponse;
  private static DockerImageTagResponse dockerImageTagResponsePaginated;

  @Before
  public void before() {
    dockerRegistryToken = new DockerRegistryToken();
    dockerRegistryToken.setToken("dockerRegistryToken");
    acrDockerRegistryToken = new DockerRegistryToken();
    acrDockerRegistryToken.setAccess_token("dockerRegistryToken");

    url = "http://localhost:" + wireMockRule.port() + "/";
    dockerConfig =
        DockerInternalConfig.builder().dockerRegistryUrl(url).username("username").password("password").build();
    harborConfig = DockerInternalConfig.builder()
                       .dockerRegistryUrl(url)
                       .username("username")
                       .password("password")
                       .providerType(HARBOR)
                       .build();
    dockerRegistryRestClient = new DockerRestClientFactoryImpl().getDockerRegistryRestClient(dockerConfig);

    dockerImageTagResponse = new DockerImageTagResponse();
    dockerImageTagResponse.setName("DOCKER_IMAGE");
    List<String> tags = Arrays.asList("tag1", "tag2");
    dockerImageTagResponse.setTags(tags);

    dockerImageTagResponsePaginated = new DockerImageTagResponse();
    dockerImageTagResponsePaginated.setName("Docker_Image_Paginated");
    tags = Arrays.asList("tag3", "tag4");
    dockerImageTagResponsePaginated.setTags(tags);

    initializeLogging();
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testValidateCredentialForIOException() {
    wireMockRule.stubFor(get(urlEqualTo("/v2/"))
                             .willReturn(aResponse().withStatus(401).withHeader("Www-Authenticate",
                                 "Bearer realm=\"https://localhost:" + wireMockRule.port()
                                     + "/service/token\",service=\"harbor-registry\"")));
    doReturn(dockerRegistryRestClient).when(dockerRestClientFactory).getDockerRegistryRestClient(dockerConfig);
    assertThatThrownBy(() -> dockerRegistryService.validateCredentials(dockerConfig))
        .isInstanceOf(HintException.class)
        .getCause()
        .isInstanceOf(ExplanationException.class)
        .getCause()
        .isInstanceOf(InvalidArtifactServerException.class);
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void testValidateCredentialForIOException2() {
    doReturn(dockerRegistryRestClient).when(dockerRestClientFactory).getDockerRegistryRestClient(dockerConfig);
    wireMockRule.stubFor(get(urlEqualTo("/v2"))
                             .willReturn(aResponse().withStatus(401).withHeader("Www-Authenticate",
                                 "Bearer realm=\"http://localhost:" + wireMockRule.port()
                                     + "/service/token\",service=\"harbor-registry\",scope=\"somevalue\"")));
    wireMockRule.stubFor(get(urlEqualTo("/service/token?service=harbor-registry&scope=somevalue"))
                             .willReturn(aResponse().withBody(JsonUtils.asJson(dockerRegistryToken))));
    assertThatThrownBy(() -> dockerRegistryService.validateCredentials(dockerConfig))
        .isInstanceOf(HintException.class)
        .getCause()
        .isInstanceOf(ExplanationException.class)
        .getCause()
        .isInstanceOf(InvalidArtifactServerException.class);
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void testValidateCredentialForIOExceptionForGetAPIEndingWithForwardSlash() {
    doReturn(dockerRegistryRestClient).when(dockerRestClientFactory).getDockerRegistryRestClient(dockerConfig);
    wireMockRule.stubFor(get(urlEqualTo("/v2/")).willReturn(aResponse().withFault(Fault.EMPTY_RESPONSE)));
    assertThatThrownBy(() -> dockerRegistryService.validateCredentials(dockerConfig))
        .isInstanceOf(HintException.class)
        .getCause()
        .isInstanceOf(ExplanationException.class)
        .getCause()
        .isInstanceOf(InvalidArtifactServerException.class);
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void testValidateCredentialIOExceptionForAllgetapiVersionCalls() {
    doReturn(dockerRegistryRestClient).when(dockerRestClientFactory).getDockerRegistryRestClient(dockerConfig);
    wireMockRule.stubFor(get(urlEqualTo("/v2")).willReturn(aResponse().withFault(Fault.MALFORMED_RESPONSE_CHUNK)));
    wireMockRule.stubFor(get(urlEqualTo("/v2/"))
                             .willReturn(aResponse().withStatus(401).withHeader("Www-Authenticate",
                                 "Bearer realm=\"http://localhost:" + wireMockRule.port()
                                     + "/service/token\",service=\"harbor-registry\",scope=\"somevalue\"")));

    // http://localhost:9883/service/token?service=harbor-registry&scope=somevalue
    wireMockRule.stubFor(get(urlEqualTo("/service/token?service=harbor-registry&scope=somevalue"))
                             .willReturn(aResponse().withBody(JsonUtils.asJson(dockerRegistryToken))));
    assertThatThrownBy(() -> dockerRegistryService.validateCredentials(dockerConfig))
        .isInstanceOf(HintException.class)
        .getCause()
        .isInstanceOf(ExplanationException.class)
        .getCause()
        .isInstanceOf(InvalidArtifactServerException.class);
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void testValidateCredentialNotFoundResponse() {
    doReturn(dockerRegistryRestClient).when(dockerRestClientFactory).getDockerRegistryRestClient(dockerConfig);
    wireMockRule.stubFor(get(urlEqualTo("/v2")).willReturn(aResponse().withStatus(404)));
    wireMockRule.stubFor(get(urlEqualTo("/v2/"))
                             .willReturn(aResponse().withStatus(401).withHeader("Www-Authenticate",
                                 "Bearer realm=\"http://localhost:" + wireMockRule.port()
                                     + "/service/token\",service=\"harbor-registry\",scope=\"somevalue\"")));

    // http://localhost:9883/service/token?service=harbor-registry&scope=somevalue
    wireMockRule.stubFor(get(urlEqualTo("/service/token?service=harbor-registry&scope=somevalue"))
                             .willReturn(aResponse().withBody(JsonUtils.asJson(dockerRegistryToken))));
    assertThatThrownBy(() -> dockerRegistryService.validateCredentials(dockerConfig))
        .isInstanceOf(HintException.class)
        .getCause()
        .isInstanceOf(ExplanationException.class)
        .getCause()
        .isInstanceOf(InvalidArtifactServerException.class)
        .extracting("params.message")
        .isEqualTo("Invalid Docker Registry credentials");
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void testValidateInvalidConnectorURL() {
    doReturn(dockerRegistryRestClient).when(dockerRestClientFactory).getDockerRegistryRestClient(dockerConfig);
    wireMockRule.stubFor(get(urlEqualTo("/v2")).willReturn(aResponse().withStatus(404)));
    wireMockRule.stubFor(get(urlEqualTo("/v2/")).willReturn(aResponse().withStatus(404)));

    assertThatThrownBy(() -> dockerRegistryService.validateCredentials(dockerConfig))
        .isInstanceOf(HintException.class)
        .getCause()
        .isInstanceOf(ExplanationException.class)
        .getCause()
        .isInstanceOf(InvalidArtifactServerException.class)
        .extracting("params.message")
        .isEqualTo("Invalid Docker Registry URL");
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testValidateCredentialForMissingPassword() {
    try {
      DockerInternalConfig dockerInternalConfig =
          DockerInternalConfig.builder().dockerRegistryUrl(url).username("username").build();
      doReturn(dockerRegistryRestClient)
          .when(dockerRestClientFactory)
          .getDockerRegistryRestClient(dockerInternalConfig);
      dockerRegistryService.validateCredentials(dockerInternalConfig);
      fail("Should not reach here");
    } catch (Exception exception) {
      assertThat(getMessage(exception))
          .isEqualTo(
              "Invalid Docker Credentials. Password field value cannot be empty if username field is not empty. Password is a required field along with Username");
    }
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testValidateCredentialForDockerAcr() {
    doReturn(dockerRegistryRestClient).when(dockerRestClientFactory).getDockerRegistryRestClient(dockerConfig);
    doReturn(true).when(dockerRegistryUtils).isAcrContainerRegistry(any());
    wireMockRule.stubFor(get(urlEqualTo("/v2"))
                             .willReturn(aResponse().withStatus(401).withHeader("Www-Authenticate",
                                 "Bearer realm=\"http://localhost:" + wireMockRule.port()
                                     + "/oauth2/token\",service=\"test.azurecr.io\",scope=\"somevalue\"")));
    wireMockRule.stubFor(
        get(urlEqualTo("/v2"))
            .withHeader("Authorization", equalTo("Bearer dockerRegistryToken"))
            .willReturn(aResponse().withStatus(200).withBody(JsonUtils.asJson(new HashMap<String, String>()))));
    wireMockRule.stubFor(
        get(urlEqualTo("/oauth2/token?service=test.azurecr.io&scope=somevalue"))
            .willReturn(aResponse().withStatus(200).withBody(JsonUtils.asJson(acrDockerRegistryToken))));
    assertThat(dockerRegistryService.validateCredentials(dockerConfig)).isTrue();
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testGetBuildDetailsWithException() {
    try {
      doReturn(dockerRegistryRestClient).when(dockerRestClientFactory).getDockerRegistryRestClient(dockerConfig);
      dockerRegistryService.getBuilds(dockerConfig, "image", 10, null);
      fail("Should not reach here");
    } catch (Exception ex) {
      assertThat(getMessage(ex))
          .isEqualTo(
              "Could not fetch tags for the image. Check if the image exists and if the permissions are scoped for the authenticated user. Unable to fetch the tags for the image. Check if the image exists and if the permissions are scoped for the authenticated user. Bad Request. Unable to fetch the tags for the image. Bad Request");
    }
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testGetBuildDetailsInvalidCredentialsException() {
    try {
      doReturn(dockerRegistryRestClient).when(dockerRestClientFactory).getDockerRegistryRestClient(dockerConfig);

      wireMockRule.stubFor(get(urlEqualTo("/v2/image/tags/list"))
                               .willReturn(aResponse().withStatus(401).withHeader("Www-Authenticate",
                                   "Bearer realm=\"http://localhost:" + wireMockRule.port()
                                       + "/service/token\",service=\"harbor-registry\",scope=\"somevalue\"")));

      // http://localhost:9883/service/token?service=harbor-registry&scope=somevalue
      wireMockRule.stubFor(get(urlEqualTo("/service/token?service=harbor-registry&scope=somevalue"))
                               .willReturn(aResponse().withBody(JsonUtils.asJson(dockerRegistryToken))));

      if (!GlobalContextManager.isAvailable()) {
        GlobalContextManager.set(new GlobalContext());
      }
      GlobalContextManager.upsertGlobalContextRecord(
          ErrorHandlingGlobalContextData.builder().isSupportedErrorFramework(true).build());
      dockerRegistryService.getBuilds(dockerConfig, "image", 10, null);
      fail("Should not reach here");
    } catch (Exception ex) {
      GlobalContextManager.unset();
      assertThat(ex).isInstanceOf(DockerHubServerRuntimeException.class);
      assertThat(((DockerHubServerRuntimeException) ex).getCode()).isEqualTo(ErrorCode.INVALID_CREDENTIAL);
    }
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testGetBuildDetails() {
    doReturn(dockerRegistryRestClient).when(dockerRestClientFactory).getDockerRegistryRestClient(dockerConfig);

    wireMockRule.stubFor(
        get(urlEqualTo("/v2/image/tags/list"))
            .willReturn(aResponse().withStatus(200).withBody(JsonUtils.asJson(dockerImageTagResponse))));
    List<BuildDetailsInternal> builds = dockerRegistryService.getBuilds(dockerConfig, "image", 10, null);
    assertThat(builds).isNotNull();
    assertThat(builds.size()).isEqualTo(2);
    assertThat(builds.get(0).getNumber()).isEqualTo("tag1");
    assertThat(builds.get(0).getBuildUrl()).isEqualTo(url + "image/tags/tag1");
    assertThat(builds.get(0).getMetadata().get("tag")).isEqualTo("tag1");
    assertThat(builds.get(0).getMetadata().get("image")).isEqualTo(Http.getDomainWithPort(url) + "/image:tag1");
    assertThat(builds.get(1).getNumber()).isEqualTo("tag2");
    assertThat(builds.get(1).getBuildUrl()).isEqualTo(url + "image/tags/tag2");
    assertThat(builds.get(1).getMetadata().get("tag")).isEqualTo("tag2");
    assertThat(builds.get(1).getMetadata().get("image")).isEqualTo(Http.getDomainWithPort(url) + "/image:tag2");
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testGetBuildDetailsForAcr() {
    doReturn(dockerRegistryRestClient).when(dockerRestClientFactory).getDockerRegistryRestClient(dockerConfig);
    doReturn(true).when(dockerRegistryUtils).isAcrContainerRegistry(any());
    wireMockRule.stubFor(get(urlEqualTo("/v2/image/tags/list"))
                             .willReturn(aResponse().withStatus(401).withHeader("Www-Authenticate",
                                 "Bearer realm=\"http://localhost:" + wireMockRule.port()
                                     + "/oauth2/token\",service=\"test.azurecr.io\",scope=\"somevalue\"")));
    wireMockRule.stubFor(
        get(urlEqualTo("/v2/image/tags/list"))
            .withHeader("Authorization", equalTo("Bearer dockerRegistryToken"))
            .willReturn(aResponse().withStatus(200).withBody(JsonUtils.asJson(dockerImageTagResponse))));
    wireMockRule.stubFor(
        get(urlEqualTo("/oauth2/token?service=test.azurecr.io&scope=somevalue"))
            .willReturn(aResponse().withStatus(200).withBody(JsonUtils.asJson(acrDockerRegistryToken))));
    List<BuildDetailsInternal> builds = dockerRegistryService.getBuilds(dockerConfig, "image", 10, null);
    assertThat(builds).isNotNull();
    assertThat(builds.size()).isEqualTo(2);
    assertThat(builds.get(0).getNumber()).isEqualTo("tag1");
    assertThat(builds.get(0).getBuildUrl()).isEqualTo(url + "image/tags/tag1");
    assertThat(builds.get(0).getMetadata().get("tag")).isEqualTo("tag1");
    assertThat(builds.get(0).getMetadata().get("image")).isEqualTo(Http.getDomainWithPort(url) + "/image:tag1");
    assertThat(builds.get(1).getNumber()).isEqualTo("tag2");
    assertThat(builds.get(1).getBuildUrl()).isEqualTo(url + "image/tags/tag2");
    assertThat(builds.get(1).getMetadata().get("tag")).isEqualTo("tag2");
    assertThat(builds.get(1).getMetadata().get("image")).isEqualTo(Http.getDomainWithPort(url) + "/image:tag2");
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testGetBuildDetailsPaginatedResponseWithBasic() {
    doReturn(dockerRegistryRestClient).when(dockerRestClientFactory).getDockerRegistryRestClient(dockerConfig);

    wireMockRule.stubFor(
        get(urlEqualTo("/v2/image/tags/list"))
            .withBasicAuth(dockerConfig.getUsername(), dockerConfig.getPassword())
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withBody(JsonUtils.asJson(dockerImageTagResponse))
                    .withHeader("link", "</v2/tags/list?next_page=gAAAAABbuZsLNl9W6tAycol&n=2>; rel=\"next\"")));

    wireMockRule.stubFor(
        get(urlEqualTo("/v2/image/tags/list?next_page=gAAAAABbuZsLNl9W6tAycol&n=2"))
            .withBasicAuth(dockerConfig.getUsername(), dockerConfig.getPassword())
            .willReturn(aResponse().withStatus(200).withBody(JsonUtils.asJson(dockerImageTagResponsePaginated))));

    List<BuildDetailsInternal> builds = dockerRegistryService.getBuilds(dockerConfig, "image", 10, null);
    assertThat(builds).isNotNull();
    assertThat(builds.get(0).getNumber()).isEqualTo("tag1");
    assertThat(builds.get(0).getBuildUrl()).isEqualTo(url + "image/tags/tag1");
    assertThat(builds.get(0).getMetadata().get("tag")).isEqualTo("tag1");
    assertThat(builds.get(0).getMetadata().get("image")).isEqualTo(Http.getDomainWithPort(url) + "/image:tag1");
    assertThat(builds.get(1).getNumber()).isEqualTo("tag2");
    assertThat(builds.get(1).getBuildUrl()).isEqualTo(url + "image/tags/tag2");
    assertThat(builds.get(1).getMetadata().get("tag")).isEqualTo("tag2");
    assertThat(builds.get(1).getMetadata().get("image")).isEqualTo(Http.getDomainWithPort(url) + "/image:tag2");

    assertThat(builds.get(2).getNumber()).isEqualTo("tag3");
    assertThat(builds.get(2).getBuildUrl()).isEqualTo(url + "image/tags/tag3");
    assertThat(builds.get(2).getMetadata().get("tag")).isEqualTo("tag3");
    assertThat(builds.get(2).getMetadata().get("image")).isEqualTo(Http.getDomainWithPort(url) + "/image:tag3");
    assertThat(builds.get(3).getNumber()).isEqualTo("tag4");
    assertThat(builds.get(3).getBuildUrl()).isEqualTo(url + "image/tags/tag4");
    assertThat(builds.get(3).getMetadata().get("tag")).isEqualTo("tag4");
    assertThat(builds.get(3).getMetadata().get("image")).isEqualTo(Http.getDomainWithPort(url) + "/image:tag4");
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void testGetBuildDetailsPaginatedResponseWithBearer() {
    doReturn(dockerRegistryRestClient).when(dockerRestClientFactory).getDockerRegistryRestClient(dockerConfig);

    wireMockRule.stubFor(get(urlEqualTo("/v2/image/tags/list"))
                             .withBasicAuth(dockerConfig.getUsername(), dockerConfig.getPassword())
                             .willReturn(aResponse().withStatus(401).withHeader("Www-Authenticate",
                                 "Bearer realm=\"http://localhost:" + wireMockRule.port()
                                     + "/service/token\",service=\"harbor-registry\",scope=\"somevalue\"")));

    // http://localhost:9883/service/token?service=harbor-registry&scope=somevalue
    wireMockRule.stubFor(get(urlEqualTo("/service/token?service=harbor-registry&scope=somevalue"))
                             .willReturn(aResponse().withBody(JsonUtils.asJson(dockerRegistryToken))));

    wireMockRule.stubFor(
        get(urlEqualTo("/v2/image/tags/list"))
            .withHeader("Authorization", equalTo("Bearer dockerRegistryToken"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withBody(JsonUtils.asJson(dockerImageTagResponse))
                    .withHeader("link", "</v2/tags/list?next_page=gAAAAABbuZsLNl9W6tAycol&n=2>; rel=\"next\"")));

    wireMockRule.stubFor(
        get(urlEqualTo("/v2/image/tags/list?next_page=gAAAAABbuZsLNl9W6tAycol&n=2"))
            .withHeader("Authorization", equalTo("Bearer dockerRegistryToken"))
            .willReturn(aResponse().withStatus(200).withBody(JsonUtils.asJson(dockerImageTagResponsePaginated))));

    List<BuildDetailsInternal> builds = dockerRegistryService.getBuilds(dockerConfig, "image", 10, null);
    assertThat(builds).isNotNull();
    assertThat(builds.get(0).getNumber()).isEqualTo("tag1");
    assertThat(builds.get(0).getBuildUrl()).isEqualTo(url + "image/tags/tag1");
    assertThat(builds.get(0).getMetadata().get("tag")).isEqualTo("tag1");
    assertThat(builds.get(0).getMetadata().get("image")).isEqualTo(Http.getDomainWithPort(url) + "/image:tag1");
    assertThat(builds.get(1).getNumber()).isEqualTo("tag2");
    assertThat(builds.get(1).getBuildUrl()).isEqualTo(url + "image/tags/tag2");
    assertThat(builds.get(1).getMetadata().get("tag")).isEqualTo("tag2");
    assertThat(builds.get(1).getMetadata().get("image")).isEqualTo(Http.getDomainWithPort(url) + "/image:tag2");

    assertThat(builds.get(2).getNumber()).isEqualTo("tag3");
    assertThat(builds.get(2).getBuildUrl()).isEqualTo(url + "image/tags/tag3");
    assertThat(builds.get(2).getMetadata().get("tag")).isEqualTo("tag3");
    assertThat(builds.get(2).getMetadata().get("image")).isEqualTo(Http.getDomainWithPort(url) + "/image:tag3");
    assertThat(builds.get(3).getNumber()).isEqualTo("tag4");
    assertThat(builds.get(3).getBuildUrl()).isEqualTo(url + "image/tags/tag4");
    assertThat(builds.get(3).getMetadata().get("tag")).isEqualTo("tag4");
    assertThat(builds.get(3).getMetadata().get("image")).isEqualTo(Http.getDomainWithPort(url) + "/image:tag4");
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testVerifyImageName() {
    doReturn(dockerRegistryRestClient).when(dockerRestClientFactory).getDockerRegistryRestClient(dockerConfig);

    wireMockRule.stubFor(
        get(urlEqualTo("/v2/image/tags/list"))
            .willReturn(aResponse().withStatus(200).withBody(JsonUtils.asJson(dockerImageTagResponse))));

    boolean image = dockerRegistryService.verifyImageName(dockerConfig, "image");
    assertThat(image).isTrue();
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testVerifyImageNameForAcr() {
    doReturn(dockerRegistryRestClient).when(dockerRestClientFactory).getDockerRegistryRestClient(dockerConfig);
    doReturn(true).when(dockerRegistryUtils).isAcrContainerRegistry(any());
    wireMockRule.stubFor(get(urlEqualTo("/v2/image/tags/list"))
                             .willReturn(aResponse().withStatus(401).withHeader("Www-Authenticate",
                                 "Bearer realm=\"http://localhost:" + wireMockRule.port()
                                     + "/oauth2/token\",service=\"test.azurecr.io\",scope=\"somevalue\"")));
    wireMockRule.stubFor(
        get(urlEqualTo("/v2/image/tags/list"))
            .withHeader("Authorization", equalTo("Bearer dockerRegistryToken"))
            .willReturn(aResponse().withStatus(200).withBody(JsonUtils.asJson(dockerImageTagResponse))));
    wireMockRule.stubFor(
        get(urlEqualTo("/oauth2/token?service=test.azurecr.io&scope=somevalue"))
            .willReturn(aResponse().withStatus(200).withBody(JsonUtils.asJson(acrDockerRegistryToken))));

    boolean image = dockerRegistryService.verifyImageName(dockerConfig, "image");
    assertThat(image).isTrue();
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
        dockerRegistryService.getLabels(dockerConfig, "image", asList("abc", "latest"));
    assertThat(resultLabelsMap).isNotNull();
    assertThat(resultLabelsMap.size()).isEqualTo(2);
    assertThat(resultLabelsMap.get(0).get("abc")).isEqualTo("abc");
    assertThat(resultLabelsMap.get(1).get("abc1")).isEqualTo("abc1");
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testGetLastSuccessfulBuild() {
    BuildDetailsInternal buildDetailsInternal = dockerRegistryService.getLastSuccessfulBuild(dockerConfig, "image");
    assertThat(buildDetailsInternal).isNull();
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testIsSuccessfulNullResponse() {
    try {
      DockerRegistryServiceImpl.isSuccessful(null);
      fail("Should not reach here");
    } catch (Exception ex) {
      assertThat(getMessage(ex)).isEqualTo("Null response found");
    }
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testIsSuccessfulErrorCode500() {
    try {
      doReturn(dockerRegistryRestClient).when(dockerRestClientFactory).getDockerRegistryRestClient(dockerConfig);
      dockerRegistryService.getBuilds(dockerConfig, "image_500", 10, null);
      fail("Should not reach here");
    } catch (Exception ex) {
      assertThat(getMessage(ex))
          .isEqualTo(
              "Could not fetch tags for the image. Check if the image exists and if the permissions are scoped for the authenticated user. Server Error");
    }
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldParseLink() {
    String link =
        "</v2/myAccount/myfirstrepo/tags/list?next_page=gAAAAABbuZsLNl9W6tAycol_oLvcYeti2w53XnoV3FYyFBkd-TQV3OBiWNJLqp2m8isy3SWusAqA4Y32dHJ7tGi0br18kXEt6nTW306QUFexaXrAGq8KeSc%3D&n=25>; rel=\"next\"";
    String parsedLink = DockerRegistryServiceImpl.parseLink(link);
    assertThat(parsedLink).isNotEmpty();
    assertThat(parsedLink)
        .isEqualTo(
            "v2/myAccount/myfirstrepo/tags/list?next_page=gAAAAABbuZsLNl9W6tAycol_oLvcYeti2w53XnoV3FYyFBkd-TQV3OBiWNJLqp2m8isy3SWusAqA4Y32dHJ7tGi0br18kXEt6nTW306QUFexaXrAGq8KeSc%3D&n=25");
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void testHarborProjectsSuccessfulResponse() {
    wireMockRule.stubFor(get(urlEqualTo("/api/v2.0/projects"))
                             .willReturn(aResponse().withStatus(200).withBody("[{\"name\": \"library\"}]")));
    assertThat(dockerRegistryService.validateCredentials(harborConfig)).isTrue();
    verify(dockerRestClientFactory, never()).getDockerRegistryRestClient(any());
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void testHarborProjectsFailureCases() {
    wireMockRule.stubFor(get(urlEqualTo("/api/v2.0/projects")).willReturn(aResponse().withStatus(401)));
    assertThatThrownBy(() -> dockerRegistryService.validateCredentials(harborConfig))
        .isInstanceOf(HintException.class)
        .getCause()
        .isInstanceOf(ExplanationException.class)
        .getCause()
        .isInstanceOf(InvalidArtifactServerException.class)
        .extracting("params.message")
        .isEqualTo("Invalid Docker Registry credentials");

    verify(dockerRestClientFactory, never()).getDockerRegistryRestClient(any());

    wireMockRule.stubFor(get(urlEqualTo("/api/v2.0/projects")).willReturn(aResponse().withStatus(200).withBody("[]")));
    assertThatThrownBy(() -> dockerRegistryService.validateCredentials(harborConfig))
        .isInstanceOf(HintException.class)
        .getCause()
        .isInstanceOf(ExplanationException.class)
        .getCause()
        .isInstanceOf(InvalidArtifactServerException.class)
        .extracting("params.message")
        .isEqualTo("No Harbor projects associated with given user");

    verify(dockerRestClientFactory, never()).getDockerRegistryRestClient(any());

    wireMockRule.stubFor(
        get(urlEqualTo("/api/v2.0/projects")).willReturn(aResponse().withFault(Fault.MALFORMED_RESPONSE_CHUNK)));
    assertThatThrownBy(() -> dockerRegistryService.validateCredentials(harborConfig))
        .isInstanceOf(HintException.class)
        .getCause()
        .isInstanceOf(ExplanationException.class)
        .getCause()
        .isInstanceOf(InvalidArtifactServerException.class)
        .extracting("params.message")
        .isEqualTo("ProtocolException: Expected leading [0-9a-fA-F] character but was 0x6c");
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testForValidURL() {
    String url2 = "http://localhost:" + wireMockRule.port() + "/";
    DockerInternalConfig dockerConfig2 =
        DockerInternalConfig.builder().dockerRegistryUrl(url2).username("username").password("password").build();
    doReturn(dockerRegistryRestClient).when(dockerRestClientFactory).getDockerRegistryRestClient(dockerConfig2);
    wireMockRule.stubFor(get(urlEqualTo("/v2/")).willReturn(aResponse().withStatus(200)));
    wireMockRule.stubFor(get(urlEqualTo("/")).willReturn(aResponse().withStatus(400)));
    assertThat(dockerRegistryService.validateCredentials(dockerConfig));
    url2 = "http://localhost:" + wireMockRule.port() + "/v2/";
    dockerConfig2 =
        DockerInternalConfig.builder().dockerRegistryUrl(url2).username("username").password("password").build();
    doReturn(dockerRegistryRestClient).when(dockerRestClientFactory).getDockerRegistryRestClient(dockerConfig2);
    assertThat(dockerRegistryService.validateCredentials(dockerConfig));
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void testHarborConnectivity() {
    // Test for https://harness.atlassian.net/browse/CDS-44746
    wireMockRule.stubFor(get(urlEqualTo("/v2")).willReturn(aResponse().withStatus(400)));
    wireMockRule.stubFor(get(urlEqualTo("/v2/")).willReturn(aResponse().withStatus(400)));
    wireMockRule.stubFor(get(urlEqualTo("/api/v2.0/ping")).willReturn(aResponse().withStatus(200)));
    assertThat(dockerRegistryService.validateCredentials(
                   DockerInternalConfig.builder().providerType(HARBOR).dockerRegistryUrl(url).build()))
        .isTrue();
    verify(dockerRestClientFactory, never()).getDockerRegistryRestClient(any());
    assertThatThrownBy(()
                           -> dockerRegistryService.validateCredentials(
                               DockerInternalConfig.builder().providerType(OTHER).dockerRegistryUrl(url).build()))
        .isInstanceOf(HintException.class)
        .getCause()
        .isInstanceOf(ExplanationException.class)
        .extracting("message")
        .isEqualTo("The given Docker Registry URL may be incorrect or not reachable from your delegate(s)");
  }

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testgenerateConnectivityUrl() {
    assertThat(dockerRegistryService.generateConnectivityUrl("https://harness.atlassian.net/", DOCKER_HUB))
        .isEqualTo("https://harness.atlassian.net/v2");

    assertThat(dockerRegistryService.generateConnectivityUrl("https://harness.atlassian.net", DOCKER_HUB))
        .isEqualTo("https://harness.atlassian.net/v2");

    assertThat(dockerRegistryService.generateConnectivityUrl("https://harness.atlassian.net/", HARBOR))
        .isEqualTo("https://harness.atlassian.net/api/v2.0/ping");

    assertThat(dockerRegistryService.generateConnectivityUrl("https://harness.atlassian.net", HARBOR))
        .isEqualTo("https://harness.atlassian.net/api/v2.0/ping");
  }

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testgetArtifactMetaInfo() {
    DockerInternalConfig dockerInternalConfig = DockerInternalConfig.builder().dockerRegistryUrl(url).build();

    ArtifactMetaInfo artifactMetaInfo = ArtifactMetaInfo.builder().build();

    when(dockerPublicRegistryProcessor.getArtifactMetaInfo(dockerInternalConfig, "imageName", "tag", false))
        .thenReturn(artifactMetaInfo);

    assertThat(dockerRegistryService.getArtifactMetaInfo(dockerInternalConfig, "imageName", "tag", false))
        .isEqualTo(artifactMetaInfo);
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldRetryFetchToken() {
    Map<String, String> tokens = new HashMap<>();
    tokens.put("realm", "realm-value");
    tokens.put("service", "service-value");
    tokens.put("scope", "scope-value");

    final DockerRegistryRestClient restClient = Mockito.spy(dockerRegistryRestClient);
    doThrow(RuntimeException.class)
        .when(restClient)
        .getToken("authHeader", "realm-value", "service-value", "scope-value");
    try {
      dockerRegistryService.fetchTokenWithRetry(restClient, "authHeader", tokens);
    } catch (Exception e) {
      verify(restClient, times(5)).getToken("authHeader", "realm-value", "service-value", "scope-value");
    }
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldRetryListImageTagsByUrl() {
    final DockerRegistryRestClient restClient = Mockito.spy(dockerRegistryRestClient);
    doThrow(RuntimeException.class).when(restClient).listImageTagsByUrl("authHeader", "nextPage");
    try {
      dockerRegistryService.listImageTagsByUrl(restClient, "authHeader", "nextPage");
    } catch (Exception e) {
      verify(restClient, times(5)).listImageTagsByUrl(anyString(), anyString());
    }
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testVerifyImageTag() {
    doReturn(dockerRegistryRestClient).when(dockerRestClientFactory).getDockerRegistryRestClient(dockerConfig);
    wireMockRule.stubFor(get(urlEqualTo("/v2/image/manifests/latest"))
                             .willReturn(aResponse().withStatus(401).withHeader("Www-Authenticate",
                                 "Bearer realm=\"http://localhost:" + wireMockRule.port()
                                     + "/oauth2/token\",service=\"test.azurecr.io\",scope=\"somevalue\"")));
    wireMockRule.stubFor(
        get(urlEqualTo("/v2/image/manifests/latest"))
            .withHeader("Authorization", equalTo("Bearer dockerRegistryToken"))
            .willReturn(aResponse().withStatus(200).withBody(JsonUtils.asJson(dockerImageTagResponse))));
    wireMockRule.stubFor(get(urlEqualTo("/oauth2/token?service=test.azurecr.io&scope=somevalue"))
                             .willReturn(aResponse().withStatus(200).withBody(JsonUtils.asJson(dockerRegistryToken))));

    BuildDetailsInternal buildDetailsInternal =
        dockerRegistryService.verifyBuildNumber(dockerConfig, "image", "latest");
    assertThat(buildDetailsInternal.getNumber()).isEqualTo("latest");
  }
}
