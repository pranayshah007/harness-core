/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.execution;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.engine.OrchestrationEngine;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.tasks.ResponseData;
import io.harness.waiter.OldNotifyCallback;
import io.harness.waiter.WaitNotifyEngine;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.EnumSet;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Data
@OwnedBy(PIPELINE)
@Slf4j
public class WaitForExecutionInputCallback implements OldNotifyCallback {
  @Inject private NodeExecutionService nodeExecutionService;
  @Inject private WaitNotifyEngine waitNotifyEngine;
  @Inject private OrchestrationEngine engine;
  @Inject @Named("EngineExecutorService") private ExecutorService executorService;
  private Ambiance ambiance;

  String nodeExecutionId;
  String inputInstanceId;

  @Builder
  public WaitForExecutionInputCallback(String nodeExecutionId, String inputInstanceId, Ambiance ambiance) {
    this.nodeExecutionId = nodeExecutionId;
    this.inputInstanceId = inputInstanceId;
    this.ambiance = ambiance;
  }

  @Override
  public void notifyTimeout(Map<String, ResponseData> responseMap) {
    // TODO(BRIJESH): Update the pipeline status to Failed.
    log.error("Execution input timed out for nodeExecutionId {}", nodeExecutionId);
  }

  @Override
  public void notify(Map<String, ResponseData> response) {
    nodeExecutionService.updateStatusWithOps(nodeExecutionId, Status.QUEUED, null, EnumSet.of(Status.INPUT_WAITING));
    executorService.submit(() -> engine.startNodeExecution(ambiance));
  }

  @Override
  public void notifyError(Map<String, ResponseData> response) {
    // TODO(BRIJESH): Update the pipeline status to Failed.
    log.error("Execution input failed for nodeExecutionId {}", nodeExecutionId);
  }
}
