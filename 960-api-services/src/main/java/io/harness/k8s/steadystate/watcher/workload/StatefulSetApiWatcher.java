/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.k8s.steadystate.watcher.workload;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.k8s.steadystate.K8sSteadyStateConstants.WATCH_CALL_TIMEOUT_SECONDS;

import io.harness.annotations.dev.OwnedBy;
import io.harness.configuration.KubernetesCliCommandType;
import io.harness.exception.KubernetesCliTaskRuntimeException;
import io.harness.k8s.KubernetesContainerServiceImpl;
import io.harness.k8s.WorkloadDetails;
import io.harness.k8s.model.KubernetesResourceId;
import io.harness.k8s.steadystate.model.K8ApiResponseDTO;
import io.harness.k8s.steadystate.model.K8sStatusWatchDTO;
import io.harness.k8s.steadystate.statusviewer.StatefulSetStatusViewer;
import io.harness.logging.LogCallback;
import io.harness.supplier.ThrowingSupplier;

import com.google.gson.reflect.TypeToken;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.apis.AppsV1Api;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1StatefulSet;
import io.kubernetes.client.util.Watch;
import java.lang.reflect.Type;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;

@Singleton
@Slf4j
@OwnedBy(CDP)
public class StatefulSetApiWatcher implements WorkloadWatcher {
  @Inject private StatefulSetStatusViewer statusViewer;
  @Inject private KubernetesContainerServiceImpl kubernetesContainerService;
  private static final Type v1StatefulSetType = new TypeToken<Watch.Response<V1StatefulSet>>() {}.getType();

  @Override
  public boolean watchRolloutStatus(K8sStatusWatchDTO k8SStatusWatchDTO, KubernetesResourceId workload,
      LogCallback executionLogCallback) throws Exception {
    return watchStatefulSetWithRetry(
        k8SStatusWatchDTO.getApiClient(), workload, executionLogCallback, k8SStatusWatchDTO.isErrorFrameworkEnabled());
  }

  private boolean watchStatefulSetWithRetry(ApiClient apiClient, KubernetesResourceId workload,
      LogCallback executionLogCallback, boolean errorFrameworkEnabled) throws Exception {
    AppsV1Api appsV1Api = new AppsV1Api(apiClient);
    WorkloadDetails workloadDetails =
        new WorkloadDetails(v1StatefulSetType, apiClient, workload, executionLogCallback, errorFrameworkEnabled);
    ThrowingSupplier<Call> callSupplier = ()
        -> appsV1Api.listNamespacedStatefulSetCall(workload.getNamespace(), null, null, null, null, null, null, null,
            null, WATCH_CALL_TIMEOUT_SECONDS, true, null);
    return kubernetesContainerService.<V1StatefulSet>watchRetriesWrapper(workloadDetails, callSupplier, event -> {
      V1StatefulSet statefulSet = event.object;
      V1ObjectMeta meta = statefulSet.getMetadata();
      if (meta == null || workload.getName().equals(meta.getName())) {
        switch (event.type) {
          case "ADDED":
          case "MODIFIED":
            K8ApiResponseDTO rolloutStatus = statusViewer.extractRolloutStatus(statefulSet);
            executionLogCallback.saveExecutionLog(rolloutStatus.getMessage());
            if (rolloutStatus.isFailed()) {
              if (errorFrameworkEnabled) {
                throw new KubernetesCliTaskRuntimeException(
                    rolloutStatus.getMessage(), KubernetesCliCommandType.STEADY_STATE_CHECK);
              }
              return false;
            }
            if (rolloutStatus.isDone()) {
              return true;
            }
            break;
          case "DELETED":
            throw new KubernetesCliTaskRuntimeException(
                "object has been deleted", KubernetesCliCommandType.STEADY_STATE_CHECK);
          default:
            log.warn(String.format("Unexpected k8s event type %s", event.type));
        }
      }
      return false;
    });
  }
}
