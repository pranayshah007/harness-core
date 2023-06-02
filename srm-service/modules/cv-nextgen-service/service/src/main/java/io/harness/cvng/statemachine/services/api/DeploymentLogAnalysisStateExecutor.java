/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.statemachine.services.api;

import io.harness.beans.FeatureName;
import io.harness.cvng.analysis.entities.LearningEngineTask;
import io.harness.cvng.beans.job.VerificationJobType;
import io.harness.cvng.core.services.api.FeatureFlagService;
import io.harness.cvng.core.services.api.VerificationTaskService;
import io.harness.cvng.statemachine.beans.AnalysisInput;
import io.harness.cvng.statemachine.beans.AnalysisState;
import io.harness.cvng.statemachine.beans.AnalysisStatus;
import io.harness.cvng.statemachine.entities.DeploymentLogAnalysisState;
import io.harness.cvng.statemachine.entities.DeploymentLogFeedbackState;
import io.harness.cvng.statemachine.exception.AnalysisStateMachineException;
import io.harness.cvng.verificationjob.entities.VerificationJobInstance;
import io.harness.cvng.verificationjob.services.api.VerificationJobInstanceService;

import com.google.inject.Inject;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

public class DeploymentLogAnalysisStateExecutor extends LogAnalysisStateExecutor<DeploymentLogAnalysisState> {
  @Inject VerificationJobInstanceService verificationJobInstanceService;
  @Inject VerificationTaskService verificationTaskService;
  @Inject FeatureFlagService featureFlagService;
  @Override
  protected String scheduleAnalysis(AnalysisInput analysisInput) {
    return logAnalysisService.scheduleDeploymentLogAnalysisTask(analysisInput);
  }

  @Override
  public void handleFinalStatuses(DeploymentLogAnalysisState analysisState) {
    logAnalysisService.logDeploymentVerificationProgress(analysisState.getInputs(), analysisState.getStatus());
  }

  @Override
  public AnalysisState handleRetry(DeploymentLogAnalysisState analysisState) {
    if (analysisState.getRetryCount() >= getMaxRetry()) {
      analysisState.setStatus(AnalysisStatus.FAILED);
    } else {
      return handleRerun(analysisState);
    }
    return analysisState;
  }

  private boolean isLastState(
      DeploymentLogAnalysisState analysisState, VerificationJobInstance verificationJobInstance) {
    Instant endTime = analysisState.getInputs().getEndTime();
    Duration duration = verificationJobInstance.getResolvedJob().getDuration();
    Instant startTime = verificationJobInstance.getStartTime();
    Instant expectedStartTime = endTime.minus(duration.toMinutes(), ChronoUnit.MINUTES);
    return expectedStartTime.equals(startTime);
  }

  @Override
  public AnalysisStatus getExecutionStatus(DeploymentLogAnalysisState analysisState) {
    if (analysisState.getStatus() != AnalysisStatus.SUCCESS) {
      Map<String, LearningEngineTask.ExecutionStatus> taskStatuses =
          logAnalysisService.getTaskStatus(List.of(analysisState.getWorkerTaskId()));
      LearningEngineTask.ExecutionStatus taskStatus = taskStatuses.get(analysisState.getWorkerTaskId());
      // This could be common code for all states.
      switch (taskStatus) {
        case SUCCESS:
          return AnalysisStatus.TRANSITION;
        case FAILED:
        case TIMEOUT:
          return AnalysisStatus.RETRY;
        case QUEUED:
        case RUNNING:
          return AnalysisStatus.RUNNING;
        default:
          throw new AnalysisStateMachineException(
              "Unknown worker state when executing service guard log analysis: " + taskStatus);
      }
    }
    return AnalysisStatus.TRANSITION;
  }

  @Override
  public AnalysisState handleTransition(DeploymentLogAnalysisState analysisState) {
    analysisState.setStatus(AnalysisStatus.SUCCESS);
    String verificationJobInstanceId =
        verificationTaskService.getVerificationJobInstanceId(analysisState.getInputs().getVerificationTaskId());
    VerificationJobInstance verificationJobInstance =
        verificationJobInstanceService.getVerificationJobInstance(verificationJobInstanceId);

    if (verificationJobInstance.getResolvedJob().getType() == VerificationJobType.TEST
        && verificationJobInstance.getResolvedJob().getBaselineVerificationJobInstanceId() == null) {
      return analysisState;
    } else if (isLastState(analysisState, verificationJobInstance)
        && featureFlagService.isFeatureFlagEnabled(
            verificationJobInstance.getAccountId(), FeatureName.SRM_LOG_FEEDBACK_ENABLE_UI.toString())) {
      DeploymentLogFeedbackState deploymentLogFeedbackState = new DeploymentLogFeedbackState();
      deploymentLogFeedbackState.setInputs(analysisState.getInputs());
      deploymentLogFeedbackState.setStatus(AnalysisStatus.CREATED);
      return deploymentLogFeedbackState;
    }
    return analysisState;
  }
}
