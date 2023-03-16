/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.analysis.services.api;

import io.harness.cvng.analysis.beans.DeploymentLogAnalysisDTO;
import io.harness.cvng.analysis.beans.LogAnalysisDTO;
import io.harness.cvng.analysis.beans.LogClusterDTO;
import io.harness.cvng.analysis.entities.LearningEngineTask.ExecutionStatus;
import io.harness.cvng.analysis.entities.LogAnalysisCluster;
import io.harness.cvng.analysis.entities.LogAnalysisResult;
import io.harness.cvng.analysis.entities.LogAnalysisResult.LogAnalysisTag;
import io.harness.cvng.core.beans.LogFeedback;
import io.harness.cvng.statemachine.beans.AnalysisInput;
import io.harness.cvng.statemachine.beans.AnalysisStatus;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface LogAnalysisService {
  String scheduleDeploymentLogFeedbackTask(AnalysisInput input);
  String scheduleServiceGuardLogAnalysisTask(AnalysisInput input);
  String scheduleDeploymentLogAnalysisTask(AnalysisInput analysisInput);
  Map<String, ExecutionStatus> getTaskStatus(List<String> taskIds);
  List<LogClusterDTO> getTestData(String verificationTaskId, Instant analysisStartTime, Instant analysisEndTime);
  List<LogClusterDTO> getTestDataForDeploymentLog(
      String verificationTaskId, Instant analysisStartTime, Instant analysisEndTime, String commaSeparatedHosts);
  List<LogAnalysisCluster> getPreviousAnalysis(
      String verificationTaskId, Instant analysisStartTime, Instant analysisEndTime);

  DeploymentLogAnalysisDTO getPreviousDeploymentAnalysis(
      String verificationTaskId, Instant analysisStartTime, Instant analysisEndTime);

  void saveAnalysis(String taskId, LogAnalysisDTO analysisBody);
  List<LogAnalysisCluster> getAnalysisClusters(String verificationTaskId, Set<Long> labels);
  List<LogAnalysisResult> getAnalysisResults(
      String verificationTaskId, List<LogAnalysisTag> tags, Instant startTime, Instant endTime);
  List<LogAnalysisResult> getAnalysisResults(String verificationTaskId, Instant startTime, Instant endTime);
  void saveAnalysis(String learningEngineTaskId, DeploymentLogAnalysisDTO deploymentLogAnalysisDTO);
  void logDeploymentVerificationProgress(AnalysisInput inputs, AnalysisStatus finalStatus);
  List<LogFeedback> getLogFeedbackList(String verificationTaskId);
  List<LogAnalysisResult> getTopLogAnalysisResults(
      List<String> verificationTaskIds, Instant startTime, Instant endTime);
}
