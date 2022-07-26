/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.interrupts.handlers.publisher;

import static io.harness.data.structure.UUIDGenerator.generateUuid;

import io.harness.data.structure.CollectionUtils;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.engine.pms.commons.events.PmsEventSender;
import io.harness.execution.NodeExecution;
import io.harness.interrupts.Interrupt;
import io.harness.pms.contracts.execution.ExecutableResponse;
import io.harness.pms.contracts.interrupts.InterruptEvent;
import io.harness.pms.contracts.interrupts.InterruptEvent.Builder;
import io.harness.pms.contracts.interrupts.InterruptType;
import io.harness.pms.events.base.PmsEventCategory;
import io.harness.pms.execution.utils.InterruptEventUtils;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RedisInterruptEventPublisher implements InterruptEventPublisher {
  @Inject private NodeExecutionService nodeExecutionService;
  @Inject private PmsEventSender eventSender;

  @Override
  public String publishEvent(String nodeExecutionId, Interrupt interrupt, InterruptType interruptType) {
    NodeExecution nodeExecution = nodeExecutionService.get(nodeExecutionId);
    Builder builder = InterruptEvent.newBuilder()
                          .setInterruptUuid(interrupt.getUuid())
                          .setAmbiance(nodeExecution.getAmbiance())
                          .setType(interruptType)
                          .putAllMetadata(CollectionUtils.emptyIfNull(interrupt.getMetadata()))
                          .setNotifyId(generateUuid())
                          .setStepParameters(nodeExecution.getResolvedStepParametersBytes());
    InterruptEvent event = populateResponse(nodeExecution, builder);

    eventSender.sendEvent(nodeExecution.getAmbiance(), event.toByteString(), PmsEventCategory.INTERRUPT_EVENT,
        nodeExecution.getModule(), false);
    log.info("Interrupt Event ");
    return event.getNotifyId();
  }

  private InterruptEvent populateResponse(NodeExecution nodeExecution, Builder builder) {
    int responseCount = nodeExecution.getExecutableResponses().size();
    if (responseCount <= 0) {
      return builder.build();
    }
    ExecutableResponse executableResponse = nodeExecution.getExecutableResponses().get(responseCount - 1);
    return InterruptEventUtils.buildInterruptEvent(builder, executableResponse);
  }
}
