/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.service.core.apiclient;

import static io.harness.rule.OwnerRule.MARKO;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import com.google.common.reflect.TypeToken;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.ApiResponse;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodList;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1SecretList;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1ServiceList;
import io.kubernetes.client.openapi.models.V1Status;
import java.lang.reflect.Type;
import java.time.Duration;
import java.util.Map;
import org.assertj.core.api.Condition;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class CoreV1ApiWithRetryTest {
  private static final String NAMESPACE = "namespace";
  private static final String NAME = "test";
  private static final String LABEL_SELECTOR = "harness=label";
  private static final Condition<ApiException> NOT_FOUND = new Condition<>(e -> e.getCode() == 404, "code not found");
  private static final Type POD_TYPE = new TypeToken<V1Pod>() {}.getType();
  private static final Type PODLIST_TYPE = new TypeToken<V1PodList>() {}.getType();
  private static final Type SECRET_TYPE = new TypeToken<V1Secret>() {}.getType();
  private static final Type SECRETLIST_TYPE = new TypeToken<V1SecretList>() {}.getType();
  private static final Type STATUS_TYPE = new TypeToken<V1Status>() {}.getType();
  private static final Type SERVICE_TYPE = new TypeToken<V1Service>() {}.getType();
  private static final Type SERVICELIST_TYPE = new TypeToken<V1ServiceList>() {}.getType();

  private CoreV1ApiWithRetry underTest;
  @Mock private ApiClient apiClient;

  @Before
  public void setUp() {
    underTest = new CoreV1ApiWithRetry(apiClient, Duration.ofSeconds(0));
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void testCoreV1ApiFailsWhenDurationNegative() {
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> new CoreV1ApiWithRetry(apiClient, Duration.ofSeconds(-1)));
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void testCoreV1ApiFailsWhenDurationNull() {
    assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> new CoreV1ApiWithRetry(apiClient, null));
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void testListNamespacedPods() throws ApiException {
    final var expectedPods = podList();
    final var dummyResponse = new ApiResponse<>(200, Map.of(), expectedPods);

    when(apiClient.<V1PodList>execute(any(), eq(PODLIST_TYPE))).thenReturn(dummyResponse);
    when(apiClient.escapeString(NAMESPACE)).thenCallRealMethod();

    final var actual =
        underTest.listNamespacedPod(NAMESPACE, null, null, null, null, LABEL_SELECTOR, null, null, null, null, null);
    assertThat(actual).isEqualTo(expectedPods);
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void testListNamespacedPodsAfterRetry() throws ApiException {
    final var expectedPods = podList();
    final var dummyResponse = new ApiResponse<>(200, Map.of(), expectedPods);

    when(apiClient.<V1PodList>execute(any(), eq(PODLIST_TYPE))).thenThrow(new ApiException()).thenReturn(dummyResponse);
    when(apiClient.escapeString(NAMESPACE)).thenCallRealMethod();

    final var actual =
        underTest.listNamespacedPod(NAMESPACE, null, null, null, null, LABEL_SELECTOR, null, null, null, null, null);
    assertThat(actual).isEqualTo(expectedPods);
  }

  @Test(expected = ApiException.class)
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void testListNamespacedPodsFails() throws ApiException {
    when(apiClient.<V1PodList>execute(any(), eq(PODLIST_TYPE)))
        .thenThrow(new ApiException())
        .thenThrow(new ApiException())
        .thenThrow(new ApiException());
    when(apiClient.escapeString(NAMESPACE)).thenCallRealMethod();

    underTest.listNamespacedPod(NAMESPACE, null, null, null, null, LABEL_SELECTOR, null, null, null, null, null);
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void testCreateNamespacedPod() throws ApiException {
    final var pod = pod();

    final var expectedPod = new V1Pod();
    final var dummyResponse = new ApiResponse<>(200, Map.of(), expectedPod);

    when(apiClient.<V1Pod>execute(any(), eq(POD_TYPE))).thenReturn(dummyResponse);
    when(apiClient.escapeString(NAMESPACE)).thenCallRealMethod();

    final var actual = underTest.createNamespacedPod(NAMESPACE, pod, null, null, null, null);
    assertThat(actual).isEqualTo(expectedPod);
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void testCreateNamespacedPodWhenExists() throws ApiException {
    final var pod = pod();

    final var expectedPod = new V1Pod();
    final var deleteResponse = new ApiResponse<>(200, Map.of(), pod);
    final var createResponse = new ApiResponse<>(200, Map.of(), expectedPod);

    when(apiClient.<V1Pod>execute(any(), eq(POD_TYPE)))
        .thenThrow(new ApiException(409, "exists"))
        .thenReturn(deleteResponse)
        .thenReturn(createResponse);
    when(apiClient.escapeString(NAMESPACE)).thenCallRealMethod();
    when(apiClient.escapeString(NAME)).thenCallRealMethod();

    final var actual = underTest.createNamespacedPod(NAMESPACE, pod, null, null, null, null);
    assertThat(actual).isEqualTo(expectedPod);
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void testCreateNamespacedPodAfterRetry() throws ApiException {
    final var pod = pod();

    final var expectedPod = new V1Pod();
    final var dummyResponse = new ApiResponse<>(200, Map.of(), expectedPod);

    when(apiClient.<V1Pod>execute(any(), eq(POD_TYPE))).thenThrow(new ApiException()).thenReturn(dummyResponse);
    when(apiClient.escapeString(NAMESPACE)).thenCallRealMethod();

    final var actual = underTest.createNamespacedPod(NAMESPACE, pod, null, null, null, null);
    assertThat(actual).isEqualTo(expectedPod);
  }

  @Test(expected = ApiException.class)
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void testCreateNamespacedPodAfterRetryFails() throws ApiException {
    final var pod = pod();

    when(apiClient.<V1Pod>execute(any(), eq(POD_TYPE)))
        .thenThrow(new ApiException())
        .thenThrow(new ApiException())
        .thenThrow(new ApiException());
    when(apiClient.escapeString(NAMESPACE)).thenCallRealMethod();

    underTest.createNamespacedPod(NAMESPACE, pod, null, null, null, null);
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void testDeleteNamespacedPod() throws ApiException {
    final var expected = pod();
    final var dummyResponse = new ApiResponse<>(200, Map.of(), expected);

    when(apiClient.<V1Pod>execute(any(), eq(POD_TYPE))).thenReturn(dummyResponse);
    when(apiClient.escapeString(NAMESPACE)).thenCallRealMethod();
    when(apiClient.escapeString(NAME)).thenCallRealMethod();

    final var actual = underTest.deleteNamespacedPod(NAME, NAMESPACE, null, null, null, null, null, null);
    assertThat(actual).isEqualTo(expected);
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void testDeleteNamespacedPodWhenNotFound() throws ApiException {
    when(apiClient.<V1Pod>execute(any(), eq(POD_TYPE)))
        .thenThrow(new ApiException(404, "not found"))
        .thenThrow(new ApiException(404, "not found"))
        .thenThrow(new ApiException(404, "not found"));
    when(apiClient.escapeString(NAMESPACE)).thenCallRealMethod();
    when(apiClient.escapeString(NAME)).thenCallRealMethod();

    assertThatExceptionOfType(ApiException.class)
        .isThrownBy(() -> underTest.deleteNamespacedPod(NAME, NAMESPACE, null, null, null, null, null, null))
        .is(NOT_FOUND);
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void testDeleteNamespacedPodAfterRetry() throws ApiException {
    final var expected = pod();
    final var dummyResponse = new ApiResponse<>(200, Map.of(), expected);

    when(apiClient.<V1Pod>execute(any(), eq(POD_TYPE))).thenThrow(new ApiException()).thenReturn(dummyResponse);
    when(apiClient.escapeString(NAMESPACE)).thenCallRealMethod();
    when(apiClient.escapeString(NAME)).thenCallRealMethod();

    final var actual = underTest.deleteNamespacedPod(NAME, NAMESPACE, null, null, null, null, null, null);
    assertThat(actual).isEqualTo(expected);
  }

  @Test(expected = ApiException.class)
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void testDeleteNamespacedPodAfterRetryFails() throws ApiException {
    when(apiClient.<V1Pod>execute(any(), eq(POD_TYPE)))
        .thenThrow(new ApiException())
        .thenThrow(new ApiException())
        .thenThrow(new ApiException());
    when(apiClient.escapeString(NAMESPACE)).thenCallRealMethod();
    when(apiClient.escapeString(NAME)).thenCallRealMethod();

    underTest.deleteNamespacedPod(NAME, NAMESPACE, null, null, null, null, null, null);
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void testListNamespacedSecrets() throws ApiException {
    final var expected = secretList();
    final var dummyResponse = new ApiResponse<>(200, Map.of(), expected);

    when(apiClient.<V1SecretList>execute(any(), eq(SECRETLIST_TYPE))).thenReturn(dummyResponse);
    when(apiClient.escapeString(NAMESPACE)).thenCallRealMethod();

    final var actual =
        underTest.listNamespacedSecret(NAMESPACE, null, null, null, null, LABEL_SELECTOR, null, null, null, null, null);
    assertThat(actual).isEqualTo(expected);
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void testListNamespacedSecretsAfterRetry() throws ApiException {
    final var expected = secretList();
    final var dummyResponse = new ApiResponse<>(200, Map.of(), expected);

    when(apiClient.<V1SecretList>execute(any(), eq(SECRETLIST_TYPE)))
        .thenThrow(new ApiException())
        .thenReturn(dummyResponse);
    when(apiClient.escapeString(NAMESPACE)).thenCallRealMethod();

    final var actual =
        underTest.listNamespacedSecret(NAMESPACE, null, null, null, null, LABEL_SELECTOR, null, null, null, null, null);
    assertThat(actual).isEqualTo(expected);
  }

  @Test(expected = ApiException.class)
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void testListNamespacedSecretsFails() throws ApiException {
    when(apiClient.<V1SecretList>execute(any(), eq(SECRETLIST_TYPE)))
        .thenThrow(new ApiException())
        .thenThrow(new ApiException())
        .thenThrow(new ApiException());
    when(apiClient.escapeString(NAMESPACE)).thenCallRealMethod();

    underTest.listNamespacedSecret(NAMESPACE, null, null, null, null, LABEL_SELECTOR, null, null, null, null, null);
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void testCreateNamespacedSecret() throws ApiException {
    final var secret = secret();

    final var expectedSecret = new V1Secret();
    final var dummyResponse = new ApiResponse<>(200, Map.of(), expectedSecret);

    when(apiClient.<V1Secret>execute(any(), eq(SECRET_TYPE))).thenReturn(dummyResponse);
    when(apiClient.escapeString(NAMESPACE)).thenCallRealMethod();

    final var actual = underTest.createNamespacedSecret(NAMESPACE, secret, null, null, null, null);
    assertThat(actual).isEqualTo(expectedSecret);
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void testCreateNamespacedSecretWhenExists() throws ApiException {
    final var secret = secret();
    final var status = status();

    final var expected = new V1Secret();
    final var deleteResponse = new ApiResponse<>(200, Map.of(), status);
    final var createResponse = new ApiResponse<>(200, Map.of(), expected);

    when(apiClient.<V1Status>execute(any(), eq(STATUS_TYPE))).thenReturn(deleteResponse);
    when(apiClient.<V1Secret>execute(any(), eq(SECRET_TYPE)))
        .thenThrow(new ApiException(409, "exists"))
        .thenReturn(createResponse);

    when(apiClient.escapeString(NAMESPACE)).thenCallRealMethod();
    when(apiClient.escapeString(NAME)).thenCallRealMethod();

    final var actual = underTest.createNamespacedSecret(NAMESPACE, secret, null, null, null, null);
    assertThat(actual).isEqualTo(expected);
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void testCreateNamespacedSecretAfterRetry() throws ApiException {
    final var secret = secret();

    final var expectedSecret = new V1Secret();
    final var dummyResponse = new ApiResponse<>(200, Map.of(), expectedSecret);

    when(apiClient.<V1Secret>execute(any(), eq(SECRET_TYPE))).thenThrow(new ApiException()).thenReturn(dummyResponse);
    when(apiClient.escapeString(NAMESPACE)).thenCallRealMethod();

    final var actual = underTest.createNamespacedSecret(NAMESPACE, secret, null, null, null, null);
    assertThat(actual).isEqualTo(expectedSecret);
  }

  @Test(expected = ApiException.class)
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void testCreateNamespacedSecretAfterRetryFails() throws ApiException {
    final var secret = secret();

    when(apiClient.<V1Secret>execute(any(), eq(SECRET_TYPE)))
        .thenThrow(new ApiException())
        .thenThrow(new ApiException())
        .thenThrow(new ApiException());
    when(apiClient.escapeString(NAMESPACE)).thenCallRealMethod();

    underTest.createNamespacedSecret(NAMESPACE, secret, null, null, null, null);
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void testDeleteNamespacedSecret() throws ApiException {
    final var expected = status();

    final var dummyResponse = new ApiResponse<>(200, Map.of(), expected);

    when(apiClient.<V1Status>execute(any(), eq(STATUS_TYPE))).thenReturn(dummyResponse);
    when(apiClient.escapeString(NAMESPACE)).thenCallRealMethod();
    when(apiClient.escapeString(NAME)).thenCallRealMethod();

    final var actual = underTest.deleteNamespacedSecret(NAME, NAMESPACE, null, null, null, null, null, null);
    assertThat(actual).isEqualTo(expected);
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void testDeleteNamespacedSecretWhenNotFound() throws ApiException {
    when(apiClient.<V1Status>execute(any(), eq(STATUS_TYPE)))
        .thenThrow(new ApiException(404, "not found"))
        .thenThrow(new ApiException(404, "not found"))
        .thenThrow(new ApiException(404, "not found"));
    when(apiClient.escapeString(NAMESPACE)).thenCallRealMethod();
    when(apiClient.escapeString(NAME)).thenCallRealMethod();

    assertThatExceptionOfType(ApiException.class)
        .isThrownBy(() -> underTest.deleteNamespacedSecret(NAME, NAMESPACE, null, null, null, null, null, null))
        .is(NOT_FOUND);
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void testDeleteNamespacedSecretAfterRetry() throws ApiException {
    final var expected = status();
    final var dummyResponse = new ApiResponse<>(200, Map.of(), expected);

    when(apiClient.<V1Status>execute(any(), eq(STATUS_TYPE))).thenThrow(new ApiException()).thenReturn(dummyResponse);
    when(apiClient.escapeString(NAMESPACE)).thenCallRealMethod();
    when(apiClient.escapeString(NAME)).thenCallRealMethod();

    final var actual = underTest.deleteNamespacedSecret(NAME, NAMESPACE, null, null, null, null, null, null);
    assertThat(actual).isEqualTo(expected);
  }

  @Test(expected = ApiException.class)
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void testDeleteNamespacedSecretAfterRetryFails() throws ApiException {
    when(apiClient.<V1Status>execute(any(), eq(STATUS_TYPE)))
        .thenThrow(new ApiException())
        .thenThrow(new ApiException())
        .thenThrow(new ApiException());
    when(apiClient.escapeString(NAMESPACE)).thenCallRealMethod();
    when(apiClient.escapeString(NAME)).thenCallRealMethod();

    underTest.deleteNamespacedSecret(NAME, NAMESPACE, null, null, null, null, null, null);
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void testListNamespacedService() throws ApiException {
    final var expected = serviceList();
    final var dummyResponse = new ApiResponse<>(200, Map.of(), expected);

    when(apiClient.<V1ServiceList>execute(any(), eq(SERVICELIST_TYPE))).thenReturn(dummyResponse);
    when(apiClient.escapeString(NAMESPACE)).thenCallRealMethod();

    final var actual = underTest.listNamespacedService(
        NAMESPACE, null, null, null, null, LABEL_SELECTOR, null, null, null, null, null);
    assertThat(actual).isEqualTo(expected);
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void testListNamespacedServicesAfterRetry() throws ApiException {
    final var expected = serviceList();
    final var dummyResponse = new ApiResponse<>(200, Map.of(), expected);

    when(apiClient.<V1ServiceList>execute(any(), eq(SERVICELIST_TYPE)))
        .thenThrow(new ApiException())
        .thenReturn(dummyResponse);
    when(apiClient.escapeString(NAMESPACE)).thenCallRealMethod();

    final var actual = underTest.listNamespacedService(
        NAMESPACE, null, null, null, null, LABEL_SELECTOR, null, null, null, null, null);
    assertThat(actual).isEqualTo(expected);
  }

  @Test(expected = ApiException.class)
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void testListNamespacedServicesFails() throws ApiException {
    when(apiClient.<V1ServiceList>execute(any(), eq(SERVICELIST_TYPE)))
        .thenThrow(new ApiException())
        .thenThrow(new ApiException())
        .thenThrow(new ApiException());
    when(apiClient.escapeString(NAMESPACE)).thenCallRealMethod();

    underTest.listNamespacedService(NAMESPACE, null, null, null, null, LABEL_SELECTOR, null, null, null, null, null);
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void testCreateNamespacedService() throws ApiException {
    final var secret = service();

    final var expectedService = new V1Service();
    final var dummyResponse = new ApiResponse<>(200, Map.of(), expectedService);

    when(apiClient.<V1Service>execute(any(), eq(SERVICE_TYPE))).thenReturn(dummyResponse);
    when(apiClient.escapeString(NAMESPACE)).thenCallRealMethod();

    final var actual = underTest.createNamespacedService(NAMESPACE, secret, null, null, null, null);
    assertThat(actual).isEqualTo(expectedService);
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void testCreateNamespacedServiceWhenExists() throws ApiException {
    final var service = service();

    final var expectedService = new V1Service();
    final var deleteResponse = new ApiResponse<>(200, Map.of(), service);
    final var createResponse = new ApiResponse<>(200, Map.of(), expectedService);

    when(apiClient.<V1Service>execute(any(), eq(SERVICE_TYPE)))
        .thenThrow(new ApiException(409, "exists"))
        .thenReturn(deleteResponse)
        .thenReturn(createResponse);
    when(apiClient.escapeString(NAMESPACE)).thenCallRealMethod();
    when(apiClient.escapeString(NAME)).thenCallRealMethod();

    final var actual = underTest.createNamespacedService(NAMESPACE, service, null, null, null, null);
    assertThat(actual).isEqualTo(expectedService);
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void testCreateNamespacedServiceAfterRetry() throws ApiException {
    final var service = service();

    final var expectedService = new V1Service();
    final var dummyResponse = new ApiResponse<>(200, Map.of(), expectedService);

    when(apiClient.<V1Service>execute(any(), eq(SERVICE_TYPE))).thenThrow(new ApiException()).thenReturn(dummyResponse);
    when(apiClient.escapeString(NAMESPACE)).thenCallRealMethod();

    final var actual = underTest.createNamespacedService(NAMESPACE, service, null, null, null, null);
    assertThat(actual).isEqualTo(expectedService);
  }

  @Test(expected = ApiException.class)
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void testCreateNamespacedServiceAfterRetryFails() throws ApiException {
    final var service = service();

    when(apiClient.<V1Secret>execute(any(), eq(SERVICE_TYPE)))
        .thenThrow(new ApiException())
        .thenThrow(new ApiException())
        .thenThrow(new ApiException());
    when(apiClient.escapeString(NAMESPACE)).thenCallRealMethod();

    underTest.createNamespacedService(NAMESPACE, service, null, null, null, null);
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void testDeleteNamespacedService() throws ApiException {
    final var expected = service();
    final var dummyResponse = new ApiResponse<>(200, Map.of(), expected);

    when(apiClient.<V1Service>execute(any(), eq(SERVICE_TYPE))).thenReturn(dummyResponse);
    when(apiClient.escapeString(NAMESPACE)).thenCallRealMethod();
    when(apiClient.escapeString(NAME)).thenCallRealMethod();

    final var actual = underTest.deleteNamespacedService(NAME, NAMESPACE, null, null, null, null, null, null);
    assertThat(actual).isEqualTo(expected);
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void testDeleteNamespacedServiceWhenNotFound() throws ApiException {
    when(apiClient.<V1Service>execute(any(), eq(SERVICE_TYPE)))
        .thenThrow(new ApiException(404, "not found"))
        .thenThrow(new ApiException(404, "not found"))
        .thenThrow(new ApiException(404, "not found"));
    when(apiClient.escapeString(NAMESPACE)).thenCallRealMethod();
    when(apiClient.escapeString(NAME)).thenCallRealMethod();

    assertThatExceptionOfType(ApiException.class)
        .isThrownBy(() -> underTest.deleteNamespacedService(NAME, NAMESPACE, null, null, null, null, null, null))
        .is(NOT_FOUND);
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void testDeleteNamespacedServiceAfterRetry() throws ApiException {
    final var expected = service();
    final var dummyResponse = new ApiResponse<>(200, Map.of(), expected);

    when(apiClient.<V1Service>execute(any(), eq(SERVICE_TYPE))).thenThrow(new ApiException()).thenReturn(dummyResponse);
    when(apiClient.escapeString(NAMESPACE)).thenCallRealMethod();
    when(apiClient.escapeString(NAME)).thenCallRealMethod();

    final var actual = underTest.deleteNamespacedService(NAME, NAMESPACE, null, null, null, null, null, null);
    assertThat(actual).isEqualTo(expected);
  }

  @Test(expected = ApiException.class)
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void testDeleteNamespacedServiceAfterRetryFails() throws ApiException {
    when(apiClient.<V1Service>execute(any(), eq(SERVICE_TYPE)))
        .thenThrow(new ApiException())
        .thenThrow(new ApiException())
        .thenThrow(new ApiException());
    when(apiClient.escapeString(NAMESPACE)).thenCallRealMethod();
    when(apiClient.escapeString(NAME)).thenCallRealMethod();

    underTest.deleteNamespacedService(NAME, NAMESPACE, null, null, null, null, null, null);
  }

  private V1Pod pod() {
    return new V1Pod().kind("Pod").metadata(new V1ObjectMeta().name(NAME).namespace(NAMESPACE));
  }

  private V1PodList podList() {
    return new V1PodList().addItemsItem(pod());
  }

  private V1Secret secret() {
    return new V1Secret().kind("Secret").metadata(new V1ObjectMeta().name(NAME).namespace(NAMESPACE));
  }

  private V1Status status() {
    return new V1Status().code(200).message("OK");
  }

  private V1SecretList secretList() {
    return new V1SecretList().addItemsItem(secret());
  }

  private V1Service service() {
    return new V1Service().kind("Service").metadata(new V1ObjectMeta().name(NAME).namespace(NAMESPACE));
  }

  private V1ServiceList serviceList() {
    return new V1ServiceList().addItemsItem(service());
  }
}
