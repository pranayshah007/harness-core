/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.interrupts.helpers;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.eraro.ErrorCode.USER_MARKED_FAILURE;
import static io.harness.pms.contracts.interrupts.InterruptType.USER_MARKED_FAIL_ALL;

import io.harness.OrchestrationPublisherName;
import io.harness.annotations.dev.OwnedBy;
import io.harness.engine.OrchestrationEngine;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.engine.executions.node.NodeExecutionUpdateFailedException;
import io.harness.engine.interrupts.InterruptProcessingFailedException;
import io.harness.engine.interrupts.UserMarkedFailureInterruptCallback;
import io.harness.engine.interrupts.handlers.publisher.InterruptEventPublisher;
import io.harness.eraro.Level;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.ExecutionModeUtils;
import io.harness.execution.NodeExecution;
import io.harness.execution.NodeExecution.NodeExecutionKeys;
import io.harness.interrupts.Interrupt;
import io.harness.interrupts.InterruptEffect;
import io.harness.logging.AutoLogContext;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.ExecutionMode;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureData;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.execution.failure.FailureType;
import io.harness.pms.contracts.interrupts.InterruptConfig;
import io.harness.pms.contracts.interrupts.InterruptType;
import io.harness.pms.contracts.steps.io.StepResponseProto;
import io.harness.waiter.WaitNotifyEngine;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.time.Duration;
import java.util.Collections;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(PIPELINE)
public class UserMarkedFailAllHelper {
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
        failDiscontinuingNode(nodeExecution.getAmbiance(), nodeExecution.getUuid(), interrupt.getType(),
            interrupt.getUuid(), interrupt.getInterruptConfig());
        return;
      }

      String notifyId = interruptEventPublisher.publishEvent(nodeExecution.getUuid(), interrupt, USER_MARKED_FAIL_ALL);
      UserMarkedFailureInterruptCallback userMarkedFailureInterruptCallback =
          UserMarkedFailureInterruptCallback.builder()
              .nodeExecutionId(nodeExecution.getUuid())
              .interruptId(interrupt.getUuid())
              .interruptType(interrupt.getType())
              .interruptConfig(interrupt.getInterruptConfig())
              .ambiance(nodeExecution.getAmbiance())
              .build();
      waitNotifyEngine.waitForAllOnInList(publisherName, userMarkedFailureInterruptCallback,
          Collections.singletonList(notifyId), Duration.ofMinutes(1));

    } catch (NodeExecutionUpdateFailedException ex) {
      throw new InterruptProcessingFailedException(USER_MARKED_FAIL_ALL,
          "UserMarkedFailure failed for execution Plan :" + nodeExecution.getAmbiance().getPlanExecutionId()
              + "for NodeExecutionId: " + nodeExecution.getUuid(),
          ex);
    } catch (Exception e) {
      log.error("Error in discontinuing", e);
      throw new InvalidRequestException("Error in discontinuing, " + e.getMessage());
    }
  }

  public void failDiscontinuingNode(Ambiance ambiance, String nodeExecutionId, InterruptType interruptType,
      String interruptId, InterruptConfig interruptConfig) {
    nodeExecutionService.updateV2(nodeExecutionId,
        ops
        -> ops.addToSet(NodeExecutionKeys.interruptHistories,
            InterruptEffect.builder()
                .interruptType(interruptType)
                .tookEffectAt(System.currentTimeMillis())
                .interruptId(interruptId)
                .interruptConfig(interruptConfig)
                .build()));
    engine.processStepResponse(ambiance,
        StepResponseProto.newBuilder()
            .setStatus(Status.FAILED)
            .setFailureInfo(FailureInfo.newBuilder()
                                .setErrorMessage("User Initiated Failure")
                                .addFailureTypes(FailureType.USER_MARKED_FAILURE)
                                .addFailureData(FailureData.newBuilder()
                                                    .addFailureTypes(FailureType.USER_MARKED_FAILURE)
                                                    .setLevel(Level.ERROR.name())
                                                    .setCode(USER_MARKED_FAILURE.name())
                                                    .setMessage("User Initiated Failure")
                                                    .build())
                                .build())
            .build());
  }
}
