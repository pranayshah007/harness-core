/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.service.core.apiclient;

import static java.lang.String.format;
import static java.net.HttpURLConnection.HTTP_CONFLICT;

import io.harness.delegate.service.core.util.ApiExceptionLogger;

import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1DeleteOptions;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodList;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1SecretList;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1ServiceList;
import io.kubernetes.client.openapi.models.V1Status;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.FailsafeException;
import net.jodah.failsafe.RetryPolicy;
import net.jodah.failsafe.function.CheckedSupplier;

@Slf4j
public class CoreV1ApiWithRetry extends CoreV1Api {
  private static final Duration DEFAULT_RETRY_DELAY = Duration.ofSeconds(2);
  private static final int MAX_ATTEMPTS = 3;
  private static final String POD_LIST_FAILED_ATTEMPT =
      "Failed to list pods in namespace %s. Retrying, attempt: {}. {}";
  private static final String POD_LIST_FAILURE = "Failed to list pods in namespace %s after retrying {} times. {}";
  private static final String POD_CREATE_FAILED_ATTEMPT = "Failed to create pod %s. Retrying, attempt: {}. {}";
  private static final String POD_CREATE_FAILURE = "Failed to create pod %s after retrying {} times. {}";
  private static final String POD_DELETE_FAILED_ATTEMPT =
      "Failed to delete pod %s in namespace %s. Retrying, attempt: {}. {}";
  private static final String POD_DELETE_FAILURE =
      "Failed to delete pod %s in namespace %s after retrying {} times. {}";

  private static final String SECRET_LIST_FAILED_ATTEMPT =
      "Failed to list secrets in namespace %s. Retrying, attempt: {}. {}";
  private static final String SECRET_LIST_FAILURE =
      "Failed to list secrets in namespace %s after retrying {} times. {}";
  private static final String SECRET_CREATE_FAILED_ATTEMPT = "Failed to create secret %s. Retrying, attempt: {}. {}";
  private static final String SECRET_CREATE_FAILURE = "Failed to create secret %s after retrying {} times. {}";
  private static final String SECRET_DELETE_FAILED_ATTEMPT =
      "Failed to delete secret %s in namespace %s. Retrying, attempt: {}. {}";
  private static final String SECRET_DELETE_FAILURE =
      "Failed to delete secret %s in namespace %s after retrying {} times. {}";

  private static final String SERVICE_LIST_FAILED_ATTEMPT =
      "Failed to list services in namespace %s. Retrying, attempt: {}. {}";
  private static final String SERVICE_LIST_FAILURE =
      "Failed to list services in namespace %s after retrying {} times. {}";
  private static final String SERVICE_CREATE_FAILED_ATTEMPT = "Failed to create service %s. Retrying, attempt: {}. {}";
  private static final String SERVICE_CREATE_FAILURE = "Failed to create service %s after retrying {} times. {}";
  private static final String SERVICE_DELETE_FAILED_ATTEMPT =
      "Failed to delete service %s in namespace %s. Retrying, attempt: {}. {}";
  private static final String SERVICE_DELETE_FAILURE =
      "Failed to delete service %s in namespace %s after retrying {} times. {}";

  private final Duration delay;

  public CoreV1ApiWithRetry(final ApiClient apiClient) {
    this(apiClient, DEFAULT_RETRY_DELAY);
  }

  /*
   * This constructor is used mostly for testing purposes. Tests don't need to wait for retries, so they can run much
   * faster.
   */
  protected CoreV1ApiWithRetry(final ApiClient apiClient, final Duration delay) {
    super(apiClient);
    if (delay == null || delay.isNegative()) {
      throw new IllegalArgumentException("Delay must be a positive value.");
    }
    this.delay = delay;
  }

  @Override
  public V1PodList listNamespacedPod(final String namespace, final String pretty, final Boolean allowWatchBookmarks,
      final String _continue, final String fieldSelector, final String labelSelector, final Integer limit,
      final String resourceVersion, final String resourceVersionMatch, final Integer timeoutSeconds,
      final Boolean watch) throws ApiException {
    final var policy = getRetryPolicy(format(POD_LIST_FAILED_ATTEMPT, namespace), format(POD_LIST_FAILURE, namespace));
    final CheckedSupplier<V1PodList> retryable = ()
        -> super.listNamespacedPod(namespace, pretty, allowWatchBookmarks, _continue, fieldSelector, labelSelector,
            limit, resourceVersion, resourceVersionMatch, timeoutSeconds, watch);

    return retryableCall(policy, retryable);
  }

  @Override
  public V1Pod createNamespacedPod(final String namespace, final V1Pod body, final String pretty, final String dryRun,
      final String fieldManager, final String fieldValidation) throws ApiException {
    final var policy = getRetryPolicy(format(POD_CREATE_FAILED_ATTEMPT, body.getMetadata().getName()),
        format(POD_CREATE_FAILURE, body.getMetadata().getName()));
    final CheckedSupplier<V1Pod> retryable =
        () -> createOrReplaceNamespacedPod(namespace, body, pretty, dryRun, fieldManager, fieldValidation);

    return retryableCall(policy, retryable);
  }

  @Override
  public V1Pod deleteNamespacedPod(final String name, final String namespace, final String pretty, final String dryRun,
      final Integer gracePeriodSeconds, final Boolean orphanDependents, final String propagationPolicy,
      final V1DeleteOptions body) throws ApiException {
    final var policy =
        getRetryPolicy(format(POD_DELETE_FAILED_ATTEMPT, name, namespace), format(POD_DELETE_FAILURE, name, namespace));
    final CheckedSupplier<V1Pod> retryable = ()
        -> super.deleteNamespacedPod(
            name, namespace, pretty, dryRun, gracePeriodSeconds, orphanDependents, propagationPolicy, body);

    return retryableCall(policy, retryable);
  }

  @Override
  public V1SecretList listNamespacedSecret(final String namespace, final String pretty,
      final Boolean allowWatchBookmarks, final String _continue, final String fieldSelector, final String labelSelector,
      final Integer limit, final String resourceVersion, final String resourceVersionMatch,
      final Integer timeoutSeconds, final Boolean watch) throws ApiException {
    final var policy =
        getRetryPolicy(format(SECRET_LIST_FAILED_ATTEMPT, namespace), format(SECRET_LIST_FAILURE, namespace));
    final CheckedSupplier<V1SecretList> retryable = ()
        -> super.listNamespacedSecret(namespace, pretty, allowWatchBookmarks, _continue, fieldSelector, labelSelector,
            limit, resourceVersion, resourceVersionMatch, timeoutSeconds, watch);

    return retryableCall(policy, retryable);
  }

  @Override
  public V1Secret createNamespacedSecret(final String namespace, final V1Secret body, final String pretty,
      final String dryRun, final String fieldManager, final String fieldValidation) throws ApiException {
    final var policy = getRetryPolicy(format(SECRET_CREATE_FAILED_ATTEMPT, body.getMetadata().getName()),
        format(SECRET_CREATE_FAILURE, body.getMetadata().getName()));
    final CheckedSupplier<V1Secret> retryable =
        () -> createOrReplaceNamespacedSecret(namespace, body, pretty, dryRun, fieldManager, fieldValidation);

    return retryableCall(policy, retryable);
  }

  @Override
  public V1Status deleteNamespacedSecret(final String name, final String namespace, final String pretty,
      final String dryRun, final Integer gracePeriodSeconds, final Boolean orphanDependents,
      final String propagationPolicy, final V1DeleteOptions body) throws ApiException {
    final var policy = getRetryPolicy(
        format(SECRET_DELETE_FAILED_ATTEMPT, name, namespace), format(SECRET_DELETE_FAILURE, name, namespace));
    final CheckedSupplier<V1Status> retryable = ()
        -> super.deleteNamespacedSecret(
            name, namespace, pretty, dryRun, gracePeriodSeconds, orphanDependents, propagationPolicy, body);

    return retryableCall(policy, retryable);
  }

  @Override
  public V1ServiceList listNamespacedService(final String namespace, final String pretty,
      final Boolean allowWatchBookmarks, final String _continue, final String fieldSelector, final String labelSelector,
      final Integer limit, final String resourceVersion, final String resourceVersionMatch,
      final Integer timeoutSeconds, final Boolean watch) throws ApiException {
    final var policy =
        getRetryPolicy(format(SERVICE_LIST_FAILED_ATTEMPT, namespace), format(SERVICE_LIST_FAILURE, namespace));
    final CheckedSupplier<V1ServiceList> retryable = ()
        -> super.listNamespacedService(namespace, pretty, allowWatchBookmarks, _continue, fieldSelector, labelSelector,
            limit, resourceVersion, resourceVersionMatch, timeoutSeconds, watch);

    return retryableCall(policy, retryable);
  }

  @Override
  public V1Service createNamespacedService(final String namespace, final V1Service body, final String pretty,
      final String dryRun, final String fieldManager, final String fieldValidation) throws ApiException {
    final var policy = getRetryPolicy(format(SERVICE_CREATE_FAILED_ATTEMPT, body.getMetadata().getName()),
        format(SERVICE_CREATE_FAILURE, body.getMetadata().getName()));
    final CheckedSupplier<V1Service> retryable =
        () -> createOrReplaceNamespacedService(namespace, body, pretty, dryRun, fieldManager, fieldValidation);

    return retryableCall(policy, retryable);
  }

  @Override
  public V1Service deleteNamespacedService(final String name, final String namespace, final String pretty,
      final String dryRun, final Integer gracePeriodSeconds, final Boolean orphanDependents,
      final String propagationPolicy, final V1DeleteOptions body) throws ApiException {
    final var policy = getRetryPolicy(
        format(SERVICE_DELETE_FAILED_ATTEMPT, name, namespace), format(SERVICE_DELETE_FAILURE, name, namespace));
    final CheckedSupplier<V1Service> retryable = ()
        -> super.deleteNamespacedService(
            name, namespace, pretty, dryRun, gracePeriodSeconds, orphanDependents, propagationPolicy, body);

    return retryableCall(policy, retryable);
  }

  private V1Pod createOrReplaceNamespacedPod(final String namespace, final V1Pod body, final String pretty,
      final String dryRun, final String fieldManager, final String fieldValidation) throws ApiException {
    final ApiSupplier<V1Pod> create =
        () -> super.createNamespacedPod(namespace, body, pretty, dryRun, fieldManager, fieldValidation);
    final ApiSupplier<V1Pod> delete =
        () -> super.deleteNamespacedPod(body.getMetadata().getName(), namespace, pretty, dryRun, 0, null, null, null);

    return createOrReplace(create, delete);
  }

  private V1Secret createOrReplaceNamespacedSecret(final String namespace, final V1Secret body, final String pretty,
      final String dryRun, final String fieldManager, final String fieldValidation) throws ApiException {
    try {
      return super.createNamespacedSecret(namespace, body, pretty, dryRun, fieldManager, fieldValidation);
    } catch (final ApiException e) {
      if (e.getCode() != HTTP_CONFLICT) {
        throw e;
      }
      log.warn("Secret1 {} already exists, replacing it", body.getMetadata().getName());
      super.deleteNamespacedSecret(body.getMetadata().getName(), namespace, pretty, dryRun, 0, null, null, null);
      return super.createNamespacedSecret(namespace, body, pretty, dryRun, fieldManager, fieldValidation);
    }
  }

  private V1Service createOrReplaceNamespacedService(final String namespace, final V1Service body, final String pretty,
      final String dryRun, final String fieldManager, final String fieldValidation) throws ApiException {
    final ApiSupplier<V1Service> create =
        () -> super.createNamespacedService(namespace, body, pretty, dryRun, fieldManager, fieldValidation);
    final ApiSupplier<V1Service> delete = ()
        -> super.deleteNamespacedService(body.getMetadata().getName(), namespace, pretty, dryRun, 0, null, null, null);

    return createOrReplace(create, delete);
  }

  private <T extends KubernetesObject> T createOrReplace(final ApiSupplier<T> create, final ApiSupplier<T> delete)
      throws ApiException {
    try {
      return create.get();
    } catch (final ApiException e) {
      if (e.getCode() != HTTP_CONFLICT) {
        throw e;
      }
      final var resource = delete.get();
      log.warn("{} {} already exists, replacing it", resource.getKind(), resource.getMetadata().getName());
      return create.get();
    }
  }

  private <T> T retryableCall(final RetryPolicy<Object> retryPolicy, final CheckedSupplier<T> retryable)
      throws ApiException {
    try {
      return Failsafe.with(retryPolicy).get(retryable);
    } catch (FailsafeException fsException) {
      if (fsException.getCause() instanceof ApiException) {
        throw(ApiException) fsException.getCause();
      } else {
        throw fsException;
      }
    }
  }

  private RetryPolicy<Object> getRetryPolicy(final String failedAttemptMessage, final String failureMessage) {
    final var retryPolicy = new RetryPolicy<>()
                                .handle(ApiException.class)
                                .withMaxAttempts(MAX_ATTEMPTS)
                                .onFailedAttempt(event
                                    -> log.warn(failedAttemptMessage, event.getAttemptCount(),
                                        logThrowable(event.getLastFailure()), event.getLastFailure()))
                                .onFailure(event
                                    -> log.error(failureMessage, event.getAttemptCount(),
                                        logThrowable(event.getFailure()), event.getFailure()));

    if (delay != null && !delay.isZero()) {
      retryPolicy.withDelay(delay);
    }
    return retryPolicy;
  }

  private String logThrowable(final Throwable e) {
    if (e instanceof ApiException) {
      return ApiExceptionLogger.format((ApiException) e);
    }
    return e.getMessage();
  }

  private interface ApiSupplier<T> {
    T get() throws ApiException;
  }
}
