/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.concurrency;

import static io.harness.steps.SdkCoreStepUtils.createStepResponseFromChildResponse;

import io.harness.OrchestrationPublisherName;
import io.harness.data.structure.EmptyPredicate;
import io.harness.engine.OrchestrationEngine;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.exception.UnexpectedException;
import io.harness.execution.NodeExecution;
import io.harness.graph.stepDetail.service.PmsGraphStepDetailsService;
import io.harness.lock.AcquiredLock;
import io.harness.lock.PersistentLocker;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.steps.io.StepResponseProto;
import io.harness.pms.execution.utils.NodeProjectionUtils;
import io.harness.pms.execution.utils.StatusUtils;
import io.harness.tasks.ResponseData;
import io.harness.waiter.OldNotifyCallback;
import io.harness.waiter.WaitNotifyEngine;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.time.Duration;
import java.util.Map;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Data
@Builder
@Slf4j
public class MaxConcurrentChildCallback implements OldNotifyCallback {
  private static final String EXECUTION_START_PREFIX = "EXECUTION_START_CALLBACK_%s";
  @Inject @Named(OrchestrationPublisherName.PUBLISHER_NAME) private String publisherName;

  @Inject OrchestrationEngine engine;
  @Inject NodeExecutionService nodeExecutionService;
  @Inject WaitNotifyEngine waitNotifyEngine;
  @Inject PmsGraphStepDetailsService nodeExecutionInfoService;
  @Inject PersistentLocker persistentLocker;

  long maxConcurrency;
  String parentNodeExecutionId;
  Ambiance ambiance; // Store only planExecutionId

  Boolean proceedIfFailed;
  @Override
  public void notify(Map<String, ResponseData> response) {
    String lockName = String.format(EXECUTION_START_PREFIX, parentNodeExecutionId);
    try (AcquiredLock<?> lock =
             persistentLocker.waitToAcquireLockOptional(lockName, Duration.ofSeconds(10), Duration.ofSeconds(30))) {
      if (lock == null) {
        log.error("Could not acquire lock for nodeExecutionId: [{}]", parentNodeExecutionId);
        throw new UnexpectedException("Unable to occupy lock therefore throwing the exception");
      }
      Status currentStatus = createStepResponseFromChildResponse(response).getStatus();
      ConcurrentChildInstance childInstance =
          nodeExecutionInfoService.incrementCursor(parentNodeExecutionId, currentStatus);

      if (childInstance == null) {
        log.error("[MaxConcurrentCallback]: ChildInstance found null for parentId: " + parentNodeExecutionId);
        nodeExecutionService.errorOutActiveNodes(ambiance.getPlanExecutionId());
        return;
      }
      log.info("[MaxConcurrentCallback]: MaxConcurrentCallback called for parentId: " + parentNodeExecutionId);

      // We have reached the last child already so ignore this callback as there is no new child to run.
      if (childInstance.getCursor() >= childInstance.getChildrenNodeExecutionIds().size()) {
        log.info(
            "[MaxConcurrentCallback]: Ignoring the callback as we have traversed all the children for parentExecutionId: "
            + parentNodeExecutionId);
        return;
      }
      int cursor = childInstance.getCursor();
      String nodeExecutionToStart = childInstance.getChildrenNodeExecutionIds().get(cursor);
      // If proceedIfFailed is false then we should mark all the remaining nodes as skipped.
      if (proceedIfFailed != null && !proceedIfFailed
          && (StatusUtils.brokeStatuses().contains(currentStatus)
              || (EmptyPredicate.isNotEmpty(childInstance.getChildStatuses())
                  && childInstance.getChildStatuses().stream().anyMatch(
                      s -> StatusUtils.brokeStatuses().contains(s))))) {
        skipExecution(nodeExecutionToStart);
        return;
      }
      getAmbianceAndStartExecution(nodeExecutionToStart);
    }
  }

  private void skipExecution(String nodeExecutionId) {
    log.info(String.format("Skipping node: %s", nodeExecutionId));
    NodeExecution nodeExecution =
        nodeExecutionService.getWithFieldsIncluded(nodeExecutionId, NodeProjectionUtils.withAmbianceAndStatus);
    StepResponseProto response = StepResponseProto.newBuilder().setStatus(Status.SKIPPED).build();
    engine.processStepResponse(nodeExecution.getAmbiance(), response);
  }

  private void getAmbianceAndStartExecution(String nodeExecutionToStart) {
    NodeExecution nodeExecution =
        nodeExecutionService.getWithFieldsIncluded(nodeExecutionToStart, NodeProjectionUtils.withAmbianceAndStatus);
    if (StatusUtils.resumableStatuses().contains(nodeExecution.getStatus())) {
      log.info("[MaxConcurrentCallback]: Starting the execution with id: " + nodeExecutionToStart);
      engine.startNodeExecution(nodeExecution.getAmbiance());
    }
  }

  @Override
  public void notifyError(Map<String, ResponseData> response) {
    notify(response);
  }
}
