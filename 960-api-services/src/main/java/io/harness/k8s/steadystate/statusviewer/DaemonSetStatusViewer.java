/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.k8s.steadystate.statusviewer;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.k8s.steadystate.statusviewer.DaemonSetStatusViewer.ResponseMessages.DONE;
import static io.harness.k8s.steadystate.statusviewer.DaemonSetStatusViewer.ResponseMessages.PARTIALLY_AVAILABLE;
import static io.harness.k8s.steadystate.statusviewer.DaemonSetStatusViewer.ResponseMessages.PARTIALLY_UPDATED;
import static io.harness.k8s.steadystate.statusviewer.DaemonSetStatusViewer.ResponseMessages.UNSUPPORTED_STRATEGY_TYPE;
import static io.harness.k8s.steadystate.statusviewer.DaemonSetStatusViewer.ResponseMessages.WAITING;

import io.harness.annotations.dev.OwnedBy;
import io.harness.k8s.steadystate.model.K8ApiResponseDTO;

import com.google.inject.Singleton;
import io.kubernetes.client.openapi.models.V1DaemonSet;
import io.kubernetes.client.openapi.models.V1DaemonSetStatus;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@OwnedBy(CDP)
@Singleton
public class DaemonSetStatusViewer {
  private static final String ROLLING_UPDATE_STRATEGY_TYPE = "RollingUpdate";

  // This logic has been copied from kubectl go client code
  public K8ApiResponseDTO extractRolloutStatus(V1DaemonSet daemonSet) {
    if (daemonSet.getSpec() != null && daemonSet.getSpec().getUpdateStrategy() != null
        && !ROLLING_UPDATE_STRATEGY_TYPE.equals(daemonSet.getSpec().getUpdateStrategy().getType())) {
      return K8ApiResponseDTO.builder().isDone(true).message(UNSUPPORTED_STRATEGY_TYPE).build();
    }
    V1ObjectMeta meta = daemonSet.getMetadata();
    V1DaemonSetStatus daemonSetStatus = daemonSet.getStatus();

    if (meta != null && meta.getGeneration() != null && daemonSetStatus != null
        && daemonSetStatus.getObservedGeneration() != null
        && meta.getGeneration() <= daemonSetStatus.getObservedGeneration()) {
      initializeNullFieldsInStatefulSetStatus(daemonSetStatus);

      if (daemonSetStatus.getUpdatedNumberScheduled() < daemonSetStatus.getDesiredNumberScheduled()) {
        return K8ApiResponseDTO.builder()
            .message(String.format(PARTIALLY_UPDATED, meta.getName(), daemonSetStatus.getUpdatedNumberScheduled(),
                daemonSetStatus.getDesiredNumberScheduled()))
            .isDone(false)
            .build();
      }

      if (daemonSetStatus.getNumberAvailable() < daemonSetStatus.getDesiredNumberScheduled()) {
        return K8ApiResponseDTO.builder()
            .message(String.format(PARTIALLY_AVAILABLE, meta.getName(), daemonSetStatus.getNumberAvailable(),
                daemonSetStatus.getDesiredNumberScheduled()))
            .isDone(false)
            .build();
      }

      return K8ApiResponseDTO.builder()
          .message(String.format(DONE, meta.getName(), daemonSetStatus.getNumberAvailable()))
          .isDone(true)
          .build();
    }

    return K8ApiResponseDTO.builder().isDone(false).message(WAITING).build();
  }

  private void initializeNullFieldsInStatefulSetStatus(V1DaemonSetStatus daemonSetStatus) {
    if (daemonSetStatus == null) {
      daemonSetStatus = new V1DaemonSetStatus();
    }
    if (daemonSetStatus.getUpdatedNumberScheduled() == null) {
      daemonSetStatus.setUpdatedNumberScheduled(0);
    }

    if (daemonSetStatus.getDesiredNumberScheduled() == null) {
      daemonSetStatus.setDesiredNumberScheduled(0);
    }

    if (daemonSetStatus.getNumberAvailable() == null) {
      daemonSetStatus.setNumberAvailable(0);
    }
  }

  @NoArgsConstructor(access = AccessLevel.PRIVATE)
  static class ResponseMessages {
    static final String WAITING = "Waiting for daemon set spec update to be observed...";
    static final String DONE = "daemon set %s successfully rolled out with %s available replicas.";
    static final String PARTIALLY_AVAILABLE =
        "Waiting for daemon set %s rollout to finish: %d of %d updated pods are available...";
    static final String PARTIALLY_UPDATED =
        "Waiting for daemon set %s rollout to finish: %d out of %d new pods have been updated...";
    static final String UNSUPPORTED_STRATEGY_TYPE = "rollout status is only available for RollingUpdate strategy type";
  }
}
