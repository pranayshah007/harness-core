/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.event.timeseries;

import static io.harness.event.model.EventType.DEPLOYMENT_EVENT;
import static io.harness.event.model.EventType.DEPLOYMENT_STEP_EVENT;
import static io.harness.event.model.EventType.DEPLOYMENT_VERIFIED;
import static io.harness.event.model.EventType.EXECUTION_INTERRUPT;
import static io.harness.event.model.EventType.INSTANCE_EVENT;
import static io.harness.event.model.EventType.SERVICE_GUARD_SETUP;

import io.harness.event.handler.EventHandler;
import io.harness.event.listener.EventListener;
import io.harness.event.model.Event;
import io.harness.event.timeseries.processor.DeploymentEventProcessor;
import io.harness.event.timeseries.processor.DeploymentStepEventProcessor;
import io.harness.event.timeseries.processor.ExecutionInterruptProcessor;
import io.harness.event.timeseries.processor.ServiceGuardSetupEventProcessor;
import io.harness.event.timeseries.processor.VerificationEventProcessor;
import io.harness.event.timeseries.processor.instanceeventprocessor.InstanceEventProcessor;
import io.harness.logging.AutoLogContext;

import software.wings.service.impl.event.timeseries.TimeSeriesBatchEventInfo;
import software.wings.service.impl.event.timeseries.TimeSeriesEventInfo;

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Arrays;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
public class TimeSeriesHandler implements EventHandler {
  @Inject private DeploymentEventProcessor deploymentEventProcessor;
  @Inject private DeploymentStepEventProcessor deploymentStepEventProcessor;
  @Inject private InstanceEventProcessor instanceEventProcessor;
  @Inject private VerificationEventProcessor verificationEventProcessor;
  @Inject private ServiceGuardSetupEventProcessor serviceGuardSetupEventProcessor;
  @Inject private ExecutionInterruptProcessor executionInterruptProcessor;

  @Inject
  public TimeSeriesHandler(EventListener eventListener) {
    registerForEvents(eventListener);
  }

  private void registerForEvents(EventListener eventListener) {
    eventListener.registerEventHandler(this,
        Sets.newHashSet(DEPLOYMENT_EVENT, DEPLOYMENT_STEP_EVENT, EXECUTION_INTERRUPT, INSTANCE_EVENT,
            DEPLOYMENT_VERIFIED, SERVICE_GUARD_SETUP));
  }

  @Override
  public void handleEvent(Event event) {
    switch (event.getEventType()) {
      case INSTANCE_EVENT:
        try (AutoLogContext ignore = new TimeseriesLogContext(AutoLogContext.OverrideBehavior.OVERRIDE_ERROR)) {
          TimeSeriesBatchEventInfo eventInfo = (TimeSeriesBatchEventInfo) event.getEventData().getEventInfo();
          instanceEventProcessor.processEvent((TimeSeriesBatchEventInfo) event.getEventData().getEventInfo());
        } catch (Exception ex) {
          log.error(
              "Failed to process Event : [{}] , error : [{}]", event.toString(), Arrays.toString(ex.getStackTrace()));
        }
        break;
      case DEPLOYMENT_EVENT:
        try {
          deploymentEventProcessor.processEvent((TimeSeriesEventInfo) event.getEventData().getEventInfo());
        } catch (Exception ex) {
          log.error(
              "Failed to process Event : [{}] , error : [{}]", event.toString(), Arrays.toString(ex.getStackTrace()));
        }
        break;
      case DEPLOYMENT_STEP_EVENT:
        try {
          deploymentStepEventProcessor.processEvent((TimeSeriesEventInfo) event.getEventData().getEventInfo());
        } catch (Exception ex) {
          log.error(
              "Failed to process Event : [{}] , error : [{}]", event.toString(), Arrays.toString(ex.getStackTrace()));
        }
        break;
      case EXECUTION_INTERRUPT:
        try {
          executionInterruptProcessor.processEvent((TimeSeriesEventInfo) event.getEventData().getEventInfo());
        } catch (Exception ex) {
          log.error(
              "Failed to process Event : [{}] , error : [{}]", event.toString(), Arrays.toString(ex.getStackTrace()));
        }
        break;
      case DEPLOYMENT_VERIFIED:
        verificationEventProcessor.processEvent(event.getEventData().getProperties());
        break;
      case SERVICE_GUARD_SETUP:
        serviceGuardSetupEventProcessor.processEvent(event.getEventData().getProperties());
        break;
      default:
        log.error("Invalid event typ e, dropping event : [{}]", event);
    }
  }
}
