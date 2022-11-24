/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.statemachine.services.api;

import io.harness.cvng.core.services.api.ExecutionLogService;
import io.harness.cvng.core.services.api.VerificationTaskService;
import io.harness.cvng.statemachine.beans.AnalysisInput;
import io.harness.cvng.statemachine.beans.AnalysisStatus;
import io.harness.cvng.statemachine.entities.AnalysisStateMachine;

import com.google.inject.Inject;

public abstract class StateMachineService {
  @Inject protected VerificationTaskService verificationTaskService;
  @Inject protected ExecutionLogService executionLogService;

  protected AnalysisStateMachine analysisStateMachine;
  protected StateMachineService(AnalysisInput inputForAnalysis) {
    this.analysisStateMachine = AnalysisStateMachine.builder()
                                    .verificationTaskId(inputForAnalysis.getVerificationTaskId())
                                    .analysisStartTime(inputForAnalysis.getStartTime())
                                    .analysisEndTime(inputForAnalysis.getEndTime())
                                    .status(AnalysisStatus.CREATED)
                                    .build();
  }

  protected void log() {
    executionLogService.getLogger(this.analysisStateMachine)
        .log(this.analysisStateMachine.getLogLevel(),
            "Analysis state machine status: " + this.analysisStateMachine.getStatus());
  }
  public abstract AnalysisStateMachine createAnalysisStateMachine(AnalysisInput inputForAnalysis);
}
