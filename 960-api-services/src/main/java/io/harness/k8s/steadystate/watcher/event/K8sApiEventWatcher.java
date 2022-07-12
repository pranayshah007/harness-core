/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.k8s.steadystate.watcher.event;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.k8s.steadystate.K8sSteadyStateConstants.RESOURCE_VERSION_PATTERN;
import static io.harness.k8s.steadystate.K8sSteadyStateConstants.WATCH_CALL_TIMEOUT_SECONDS;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.k8s.model.KubernetesResourceId;
import io.harness.k8s.steadystate.model.K8sEventWatchDTO;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.CoreV1Event;
import io.kubernetes.client.openapi.models.CoreV1EventList;
import io.kubernetes.client.openapi.models.V1ObjectReference;
import io.kubernetes.client.util.Watch;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;

@Singleton
@Slf4j
@OwnedBy(CDP)
public class K8sApiEventWatcher {
  @Inject @Named("k8sSteadyStateExecutor") private ExecutorService k8sSteadyStateExecutor;

  public Future<?> watchForEvents(
      String namespace, K8sEventWatchDTO k8sEventWatchDTO, LogCallback executionLogCallback) {
    return k8sSteadyStateExecutor.submit(
        () -> runEventWatchInNamespace(namespace, k8sEventWatchDTO, executionLogCallback));
  }

  public void destroyRunning(List<Future<?>> eventWatchRefs) {
    for (Future<?> future : eventWatchRefs) {
      boolean cancelled = future.cancel(true);
      if (!cancelled) {
        log.warn("Failed to cancel k8s steady state thread ref.");
        future.cancel(true);
      }
    }
  }

  public void runEventWatchInNamespace(
      String namespace, K8sEventWatchDTO k8sNamespaceEventWatchDTO, LogCallback executionLogCallback) {
    ApiClient apiClient = k8sNamespaceEventWatchDTO.getApiClient();
    CoreV1Api coreV1Api = new CoreV1Api(apiClient);
    String eventInfoFormat = k8sNamespaceEventWatchDTO.getEventInfoFormat();
    String eventErrorFormat = k8sNamespaceEventWatchDTO.getEventErrorFormat();

    Set<String> workloadNames = k8sNamespaceEventWatchDTO.getResourceIds()
                                    .stream()
                                    .map(KubernetesResourceId::getName)
                                    .collect(Collectors.toSet());

    try {
      String resourceVersion = null;
      while (true) {
        if (resourceVersion == null) {
          CoreV1EventList coreV1EventList =
              coreV1Api.listNamespacedEvent(namespace, null, null, null, null, null, null, null, null, null, false);
          resourceVersion = coreV1EventList.getMetadata() != null ? coreV1EventList.getMetadata().getResourceVersion()
                                                                  : resourceVersion;
        }
        try (Watch<CoreV1Event> watch = createWatchCall(apiClient, coreV1Api, namespace, resourceVersion)) {
          for (Watch.Response<CoreV1Event> eventListResponse : watch) {
            CoreV1Event event = eventListResponse.object;
            V1ObjectReference ref = event.getInvolvedObject();
            if (ref.getName() != null
                && workloadNames.stream().noneMatch(workloadName -> ref.getName().contains(workloadName))) {
              continue;
            }
            if ("WARNING".equalsIgnoreCase(event.getType())) {
              executionLogCallback.saveExecutionLog(format(eventErrorFormat, "Event", event.getMessage()));
            } else {
              executionLogCallback.saveExecutionLog(
                  format(eventInfoFormat, "Event", ref.getName(), event.getMessage()));
            }
          }
        } catch (ApiException ex) {
          if (ex.getCode() == 504 || ex.getCode() == 410) {
            resourceVersion = extractResourceVersionFromException(ex);
          } else {
            resourceVersion = null;
          }
        } catch (IOException e) {
          IOException ex = ExceptionMessageSanitizer.sanitizeException(e);
          String errorMessage = "Failed to close Kubernetes watch." + ExceptionUtils.getMessage(ex);
          log.error(errorMessage, ex);
        }
      }
    } catch (ApiException e) {
      ApiException ex = ExceptionMessageSanitizer.sanitizeException(e);
      String errorMessage =
          String.format("Failed to watch events in namespace %s. ", namespace) + ExceptionUtils.getMessage(ex);
      log.error(errorMessage, ex);
      executionLogCallback.saveExecutionLog(errorMessage, LogLevel.ERROR);
    }
  }

  private Watch<CoreV1Event> createWatchCall(
      ApiClient apiClient, CoreV1Api coreV1Api, String namespace, String resourceVersion) throws ApiException {
    Call call = coreV1Api.listNamespacedEventCall(
        namespace, null, null, null, null, null, null, resourceVersion, null, WATCH_CALL_TIMEOUT_SECONDS, true, null);
    return Watch.createWatch(apiClient, call, new TypeToken<Watch.Response<CoreV1Event>>() {}.getType());
  }

  private static String extractResourceVersionFromException(ApiException ex) {
    String body = ex.getResponseBody();
    if (body == null) {
      return null;
    }

    Gson gson = new Gson();
    Map<?, ?> st = gson.fromJson(body, Map.class);
    String msg = (String) st.get("message");
    Matcher m = RESOURCE_VERSION_PATTERN.matcher(msg);
    if (!m.matches()) {
      return null;
    }

    return m.group(2);
  }
}
