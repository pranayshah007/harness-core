/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.k8s.steadystate.watcher.workload;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.threading.Morpheus.sleep;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.KubernetesApiTaskException;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.k8s.model.KubernetesResourceId;
import io.harness.k8s.steadystate.model.K8sStatusWatchDTO;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;

import io.kubernetes.client.PodLogs;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1Pod;
import java.io.*;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(CDP)
public class PodApiLogStreamerTask implements Runnable {
  private final CoreV1Api coreV1Api;
  private final V1Pod pod;
  private final LogCallback logCallback;

  public PodApiLogStreamerTask(CoreV1Api coreV1Api, V1Pod pod, LogCallback logCallback) {
    this.coreV1Api = coreV1Api;
    this.pod = pod;
    this.logCallback = logCallback;
  }

  @Override
  public void run() {
    try {
      waitUntilPodIsPending(coreV1Api, pod);
      logCallback.saveExecutionLog(String.format("Pod %s ready to stream logs", pod.getMetadata().getName()));
      PodLogs logs = new PodLogs();
      try (InputStream stream = logs.streamNamespacedPodLog(pod)) {
        byte[] data = new byte[128];
        StringBuilder logLine = new StringBuilder();
        int offset;
        while (true) {
          log.error("STILL RUNNING POD LOG STREAMER");
          offset = stream.read(data);
          if (offset != -1) {
            for (byte b : data) {
              char c = (char) (b & 0xFF);
              if (c == '\n') {
                String logMessage = format("%s: %s", pod.getMetadata().getName(), logLine);
                log.error("streamLogs 9 : {}", logMessage);
                logCallback.saveExecutionLog(logMessage);
                logLine.setLength(0);
              } else if (c != '\u0000') {
                logLine.append(c);
              }
            }
          }
        }
      } catch (IOException e) {
        IOException ex = ExceptionMessageSanitizer.sanitizeException(e);
        String errorMessage = "Failed to close Kubernetes log stream." + ExceptionUtils.getMessage(ex);
        log.error(errorMessage, ex);
      }
    } catch (ApiException e) {
      ApiException ex = ExceptionMessageSanitizer.sanitizeException(e);
      String errorMessage = String.format("Failed to stream logs for pod %s. ", pod.getMetadata().getName())
          + ExceptionUtils.getMessage(ex);
      log.error(errorMessage, ex);
      logCallback.saveExecutionLog(errorMessage, LogLevel.ERROR);
    }
  }

  private void waitUntilPodIsPending(CoreV1Api coreV1Api, V1Pod pod) throws ApiException {
    if (pod.getMetadata() == null) {
      throw new KubernetesApiTaskException("Could not find pod metadata");
    }
    if (pod.getStatus() == null || pod.getStatus().getPhase() == null) {
      throw new KubernetesApiTaskException(
          String.format("Could not find pod status for [%s]", pod.getMetadata().getName()));
    }
    while (pod.getStatus().getPhase().equals("Pending")) {
      pod = coreV1Api.readNamespacedPodStatus(pod.getMetadata().getName(), pod.getMetadata().getNamespace(), null);
      sleep(Duration.ofSeconds(1));
    }
  }
}
