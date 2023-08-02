/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.statemachine.services.api;

import io.harness.cvng.analysis.beans.LogClusterLevel;
import io.harness.cvng.statemachine.beans.AnalysisInput;
import io.harness.cvng.statemachine.beans.AnalysisState;
import io.harness.cvng.statemachine.beans.AnalysisStatus;
import io.harness.cvng.statemachine.entities.LogClusterState;
import io.harness.cvng.statemachine.entities.PreDeploymentLogClusterState;

import com.google.common.base.Preconditions;
import java.util.List;

public class PreDeploymentLogClusterStateExecutor extends LogClusterStateExecutor<PreDeploymentLogClusterState> {
  @Override
  protected List<String> scheduleAnalysis(AnalysisInput analysisInput, LogClusterState analysisState) {
    Preconditions.checkState(analysisState.getClusterLevel() == LogClusterLevel.L1,
        "PreDeployment Log cluster state only does L1 clustering");
    return logClusterService.scheduleL1ClusteringTasks(analysisInput, true);
  }

  @Override
  public AnalysisState handleTransition(PreDeploymentLogClusterState analysisState) {
    analysisState.setStatus(AnalysisStatus.SUCCESS);
    return analysisState;
  }

  @Override
  public AnalysisState handleRetry(PreDeploymentLogClusterState analysisState) {
    if (analysisState.getRetryCount() >= getMaxRetry()) {
      analysisState.setStatus(AnalysisStatus.FAILED);
    } else {
      return handleRerun(analysisState);
    }
    return analysisState;
  }

  @Override
  public void handleFinalStatuses(PreDeploymentLogClusterState analysisState) {
    logClusterService.logDeploymentVerificationProgress(
        analysisState.getInputs(), analysisState.getStatus(), analysisState.getClusterLevel());
  }
}
