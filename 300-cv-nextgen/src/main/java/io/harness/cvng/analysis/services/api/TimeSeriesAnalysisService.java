/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.analysis.services.api;

import io.harness.cvng.analysis.beans.DeploymentTimeSeriesAnalysisDTO;
import io.harness.cvng.analysis.beans.ServiceGuardTimeSeriesAnalysisDTO;
import io.harness.cvng.analysis.beans.ServiceGuardTxnMetricAnalysisDataDTO.MetricSumDTO;
import io.harness.cvng.analysis.beans.TimeSeriesAnomaliesDTO;
import io.harness.cvng.analysis.beans.TimeSeriesRecordDTO;
import io.harness.cvng.analysis.entities.LearningEngineTask.ExecutionStatus;
import io.harness.cvng.analysis.entities.TimeSeriesRiskSummary.TransactionMetricRisk;
import io.harness.cvng.analysis.entities.TimeSeriesShortTermHistory;
import io.harness.cvng.core.beans.TimeSeriesMetricDefinition;
import io.harness.cvng.statemachine.beans.AnalysisInput;
import io.harness.cvng.statemachine.beans.AnalysisStatus;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface TimeSeriesAnalysisService {
  List<String> scheduleServiceGuardAnalysis(AnalysisInput input);
  List<String> scheduleCanaryVerificationTaskAnalysis(AnalysisInput analysisInput);
  List<String> scheduleTestVerificationTaskAnalysis(AnalysisInput analysisInput);
  void logDeploymentVerificationProgress(AnalysisInput analysisInput, AnalysisStatus analysisStatus);

  Map<String, ExecutionStatus> getTaskStatus(String verificationTaskId, Set<String> taskIds);
  Map<String, Map<String, List<MetricSumDTO>>> getCumulativeSums(
      String verificationTaskId, Instant startTime, Instant endTime);
  Map<String, Map<String, List<Double>>> getShortTermHistory(String verificationTaskId);
  Map<String, Map<String, List<TimeSeriesAnomaliesDTO>>> getLongTermAnomalies(String verificationTaskId);
  List<TimeSeriesMetricDefinition> getMetricTemplate(String cvConfigId);
  List<TimeSeriesRecordDTO> getTimeSeriesRecordDTOs(String verificationTaskId, Instant startTime, Instant endTime);
  void saveAnalysis(String taskId, ServiceGuardTimeSeriesAnalysisDTO analysis);
  void saveAnalysis(String taskId, DeploymentTimeSeriesAnalysisDTO analysis);

  List<TransactionMetricRisk> getTopTimeSeriesTransactionMetricRisk(
      List<String> verificationTaskIds, Instant startTime, Instant endTime);

  void saveShortTermHistory(TimeSeriesShortTermHistory shortTermHistory);
}
