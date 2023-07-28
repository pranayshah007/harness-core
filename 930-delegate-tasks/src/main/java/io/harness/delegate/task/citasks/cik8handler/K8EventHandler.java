/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.citasks.cik8handler;
import static io.harness.annotations.dev.HarnessTeam.CI;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.k8s.apiclient.ApiClientFactory;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.logging.LogLevel;
import io.harness.logstreaming.LogLine;

import com.google.gson.reflect.TypeToken;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.CoreV1Event;
import io.kubernetes.client.util.Watch;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_AMI_ASG, HarnessModuleComponent.CDS_DASHBOARD})
@Slf4j
@Singleton
@OwnedBy(CI)
public class K8EventHandler {
  @Inject private ApiClientFactory apiClientFactory;

  private static Integer watchTimeout = 8 * 60;

  public Watch<CoreV1Event> startAsyncPodEventWatch(
      KubernetesConfig kubernetesConfig, String namespace, String pod, ILogStreamingTaskClient logStreamingTaskClient) {
    String fieldSelector = String.format("involvedObject.name=%s,involvedObject.kind=Pod", pod);
    try {
      Watch<CoreV1Event> watch = createWatch(kubernetesConfig, namespace, fieldSelector);
      new Thread(() -> {
        try {
          logWatchEvents(watch, logStreamingTaskClient);
        } catch (IOException e) {
          log.warn("error in watching pod events", e);
        }
      }).start();
      return watch;

    } catch (ApiException e) {
      streamLogLine(
          logStreamingTaskClient, LogLevel.ERROR, String.format("failed to watch pod event: %s", e.getMessage()));
      log.error("failed to create watch on pod events", e);
      return null;
    }
  }

  public void stopEventWatch(Watch<CoreV1Event> watch) {
    ExecutorService executorService = Executors.newSingleThreadExecutor();
    executorService.submit(() -> {
      try {
        watch.close();
      } catch (IOException e) {
        log.warn("failed to stop event watch", e);
      }
    });
    try {
      executorService.shutdown();
      executorService.awaitTermination(30, TimeUnit.SECONDS);
    } catch (Exception e) {
      log.warn("failed to stop event watch", e);
    }
  }

  private Watch<CoreV1Event> createWatch(KubernetesConfig kubernetesConfig, String namespace, String fieldSelector)
      throws ApiException {
    ApiClient apiClient = apiClientFactory.getClient(kubernetesConfig);
    CoreV1Api coreV1Api = new CoreV1Api(apiClient);
    return Watch.createWatch(apiClient,
        coreV1Api.listNamespacedEventCall(
            namespace, null, false, null, fieldSelector, null, null, null, null, watchTimeout, Boolean.TRUE, null),
        new TypeToken<Watch.Response<CoreV1Event>>() {}.getType());
  }

  private void logWatchEvents(Watch<CoreV1Event> watch, ILogStreamingTaskClient logStreamingTaskClient)
      throws IOException {
    try {
      for (Watch.Response<CoreV1Event> item : watch) {
        if (item != null && item.object != null && isNotEmpty(item.object.getMessage())) {
          streamLogLine(logStreamingTaskClient, getLogLevel(item.object.getType()), item.object.getMessage());
          log.info(
              "{}: Event- {}, Reason - {}", item.object.getType(), item.object.getMessage(), item.object.getReason());
        }
      }
    } finally {
      watch.close();
    }
  }

  private LogLevel getLogLevel(String eventType) {
    if (eventType.equals("WARNING")) {
      return LogLevel.WARN;
    }
    return LogLevel.INFO;
  }

  private void streamLogLine(ILogStreamingTaskClient logStreamingTaskClient, LogLevel logLevel, String message) {
    LogLine logLine =
        LogLine.builder().level(logLevel).message(message).timestamp(OffsetDateTime.now().toInstant()).build();
    logStreamingTaskClient.writeLogLine(logLine, "");
  }
}
