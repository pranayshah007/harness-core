/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.artifacts.docker.service;

import static io.harness.artifacts.docker.beans.DockerImageManifestResponse.DockerImageManifestHistoryElement;
import static io.harness.rule.OwnerRule.AADITI;
import static io.harness.rule.OwnerRule.ABHISHEK;
import static io.harness.rule.OwnerRule.DEEPAK_PUTHRAYA;
import static io.harness.rule.OwnerRule.HARSH;
import static io.harness.rule.OwnerRule.SRIDHAR;
import static io.harness.rule.OwnerRule.VINICIUS;

import static java.util.Arrays.asList;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.artifacts.docker.DockerRegistryRestClient;
import io.harness.artifacts.docker.beans.DockerImageManifestResponse;
import io.harness.artifacts.docker.beans.DockerInternalConfig;
import io.harness.beans.ArtifactMetaInfo;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import okhttp3.MediaType;
import okhttp3.Protocol;
import okhttp3.ResponseBody;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import retrofit2.Call;
import retrofit2.Response;

@OwnedBy(HarnessTeam.CDC)
public class DockerRegistryUtilsTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  @Mock private DockerRegistryRestClient dockerRegistryRestClient;
  @InjectMocks DockerRegistryUtils dockerRegistryUtils = new DockerRegistryUtils();
  private static final String AUTH_HEADER = "AUTH_HEADER";
  private static final String IMAGE_NAME = "IMAGE_NAME";
  private static final DockerInternalConfig dockerConfig =
      DockerInternalConfig.builder().dockerRegistryUrl("https://registry.hub.docker.com/v2/").build();

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldNotGetLabelsIfEmptyTags() {
    List<Map<String, String>> labelsMap =
        dockerRegistryUtils.getLabels(dockerConfig, dockerRegistryRestClient, null, AUTH_HEADER, IMAGE_NAME, asList());
    assertThat(labelsMap).isEmpty();
  }

  @Test
  @Owner(developers = HARSH)
  @Category(UnitTests.class)
  public void shouldGetLabelsIfNonEmptyTags() throws IOException {
    Call<DockerImageManifestResponse> requestCall = mock(Call.class);
    when(requestCall.execute()).thenReturn(Response.success(getDockerImageManifestResponse(null)));
    when(dockerRegistryRestClient.getImageManifest(any(), any(), any())).thenReturn(requestCall);

    List<Map<String, String>> labelsMap = dockerRegistryUtils.getLabels(
        dockerConfig, dockerRegistryRestClient, null, AUTH_HEADER, IMAGE_NAME, asList("abc", "abc1"));
    assertThat(labelsMap).isNotEmpty();
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void shouldGetLabelsIfNonEmptyTagsWithValidLabels() throws IOException {
    Call<DockerImageManifestResponse> requestCall = mock(Call.class);
    when(requestCall.execute())
        .thenReturn(Response.success(getDockerImageManifestResponse(
            "{\"architecture\":\"amd64\",\"config\":{\"Labels\":{\"maintainer\":\"NGINX Docker Maintainers <docker-maint@nginx.com>\",\"please\":\"work\"}}}")))
        .thenReturn(Response.success(getDockerImageManifestResponse(
            "{\"architecture\":\"amd64\",\"container_config\":{\"Labels\":{\"app.name\":\"python-hello\",\"author\":\"deepakputhraya\"}},\"config\":{\"Labels\":{\"app.name\":\"hello-world\",\"author\":\"deepakputhraya\",\"version\":\"4.0\"}}}")))
        .thenReturn(Response.success(getDockerImageManifestResponse(
            "{\"architecture\":\"amd64\",\"container_config\":{\"Labels\":{\"maintainer\":\"docker-maint@nginx.com\",\"please\":\"work\"}}}")));
    when(dockerRegistryRestClient.getImageManifest(any(), any(), any())).thenReturn(requestCall);

    List<Map<String, String>> labelsMap = dockerRegistryUtils.getLabels(
        dockerConfig, dockerRegistryRestClient, null, AUTH_HEADER, IMAGE_NAME, asList("abc", "abc1", "abc3"));
    assertThat(labelsMap).isNotEmpty();
    assertThat(labelsMap).hasSize(3);

    assertThat(labelsMap.get(0)).hasSize(2);
    assertThat(labelsMap.get(0))
        .isEqualTo(ImmutableMap.<String, String>builder()
                       .put("please", "work")
                       .put("maintainer", "NGINX Docker Maintainers <docker-maint@nginx.com>")
                       .build());

    assertThat(labelsMap.get(1)).hasSize(3);
    assertThat(labelsMap.get(1))
        .isEqualTo(ImmutableMap.<String, String>builder()
                       .put("app.name", "python-hello")
                       .put("author", "deepakputhraya")
                       .put("version", "4.0")
                       .build());

    assertThat(labelsMap.get(2)).hasSize(2);
    assertThat(labelsMap.get(2))
        .isEqualTo(ImmutableMap.<String, String>builder()
                       .put("please", "work")
                       .put("maintainer", "docker-maint@nginx.com")
                       .build());
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void shouldGetArtifactMetaInfoWithSHA256Digests() throws IOException {
    Call<DockerImageManifestResponse> requestCallV1 = mock(Call.class);
    when(requestCallV1.execute())
        .thenReturn(Response.success(
            getDockerImageManifestResponse(
                "{\"architecture\":\"amd64\",\"config\":{\"Labels\":{\"maintainer\":\"NGINX Docker Maintainers <docker-maint@nginx.com>\",\"please\":\"work\"}}}"),
            (new okhttp3.Response.Builder())
                .code(200)
                .message("OK")
                .addHeader("docker-content-digest", "DIGEST_V1")
                .protocol(Protocol.HTTP_1_1)
                .request((new okhttp3.Request.Builder()).url("http://localhost/").build())
                .build()));
    when(dockerRegistryRestClient.getImageManifest(any(), any(), any())).thenReturn(requestCallV1);
    Call<DockerImageManifestResponse> requestCallV2 = mock(Call.class);
    when(requestCallV2.execute())
        .thenReturn(Response.success(
            getDockerImageManifestResponse(
                "{\"architecture\":\"amd64\",\"config\":{\"Labels\":{\"maintainer\":\"NGINX Docker Maintainers <docker-maint@nginx.com>\",\"please\":\"work\"}}}"),
            (new okhttp3.Response.Builder())
                .code(200)
                .message("OK")
                .addHeader("docker-content-digest", "DIGEST_V2")
                .protocol(Protocol.HTTP_1_1)
                .request((new okhttp3.Request.Builder()).url("http://localhost/").build())
                .build()));
    when(dockerRegistryRestClient.getImageManifestV2(any(), any(), any())).thenReturn(requestCallV2);

    ArtifactMetaInfo artifactMetaInfo = dockerRegistryUtils.getArtifactMetaInfo(
        dockerConfig, dockerRegistryRestClient, null, AUTH_HEADER, IMAGE_NAME, "tag", true);
    assertThat(artifactMetaInfo).isInstanceOf(ArtifactMetaInfo.class);
    assertThat(artifactMetaInfo.getSha()).isEqualTo("DIGEST_V1");
    assertThat(artifactMetaInfo.getShaV2()).isEqualTo("DIGEST_V2");
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void shouldGetArtifactMetaInfoWithSHA256Digests_V1Failed() throws IOException {
    Call<DockerImageManifestResponse> requestCallV1 = mock(Call.class);
    when(requestCallV1.execute())
        .thenReturn(Response.error(ResponseBody.create(MediaType.parse("application/json"), "404 related error"),
            (new okhttp3.Response.Builder())
                .code(404)
                .message("OK")
                .protocol(Protocol.HTTP_1_1)
                .request((new okhttp3.Request.Builder()).url("http://localhost/").build())
                .build()));
    when(dockerRegistryRestClient.getImageManifest(any(), any(), any())).thenReturn(requestCallV1);
    Call<DockerImageManifestResponse> requestCallV2 = mock(Call.class);
    when(requestCallV2.execute())
        .thenReturn(Response.success(
            getDockerImageManifestResponse(
                "{\"architecture\":\"amd64\",\"config\":{\"Labels\":{\"maintainer\":\"NGINX Docker Maintainers <docker-maint@nginx.com>\",\"please\":\"work\"}}}"),
            (new okhttp3.Response.Builder())
                .code(200)
                .message("OK")
                .addHeader("docker-content-digest", "DIGEST_V2")
                .protocol(Protocol.HTTP_1_1)
                .request((new okhttp3.Request.Builder()).url("http://localhost/").build())
                .build()));
    when(dockerRegistryRestClient.getImageManifestV2(any(), any(), any())).thenReturn(requestCallV2);

    ArtifactMetaInfo artifactMetaInfo = dockerRegistryUtils.getArtifactMetaInfo(
        dockerConfig, dockerRegistryRestClient, null, AUTH_HEADER, IMAGE_NAME, "tag", true);
    assertThat(artifactMetaInfo).isInstanceOf(ArtifactMetaInfo.class);
    assertThat(artifactMetaInfo.getSha()).isEqualTo(null);
    assertThat(artifactMetaInfo.getShaV2()).isEqualTo("DIGEST_V2");
  }

  private DockerImageManifestResponse getDockerImageManifestResponse(String v1Compatibility) {
    DockerImageManifestResponse dockerImageManifestResponse = new DockerImageManifestResponse();
    dockerImageManifestResponse.setName("abc");
    DockerImageManifestHistoryElement history = new DockerImageManifestHistoryElement();
    history.setV1Compatibility(v1Compatibility);
    dockerImageManifestResponse.setHistory(Collections.singletonList(history));
    return dockerImageManifestResponse;
  }

  @Test
  @Owner(developers = SRIDHAR)
  @Category(UnitTests.class)
  public void testValidateCredentialForDockerAcrDomain() {
    boolean response = dockerRegistryUtils.isAcrContainerRegistry(dockerConfig);
    assertFalse(response);
    DockerInternalConfig dockerInternalConfig =
        DockerInternalConfig.builder().dockerRegistryUrl("https://sridhartest.azurecr.io/").build();
    response = dockerRegistryUtils.isAcrContainerRegistry(dockerInternalConfig);
    assertTrue(response);

    dockerInternalConfig = DockerInternalConfig.builder().dockerRegistryUrl("https://sridhartest.azurecr.us/").build();
    response = dockerRegistryUtils.isAcrContainerRegistry(dockerInternalConfig);
    assertTrue(response);
  }
}
