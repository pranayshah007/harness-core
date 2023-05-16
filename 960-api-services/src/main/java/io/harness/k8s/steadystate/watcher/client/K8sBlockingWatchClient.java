/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.k8s.steadystate.watcher.client;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.supplier.ThrowingSupplier;

import io.github.resilience4j.retry.Retry;
import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.util.Watch;
import io.vavr.CheckedFunction0;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;

@Slf4j
@OwnedBy(CDP)
public class K8sBlockingWatchClient implements K8sWatchClient {
  private final ExecutorService executor;

  private final ApiClient apiClient;
  private final Retry retryConfig;

  K8sBlockingWatchClient(ApiClient apiClient) {
    this.apiClient = apiClient;
    this.retryConfig = null;
    this.executor = Executors.newSingleThreadExecutor();
  }

  K8sBlockingWatchClient(ApiClient apiClient, Retry retryConfig) {
    this.apiClient = apiClient;
    this.retryConfig = retryConfig;
    this.executor = Executors.newSingleThreadExecutor();
  }

  K8sBlockingWatchClient(ApiClient apiClient, Retry retryConfig, ExecutorService executor) {
    this.apiClient = apiClient;
    this.retryConfig = retryConfig;
    this.executor = Objects.requireNonNullElseGet(executor, Executors::newSingleThreadExecutor);
  }

  @Override
  public <T extends KubernetesObject> boolean waitOnCondition(
      Type type, ThrowingSupplier<Call> callSupplier, K8sEventPredicate<T> condition) throws Exception {
    Future<Boolean> future = null;
    try {
      // Due to an issue in the JDK socket read native method can prevent the current running request thread
      // to be interrupted. To prevent hanging current task thread we're running the watch request in a different
      // thread and if the current thread, even if the request running thread is unresponding to interrupt it
      // will not block the task thread
      future = executor.submit(() -> waitInternal(type, callSupplier, condition));
      return future.get();
    } catch (InterruptedException e) {
      future.cancel(true);
      throw e;
    } catch (RejectedExecutionException e) {
      // Even if we can't make this call in a different thread we can still try to execute request in curent thread
      log.error("Failed to schedule a new task execution, fallback to default behavior", e);
      return waitInternal(type, callSupplier, condition);
    }
  }

  private <T extends KubernetesObject> boolean waitInternal(
      Type type, ThrowingSupplier<Call> callSupplier, K8sEventPredicate<T> condition) throws Exception {
    try {
      if (retryConfig == null) {
        return executeWait(type, callSupplier, condition);
      }

      CheckedFunction0<Boolean> retrySupplier =
          Retry.decorateCheckedSupplier(retryConfig, () -> executeWait(type, callSupplier, condition));
      return retrySupplier.apply();
    } catch (Exception e) {
      throw e;
    } catch (Throwable t) {
      // Callable from java function library interface don't handle throwable
      throw new RuntimeException("Failed to watch workload", t);
    }
  }

  private <T extends KubernetesObject> boolean executeWait(
      Type type, ThrowingSupplier<Call> callSupplier, K8sEventPredicate<T> condition) throws Throwable {
    while (!Thread.currentThread().isInterrupted()) {
      try (Watch<T> watch = Watch.createWatch(apiClient, callSupplier.get(), type)) {
        for (Watch.Response<T> event : watch) {
          if (condition.test(event)) {
            return true;
          }
        }
      } catch (IOException e) {
        IOException ex = ExceptionMessageSanitizer.sanitizeException(e);
        String errorMessage = "Failed to close Kubernetes watch." + ExceptionUtils.getMessage(ex);
        log.error(errorMessage, ex);
        return false;
      }
    }

    return false;
  }
}
