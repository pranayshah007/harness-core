/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.statemachine.services.api;

import io.harness.cvng.analysis.beans.TimeSeriesRecordDTO;
import io.harness.cvng.analysis.entities.LearningEngineTask.LearningEngineTaskType;
import io.harness.cvng.core.beans.TimeRange;
import io.harness.cvng.core.services.api.TimeSeriesRecordService;
import io.harness.cvng.statemachine.beans.AnalysisInput;
import io.harness.cvng.statemachine.beans.AnalysisState;
import io.harness.cvng.statemachine.beans.AnalysisStatus;
import io.harness.cvng.statemachine.entities.DeploymentTimeSeriesAnalysisState;
import io.harness.cvng.statemachine.entities.HostSamplingState;
import io.harness.cvng.verificationjob.entities.VerificationJob;
import io.harness.cvng.verificationjob.entities.VerificationJobInstance;

import com.google.inject.Inject;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class DeploymentMetricHostSamplingStateExecutor extends HostSamplingStateExecutor<HostSamplingState> {
  @Inject private TimeSeriesRecordService timeSeriesRecordService;

  @Override
  protected Set<String> getPostDeploymentHosts(
      VerificationJobInstance verificationJobInstance, AnalysisInput analysisInput) {
    List<TimeSeriesRecordDTO> postDeploymentTimeSeriesRecords = timeSeriesRecordService.getTimeSeriesRecordDTOs(
        analysisInput.getVerificationTaskId(), verificationJobInstance.getStartTime(), analysisInput.getEndTime());
    return postDeploymentTimeSeriesRecords.stream().map(TimeSeriesRecordDTO::getHost).collect(Collectors.toSet());
  }

  @Override
  protected Set<String> getPreDeploymentHosts(
      VerificationJobInstance verificationJobInstance, String verificationTaskId) {
    VerificationJob verificationJob = verificationJobInstance.getResolvedJob();
    Optional<TimeRange> preDeploymentTimeRange =
        verificationJob.getPreActivityTimeRange(verificationJobInstance.getDeploymentStartTime());
    List<TimeSeriesRecordDTO> preDeploymentTimeSeriesRecords = timeSeriesRecordService.getTimeSeriesRecordDTOs(
        verificationTaskId, preDeploymentTimeRange.get().getStartTime(), preDeploymentTimeRange.get().getEndTime());
    return preDeploymentTimeSeriesRecords.stream().map(TimeSeriesRecordDTO::getHost).collect(Collectors.toSet());
  }

  @Override
  protected LearningEngineTaskType getBeforeAfterTaskType() {
    return LearningEngineTaskType.BEFORE_AFTER_DEPLOYMENT_TIME_SERIES;
  }

  @Override
  protected LearningEngineTaskType getCanaryTaskType() {
    return LearningEngineTaskType.CANARY_DEPLOYMENT_TIME_SERIES;
  }

  @Override
  public AnalysisState handleTransition(HostSamplingState analysisState) {
    analysisState.setStatus(AnalysisStatus.SUCCESS);
    DeploymentTimeSeriesAnalysisState deploymentTimeSeriesAnalysisState = new DeploymentTimeSeriesAnalysisState();
    deploymentTimeSeriesAnalysisState.setInputs(analysisState.getInputs());
    deploymentTimeSeriesAnalysisState.setStatus(AnalysisStatus.CREATED);
    deploymentTimeSeriesAnalysisState.setVerificationJobInstanceId(analysisState.getVerificationJobInstanceId());
    return deploymentTimeSeriesAnalysisState;
  }
}