/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.statemachine.services.impl;

import static io.harness.cvng.CVConstants.STATE_MACHINE_IGNORE_MINUTES_FOR_SLI;

import io.harness.cvng.servicelevelobjective.entities.ServiceLevelIndicator;
import io.harness.cvng.servicelevelobjective.services.api.ServiceLevelIndicatorService;
import io.harness.cvng.statemachine.beans.AnalysisInput;
import io.harness.cvng.statemachine.beans.AnalysisState;
import io.harness.cvng.statemachine.beans.AnalysisStatus;
import io.harness.cvng.statemachine.entities.AnalysisStateMachine;
import io.harness.cvng.statemachine.entities.SLIMetricAnalysisState;
import io.harness.cvng.statemachine.services.api.StateMachineService;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;

public class SLIStateMachineService extends StateMachineService {
  @Inject private ServiceLevelIndicatorService serviceLevelIndicatorService;

  public SLIStateMachineService(AnalysisInput inputForAnalysis) {
    super(inputForAnalysis);
  }

  @Override
  public AnalysisStateMachine createAnalysisStateMachine(AnalysisInput inputForAnalysis) {
    String sliId = this.verificationTaskService.getSliId(inputForAnalysis.getVerificationTaskId());
    ServiceLevelIndicator serviceLevelIndicator = serviceLevelIndicatorService.get(sliId);
    Preconditions.checkNotNull(serviceLevelIndicator, "Service Level Indicator can't be null");
    AnalysisState firstState = SLIMetricAnalysisState.builder().build();
    firstState.setStatus(AnalysisStatus.CREATED);
    firstState.setInputs(inputForAnalysis);
    this.analysisStateMachine.setAccountId(serviceLevelIndicator.getAccountId());
    this.analysisStateMachine.setStateMachineIgnoreMinutes(STATE_MACHINE_IGNORE_MINUTES_FOR_SLI);
    this.analysisStateMachine.setCurrentState(firstState);
    log();
    return this.analysisStateMachine;
  }
}
