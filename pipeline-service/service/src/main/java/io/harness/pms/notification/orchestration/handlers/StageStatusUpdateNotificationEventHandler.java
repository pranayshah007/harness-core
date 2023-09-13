/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.notification.orchestration.handlers;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.engine.observers.NodeStatusUpdateObserver;
import io.harness.engine.observers.NodeUpdateInfo;
import io.harness.engine.utils.OrchestrationUtils;
import io.harness.execution.NodeExecution;
import io.harness.notification.PipelineEventType;
import io.harness.observer.AsyncInformObserver;
import io.harness.plancreator.NGCommonUtilPlanCreationConstants;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.steps.SkipType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.execution.utils.StatusUtils;
import io.harness.pms.notification.NotificationHelper;
import io.harness.pms.sdk.SdkStepHelper;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;

@OwnedBy(HarnessTeam.PIPELINE)
@Singleton
public class StageStatusUpdateNotificationEventHandler implements AsyncInformObserver, NodeStatusUpdateObserver {
  @Inject @Named("PipelineExecutorService") ExecutorService executorService;
  @Inject NotificationHelper notificationHelper;
  @Inject SdkStepHelper sdkStepHelper;

  @Override
  public void onNodeStatusUpdate(NodeUpdateInfo nodeUpdateInfo) {
    NodeExecution nodeExecution = nodeUpdateInfo.getNodeExecution();
    if (OrchestrationUtils.isStageNode(nodeExecution)) {
      Optional<PipelineEventType> pipelineEventType = notificationHelper.getEventTypeForStage(nodeExecution);
      pipelineEventType.ifPresent(eventType
          -> notificationHelper.sendNotification(
              nodeExecution.getAmbiance(), eventType, nodeExecution, nodeUpdateInfo.getUpdatedTs()));
      return;
    }

    Set<String> nodesVisibleInUI = sdkStepHelper.getAllStepVisibleInUI();
    Level currentLevel = Objects.requireNonNull(AmbianceUtils.obtainCurrentLevel(nodeExecution.getAmbiance()));
    String identifier = currentLevel.getIdentifier();
    if (!nodesVisibleInUI.contains(currentLevel.getStepType().getType())
        || identifier.endsWith(NGCommonUtilPlanCreationConstants.ROLLBACK_NODE_NAME)) {
      return;
    }
    if (!Objects.equals(nodeExecution.getSkipGraphType(), SkipType.SKIP_NODE)
        && StatusUtils.brokeStatuses().contains(nodeExecution.getStatus())) {
      notificationHelper.sendNotification(
          nodeExecution.getAmbiance(), PipelineEventType.STEP_FAILED, nodeExecution, nodeUpdateInfo.getUpdatedTs());
    }
  }

  @Override
  public ExecutorService getInformExecutorService() {
    return executorService;
  }
}
