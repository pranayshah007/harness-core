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
import io.harness.k8s.model.KubernetesResourceId;
import io.harness.k8s.steadystate.model.K8ApiResponseDTO;
import io.harness.k8s.steadystate.model.K8sStatusWatchDTO;
import io.harness.k8s.steadystate.statusviewer.DeploymentStatusViewer;
import io.harness.k8s.steadystate.watcher.client.K8sWatchClient;
import io.harness.k8s.steadystate.watcher.client.K8sWatchClientFactory;
import io.harness.logging.LogCallback;
import io.harness.supplier.ThrowingSupplier;

import com.google.common.annotations.VisibleForTesting;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.kubernetes.client.openapi.apis.AppsV1Api;
import io.kubernetes.client.openapi.models.V1Deployment;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.util.Watch;
import java.lang.reflect.Type;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;

@Singleton
@Slf4j
@OwnedBy(CDP)
public class DeploymentApiWatcher extends AbstractWorkloadWatcher {
  @Inject private DeploymentStatusViewer statusViewer;
  @Inject private K8sWatchClientFactory watchClientFactory;
  @VisibleForTesting static final Type v1DeploymentType = new TypeToken<Watch.Response<V1Deployment>>() {}.getType();

  @Override
  protected boolean watchRolloutStatusInternal(K8sStatusWatchDTO k8SStatusWatchDTO, KubernetesResourceId workload,
      LogCallback executionLogCallback) throws Exception {
    K8sWatchClient k8sWatchClient =
        watchClientFactory.create(k8SStatusWatchDTO.getApiClient(), k8SStatusWatchDTO.getRetry());
    AppsV1Api appsV1Api = new AppsV1Api(k8SStatusWatchDTO.getApiClient());
    ThrowingSupplier<Call> callSupplier = ()
        -> appsV1Api.listNamespacedDeploymentCall(workload.getNamespace(), null, null, null, null, null, null, null,
            null, WATCH_CALL_TIMEOUT_SECONDS, true, null);

    return k8sWatchClient.<V1Deployment>waitOnCondition(
        v1DeploymentType, callSupplier, event -> processEvent(event, workload, executionLogCallback));
  }

  private boolean processEvent(
      Watch.Response<V1Deployment> event, KubernetesResourceId workload, LogCallback logCallback) throws Exception {
    V1Deployment deployment = event.object;
    V1ObjectMeta meta = deployment.getMetadata();
    if (meta == null || workload.getName().equals(meta.getName())) {
      switch (event.type) {
        case "ADDED":
        case "MODIFIED":
          K8ApiResponseDTO rolloutStatus = statusViewer.extractRolloutStatus(deployment);
          logCallback.saveExecutionLog(rolloutStatus.getMessage());
          if (rolloutStatus.isFailed()) {
            throw new KubernetesCliTaskRuntimeException(
                rolloutStatus.getMessage(), KubernetesCliCommandType.STEADY_STATE_CHECK);
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
  }
}
