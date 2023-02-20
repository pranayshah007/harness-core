/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.statemachine.services.api;

import io.harness.cvng.core.services.api.FeatureFlagService;
import io.harness.cvng.core.utils.FeatureFlagNames;
import io.harness.cvng.statemachine.beans.AnalysisInput;
import io.harness.cvng.statemachine.beans.AnalysisState;
import io.harness.cvng.statemachine.beans.AnalysisStatus;
import io.harness.cvng.statemachine.entities.DeploymentLogAnalysisState;
import io.harness.cvng.statemachine.entities.DeploymentLogFeedbackState;
import io.harness.cvng.verificationjob.entities.VerificationJobInstance;
import io.harness.cvng.verificationjob.services.api.VerificationJobInstanceService;

import com.google.inject.Inject;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

public class DeploymentLogAnalysisStateExecutor extends LogAnalysisStateExecutor<DeploymentLogAnalysisState> {
  @Inject VerificationJobInstanceService verificationJobInstanceService;
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
    Instant instant = endTime.minus(duration.toMinutes(), ChronoUnit.MINUTES);
    return instant.equals(startTime);
  }

  @Override
  public AnalysisState handleTransition(DeploymentLogAnalysisState analysisState) {
    analysisState.setStatus(AnalysisStatus.SUCCESS);
    VerificationJobInstance verificationJobInstance = verificationJobInstanceService.getVerificationJobInstance(
        analysisState.getInputs().getVerificationJobInstanceId());
    // once analysis is completed successfully, check whether it's last state or not
    if (isLastState(analysisState, verificationJobInstance)
        && featureFlagService.isFeatureFlagEnabled(
            verificationJobInstance.getAccountId(), FeatureFlagNames.SRM_LOG_FEEDBACK_ENABLE_UI)) {
      analysisState.setStatus(AnalysisStatus.SUCCESS);
      DeploymentLogFeedbackState deploymentLogFeedbackState = new DeploymentLogFeedbackState();
      deploymentLogFeedbackState.setInputs(analysisState.getInputs());
      deploymentLogFeedbackState.setStatus(AnalysisStatus.CREATED);
      return deploymentLogFeedbackState;
    }
    return analysisState;
  }
}
