/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.k8s.apiclient;

import static io.harness.k8s.apiclient.ApiClientFactoryImpl.fromKubernetesConfig;
import static io.harness.k8s.apiclient.ApiClientFactoryImpl.fromKubernetesConfigWithReadTimeout;
import static io.harness.rule.OwnerRule.ABOSII;
import static io.harness.rule.OwnerRule.AVMOHAN;
import static io.harness.rule.OwnerRule.BOGDAN;
import static io.harness.rule.OwnerRule.PRATYUSH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.k8s.model.KubernetesClusterAuthType;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.rule.Owner;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.VersionApi;
import io.kubernetes.client.openapi.auth.ApiKeyAuth;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import okhttp3.HttpUrl;
import okhttp3.logging.HttpLoggingInterceptor;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okio.ByteString;
import org.apache.commons.codec.binary.Base64;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.MockedStatic;

public class ApiClientFactoryImplTest extends CategoryTest {
  private static final String TEST_CERT = "-----BEGIN CERTIFICATE-----\n"
      + "MIIDljCCAn4CCQCL6lQ6Zho9QTANBgkqhkiG9w0BAQsFADCBjDELMAkGA1UEBhMC\n"
      + "SU4xEjAQBgNVBAgMCUthcm5hdGFrYTESMBAGA1UEBwwJQmFuZ2Fsb3JlMRAwDgYD\n"
      + "VQQKDAdIYXJuZXNzMQswCQYDVQQLDAJDRTEXMBUGA1UEAwwOZGV2Lmhhcm5lc3Mu\n"
      + "aW8xHTAbBgkqhkiG9w0BCQEWDmJvdEBoYXJuZXNzLmlvMB4XDTIwMDIyMTA1MzYz\n"
      + "MVoXDTIxMDIyMDA1MzYzMVowgYwxCzAJBgNVBAYTAklOMRIwEAYDVQQIDAlLYXJu\n"
      + "YXRha2ExEjAQBgNVBAcMCUJhbmdhbG9yZTEQMA4GA1UECgwHSGFybmVzczELMAkG\n"
      + "A1UECwwCQ0UxFzAVBgNVBAMMDmRldi5oYXJuZXNzLmlvMR0wGwYJKoZIhvcNAQkB\n"
      + "Fg5ib3RAaGFybmVzcy5pbzCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEB\n"
      + "AOPCuMa7+b11kG9lcC23ZsydmjdgKz0sex3dIJQQ9P8EzIq74jSstG0oZFLAZ1hd\n"
      + "DoyisqEOyDOWFa/HlYG15E8oZ0OFKrRXdva3kPadH2Grn1MlrLCqaihFsPgpIA9i\n"
      + "y7t12u3f3oMo+BXuUgAi1KltElOJz3fK7NDt71UhkfilbcVzJbeAb8Ho+jl7zw6B\n"
      + "UsRHhwQxzxR6+VIQW5XT08U/P2vOi2B6v7oUBG6mNN0+EQB2K7TzcJYMFbX6Ub7y\n"
      + "wcuLTAZuKSN0mvKs7NsRjF6GFW/Ln588g6Ooe4SBO6I5I9fWEYuBb8Y4FgIame74\n"
      + "2EWOPTr6IRnyovzFVVYsWUkCAwEAATANBgkqhkiG9w0BAQsFAAOCAQEAPe4UxykN\n"
      + "8phABMurLstr//hySylPXYXbp/rE9KQcLe/Fhf0ChDdRLAEEA7Btsy6EPptL6MUB\n"
      + "32vNjpXRVUNS2AjmP8H73pHzcvXWja/AHkQtjnLy9JP3hcYJxXkYkvk9F2Hqcqud\n"
      + "svxRhaq39pR1HYkOFPhu8miw/QJc43yXz4d/2i/NZ+OWm68ZYD3JTBgORc8KoQrS\n"
      + "VFI2x1dwRck9pfh5BWqRySMXTVEeiEDkLC8ffzBLkzxVndLw9S0KrM110SeEqZG0\n"
      + "qwQuGwyvzkeKKiebTa12RJgrdHO+ZZjE+LT4ZPMq51CfA5OIR9sRGkTgWXJchECe\n"
      + "/exf+BvOZKtG6A==\n"
      + "-----END CERTIFICATE-----";

  private static final String TEST_KEY = "-----BEGIN RSA PRIVATE KEY-----\n"
      + "MIIEpAIBAAKCAQEA48K4xrv5vXWQb2VwLbdmzJ2aN2ArPSx7Hd0glBD0/wTMirvi\n"
      + "NKy0bShkUsBnWF0OjKKyoQ7IM5YVr8eVgbXkTyhnQ4UqtFd29reQ9p0fYaufUyWs\n"
      + "sKpqKEWw+CkgD2LLu3Xa7d/egyj4Fe5SACLUqW0SU4nPd8rs0O3vVSGR+KVtxXMl\n"
      + "t4Bvwej6OXvPDoFSxEeHBDHPFHr5UhBbldPTxT8/a86LYHq/uhQEbqY03T4RAHYr\n"
      + "tPNwlgwVtfpRvvLBy4tMBm4pI3Sa8qzs2xGMXoYVb8ufnzyDo6h7hIE7ojkj19YR\n"
      + "i4FvxjgWAhqZ7vjYRY49OvohGfKi/MVVVixZSQIDAQABAoIBAQCFUcQLQJktV5XW\n"
      + "PxBtEj/wYgiVhYuJ4XGnx3p8cXiXll2Mj/IXV0i95Ljk348e4EnV9J6PPDHgUGgd\n"
      + "XrybErezxji8A0U+DzypqkYGtW5bI7S9XP4642YEcNboTFph5zjOYGxodXSwXdjq\n"
      + "LXh+b+T/z8K8d5yjyHrayYwgzue9HS3YfEZgaB/0OcLvmTh2BPIiQPYildPn2Vwg\n"
      + "0RiEGCdyegTzUO3XRwHYks8ZDAIACGtM20JvcJoIWMCMI6LSllBA4nGCArMPptV6\n"
      + "4QKaiBQFIFJviAWFjrBcBP66o7GjVSsWQ+kHyn+A3VxICFOOIhs+n8F8YuMvkXUD\n"
      + "gXutcl0BAoGBAPhowZp2S9lJRNoG72qhmDhU1cWLQ0mxUZGvm/rtWI4TXiQBoNIA\n"
      + "2kM6fdVilT3XbYmpa2B0DtM1wT7VzjRgNGg+ybXRFXF6rsWLJN+47MKfqnXc9/vp\n"
      + "KoNnVqY0ZuCLl0FGndhhRFrH8GprDNFanqsViVLodd+hQt5CrGrERcWxAoGBAOq4\n"
      + "b9VL+QUO8Du8Ut5Iz+dqtl4jGbGetJ9sBOzQyJrr8LnzDFYxq0oeNLdXzO49jAMR\n"
      + "OQEg+XqaIYoIn9zkNvhhBz1e623FPd1moktHJiwkPGbrH9daDs/Ib5q5Wdfs01uc\n"
      + "XuSXB4bscXbIJtZ08zm3EPCYITNZBjk1iUJzYHsZAoGBAMj6AEFXGlDHPcRkPgn4\n"
      + "ia5xvK7h2GPj6YnEGZ+vrajtNIpPIu0lMXGY/jvJUdPB2ua4wp47586sPBf5Zabs\n"
      + "exooSowmiIHSb1p2FDRJaoygH4rSZ3RRlkrQLcO0u9NKPOrcFlL9hw8nmnSO/cTX\n"
      + "222xs/P3DX8L+ozWRqbu+0BRAoGAIkK1oC6stH5PtohwmB2Mqzy6TddVwsVlm/eK\n"
      + "aH65KVPTGXFOla4+UF1EWJaqRQQa0b/L0Exd3fVte9Zyby2okGypP94BA07NDuoS\n"
      + "OmayPbM0VdlwFmEA1HdvQuhXIttgpniWqUsaQCl3Dl18vcToU75S7Ktn/TS7YdJc\n"
      + "rKRmqtECgYBaVS+mtPKikraQn8s1E+miJp+E+YpPKuRqtIxW7xq0vLndgmgFTjSs\n"
      + "iEQN1d1VOx1UK8SxUUvq5JZRxb8w1VZ7nPu+xU8foJ7aiH+QtJEl/Mr+E5Yzyg9Y\n"
      + "H9WINAUEjk6+GrpN1HJdUrT3nYcwCn8RnLkBvh/OcKoTgRbC9lhRxA==\n"
      + "-----END RSA PRIVATE KEY-----\n";

  private ApiClientFactory apiClientFactory;

  @Before
  public void setUp() throws Exception {
    apiClientFactory = new ApiClientFactoryImpl();
  }

  @Test
  @Owner(developers = AVMOHAN)
  @Category(UnitTests.class)
  public void testReadTimeout() throws Exception {
    assertThat(apiClientFactory.getClient(KubernetesConfig.builder().build()).getHttpClient().readTimeoutMillis())
        .isEqualTo(0);
  }

  @Test
  @Owner(developers = PRATYUSH)
  @Category(UnitTests.class)
  public void testReadTimeoutAndConnectTimeout() throws Exception {
    assertThat(fromKubernetesConfig(KubernetesConfig.builder().build(), null).getHttpClient().readTimeoutMillis())
        .isEqualTo(0);
    assertThat(fromKubernetesConfigWithReadTimeout(KubernetesConfig.builder().build(), null)
                   .getHttpClient()
                   .readTimeoutMillis())
        .isEqualTo(120000);
    assertThat(fromKubernetesConfig(KubernetesConfig.builder().build(), null).getHttpClient().connectTimeoutMillis())
        .isEqualTo(60000);
    assertThat(fromKubernetesConfigWithReadTimeout(KubernetesConfig.builder().build(), null)
                   .getHttpClient()
                   .connectTimeoutMillis())
        .isEqualTo(60000);
  }

  @Test
  @Owner(developers = AVMOHAN)
  @Category(UnitTests.class)
  public void testGetClientWithServiceTokenAuth() throws Exception {
    KubernetesConfig kubernetesConfig = KubernetesConfig.builder()
                                            .masterUrl("https://34.66.78.221")
                                            .serviceAccountTokenSupplier(() -> "service-token")
                                            .build();

    ApiClient client = apiClientFactory.getClient(kubernetesConfig);
    assertThat(client.getBasePath()).isEqualTo("https://34.66.78.221");
    assertThat(client.getAuthentication("BearerToken")).isInstanceOfSatisfying(ApiKeyAuth.class, apiKeyAuth -> {
      assertThat(apiKeyAuth.getApiKeyPrefix()).isEqualTo("Bearer");
      assertThat(apiKeyAuth.getApiKey()).isEqualTo("service-token");
    });
  }

  @Test
  @Owner(developers = AVMOHAN)
  @Category(UnitTests.class)
  public void testGetClientWithBasicAuth() {
    KubernetesConfig kubernetesConfig = KubernetesConfig.builder()
                                            .masterUrl("https://34.66.78.221")
                                            .username("avmohan".toCharArray())
                                            .password("test".toCharArray())
                                            .build();

    ApiClient client = apiClientFactory.getClient(kubernetesConfig);
    assertThat(client.getBasePath()).isEqualTo("https://34.66.78.221");
    assertThat(client.isVerifyingSsl()).isEqualTo(false);
    assertThat(client.getSslCaCert()).isNull();
    assertThat(client.getAuthentication("BearerToken")).isInstanceOfSatisfying(ApiKeyAuth.class, apiKeyAuth -> {
      assertThat(apiKeyAuth.getApiKeyPrefix()).isEqualTo("Basic");
      assertThat(apiKeyAuth.getApiKey())
          .isEqualTo(ByteString.of("avmohan:test".getBytes(StandardCharsets.ISO_8859_1)).base64());
    });
  }

  @Test
  @Owner(developers = AVMOHAN)
  @Category(UnitTests.class)
  public void testGetClientWithClientCertificateAuth() {
    KubernetesConfig kubernetesConfig = KubernetesConfig.builder()
                                            .masterUrl("https://34.66.78.221")
                                            .clientCert(TEST_CERT.toCharArray())
                                            .clientKey(TEST_KEY.toCharArray())
                                            .build();

    ApiClient client = apiClientFactory.getClient(kubernetesConfig);
    assertThat(client.getBasePath()).isEqualTo("https://34.66.78.221");
    assertThat(client.isVerifyingSsl()).isEqualTo(false);
    assertThat(client.getSslCaCert()).isNull();
    assertThat(client.getKeyManagers()).isNotEmpty();
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testApiClientLoggingInterceptor() {
    try (MockedStatic<K8sApiClientHelper> mockedStatic = mockStatic(K8sApiClientHelper.class)) {
      mockedStatic.when(() -> K8sApiClientHelper.isEnvVarSet(anyString())).thenReturn(true);

      KubernetesConfig kubernetesConfig = KubernetesConfig.builder()
                                              .masterUrl("https://34.66.78.221")
                                              .clientCert(TEST_CERT.toCharArray())
                                              .clientKey(TEST_KEY.toCharArray())
                                              .build();
      ApiClient client = apiClientFactory.getClient(kubernetesConfig);
      assertThat(client.getHttpClient().networkInterceptors()).hasSize(2);
      assertThat(client.getHttpClient().networkInterceptors().get(1)).isInstanceOf(HttpLoggingInterceptor.class);
    }
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGetClientWithClientCertificateAuthBase64Encoded() {
    KubernetesConfig kubernetesConfig = KubernetesConfig.builder()
                                            .masterUrl("https://34.66.78.221")
                                            .clientCert(Base64.encodeBase64String(TEST_CERT.getBytes()).toCharArray())
                                            .clientKey(Base64.encodeBase64String(TEST_KEY.getBytes()).toCharArray())
                                            .build();

    ApiClient client = apiClientFactory.getClient(kubernetesConfig);
    assertThat(client.getBasePath()).isEqualTo("https://34.66.78.221");
    assertThat(client.isVerifyingSsl()).isEqualTo(false);
    assertThat(client.getSslCaCert()).isNull();
    assertThat(client.getKeyManagers()).isNotEmpty();
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGetClientWithCaCert() throws IOException {
    testGetApiClientWithCaCert(TEST_CERT, TEST_CERT);
    testGetApiClientWithCaCert(Base64.encodeBase64String(TEST_CERT.getBytes()), TEST_CERT);
  }

  @Test
  @Owner(developers = BOGDAN)
  @Category(UnitTests.class)
  public void testGcpInterceptor() throws ApiException, IOException, InterruptedException {
    try (MockWebServer mockWebServer = new MockWebServer()) {
      // given
      mockWebServer.start();
      HttpUrl url = mockWebServer.url("");
      mockWebServer.enqueue(new MockResponse());
      mockWebServer.enqueue(new MockResponse());

      String firstToken = "FirstToken";
      String secondToken = "SecondToken";
      KubernetesConfig kubernetesConfig = KubernetesConfig.builder()
                                              .masterUrl(url.toString())
                                              .authType(KubernetesClusterAuthType.GCP_OAUTH)
                                              .serviceAccountTokenSupplier(supplierReturning(firstToken, secondToken))
                                              .build();
      ApiClient apiClient = apiClientFactory.getClient(kubernetesConfig);

      // when
      VersionApi apiInstance = new VersionApi(apiClient);
      apiInstance.getCode();
      apiInstance.getCode();

      // then
      assertThat(mockWebServer.takeRequest(1L, TimeUnit.SECONDS).getHeader("Authorization"))
          .isEqualTo("Bearer " + firstToken);
      assertThat(mockWebServer.takeRequest(1L, TimeUnit.SECONDS).getHeader("Authorization"))
          .isEqualTo("Bearer " + secondToken);
    }
  }

  private Supplier<String> supplierReturning(String... tokens) {
    return new Supplier<String>() {
      private int callCount = 0;
      @Override
      public String get() {
        return tokens[callCount++];
      }
    };
  }

  private void testGetApiClientWithCaCert(String cert, String expectedCert) throws IOException {
    KubernetesConfig kubernetesConfig = KubernetesConfig.builder()
                                            .masterUrl("https://34.13.13.112")
                                            .serviceAccountTokenSupplier(() -> "token")
                                            .caCert(cert.toCharArray())
                                            .build();

    ApiClient client = apiClientFactory.getClient(kubernetesConfig);
    assertThat(client.isVerifyingSsl()).isTrue();
    assertThat(client.getSslCaCert()).isNotNull();
    client.getSslCaCert().reset();
    BufferedReader reader = new BufferedReader(new InputStreamReader(client.getSslCaCert()));
    assertThat(reader.lines().collect(Collectors.joining("\n"))).isEqualTo(expectedCert);
  }
}
