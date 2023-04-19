/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.k8s.model;

import static io.harness.rule.OwnerRule.BOGDAN;
import static io.harness.rule.OwnerRule.NAMAN_TALAYCHA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.category.element.UnitTests;
import io.harness.exception.ExplanationException;
import io.harness.k8s.model.GcpAccessTokenSupplier.GcpAccessTokenSupplierBuilder;
import io.harness.rule.Owner;

import com.google.api.client.auth.oauth2.StoredCredential;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.store.DataStore;
import com.google.api.client.util.store.MemoryDataStoreFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class GcpAccessTokenSupplierTest {
  private static final String CLOUD_PLATFORM = "https://www.googleapis.com/auth/cloud-platform";

  private GoogleCredential googleCredential;
  private DataStore<StoredCredential> cache;
  private MockTransport transport;
  private FakeJavaClock clock;
  private GcpAccessTokenSupplier gcpAccessTokenSupplier;

  @Before
  public void setUp() throws IOException {
    transport = new MockTransport();

    clock = new FakeJavaClock(Instant.now());
    cache = StoredCredential.getDefaultDataStore(new MemoryDataStoreFactory());

    ByteArrayInputStream bis = new ByteArrayInputStream(DUMMY_GCP_KEY.getBytes(StandardCharsets.UTF_8));
    googleCredential = GoogleCredential.fromStream(bis, transport, JacksonFactory.getDefaultInstance());
    googleCredential = googleCredential.createScoped(Collections.singleton(CLOUD_PLATFORM));

    gcpAccessTokenSupplier = defaultSupplier();
  }

  @Test
  @Owner(developers = BOGDAN)
  @Category(UnitTests.class)
  public void shouldGetAFreshTokenWhenInitialized() {
    // given
    String expectedToken = "newAccessToken";
    transport.addServiceAccount(googleCredential.getServiceAccountId(), expectedToken);

    // when
    String token = gcpAccessTokenSupplier.get();

    // then
    assertThat(token).isEqualTo(expectedToken);
  }

  @Test
  @Owner(developers = BOGDAN)
  @Category(UnitTests.class)
  public void shouldReuseTokenFromCacheIfTokenNotExpired() {
    // when
    transport.addServiceAccount(googleCredential.getServiceAccountId(), "firstToken");
    String firstToken = gcpAccessTokenSupplier.get();

    transport.addServiceAccount(googleCredential.getServiceAccountId(), "secondToken");
    String secondToken = gcpAccessTokenSupplier.get();

    // then
    assertThat(firstToken).isEqualTo(secondToken);
  }

  @Test
  @Owner(developers = BOGDAN)
  @Category(UnitTests.class)
  public void shouldRefreshTokenIfTokenWillExpireInOneMinute() {
    // given
    transport.addServiceAccount(googleCredential.getServiceAccountId(), "firstToken");
    String firstToken = gcpAccessTokenSupplier.get();

    // when
    clock.advance(Duration.ofMinutes(59).plus(Duration.ofSeconds(1L)));
    transport.addServiceAccount(googleCredential.getServiceAccountId(), "secondToken");
    String secondToken = gcpAccessTokenSupplier.get();

    // then
    assertThat(firstToken).isNotEqualTo(secondToken);
  }

  @Test
  @Owner(developers = BOGDAN)
  @Category(UnitTests.class)
  public void shouldReuseTokenIfNewSupplierInstanceIsCreated() {
    // given
    GcpAccessTokenSupplier firstInstance = defaultSupplier();
    transport.addServiceAccount(googleCredential.getServiceAccountId(), "firstToken");
    String firstToken = firstInstance.get();

    // when
    GcpAccessTokenSupplier secondInstance = defaultSupplier();
    transport.addServiceAccount(googleCredential.getServiceAccountId(), "secondToken");
    String secondToken = secondInstance.get();

    // then
    assertThat(firstToken).isEqualTo(secondToken);
  }

  @Test(expected = ExplanationException.class)
  @Owner(developers = BOGDAN)
  @Category(UnitTests.class)
  public void shouldThrowExplanationExceptionIfCacheIsNotAvailable() throws IOException {
    // given
    DataStore<StoredCredential> exceptionThrowingCache = mock(DataStore.class);
    when(exceptionThrowingCache.get(anyString())).thenThrow(new IOException());
    GcpAccessTokenSupplier tokenSupplier = defaultSupplierBuilder().cache(exceptionThrowingCache).build();

    // when
    tokenSupplier.get();
  }

  @Test
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testCopyAndAddRefreshListener()
      throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
    Method method = gcpAccessTokenSupplier.getClass().getDeclaredMethod(
        "copyAndAddRefreshListener", GoogleCredential.class, Clock.class, DataStore.class);
    method.setAccessible(true);
    GoogleCredential newGoogleCredential =
        (GoogleCredential) method.invoke(gcpAccessTokenSupplier, googleCredential, clock, cache);
    assertThat(newGoogleCredential.getServiceAccountScopes().size()).isEqualTo(2);
    assertThat(newGoogleCredential.getServiceAccountScopes().contains("https://www.googleapis.com/auth/userinfo.email"))
        .isTrue();
  }

  private GcpAccessTokenSupplierBuilder defaultSupplierBuilder() {
    return GcpAccessTokenSupplier.builder()
        .cache(cache)
        .clock(clock)
        .serviceAccountJsonKey(DUMMY_GCP_KEY)
        .jsonKeyToCredential(unused -> googleCredential);
  }

  private GcpAccessTokenSupplier defaultSupplier() {
    return defaultSupplierBuilder().build();
  }

  private static final String DUMMY_GCP_KEY = "{\n"
      + "      \"type\": \"service_account\",\n"
      + "      \"project_id\": \"mock-project\",\n"
      + "      \"private_key_id\": \"768b325f9fad5b898890cad91e64d44c8b9851f7\",\n"
      + "      \"private_key\": \"-----BEGIN PRIVATE KEY-----\\nMIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQDB82kQsrtIhE9Y\\nTvHIplTGcc8YF4tIFe8oqLVQD1TExYCZ8P+nNuAO7NAmMCBbvNMwozTD+N/IgqC9\\n3nfwKzNgaRDmPDBdXItWii3AnxnCrAszJRJrUD/mQUGRIUe6P5q2zazFSdKqBMrX\\nW6/4VKXYF70Uz2rDFMbygxd1ndRJvXIIFLIlulqu7U8HePhVMHSLNi0u2WCcC76S\\nPH5spmBliABYUj0fRYgOtnPTw3qukWFPcq0VhPTRdP5/ApTriMr0nkfbrbJEh+Z4\\n7+/vY6vYmm1xaHc5Knl0sgPqXiRFOeUy3oQMF8KOsuJIFf24BFOXRIv8d257ChdE\\n011eyvCbAgMBAAECggEAT/mfCVOqBm0IitGCwcpUir/DNZv/wunIhGuM2EZ6HemS\\n7eeCg+EM4xqjehu+PBXQv+2MhILLRFMZFTH3IwGtXcP1Q/rttpHCoxy3YQY6CRwI\\nQj63KakdsESYVM/0U8iGc3q8E14tkA4J1mPoW+4LtN+VCE+/JlIa90U3FzjNoNnU\\ntSN6meGXgPm2sW7qb9Gy55mtwyGvILysVqCTBsA9J1+luUwDhsX+FK64QNJUQE7V\\niquThoDpusNaQeozR3LLLkgYAe8Vosi9JiqQsO5VsOTCdss6q+t5GIXVNuk088eg\\nRmeeDQ/4kWkl1KXafD2fWxNPZnySUySNedarLFiIAQKBgQD3bzaJ8/JG5fYVO1+2\\nII7kYjEnWOY5NuyWBRcHsYryi/dtfMC/NLnXFI/lrZEcFq8bpwzvRe4WeE/9Lth0\\nwQ0Bvt8vCItR2fEuyQlGQJH4D55440kceP0UNRqxeNY5HQ94HSHuUymP4rLt+12Z\\nftWCMYGxjRRWMZ90M6nKAXk82wKBgQDIqjh+nSDiMpkHXup2gkViJZDewtt822Ki\\nYazgAH6rd2DbX98gEY9vPyURkFirHFNwT2p1Cb/xCDv+/V2cIj3E9LE+nzsCuqHv\\nK/aDOh7JxRiR7QbJVAzph7+CXsBXZ9trln7apFX4JzWKhoJKIjga+kYDRBOW2X5V\\nyDftXjGHQQKBgQDuQZagq9gFUPXuZ+e3tg4h+DMgkkfNnAegRXJxpBIJj6FHOjNX\\namvwoQoWvVTXWThwRiD8Xbfuxxcu0mb3tdTSc3rxDScqP9QvmsFldlOYK2ILQcBq\\nvE3loWT8s0CEamk03ciIdme09zQYWE0+upTY8tbRoumMPeguunip3VVitQKBgA5Q\\nbjVB+i2IlHf9IlaP1mk46supNMUEVVXmB9H21xJeMq+TeDQubH/wDjHhjSGvpJgX\\nYi21I1cLUlRPOJVBsAxTtC0WaLw6GgEYrr4PsFCOWcFXGivUbhNelp+zKJ9Tjkhv\\ndN8d5/AKw/v8umCVblEmV0Y2XftdynBOFwc8t+XBAoGANfYiTARtDfXZDQyLgxh1\\njWvdZ+9r03CjIv92DSOOjbZAscqIyD7eI9J4pnntzXZcnEyqrR/Yzh4YxD8lKnyv\\ngphCp7yU3Sjl+h+ujyD1W0CSKvk+Pk/d83UtzDAI4tfMBXI2boifoCDspI0m1fIY\\n+ztYqVJVL3nV4AwsNomqY/4=\\n-----END PRIVATE KEY-----\\n\",\n"
      + "      \"client_email\": \"mock-account-id@mock-project.iam.gserviceaccount.com\",\n"
      + "      \"client_id\": \"114729593829257735690\",\n"
      + "      \"auth_uri\": \"https://accounts.google.com/o/oauth2/auth\",\n"
      + "      \"token_uri\": \"https://oauth2.googleapis.com/token\",\n"
      + "      \"auth_provider_x509_cert_url\": \"https://www.googleapis.com/oauth2/v1/certs\",\n"
      + "      \"client_x509_cert_url\": \"https://www.googleapis.com/robot/v1/metadata/x509/mock-account-id%40mock-project.iam.gserviceaccount.com\"\n"
      + "    }";
}
