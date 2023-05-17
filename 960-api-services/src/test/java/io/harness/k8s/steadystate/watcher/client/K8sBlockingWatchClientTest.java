/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.k8s.steadystate.watcher.client;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.ABOSII;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.retry.RetryHelper;
import io.harness.rule.Owner;
import io.harness.supplier.ThrowingSupplier;

import com.google.gson.reflect.TypeToken;
import io.github.resilience4j.retry.Retry;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.JSON;
import io.kubernetes.client.openapi.models.V1Deployment;
import io.kubernetes.client.openapi.models.V1DeploymentBuilder;
import io.kubernetes.client.util.Watch;
import java.lang.reflect.Type;
import java.net.SocketTimeoutException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.SneakyThrows;
import okhttp3.Call;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.BufferedSource;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@OwnedBy(CDP)
public class K8sBlockingWatchClientTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock private ApiClient apiClient;
  @Mock private Call call;

  private final Type v1DeploymentType = new TypeToken<Watch.Response<V1Deployment>>() {}.getType();
  private final ThrowingSupplier<Call> callSupplier = () -> call;
  private final JSON serializer = new JSON();
  private final Retry retry = RetryHelper.getExponentialRetry("test-retry", new Class[] {SocketTimeoutException.class});

  @Before
  public void setup() {
    doReturn(serializer).when(apiClient).getJSON();
  }

  @Test
  @SneakyThrows
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testWaitOnCondition() {
    final K8sWatchClient client = new K8sBlockingWatchClient(apiClient);

    doReturn(createSimpleResponse()).when(call).execute();
    boolean result = client.waitOnCondition(v1DeploymentType, callSupplier, event -> true);

    assertThat(result).isTrue();
  }

  @Test
  @SneakyThrows
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testWaitOnConditionWithRetriesAndFailedCondition() {
    final AtomicInteger callCount = new AtomicInteger();
    final K8sWatchClient client = new K8sBlockingWatchClient(apiClient, retry);

    doAnswer(i -> {
      callCount.incrementAndGet();
      return createSimpleResponse();
    })
        .when(call)
        .execute();

    assertThatThrownBy(() -> client.waitOnCondition(v1DeploymentType, callSupplier, event -> {
      throw new RuntimeException("failed condition");
    })).hasStackTraceContaining("failed condition");
    // Condition fail shouldn't be a reason for retry;
    assertThat(callCount.get()).isEqualTo(1);
  }

  @Test
  @SneakyThrows
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testWaitOnConditionMultipleEvents() {
    final AtomicInteger count = new AtomicInteger(4);
    final K8sWatchClient client = new K8sBlockingWatchClient(apiClient);

    doAnswer(invocation -> {
      Thread.sleep(50);
      return createSimpleResponse();
    })
        .when(call)
        .execute();

    boolean result = client.waitOnCondition(v1DeploymentType, callSupplier, event -> count.decrementAndGet() <= 0);
    assertThat(result).isTrue();
  }

  @Test
  @SneakyThrows
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testWaitOnConditionWithRetries() {
    final AtomicInteger retryCount = new AtomicInteger();
    final K8sWatchClient client = new K8sBlockingWatchClient(apiClient, retry);

    doAnswer(invocation -> {
      int count = retryCount.incrementAndGet();
      if (count < 3) {
        throw new SocketTimeoutException();
      }

      return createSimpleResponse();
    })
        .when(call)
        .execute();

    boolean result = client.waitOnCondition(v1DeploymentType, callSupplier, event -> true);
    assertThat(result).isTrue();
    assertThat(retryCount.get()).isEqualTo(3);
  }

  @Test
  @SneakyThrows
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testWaitOnConditionWithRetriesFailed() {
    final AtomicInteger retryCount = new AtomicInteger();
    final K8sWatchClient client = new K8sBlockingWatchClient(apiClient, retry);

    doAnswer(invocation -> {
      retryCount.incrementAndGet();
      throw new SocketTimeoutException();
    })
        .when(call)
        .execute();

    assertThatThrownBy(() -> client.waitOnCondition(v1DeploymentType, callSupplier, event -> true))
        .hasStackTraceContaining(SocketTimeoutException.class.getName());
    assertThat(retryCount.get()).isEqualTo(3);
  }

  @Test
  @SneakyThrows
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testWaitOnConditionFailedScheduling() {
    final ExecutorService mockExecutor = mock(ExecutorService.class);
    final K8sWatchClient client = new K8sBlockingWatchClient(apiClient, retry, mockExecutor);

    doThrow(new RejectedExecutionException("Failed to schedule")).when(mockExecutor).submit(any(Callable.class));

    doReturn(createSimpleResponse()).when(call).execute();
    boolean result = client.waitOnCondition(v1DeploymentType, callSupplier, event -> true);
    assertThat(result).isTrue();
    verify(mockExecutor).submit(any(Callable.class));
  }

  @SneakyThrows
  private Response createSimpleResponse() {
    ResponseBody mockBody = mock(ResponseBody.class);
    String bodyResponse = getWatchResponseJson();
    BufferedSource mockSource = mock(BufferedSource.class);

    Response response = new Response.Builder()
                            .protocol(Protocol.HTTP_1_1)
                            .message("test message")
                            .request(new Request.Builder()
                                         .url("https://test.cluster.dv")

                                         .build())
                            .body(mockBody)
                            .code(200)
                            .build();

    doReturn(bodyResponse).when(mockBody).string();
    doReturn(mockSource).when(mockBody).source();
    doReturn(bodyResponse).when(mockSource).readUtf8Line();
    doReturn(bodyResponse).when(mockSource).readUtf8();

    return response;
  }

  private String getWatchResponseJson() {
    return serializer.serialize(new Watch.Response<>("ADDED", new V1DeploymentBuilder().build()));
  }
}