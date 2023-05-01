/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.statemachine.services.api;

import io.harness.cvng.core.services.api.VerificationTaskService;
import io.harness.cvng.metrics.CVNGMetricsUtils;
import io.harness.cvng.metrics.beans.SLOMetricContext;
import io.harness.cvng.servicelevelobjective.entities.CompositeServiceLevelObjective;
import io.harness.cvng.servicelevelobjective.services.api.CompositeSLORecordService;
import io.harness.cvng.servicelevelobjective.services.impl.ServiceLevelObjectiveV2ServiceImpl;
import io.harness.cvng.statemachine.beans.AnalysisState;
import io.harness.cvng.statemachine.beans.AnalysisStatus;
import io.harness.cvng.statemachine.entities.CompositeSLORestoreMetricAnalysisState;
import io.harness.metrics.service.api.MetricService;

import com.google.inject.Inject;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CompositeSLORestoreMetricAnalysisStateExecutor
    extends AnalysisStateExecutor<CompositeSLORestoreMetricAnalysisState> {
  @Inject private ServiceLevelObjectiveV2ServiceImpl serviceLevelObjectiveV2Service;

  @Inject private CompositeSLORecordService compositeSLORecordService;

  @Inject private VerificationTaskService verificationTaskService;
  @Inject private Clock clock;

  @Inject private MetricService metricService;
  @Override
  public AnalysisState execute(CompositeSLORestoreMetricAnalysisState analysisState) {
    String verificationTaskId = analysisState.getInputs().getVerificationTaskId();
    String sloId = verificationTaskService.getCompositeSLOId(verificationTaskId);
    CompositeServiceLevelObjective compositeServiceLevelObjective =
        (CompositeServiceLevelObjective) serviceLevelObjectiveV2Service.get(sloId);
    LocalDateTime currentLocalDate =
        LocalDateTime.ofInstant(clock.instant(), compositeServiceLevelObjective.getZoneOffset());
    Instant startTimeForCurrentRange = compositeServiceLevelObjective.getCurrentTimeRange(currentLocalDate)
                                           .getStartTime(compositeServiceLevelObjective.getZoneOffset());
    Instant startedAtTime = Instant.ofEpochMilli(compositeServiceLevelObjective.getStartedAt());
    Instant startTime = (startTimeForCurrentRange.isAfter(startedAtTime)) ? startTimeForCurrentRange : startedAtTime;
    startTime = startTime.isAfter(analysisState.getInputs().getStartTime()) ? startTime
                                                                            : analysisState.getInputs().getStartTime();
    Instant endTime = analysisState.getInputs().getEndTime();
    compositeSLORecordService.create(compositeServiceLevelObjective, startTime, endTime, verificationTaskId);
    try (SLOMetricContext sloMetricContext = new SLOMetricContext(compositeServiceLevelObjective)) {
      metricService.recordDuration(CVNGMetricsUtils.SLO_DATA_ANALYSIS_METRIC,
          Duration.between(clock.instant(), analysisState.getInputs().getStartTime()));
    }
    analysisState.setStatus(AnalysisStatus.SUCCESS);
    return analysisState;
  }

  @Override
  public AnalysisStatus getExecutionStatus(CompositeSLORestoreMetricAnalysisState analysisState) {
    return analysisState.getStatus();
  }

  @Override
  public AnalysisState handleRerun(CompositeSLORestoreMetricAnalysisState analysisState) {
    // increment the retryCount without caring for the max
    // clean up state in underlying worker and then execute
    analysisState.setRetryCount(analysisState.getRetryCount() + 1);
    log.info("In composite slo Restore analysis for Inputs {}, cleaning up worker task. Old taskID: {}",
        analysisState.getInputs(), analysisState.getWorkerTaskId());
    analysisState.setWorkerTaskId(null);
    execute(analysisState);
    return analysisState;
  }

  @Override
  public AnalysisState handleRunning(CompositeSLORestoreMetricAnalysisState analysisState) {
    return analysisState;
  }

  @Override
  public AnalysisState handleSuccess(CompositeSLORestoreMetricAnalysisState analysisState) {
    analysisState.setStatus(AnalysisStatus.SUCCESS);
    return analysisState;
  }

  @Override
  public AnalysisState handleTransition(CompositeSLORestoreMetricAnalysisState analysisState) {
    return analysisState;
  }

  @Override
  public AnalysisState handleRetry(CompositeSLORestoreMetricAnalysisState analysisState) {
    if (analysisState.getRetryCount() >= getMaxRetry()) {
      analysisState.setStatus(AnalysisStatus.FAILED);
    } else {
      return handleRerun(analysisState);
    }
    return analysisState;
  }
}
