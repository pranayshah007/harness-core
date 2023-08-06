/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.execution;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.eraro.ErrorCode.TIMEOUT_ENGINE_EXCEPTION;
import static io.harness.springdata.SpringDataMongoUtils.setUnset;

import io.harness.annotations.dev.OwnedBy;
import io.harness.engine.OrchestrationEngine;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.engine.executions.plan.PlanService;
import io.harness.engine.pms.advise.NodeAdviseHelper;
import io.harness.eraro.Level;
import io.harness.execution.NodeExecution;
import io.harness.execution.NodeExecution.NodeExecutionKeys;
import io.harness.plan.PlanNode;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureData;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.execution.failure.FailureType;
import io.harness.pms.sdk.core.adviser.proceedwithdefault.ProceedWithDefaultValueAdviser;
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
import org.springframework.util.CollectionUtils;

@Data
@OwnedBy(PIPELINE)
@Slf4j
public class WaitForExecutionInputCallback implements OldNotifyCallback {
  @Inject private NodeExecutionService nodeExecutionService;
  @Inject private PlanService planService;
  @Inject private WaitNotifyEngine waitNotifyEngine;
  @Inject private OrchestrationEngine engine;

  @Inject @Named("EngineExecutorService") private ExecutorService executorService;
  @Inject NodeAdviseHelper adviseHelper;
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
    NodeExecution nodeExecution = nodeExecutionService.get(nodeExecutionId);
    PlanNode node = planService.fetchNode(nodeExecution.getNodeId());
    FailureInfo failureInfo =
        FailureInfo.newBuilder()
            .setErrorMessage("ExecutionInputExpired")
            .addFailureTypes(FailureType.INPUT_TIMEOUT_FAILURE)
            .addFailureData(
                FailureData.newBuilder()
                    .addFailureTypes(FailureType.INPUT_TIMEOUT_FAILURE)
                    .setLevel(Level.ERROR.name())
                    .setCode(TIMEOUT_ENGINE_EXCEPTION.name())
                    .setMessage(
                        "Pipeline has passed the time limit to take the user input. Please check the timeout configuration")
                    .build())
            .build();

    // TODO refactor this logic its confusing what should happen if no advisers are present
    // ProceedWithDefault FailureStrategy is not configured, then expire the nodeExecution.
    if (node.getAdviserObtainments().stream().noneMatch(
            o -> o.getType().getType().equals(ProceedWithDefaultValueAdviser.ADVISER_TYPE.getType()))) {
      log.debug("Execution input timed out for nodeExecutionId {}", nodeExecutionId);
      nodeExecution = nodeExecutionService.updateStatusWithOps(nodeExecutionId, Status.EXPIRED,
          ops -> setUnset(ops, NodeExecutionKeys.failureInfo, failureInfo), EnumSet.noneOf(Status.class));
    }

    // End nodeExecution if advisers are empty.
    if (CollectionUtils.isEmpty(node.getAdviserObtainments())) {
      engine.endNodeExecution(nodeExecution.getAmbiance());
      return;
    }
    // Queue advising event so that failure-strategies will be honored.
    adviseHelper.queueAdvisingEvent(nodeExecution, failureInfo, node, Status.EXPIRED);
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
