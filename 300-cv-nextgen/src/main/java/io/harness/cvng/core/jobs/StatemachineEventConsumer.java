/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.jobs;

import static io.harness.eventsframework.EventsFrameworkConstants.SRM_STATEMACHINE_EVENT;

import io.harness.eventsframework.api.Consumer;
import io.harness.eventsframework.consumer.Message;
import io.harness.eventsframework.schemas.cv.StateMachineTrigger;
import io.harness.exception.InvalidRequestException;
import io.harness.queue.QueueController;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.concurrent.ExecutorService;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class StatemachineEventConsumer extends AbstractStreamConsumer {
  private static final int MAX_WAIT_TIME_SEC = 10;
  @Inject @Named("stateMachineMessageProcessorExecutor") protected ExecutorService stateMachineMessageProcessorExecutor;
  @Inject StateMachineMessageProcessor stateMachineMessageProcessor;

  @Inject
  public StatemachineEventConsumer(@Named(SRM_STATEMACHINE_EVENT) Consumer consumer, QueueController queueController) {
    super(MAX_WAIT_TIME_SEC, consumer, queueController);
  }

  @Override
  protected boolean processMessage(Message message) {
    StateMachineTrigger trigger;
    try {
      trigger = StateMachineTrigger.parseFrom(message.getMessage().getData());
      stateMachineMessageProcessorExecutor.submit(
          () -> stateMachineMessageProcessor.processAnalysisStateMachine(trigger));
    } catch (Exception ex) {
      throw new InvalidRequestException("Invalid message for srm_statemachine_event topic  " + message);
    }

    return true;
  }
}
