/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.statemachine.services.api;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.cvng.analysis.entities.LearningEngineTask;
import io.harness.cvng.analysis.services.api.LogClusterService;
import io.harness.cvng.statemachine.beans.AnalysisInput;
import io.harness.cvng.statemachine.beans.AnalysisState;
import io.harness.cvng.statemachine.beans.AnalysisStatus;
import io.harness.cvng.statemachine.entities.LogClusterState;

import com.google.inject.Inject;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class LogClusterStateExecutor<T extends LogClusterState> extends AnalysisStateExecutor<T> {
  @Inject protected transient LogClusterService logClusterService;

  protected abstract List<String> scheduleAnalysis(AnalysisInput analysisInput, LogClusterState analysisState);

  @Override
  public AnalysisState execute(T analysisState) {
    List<String> taskIds = scheduleAnalysis(analysisState.getInputs(), analysisState);
    if (isNotEmpty(taskIds)) {
      if (analysisState.getWorkerTaskIds() == null) {
        analysisState.setWorkerTaskIds(new HashSet<>());
      }
      analysisState.getWorkerTaskIds().addAll(taskIds);
      analysisState.setStatus(AnalysisStatus.RUNNING);
      log.info("Executing ServiceGuardLogClusterState for input: {}. Created {} tasks", analysisState.getInputs(),
          analysisState.getWorkerTaskIds().size());
    } else {
      log.error(
          "Executing ServiceGuardLogClusterState for input: {}. No clustering tasks were created. This is an error state",
          analysisState.getInputs());
      throw new IllegalStateException("LogClusterState for input: " + analysisState.getInputs()
          + ". No clustering tasks were created."
          + " This is an error state");
    }
    return analysisState;
  }

  @Override
  public AnalysisStatus getExecutionStatus(T analysisState) {
    if (!analysisState.getStatus().equals(AnalysisStatus.SUCCESS)) {
      if (analysisState.getWorkerTaskIds() == null) {
        return AnalysisStatus.CREATED;
      }
      Map<String, LearningEngineTask.ExecutionStatus> taskStatuses =
          logClusterService.getTaskStatus(analysisState.getWorkerTaskIds());
      Map<LearningEngineTask.ExecutionStatus, Set<String>> statusTaskMap = new HashMap<>();
      taskStatuses.forEach((taskId, taskStatus) -> {
        if (!statusTaskMap.containsKey(taskStatus)) {
          statusTaskMap.put(taskStatus, new HashSet<>());
        }
        statusTaskMap.get(taskStatus).add(taskId);
      });

      log.info("Current statuses of worker tasks with inputs {} is {}", analysisState.getInputs(), statusTaskMap);
      if (statusTaskMap.containsKey(LearningEngineTask.ExecutionStatus.SUCCESS)
          && analysisState.getWorkerTaskIds().size()
              == statusTaskMap.get(LearningEngineTask.ExecutionStatus.SUCCESS).size()) {
        log.info("All worker tasks have succeeded.");
        return AnalysisStatus.TRANSITION;
      } else if (statusTaskMap.containsKey(LearningEngineTask.ExecutionStatus.RUNNING)
          || statusTaskMap.containsKey(LearningEngineTask.ExecutionStatus.QUEUED)) {
        return AnalysisStatus.RUNNING;
      } else {
        return AnalysisStatus.RETRY;
      }
    }
    return AnalysisStatus.TRANSITION;
  }

  @Override
  public AnalysisState handleRerun(T analysisState) {
    analysisState.setRetryCount(analysisState.getRetryCount() + 1);
    log.info("In log clustering for Inputs {}, cleaning up worker task. Old taskIDs: {}", analysisState.getInputs(),
        analysisState.getWorkerTaskIds());
    analysisState.setWorkerTaskIds(new HashSet<>());
    return execute(analysisState);
  }

  @Override
  public AnalysisState handleRunning(T analysisState) {
    return analysisState;
  }

  @Override
  public AnalysisState handleSuccess(T analysisState) {
    analysisState.setStatus(AnalysisStatus.SUCCESS);
    return analysisState;
  }
}
