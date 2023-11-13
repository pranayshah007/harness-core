/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.statemachine.services.api;

import io.harness.cvng.statemachine.beans.AnalysisState;
import io.harness.cvng.statemachine.beans.AnalysisStatus;
import io.harness.cvng.statemachine.entities.SLIMetricLessAnalysisState;

import com.google.inject.Inject;
import java.time.Clock;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SLIMetricLessAnalysisStateExecutor extends AnalysisStateExecutor<SLIMetricLessAnalysisState> {
  @Inject private Clock clock;
  @Override
  public AnalysisState execute(SLIMetricLessAnalysisState analysisState) {
    return analysisState;
  }

  @Override
  public AnalysisStatus getExecutionStatus(SLIMetricLessAnalysisState analysisState) {
    return analysisState.getStatus();
  }

  @Override
  public AnalysisState handleRerun(SLIMetricLessAnalysisState analysisState) {
    analysisState.setRetryCount(analysisState.getRetryCount() + 1);
    analysisState.setStatus(AnalysisStatus.RETRY);
    return execute(analysisState);
  }

  @Override
  public AnalysisState handleRunning(SLIMetricLessAnalysisState analysisState) {
    return analysisState;
  }

  @Override
  public AnalysisState handleSuccess(SLIMetricLessAnalysisState analysisState) {
    analysisState.setStatus(AnalysisStatus.SUCCESS);
    handleFinalStatuses(analysisState);
    return analysisState;
  }

  @Override
  public AnalysisState handleTransition(SLIMetricLessAnalysisState analysisState) {
    return analysisState;
  }
}
