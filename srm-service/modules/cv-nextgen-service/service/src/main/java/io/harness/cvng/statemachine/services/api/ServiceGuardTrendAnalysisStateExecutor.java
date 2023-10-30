/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.statemachine.services.api;

import io.harness.cvng.analysis.entities.LearningEngineTask;
import io.harness.cvng.analysis.services.api.TrendAnalysisService;
import io.harness.cvng.statemachine.beans.AnalysisState;
import io.harness.cvng.statemachine.beans.AnalysisStatus;
import io.harness.cvng.statemachine.entities.ServiceGuardTrendAnalysisState;
import io.harness.cvng.statemachine.exception.AnalysisStateMachineException;

import com.google.inject.Inject;
import java.util.Collections;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ServiceGuardTrendAnalysisStateExecutor extends AnalysisStateExecutor<ServiceGuardTrendAnalysisState> {
  @Inject private transient TrendAnalysisService trendAnalysisService;

  @Override
  public AnalysisState execute(ServiceGuardTrendAnalysisState analysisState) {
    analysisState.setWorkerTaskId(trendAnalysisService.scheduleTrendAnalysisTask(analysisState.getInputs()));
    analysisState.setStatus(AnalysisStatus.RUNNING);
    log.info("Executing service guard trend analysis for {}", analysisState.getInputs());
    return analysisState;
  }

  @Override
  public AnalysisStatus getExecutionStatus(ServiceGuardTrendAnalysisState analysisState) {
    if (analysisState.getStatus() != AnalysisStatus.SUCCESS) {
      Map<String, LearningEngineTask.ExecutionStatus> taskStatuses =
          trendAnalysisService.getTaskStatus(Collections.singletonList(analysisState.getWorkerTaskId()));
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
              "Unknown worker state when executing service guard trend analysis: " + taskStatus);
      }
    }
    return AnalysisStatus.SUCCESS;
  }

  @Override
  public AnalysisState handleRerun(ServiceGuardTrendAnalysisState analysisState) {
    // increment the retryCount without caring for the max
    // clean up state in underlying worker and then execute
    analysisState.setRetryCount(analysisState.getRetryCount() + 1);
    log.info("In service guard trend analysis for Inputs {}, cleaning up worker task. Old taskID: {}",
        analysisState.getInputs(), analysisState.getWorkerTaskId());
    analysisState.setWorkerTaskId(null);
    execute(analysisState);
    return analysisState;
  }

  @Override
  public AnalysisState handleRunning(ServiceGuardTrendAnalysisState analysisState) {
    return analysisState;
  }

  @Override
  public AnalysisState handleSuccess(ServiceGuardTrendAnalysisState analysisState) {
    analysisState.setStatus(AnalysisStatus.SUCCESS);
    return analysisState;
  }

  @Override
  public AnalysisState handleTransition(ServiceGuardTrendAnalysisState analysisState) {
    analysisState.setStatus(AnalysisStatus.SUCCESS);
    return analysisState;
  }
}
