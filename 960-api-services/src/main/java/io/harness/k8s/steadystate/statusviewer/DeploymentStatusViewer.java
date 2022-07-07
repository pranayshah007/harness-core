/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.k8s.steadystate.statusviewer;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.k8s.steadystate.statusviewer.DeploymentStatusViewer.ResponseMessages.PARTIALLY_AVAILABLE;
import static io.harness.k8s.steadystate.statusviewer.DeploymentStatusViewer.ResponseMessages.PARTIALLY_ROLLED_OUT;
import static io.harness.k8s.steadystate.statusviewer.DeploymentStatusViewer.ResponseMessages.PARTIALLY_UPDATED;
import static io.harness.k8s.steadystate.statusviewer.DeploymentStatusViewer.ResponseMessages.PROGRESS_DEADLINE_EXCEEDED;
import static io.harness.k8s.steadystate.statusviewer.DeploymentStatusViewer.ResponseMessages.SUCCESSFUL;
import static io.harness.k8s.steadystate.statusviewer.DeploymentStatusViewer.ResponseMessages.WAITING_FOR_UPDATE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.k8s.steadystate.model.K8ApiResponseDTO;

import com.google.inject.Singleton;
import io.kubernetes.client.openapi.models.V1Deployment;
import io.kubernetes.client.openapi.models.V1DeploymentCondition;
import io.kubernetes.client.openapi.models.V1DeploymentSpec;
import io.kubernetes.client.openapi.models.V1DeploymentStatus;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import java.util.List;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@OwnedBy(CDP)
@Singleton
public class DeploymentStatusViewer {
  private static final String PROGRESS_DEADLINE_EXCEEDED_REASON = "ProgressDeadlineExceeded";
  private static final String TYPE_PROGRESSING = "Progressing";

  // This logic has been copied from kubectl go client code
  public K8ApiResponseDTO extractRolloutStatus(V1Deployment deployment) {
    V1ObjectMeta meta = deployment.getMetadata();
    if (meta != null && meta.getGeneration() != null && deployment.getStatus() != null
        && deployment.getStatus().getObservedGeneration() != null
        && meta.getGeneration() <= deployment.getStatus().getObservedGeneration()) {
      V1DeploymentStatus deploymentStatus = deployment.getStatus();
      List<V1DeploymentCondition> deploymentConditionList = deploymentStatus.getConditions();

      if (deploymentConditionList != null) {
        Optional<V1DeploymentCondition> deploymentConditionOptional =
            deploymentConditionList.stream()
                .filter(deploymentCondition -> deploymentCondition.getType().equalsIgnoreCase(TYPE_PROGRESSING))
                .findFirst();

        if (deploymentConditionOptional.isPresent()) {
          V1DeploymentCondition deploymentCondition = deploymentConditionOptional.get();

          if (PROGRESS_DEADLINE_EXCEEDED_REASON.equals(deploymentCondition.getReason())) {
            return K8ApiResponseDTO.builder()
                .isFailed(true)
                .message(String.format(PROGRESS_DEADLINE_EXCEEDED, meta.getName()))
                .build();
          }
        }
      }

      V1DeploymentSpec deploymentSpec = deployment.getSpec();
      initializeNullFieldsInDeploymentStatus(deploymentStatus);

      if (deploymentSpec != null && deploymentSpec.getReplicas() != null
          && deploymentStatus.getUpdatedReplicas() < deploymentSpec.getReplicas()) {
        return K8ApiResponseDTO.builder()
            .isDone(false)
            .message(String.format(
                PARTIALLY_UPDATED, meta.getName(), deploymentStatus.getUpdatedReplicas(), deploymentSpec.getReplicas()))
            .build();
      }
      if (deploymentStatus.getReplicas() > deploymentStatus.getUpdatedReplicas()) {
        return K8ApiResponseDTO.builder()
            .isDone(false)
            .message(String.format(PARTIALLY_ROLLED_OUT, meta.getName(),
                deploymentStatus.getReplicas() - deploymentStatus.getUpdatedReplicas()))
            .build();
      }
      if (deploymentStatus.getAvailableReplicas() < deploymentStatus.getUpdatedReplicas()) {
        return K8ApiResponseDTO.builder()
            .isDone(false)
            .message(String.format(PARTIALLY_AVAILABLE, meta.getName(), deploymentStatus.getAvailableReplicas(),
                deploymentStatus.getUpdatedReplicas()))
            .build();
      }
      return K8ApiResponseDTO.builder()
          .isDone(true)
          .message(String.format(SUCCESSFUL, meta.getName(), deploymentStatus.getAvailableReplicas()))
          .build();
    }
    return K8ApiResponseDTO.builder().isDone(false).message(WAITING_FOR_UPDATE).build();
  }

  private void initializeNullFieldsInDeploymentStatus(V1DeploymentStatus deploymentStatus) {
    if (deploymentStatus == null) {
      deploymentStatus = new V1DeploymentStatus();
    }
    if (deploymentStatus.getAvailableReplicas() == null) {
      deploymentStatus.setAvailableReplicas(0);
    }
    if (deploymentStatus.getUpdatedReplicas() == null) {
      deploymentStatus.setUpdatedReplicas(0);
    }
    if (deploymentStatus.getReplicas() == null) {
      deploymentStatus.setReplicas(0);
    }
    if (deploymentStatus.getReadyReplicas() == null) {
      deploymentStatus.setReadyReplicas(0);
    }
    if (deploymentStatus.getUnavailableReplicas() == null) {
      deploymentStatus.setUnavailableReplicas(0);
    }
  }

  @NoArgsConstructor(access = AccessLevel.PRIVATE)
  static class ResponseMessages {
    static final String WAITING_FOR_UPDATE = "Waiting for deployment spec update to be observed...";
    static final String SUCCESSFUL = "deployment %s successfully rolled out with %s available replicas.";
    static final String PARTIALLY_AVAILABLE =
        "Waiting for deployment %s rollout to finish: %s of %s updated replicas are available...";
    static final String PARTIALLY_ROLLED_OUT =
        "Waiting for deployment %s rollout to finish: %s old replicas are pending termination...";
    static final String PARTIALLY_UPDATED =
        "Waiting for deployment %s rollout to finish: %s out of %s new replicas have been updated...";
    static final String PROGRESS_DEADLINE_EXCEEDED = "deployment %s exceeded its progress deadline";
  }
}
