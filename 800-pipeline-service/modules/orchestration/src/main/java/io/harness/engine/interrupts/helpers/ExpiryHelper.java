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

import io.harness.annotations.dev.OwnedBy;
import io.harness.engine.OrchestrationEngine;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.engine.executions.node.NodeExecutionUpdateFailedException;
import io.harness.engine.interrupts.InterruptProcessingFailedException;
import io.harness.eraro.Level;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.NodeExecution;
import io.harness.execution.NodeExecution.NodeExecutionKeys;
import io.harness.interrupts.Interrupt;
import io.harness.interrupts.InterruptEffect;
import io.harness.logging.UnitProgress;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureData;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.execution.failure.FailureType;
import io.harness.pms.contracts.interrupts.InterruptType;
import io.harness.pms.contracts.steps.io.StepResponseProto;

import com.google.inject.Inject;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PIPELINE)
@Slf4j
public class ExpiryHelper {
  protected static final String EXPIRE_ERROR_MESSAGE =
      "Please Check the timeout configuration on the step to extend the duration of the step";

  @Inject private OrchestrationEngine engine;
  @Inject private InterruptHelper interruptHelper;
  @Inject private NodeExecutionService nodeExecutionService;

  public void expireMarkedInstance(NodeExecution nodeExecution, Interrupt interrupt) {
    try {
      boolean taskDiscontinued = interruptHelper.discontinueTaskIfRequired(nodeExecution);
      if (!taskDiscontinued) {
        log.error("Delegate Task Cannot be aborted for NodeExecutionId: {}", nodeExecution.getUuid());
      }

      List<UnitProgress> unitProgressList = InterruptHelper.evaluateUnitProgresses(nodeExecution, EXPIRED);
      nodeExecutionService.updateV2(nodeExecution.getUuid(),
          ops
          -> ops.addToSet(NodeExecutionKeys.interruptHistories,
              InterruptEffect.builder()
                  .interruptType(interrupt.getType())
                  .tookEffectAt(System.currentTimeMillis())
                  .interruptId(interrupt.getUuid())
                  .interruptConfig(interrupt.getInterruptConfig())
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
    } catch (NodeExecutionUpdateFailedException ex) {
      throw new InterruptProcessingFailedException(
          InterruptType.MARK_EXPIRED, "Expiry failed for NodeExecutionId: " + nodeExecution.getUuid(), ex);
    } catch (Exception e) {
      log.error("Error in discontinuing", e);
      throw new InvalidRequestException("Error in discontinuing, " + e.getMessage());
    }
  }
}
