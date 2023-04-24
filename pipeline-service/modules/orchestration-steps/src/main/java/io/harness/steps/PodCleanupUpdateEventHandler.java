/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.execution.utils.StatusUtils;
import io.harness.pms.sdk.core.events.OrchestrationEvent;
import io.harness.pms.sdk.core.events.OrchestrationEventHandler;
import io.harness.steps.common.steps.stepgroup.StepGroupStep;
import io.harness.steps.container.execution.ContainerStepCleanupHelper;

import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
@OwnedBy(HarnessTeam.PIPELINE)
public class PodCleanupUpdateEventHandler implements OrchestrationEventHandler {
  @Inject ContainerStepCleanupHelper containerStepCleanupHelper;

  @Override
  public void handleEvent(OrchestrationEvent event) {
    Level level = AmbianceUtils.obtainCurrentLevel(event.getAmbiance());
    if (level.getStepType().equals(StepGroupStep.STEP_TYPE) && StatusUtils.isFinalStatus(event.getStatus())) {
      containerStepCleanupHelper.sendCleanupRequest(event.getAmbiance());
    }
  }
}
