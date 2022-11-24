/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.statemachine.services.impl;

import static io.harness.cvng.CVConstants.STATE_MACHINE_IGNORE_MINUTES_FOR_SLI;

import io.harness.cvng.servicelevelobjective.entities.CompositeServiceLevelObjective;
import io.harness.cvng.servicelevelobjective.services.api.ServiceLevelObjectiveV2Service;
import io.harness.cvng.statemachine.beans.AnalysisInput;
import io.harness.cvng.statemachine.beans.AnalysisState;
import io.harness.cvng.statemachine.beans.AnalysisStatus;
import io.harness.cvng.statemachine.entities.AnalysisStateMachine;
import io.harness.cvng.statemachine.entities.CompositeSLOMetricAnalysisState;
import io.harness.cvng.statemachine.services.api.StateMachineService;

import com.google.inject.Inject;

public class CompositeSLOStateMachineService extends StateMachineService {
  @Inject private ServiceLevelObjectiveV2Service serviceLevelObjectiveV2Service;

  public CompositeSLOStateMachineService(AnalysisInput inputForAnalysis) {
    super(inputForAnalysis);
  }

  @Override
  public AnalysisStateMachine createAnalysisStateMachine(AnalysisInput inputForAnalysis) {
    String sloId = this.verificationTaskService.getCompositeSLOId(inputForAnalysis.getVerificationTaskId());
    CompositeServiceLevelObjective compositeServiceLevelObjective =
        (CompositeServiceLevelObjective) serviceLevelObjectiveV2Service.get(sloId);
    AnalysisState firstState = CompositeSLOMetricAnalysisState.builder().build();
    firstState.setStatus(AnalysisStatus.CREATED);
    firstState.setInputs(inputForAnalysis);
    this.analysisStateMachine.setAccountId(compositeServiceLevelObjective.getAccountId());
    this.analysisStateMachine.setStateMachineIgnoreMinutes(STATE_MACHINE_IGNORE_MINUTES_FOR_SLI);
    this.analysisStateMachine.setCurrentState(firstState);
    log();
    return this.analysisStateMachine;
  }
}
