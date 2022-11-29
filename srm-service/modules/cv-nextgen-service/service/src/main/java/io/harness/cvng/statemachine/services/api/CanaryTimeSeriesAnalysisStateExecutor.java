/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.statemachine.services.api;

import io.harness.cvng.statemachine.beans.AnalysisInput;
import io.harness.cvng.statemachine.beans.AnalysisState;
import io.harness.cvng.statemachine.beans.AnalysisStatus;
import io.harness.cvng.statemachine.entities.CanaryTimeSeriesAnalysisState;

import java.util.List;

public class CanaryTimeSeriesAnalysisStateExecutor
    extends TimeSeriesAnalysisStateExecutor<CanaryTimeSeriesAnalysisState> {
  @Override
  protected List<String> scheduleAnalysis(AnalysisInput analysisInput) {
    return timeSeriesAnalysisService.scheduleCanaryVerificationTaskAnalysis(analysisInput);
  }

  @Override
  public void handleFinalStatuses(CanaryTimeSeriesAnalysisState analysisState) {
    timeSeriesAnalysisService.logDeploymentVerificationProgress(analysisState.getInputs(), analysisState.getStatus());
  }

  @Override
  public AnalysisState handleRetry(CanaryTimeSeriesAnalysisState analysisState) {
    if (analysisState.getRetryCount() >= getMaxRetry()) {
      analysisState.setStatus(AnalysisStatus.FAILED);
    } else {
      return handleRerun(analysisState);
    }
    return analysisState;
  }
}
