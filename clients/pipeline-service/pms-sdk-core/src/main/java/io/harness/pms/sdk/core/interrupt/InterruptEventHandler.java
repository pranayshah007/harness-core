/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.core.interrupt;

import static io.harness.govern.Switch.noop;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.ExecutionMode;
import io.harness.pms.contracts.interrupts.InterruptEvent;
import io.harness.pms.contracts.interrupts.InterruptEvent.ResponseCase;
import io.harness.pms.contracts.interrupts.InterruptType;
import io.harness.pms.events.base.PmsBaseEventHandler;
import io.harness.pms.sdk.core.execution.ExecutableProcessor;
import io.harness.pms.sdk.core.execution.ExecutableProcessorFactory;
import io.harness.pms.sdk.core.execution.InterruptPackage;
import io.harness.pms.sdk.core.execution.InterruptPackage.InterruptPackageBuilder;
import io.harness.pms.sdk.core.steps.io.StepParameters;
import io.harness.pms.serializer.recaster.RecastOrchestrationUtils;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_COMMON_STEPS})
@Slf4j
public class InterruptEventHandler extends PmsBaseEventHandler<InterruptEvent> {
  @Inject private PMSInterruptService pmsInterruptService;
  @Inject private ExecutableProcessorFactory executableProcessorFactory;

  @Override
  protected Map<String, String> extraLogProperties(InterruptEvent event) {
    return ImmutableMap.<String, String>builder()
        .put("interruptType", event.getType().name())
        .put("interruptUuid", event.getInterruptUuid())
        .put("notifyId", event.getNotifyId())
        .build();
  }

  @Override
  protected Ambiance extractAmbiance(InterruptEvent event) {
    return event.getAmbiance();
  }

  @Override
  protected Map<String, String> extractMetricContext(Map<String, String> metadataMap, InterruptEvent message) {
    return ImmutableMap.of();
  }

  @Override
  protected String getMetricPrefix(InterruptEvent message) {
    return null;
  }

  @Override
  protected void handleEventWithContext(InterruptEvent event) {
    InterruptType interruptType = event.getType();
    switch (interruptType) {
      case ABORT:
      case USER_MARKED_FAIL_ALL:
        handleAbortAndUserMarkedFailure(event, interruptType);
        log.info(String.format("Handled %s InterruptEvent Successfully", interruptType));
        break;
      case MARK_EXPIRED:
        handleExpire(event);
        log.info("Handled MARK_EXPIRED InterruptEvent Successfully");
        break;
      case CUSTOM_FAILURE:
        handleFailure(event);
        log.info("Handled CUSTOM_FAILURE InterruptEvent Successfully");
        break;
      default:
        log.warn("No Handling present for Interrupt Event of type : {}", interruptType);
        noop();
    }
  }

  public void handleFailure(InterruptEvent event) {
    try {
      ExecutionMode mode = extractExecutionMode(event.getResponseCase());
      ExecutableProcessor processor = executableProcessorFactory.obtainProcessor(mode);
      processor.handleFailure(buildInterruptPackage(event, false));
      pmsInterruptService.handleFailure(event.getNotifyId());
    } catch (Exception ex) {
      throw new InvalidRequestException("Handling failure at sdk failed with exception - " + ex.getMessage()
          + " with interrupt event - " + event.getInterruptUuid());
    }
  }

  public void handleAbortAndUserMarkedFailure(InterruptEvent event, InterruptType interruptType) {
    try {
      ExecutionMode mode = extractExecutionMode(event.getResponseCase());
      ExecutableProcessor processor = executableProcessorFactory.obtainProcessor(mode);
      boolean userMarked = interruptType == InterruptType.USER_MARKED_FAIL_ALL;
      processor.handleAbort(buildInterruptPackage(event, userMarked));
    } catch (Exception ex) {
      log.error(String.format(
                    "Handling %s at sdk failed with interrupt event - %s} ", interruptType, event.getInterruptUuid()),
          ex);

    } finally {
      pmsInterruptService.handleAbort(event.getNotifyId());
    }
  }

  public void handleExpire(InterruptEvent event) {
    try {
      ExecutionMode mode = extractExecutionMode(event.getResponseCase());
      ExecutableProcessor processor = executableProcessorFactory.obtainProcessor(mode);
      processor.handleExpire(buildInterruptPackage(event, false));
    } catch (Exception ex) {
      log.error("Handling expire at sdk failed with interrupt event - {} ", event.getInterruptUuid(), ex);
    } finally {
      // Even if error always send feedback
      pmsInterruptService.handleExpire(event.getNotifyId());
    }
  }

  private static InterruptPackage buildInterruptPackage(InterruptEvent event, boolean userMarked) {
    StepParameters stepParameters =
        RecastOrchestrationUtils.fromJson(event.getStepParameters().toStringUtf8(), StepParameters.class);

    InterruptPackageBuilder builder = InterruptPackage.builder()
                                          .ambiance(event.getAmbiance())
                                          .parameters(stepParameters)
                                          .metadata(event.getMetadataMap())
                                          .userMarked(userMarked);

    switch (event.getResponseCase()) {
      case ASYNC:
        builder.async(event.getAsync());
        break;
      case TASK:
        builder.task(event.getTask());
        break;
      case TASKCHAIN:
        builder.taskChain(event.getTaskChain());
        break;
      case ASYNCCHAIN:
        builder.asyncChain(event.getAsyncChain());
        break;
      default:
        // This should never happen
        throw new IllegalStateException("response case not supported");
    }
    return builder.build();
  }

  private ExecutionMode extractExecutionMode(ResponseCase responseCase) {
    switch (responseCase) {
      case ASYNC:
        return ExecutionMode.ASYNC;
      case ASYNCCHAIN:
        return ExecutionMode.ASYNC_CHAIN;
      case TASK:
        return ExecutionMode.TASK;
      case TASKCHAIN:
        return ExecutionMode.TASK_CHAIN;
      case RESPONSE_NOT_SET:
      default:
        log.warn("No Handling present for Executable Response of type : {}", responseCase);
        return ExecutionMode.UNKNOWN;
    }
  }
}
