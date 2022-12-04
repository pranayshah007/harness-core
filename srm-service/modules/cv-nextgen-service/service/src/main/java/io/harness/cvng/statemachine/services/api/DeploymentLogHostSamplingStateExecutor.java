/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.statemachine.services.api;

import io.harness.cvng.analysis.entities.LearningEngineTask.LearningEngineTaskType;
import io.harness.cvng.core.beans.TimeRange;
import io.harness.cvng.core.services.api.HostRecordService;
import io.harness.cvng.statemachine.beans.AnalysisInput;
import io.harness.cvng.statemachine.beans.AnalysisState;
import io.harness.cvng.statemachine.beans.AnalysisStatus;
import io.harness.cvng.statemachine.entities.DeploymentLogAnalysisState;
import io.harness.cvng.statemachine.entities.HostSamplingState;
import io.harness.cvng.verificationjob.entities.VerificationJob;
import io.harness.cvng.verificationjob.entities.VerificationJobInstance;

import com.google.inject.Inject;
import java.util.Optional;
import java.util.Set;

public class DeploymentLogHostSamplingStateExecutor extends HostSamplingStateExecutor<HostSamplingState> {
  @Inject HostRecordService hostRecordService;

  @Override
  protected Set<String> getPostDeploymentHosts(
      VerificationJobInstance verificationJobInstance, AnalysisInput analysisInput) {
    return hostRecordService.get(
        analysisInput.getVerificationTaskId(), analysisInput.getStartTime(), analysisInput.getEndTime());
  }

  @Override
  protected Set<String> getPreDeploymentHosts(
      VerificationJobInstance verificationJobInstance, String verificationTaskId) {
    VerificationJob verificationJob = verificationJobInstance.getResolvedJob();
    Optional<TimeRange> preDeploymentTimeRange =
        verificationJob.getPreActivityTimeRange(verificationJobInstance.getDeploymentStartTime());
    return hostRecordService.get(
        verificationTaskId, preDeploymentTimeRange.get().getStartTime(), preDeploymentTimeRange.get().getEndTime());
  }

  @Override
  protected LearningEngineTaskType getBeforeAfterTaskType() {
    return LearningEngineTaskType.BEFORE_AFTER_DEPLOYMENT_LOG;
  }

  @Override
  protected LearningEngineTaskType getCanaryTaskType() {
    return LearningEngineTaskType.CANARY_DEPLOYMENT_LOG;
  }

  @Override
  public AnalysisState handleTransition(HostSamplingState analysisState) {
    analysisState.setStatus(AnalysisStatus.SUCCESS);
    DeploymentLogAnalysisState deploymentLogAnalysisState = new DeploymentLogAnalysisState();
    deploymentLogAnalysisState.setInputs(analysisState.getInputs());
    deploymentLogAnalysisState.setStatus(AnalysisStatus.CREATED);
    return deploymentLogAnalysisState;
  }
}