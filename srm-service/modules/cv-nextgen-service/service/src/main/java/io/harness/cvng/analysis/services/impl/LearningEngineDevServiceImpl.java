/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.analysis.services.impl;

import static io.harness.persistence.HQuery.excludeAuthority;

import io.harness.cvng.analysis.entities.ClusteredLog;
import io.harness.cvng.analysis.entities.ClusteredLog.ClusteredLogKeys;
import io.harness.cvng.analysis.entities.LogAnalysisRecord;
import io.harness.cvng.analysis.entities.LogAnalysisRecord.LogAnalysisRecordKeys;
import io.harness.cvng.analysis.entities.LogAnalysisResult;
import io.harness.cvng.analysis.entities.LogAnalysisResult.LogAnalysisResultKeys;
import io.harness.cvng.analysis.entities.TimeSeriesAnomalousPatterns;
import io.harness.cvng.analysis.entities.TimeSeriesAnomalousPatterns.TimeSeriesAnomalousPatternsKeys;
import io.harness.cvng.analysis.entities.TimeSeriesCumulativeSums;
import io.harness.cvng.analysis.entities.TimeSeriesCumulativeSums.TimeSeriesCumulativeSumsKeys;
import io.harness.cvng.analysis.entities.TimeSeriesRiskSummary;
import io.harness.cvng.analysis.entities.TimeSeriesRiskSummary.TimeSeriesRiskSummaryKeys;
import io.harness.cvng.analysis.entities.TimeSeriesShortTermHistory;
import io.harness.cvng.analysis.services.api.LearningEngineDevService;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import java.time.Instant;
import java.util.List;

public class LearningEngineDevServiceImpl implements LearningEngineDevService {
  @Inject HPersistence hPersistence;

  @Override
  public List<TimeSeriesRiskSummary> getRiskSummariesByTimeRange(
      String verificationTaskId, Instant startTime, Instant endTime) {
    return hPersistence.createQuery(TimeSeriesRiskSummary.class, excludeAuthority)
        .filter(TimeSeriesRiskSummaryKeys.verificationTaskId, verificationTaskId)
        .field(TimeSeriesRiskSummaryKeys.analysisStartTime)
        .greaterThanOrEq(startTime)
        .field(TimeSeriesRiskSummaryKeys.analysisEndTime)
        .lessThanOrEq(endTime)
        .asList();
  }

  @Override
  public TimeSeriesAnomalousPatterns getTimeSeriesAnomalousPatternsByTaskId(String verificationTaskId) {
    TimeSeriesAnomalousPatterns anomalousPatterns =
        hPersistence.createQuery(TimeSeriesAnomalousPatterns.class, excludeAuthority)
            .filter(TimeSeriesAnomalousPatternsKeys.verificationTaskId, verificationTaskId)
            .get();
    anomalousPatterns.deCompressAnomalies();
    return anomalousPatterns;
  }

  @Override
  public List<TimeSeriesCumulativeSums> getTimeSeriesCumulativeSumsByTimeRange(
      String verificationTaskId, Instant startTime, Instant endTime) {
    return hPersistence.createQuery(TimeSeriesCumulativeSums.class, excludeAuthority)
        .filter(TimeSeriesCumulativeSumsKeys.verificationTaskId, verificationTaskId)
        .field(TimeSeriesCumulativeSumsKeys.analysisStartTime)
        .greaterThanOrEq(startTime)
        .field(TimeSeriesCumulativeSumsKeys.analysisEndTime)
        .lessThanOrEq(endTime)
        .asList();
  }

  @Override
  public TimeSeriesShortTermHistory getTimeSeriesShortTermHistoryByTaskId(String verificationTaskId) {
    TimeSeriesShortTermHistory shortTermHistory =
        hPersistence.createQuery(TimeSeriesShortTermHistory.class, excludeAuthority)
            .filter(TimeSeriesCumulativeSumsKeys.verificationTaskId, verificationTaskId)
            .get();
    shortTermHistory.deCompressMetricHistories();
    return shortTermHistory;
  }

  @Override
  public List<LogAnalysisRecord> getLogAnalysisRecordsByTimeRange(
      String verificationTaskId, Instant startTime, Instant endTime) {
    return hPersistence.createQuery(LogAnalysisRecord.class, excludeAuthority)
        .filter(LogAnalysisRecordKeys.verificationTaskId, verificationTaskId)
        .field(LogAnalysisRecordKeys.analysisStartTime)
        .greaterThanOrEq(startTime)
        .field(LogAnalysisRecordKeys.analysisEndTime)
        .lessThanOrEq(endTime)
        .asList();
  }

  @Override
  public List<LogAnalysisResult> getLogAnalysisResultByTimeRange(
      String verificationTaskId, Instant startTime, Instant endTime) {
    return hPersistence.createQuery(LogAnalysisResult.class, excludeAuthority)
        .filter(LogAnalysisResultKeys.verificationTaskId, verificationTaskId)
        .field(LogAnalysisResultKeys.analysisStartTime)
        .greaterThanOrEq(startTime)
        .field(LogAnalysisResultKeys.analysisEndTime)
        .lessThanOrEq(endTime)
        .asList();
  }

  @Override
  public List<ClusteredLog> getClusteredLogsByTimeRange(String verificationTaskId, Instant startTime, Instant endTime) {
    return hPersistence.createQuery(ClusteredLog.class, excludeAuthority)
        .filter(LogAnalysisResultKeys.verificationTaskId, verificationTaskId)
        .field(ClusteredLogKeys.timestamp)
        .greaterThanOrEq(startTime)
        .field(ClusteredLogKeys.timestamp)
        .lessThanOrEq(endTime)
        .asList();
  }
}
