/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.statemachine.beans;

import io.harness.cvng.verificationjob.entities.VerificationJobInstance.ExecutionStatus;

import java.util.Arrays;
import java.util.List;

public enum AnalysisStatus {
  CREATED,
  RUNNING,
  SUCCESS,
  RETRY,
  TRANSITION,
  IGNORED,
  TIMEOUT,
  FAILED,
  COMPLETED,
  TERMINATED;

  public static ExecutionStatus mapToVerificationJobExecutionStatus(AnalysisStatus analysisStatus) {
    switch (analysisStatus) {
      case SUCCESS:
        return ExecutionStatus.SUCCESS;
      case FAILED:
      case TERMINATED:
        return ExecutionStatus.FAILED;
      case TIMEOUT:
        return ExecutionStatus.TIMEOUT;
      default:
        throw new IllegalStateException("AnalysisStatus " + analysisStatus
            + " should be one of final status. Mapping to executionStatus not defined.");
    }
  }

  public static List<AnalysisStatus> getFinalStates() {
    return Arrays.asList(COMPLETED, FAILED, TIMEOUT, IGNORED, TERMINATED, SUCCESS);
  }
  public static List<AnalysisStatus> getFailedStatuses() {
    return Arrays.asList(FAILED, TIMEOUT, TERMINATED);
  }
  public static List<AnalysisStatus> getCountMetricsNonFinalStatuses() {
    return Arrays.asList(CREATED, RUNNING, TRANSITION, RETRY);
  }
}
