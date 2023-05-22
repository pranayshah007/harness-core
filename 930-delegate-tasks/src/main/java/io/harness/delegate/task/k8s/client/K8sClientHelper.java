/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.k8s.client;

import static java.util.stream.Collectors.toSet;

import io.harness.delegate.task.k8s.ContainerDeploymentDelegateBaseHelper;
import io.harness.delegate.task.k8s.K8sInfraDelegateConfig;
import io.harness.k8s.KubernetesApiRetryUtils;
import io.harness.k8s.KubernetesHelperService;
import io.harness.k8s.kubectl.Kubectl;
import io.harness.k8s.model.K8sDelegateTaskParams;
import io.harness.k8s.model.K8sSteadyStateDTO;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.k8s.model.KubernetesResourceId;
import io.harness.k8s.steadystate.model.K8sEventWatchDTO;
import io.harness.k8s.steadystate.model.K8sStatusWatchDTO;
import io.harness.logging.LogCallback;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.github.resilience4j.retry.Retry;
import io.kubernetes.client.openapi.ApiClient;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Singleton
public class K8sClientHelper {
  @Inject private KubernetesHelperService kubernetesHelperService;
  @Inject private ContainerDeploymentDelegateBaseHelper containerDeploymentDelegateBaseHelper;

  private static final String EVENT_ERROR_FORMAT = "%-7s: %s";
  private static final String MAX_RESOURCE_NAME_LENGTH = "${MAX_RESOURCE_NAME_LENGTH}";
  private static final String EVENT_INFO_FORMAT = "%-7s: %-" + MAX_RESOURCE_NAME_LENGTH + "s   %s";
  private static final String WATCH_STATUS_FORMAT = "%n%-7s: %-" + MAX_RESOURCE_NAME_LENGTH + "s   %s";

  private final Retry watchRetry = KubernetesApiRetryUtils.buildRetryAndRegisterListeners(this.getClass().getName());

  K8sEventWatchDTO createEventWatchDTO(K8sSteadyStateDTO steadyStateDTO, ApiClient apiClient) {
    final String eventInfoFormat = fetchEventInfoFormat(steadyStateDTO.getResourceIds(), EVENT_INFO_FORMAT);
    return K8sEventWatchDTO.builder()
        .apiClient(apiClient)
        .eventInfoFormat(eventInfoFormat)
        .eventErrorFormat(EVENT_ERROR_FORMAT)
        .releaseName(steadyStateDTO.getRequest().getReleaseName())
        .resourceIds(steadyStateDTO.getResourceIds())
        .workingDirectory(steadyStateDTO.getK8sDelegateTaskParams().getWorkingDirectory())
        .isErrorFrameworkEnabled(steadyStateDTO.isErrorFrameworkEnabled())
        .build();
  }

  K8sEventWatchDTO createEventWatchDTO(K8sSteadyStateDTO steadyStateDTO, Kubectl client) {
    final String eventInfoFormat = fetchEventInfoFormat(steadyStateDTO.getResourceIds(), EVENT_INFO_FORMAT);
    return K8sEventWatchDTO.builder()
        .client(client)
        .eventInfoFormat(eventInfoFormat)
        .eventErrorFormat(EVENT_ERROR_FORMAT)
        .releaseName(steadyStateDTO.getRequest().getReleaseName())
        .resourceIds(steadyStateDTO.getResourceIds())
        .workingDirectory(steadyStateDTO.getK8sDelegateTaskParams().getWorkingDirectory())
        .isErrorFrameworkEnabled(steadyStateDTO.isErrorFrameworkEnabled())
        .build();
  }

  K8sStatusWatchDTO createStatusWatchDTO(K8sSteadyStateDTO steadyStateDTO, ApiClient apiClient) {
    final String statusFormat = fetchEventInfoFormat(steadyStateDTO.getResourceIds(), WATCH_STATUS_FORMAT);
    return K8sStatusWatchDTO.builder()
        .apiClient(apiClient)
        .retry(watchRetry)
        .k8sDelegateTaskParams(steadyStateDTO.getK8sDelegateTaskParams())
        .isErrorFrameworkEnabled(steadyStateDTO.isErrorFrameworkEnabled())
        .statusFormat(statusFormat)
        .build();
  }

  K8sStatusWatchDTO createStatusWatchDTO(K8sSteadyStateDTO steadyStateDTO, Kubectl client) {
    final String statusFormat = fetchEventInfoFormat(steadyStateDTO.getResourceIds(), WATCH_STATUS_FORMAT);
    return K8sStatusWatchDTO.builder()
        .client(client)
        .k8sDelegateTaskParams(steadyStateDTO.getK8sDelegateTaskParams())
        .isErrorFrameworkEnabled(steadyStateDTO.isErrorFrameworkEnabled())
        .statusFormat(statusFormat)
        .build();
  }

  ApiClient createKubernetesApiClient(K8sInfraDelegateConfig k8sInfraDelegateConfig, String workingDirectory,
      LogCallback logCallback, KubernetesConfig kubernetesConfig) {
    if (kubernetesConfig == null) {
      kubernetesConfig = containerDeploymentDelegateBaseHelper.createKubernetesConfig(
          k8sInfraDelegateConfig, workingDirectory, logCallback);
    }
    return kubernetesHelperService.getApiClient(kubernetesConfig);
  }

  Kubectl createKubernetesCliClient(K8sDelegateTaskParams k8sDelegateTaskParams) {
    return Kubectl.client(k8sDelegateTaskParams.getKubectlPath(), k8sDelegateTaskParams.getKubeconfigPath());
  }

  Set<String> getNamespacesToMonitor(List<KubernetesResourceId> resourceIds, String namespace) {
    Set<String> namespacesToMonitor = resourceIds.stream().map(KubernetesResourceId::getNamespace).collect(toSet());
    namespacesToMonitor.add(namespace);
    return namespacesToMonitor;
  }

  void logSteadyStateInfo(
      List<KubernetesResourceId> workloads, Set<String> namespaces, LogCallback executionLogCallback) {
    executionLogCallback.saveExecutionLog(String.format("Watching following namespaces for events: %s", namespaces));

    String workloadKindNames =
        workloads.stream().map(KubernetesResourceId::kindNameRef).collect(Collectors.joining(", "));
    executionLogCallback.saveExecutionLog(
        String.format("Waiting for following workloads to finish: [%s]", workloadKindNames));
  }

  private String fetchEventInfoFormat(List<KubernetesResourceId> resourceIds, String eventInfoFormat) {
    int maxResourceNameLength = getMaxResourceNameLength(resourceIds);
    return eventInfoFormat.replace(MAX_RESOURCE_NAME_LENGTH, String.valueOf(maxResourceNameLength));
  }

  private int getMaxResourceNameLength(List<KubernetesResourceId> resourceIds) {
    int maxResourceNameLength = 0;
    for (KubernetesResourceId kubernetesResourceId : resourceIds) {
      maxResourceNameLength = Math.max(maxResourceNameLength, kubernetesResourceId.getName().length());
    }
    return maxResourceNameLength;
  }
}
