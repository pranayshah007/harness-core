/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.interrupts;
import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.engine.interrupts.helpers.ExpiryHelper;
import io.harness.execution.NodeExecution;
import io.harness.execution.NodeExecution.NodeExecutionKeys;
import io.harness.pms.contracts.interrupts.InterruptConfig;
import io.harness.pms.contracts.interrupts.InterruptType;
import io.harness.tasks.ResponseData;
import io.harness.waiter.OldNotifyCallback;
import io.harness.waiter.WaitNotifyEngine;

import com.google.inject.Inject;
import java.util.Map;
import java.util.Set;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_COMMON_STEPS})
@OwnedBy(PIPELINE)
@Slf4j
public class ExpiryInterruptCallback implements OldNotifyCallback {
  @Inject private NodeExecutionService nodeExecutionService;
  @Inject private ExpiryHelper expiryHelper;
  @Inject private WaitNotifyEngine waitNotifyEngine;

  String nodeExecutionId;
  String interruptId;
  InterruptConfig interruptConfig;
  InterruptType interruptType;

  @Builder
  public ExpiryInterruptCallback(
      String nodeExecutionId, String interruptId, InterruptConfig interruptConfig, InterruptType interruptType) {
    this.nodeExecutionId = nodeExecutionId;
    this.interruptId = interruptId;
    this.interruptConfig = interruptConfig;
    this.interruptType = interruptType;
  }

  @Override
  public void notify(Map<String, ResponseData> response) {
    expireNode(response);
  }

  @Override
  public void notifyTimeout(Map<String, ResponseData> responseMap) {
    log.error("Expire event timed out for nodeExecutionId {} and interrupt {}", nodeExecutionId, interruptId);
    expireNode(responseMap);
  }

  @Override
  public void notifyError(Map<String, ResponseData> response) {
    log.error("Expire event failed for nodeExecutionId {} and interrupt {}", nodeExecutionId, interruptId);
    expireNode(response);
  }

  void expireNode(Map<String, ResponseData> response) {
    NodeExecution nodeExecution = nodeExecutionService.getWithFieldsIncluded(
        nodeExecutionId, Set.of(NodeExecutionKeys.uuid, NodeExecutionKeys.ambiance, NodeExecutionKeys.unitProgresses));
    expiryHelper.expireDiscontinuedInstance(nodeExecution, interruptConfig, interruptId, interruptType);
    ResponseData responseData = isEmpty(response) ? null : response.values().iterator().next();
    waitNotifyEngine.doneWith(nodeExecutionId + "|" + interruptId, responseData);
  }
}
