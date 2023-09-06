/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.steps.barriers.event;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.engine.observers.NodeStatusUpdateObserver;
import io.harness.engine.observers.NodeUpdateInfo;
import io.harness.execution.NodeExecution;
import io.harness.observer.AsyncInformObserver;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.steps.barriers.beans.BarrierExecutionInstance;
import io.harness.steps.barriers.beans.BarrierPositionInfo.BarrierPosition.BarrierPositionType;
import io.harness.steps.barriers.service.BarrierService;
import io.harness.utils.PmsFeatureFlagService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public class BarrierPositionHelperEventHandler implements AsyncInformObserver, NodeStatusUpdateObserver {
  @Inject @Named("OrchestrationVisualizationExecutorService") ExecutorService executorService;
  @Inject BarrierService barrierService;
  @Inject PmsFeatureFlagService featureFlagService;

  @Override
  public void onNodeStatusUpdate(NodeUpdateInfo nodeUpdateInfo) {
    String planExecutionId = nodeUpdateInfo.getPlanExecutionId();
    NodeExecution nodeExecution = nodeUpdateInfo.getNodeExecution();
    try {
      Level level = Objects.requireNonNull(AmbianceUtils.obtainCurrentLevel(nodeExecution.getAmbiance()));
      String group = level.getGroup();
      if (BarrierPositionType.STAGE.name().equals(group)) {
        updatePosition(planExecutionId, BarrierPositionType.STAGE, nodeExecution);
      } else if (BarrierPositionType.STEP_GROUP.name().equals(group)) {
        updatePosition(planExecutionId, BarrierPositionType.STEP_GROUP, nodeExecution);
      } else if (BarrierPositionType.STEP.name().equals(group)) {
        updatePosition(planExecutionId, BarrierPositionType.STEP, nodeExecution);
      }
    } catch (Exception e) {
      log.error("Failed to update barrier position for planExecutionId: [{}]", planExecutionId);
      throw e;
    }
  }

  private List<BarrierExecutionInstance> updatePosition(
      String planExecutionId, BarrierPositionType type, NodeExecution nodeExecution) {
    Ambiance ambiance = nodeExecution.getAmbiance();
    String accountId = AmbianceUtils.getAccountId(ambiance);
    Level level = Objects.requireNonNull(AmbianceUtils.obtainCurrentLevel(ambiance));
    String stageRuntimeId = null;
    String stepGroupRuntimeId = null;
    // TODO: Remove the below boolean variable when cleaning up the FF CDS_NG_BARRIER_STEPS_WITHIN_LOOPING_STRATEGIES.
    boolean addFiltersForBarriersWithinLoopingStrategy = false;
    if (featureFlagService.isEnabled(accountId, FeatureName.CDS_NG_BARRIER_STEPS_WITHIN_LOOPING_STRATEGIES)) {
      addFiltersForBarriersWithinLoopingStrategy = true;
      stageRuntimeId = AmbianceUtils.getStageLevelFromAmbiance(ambiance).map(Level::getRuntimeId).orElse(null);
      stepGroupRuntimeId = AmbianceUtils.getStepGroupLevelFromAmbiance(ambiance).map(Level::getRuntimeId).orElse(null);
    }
    return barrierService.updatePosition(planExecutionId, type, level.getSetupId(), nodeExecution.getUuid(),
        stageRuntimeId, stepGroupRuntimeId, addFiltersForBarriersWithinLoopingStrategy);
  }

  @Override
  public ExecutorService getInformExecutorService() {
    return executorService;
  }
}
