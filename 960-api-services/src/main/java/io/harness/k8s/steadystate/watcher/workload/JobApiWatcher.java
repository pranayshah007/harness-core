/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.k8s.steadystate.watcher.workload;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.threading.Morpheus.sleep;

import static java.time.Duration.ofSeconds;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.KubernetesTaskException;
import io.harness.exception.NestedExceptionUtils;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.k8s.exception.KubernetesExceptionExplanation;
import io.harness.k8s.exception.KubernetesExceptionHints;
import io.harness.k8s.exception.KubernetesExceptionMessages;
import io.harness.k8s.model.KubernetesResourceId;
import io.harness.k8s.steadystate.model.K8ApiResponseDTO;
import io.harness.k8s.steadystate.model.K8sStatusWatchDTO;
import io.harness.k8s.steadystate.statusviewer.JobStatusViewer;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.BatchV1Api;
import io.kubernetes.client.openapi.models.V1Job;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
@OwnedBy(CDP)
public class JobApiWatcher implements WorkloadWatcher {
  @Inject private JobStatusViewer statusViewer;
  @Override
  public boolean watchRolloutStatus(K8sStatusWatchDTO k8SStatusWatchDTO, KubernetesResourceId workload,
      LogCallback executionLogCallback) throws Exception {
    return watchJobStatus(
        k8SStatusWatchDTO.getApiClient(), workload, executionLogCallback, k8SStatusWatchDTO.isErrorFrameworkEnabled());
  }

  private boolean watchJobStatus(ApiClient apiClient, KubernetesResourceId workload, LogCallback executionLogCallback,
      boolean errorFrameworkEnabled) throws Exception {
    Preconditions.checkNotNull(apiClient, "K8s API Client cannot be null.");
    BatchV1Api batchV1Api = new BatchV1Api(apiClient);
    while (true) {
      try {
        V1Job job = batchV1Api.readNamespacedJob(workload.getName(), workload.getNamespace(), null);
        K8ApiResponseDTO response = statusViewer.extractRolloutStatus(job);
        executionLogCallback.saveExecutionLog(response.getMessage());

        if (response.isFailed()) {
          if (errorFrameworkEnabled) {
            throw NestedExceptionUtils.hintWithExplanationException(
                KubernetesExceptionHints.WAIT_FOR_STEADY_STATE_JOB_FAILED,
                KubernetesExceptionExplanation.WAIT_FOR_STEADY_STATE_JOB_FAILED,
                new KubernetesTaskException(
                    KubernetesExceptionMessages.WAIT_FOR_STEADY_STATE_FAILED + response.getMessage()));
          }
          return false;
        }

        if (response.isDone()) {
          return true;
        }
        sleep(ofSeconds(5));
      } catch (ApiException e) {
        ApiException ex = ExceptionMessageSanitizer.sanitizeException(e);
        String errorMessage =
            String.format("Failed to watch rollout status for workload [%s]. ", workload.kindNameRef())
            + ExceptionUtils.getMessage(ex);
        log.error(errorMessage, ex);
        executionLogCallback.saveExecutionLog(errorMessage, LogLevel.ERROR);
        if (errorFrameworkEnabled) {
          throw e;
        }
        return false;
      }
    }
  }
}
