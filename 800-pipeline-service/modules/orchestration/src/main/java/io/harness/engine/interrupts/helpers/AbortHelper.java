/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.interrupts.helpers;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.logging.UnitStatus.FAILURE;
import static io.harness.pms.contracts.interrupts.InterruptType.ABORT;

import io.harness.OrchestrationPublisherName;
import io.harness.annotations.dev.OwnedBy;
import io.harness.engine.OrchestrationEngine;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.engine.executions.node.NodeExecutionUpdateFailedException;
import io.harness.engine.interrupts.AbortInterruptCallback;
import io.harness.engine.interrupts.InterruptProcessingFailedException;
import io.harness.engine.interrupts.handlers.publisher.InterruptEventPublisher;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.ExecutionModeUtils;
import io.harness.execution.NodeExecution;
import io.harness.execution.NodeExecution.NodeExecutionKeys;
import io.harness.interrupts.Interrupt;
import io.harness.interrupts.InterruptEffect;
import io.harness.logging.AutoLogContext;
import io.harness.logging.UnitProgress;
import io.harness.pms.contracts.execution.ExecutionMode;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.interrupts.InterruptConfig;
import io.harness.pms.contracts.interrupts.InterruptType;
import io.harness.waiter.WaitNotifyEngine;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.time.Duration;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(PIPELINE)
public class AbortHelper {
  @Inject private NodeExecutionService nodeExecutionService;
  @Inject @Named(OrchestrationPublisherName.PUBLISHER_NAME) String publisherName;
  @Inject private WaitNotifyEngine waitNotifyEngine;
  @Inject private InterruptHelper interruptHelper;
  @Inject private InterruptEventPublisher interruptEventPublisher;
  @Inject private OrchestrationEngine engine;

  public void discontinueMarkedInstance(NodeExecution nodeExecution, Interrupt interrupt) {
    try (AutoLogContext ignore = interrupt.autoLogContext()) {
      boolean taskDiscontinued = interruptHelper.discontinueTaskIfRequired(nodeExecution);
      if (!taskDiscontinued) {
        log.error("Delegate Task Cannot be aborted for NodeExecutionId: {}", nodeExecution.getUuid());
      }

      if (nodeExecution.getMode() == ExecutionMode.SYNC || ExecutionModeUtils.isParentMode(nodeExecution.getMode())) {
        log.info("Aborting directly because mode is {}", nodeExecution.getMode());
        abortDiscontinuingNode(nodeExecution, interrupt.getUuid(), interrupt.getInterruptConfig());
        return;
      }

      String notifyId = interruptEventPublisher.publishEvent(nodeExecution.getUuid(), interrupt, ABORT);
      AbortInterruptCallback abortCallback = AbortInterruptCallback.builder()
                                                 .nodeExecutionId(nodeExecution.getUuid())
                                                 .interruptId(interrupt.getUuid())
                                                 .interruptType(interrupt.getType())
                                                 .interruptConfig(interrupt.getInterruptConfig())
                                                 .build();
      waitNotifyEngine.waitForAllOnInList(
          publisherName, abortCallback, Collections.singletonList(notifyId), Duration.ofMinutes(1));
      log.info("AbortCallback Registered with notifyId: {}", notifyId);
    } catch (NodeExecutionUpdateFailedException ex) {
      throw new InterruptProcessingFailedException(InterruptType.ABORT_ALL,
          "Abort failed for execution Plan :" + nodeExecution.getAmbiance().getPlanExecutionId()
              + "for NodeExecutionId: " + nodeExecution.getUuid(),
          ex);
    } catch (Exception e) {
      log.error("Error in discontinuing", e);
      throw new InvalidRequestException("Error in discontinuing, " + e.getMessage());
    }
  }

  public void abortDiscontinuingNode(NodeExecution nodeExecution, String interruptId, InterruptConfig interruptConfig) {
    List<UnitProgress> unitProgresses = InterruptHelper.evaluateUnitProgresses(nodeExecution, FAILURE);
    NodeExecution updatedNodeExecution =
        nodeExecutionService.updateStatusWithOps(nodeExecution.getUuid(), Status.ABORTED, ops -> {
          ops.set(NodeExecutionKeys.endTs, System.currentTimeMillis());
          ops.set(NodeExecutionKeys.unitProgresses, unitProgresses);
          ops.addToSet(NodeExecutionKeys.interruptHistories,
              InterruptEffect.builder()
                  .interruptId(interruptId)
                  .tookEffectAt(System.currentTimeMillis())
                  .interruptType(ABORT)
                  .interruptConfig(interruptConfig)
                  .build());
        }, EnumSet.noneOf(Status.class));
    log.info("Updated NodeExecution :{} Status to ABORTED", nodeExecution.getUuid());
    engine.endNodeExecution(updatedNodeExecution.getAmbiance());
  }
}
