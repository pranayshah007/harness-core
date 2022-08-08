/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.interrupts.statusupdate;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.engine.observers.NodeStatusUpdateHandler;
import io.harness.engine.observers.NodeUpdateInfo;
import io.harness.pms.execution.utils.StatusUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;

@OwnedBy(PIPELINE)
@Singleton
public class NodeStatusUpdateHandlerFactory {
  @Inject ApprovalStepStatusUpdate approvalStepStatusUpdate;
  @Inject InterventionWaitStepStatusUpdate interventionWaitStepStatusUpdate;
  @Inject PausedStepStatusUpdate pausedStepStatusUpdate;
  @Inject InputWaitingStepStatusUpdate inputWaitingStepStatusUpdate;
  @Inject ResumeStepStatusUpdate resumeStepStatusUpdate;
  @Inject TerminalStepStatusUpdate terminalStepStatusUpdate;
  @Inject AbortAndRunningStepStatusUpdate abortAndRunningStepStatusUpdate;

  public NodeStatusUpdateHandler obtainStepStatusUpdate(NodeUpdateInfo nodeStatusUpdateInfo) {
    switch (nodeStatusUpdateInfo.getStatus()) {
      case APPROVAL_WAITING:
        return approvalStepStatusUpdate;
      case INTERVENTION_WAITING:
        return interventionWaitStepStatusUpdate;
      case PAUSED:
        return pausedStepStatusUpdate;
      case INPUT_WAITING:
        return inputWaitingStepStatusUpdate;
      case QUEUED:
        return resumeStepStatusUpdate;
      case ABORTED:
        return abortAndRunningStepStatusUpdate;
      default:
        // Do not do this for other statuses as there multiple queries
        // Till Now only handling these will figure out a better way to do this
        if (StatusUtils.isFinalStatus(nodeStatusUpdateInfo.getStatus())) {
          return terminalStepStatusUpdate;
        }
        return null;
    }
  }
}
