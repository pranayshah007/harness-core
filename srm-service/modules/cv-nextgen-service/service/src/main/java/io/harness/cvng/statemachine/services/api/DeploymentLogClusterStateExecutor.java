/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.statemachine.services.api;

import static io.harness.cvng.analysis.beans.LogClusterLevel.L2;

import io.harness.cvng.beans.job.VerificationJobType;
import io.harness.cvng.core.services.api.FeatureFlagService;
import io.harness.cvng.core.services.api.VerificationTaskService;
import io.harness.cvng.statemachine.beans.AnalysisInput;
import io.harness.cvng.statemachine.beans.AnalysisState;
import io.harness.cvng.statemachine.beans.AnalysisStatus;
import io.harness.cvng.statemachine.entities.DeploymentLogAnalysisState;
import io.harness.cvng.statemachine.entities.DeploymentLogClusterState;
import io.harness.cvng.statemachine.entities.DeploymentLogHostSamplingState;
import io.harness.cvng.statemachine.entities.LogClusterState;
import io.harness.cvng.statemachine.exception.AnalysisStateMachineException;
import io.harness.cvng.verificationjob.entities.VerificationJobInstance;
import io.harness.cvng.verificationjob.services.api.VerificationJobInstanceService;

import com.google.inject.Inject;
import java.util.Collections;
import java.util.List;

public class DeploymentLogClusterStateExecutor extends LogClusterStateExecutor<DeploymentLogClusterState> {
  @Inject private FeatureFlagService featureFlagService;

  @Inject private VerificationTaskService verificationTaskService;

  @Inject private VerificationJobInstanceService verificationJobInstanceService;

  @Override
  protected List<String> scheduleAnalysis(AnalysisInput analysisInput, LogClusterState analysisState) {
    switch (analysisState.getClusterLevel()) {
      case L1:
        return logClusterService.scheduleL1ClusteringTasks(analysisInput);
      case L2:
        return logClusterService.scheduleDeploymentL2ClusteringTask(analysisInput)
            .map(Collections::singletonList)
            .orElseGet(Collections::emptyList);
      default:
        throw new IllegalStateException("Invalid clusterLevel: " + analysisState.getClusterLevel());
    }
  }

  @Override
  public AnalysisState handleTransition(DeploymentLogClusterState analysisState) {
    analysisState.setStatus(AnalysisStatus.SUCCESS);
    switch (analysisState.getClusterLevel()) {
      case L1:
        DeploymentLogClusterState deploymentLogClusterState = DeploymentLogClusterState.builder().build();
        deploymentLogClusterState.setClusterLevel(L2);
        deploymentLogClusterState.setInputs(analysisState.getInputs());
        deploymentLogClusterState.setStatus(AnalysisStatus.CREATED);
        return deploymentLogClusterState;
      case L2:
        VerificationJobInstance verificationJobInstance = verificationJobInstanceService.getVerificationJobInstance(
            analysisState.getInputs().getVerificationJobInstanceId());
        if (verificationJobInstance.getResolvedJob().getType() == VerificationJobType.TEST) {
          DeploymentLogAnalysisState deploymentLogAnalysisState = DeploymentLogAnalysisState.builder().build();
          deploymentLogAnalysisState.setInputs(analysisState.getInputs());
          deploymentLogAnalysisState.setStatus(AnalysisStatus.CREATED);
          return deploymentLogAnalysisState;
        } else {
          DeploymentLogHostSamplingState deploymentLogHostSamplingState = new DeploymentLogHostSamplingState();
          deploymentLogHostSamplingState.setInputs(analysisState.getInputs());
          deploymentLogHostSamplingState.setStatus(AnalysisStatus.CREATED);
          return deploymentLogHostSamplingState;
        }
      default:
        throw new AnalysisStateMachineException("Unknown cluster level in handleTransition "
            + "of ServiceGuardLogClusterState: " + analysisState.getClusterLevel());
    }
  }

  @Override
  public AnalysisState handleRetry(DeploymentLogClusterState analysisState) {
    if (analysisState.getRetryCount() >= getMaxRetry()) {
      analysisState.setStatus(AnalysisStatus.FAILED);
    } else {
      return handleRerun(analysisState);
    }
    return analysisState;
  }

  @Override
  public void handleFinalStatuses(DeploymentLogClusterState analysisState) {
    logClusterService.logDeploymentVerificationProgress(
        analysisState.getInputs(), analysisState.getStatus(), analysisState.getClusterLevel());
  }
}
