/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.jobs;

import io.harness.cvng.core.services.api.InternalChangeConsumerService;
import io.harness.eventsframework.EventsFrameworkConstants;
import io.harness.eventsframework.api.Consumer;
import io.harness.eventsframework.consumer.Message;
import io.harness.queue.QueueController;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class InternalChangeEventCEConsumer extends AbstractStreamConsumer {
  private static final int MAX_WAIT_TIME_SEC = 10;

  @Inject InternalChangeConsumerService internalChangeConsumerService;

  @Inject
  public InternalChangeEventCEConsumer(
      @Named(EventsFrameworkConstants.INTERNAL_CHANGE_EVENT_CE) Consumer consumer, QueueController queueController) {
    super(MAX_WAIT_TIME_SEC, consumer, queueController);
  }

  @Override
  protected boolean processMessage(Message message) {
    return internalChangeConsumerService.processMessage(message);
  }
}
