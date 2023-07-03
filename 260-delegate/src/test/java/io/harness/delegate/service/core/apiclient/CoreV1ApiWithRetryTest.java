/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.service.core.apiclient;

import static io.harness.rule.OwnerRule.MARKO;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.ApiResponse;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1Secret;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class CoreV1ApiWithRetryTest {
  private CoreV1ApiWithRetry underTest;
  @Mock private ApiClient apiClient;

  @Before
  public void setUp() {
    underTest = new CoreV1ApiWithRetry(apiClient);
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void testCreateNamespacedPod() throws ApiException {
    final var namespace = "namespace";
    final var pod = new V1Pod().metadata(new V1ObjectMeta().name("test").namespace(namespace));

    final var expectedPod = new V1Pod();
    final var dummyResponse = new ApiResponse<>(200, Map.of(), expectedPod);

    when(apiClient.<V1Pod>execute(any(), any())).thenReturn(dummyResponse);
    when(apiClient.escapeString(namespace)).thenCallRealMethod();

    final var actual = underTest.createNamespacedPod(namespace, pod, null, null, null, null);
    assertThat(actual).isEqualTo(expectedPod);
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void testCreateNamespacedPodAfterRetry() throws ApiException {
    final var namespace = "namespace";
    final var pod = new V1Pod().metadata(new V1ObjectMeta().name("test").namespace(namespace));

    final var expectedPod = new V1Pod();
    final var dummyResponse = new ApiResponse<>(200, Map.of(), expectedPod);

    when(apiClient.<V1Pod>execute(any(), any())).thenThrow(new ApiException()).thenReturn(dummyResponse);
    when(apiClient.escapeString(namespace)).thenCallRealMethod();

    final var actual = underTest.createNamespacedPod(namespace, pod, null, null, null, null);
    assertThat(actual).isEqualTo(expectedPod);
  }

  @Test(expected = ApiException.class)
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void testCreateNamespacedPodAfterRetryFails() throws ApiException {
    final var namespace = "namespace";
    final var pod = new V1Pod().metadata(new V1ObjectMeta().name("test").namespace(namespace));

    when(apiClient.<V1Pod>execute(any(), any()))
        .thenThrow(new ApiException())
        .thenThrow(new ApiException())
        .thenThrow(new ApiException());
    when(apiClient.escapeString(namespace)).thenCallRealMethod();

    underTest.createNamespacedPod(namespace, pod, null, null, null, null);
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void testCreateNamespacedSecret() throws ApiException {
    final var namespace = "namespace";
    final var secret = new V1Secret().metadata(new V1ObjectMeta().name("test").namespace(namespace));

    final var expectedSecret = new V1Secret();
    final var dummyResponse = new ApiResponse<>(200, Map.of(), expectedSecret);

    when(apiClient.<V1Secret>execute(any(), any())).thenReturn(dummyResponse);
    when(apiClient.escapeString(namespace)).thenCallRealMethod();

    final var actual = underTest.createNamespacedSecret(namespace, secret, null, null, null, null);
    assertThat(actual).isEqualTo(expectedSecret);
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void testCreateNamespacedSecretAfterRetry() throws ApiException {
    final var namespace = "namespace";
    final var secret = new V1Secret().metadata(new V1ObjectMeta().name("test").namespace(namespace));

    final var expectedSecret = new V1Secret();
    final var dummyResponse = new ApiResponse<>(200, Map.of(), expectedSecret);

    when(apiClient.<V1Secret>execute(any(), any())).thenThrow(new ApiException()).thenReturn(dummyResponse);
    when(apiClient.escapeString(namespace)).thenCallRealMethod();

    final var actual = underTest.createNamespacedSecret(namespace, secret, null, null, null, null);
    assertThat(actual).isEqualTo(expectedSecret);
  }

  @Test(expected = ApiException.class)
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void testCreateNamespacedSecretAfterRetryFails() throws ApiException {
    final var namespace = "namespace";
    final var secret = new V1Secret().metadata(new V1ObjectMeta().name("test").namespace(namespace));

    when(apiClient.<V1Secret>execute(any(), any()))
        .thenThrow(new ApiException())
        .thenThrow(new ApiException())
        .thenThrow(new ApiException());
    when(apiClient.escapeString(namespace)).thenCallRealMethod();

    underTest.createNamespacedSecret(namespace, secret, null, null, null, null);
  }
}
