/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.statemachine.services.api;

import io.harness.cvng.core.entities.VerificationTask;
import io.harness.cvng.statemachine.beans.AnalysisInput;
import io.harness.cvng.statemachine.services.impl.CompositeSLOStateMachineService;
import io.harness.cvng.statemachine.services.impl.DeploymentStateMachineService;
import io.harness.cvng.statemachine.services.impl.LiveMonitoringStateMachineService;
import io.harness.cvng.statemachine.services.impl.SLIStateMachineService;

public class StateMachineFactory {
  public StateMachineService getStateMachineService(
      VerificationTask.TaskType taskType, AnalysisInput inputForAnalysis) {
    switch (taskType) {
      case SLI:
        return new SLIStateMachineService(inputForAnalysis);
      case COMPOSITE_SLO:
        return new CompositeSLOStateMachineService(inputForAnalysis);
      case DEPLOYMENT:
        return new DeploymentStateMachineService(inputForAnalysis);
      case LIVE_MONITORING:
        return new LiveMonitoringStateMachineService(inputForAnalysis);
      default:
        throw new IllegalStateException("Invalid verificationType");
    }
  }
}
