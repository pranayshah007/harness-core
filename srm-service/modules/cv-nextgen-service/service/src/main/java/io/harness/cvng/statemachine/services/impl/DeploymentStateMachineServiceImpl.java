/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.statemachine.services.impl;

import static io.harness.cvng.CVConstants.STATE_MACHINE_IGNORE_MINUTES_DEFAULT;

import io.harness.cvng.analysis.beans.LogClusterLevel;
import io.harness.cvng.beans.job.VerificationJobType;
import io.harness.cvng.core.beans.TimeRange;
import io.harness.cvng.core.entities.CVConfig;
import io.harness.cvng.core.entities.VerificationTask;
import io.harness.cvng.core.services.api.CVConfigService;
import io.harness.cvng.core.services.api.ExecutionLogService;
import io.harness.cvng.core.services.api.FeatureFlagService;
import io.harness.cvng.core.services.api.VerificationTaskService;
import io.harness.cvng.statemachine.beans.AnalysisInput;
import io.harness.cvng.statemachine.beans.AnalysisState;
import io.harness.cvng.statemachine.beans.AnalysisStatus;
import io.harness.cvng.statemachine.entities.AnalysisStateMachine;
import io.harness.cvng.statemachine.entities.DeploymentLogClusterState;
import io.harness.cvng.statemachine.entities.DeploymentMetricHostSamplingState;
import io.harness.cvng.statemachine.entities.PreDeploymentLogClusterState;
import io.harness.cvng.statemachine.entities.TestTimeSeriesAnalysisState;
import io.harness.cvng.verificationjob.entities.VerificationJob;
import io.harness.cvng.verificationjob.entities.VerificationJobInstance;
import io.harness.cvng.verificationjob.services.api.VerificationJobInstanceService;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.apache.commons.collections.CollectionUtils;

public class DeploymentStateMachineServiceImpl extends AnalysisStateMachineServiceImpl {
  @Inject private VerificationTaskService verificationTaskService;
  @Inject private CVConfigService cvConfigService;
  @Inject private VerificationJobInstanceService verificationJobInstanceService;
  @Inject private ExecutionLogService executionLogService;
  @Inject private FeatureFlagService featureFlagService;

  @Override
  public AnalysisStateMachine createStateMachine(AnalysisInput inputForAnalysis) {
    AnalysisStateMachine stateMachine = AnalysisStateMachine.builder()
                                            .verificationTaskId(inputForAnalysis.getVerificationTaskId())
                                            .analysisStartTime(inputForAnalysis.getStartTime())
                                            .analysisEndTime(inputForAnalysis.getEndTime())
                                            .status(AnalysisStatus.CREATED)
                                            .build();

    VerificationTask verificationTask = verificationTaskService.get(inputForAnalysis.getVerificationTaskId());
    VerificationTask.DeploymentInfo deploymentInfo = (VerificationTask.DeploymentInfo) verificationTask.getTaskInfo();
    String cvConfigId = deploymentInfo.getCvConfigId();
    VerificationJobInstance verificationJobInstance =
        verificationJobInstanceService.getVerificationJobInstance(deploymentInfo.getVerificationJobInstanceId());
    Preconditions.checkNotNull(verificationJobInstance, "verificationJobInstance can not be null");
    VerificationJob resolvedVerificationJob = verificationJobInstance.getResolvedJob();
    CVConfig cvConfigForDeployment = null;
    if (Objects.nonNull(resolvedVerificationJob)) {
      List<CVConfig> cvConfigs = resolvedVerificationJob.getCvConfigs();
      if (CollectionUtils.isNotEmpty(cvConfigs)) {
        cvConfigForDeployment =
            cvConfigs.stream().filter(cvConfig -> cvConfig.getUuid().equals(cvConfigId)).findFirst().orElse(null);
      }
    }
    if (Objects.isNull(cvConfigForDeployment)) {
      cvConfigForDeployment = cvConfigService.get(cvConfigId);
    }
    Preconditions.checkNotNull(cvConfigForDeployment, "cvConfigForDeployment can not be null");
    stateMachine.setAccountId(verificationTask.getAccountId());
    stateMachine.setStateMachineIgnoreMinutes(STATE_MACHINE_IGNORE_MINUTES_DEFAULT);
    createDeploymentAnalysisState(stateMachine, inputForAnalysis, verificationJobInstance, cvConfigForDeployment);
    executionLogService.getLogger(stateMachine)
        .log(stateMachine.getLogLevel(), "Analysis state machine status: " + stateMachine.getStatus());
    return stateMachine;
  }

  private void createDeploymentAnalysisState(AnalysisStateMachine stateMachine, AnalysisInput inputForAnalysis,
      VerificationJobInstance verificationJobInstance, CVConfig cvConfigForDeployment) {
    switch (cvConfigForDeployment.getVerificationType()) {
      case TIME_SERIES:
        if (verificationJobInstance.getResolvedJob().getType() == VerificationJobType.TEST
            || verificationJobInstance.getResolvedJob().getType() == VerificationJobType.SIMPLE) {
          TestTimeSeriesAnalysisState testTimeSeriesAnalysisState = TestTimeSeriesAnalysisState.builder().build();
          testTimeSeriesAnalysisState.setStatus(AnalysisStatus.CREATED);
          testTimeSeriesAnalysisState.setInputs(inputForAnalysis);
          stateMachine.setCurrentState(testTimeSeriesAnalysisState);
        } else {
          DeploymentMetricHostSamplingState deploymentMetricHostSamplingState = new DeploymentMetricHostSamplingState();
          deploymentMetricHostSamplingState.setStatus(AnalysisStatus.CREATED);
          deploymentMetricHostSamplingState.setInputs(inputForAnalysis);
          inputForAnalysis.setVerificationJobInstanceId(verificationJobInstance.getUuid());
          deploymentMetricHostSamplingState.setVerificationJobInstanceId(verificationJobInstance.getUuid());
          stateMachine.setCurrentState(deploymentMetricHostSamplingState);
        }
        break;
      case LOG:
        AnalysisState analysisState = createDeploymentLogState(inputForAnalysis, verificationJobInstance);
        analysisState.setStatus(AnalysisStatus.CREATED);
        analysisState.setInputs(inputForAnalysis);
        stateMachine.setCurrentState(analysisState);
        break;
      default:
        throw new IllegalStateException("Invalid verificationType");
    }
  }

  private AnalysisState createDeploymentLogState(
      AnalysisInput analysisInput, VerificationJobInstance verificationJobInstance) {
    Optional<TimeRange> preDeploymentTimeRange =
        verificationJobInstanceService.getPreDeploymentTimeRange(verificationJobInstance.getUuid());
    if (preDeploymentTimeRange.isPresent()
        && analysisInput.getTimeRange().getStartTime().isBefore(preDeploymentTimeRange.get().getEndTime())) {
      // first task so needs to enqueue clustering task
      PreDeploymentLogClusterState preDeploymentLogClusterState = PreDeploymentLogClusterState.builder().build();
      preDeploymentLogClusterState.setClusterLevel(LogClusterLevel.L1);
      return preDeploymentLogClusterState;
    } else {
      DeploymentLogClusterState deploymentLogClusterState = DeploymentLogClusterState.builder().build();
      deploymentLogClusterState.setClusterLevel(LogClusterLevel.L1);
      analysisInput.setVerificationJobInstanceId(verificationJobInstance.getUuid());
      deploymentLogClusterState.setInputs(analysisInput);
      return deploymentLogClusterState;
    }
  }
}
