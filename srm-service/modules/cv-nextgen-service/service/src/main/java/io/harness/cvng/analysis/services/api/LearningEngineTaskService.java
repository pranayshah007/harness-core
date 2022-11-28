/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.analysis.services.api;

import io.harness.cvng.analysis.beans.ExceptionInfo;
import io.harness.cvng.analysis.entities.LearningEngineTask;
import io.harness.cvng.analysis.entities.LearningEngineTask.ExecutionStatus;
import io.harness.cvng.analysis.entities.LearningEngineTask.LearningEngineTaskType;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface LearningEngineTaskService {
  LearningEngineTask getNextAnalysisTask();
  LearningEngineTask getNextAnalysisTask(List<LearningEngineTaskType> taskType);
  List<String> createLearningEngineTasks(List<LearningEngineTask> tasks);
  String createLearningEngineTask(LearningEngineTask learningEngineTask);
  Map<String, ExecutionStatus> getTaskStatus(Set<String> taskIds);
  void markCompleted(String taskId);
  void markFailure(String taskId, ExceptionInfo exceptionInfo);
  String createFailureUrl(String taskId);
  LearningEngineTask get(String learningEngineTaskId);
}
