/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.statemachine.services.api;

import io.harness.cvng.core.services.api.VerificationTaskService;
import io.harness.cvng.core.utils.DateTimeUtils;
import io.harness.cvng.metrics.CVNGMetricsUtils;
import io.harness.cvng.metrics.beans.SLOMetricContext;
import io.harness.cvng.servicelevelobjective.entities.CompositeSLORecord;
import io.harness.cvng.servicelevelobjective.entities.CompositeServiceLevelObjective;
import io.harness.cvng.servicelevelobjective.services.api.CompositeSLORecordService;
import io.harness.cvng.servicelevelobjective.services.impl.ServiceLevelObjectiveV2ServiceImpl;
import io.harness.cvng.statemachine.beans.AnalysisInput;
import io.harness.cvng.statemachine.beans.AnalysisState;
import io.harness.cvng.statemachine.beans.AnalysisStatus;
import io.harness.cvng.statemachine.entities.CompositeSLOMetricAnalysisState;
import io.harness.metrics.service.api.MetricService;

import com.google.inject.Inject;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CompositeSLOMetricAnalysisStateExecutor extends AnalysisStateExecutor<CompositeSLOMetricAnalysisState> {
  public static final int MAXIMUM_SLO_WINDOW_PROCESSING_HOURS = 12;
  @Inject private ServiceLevelObjectiveV2ServiceImpl serviceLevelObjectiveV2Service;

  @Inject private CompositeSLORecordService compositeSLORecordService;
  @Inject private VerificationTaskService verificationTaskService;
  @Inject private OrchestrationService orchestrationService;
  @Inject private Clock clock;

  @Inject private MetricService metricService;
  @Override
  public AnalysisState execute(CompositeSLOMetricAnalysisState analysisState) {
    CompositeServiceLevelObjective compositeServiceLevelObjective = null;
    String sloId = null;
    try {
      String verificationTaskId = analysisState.getInputs().getVerificationTaskId();
      // here startTime will be the prv Data endTime and endTime will be the current time.
      sloId = verificationTaskService.getCompositeSLOId(verificationTaskId);
      compositeServiceLevelObjective = (CompositeServiceLevelObjective) serviceLevelObjectiveV2Service.get(sloId);
      Instant startTime = getStartTime(compositeServiceLevelObjective);
      Instant endTime = getEndtimeWithMaxWindowSize(startTime);
      if (endTime.isAfter(startTime)) {
        compositeSLORecordService.create(compositeServiceLevelObjective, startTime, endTime, verificationTaskId);
      }
      try (SLOMetricContext sloMetricContext = new SLOMetricContext(compositeServiceLevelObjective)) {
        metricService.recordDuration(CVNGMetricsUtils.SLO_DATA_ANALYSIS_METRIC,
            Duration.between(analysisState.getInputs().getStartTime(), clock.instant()));
      }
      analysisState.setStatus(AnalysisStatus.SUCCESS);
    } catch (Exception exception) {
      if (compositeServiceLevelObjective == null) {
        analysisState.setStatus(AnalysisStatus.TERMINATED);
        log.info(
            "The composite slo with id {} has already been deleted, marking current analysisState to be cleaned up : {}",
            sloId, analysisState.getWorkerTaskId());
      } else {
        log.warn(String.format("Composite SLO Execute for sloId: {} failed with:",
                     analysisState.getInputs().getVerificationTaskId()),
            exception);
        analysisState.setStatus(AnalysisStatus.RETRY);
      }
    }
    return analysisState;
  }

  private Instant getStartTime(CompositeServiceLevelObjective compositeServiceLevelObjective) {
    LocalDateTime currentLocalDate =
        LocalDateTime.ofInstant(clock.instant(), compositeServiceLevelObjective.getZoneOffset());
    Instant startTimeForCurrentSLOTarget = compositeServiceLevelObjective.getCurrentTimeRange(currentLocalDate)
                                               .getStartTime(compositeServiceLevelObjective.getZoneOffset());
    Instant sloStartedAtTime = Instant.ofEpochMilli(compositeServiceLevelObjective.getStartedAt());
    Instant startTime =
        (startTimeForCurrentSLOTarget.isAfter(sloStartedAtTime)) ? startTimeForCurrentSLOTarget : sloStartedAtTime;
    CompositeSLORecord lastSLORecord = compositeSLORecordService.getLatestCompositeSLORecordWithVersion(
        compositeServiceLevelObjective.getUuid(), startTime,
        compositeServiceLevelObjective.getVersion()); // We have to have both written parallel without fail.
    return roundUpTo5MinuteBoundary(lastSLORecord, startTime);
  }

  private static Instant roundUpTo5MinuteBoundary(CompositeSLORecord lastSLORecord, Instant startTime) {
    if (lastSLORecord != null) {
      startTime = Instant.ofEpochSecond(lastSLORecord.getEpochMinute() * 60 + 60);
    }
    return DateTimeUtils.roundUpTo5MinBoundary(startTime);
  }

  private Instant getEndtimeWithMaxWindowSize(Instant startTime) {
    Instant endTime = clock.instant();
    if (endTime.isAfter(
            startTime.plus(MAXIMUM_SLO_WINDOW_PROCESSING_HOURS, ChronoUnit.HOURS))) { // restrict max window to 12 hrs
      endTime = startTime.plus(MAXIMUM_SLO_WINDOW_PROCESSING_HOURS, ChronoUnit.HOURS);
    }
    return DateTimeUtils.roundDownTo5MinBoundary(endTime);
  }

  @Override
  public AnalysisStatus getExecutionStatus(CompositeSLOMetricAnalysisState analysisState) {
    return analysisState.getStatus();
  }

  @Override
  public AnalysisState handleRerun(CompositeSLOMetricAnalysisState analysisState) {
    // increment the retryCount without caring for the max
    // clean up state in underlying worker and then execute
    analysisState.setRetryCount(analysisState.getRetryCount() + 1);
    log.info("In composite slo analysis for Inputs {}, cleaning up worker task. Old taskID: {}",
        analysisState.getInputs(), analysisState.getWorkerTaskId());
    analysisState.setStatus(AnalysisStatus.RETRY);
    return execute(analysisState);
  }

  @Override
  public AnalysisState handleRunning(CompositeSLOMetricAnalysisState analysisState) {
    return analysisState;
  }

  @Override
  public AnalysisState handleSuccess(CompositeSLOMetricAnalysisState analysisState) {
    analysisState.setStatus(AnalysisStatus.SUCCESS);
    return analysisState;
  }

  @Override
  public AnalysisState handleTransition(CompositeSLOMetricAnalysisState analysisState) {
    return analysisState;
  }

  @Override
  public void handleFinalStatuses(CompositeSLOMetricAnalysisState analysisState) {
    String verificationTaskId = analysisState.getInputs().getVerificationTaskId();
    String sloId = verificationTaskService.getCompositeSLOId(verificationTaskId);
    CompositeServiceLevelObjective compositeServiceLevelObjective =
        (CompositeServiceLevelObjective) serviceLevelObjectiveV2Service.get(sloId);
    if (compositeServiceLevelObjective != null) {
      orchestrationService.queueAnalysisWithoutEventPublish(compositeServiceLevelObjective.getAccountId(),
          AnalysisInput.builder()
              .verificationTaskId(verificationTaskId)
              .startTime(Instant.now())
              .endTime(Instant.now())
              .build());
    }
  }
}
