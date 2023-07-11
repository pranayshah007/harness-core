/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.service.core.litek8s;

import static io.harness.delegate.service.core.util.LabelHelper.getTaskGroupSelector;

import io.harness.delegate.service.core.util.ApiExceptionLogger;

import com.google.inject.Inject;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RequiredArgsConstructor(onConstructor_ = @Inject)
@Slf4j
public class InfraCleaner {
  private final CoreV1Api coreApi;

  public void deleteSecrets(final String taskGroupId, final String namespace) throws ApiException {
    final var secrets = coreApi.listNamespacedSecret(
        namespace, null, null, null, null, getTaskGroupSelector(taskGroupId), null, null, null, null, null);
    log.info("Deleting {} secrets for task group {}", secrets.getItems().size(), taskGroupId);
    secrets.getItems().forEach(secret -> {
      try {
        coreApi.deleteNamespacedSecret(secret.getMetadata().getName(), namespace, null, null, 0, null, null, null);
      } catch (ApiException e) {
        log.error("Failed to delete secret {}. {}", secret.getMetadata().getName(), ApiExceptionLogger.format(e), e);
      } catch (Exception e) {
        log.error("Failed to delete secret {}", secret.getMetadata().getName(), e);
      }
    });
  }

  public void deletePod(final String taskGroupId, final String namespace) throws ApiException {
    final var pods = coreApi.listNamespacedPod(
        namespace, null, null, null, null, getTaskGroupSelector(taskGroupId), null, null, null, null, null);
    log.info("Deleting {} pods for task group {}", pods.getItems().size(), taskGroupId);
    pods.getItems().forEach(pod -> {
      try {
        coreApi.deleteNamespacedPod(pod.getMetadata().getName(), namespace, null, null, 0, null, null, null);
      } catch (ApiException e) {
        log.error("Failed to delete pod {}. {}", pod.getMetadata().getName(), ApiExceptionLogger.format(e), e);
      } catch (Exception e) {
        log.error("Failed to delete pod {}", pod.getMetadata().getName(), e);
      }
    });
  }

  public void deleteServiceEndpoint(final String taskGroupId, final String namespace) throws ApiException {
    final var services = coreApi.listNamespacedService(
        namespace, null, null, null, null, getTaskGroupSelector(taskGroupId), null, null, null, null, null);
    log.info("Deleting {} services for task group {}", services.getItems().size(), taskGroupId);
    services.getItems().forEach(service -> {
      try {
        coreApi.deleteNamespacedService(service.getMetadata().getName(), namespace, null, null, 0, null, null, null);
      } catch (ApiException e) {
        log.error("Failed to delete service {}. {}", service.getMetadata().getName(), ApiExceptionLogger.format(e), e);
      } catch (Exception e) {
        log.error("Failed to delete service {}", service.getMetadata().getName(), e);
      }
    });
  }
}
