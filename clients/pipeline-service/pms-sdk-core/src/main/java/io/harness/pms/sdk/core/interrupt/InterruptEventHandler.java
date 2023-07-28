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
import io.harness.pms.contracts.interrupts.InterruptEvent;
import io.harness.pms.contracts.interrupts.InterruptEvent.ResponseCase;
import io.harness.pms.contracts.interrupts.InterruptType;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.events.base.PmsBaseEventHandler;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.registries.StepRegistry;
import io.harness.pms.sdk.core.steps.Step;
import io.harness.pms.sdk.core.steps.executables.Abortable;
import io.harness.pms.sdk.core.steps.executables.Failable;
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
  @Inject private StepRegistry stepRegistry;

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
      case MARK_EXPIRED:
        handleAbort(event);
        log.info("Handled ABORT InterruptEvent Successfully");
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
      Step<?> step = stepRegistry.obtain(AmbianceUtils.getCurrentStepType(event.getAmbiance()));
      if (step instanceof Failable) {
        StepParameters stepParameters =
            RecastOrchestrationUtils.fromJson(event.getStepParameters().toStringUtf8(), StepParameters.class);
        ((Failable) step).handleFailureInterrupt(event.getAmbiance(), stepParameters, event.getMetadataMap());
      }
      pmsInterruptService.handleFailure(event.getNotifyId());
    } catch (Exception ex) {
      throw new InvalidRequestException("Handling failure at sdk failed with exception - " + ex.getMessage()
          + " with interrupt event - " + event.getInterruptUuid());
    }
  }

  public void handleAbort(InterruptEvent event) {
    try {
      StepType stepType = AmbianceUtils.getCurrentStepType(event.getAmbiance());
      Step<?> step = stepRegistry.obtain(stepType);
      if (step instanceof Abortable) {
        StepParameters stepParameters =
            RecastOrchestrationUtils.fromJson(event.getStepParameters().toStringUtf8(), StepParameters.class);
        ((Abortable) step).handleAbort(event.getAmbiance(), stepParameters, extractExecutableResponses(event));
        pmsInterruptService.handleAbort(event.getNotifyId());
      } else {
        pmsInterruptService.handleAbort(event.getNotifyId());
      }
    } catch (Exception ex) {
      log.error("Handling abort at sdk failed with interrupt event - {} ", event.getInterruptUuid(), ex);
      // Even if error send feedback
      pmsInterruptService.handleAbort(event.getNotifyId());
    }
  }

  private Object extractExecutableResponses(InterruptEvent interruptEvent) {
    ResponseCase responseCase = interruptEvent.getResponseCase();
    switch (responseCase) {
      case ASYNC:
        return interruptEvent.getAsync();
      case TASK:
        return interruptEvent.getTask();
      case TASKCHAIN:
        return interruptEvent.getTaskChain();
      case RESPONSE_NOT_SET:
      default:
        log.warn("No Handling present for Executable Response of type : {}", responseCase);
        return null;
    }
  }
}
