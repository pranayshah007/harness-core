/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.k8s.steadystate.statusviewer;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.k8s.steadystate.statusviewer.JobStatusViewer.ResponseMessages.FAILED;
import static io.harness.k8s.steadystate.statusviewer.JobStatusViewer.ResponseMessages.SUCCESS;
import static io.harness.k8s.steadystate.statusviewer.JobStatusViewer.ResponseMessages.WAITING;
import static io.harness.k8s.steadystate.statusviewer.JobStatusViewer.ResponseMessages.WAITING_STATUS;

import io.harness.annotations.dev.OwnedBy;
import io.harness.k8s.steadystate.model.K8ApiResponseDTO;

import com.google.inject.Singleton;
import io.kubernetes.client.openapi.models.V1Job;
import io.kubernetes.client.openapi.models.V1JobCondition;
import io.kubernetes.client.openapi.models.V1JobStatus;
import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@OwnedBy(CDP)
@Singleton
public class JobStatusViewer {
  public K8ApiResponseDTO extractRolloutStatus(V1Job job) {
    V1JobStatus jobStatus = job.getStatus();
    String currentStatus = "";

    if (jobStatus != null) {
      List<V1JobCondition> jobConditions = jobStatus.getConditions();
      if (jobConditions != null) {
        if (jobConditions.stream().anyMatch(
                condition -> condition.getType().equals("Failed") && condition.getStatus().equals("True"))) {
          return K8ApiResponseDTO.builder().isFailed(true).message(String.format(FAILED, jobStatus)).build();
        }

        if (jobConditions.stream().anyMatch(
                condition -> condition.getType().equals("Complete") && condition.getStatus().equals("True"))
            && jobStatus.getCompletionTime() != null) {
          return K8ApiResponseDTO.builder().isDone(true).message(String.format(SUCCESS, jobStatus)).build();
        }
      }

      currentStatus = String.format(WAITING_STATUS, jobStatus.getStartTime(), jobStatus.getActive());
    }

    return K8ApiResponseDTO.builder().isDone(false).message(String.format(WAITING, currentStatus)).build();
  }

  @NoArgsConstructor(access = AccessLevel.PRIVATE)
  static class ResponseMessages {
    static final String WAITING_STATUS = "[startTime: %s, active: %s]";
    static final String WAITING = "Status: %s";
    static final String SUCCESS = "Successfully completed Job with status: %s";
    static final String FAILED = " Job failed. Status: %s";
  }
}
