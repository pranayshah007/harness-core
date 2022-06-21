/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.analysis.services.impl;

import io.harness.cvng.analysis.entities.HealthVerificationPeriod;
import io.harness.cvng.analysis.entities.LogAnalysisResult;
import io.harness.cvng.analysis.entities.TimeSeriesRiskSummary;
import io.harness.cvng.analysis.services.api.HealthVerificationService;
import io.harness.cvng.analysis.services.api.LogAnalysisService;
import io.harness.cvng.analysis.services.api.TimeSeriesAnalysisService;
import io.harness.cvng.core.entities.CVConfig;
import io.harness.cvng.core.entities.VerificationTask;
import io.harness.cvng.core.entities.VerificationTask.DeploymentInfo;
import io.harness.cvng.core.entities.VerificationTask.TaskType;
import io.harness.cvng.core.services.api.CVConfigService;
import io.harness.cvng.core.services.api.VerificationTaskService;
import io.harness.cvng.dashboard.services.api.HealthVerificationHeatMapService;
import io.harness.cvng.statemachine.beans.AnalysisStatus;
import io.harness.cvng.verificationjob.entities.HealthVerificationJob;
import io.harness.cvng.verificationjob.entities.VerificationJobInstance;
import io.harness.cvng.verificationjob.services.api.VerificationJobInstanceService;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class HealthVerificationServiceImpl implements HealthVerificationService {
  @Inject private VerificationTaskService verificationTaskService;
  @Inject private VerificationJobInstanceService verificationJobInstanceService;
  @Inject private TimeSeriesAnalysisService timeSeriesAnalysisService;
  @Inject private LogAnalysisService logAnalysisService;
  @Inject private CVConfigService cvConfigService;
  @Inject private HealthVerificationHeatMapService healthVerificationHeatMapService;

  @Override
  public Instant aggregateActivityAnalysis(String verificationTaskId, Instant startTime, Instant endTime,
      Instant latestTimeOfAnalysis, HealthVerificationPeriod healthVerificationPeriod) {
    VerificationTask verificationTask = verificationTaskService.get(verificationTaskId);
    Preconditions.checkNotNull(verificationTask.getTaskInfo().getTaskType().equals(TaskType.DEPLOYMENT),
        "VerificationTask should be of Deployment type");
    CVConfig cvConfig = verificationJobInstanceService.getEmbeddedCVConfig(
        ((DeploymentInfo) verificationTask.getTaskInfo()).getCvConfigId(),
        ((DeploymentInfo) verificationTask.getTaskInfo()).getVerificationJobInstanceId());
    String serviceGuardVerificationTaskId =
        verificationTaskService.getServiceGuardVerificationTaskId(cvConfig.getAccountId(), cvConfig.getUuid());
    Double overallRisk = null;
    Instant updatedLatestTime = latestTimeOfAnalysis;
    switch (cvConfig.getVerificationType()) {
      case LOG:
        LogAnalysisResult result = logAnalysisService.getLatestAnalysisForVerificationTaskId(
            serviceGuardVerificationTaskId, startTime, endTime);
        if (result != null && result.getAnalysisEndTime().isAfter(latestTimeOfAnalysis)) {
          overallRisk = result.getOverallRisk();
          updatedLatestTime = result.getAnalysisEndTime();
        }
        break;
      case TIME_SERIES:
        TimeSeriesRiskSummary riskSummary = timeSeriesAnalysisService.getLatestTimeSeriesRiskSummary(
            serviceGuardVerificationTaskId, startTime, endTime);
        if (riskSummary != null && riskSummary.getAnalysisEndTime().isAfter(latestTimeOfAnalysis)) {
          updatedLatestTime = riskSummary.getAnalysisEndTime();
          overallRisk = riskSummary.getOverallRisk();
        }
        break;
      default:
        throw new UnsupportedOperationException("Unknown verification type of CVConfig");
    }
    if (updatedLatestTime.isAfter(latestTimeOfAnalysis)) {
      healthVerificationHeatMapService.updateRisk(
          verificationTaskId, overallRisk, updatedLatestTime, healthVerificationPeriod);
      log.info("Updated the risk for verificationTaskId {}, healthVerificationPeriod {} to {} with updateTime as {}",
          verificationTaskId, healthVerificationPeriod, overallRisk, updatedLatestTime);
    }
    return updatedLatestTime;
  }

  @Override
  public void updateProgress(
      String verificationTaskId, Instant latestTimeOfAnalysis, AnalysisStatus status, boolean isFinalState) {
    VerificationTask task = verificationTaskService.get(verificationTaskId);
    Preconditions.checkNotNull(
        task.getTaskInfo().getTaskType().equals(TaskType.DEPLOYMENT), "VerificationTask should be of Deployment type");
    VerificationJobInstance jobInstance = verificationJobInstanceService.getVerificationJobInstance(
        ((DeploymentInfo) task.getTaskInfo()).getVerificationJobInstanceId());
    Preconditions.checkNotNull(jobInstance);
    verificationJobInstanceService.logProgress(
        VerificationJobInstance.AnalysisProgressLog.builder()
            .analysisStatus(status)
            .verificationTaskId(verificationTaskId)
            .startTime(((HealthVerificationJob) jobInstance.getResolvedJob())
                           .getPreActivityVerificationStartTime(jobInstance.getStartTime()))
            .endTime(latestTimeOfAnalysis)
            .isFinalState(isFinalState)
            .log("Health verification completed until time " + latestTimeOfAnalysis)
            .createdAt(Instant.now())
            .build());
  }
}
