/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.k8s.steadystate.watcher.workload;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.k8s.steadystate.model.K8sStatusWatchDTO;
import io.harness.k8s.steadystate.watcher.client.K8sEventPredicate;
import io.harness.k8s.steadystate.watcher.client.K8sWatchClient;
import io.harness.k8s.steadystate.watcher.client.K8sWatchClientFactory;
import io.harness.logging.LogCallback;
import io.harness.supplier.ThrowingSupplier;

import io.github.resilience4j.retry.Retry;
import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.util.Watch;
import java.lang.reflect.Type;
import lombok.SneakyThrows;
import org.junit.Before;
import org.junit.Rule;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@OwnedBy(HarnessTeam.CDP)
public abstract class ApiWatcherTestBase<T extends KubernetesObject> extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock protected LogCallback logCallback;
  @Mock protected ApiClient apiClient;
  @Mock protected Retry retry;
  @Mock protected K8sWatchClient watchClient;
  @Mock protected K8sWatchClientFactory watchClientFactory;

  @Before
  public void baseSetup() {
    doReturn(watchClient).when(watchClientFactory).create(any(ApiClient.class), any(Retry.class));
  }

  @SneakyThrows
  protected void prepareWatchClient(Type type, Watch.Response<T> eventResponse) {
    doAnswer(invocation -> ((K8sEventPredicate<T>) invocation.getArgument(2)).test(eventResponse))
        .when(watchClient)
        .waitOnCondition(eq(type), any(ThrowingSupplier.class), any(K8sEventPredicate.class));
  }

  protected K8sStatusWatchDTO createTestDTO() {
    return K8sStatusWatchDTO.builder().apiClient(apiClient).retry(retry).build();
  }

  protected Watch.Response<T> createAddedWatchResponse(T object) {
    return createWatchResponse("ADDED", object);
  }

  protected Watch.Response<T> createModifiedWatchResponse(T object) {
    return createWatchResponse("MODIFIED", object);
  }

  protected Watch.Response<T> createDeletedWatchResponse(T object) {
    return createWatchResponse("DELETED", object);
  }

  protected Watch.Response<T> createUnknownWatchResponse(T object) {
    return createWatchResponse("UNKNOWN", object);
  }

  protected Watch.Response<T> createWatchResponse(String type, T object) {
    return new Watch.Response<>(type, object);
  }
}
