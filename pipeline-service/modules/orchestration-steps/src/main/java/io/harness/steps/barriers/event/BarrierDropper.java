/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.steps.barriers.event;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.engine.observers.NodeStatusUpdateObserver;
import io.harness.engine.observers.NodeUpdateInfo;
import io.harness.execution.NodeExecution;
import io.harness.observer.AsyncInformObserver;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.StrategyMetadata;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.serializer.recaster.RecastOrchestrationUtils;
import io.harness.steps.barriers.BarrierSpecParameters;
import io.harness.steps.barriers.BarrierStep;
import io.harness.steps.barriers.beans.BarrierExecutionInstance;
import io.harness.steps.barriers.service.BarrierService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public class BarrierDropper implements AsyncInformObserver, NodeStatusUpdateObserver {
  @Inject @Named("OrchestrationVisualizationExecutorService") ExecutorService executorService;
  @Inject private BarrierService barrierService;

  @Override
  public void onNodeStatusUpdate(NodeUpdateInfo nodeUpdateInfo) {
    String planExecutionId = nodeUpdateInfo.getPlanExecutionId();
    try {
      NodeExecution nodeExecution = nodeUpdateInfo.getNodeExecution();
      if (Status.ASYNC_WAITING != nodeExecution.getStatus()
          || !BarrierStep.STEP_TYPE.equals(AmbianceUtils.getCurrentStepType(nodeExecution.getAmbiance()))) {
        return;
      }
      StepElementParameters stepElementParameters =
          RecastOrchestrationUtils.fromMap(nodeExecution.getResolvedStepParameters(), StepElementParameters.class);
      BarrierSpecParameters barrierSpecParameters = (BarrierSpecParameters) stepElementParameters.getSpec();

      Optional<Level> strategyLevel = AmbianceUtils.getStrategyLevelFromAmbiance(nodeUpdateInfo.getNodeExecution().getAmbiance());
      String strategyExecutionId = strategyLevel.isPresent() ? strategyLevel.get().getRuntimeId() : null;

      BarrierExecutionInstance barrierExecutionInstance =
          barrierService.findByIdentifierAndPlanExecutionIdAndStrategyExecutionId(barrierSpecParameters.getBarrierRef(), planExecutionId, strategyExecutionId);
      barrierService.update(barrierExecutionInstance);
    } catch (Exception e) {
      log.error("Failed to bring barriers down for planExecutionId: [{}]", planExecutionId);
      throw e;
    }
  }

  @Override
  public ExecutorService getInformExecutorService() {
    return executorService;
  }
}
