/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.k8s.steadystate.watcher.workload;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.KubernetesCliTaskRuntimeException;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.k8s.model.KubernetesResourceId;
import io.harness.k8s.steadystate.model.K8sStatusWatchDTO;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;

import io.kubernetes.client.openapi.ApiException;
import java.io.InterruptedIOException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.CDP)
public abstract class AbstractWorkloadWatcher implements WorkloadWatcher {
  @Override
  public boolean watchRolloutStatus(K8sStatusWatchDTO k8SStatusWatchDTO, KubernetesResourceId workload,
      LogCallback executionLogCallback) throws Exception {
    try {
      return watchRolloutStatusInternal(k8SStatusWatchDTO, workload, executionLogCallback);
    } catch (ApiException e) {
      ApiException ex = ExceptionMessageSanitizer.sanitizeException(e);
      String errorMessage = String.format("Failed to watch rollout status for workload [%s]. ", workload.kindNameRef())
          + ExceptionUtils.getMessage(ex);
      log.error(errorMessage, ex);
      executionLogCallback.saveExecutionLog(errorMessage, LogLevel.ERROR);
      if (k8SStatusWatchDTO.isErrorFrameworkEnabled()) {
        throw e;
      }

      return false;
    } catch (KubernetesCliTaskRuntimeException e) {
      KubernetesCliTaskRuntimeException ex = ExceptionMessageSanitizer.sanitizeException(e);
      String errorMessage = String.format("Steady state check for workload [%s] did not succeed due to error: %s",
          workload.kindNameRef(), ex.getMessage());
      log.error(errorMessage, ex);
      executionLogCallback.saveExecutionLog(errorMessage, LogLevel.ERROR);
      if (k8SStatusWatchDTO.isErrorFrameworkEnabled()) {
        throw e;
      }

      return false;
    } catch (RuntimeException e) {
      if (e.getCause() != null && e.getCause() instanceof InterruptedIOException) {
        log.warn("Kubernetes watch was aborted.", e);
        Thread.currentThread().interrupt();
        return false;
      }

      log.error("Runtime exception during Kubernetes watch.", e);
      throw e;
    } catch (InterruptedException e) {
      log.warn("Workload watch was interrupted");
      if (k8SStatusWatchDTO.isErrorFrameworkEnabled()) {
        throw e;
      }

      Thread.currentThread().interrupt();
      return false;
    } catch (Exception e) {
      log.warn("Unhandled exception thrown", e);
      if (k8SStatusWatchDTO.isErrorFrameworkEnabled()) {
        throw e;
      }

      return false;
    }
  }

  protected abstract boolean watchRolloutStatusInternal(K8sStatusWatchDTO k8SStatusWatchDTO,
      KubernetesResourceId workload, LogCallback executionLogCallback) throws Exception;
}
