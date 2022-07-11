/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.k8s.steadystate.statusviewer;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.k8s.steadystate.statusviewer.StatefulSetStatusViewer.ResponseMessages.PARTIAL_ROLLOUT;
import static io.harness.k8s.steadystate.statusviewer.StatefulSetStatusViewer.ResponseMessages.PARTITIONED_PARTIAL_ROLLOUT;
import static io.harness.k8s.steadystate.statusviewer.StatefulSetStatusViewer.ResponseMessages.PARTITIONED_SUCCESSFUL_ROLLOUT;
import static io.harness.k8s.steadystate.statusviewer.StatefulSetStatusViewer.ResponseMessages.SUCCESSFUL_ROLLOUT;
import static io.harness.k8s.steadystate.statusviewer.StatefulSetStatusViewer.ResponseMessages.UNSUPPORTED_STRATEGY_TYPE;
import static io.harness.k8s.steadystate.statusviewer.StatefulSetStatusViewer.ResponseMessages.WAITING_FOR_ROLLOUT;
import static io.harness.k8s.steadystate.statusviewer.StatefulSetStatusViewer.ResponseMessages.WAITING_FOR_UPDATE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.k8s.steadystate.model.K8ApiResponseDTO;

import com.google.inject.Singleton;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1StatefulSet;
import io.kubernetes.client.openapi.models.V1StatefulSetSpec;
import io.kubernetes.client.openapi.models.V1StatefulSetStatus;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@OwnedBy(CDP)
@Singleton
public class StatefulSetStatusViewer {
  private static final String ROLLING_UPDATE_STRATEGY_TYPE = "RollingUpdate";

  // This logic has been copied from kubectl go client code
  public K8ApiResponseDTO extractRolloutStatus(V1StatefulSet statefulSet) {
    V1ObjectMeta meta = statefulSet.getMetadata();

    if (statefulSet.getSpec() != null && statefulSet.getSpec().getUpdateStrategy() != null
        && !ROLLING_UPDATE_STRATEGY_TYPE.equals(statefulSet.getSpec().getUpdateStrategy().getType())) {
      return K8ApiResponseDTO.builder().isDone(true).message(UNSUPPORTED_STRATEGY_TYPE).build();
    }

    V1StatefulSetStatus statefulSetStatus = statefulSet.getStatus();
    initializeNullFieldsInStatefulSetStatus(statefulSetStatus);

    if (statefulSetStatus != null && statefulSetStatus.getObservedGeneration() != null) {
      if (statefulSetStatus.getObservedGeneration() == 0
          || (meta != null && meta.getGeneration() != null
              && meta.getGeneration() > statefulSetStatus.getObservedGeneration())) {
        return K8ApiResponseDTO.builder().message(WAITING_FOR_UPDATE).isDone(false).build();
      }
    }
    V1StatefulSetSpec statefulSetSpec = statefulSet.getSpec();
    if (statefulSetSpec != null && statefulSetSpec.getReplicas() != null && statefulSetStatus != null
        && statefulSetStatus.getReadyReplicas() != null
        && statefulSetStatus.getReadyReplicas() < statefulSetSpec.getReplicas()) {
      return K8ApiResponseDTO.builder()
          .isDone(false)
          .message(
              String.format(WAITING_FOR_ROLLOUT, statefulSetSpec.getReplicas() - statefulSetStatus.getReadyReplicas()))
          .build();
    }

    if (statefulSetStatus != null && statefulSetSpec != null && statefulSetSpec.getUpdateStrategy() != null
        && ROLLING_UPDATE_STRATEGY_TYPE.equals(statefulSetSpec.getUpdateStrategy().getType())
        && statefulSetSpec.getUpdateStrategy().getRollingUpdate() != null
        && statefulSetSpec.getUpdateStrategy().getRollingUpdate().getPartition() != null
        && statefulSetSpec.getUpdateStrategy().getRollingUpdate().getPartition() > 0) {
      if (statefulSetSpec.getReplicas() != null
          && statefulSetStatus.getUpdatedReplicas()
              < statefulSetSpec.getReplicas() - statefulSetSpec.getUpdateStrategy().getRollingUpdate().getPartition()) {
        return K8ApiResponseDTO.builder()
            .message(String.format(PARTITIONED_PARTIAL_ROLLOUT, statefulSetStatus.getUpdatedReplicas(),
                statefulSetSpec.getReplicas() - statefulSetSpec.getUpdateStrategy().getRollingUpdate().getPartition()))
            .isDone(false)
            .build();
      }

      return K8ApiResponseDTO.builder()
          .message(String.format(PARTITIONED_SUCCESSFUL_ROLLOUT, statefulSetStatus.getUpdatedReplicas()))
          .isDone(true)
          .build();
    }

    if (statefulSetStatus != null && statefulSetStatus.getCurrentRevision() != null
        && statefulSetStatus.getUpdateRevision() != null
        && !statefulSetStatus.getCurrentRevision().equals(statefulSetStatus.getUpdateRevision())) {
      return K8ApiResponseDTO.builder()
          .isDone(false)
          .message(String.format(
              PARTIAL_ROLLOUT, statefulSetStatus.getUpdatedReplicas(), statefulSetStatus.getUpdateRevision()))
          .build();
    }

    return K8ApiResponseDTO.builder()
        .isDone(true)
        .message(String.format(
            SUCCESSFUL_ROLLOUT, statefulSetStatus.getCurrentReplicas(), statefulSetStatus.getCurrentRevision()))
        .build();
  }

  private void initializeNullFieldsInStatefulSetStatus(V1StatefulSetStatus statefulSetStatus) {
    if (statefulSetStatus == null) {
      statefulSetStatus = new V1StatefulSetStatus();
    }
    if (statefulSetStatus.getReadyReplicas() == null) {
      statefulSetStatus.setReadyReplicas(0);
    }

    if (statefulSetStatus.getUpdatedReplicas() == null) {
      statefulSetStatus.setUpdatedReplicas(0);
    }

    if (statefulSetStatus.getCurrentReplicas() == null) {
      statefulSetStatus.setCurrentReplicas(0);
    }
  }

  @NoArgsConstructor(access = AccessLevel.PRIVATE)
  static class ResponseMessages {
    static final String UNSUPPORTED_STRATEGY_TYPE = "rollout status is only available for RollingUpdate strategy type";
    static final String WAITING_FOR_UPDATE = "Waiting for statefulset spec update to be observed...";
    static final String WAITING_FOR_ROLLOUT = "Waiting for %d pods to be ready...";
    static final String PARTITIONED_PARTIAL_ROLLOUT =
        "Waiting for partitioned roll out to finish: %d out of %d new pods have been updated...";
    static final String PARTITIONED_SUCCESSFUL_ROLLOUT =
        "partitioned roll out complete: %d new pods have been updated...";
    static final String PARTIAL_ROLLOUT =
        "waiting for statefulset rolling update to complete %d pods at revision %s...";
    static final String SUCCESSFUL_ROLLOUT = "statefulset rolling update complete %d pods at revision %s...";
  }
}
