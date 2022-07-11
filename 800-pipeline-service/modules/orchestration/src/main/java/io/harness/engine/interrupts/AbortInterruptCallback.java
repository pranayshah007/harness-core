/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.interrupts;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.OwnedBy;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.engine.interrupts.helpers.AbortHelper;
import io.harness.execution.NodeExecution;
import io.harness.pms.contracts.interrupts.InterruptConfig;
import io.harness.pms.contracts.interrupts.InterruptType;
import io.harness.tasks.ResponseData;
import io.harness.waiter.OldNotifyCallback;
import io.harness.waiter.WaitNotifyEngine;

import com.google.inject.Inject;
import java.util.Map;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PIPELINE)
@Slf4j
public class AbortInterruptCallback implements OldNotifyCallback {
  @Inject private NodeExecutionService nodeExecutionService;
  @Inject private AbortHelper abortHelper;
  @Inject private WaitNotifyEngine waitNotifyEngine;

  String nodeExecutionId;
  String interruptId;
  InterruptConfig interruptConfig;
  InterruptType interruptType;

  @Builder
  public AbortInterruptCallback(
      String nodeExecutionId, String interruptId, InterruptConfig interruptConfig, InterruptType interruptType) {
    this.nodeExecutionId = nodeExecutionId;
    this.interruptId = interruptId;
    this.interruptConfig = interruptConfig;
    this.interruptType = interruptType;
  }

  @Override
  public void notify(Map<String, ResponseData> response) {
    abortNode(response);
  }

  @Override
  public void notifyTimeout(Map<String, ResponseData> responseMap) {
    log.error("Abort event timed out for nodeExecutionId {} and interrupt {}", nodeExecutionId, interruptId);
    abortNode(responseMap);
  }

  @Override
  public void notifyError(Map<String, ResponseData> response) {
    log.error("Abort event failed for nodeExecutionId {} and interrupt {}", nodeExecutionId, interruptId);
    abortNode(response);
  }

  private void abortNode(Map<String, ResponseData> response) {
    NodeExecution nodeExecution = nodeExecutionService.get(nodeExecutionId);
    abortHelper.abortDiscontinuingNode(nodeExecution, interruptId, interruptConfig);
    ResponseData responseData = isEmpty(response) ? null : response.values().iterator().next();
    waitNotifyEngine.doneWith(nodeExecutionId + "|" + interruptId, responseData);
  }
}
