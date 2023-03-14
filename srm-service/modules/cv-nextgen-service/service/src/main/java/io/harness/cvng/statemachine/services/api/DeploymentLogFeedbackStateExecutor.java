/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.statemachine.services.api;

import io.harness.cvng.analysis.entities.LearningEngineTask;
import io.harness.cvng.analysis.services.api.LogAnalysisService;
import io.harness.cvng.analysis.services.api.TimeSeriesAnalysisService;
import io.harness.cvng.core.services.api.TimeSeriesRecordService;
import io.harness.cvng.statemachine.beans.AnalysisInput;
import io.harness.cvng.statemachine.beans.AnalysisState;
import io.harness.cvng.statemachine.beans.AnalysisStatus;
import io.harness.cvng.statemachine.entities.DeploymentLogAnalysisState;
import io.harness.cvng.statemachine.entities.DeploymentLogFeedbackState;
import io.harness.cvng.statemachine.exception.AnalysisStateMachineException;
import io.harness.cvng.verificationjob.services.api.VerificationJobInstanceService;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DeploymentLogFeedbackStateExecutor extends LogAnalysisStateExecutor<DeploymentLogFeedbackState> {
  @Inject private VerificationJobInstanceService verificationJobInstanceService;
  @Inject private TimeSeriesRecordService timeSeriesRecordService;
  @Inject private TimeSeriesAnalysisService timeSeriesAnalysisService;
  @Inject private LogAnalysisService logAnalysisService;

  @Override
  public void handleFinalStatuses(DeploymentLogAnalysisState analysisState) {
    logAnalysisService.logDeploymentVerificationProgress(analysisState.getInputs(), analysisState.getStatus());
  }

  @Override
  protected String scheduleAnalysis(AnalysisInput analysisInput) {
    return logAnalysisService.scheduleDeploymentLogFeedbackTask(analysisInput);
  }

  @Override
  public AnalysisState execute(DeploymentLogFeedbackState analysisState) {
    String workerTaskId = logAnalysisService.scheduleDeploymentLogFeedbackTask(analysisState.getInputs());
    analysisState.setWorkerTaskId(workerTaskId);
    Preconditions.checkNotNull(analysisState.getWorkerTaskId(), "workerId can not be null");
    analysisState.setStatus(AnalysisStatus.RUNNING);
    return analysisState;
  }

  @Override
  public AnalysisStatus getExecutionStatus(DeploymentLogFeedbackState analysisState) {
    if (analysisState.getStatus() != AnalysisStatus.SUCCESS) {
      Map<String, LearningEngineTask.ExecutionStatus> taskStatuses =
          logAnalysisService.getTaskStatus(List.of(analysisState.getWorkerTaskId()));
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
              "Unknown worker state when executing deployment log-feedback analysis: " + taskStatus);
      }
    }
    return AnalysisStatus.SUCCESS;
  }

  @Override
  public AnalysisState handleRerun(DeploymentLogFeedbackState analysisState) {
    analysisState.setRetryCount(analysisState.getRetryCount() + 1);
    analysisState.setStatus(AnalysisStatus.RUNNING);
    return execute(analysisState);
  }

  @Override
  public AnalysisState handleRunning(DeploymentLogFeedbackState analysisState) {
    return analysisState;
  }

  @Override
  public AnalysisState handleSuccess(DeploymentLogFeedbackState analysisState) {
    analysisState.setStatus(AnalysisStatus.SUCCESS);
    return analysisState;
  }

  @Override
  public AnalysisState handleTransition(DeploymentLogFeedbackState analysisState) {
    analysisState.setStatus(AnalysisStatus.SUCCESS);
    return analysisState;
  }

  @Override
  public AnalysisState handleRetry(DeploymentLogFeedbackState analysisState) {
    if (analysisState.getRetryCount() >= getMaxRetry()) {
      analysisState.setStatus(AnalysisStatus.FAILED);
    } else {
      return handleRerun(analysisState);
    }
    return analysisState;
  }
}