/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.service.core.apiclient;

import static java.net.HttpURLConnection.HTTP_CONFLICT;

import io.harness.delegate.service.core.util.ApiExceptionLogger;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1Secret;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.FailsafeException;
import net.jodah.failsafe.RetryPolicy;

@Slf4j
public class CoreV1ApiWithRetry extends CoreV1Api {
  private static final Duration RETRY_DELAY = Duration.ofSeconds(2);
  private static final int MAX_ATTEMPTS = 3;

  public CoreV1ApiWithRetry(final ApiClient apiClient) {
    super(apiClient);
  }

  @Override
  public V1Pod createNamespacedPod(final String namespace, final V1Pod body, final String pretty, final String dryRun,
      final String fieldManager, final String fieldValidation) throws ApiException {
    try {
      final var retryPolicy =
          getRetryPolicy("Failed to create pod " + body.getMetadata().getName() + ". Retrying, attempt: {}. {}",
              "Failed to create pod " + body.getMetadata().getName() + " after retrying {} times. {}");
      return Failsafe.with(retryPolicy)
          .get(() -> createOrReplaceNamespacedPod(namespace, body, pretty, dryRun, fieldManager, fieldValidation));
    } catch (FailsafeException fsException) {
      if (fsException.getCause() instanceof ApiException) {
        throw(ApiException) fsException.getCause();
      } else {
        throw fsException;
      }
    }
  }

  @Override
  public V1Secret createNamespacedSecret(final String namespace, final V1Secret body, final String pretty,
      final String dryRun, final String fieldManager, final String fieldValidation) throws ApiException {
    try {
      final var retryPolicy =
          getRetryPolicy("Failed to create secret " + body.getMetadata().getName() + ". Retrying, attempt: {}",
              "Failed to create secret " + body.getMetadata().getName() + " after retrying {} times");
      return Failsafe.with(retryPolicy)
          .get(() -> createOrReplaceNamespacedSecret(namespace, body, pretty, dryRun, fieldManager, fieldValidation));
    } catch (FailsafeException fsException) {
      if (fsException.getCause() instanceof ApiException) {
        throw(ApiException) fsException.getCause();
      } else {
        throw fsException;
      }
    }
  }

  private V1Pod createOrReplaceNamespacedPod(final String namespace, final V1Pod body, final String pretty,
      final String dryRun, final String fieldManager, final String fieldValidation) throws ApiException {
    try {
      return super.createNamespacedPod(namespace, body, pretty, dryRun, fieldManager, fieldValidation);
    } catch (final ApiException e) {
      if (e.getCode() != HTTP_CONFLICT) {
        throw e;
      }
      log.warn("Pod {} already exists, replacing it", body.getMetadata().getName());
      super.deleteNamespacedPod(body.getMetadata().getName(), namespace, pretty, dryRun, 0, null, null, null);
      return super.createNamespacedPod(namespace, body, pretty, dryRun, fieldManager, fieldValidation);
    }
  }

  private V1Secret createOrReplaceNamespacedSecret(final String namespace, final V1Secret body, final String pretty,
      final String dryRun, final String fieldManager, final String fieldValidation) throws ApiException {
    try {
      return super.createNamespacedSecret(namespace, body, pretty, dryRun, fieldManager, fieldValidation);
    } catch (final ApiException e) {
      if (e.getCode() != HTTP_CONFLICT) {
        throw e;
      }
      log.warn("Secret {} already exists, replacing it", body.getMetadata().getName());
      super.deleteNamespacedSecret(body.getMetadata().getName(), namespace, pretty, dryRun, 0, null, null, null);
      return super.createNamespacedSecret(namespace, body, pretty, dryRun, fieldManager, fieldValidation);
    }
  }

  private RetryPolicy<Object> getRetryPolicy(final String failedAttemptMessage, final String failureMessage) {
    return new RetryPolicy<>()
        .handle(ApiException.class)
        .withDelay(RETRY_DELAY)
        .withMaxAttempts(MAX_ATTEMPTS)
        .onFailedAttempt(event
            -> log.warn(failedAttemptMessage, event.getAttemptCount(), logThrowable(event.getLastFailure()),
                event.getLastFailure()))
        .onFailure(event
            -> log.error(
                failureMessage, event.getAttemptCount(), logThrowable(event.getFailure()), event.getFailure()));
  }

  private String logThrowable(final Throwable e) {
    if (e instanceof ApiException) {
      return ApiExceptionLogger.format((ApiException) e);
    }
    return e.getMessage();
  }
}
