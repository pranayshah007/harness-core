/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.engine.observers.NodeStatusUpdateObserver;
import io.harness.engine.observers.NodeUpdateInfo;
import io.harness.observer.AsyncInformObserver;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.execution.utils.StatusUtils;
import io.harness.steps.common.steps.stepgroup.StepGroupStep;
import io.harness.steps.container.execution.ContainerStepCleanupHelper;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.concurrent.ExecutorService;

@Singleton
@OwnedBy(HarnessTeam.PIPELINE)
public class PodCleanupUpdateEventHandler implements NodeStatusUpdateObserver, AsyncInformObserver {
  @Inject ContainerStepCleanupHelper containerStepCleanupHelper;

  @Inject @Named("PodCleanUpExecutorService") private ExecutorService podCleanUpExecutorService;

  @Override
  public void onNodeStatusUpdate(NodeUpdateInfo nodeUpdateInfo) {
    if (StepGroupStep.STEP_TYPE.getType().equals(
            AmbianceUtils.getCurrentStepType(nodeUpdateInfo.getNodeExecution().getAmbiance()).getType())
        && StatusUtils.isFinalStatus(nodeUpdateInfo.getStatus())) {
      containerStepCleanupHelper.sendCleanupRequest(nodeUpdateInfo.getNodeExecution().getAmbiance());
    }
  }

  @Override
  public ExecutorService getInformExecutorService() {
    return podCleanUpExecutorService;
  }
}
