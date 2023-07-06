/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.statemachine.services.impl;

import static io.harness.cvng.CVConstants.STATE_MACHINE_IGNORE_MINUTES_DEFAULT;
import static io.harness.cvng.CVConstants.STATE_MACHINE_IGNORE_MINUTES_FOR_DEMO;

import io.harness.cvng.analysis.beans.LogClusterLevel;
import io.harness.cvng.beans.cvnglog.CVNGLogTag;
import io.harness.cvng.core.entities.CVConfig;
import io.harness.cvng.core.services.api.CVConfigService;
import io.harness.cvng.core.services.api.ExecutionLogService;
import io.harness.cvng.core.services.api.VerificationTaskService;
import io.harness.cvng.core.utils.CVNGTaskMetadataUtils;
import io.harness.cvng.models.VerificationType;
import io.harness.cvng.statemachine.beans.AnalysisInput;
import io.harness.cvng.statemachine.beans.AnalysisState;
import io.harness.cvng.statemachine.beans.AnalysisStatus;
import io.harness.cvng.statemachine.entities.AnalysisStateMachine;
import io.harness.cvng.statemachine.entities.ServiceGuardLogClusterState;
import io.harness.cvng.statemachine.entities.ServiceGuardTimeSeriesAnalysisState;
import io.harness.cvng.statemachine.exception.AnalysisStateMachineException;

import com.google.inject.Inject;
import java.util.List;

public class LiveMonitoringStateMachineServiceImpl extends AnalysisStateMachineServiceImpl {
  @Inject private VerificationTaskService verificationTaskService;
  @Inject private CVConfigService cvConfigService;
  @Inject private ExecutionLogService executionLogService;

  @Override
  public AnalysisStateMachine createStateMachine(AnalysisInput inputForAnalysis) {
    AnalysisStateMachine stateMachine = AnalysisStateMachine.builder()
                                            .verificationTaskId(inputForAnalysis.getVerificationTaskId())
                                            .analysisStartTime(inputForAnalysis.getStartTime())
                                            .analysisEndTime(inputForAnalysis.getEndTime())
                                            .status(AnalysisStatus.CREATED)
                                            .build();

    String cvConfigId = verificationTaskService.getCVConfigId(inputForAnalysis.getVerificationTaskId());
    CVConfig cvConfig = cvConfigService.get(cvConfigId);
    VerificationType verificationType = cvConfig.getVerificationType();
    AnalysisState firstState;
    switch (verificationType) {
      case TIME_SERIES:
        firstState = ServiceGuardTimeSeriesAnalysisState.builder().build();
        break;
      case LOG:
        firstState = ServiceGuardLogClusterState.builder().clusterLevel(LogClusterLevel.L1).build();
        break;
      default:
        throw new AnalysisStateMachineException(
            "Unimplemented verification type for orchestration : " + verificationType);
    }
    if (cvConfig.isDemo()) {
      stateMachine.setStateMachineIgnoreMinutes(STATE_MACHINE_IGNORE_MINUTES_FOR_DEMO);
    } else {
      stateMachine.setStateMachineIgnoreMinutes(STATE_MACHINE_IGNORE_MINUTES_DEFAULT);
    }
    firstState.setStatus(AnalysisStatus.CREATED);
    firstState.setInputs(inputForAnalysis);
    stateMachine.setAccountId(cvConfig.getAccountId());
    stateMachine.setCurrentState(firstState);
    List<CVNGLogTag> cvngLogTags = CVNGTaskMetadataUtils.getCvngLogTagsForTask(stateMachine.getUuid());
    executionLogService.getLogger(stateMachine)
        .log(stateMachine.getLogLevel(), cvngLogTags, "Analysis state machine status: " + stateMachine.getStatus());
    return stateMachine;
  }
}
