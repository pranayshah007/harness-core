/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.statemachine.services.api;

import io.harness.cvng.analysis.entities.HealthVerificationPeriod;
import io.harness.cvng.analysis.services.api.HealthVerificationService;
import io.harness.cvng.statemachine.beans.AnalysisInput;
import io.harness.cvng.statemachine.beans.AnalysisState;
import io.harness.cvng.statemachine.beans.AnalysisStatus;
import io.harness.cvng.statemachine.entities.ActivityVerificationState;
import io.harness.cvng.statemachine.exception.AnalysisStateMachineException;

import com.google.inject.Inject;
import java.time.Instant;

public class ActivityVerificationStateExecutor extends AnalysisStateExecutor<ActivityVerificationState> {
  @Inject private transient HealthVerificationService healthVerificationService;

  @Override
  public AnalysisState execute(ActivityVerificationState analysisState) {
    Instant analysisCompletedUntil =
        healthVerificationService.aggregateActivityAnalysis(analysisState.getInputs().getVerificationTaskId(),
            analysisState.getInputs().getStartTime(), analysisState.getInputs().getEndTime(),
            analysisState.getAnalysisCompletedUntil(), analysisState.getHealthVerificationPeriod());
    analysisState.setAnalysisCompletedUntil(analysisCompletedUntil);
    analysisState.setStatus(AnalysisStatus.RUNNING);
    return analysisState;
  }

  @Override
  public AnalysisStatus getExecutionStatus(ActivityVerificationState analysisState) {
    if (analysisState.isAllAnalysesComplete()
        && analysisState.getHealthVerificationPeriod().equals(HealthVerificationPeriod.PRE_ACTIVITY)) {
      analysisState.setStatus(AnalysisStatus.TRANSITION);
    } else if (analysisState.isAllAnalysesComplete()
        && analysisState.getHealthVerificationPeriod().equals(HealthVerificationPeriod.POST_ACTIVITY)) {
      analysisState.setStatus(AnalysisStatus.SUCCESS);
    }
    if (analysisState.getAnalysisCompletedUntil().toEpochMilli() != 0) {
      healthVerificationService.updateProgress(analysisState.getInputs().getVerificationTaskId(),
          analysisState.getAnalysisCompletedUntil(), analysisState.getStatus(), false);
    }
    // TODO: When should we mark this as failed/error ?
    // TODO: What to do if the analysis had stopped for some reason for one of the underlying configs.
    return analysisState.getStatus();
  }

  @Override
  public AnalysisState handleRerun(ActivityVerificationState analysisState) {
    return null;
  }

  @Override
  public AnalysisState handleRunning(ActivityVerificationState analysisState) {
    // update any new analyses if possible.
    Instant analysisCompletedUntil =
        healthVerificationService.aggregateActivityAnalysis(analysisState.getInputs().getVerificationTaskId(),
            analysisState.getInputs().getStartTime(), analysisState.getInputs().getEndTime(),
            analysisState.getAnalysisCompletedUntil(), analysisState.getHealthVerificationPeriod());
    analysisState.setAnalysisCompletedUntil(analysisCompletedUntil);
    return analysisState;
  }

  @Override
  public AnalysisState handleSuccess(ActivityVerificationState analysisState) {
    analysisState.setStatus(AnalysisStatus.COMPLETED);
    healthVerificationService.updateProgress(analysisState.getInputs().getVerificationTaskId(),
        analysisState.getAnalysisCompletedUntil(), analysisState.getStatus(), true);
    return analysisState;
  }

  @Override
  public AnalysisState handleTransition(ActivityVerificationState analysisState) {
    switch (analysisState.getHealthVerificationPeriod()) {
      case PRE_ACTIVITY:
        ActivityVerificationState postActivityVerificationState =
            ActivityVerificationState.builder()
                .preActivityVerificationStartTime(analysisState.getPreActivityVerificationStartTime())
                .postActivityVerificationStartTime(analysisState.getPostActivityVerificationStartTime())
                .healthVerificationPeriod(HealthVerificationPeriod.POST_ACTIVITY)
                .build();
        AnalysisInput input =
            AnalysisInput.builder()
                .verificationTaskId(analysisState.getInputs().getVerificationTaskId())
                .startTime(analysisState.getPostActivityVerificationStartTime())
                .endTime(analysisState.getPostActivityVerificationStartTime().plus(analysisState.getDurationObj()))
                .build();
        postActivityVerificationState.setInputs(input);
        postActivityVerificationState.setStatus(AnalysisStatus.CREATED);
        return postActivityVerificationState;

      case POST_ACTIVITY:
        analysisState.setStatus(AnalysisStatus.SUCCESS);
        return analysisState;
      default:
        throw new AnalysisStateMachineException("Unknown period in health verification");
    }
  }

  @Override
  public AnalysisState handleRetry(ActivityVerificationState analysisState) {
    return null;
  }

  @Override
  public void handleFinalStatuses(ActivityVerificationState analysisState) {
    healthVerificationService.updateProgress(analysisState.getInputs().getVerificationTaskId(),
        analysisState.getAnalysisCompletedUntil(), analysisState.getStatus(), true);
  }
}
