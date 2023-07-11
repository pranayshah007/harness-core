/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.service.core.litek8s;

import static io.harness.rule.OwnerRule.MARKO;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodList;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1SecretList;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1ServiceList;
import java.util.Arrays;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class InfraCleanerTest {
  private static final String NAMESPACE = "namespace";
  private static final String LABEL_SELECTOR = "harness.io/task-group=task-group-id";
  private static final String TASK_GROUP_ID = "task-group-id";

  private InfraCleaner underTest;

  @Mock private CoreV1Api coreApi;

  @Before
  public void setUp() throws Exception {
    underTest = new InfraCleaner(coreApi);
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void testDeleteSecrets() throws ApiException {
    final String secretName1 = "secretName1";
    final String secretName2 = "secretName2";

    when(coreApi.listNamespacedSecret(NAMESPACE, null, null, null, null, LABEL_SELECTOR, null, null, null, null, null))
        .thenReturn(createSecrets(secretName1, secretName2));

    underTest.deleteSecrets(TASK_GROUP_ID, NAMESPACE);

    verify(coreApi).listNamespacedSecret(
        NAMESPACE, null, null, null, null, LABEL_SELECTOR, null, null, null, null, null);
    verify(coreApi).deleteNamespacedSecret(secretName1, NAMESPACE, null, null, 0, null, null, null);
    verify(coreApi).deleteNamespacedSecret(secretName2, NAMESPACE, null, null, 0, null, null, null);
    verifyNoMoreInteractions(coreApi);
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void testDeleteSecretsWhenNoSecretsFound() throws ApiException {
    when(coreApi.listNamespacedSecret(NAMESPACE, null, null, null, null, LABEL_SELECTOR, null, null, null, null, null))
        .thenReturn(createSecrets());

    underTest.deleteSecrets(TASK_GROUP_ID, NAMESPACE);

    verify(coreApi).listNamespacedSecret(
        NAMESPACE, null, null, null, null, LABEL_SELECTOR, null, null, null, null, null);
    verifyNoMoreInteractions(coreApi);
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void testDeleteSecretsWhenApiException() throws ApiException {
    when(coreApi.listNamespacedSecret(NAMESPACE, null, null, null, null, LABEL_SELECTOR, null, null, null, null, null))
        .thenThrow(ApiException.class);

    assertThatThrownBy(() -> underTest.deleteSecrets(TASK_GROUP_ID, NAMESPACE)).isInstanceOf(ApiException.class);
    verify(coreApi).listNamespacedSecret(
        NAMESPACE, null, null, null, null, LABEL_SELECTOR, null, null, null, null, null);
    verifyNoMoreInteractions(coreApi);
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void testDeletePod() throws ApiException {
    final String podName = "podName";

    when(coreApi.listNamespacedPod(NAMESPACE, null, null, null, null, LABEL_SELECTOR, null, null, null, null, null))
        .thenReturn(createPods(podName));

    underTest.deletePod(TASK_GROUP_ID, NAMESPACE);

    verify(coreApi).listNamespacedPod(NAMESPACE, null, null, null, null, LABEL_SELECTOR, null, null, null, null, null);
    verify(coreApi).deleteNamespacedPod(podName, NAMESPACE, null, null, 0, null, null, null);
    verifyNoMoreInteractions(coreApi);
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void testDeletePodsWhenNoPodFound() throws ApiException {
    when(coreApi.listNamespacedPod(NAMESPACE, null, null, null, null, LABEL_SELECTOR, null, null, null, null, null))
        .thenReturn(createPods());

    underTest.deletePod(TASK_GROUP_ID, NAMESPACE);

    verify(coreApi).listNamespacedPod(NAMESPACE, null, null, null, null, LABEL_SELECTOR, null, null, null, null, null);
    verifyNoMoreInteractions(coreApi);
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void testDeletePodsWhenApiException() throws ApiException {
    when(coreApi.listNamespacedPod(NAMESPACE, null, null, null, null, LABEL_SELECTOR, null, null, null, null, null))
        .thenThrow(ApiException.class);

    assertThatThrownBy(() -> underTest.deletePod(TASK_GROUP_ID, NAMESPACE)).isInstanceOf(ApiException.class);
    verify(coreApi).listNamespacedPod(NAMESPACE, null, null, null, null, LABEL_SELECTOR, null, null, null, null, null);
    verifyNoMoreInteractions(coreApi);
  }
  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void testDeleteServices() throws ApiException {
    final String serviceName1 = "serviceName1";
    final String serviceName2 = "serviceName2";

    when(coreApi.listNamespacedService(NAMESPACE, null, null, null, null, LABEL_SELECTOR, null, null, null, null, null))
        .thenReturn(createServices(serviceName1, serviceName2));

    underTest.deleteServiceEndpoint(TASK_GROUP_ID, NAMESPACE);

    verify(coreApi).listNamespacedService(
        NAMESPACE, null, null, null, null, LABEL_SELECTOR, null, null, null, null, null);
    verify(coreApi).deleteNamespacedService(serviceName1, NAMESPACE, null, null, 0, null, null, null);
    verify(coreApi).deleteNamespacedService(serviceName2, NAMESPACE, null, null, 0, null, null, null);
    verifyNoMoreInteractions(coreApi);
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void testDeleteServicesWhenNoServicesFound() throws ApiException {
    when(coreApi.listNamespacedService(NAMESPACE, null, null, null, null, LABEL_SELECTOR, null, null, null, null, null))
        .thenReturn(createServices());

    underTest.deleteServiceEndpoint(TASK_GROUP_ID, NAMESPACE);

    verify(coreApi).listNamespacedService(
        NAMESPACE, null, null, null, null, LABEL_SELECTOR, null, null, null, null, null);
    verifyNoMoreInteractions(coreApi);
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void testDeleteServicesWhenApiException() throws ApiException {
    when(coreApi.listNamespacedService(NAMESPACE, null, null, null, null, LABEL_SELECTOR, null, null, null, null, null))
        .thenThrow(ApiException.class);

    assertThatThrownBy(() -> underTest.deleteServiceEndpoint(TASK_GROUP_ID, NAMESPACE))
        .isInstanceOf(ApiException.class);
    verify(coreApi).listNamespacedService(
        NAMESPACE, null, null, null, null, LABEL_SELECTOR, null, null, null, null, null);
    verifyNoMoreInteractions(coreApi);
  }

  private V1SecretList createSecrets(final String... names) {
    return new V1SecretList().items(
        Arrays.stream(names)
            .map(name -> new V1Secret().metadata(new V1ObjectMeta().name(name).namespace(NAMESPACE)))
            .collect(toList()));
  }

  private V1PodList createPods(final String... names) {
    return new V1PodList().items(
        Arrays.stream(names)
            .map(name -> new V1Pod().metadata(new V1ObjectMeta().name(name).namespace(NAMESPACE)))
            .collect(toList()));
  }

  private V1ServiceList createServices(final String... names) {
    return new V1ServiceList().items(
        Arrays.stream(names)
            .map(name -> new V1Service().metadata(new V1ObjectMeta().name(name).namespace(NAMESPACE)))
            .collect(toList()));
  }
}
