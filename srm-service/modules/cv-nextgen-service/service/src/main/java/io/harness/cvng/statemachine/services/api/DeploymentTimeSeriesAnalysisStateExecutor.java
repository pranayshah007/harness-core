/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.statemachine.services.api;

import io.harness.cvng.analysis.entities.LearningEngineTask;
import io.harness.cvng.analysis.services.api.TimeSeriesAnalysisService;
import io.harness.cvng.statemachine.beans.AnalysisInput;
import io.harness.cvng.statemachine.beans.AnalysisState;
import io.harness.cvng.statemachine.beans.AnalysisStatus;
import io.harness.cvng.statemachine.entities.DeploymentTimeSeriesAnalysisState;
import io.harness.cvng.statemachine.exception.AnalysisStateMachineException;
import io.harness.cvng.verificationjob.services.api.VerificationJobInstanceService;

import com.google.inject.Inject;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DeploymentTimeSeriesAnalysisStateExecutor
    extends AnalysisStateExecutor<DeploymentTimeSeriesAnalysisState> {
  @Inject protected transient TimeSeriesAnalysisService timeSeriesAnalysisService;

  @Inject VerificationJobInstanceService verificationJobInstanceService;

  @Override
  public AnalysisState execute(DeploymentTimeSeriesAnalysisState analysisState) {
    analysisState.setVerificationJobInstanceId(analysisState.getVerificationJobInstanceId());
    analysisState.getInputs().setVerificationJobInstanceId(analysisState.getVerificationJobInstanceId());
    List<String> taskIds = scheduleAnalysis(analysisState.getInputs());
    analysisState.setStatus(AnalysisStatus.RUNNING);

    if (taskIds != null && taskIds.size() == 1) {
      analysisState.setWorkerTaskId(taskIds.get(0));
    } else {
      throw new AnalysisStateMachineException(
          "Unknown number of worker tasks created in Timeseries Analysis State: " + taskIds);
    }
    log.info("Executing timeseries analysis");
    return analysisState;
  }

  @Override
  public AnalysisStatus getExecutionStatus(DeploymentTimeSeriesAnalysisState analysisState) {
    if (!analysisState.getStatus().equals(AnalysisStatus.SUCCESS)) {
      Map<String, LearningEngineTask.ExecutionStatus> taskStatuses =
          timeSeriesAnalysisService.getTaskStatus(analysisState.getInputs().getVerificationTaskId(),
              new HashSet<>(Arrays.asList(analysisState.getWorkerTaskId())));
      LearningEngineTask.ExecutionStatus taskStatus = taskStatuses.get(analysisState.getWorkerTaskId());
      // This could be common code for all states.
      switch (taskStatus) {
        case SUCCESS:
          return AnalysisStatus.SUCCESS;
        case FAILED:
        case TIMEOUT:
          return AnalysisStatus.RETRY;
        case QUEUED:
        case RUNNING:
          return AnalysisStatus.RUNNING;
        default:
          throw new AnalysisStateMachineException(
              "Unknown worker state when executing timeseries analysis: " + taskStatus);
      }
    }
    return AnalysisStatus.SUCCESS;
  }

  @Override
  public AnalysisState handleRerun(DeploymentTimeSeriesAnalysisState analysisState) {
    // increment the retryCount without caring for the max
    // clean up state in underlying worker and then execute

    analysisState.setRetryCount(analysisState.getRetryCount() + 1);
    log.info("In TimeSeriesAnalysisState for Inputs {}, cleaning up worker task. Old taskID: {}",
        analysisState.getInputs(), analysisState.getWorkerTaskId());
    analysisState.setWorkerTaskId(null);
    execute(analysisState);
    return analysisState;
  }

  @Override
  public AnalysisState handleRunning(DeploymentTimeSeriesAnalysisState analysisState) {
    return analysisState;
  }

  @Override
  public AnalysisState handleSuccess(DeploymentTimeSeriesAnalysisState analysisState) {
    analysisState.setStatus(AnalysisStatus.SUCCESS);
    return analysisState;
  }

  @Override
  public AnalysisState handleTransition(DeploymentTimeSeriesAnalysisState analysisState) {
    analysisState.setStatus(AnalysisStatus.SUCCESS);
    return analysisState;
  }

  @Override
  public AnalysisState handleRetry(DeploymentTimeSeriesAnalysisState analysisState) {
    if (analysisState.getRetryCount() >= getMaxRetry()) {
      analysisState.setStatus(AnalysisStatus.FAILED);
    } else {
      return handleRerun(analysisState);
    }
    return analysisState;
  }

  @Override
  public void handleFinalStatuses(DeploymentTimeSeriesAnalysisState analysisState) {
    timeSeriesAnalysisService.logDeploymentVerificationProgress(analysisState.getInputs(), analysisState.getStatus());
  }

  protected List<String> scheduleAnalysis(AnalysisInput analysisInput) {
    return timeSeriesAnalysisService.scheduleDeploymentTimeSeriesAnalysis(analysisInput);
  }
}
