/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.interrupts.helpers;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.eraro.ErrorCode.TIMEOUT_ENGINE_EXCEPTION;
import static io.harness.logging.UnitStatus.EXPIRED;
import static io.harness.pms.contracts.interrupts.InterruptType.MARK_EXPIRED;
import static io.harness.springdata.SpringDataMongoUtils.setUnset;

import io.harness.OrchestrationPublisherName;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.engine.OrchestrationEngine;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.engine.executions.node.NodeExecutionUpdateFailedException;
import io.harness.engine.interrupts.ExpiryInterruptCallback;
import io.harness.engine.interrupts.InterruptProcessingFailedException;
import io.harness.engine.interrupts.handlers.publisher.InterruptEventPublisher;
import io.harness.eraro.Level;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.ExecutionModeUtils;
import io.harness.execution.NodeExecution;
import io.harness.execution.NodeExecution.NodeExecutionKeys;
import io.harness.interrupts.Interrupt;
import io.harness.interrupts.InterruptEffect;
import io.harness.logging.UnitProgress;
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
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_COMMON_STEPS})
@OwnedBy(PIPELINE)
@Slf4j
public class ExpiryHelper {
  protected static final String EXPIRE_ERROR_MESSAGE =
      "Please Check the timeout configuration on the step to extend the duration of the step";

  @Inject private OrchestrationEngine engine;
  @Inject private InterruptHelper interruptHelper;
  @Inject @Named(OrchestrationPublisherName.PUBLISHER_NAME) String publisherName;
  @Inject private NodeExecutionService nodeExecutionService;
  @Inject private InterruptEventPublisher interruptEventPublisher;
  @Inject private WaitNotifyEngine waitNotifyEngine;

  public void expireMarkedInstance(NodeExecution nodeExecution, Interrupt interrupt) {
    expireMarkedInstance(nodeExecution, interrupt, false);
  }
  public void expireMarkedInstance(NodeExecution nodeExecution, Interrupt interrupt, boolean expireAndEndExecution) {
    try {
      boolean taskDiscontinued = interruptHelper.discontinueTaskIfRequired(nodeExecution);
      if (!taskDiscontinued) {
        log.error("Delegate Task Cannot be aborted for NodeExecutionId: {}", nodeExecution.getUuid());
      }

      if (nodeExecution.getMode() == ExecutionMode.SYNC || ExecutionModeUtils.isParentMode(nodeExecution.getMode())) {
        expireExecution(nodeExecution, expireAndEndExecution, interrupt);
        return;
      }

      String notifyId = interruptEventPublisher.publishEvent(nodeExecution.getUuid(), interrupt, MARK_EXPIRED);
      /*
      We had an issue where step node failure strategy is being invoked even after the Stage node is expired.
      Advisers should not be called if Parent node get expired. To address the issue we have added a boolean
      expireAndEndExecution, endNodeExecution is called if boolean set to True.
       */
      ExpiryInterruptCallback expiryInterruptCallback = ExpiryInterruptCallback.builder()
                                                            .nodeExecutionId(nodeExecution.getUuid())
                                                            .interruptId(interrupt.getUuid())
                                                            .interruptType(interrupt.getType())
                                                            .expireAndEndExecution(expireAndEndExecution)
                                                            .interruptConfig(interrupt.getInterruptConfig())
                                                            .metadata(interrupt.getMetadata())
                                                            .build();
      waitNotifyEngine.waitForAllOnInList(
          publisherName, expiryInterruptCallback, Collections.singletonList(notifyId), Duration.ofMinutes(1));
    } catch (NodeExecutionUpdateFailedException ex) {
      throw new InterruptProcessingFailedException(
          InterruptType.MARK_EXPIRED, "Expiry failed for NodeExecutionId: " + nodeExecution.getUuid(), ex);
    } catch (Exception e) {
      log.error("Error in discontinuing", e);
      throw new InvalidRequestException("Error in discontinuing, " + e.getMessage());
    }
  }

  public void expireDiscontinuedInstance(
      NodeExecution nodeExecution, InterruptConfig interruptConfig, String interruptId, InterruptType interruptType) {
    List<UnitProgress> unitProgressList = InterruptHelper.evaluateUnitProgresses(nodeExecution, EXPIRED);
    nodeExecutionService.updateV2(nodeExecution.getUuid(),
        ops
        -> ops.addToSet(NodeExecutionKeys.interruptHistories,
            InterruptEffect.builder()
                .interruptType(interruptType)
                .tookEffectAt(System.currentTimeMillis())
                .interruptId(interruptId)
                .interruptConfig(interruptConfig)
                .build()));

    StepResponseProto expiredStepResponse =
        StepResponseProto.newBuilder()
            .setStatus(Status.EXPIRED)
            .setFailureInfo(FailureInfo.newBuilder()
                                .setErrorMessage(EXPIRE_ERROR_MESSAGE)
                                .addFailureTypes(FailureType.TIMEOUT_FAILURE)
                                .addFailureData(FailureData.newBuilder()
                                                    .addFailureTypes(FailureType.TIMEOUT_FAILURE)
                                                    .setLevel(Level.ERROR.name())
                                                    .setCode(TIMEOUT_ENGINE_EXCEPTION.name())
                                                    .setMessage(EXPIRE_ERROR_MESSAGE)
                                                    .build())
                                .build())
            .addAllUnitProgress(unitProgressList)
            .build();
    engine.processStepResponse(nodeExecution.getAmbiance(), expiredStepResponse);
  }

  public void expireDiscontinuedInstanceAndEndAllNodesExecution(NodeExecution nodeExecution,
      InterruptConfig interruptConfig, String interruptId, InterruptType interruptType, Map<String, String> metadata) {
    String errorMessage = "Please Check the timeout configuration";
    if (null != metadata) {
      errorMessage = String.format("Please Check the timeout configuration for the %s [%s] .",
          metadata.get(NodeExecutionKeys.group), metadata.get(NodeExecutionKeys.identifier));
    }

    List<UnitProgress> unitProgresses = InterruptHelper.evaluateUnitProgresses(nodeExecution, EXPIRED);
    String finalErrorMessage = errorMessage;
    NodeExecution updatedNodeExecution =
        nodeExecutionService.updateStatusWithOps(nodeExecution.getUuid(), Status.EXPIRED, ops -> {
          ops.set(NodeExecutionKeys.endTs, System.currentTimeMillis());
          ops.set(NodeExecutionKeys.unitProgresses, unitProgresses);
          setUnset(ops, NodeExecutionKeys.failureInfo,
              FailureInfo.newBuilder()
                  .setErrorMessage(finalErrorMessage)
                  .addFailureTypes(FailureType.TIMEOUT_FAILURE)
                  .addFailureData(FailureData.newBuilder()
                                      .addFailureTypes(FailureType.TIMEOUT_FAILURE)
                                      .setLevel(Level.ERROR.name())
                                      .setCode(TIMEOUT_ENGINE_EXCEPTION.name())
                                      .setMessage(finalErrorMessage)
                                      .build())
                  .build());
          ops.addToSet(NodeExecutionKeys.interruptHistories,
              InterruptEffect.builder()
                  .interruptId(interruptId)
                  .tookEffectAt(System.currentTimeMillis())
                  .interruptType(interruptType)
                  .interruptConfig(interruptConfig)
                  .build());
        }, EnumSet.noneOf(Status.class));
    log.info("Updated NodeExecution :{} Status to EXPIRED", nodeExecution.getUuid());
    engine.endNodeExecution(updatedNodeExecution.getAmbiance());
  }

  public void expireExecution(NodeExecution nodeExecution, boolean expireAndEndExecution, Interrupt interrupt) {
    log.info("Expiring directly because mode is {}", nodeExecution.getMode());
    if (expireAndEndExecution) {
      expireDiscontinuedInstanceAndEndAllNodesExecution(nodeExecution, interrupt.getInterruptConfig(),
          interrupt.getUuid(), interrupt.getType(), interrupt.getMetadata());
      return;
    }
    expireDiscontinuedInstance(nodeExecution, interrupt.getInterruptConfig(), interrupt.getUuid(), interrupt.getType());
  }
}
