/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.statemachine.services.impl;

import static io.harness.cvng.CVConstants.STATE_MACHINE_IGNORE_MINUTES_FOR_SLI;

import io.harness.cvng.beans.cvnglog.CVNGLogTag;
import io.harness.cvng.core.services.api.ExecutionLogService;
import io.harness.cvng.core.services.api.VerificationTaskService;
import io.harness.cvng.core.utils.CVNGTaskMetadataUtils;
import io.harness.cvng.servicelevelobjective.entities.CompositeServiceLevelObjective;
import io.harness.cvng.servicelevelobjective.services.api.ServiceLevelObjectiveV2Service;
import io.harness.cvng.statemachine.beans.AnalysisInput;
import io.harness.cvng.statemachine.beans.AnalysisState;
import io.harness.cvng.statemachine.beans.AnalysisStatus;
import io.harness.cvng.statemachine.entities.AnalysisStateMachine;
import io.harness.cvng.statemachine.entities.CompositeSLOMetricAnalysisState;
import io.harness.cvng.statemachine.entities.CompositeSLORestoreMetricAnalysisState;

import com.google.inject.Inject;
import java.time.Duration;
import java.util.List;

public class CompositeSLOAnalysisStateMachineServiceImpl extends AnalysisStateMachineServiceImpl {
  @Inject private VerificationTaskService verificationTaskService;
  @Inject private ServiceLevelObjectiveV2Service serviceLevelObjectiveV2Service;
  @Inject private ExecutionLogService executionLogService;

  @Override
  public AnalysisStateMachine createStateMachine(AnalysisInput inputForAnalysis) {
    AnalysisStateMachine stateMachine = AnalysisStateMachine.builder()
                                            .verificationTaskId(inputForAnalysis.getVerificationTaskId())
                                            .analysisStartTime(inputForAnalysis.getStartTime())
                                            .analysisEndTime(inputForAnalysis.getEndTime())
                                            .status(AnalysisStatus.CREATED)
                                            .build();

    String sloId = verificationTaskService.getCompositeSLOId(inputForAnalysis.getVerificationTaskId());
    CompositeServiceLevelObjective compositeServiceLevelObjective =
        (CompositeServiceLevelObjective) serviceLevelObjectiveV2Service.get(sloId);
    AnalysisState firstState;
    if (inputForAnalysis.isSLORestoreTask()) {
      firstState = CompositeSLORestoreMetricAnalysisState.builder().build();
    } else {
      firstState = CompositeSLOMetricAnalysisState.builder().build();
    }
    firstState.setStatus(AnalysisStatus.CREATED);
    firstState.setInputs(inputForAnalysis);
    stateMachine.setAccountId(compositeServiceLevelObjective.getAccountId());
    stateMachine.setStateMachineIgnoreMinutes(STATE_MACHINE_IGNORE_MINUTES_FOR_SLI);
    stateMachine.setCurrentState(firstState);
    List<CVNGLogTag> cvngLogTags = CVNGTaskMetadataUtils.getCvngLogTagsForTask(stateMachine.getUuid());
    if (AnalysisStatus.getFinalStates().contains(stateMachine.getStatus())) {
      Duration timeDuration = Duration.between(stateMachine.getStartTime(), stateMachine.getAnalysisEndTime());
      cvngLogTags.addAll(
          CVNGTaskMetadataUtils.getTaskDurationTags(CVNGTaskMetadataUtils.DurationType.TOTAL_DURATION, timeDuration));
    }
    executionLogService.getLogger(stateMachine)
        .log(stateMachine.getLogLevel(), cvngLogTags, "Analysis state machine status: " + stateMachine.getStatus());
    return stateMachine;
  }
}
