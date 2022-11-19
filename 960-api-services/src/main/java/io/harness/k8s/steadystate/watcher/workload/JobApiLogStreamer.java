/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.k8s.steadystate.watcher.workload;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.threading.Morpheus.sleep;

import static java.lang.String.format;
import static java.time.Duration.ofSeconds;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.KubernetesApiTaskException;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.k8s.model.KubernetesResourceId;
import io.harness.k8s.steadystate.model.K8ApiResponseDTO;
import io.harness.k8s.steadystate.model.K8sStatusWatchDTO;
import io.harness.k8s.steadystate.statusviewer.JobStatusViewer;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.BatchV1Api;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.*;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
@OwnedBy(CDP)
public class JobApiLogStreamer implements K8sLogStreamer {
  private static final String K8S_SELECTOR_FORMAT = "%s=%s";
  private static final String K8S_SELECTOR_DELIMITER = ",";
  @Inject private JobStatusViewer statusViewer;
  @Inject @Named("k8sLogStreamer") private ScheduledExecutorService k8sLogStreamer;

  @Override
  public boolean streamLogs(K8sStatusWatchDTO k8SStatusWatchDTO, KubernetesResourceId workload,
      LogCallback executionLogCallback) throws ApiException {
    ApiClient apiClient = k8SStatusWatchDTO.getApiClient();
    boolean errorFrameworkEnabled = k8SStatusWatchDTO.isErrorFrameworkEnabled();
    Preconditions.checkNotNull(apiClient, "K8s API Client cannot be null.");
    Configuration.setDefaultApiClient(apiClient);
    CoreV1Api coreV1Api = new CoreV1Api(apiClient);
    BatchV1Api batchV1Api = new BatchV1Api(apiClient);
    List<Future<?>> podLogFutures = new ArrayList<>();
    try {
      V1Job job = batchV1Api.readNamespacedJob(workload.getName(), workload.getNamespace(), null);
      String labelSelector = getLabelSelector(job);
      V1PodList podList = coreV1Api.listNamespacedPod(
          workload.getNamespace(), null, null, null, null, labelSelector, null, null, null, null, false);

      for (V1Pod pod : podList.getItems()) {
        PodApiLogStreamerTask podLogStreamerTask = new PodApiLogStreamerTask(coreV1Api, pod, executionLogCallback);
        Future<?> future = k8sLogStreamer.submit(podLogStreamerTask);
        podLogFutures.add(future);
      }

      while (true) {
        V1Job k8sJob = batchV1Api.readNamespacedJobStatus(workload.getName(), workload.getNamespace(), null);
        K8ApiResponseDTO response = statusViewer.extractRolloutStatus(k8sJob);
        if (response.isFailed()) {
          // grace period
          sleep(Duration.ofSeconds(2));
          return false;
        }
        if (response.isDone()) {
          // grace period
          sleep(Duration.ofSeconds(2));
          return true;
        }
        sleep(ofSeconds(5));
      }
    } catch (ApiException e) {
      ApiException ex = ExceptionMessageSanitizer.sanitizeException(e);
      String errorMessage = String.format("Failed to stream job logs for workload [%s]. ", workload.kindNameRef())
          + ExceptionUtils.getMessage(ex);
      log.error(errorMessage, ex);
      executionLogCallback.saveExecutionLog(errorMessage, LogLevel.ERROR);
      if (errorFrameworkEnabled) {
        throw e;
      }
      return false;
    } finally {
      destroyRunning(podLogFutures);
    }
  }

  public void destroyRunning(List<Future<?>> futures) {
    for (Future<?> future : futures) {
      boolean cancelled = future.cancel(true);
      if (!cancelled) {
        log.warn("Failed to cancel k8s log streaming thread ref.");
        future.cancel(true);
      }
    }
  }

  private String getLabelSelector(V1Job job) {
    if (job.getMetadata() == null) {
      throw new KubernetesApiTaskException("Could not find job metadata");
    }
    if (job.getMetadata().getLabels() == null) {
      throw new KubernetesApiTaskException(
          String.format("Could not find labels for job [%s]", job.getMetadata().getName()));
    }
    Map<String, String> labels = job.getMetadata().getLabels();
    return labels.entrySet()
        .stream()
        .filter(entry -> entry.getKey().equals("job-name"))
        .map(entry -> format(K8S_SELECTOR_FORMAT, entry.getKey(), entry.getValue()))
        .collect(Collectors.joining(K8S_SELECTOR_DELIMITER));
  }
}
